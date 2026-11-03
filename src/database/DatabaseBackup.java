package database;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

// Makes a safe copy of the database inside <project>\data\backups.
// It uses SQLite's "VACUUM INTO" command, which writes a clean, consistent copy
// even while the database is in use.
//
// TO RESTORE A BACKUP (by hand):
//   1. Close SkillCircle.
//   2. Copy data\skillcircle.db somewhere safe (it is your current data).
//   3. Copy the backup file you want over data\skillcircle.db.
//   4. Start SkillCircle again.
public final class DatabaseBackup {

    private DatabaseBackup() { }

    // Returns the path of the new backup file
    public static Path createBackup() {
        Path database = DatabaseConnection.getDatabasePath();
        if (!Files.exists(database)) {
            throw new DataAccessException("There is no database file to back up yet: " + database);
        }

        Path folder = database.getParent().resolve("backups");
        try {
            Files.createDirectories(folder);
        } catch (IOException e) {
            throw new DataAccessException("Could not create the backup folder: " + folder, e);
        }

        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        Path target = folder.resolve("skillcircle-" + stamp + ".db");
        int counter = 1;
        while (Files.exists(target)) {           // two backups in the same second
            target = folder.resolve("skillcircle-" + stamp + "-" + counter + ".db");
            counter++;
        }

        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement("VACUUM INTO ?")) {
            statement.setString(1, target.toString());
            statement.execute();
        } catch (SQLException e) {
            throw new DataAccessException("Could not create the backup: " + e.getMessage(), e);
        }
        return target;
    }
}
