package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import database.DataAccessException;
import database.DatabaseConnection;

// Reads and writes the small app_meta table: a list of (key, value) notes the
// program keeps about itself. Right now it remembers that demo data was added,
// so the demo seeder never adds it twice.
public class AppMetaDAO {

    // Returns the value for the key, or null if the key was never saved
    public String get(String key) {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT value FROM app_meta WHERE key = ?")) {
            statement.setString(1, key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not read the app settings: " + e.getMessage(), e);
        }
    }

    // Saves the value, replacing the old one if the key already exists
    public void set(String key, String value) {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT OR REPLACE INTO app_meta (key, value) VALUES (?, ?)")) {
            statement.setString(1, key);
            statement.setString(2, value);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the app settings: " + e.getMessage(), e);
        }
    }
}
