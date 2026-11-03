package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import database.DataAccessException;
import database.DatabaseConnection;
import model.ActivityEntry;

// Database access for the Dashboard's "Recent Activity" lines (table activity_log).
// One row is stored for every student a line concerns.
//
// Other DAOs call insertNotes(...) with THEIR connection, so an activity line is saved in the
// same transaction as the change it describes: both succeed or both are undone.
public class ActivityDAO {

    private static final DateTimeFormatter STORED = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    static String format(LocalDateTime time) {
        return time.format(STORED);
    }

    // Used inside a transaction owned by another DAO
    static void insertNotes(Connection connection, ActivityEntry... notes) throws SQLException {
        if (notes == null) {
            return;
        }
        String sql = "INSERT INTO activity_log (student_pk, message, created_at)"
                + " SELECT id, ?, ? FROM students WHERE student_id = ?";
        for (ActivityEntry note : notes) {
            for (String studentId : note.getStudentIds()) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, note.getText());
                    statement.setString(2, format(note.getTime()));
                    statement.setString(3, studentId);
                    statement.executeUpdate();
                }
            }
        }
    }

    // Saves one note on its own
    public void add(ActivityEntry note) {
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                insertNotes(connection, note);
                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not save the activity: " + e.getMessage(), e);
        }
    }

    // The newest lines for one student, newest first
    public List<ActivityEntry> recent(String studentId, int limit) {
        String sql = "SELECT a.message, a.created_at FROM activity_log a"
                + " JOIN students s ON s.id = a.student_pk WHERE s.student_id = ?"
                + " ORDER BY a.id DESC LIMIT ?";
        List<ActivityEntry> list = new ArrayList<>();
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    list.add(new ActivityEntry(
                            LocalDateTime.parse(result.getString(2), STORED), result.getString(1)));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Could not load the recent activity: " + e.getMessage(), e);
        }
        return list;
    }
}
