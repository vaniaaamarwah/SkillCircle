package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import database.DataAccessException;
import database.DatabaseConnection;
import model.ActivityEntry;
import model.Session;
import model.Skill;

// Database access for learning sessions (table sessions).
// The time of day is stored as text "HH:mm" in the column time_slot, the date as yyyy-MM-dd.
public class SessionDAO {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private static final String SELECT =
            "SELECT s.id, s.request_id, t.student_id AS teacher_id, l.student_id AS learner_id,"
            + " k.name AS skill, s.session_date, s.time_slot, s.mode, s.status,"
            + " p.student_id AS proposed_by"
            + " FROM sessions s"
            + " JOIN students t ON t.id = s.teacher_pk"
            + " JOIN students l ON l.id = s.learner_pk"
            + " JOIN skills k ON k.id = s.skill_id"
            + " LEFT JOIN students p ON p.id = s.proposed_by_pk";

    // Saves a NEW session (the id of 'draft' is ignored) and returns it with its real id
    public Session insert(Session draft, ActivityEntry... notes) {
        String sql = "INSERT INTO sessions (request_id, teacher_pk, learner_pk, skill_id, session_date,"
                + " time_slot, mode, status, proposed_by_pk, created_at, updated_at)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                String now = ActivityDAO.format(LocalDateTime.now());
                long newId;
                try (PreparedStatement statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
                    statement.setInt(1, draft.getRequestId());
                    statement.setLong(2, requirePk(connection, draft.getTeacherId()));
                    statement.setLong(3, requirePk(connection, draft.getLearnerId()));
                    statement.setLong(4, findOrCreateSkillId(connection, draft.getSkill()));
                    statement.setString(5, draft.getDate().toString());
                    statement.setString(6, draft.getTime().format(TIME));
                    statement.setString(7, draft.getMode());
                    statement.setString(8, draft.getStatus().name());
                    statement.setLong(9, requirePk(connection, draft.getProposedById()));
                    statement.setString(10, now);
                    statement.setString(11, now);
                    statement.executeUpdate();
                    try (ResultSet keys = statement.getGeneratedKeys()) {
                        if (!keys.next()) {
                            throw new SQLException("The new session id was not returned.");
                        }
                        newId = keys.getLong(1);
                    }
                }
                ActivityDAO.insertNotes(connection, notes);
                connection.commit();
                return new Session((int) newId, draft.getRequestId(), draft.getTeacherId(), draft.getLearnerId(),
                        draft.getSkill(), draft.getDate(), draft.getTime(), draft.getMode(),
                        draft.getStatus(), draft.getProposedById());
            } catch (SQLException | RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            }
        } catch (SQLException e) {
            throw translate("Could not save the session", e);
        }
    }

    // Saves the changeable parts of an existing session: date, time, mode, status, proposer
    public boolean update(Session session, ActivityEntry... notes) {
        String sql = "UPDATE sessions SET session_date = ?, time_slot = ?, mode = ?, status = ?,"
                + " proposed_by_pk = (SELECT id FROM students WHERE student_id = ?), updated_at = ?"
                + " WHERE id = ?";
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, session.getDate().toString());
                    statement.setString(2, session.getTime().format(TIME));
                    statement.setString(3, session.getMode());
                    statement.setString(4, session.getStatus().name());
                    statement.setString(5, session.getProposedById());
                    statement.setString(6, ActivityDAO.format(LocalDateTime.now()));
                    statement.setInt(7, session.getId());
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
            throw translate("Could not update the session", e);
        }
    }

    public Session findById(int id) {
        List<Session> found = query(" WHERE s.id = ?", true, String.valueOf(id));
        return found.isEmpty() ? null : found.get(0);
    }

    public List<Session> findAll() {
        return query("", false);
    }

    // Sessions where this student is the teacher or the learner
    public List<Session> findForStudent(String studentId) {
        return query(" WHERE t.student_id = ? OR l.student_id = ?", false, studentId, studentId);
    }

    private List<Session> query(String where, boolean numeric, String... params) {
        List<Session> list = new ArrayList<>();
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                     SELECT + where + " ORDER BY s.session_date, s.time_slot, s.id")) {
            for (int i = 0; i < params.length; i++) {
                if (numeric) {
                    statement.setInt(i + 1, Integer.parseInt(params[i]));
                } else {
                    statement.setString(i + 1, params[i]);
                }
            }
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String teacher = result.getString("teacher_id");
                    String proposedBy = result.getString("proposed_by");
                    list.add(new Session(result.getInt("id"), result.getInt("request_id"),
                            teacher, result.getString("learner_id"),
                            new Skill(result.getString("skill")),
                            parseDate(result.getString("session_date")),
                            parseTime(result.getString("time_slot")),
                            result.getString("mode"),
                            Session.Status.valueOf(result.getString("status")),
                            proposedBy == null ? teacher : proposedBy));
                }
            }
        } catch (SQLException e) {
            throw translate("Could not load the sessions", e);
        }
        return list;
    }

    private static LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text);
        } catch (DateTimeParseException e) {
            throw new DataAccessException("A stored session has an unreadable date: " + text, e);
        }
    }

    private static LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text);
        } catch (DateTimeParseException e) {
            throw new DataAccessException("A stored session has an unreadable time: " + text, e);
        }
    }

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
        if (text.contains("Student not found")) {
            return new DataAccessException("One of the students no longer exists.", e);
        }
        if (text.contains("FOREIGN KEY")) {
            return new DataAccessException("The exchange for this session no longer exists.", e);
        }
        return new DataAccessException(action + ": " + text, e);
    }
}
