package database;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

// The ONLY class that knows where the database file is and how to connect to it.
//
// WHERE IS THE DATABASE?  <project folder>\data\skillcircle.db
// The project folder is found from the location of the compiled classes (the "out"
// folder), NOT from the folder your terminal happens to be in. So you always get the
// same database file, even if you start the program from a different directory.
// To use another location, start Java with:  -Dskillcircle.db=C:\path\to\file.db
//
// Each call to open() returns a NEW connection. Callers must close it, which
// try-with-resources does automatically.
public final class DatabaseConnection {

    private static Path databasePath;   // chosen once, then reused

    private DatabaseConnection() { }    // static helper class: nobody creates an object

    // The path currently in use
    public static synchronized Path getDatabasePath() {
        if (databasePath == null) {
            databasePath = getDefaultPath();
        }
        return databasePath;
    }

    // Used by automated tests to point at a temporary database file
    public static synchronized void setDatabasePath(Path path) {
        databasePath = path.toAbsolutePath().normalize();
    }

    // The default location: <project>/data/skillcircle.db (or the -D override)
    public static Path getDefaultPath() {
        String override = System.getProperty("skillcircle.db");
        if (override != null && !override.trim().isEmpty()) {
            return Paths.get(override.trim()).toAbsolutePath().normalize();
        }
        return findProjectRoot().resolve("data").resolve("skillcircle.db");
    }

    // The compiled classes live in <project>\out, so the project folder is its parent.
    // (If the program is ever packaged as <project>\dist\app.jar, the parent of dist.)
    private static Path findProjectRoot() {
        try {
            URI location = DatabaseConnection.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI();
            Path codePath = Paths.get(location);
            Path classesFolder = Files.isDirectory(codePath) ? codePath : codePath.getParent();
            if (classesFolder != null && classesFolder.getParent() != null) {
                return classesFolder.getParent().toAbsolutePath().normalize();
            }
        } catch (Exception e) {
            // fall through to the fallback below
        }
        return Paths.get("").toAbsolutePath().normalize();   // fallback: current folder
    }

    // Opens a new connection with the safety settings every connection needs.
    public static Connection open() {
        Path path = getDatabasePath();
        Connection connection = null;
        try {
            Path folder = path.getParent();
            if (folder != null) {
                Files.createDirectories(folder);      // create the data folder if missing
            }
            connection = DriverManager.getConnection("jdbc:sqlite:" + path);

            // SQLite does NOT enforce foreign keys unless this is switched on,
            // and it must be done for EVERY connection.
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON");
                statement.execute("PRAGMA busy_timeout = 5000");   // wait up to 5 s if locked
            }
            return connection;

        } catch (SQLException e) {
            closeQuietly(connection);
            String text = String.valueOf(e.getMessage());
            if (text.contains("No suitable driver")) {
                throw new DataAccessException("The SQLite driver was not found. Start SkillCircle "
                        + "with run.ps1 so that lib\\sqlite-jdbc-3.53.4.0.jar is on the classpath.", e);
            }
            throw new DataAccessException("Could not open the database file: " + path, e);
        } catch (IOException e) {
            throw new DataAccessException("Could not create the data folder for: " + path, e);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException ignored) {
                // nothing useful can be done if closing fails
            }
        }
    }
}
