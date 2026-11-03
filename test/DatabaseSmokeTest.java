import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import database.DataAccessException;
import database.DatabaseBackup;
import database.DatabaseConnection;
import database.DatabaseInitializer;

// A simple test program (no testing library needed). It checks the database
// layer using a TEMPORARY database file, so it never touches your real data.
// Run it with run-tests.ps1. Each line prints PASS or FAIL.
public class DatabaseSmokeTest {

    private static int passed = 0;
    private static int failed = 0;

    private static void check(String name, boolean condition) {
        System.out.println((condition ? "PASS  " : "FAIL  ") + name);
        if (condition) {
            passed++;
        } else {
            failed++;
        }
    }

    // True if running this SQL is REJECTED by the database
    private static boolean isRejected(Connection c, String sql) {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
            return false;
        } catch (SQLException e) {
            return true;
        }
    }

    private static long count(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement(); ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getLong(1) : -1;
        }
    }

    private static void run(Connection c, String sql) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute(sql);
        }
    }

    private static void addStudent(Connection c, String studentId, String email) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO students (student_id, full_name, email, password_salt, password_hash,"
                + " department, year_of_study) VALUES (?, ?, ?, 'salt', 'hash', 'CSE', '1st Year')")) {
            ps.setString(1, studentId);
            ps.setString(2, "Name " + studentId);
            ps.setString(3, email);
            ps.executeUpdate();
        }
    }

    public static void main(String[] args) throws Exception {
        Path tempFolder = Files.createTempDirectory("skillcircle-test");
        Path dbFile = tempFolder.resolve("test.db");
        DatabaseConnection.setDatabasePath(dbFile);

        // ---------- initialization ----------
        check("database file does not exist before initialize()", !Files.exists(dbFile));
        DatabaseInitializer.initialize();
        check("initialize() creates the database file", Files.exists(dbFile));
        check("schema version is " + DatabaseInitializer.CURRENT_VERSION,
                DatabaseInitializer.getSchemaVersion() == DatabaseInitializer.CURRENT_VERSION);

        try (Connection c = DatabaseConnection.open()) {
            List<String> expected = new ArrayList<>();
            for (String t : new String[] {"app_meta", "students", "skills", "student_offered_skills",
                    "student_wanted_skills", "student_availability", "exchange_requests",
                    "sessions", "saved_students"}) {
                expected.add(t);
            }
            int found = 0;
            for (String t : expected) {
                if (count(c, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name='" + t + "'") == 1) {
                    found++;
                }
            }
            check("all " + expected.size() + " tables exist", found == expected.size());

            boolean hasPlainPassword = false;
            try (Statement s = c.createStatement(); ResultSet r = s.executeQuery("PRAGMA table_info(students)")) {
                while (r.next()) {
                    if (r.getString("name").equalsIgnoreCase("password")) {
                        hasPlainPassword = true;
                    }
                }
            }
            check("students table has NO plain 'password' column", !hasPlainPassword);

            // ---------- foreign keys ----------
            check("foreign keys are ON for each new connection", count(c, "PRAGMA foreign_keys") == 1);
            run(c, "INSERT INTO skills (name) VALUES ('Java')");
            check("foreign key: skill link to a missing student is rejected",
                    isRejected(c, "INSERT INTO student_offered_skills (student_pk, skill_id) VALUES (999, 1)"));

            // ---------- UNIQUE / CHECK constraints ----------
            addStudent(c, "CS1", "a@x.com");
            check("unique Student ID (ignoring upper/lower case)",
                    isRejectedStudent(c, "cs1", "other@x.com"));
            check("unique email (ignoring upper/lower case)",
                    isRejectedStudent(c, "CS9", "A@X.COM"));
            check("unique skill name (ignoring case)", isRejected(c, "INSERT INTO skills (name) VALUES ('JAVA')"));
            check("empty skill name rejected", isRejected(c, "INSERT INTO skills (name) VALUES ('   ')"));
            check("invalid learning mode rejected",
                    isRejected(c, "UPDATE students SET learning_mode = 'Hybrid' WHERE student_id = 'CS1'"));

            addStudent(c, "CS2", "b@x.com");
            addStudent(c, "CS3", "c@x.com");
            run(c, "INSERT INTO skills (name) VALUES ('Graphic Design')");   // skill id 2
            long cs1 = count(c, "SELECT id FROM students WHERE student_id='CS1'");
            long cs2 = count(c, "SELECT id FROM students WHERE student_id='CS2'");

            // ---------- requests ----------
            check("request from a student to themselves rejected",
                    isRejected(c, "INSERT INTO exchange_requests (requester_pk, receiver_pk, offered_skill_id,"
                            + " wanted_skill_id) VALUES (" + cs1 + "," + cs1 + ",1,2)"));
            String insertRequest = "INSERT INTO exchange_requests (requester_pk, receiver_pk, offered_skill_id,"
                    + " wanted_skill_id) VALUES (" + cs1 + "," + cs2 + ",1,2)";
            run(c, insertRequest);
            check("duplicate ACTIVE request rejected", isRejected(c, insertRequest));
            check("invalid request status rejected",
                    isRejected(c, "UPDATE exchange_requests SET status = 'MAYBE'"));
            run(c, "UPDATE exchange_requests SET status = 'CANCELLED'");
            check("same request allowed again after the old one is CANCELLED", !isRejected(c, insertRequest));

            // ---------- sessions ----------
            long requestId = count(c, "SELECT MAX(id) FROM exchange_requests");
            String insertSession = "INSERT INTO sessions (request_id, teacher_pk, learner_pk, skill_id,"
                    + " session_date, time_slot, mode) VALUES (" + requestId + "," + cs1 + "," + cs2
                    + ",1,'%s','Evening','Online')";
            check("badly formatted session date rejected", isRejected(c, String.format(insertSession, "22/10/2026")));
            check("valid session accepted", !isRejected(c, String.format(insertSession, "2026-10-30")));

            // ---------- saved students + links, then CASCADE delete ----------
            run(c, "INSERT INTO saved_students (owner_pk, saved_pk) VALUES (" + cs1 + "," + cs2 + ")");
            check("saving the same student twice rejected",
                    isRejected(c, "INSERT INTO saved_students (owner_pk, saved_pk) VALUES (" + cs1 + "," + cs2 + ")"));
            check("saving yourself rejected",
                    isRejected(c, "INSERT INTO saved_students (owner_pk, saved_pk) VALUES (" + cs1 + "," + cs1 + ")"));
            run(c, "INSERT INTO student_offered_skills (student_pk, skill_id) VALUES (" + cs2 + ",1)");
            run(c, "INSERT INTO student_availability (student_pk, day_of_week, time_slot)"
                    + " VALUES (" + cs2 + ",'Monday','Evening')");
            check("invalid weekday rejected", isRejected(c, "INSERT INTO student_availability (student_pk,"
                    + " day_of_week, time_slot) VALUES (" + cs2 + ",'Funday','Evening')"));

            run(c, "DELETE FROM students WHERE id = " + cs2);
            check("deleting a student cascades: requests removed",
                    count(c, "SELECT COUNT(*) FROM exchange_requests") == 0);
            check("deleting a student cascades: sessions removed", count(c, "SELECT COUNT(*) FROM sessions") == 0);
            check("deleting a student cascades: bookmarks removed", count(c, "SELECT COUNT(*) FROM saved_students") == 0);
            check("deleting a student cascades: skills and availability removed",
                    count(c, "SELECT COUNT(*) FROM student_offered_skills") == 0
                    && count(c, "SELECT COUNT(*) FROM student_availability") == 0);
            check("skills table itself is untouched by the cascade", count(c, "SELECT COUNT(*) FROM skills") == 2);
            check("a skill still in use cannot be deleted (RESTRICT)",
                    prepareRestrictCase(c, cs1));
        }

        // ---------- initialize again: data must survive ----------
        DatabaseInitializer.initialize();
        try (Connection c = DatabaseConnection.open()) {
            check("running initialize() again keeps existing data",
                    count(c, "SELECT COUNT(*) FROM students WHERE student_id='CS1'") == 1);
        }

        // ---------- backup ----------
        Path backup = DatabaseBackup.createBackup();
        check("backup file is created", Files.exists(backup) && Files.size(backup) > 0);
        Path original = DatabaseConnection.getDatabasePath();
        DatabaseConnection.setDatabasePath(backup);
        try (Connection c = DatabaseConnection.open()) {
            check("backup contains the same data", count(c, "SELECT COUNT(*) FROM students WHERE student_id='CS1'") == 1);
        }
        DatabaseConnection.setDatabasePath(original);

        // ---------- a database from a NEWER program version ----------
        try (Connection c = DatabaseConnection.open()) {
            run(c, "PRAGMA user_version = 99");
        }
        boolean refused = false;
        try {
            DatabaseInitializer.initialize();
        } catch (DataAccessException e) {
            refused = e.getMessage().contains("newer");
        }
        check("a database from a newer version is refused with a clear message", refused);
        try (Connection c = DatabaseConnection.open()) {
            check("...and its data is left untouched", count(c, "SELECT COUNT(*) FROM students WHERE student_id='CS1'") == 1);
            run(c, "PRAGMA user_version = " + DatabaseInitializer.CURRENT_VERSION);
        }

        // ---------- database path ----------
        String savedProperty = System.getProperty("skillcircle.db");
        System.clearProperty("skillcircle.db");
        Path defaultPath = DatabaseConnection.getDefaultPath();
        Path codeFolder = Paths.get(DatabaseConnection.class.getProtectionDomain()
                .getCodeSource().getLocation().toURI());
        Path expectedRoot = (Files.isDirectory(codeFolder) ? codeFolder : codeFolder.getParent()).getParent();
        check("default database path is <project>/data/skillcircle.db",
                defaultPath.equals(expectedRoot.resolve("data").resolve("skillcircle.db").toAbsolutePath().normalize()));
        check("default database path is absolute (independent of the working folder)", defaultPath.isAbsolute());
        System.setProperty("skillcircle.db", tempFolder.resolve("override.db").toString());
        check("-Dskillcircle.db overrides the default path",
                DatabaseConnection.getDefaultPath().getFileName().toString().equals("override.db"));
        if (savedProperty == null) {
            System.clearProperty("skillcircle.db");
        } else {
            System.setProperty("skillcircle.db", savedProperty);
        }

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    private static boolean isRejectedStudent(Connection c, String studentId, String email) {
        try {
            addStudent(c, studentId, email);
            return false;
        } catch (SQLException e) {
            return true;
        }
    }

    // CS1 offers Java, then we try to delete the Java skill: must be refused
    private static boolean prepareRestrictCase(Connection c, long studentPk) throws SQLException {
        run(c, "INSERT INTO student_offered_skills (student_pk, skill_id) VALUES (" + studentPk + ",1)");
        return isRejected(c, "DELETE FROM skills WHERE id = 1");
    }
}
