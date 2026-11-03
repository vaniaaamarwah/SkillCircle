package database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

// Creates the database tables the first time the program runs, and upgrades
// ("migrates") an older database when the program changes.
//
// HOW VERSIONING WORKS
// SQLite keeps a number inside the database file called user_version.
//   0 = brand-new empty file -> build the whole schema
//   1 = schema version 1 is already there -> nothing to do
// To change the tables later, add version 2 below: put the new ALTER TABLE
// statements in migrateToVersion2(), raise CURRENT_VERSION to 2, and add one
// "if (version < 2)" line. Old data is kept; a backup is made automatically first.
//
// SAFETY RULES
//   * The database file is NEVER deleted or recreated.
//   * Every upgrade runs inside one transaction: it fully succeeds or is fully undone.
//   * A database written by a NEWER version of the program is refused, not touched.
public final class DatabaseInitializer {

    public static final int CURRENT_VERSION = 2;

    private DatabaseInitializer() { }

    // Call once when the application starts
    public static void initialize() {
        Path path = DatabaseConnection.getDatabasePath();
        try {
            Path folder = path.getParent();
            if (folder != null) {
                Files.createDirectories(folder);
            }
        } catch (IOException e) {
            throw new DataAccessException("Could not create the data folder for: " + path, e);
        }

        try (Connection connection = DatabaseConnection.open()) {
            int version = readVersion(connection);

            if (version > CURRENT_VERSION) {
                throw new DataAccessException("This database (version " + version
                        + ") was created by a newer SkillCircle than this one (version "
                        + CURRENT_VERSION + "). It was left untouched.");
            }
            if (version == CURRENT_VERSION) {
                return;                                   // already up to date
            }
            if (version > 0) {
                DatabaseBackup.createBackup();            // protect existing data before upgrading
            }

            connection.setAutoCommit(false);              // start ONE transaction
            try {
                if (version < 1) {
                    createVersion1(connection);
                }
                if (version < 2) {
                    migrateToVersion2(connection);
                }
                // Future upgrades go here, in order:
                // if (version < 3) { migrateToVersion3(connection); }

                writeVersion(connection, CURRENT_VERSION);
                connection.commit();                      // everything succeeded
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection);              // undo everything
                throw new DataAccessException("Could not set up the database: " + e.getMessage(), e);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not set up the database: " + e.getMessage(), e);
        }
    }

    // The schema version stored in the file (0 for a new file)
    public static int getSchemaVersion() {
        try (Connection connection = DatabaseConnection.open()) {
            return readVersion(connection);
        } catch (SQLException e) {
            throw new DataAccessException("Could not read the database version: " + e.getMessage(), e);
        }
    }

