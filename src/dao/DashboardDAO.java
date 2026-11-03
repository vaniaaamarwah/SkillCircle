package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import database.DataAccessException;
import database.DatabaseConnection;
import model.DashboardStats;

// Counts the numbers shown on the Dashboard, straight from the database tables.
// Requests and sessions are not created by any screen yet (they arrive in later
// phases), so those counts are honestly 0 for now. The queries are already correct
// and will count real rows as soon as those screens exist.
public class DashboardDAO {

    // All six numbers for one logged-in student. Unknown student -> personal numbers are 0.
    public DashboardStats load(String studentId) {
        try (Connection connection = DatabaseConnection.open()) {

            int total = count(connection, "SELECT COUNT(*) FROM students");

            long pk = findPk(connection, studentId);
            if (pk < 0) {
                return new DashboardStats(total, 0, 0, 0, 0, 0);
            }

            int offered = count(connection,
                    "SELECT COUNT(*) FROM student_offered_skills WHERE student_pk = ?", pk);
            int wanted = count(connection,
                    "SELECT COUNT(*) FROM student_wanted_skills WHERE student_pk = ?", pk);

            // requests where I am the sender OR the receiver
            String requestSql = "SELECT COUNT(*) FROM exchange_requests"
                    + " WHERE status = ? AND (requester_pk = ? OR receiver_pk = ?)";
            int pending = countRequests(connection, requestSql, "PENDING", pk);
            int accepted = countRequests(connection, requestSql, "ACCEPTED", pk);

            // upcoming = today or later, and not cancelled (same rule as Session.isUpcoming)
            int upcoming;
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT COUNT(*) FROM sessions WHERE (session_date || ' ' || time_slot) >= ?"
                    + " AND status <> 'CANCELLED'"
                    + " AND (teacher_pk = ? OR learner_pk = ?)")) {
                statement.setString(1, java.time.LocalDateTime.now()
                        .truncatedTo(java.time.temporal.ChronoUnit.MINUTES).toString().replace('T', ' '));
                statement.setLong(2, pk);
                statement.setLong(3, pk);
                upcoming = readCount(statement);
            }

            return new DashboardStats(total, offered, wanted, pending, accepted, upcoming);

        } catch (SQLException e) {
            throw new DataAccessException("Could not load the dashboard numbers: " + e.getMessage(), e);
        }
    }

    private long findPk(Connection connection, String studentId) throws SQLException {
        if (studentId == null) {
            return -1;
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM students WHERE student_id = ?")) {
            statement.setString(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong(1) : -1;
            }
        }
    }

    private int count(Connection connection, String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            return readCount(statement);
        }
    }

    private int count(Connection connection, String sql, long value) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, value);
            return readCount(statement);
        }
    }

    private int countRequests(Connection connection, String sql, String status, long pk)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setLong(2, pk);
            statement.setLong(3, pk);
            return readCount(statement);
        }
    }

    private int readCount(PreparedStatement statement) throws SQLException {
        try (ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getInt(1) : 0;
        }
    }
}
