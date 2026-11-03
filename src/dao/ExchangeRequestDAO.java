package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import database.DataAccessException;
import database.DatabaseConnection;
import model.ActivityEntry;
import model.ExchangeRequest;
import model.RequestStatus;
import model.Skill;

// Database access for exchange requests (table exchange_requests).
// Students and skills are stored as numeric keys in the table, but the rest of the program
// works with Student IDs and skill names, so this class translates in both directions.
//
// Every method that changes data also takes optional ActivityEntry "notes" that are saved in
// the SAME transaction (see ActivityDAO).
public class ExchangeRequestDAO {

    private static final String SELECT =
            "SELECT r.id, rq.student_id AS requester_id, rc.student_id AS receiver_id,"
            + " os.name AS offered, ws.name AS wanted, r.status, r.created_at"
            + " FROM exchange_requests r"
            + " JOIN students rq ON rq.id = r.requester_pk"
            + " JOIN students rc ON rc.id = r.receiver_pk"
            + " JOIN skills os ON os.id = r.offered_skill_id"
            + " JOIN skills ws ON ws.id = r.wanted_skill_id";

    // ----- Create -----

    // Saves a NEW request (the id of 'draft' is ignored) and returns it with its real id
    public ExchangeRequest insert(ExchangeRequest draft, ActivityEntry... notes) {
        String sql = "INSERT INTO exchange_requests (requester_pk, receiver_pk, offered_skill_id,"
                + " wanted_skill_id, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                long requesterPk = requirePk(connection, draft.getRequesterId());
                long receiverPk = requirePk(connection, draft.getReceiverId());
                long offeredId = findOrCreateSkillId(connection, draft.getSkillOffered());
                long wantedId = findOrCreateSkillId(connection, draft.getSkillWanted());
                String stamp = ActivityDAO.format(LocalDateTime.of(draft.getCreatedDate(), LocalTime.now().withNano(0)));

                long newId;
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setLong(1, requesterPk);
                    statement.setLong(2, receiverPk);
                    statement.setLong(3, offeredId);
                    statement.setLong(4, wantedId);
                    statement.setString(5, draft.getStatus().name());
                    statement.setString(6, stamp);
                    statement.setString(7, stamp);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("The new request id was not returned.");
                        }
                        newId = keys.getLong(1);
                    }
                }
                ActivityDAO.insertNotes(connection, notes);
                connection.commit();
                return new ExchangeRequest((int) newId, draft.getRequesterId(), draft.getReceiverId(),
                        draft.getSkillOffered(), draft.getSkillWanted(), draft.getStatus(), draft.getCreatedDate());
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            }
        } catch (SQLException e) {
            throw translate("Could not save the request", e);
        }
    }

    // ----- Change status -----

    // Changes the status ONLY if it is still 'from' (so two changes cannot overwrite each other).
    // Returns false if the request no longer has that status.
    public boolean updateStatus(int requestId, RequestStatus from, RequestStatus to, ActivityEntry... notes) {
        String sql = "UPDATE exchange_requests SET status = ?, updated_at = ? WHERE id = ? AND status = ?";
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, to.name());
                    statement.setString(2, ActivityDAO.format(LocalDateTime.now()));
                    statement.setInt(3, requestId);
                    statement.setString(4, from.name());
                    changed = statement.executeUpdate();
                }
                if (changed == 0) {
                    connection.rollback();
                    return false;
                }
                ActivityDAO.insertNotes(connection, notes);
                connection.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            }
        } catch (SQLException e) {
            throw translate("Could not update the request", e);
        }
    }

    // ----- Read -----

    public ExchangeRequest findById(int id) {
        List<ExchangeRequest> found = query(" WHERE r.id = ?", String.valueOf(id));
        return found.isEmpty() ? null : found.get(0);
    }

    // Newest first
    public List<ExchangeRequest> findAll() {
        return query("");
    }

    // Requests this student sent or received, newest first
    public List<ExchangeRequest> findForStudent(String studentId) {
        return query(" WHERE rq.student_id = ? OR rc.student_id = ?", studentId, studentId);
    }

    // Requests between two students, in either direction
    public List<ExchangeRequest> findBetween(String studentA, String studentB) {
        return query(" WHERE (rq.student_id = ? AND rc.student_id = ?)"
                + " OR (rq.student_id = ? AND rc.student_id = ?)", studentA, studentB, studentB, studentA);
    }

    private List<ExchangeRequest> query(String where, String... params) {
        List<ExchangeRequest> list = new ArrayList<>();
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(SELECT + where + " ORDER BY r.id DESC")) {
            for (int i = 0; i < params.length; i++) {
                if (where.startsWith(" WHERE r.id")) {
                    statement.setInt(i + 1, Integer.parseInt(params[i]));
                } else {
                    statement.setString(i + 1, params[i]);
                }
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    list.add(new ExchangeRequest(result.getInt("id"),
                            result.getString("requester_id"), result.getString("receiver_id"),
                            new Skill(result.getString("offered")), new Skill(result.getString("wanted")),
                            RequestStatus.valueOf(result.getString("status")),
                            LocalDate.parse(result.getString("created_at").substring(0, 10))));
                }
            }
        } catch (SQLException e) {
            throw translate("Could not load the requests", e);
        }
        return list;
    }

    // ----- Helpers -----

    private long requirePk(Connection connection, String studentId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM students WHERE student_id = ?")) {
            statement.setString(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getLong(1);
                }
            }
        }
        throw new SQLException("Student not found: " + studentId);
    }

    private long findOrCreateSkillId(Connection connection, Skill skill) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO skills (name) VALUES (?)")) {
            statement.setString(1, skill.getName());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT id FROM skills WHERE name = ?")) {
            statement.setString(1, skill.getName());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getLong(1);
                }
            }
        }
        throw new SQLException("Could not store the skill: " + skill.getName());
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // the original error is more useful
        }
    }

    private DataAccessException translate(String action, SQLException e) {
        String text = String.valueOf(e.getMessage());
        if (text.contains("UNIQUE") && text.contains("exchange_requests")) {
            return new DataAccessException("There is already an active request for this exchange.", e);
        }
        if (text.contains("Student not found")) {
            return new DataAccessException("One of the students no longer exists.", e);
        }
        return new DataAccessException(action + ": " + text, e);
    }
}