    private static int readVersion(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA user_version")) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private static void writeVersion(Connection connection, int version) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            // 'version' is an int we control, so building the text here is safe
            statement.execute("PRAGMA user_version = " + version);
        }
    }

    private static void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // the original error is more useful than a rollback error
        }
    }

    // ------------------------------------------------------------------
    // SCHEMA VERSION 1
    //
    // Notes for your viva:
    //  * PRIMARY KEY identifies each row; FOREIGN KEY links tables together.
    //  * ON DELETE CASCADE: deleting a student automatically deletes their skills,
    //    availability, requests, sessions and bookmarks.
    //  * COLLATE NOCASE: "CS2024001" and "cs2024001" count as the same value.
    //  * CHECK constraints make the database itself refuse invalid values.
    //  * Passwords are never stored: only a random salt and a PBKDF2 hash, plus the
    //    algorithm and iteration count so the method can be upgraded in the future.
    //  * A skill name is stored once in "skills" and referenced by id everywhere.
    // ------------------------------------------------------------------
    private static final String[] SCHEMA_V1 = {

        // small key/value table (used later to remember that demo data was added)
        "CREATE TABLE app_meta ("
            + " key   TEXT PRIMARY KEY,"
            + " value TEXT NOT NULL)",

        "CREATE TABLE students ("
            + " id              INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " student_id      TEXT NOT NULL UNIQUE COLLATE NOCASE,"
            + " full_name       TEXT NOT NULL CHECK (length(trim(full_name)) > 0),"
            + " email           TEXT NOT NULL UNIQUE COLLATE NOCASE,"
            + " password_salt   TEXT NOT NULL,"
            + " password_hash   TEXT NOT NULL,"
            + " hash_algorithm  TEXT NOT NULL DEFAULT 'PBKDF2WithHmacSHA256',"
            + " hash_iterations INTEGER NOT NULL DEFAULT 65536,"
            + " department      TEXT NOT NULL,"
            + " year_of_study   TEXT NOT NULL,"
            + " bio             TEXT NOT NULL DEFAULT '',"
            + " learning_mode   TEXT NOT NULL DEFAULT 'Online'"
            + "                 CHECK (learning_mode IN ('Online', 'Offline')),"
            + " created_at      TEXT NOT NULL DEFAULT (datetime('now')),"
            + " updated_at      TEXT NOT NULL DEFAULT (datetime('now')))",

        "CREATE TABLE skills ("
            + " id   INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " name TEXT NOT NULL UNIQUE COLLATE NOCASE CHECK (length(trim(name)) > 0))",

        "CREATE TABLE student_offered_skills ("
            + " student_pk INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,"
            + " skill_id   INTEGER NOT NULL REFERENCES skills(id)   ON DELETE RESTRICT,"
            + " PRIMARY KEY (student_pk, skill_id))",

        "CREATE TABLE student_wanted_skills ("
            + " student_pk INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,"
            + " skill_id   INTEGER NOT NULL REFERENCES skills(id)   ON DELETE RESTRICT,"
            + " PRIMARY KEY (student_pk, skill_id))",

        "CREATE TABLE student_availability ("
            + " student_pk  INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,"
            + " day_of_week TEXT NOT NULL CHECK (day_of_week IN ('Monday','Tuesday','Wednesday',"
            + "             'Thursday','Friday','Saturday','Sunday')),"
            + " time_slot   TEXT NOT NULL CHECK (length(trim(time_slot)) > 0),"
            + " PRIMARY KEY (student_pk, day_of_week, time_slot))",

        // requester TEACHES offered_skill_id and wants to LEARN wanted_skill_id
        "CREATE TABLE exchange_requests ("
            + " id               INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " requester_pk     INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,"
            + " receiver_pk      INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,"
            + " offered_skill_id INTEGER NOT NULL REFERENCES skills(id)   ON DELETE RESTRICT,"
            + " wanted_skill_id  INTEGER NOT NULL REFERENCES skills(id)   ON DELETE RESTRICT,"
            + " status           TEXT NOT NULL DEFAULT 'PENDING'"
            + "                  CHECK (status IN ('PENDING','ACCEPTED','REJECTED','CANCELLED')),"
            + " created_at       TEXT NOT NULL DEFAULT (datetime('now')),"
            + " updated_at       TEXT NOT NULL DEFAULT (datetime('now')),"
            + " CHECK (requester_pk <> receiver_pk))",

        // the same swap cannot be PENDING or ACCEPTED twice in the same direction
        "CREATE UNIQUE INDEX ux_active_request ON exchange_requests"
            + " (requester_pk, receiver_pk, offered_skill_id, wanted_skill_id)"
            + " WHERE status IN ('PENDING','ACCEPTED')",

        "CREATE INDEX ix_requests_receiver  ON exchange_requests (receiver_pk, status)",
        "CREATE INDEX ix_requests_requester ON exchange_requests (requester_pk, status)",

        "CREATE TABLE sessions ("
            + " id           INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " request_id   INTEGER NOT NULL REFERENCES exchange_requests(id) ON DELETE CASCADE,"
            + " teacher_pk   INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,"
            + " learner_pk   INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,"
            + " skill_id     INTEGER NOT NULL REFERENCES skills(id)   ON DELETE RESTRICT,"
            + " session_date TEXT NOT NULL"
            + "              CHECK (session_date GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]'),"
            + " time_slot    TEXT NOT NULL CHECK (length(trim(time_slot)) > 0),"
            + " mode         TEXT NOT NULL CHECK (mode IN ('Online', 'Offline')),"
            + " status       TEXT NOT NULL DEFAULT 'PROPOSED'"
            + "              CHECK (status IN ('PROPOSED','CONFIRMED','CANCELLED')),"
            + " created_at   TEXT NOT NULL DEFAULT (datetime('now')),"
            + " updated_at   TEXT NOT NULL DEFAULT (datetime('now')),"
            + " CHECK (teacher_pk <> learner_pk))",

        "CREATE INDEX ix_sessions_request ON sessions (request_id)",

        "CREATE TABLE saved_students ("
            + " owner_pk  INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,"
            + " saved_pk  INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,"
            + " created_at TEXT NOT NULL DEFAULT (datetime('now')),"
            + " PRIMARY KEY (owner_pk, saved_pk),"
            + " CHECK (owner_pk <> saved_pk))",

        "CREATE INDEX ix_offered_skill ON student_offered_skills (skill_id)",
        "CREATE INDEX ix_wanted_skill  ON student_wanted_skills (skill_id)"
    };

    // ------------------------------------------------------------------
    // SCHEMA VERSION 2 (added when requests, sessions and the activity feed became persistent)
    //  * students.is_sample     : 1 for the built-in demo students, 0 for real accounts
    //  * sessions.proposed_by_pk: who made the latest proposal (the OTHER student confirms)
    //  * activity_log           : the "Recent Activity" lines of the Dashboard, one row per
    //                             student the line concerns
    // Existing rows are kept: old students get is_sample = 0, old sessions get NULL
    // (the application then treats the teacher as the proposer).
    // ------------------------------------------------------------------
    private static final String[] MIGRATION_V2 = {
        "ALTER TABLE students ADD COLUMN is_sample INTEGER NOT NULL DEFAULT 0"
            + " CHECK (is_sample IN (0, 1))",

        "ALTER TABLE sessions ADD COLUMN proposed_by_pk INTEGER"
            + " REFERENCES students(id) ON DELETE SET NULL",

        "CREATE TABLE activity_log ("
            + " id         INTEGER PRIMARY KEY AUTOINCREMENT,"
            + " student_pk INTEGER NOT NULL REFERENCES students(id) ON DELETE CASCADE,"
            + " message    TEXT NOT NULL CHECK (length(trim(message)) > 0),"
            + " created_at TEXT NOT NULL)",

        "CREATE INDEX ix_activity_student ON activity_log (student_pk, id)"
    };

    private static void migrateToVersion2(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : MIGRATION_V2) {
                statement.execute(sql);
            }
        }
    }

    private static void createVersion1(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            for (String sql : SCHEMA_V1) {
                statement.execute(sql);
            }
        }
    }
}
