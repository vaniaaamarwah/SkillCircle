package dao;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import database.DataAccessException;
import database.DatabaseConnection;
import model.ActivityEntry;
import model.PasswordHash;
import model.Skill;
import model.Student;

// DAO = Data Access Object. This is the ONLY class that writes SQL about students.
// Screens never see SQL; services call these methods and get Student objects back.
//
// Rules this class follows (good habits to mention in your viva):
//  * every method opens its own connection with try-with-resources, so it is always closed
//  * every SQL statement is a PreparedStatement with ? placeholders (no text glued into SQL),
//    which also protects against "SQL injection"
//  * a change that touches several tables (student + skills + availability) runs in ONE
//    transaction: it is saved completely or not at all
//  * a student is rebuilt as a complete Student object: profile, offered skills, wanted
//    skills, availability, learning mode and saved-student ids
//  * password data is stored and read separately (findPasswordHash) and is never put
//    inside a Student object, so it cannot leak into a table or a dialog by accident
public class StudentDAO {

    // ======================================================================
    // INSERT
    // ======================================================================

    // Saves a new student with their password hash, skills and availability.
    // Throws DataAccessException (friendly message) if the Student ID or email already exists.
    public void insert(Student student, PasswordHash password) {
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);          // start ONE transaction
            try {
                insertStudentRow(connection, student, password);
                long pk = requirePk(connection, student.getId());
                writeSkills(connection, pk, true, student.getOfferedSkills());
                writeSkills(connection, pk, false, student.getWantedSkills());
                writeAvailability(connection, pk, student);
                connection.commit();                  // everything worked
            } catch (SQLException e) {
                rollbackQuietly(connection);          // undo everything
                throw e;
            } catch (RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            }
        } catch (SQLException e) {
            throw translate("Could not save the student", e);
        }
    }

    // ======================================================================
    // FIND / LIST / COUNT
    // ======================================================================

    // Returns the full Student, or null if there is no such Student ID (case does not matter)
    public Student findByStudentId(String studentId) {
        return findOne(" WHERE student_id = ?", studentId);
    }

    // Returns the full Student, or null if there is no such email (case does not matter)
    public Student findByEmail(String email) {
        return findOne(" WHERE email = ?", email);
    }

    // Everyone, sorted by name
    public List<Student> findAll() {
        try (Connection connection = DatabaseConnection.open()) {
            return loadStudents(connection, "");
        } catch (SQLException e) {
            throw translate("Could not load the students", e);
        }
    }

    // Students whose name or Student ID contains the text (case does not matter).
    // An empty text returns everyone.
    public List<Student> search(String text) {
        String clean = (text == null) ? "" : text.trim();
        if (clean.isEmpty()) {
            return findAll();
        }
        // % and _ are wildcards in SQL LIKE. We escape them so a user who types "50%"
        // searches for the characters "50%" and not for "50 followed by anything".
        String pattern = "%" + clean.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        try (Connection connection = DatabaseConnection.open()) {
            return loadStudents(connection,
                    " WHERE full_name LIKE ? ESCAPE '\\' OR student_id LIKE ? ESCAPE '\\'",
                    pattern, pattern);
        } catch (SQLException e) {
            throw translate("Could not search the students", e);
        }
    }

    public int count() {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM students");
             ResultSet result = statement.executeQuery()) {
            return result.next() ? result.getInt(1) : 0;
        } catch (SQLException e) {
            throw translate("Could not count the students", e);
        }
    }

    public boolean existsStudentId(String studentId) {
        return exists("SELECT 1 FROM students WHERE student_id = ?", studentId);
    }

    public boolean existsEmail(String email) {
        return exists("SELECT 1 FROM students WHERE email = ?", email);
    }

    // The stored password data for one student, or null if the Student ID is unknown.
    // Only the login code should call this.
    public PasswordHash findPasswordHash(String studentId) {
        String sql = "SELECT password_salt, password_hash, hash_algorithm, hash_iterations"
                + " FROM students WHERE student_id = ?";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return null;
                }
                return new PasswordHash(result.getString(1), result.getString(2),
                        result.getString(3), result.getInt(4));
            }
        } catch (SQLException e) {
            throw translate("Could not read the login data", e);
        }
    }

    // ======================================================================
    // UPDATE
    // ======================================================================

    // Saves the student's profile, learning mode, skills and availability.
    // The Student ID and the password are NOT changed here.
    // (Saved-student bookmarks are owned by the saved-students feature, a later phase.)
    // Returns false if no student has that Student ID.
    public boolean update(Student student, ActivityEntry... notes) {
        String sql = "UPDATE students SET full_name = ?, email = ?, department = ?,"
                + " year_of_study = ?, bio = ?, learning_mode = ?, updated_at = datetime('now')"
                + " WHERE student_id = ?";
        try (Connection connection = DatabaseConnection.open()) {
            connection.setAutoCommit(false);
            try {
                int changed;
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setString(1, student.getFullName());
                    statement.setString(2, student.getEmail());
                    statement.setString(3, student.getDepartment());
                    statement.setString(4, student.getYearOfStudy());
                    statement.setString(5, student.getBio() == null ? "" : student.getBio());
                    statement.setString(6, student.getLearningMode());
                    statement.setString(7, student.getId());
                    changed = statement.executeUpdate();
                }
                if (changed == 0) {
                    connection.rollback();
                    return false;
                }
                long pk = requirePk(connection, student.getId());
                // replace the old skill and availability rows with the new ones
                deleteRows(connection, "DELETE FROM student_offered_skills WHERE student_pk = ?", pk);
                deleteRows(connection, "DELETE FROM student_wanted_skills WHERE student_pk = ?", pk);
                deleteRows(connection, "DELETE FROM student_availability WHERE student_pk = ?", pk);
                writeSkills(connection, pk, true, student.getOfferedSkills());
                writeSkills(connection, pk, false, student.getWantedSkills());
                writeAvailability(connection, pk, student);
                ActivityDAO.insertNotes(connection, notes);   // same transaction
                connection.commit();
                return true;
            } catch (SQLException e) {
                rollbackQuietly(connection);
                throw e;
            } catch (RuntimeException e) {
                rollbackQuietly(connection);
                throw e;
            }
        } catch (SQLException e) {
            throw translate("Could not update the student", e);
        }
    }

    // ======================================================================
    // DELETE
    // ======================================================================

    // Deletes the student. The database itself removes everything that belongs to them
    // (skills, availability, requests, sessions, bookmarks) because of ON DELETE CASCADE.
    // Returns false if no student has that Student ID.
    public boolean delete(String studentId) {
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                     "DELETE FROM students WHERE student_id = ?")) {
            statement.setString(1, studentId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate("Could not delete the student", e);
        }
    }

    // ======================================================================
    // Private helpers
    // ======================================================================

    // ----- Saved students -----

    // Returns false if the pair was already saved or one of the students does not exist
    public boolean addSaved(String ownerId, String savedId) {
        String sql = "INSERT OR IGNORE INTO saved_students (owner_pk, saved_pk, created_at)"
                + " SELECT o.id, s.id, ? FROM students o, students s"
                + " WHERE o.student_id = ? AND s.student_id = ? AND o.id <> s.id";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ActivityDAO.format(LocalDateTime.now()));
            statement.setString(2, ownerId);
            statement.setString(3, savedId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate("Could not save the student", e);
        }
    }

    public boolean removeSaved(String ownerId, String savedId) {
        String sql = "DELETE FROM saved_students WHERE owner_pk = (SELECT id FROM students WHERE student_id = ?)"
                + " AND saved_pk = (SELECT id FROM students WHERE student_id = ?)";
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, ownerId);
            statement.setString(2, savedId);
            return statement.executeUpdate() > 0;
        } catch (SQLException e) {
            throw translate("Could not remove the saved student", e);
        }
    }

    // Just the name (one small query), used by tables that show "who" for every row
    public String findFullName(String studentId) {
        if (studentId == null) {
            return null;
        }
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(
                     "SELECT full_name FROM students WHERE student_id = ?")) {
            statement.setString(1, studentId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getString(1) : null;
            }
        } catch (SQLException e) {
            throw translate("Could not read the student name", e);
        }
    }

    private Student findOne(String where, String value) {
        if (value == null) {
            return null;
        }
        try (Connection connection = DatabaseConnection.open()) {
            List<Student> found = loadStudents(connection, where, value);
            return found.isEmpty() ? null : found.get(0);
        } catch (SQLException e) {
            throw translate("Could not load the student", e);
        }
    }

    private boolean exists(String sql, String value) {
        if (value == null) {
            return false;
        }
        try (Connection connection = DatabaseConnection.open();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException e) {
            throw translate("Could not check the database", e);
        }
    }

    // Builds COMPLETE Student objects for every student matching the WHERE text.
    // Step 1 reads the student rows; steps 2-5 read the skills, availability and bookmarks
    // of exactly those students and attach them. (The same WHERE text is reused inside each
    // sub-query, so we never build a huge "IN (...)" list.)
    private List<Student> loadStudents(Connection connection, String where, String... params)
            throws SQLException {

        Map<Long, Student> byPk = new LinkedHashMap<>();
        Map<Long, String> modeByPk = new LinkedHashMap<>();

        String studentSql = "SELECT id, student_id, full_name, email, department, year_of_study,"
                + " bio, learning_mode, is_sample FROM students" + where
                + " ORDER BY full_name COLLATE NOCASE, student_id COLLATE NOCASE";
        try (PreparedStatement statement = connection.prepareStatement(studentSql)) {
            bind(statement, 1, params);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long pk = result.getLong("id");
                    Student loaded = new Student(
                            result.getString("student_id"), result.getString("full_name"),
                            result.getString("email"), result.getString("department"),
                            result.getString("year_of_study"), result.getString("bio"));
                    if (result.getInt("is_sample") == 1) {
                        loaded.markAsSample();
                    }
                    byPk.put(pk, loaded);
                    modeByPk.put(pk, result.getString("learning_mode"));
                }
            }
        }
        if (byPk.isEmpty()) {
            return new ArrayList<>();
        }

        String idsOfThese = "SELECT id FROM students" + where;

        // offered skills, in the order they were added (rowid = insertion order)
        String offeredSql = "SELECT t.student_pk, s.name FROM student_offered_skills t"
                + " JOIN skills s ON s.id = t.skill_id WHERE t.student_pk IN (" + idsOfThese
                + ") ORDER BY t.rowid";
        try (PreparedStatement statement = connection.prepareStatement(offeredSql)) {
            bind(statement, 1, params);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    byPk.get(result.getLong(1)).addOfferedSkill(new Skill(result.getString(2)));
                }
            }
        }

        String wantedSql = "SELECT t.student_pk, s.name FROM student_wanted_skills t"
                + " JOIN skills s ON s.id = t.skill_id WHERE t.student_pk IN (" + idsOfThese
                + ") ORDER BY t.rowid";
        try (PreparedStatement statement = connection.prepareStatement(wantedSql)) {
            bind(statement, 1, params);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    byPk.get(result.getLong(1)).addWantedSkill(new Skill(result.getString(2)));
                }
            }
        }

        // availability is stored as (day, slot) pairs; we rebuild the day list and slot list
        Map<Long, List<String>> days = new LinkedHashMap<>();
        Map<Long, List<String>> slots = new LinkedHashMap<>();
        String availabilitySql = "SELECT student_pk, day_of_week, time_slot FROM student_availability"
                + " WHERE student_pk IN (" + idsOfThese + ")";
        try (PreparedStatement statement = connection.prepareStatement(availabilitySql)) {
            bind(statement, 1, params);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    long pk = result.getLong(1);
                    addOnce(days, pk, result.getString(2));
                    addOnce(slots, pk, result.getString(3));
                }
            }
        }

        // saved (bookmarked) students, as Student IDs
        String savedSql = "SELECT v.owner_pk, s.student_id FROM saved_students v"
                + " JOIN students s ON s.id = v.saved_pk WHERE v.owner_pk IN (" + idsOfThese
                + ") ORDER BY v.created_at, v.saved_pk";
        try (PreparedStatement statement = connection.prepareStatement(savedSql)) {
            bind(statement, 1, params);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    byPk.get(result.getLong(1)).saveStudent(result.getString(2));
                }
            }
        }

        for (Map.Entry<Long, Student> entry : byPk.entrySet()) {
            long pk = entry.getKey();
            List<String> studentDays = days.getOrDefault(pk, new ArrayList<>());
            List<String> studentSlots = slots.getOrDefault(pk, new ArrayList<>());
            sortByOrder(studentDays, Student.WEEK_DAYS);
            sortByOrder(studentSlots, Student.TIME_SLOTS);
            entry.getValue().setAvailability(studentDays, studentSlots, modeByPk.get(pk));
        }
        return new ArrayList<>(byPk.values());
    }

    private static void addOnce(Map<Long, List<String>> map, long pk, String value) {
        List<String> list = map.computeIfAbsent(pk, key -> new ArrayList<>());
        if (!list.contains(value)) {
            list.add(value);
        }
    }

    // Sorts into the order of the fixed list (Monday..Sunday, Morning..Evening)
    private static void sortByOrder(List<String> items, String[] order) {
        List<String> reference = Arrays.asList(order);
        items.sort((a, b) -> {
            int indexA = reference.indexOf(a);
            int indexB = reference.indexOf(b);
            return Integer.compare(indexA < 0 ? order.length : indexA,
                                   indexB < 0 ? order.length : indexB);
        });
    }

    // Fills the ? placeholders in order: first parameter -> first ?, and so on.
    // Every statement above contains the WHERE text exactly once, so this is all we need.
    private static void bind(PreparedStatement statement, int firstIndex, String[] params)
            throws SQLException {
        for (int i = 0; i < params.length; i++) {
            statement.setString(firstIndex + i, params[i]);
        }
    }

    private void insertStudentRow(Connection connection, Student student, PasswordHash password)
            throws SQLException {
        String sql = "INSERT INTO students (student_id, full_name, email, password_salt,"
                + " password_hash, hash_algorithm, hash_iterations, department, year_of_study,"
                + " bio, learning_mode, is_sample) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, student.getId());
            statement.setString(2, student.getFullName());
            statement.setString(3, student.getEmail());
            statement.setString(4, password.getSalt());
            statement.setString(5, password.getHash());
            statement.setString(6, password.getAlgorithm());
            statement.setInt(7, password.getIterations());
            statement.setString(8, student.getDepartment());
            statement.setString(9, student.getYearOfStudy());
            statement.setString(10, student.getBio() == null ? "" : student.getBio());
            statement.setString(11, student.getLearningMode());
            statement.setInt(12, student.isSample() ? 1 : 0);
            statement.executeUpdate();
        }
    }

    // The internal database number (primary key) of a student
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

    private void writeSkills(Connection connection, long studentPk, boolean offered,
                             List<Skill> skills) throws SQLException {
        // the table name is one of two fixed words chosen by our own code (not user input)
        String table = offered ? "student_offered_skills" : "student_wanted_skills";
        String sql = "INSERT OR IGNORE INTO " + table + " (student_pk, skill_id) VALUES (?, ?)";
        for (Skill skill : skills) {
            long skillId = findOrCreateSkillId(connection, skill);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setLong(1, studentPk);
                statement.setLong(2, skillId);
                statement.executeUpdate();
            }
        }
    }

    // Each skill name is stored ONCE in the skills table and shared by every student
    private long findOrCreateSkillId(Connection connection, Skill skill) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT OR IGNORE INTO skills (name) VALUES (?)")) {
            statement.setString(1, skill.getName());
            statement.executeUpdate();
        }
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT id FROM skills WHERE name = ?")) {      // the column ignores upper/lower case
            statement.setString(1, skill.getName());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return result.getLong(1);
                }
            }
        }
        throw new SQLException("Could not store the skill: " + skill.getName());
    }

    // One row per (day, time slot) combination
    private void writeAvailability(Connection connection, long studentPk, Student student)
            throws SQLException {
        String sql = "INSERT OR IGNORE INTO student_availability (student_pk, day_of_week, time_slot)"
                + " VALUES (?, ?, ?)";
        for (String day : student.getAvailableDays()) {
            for (String slot : student.getTimeSlots()) {
                try (PreparedStatement statement = connection.prepareStatement(sql)) {
                    statement.setLong(1, studentPk);
                    statement.setString(2, day);
                    statement.setString(3, slot);
                    statement.executeUpdate();
                }
            }
        }
    }

    private void deleteRows(Connection connection, String sql, long studentPk) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, studentPk);
            statement.executeUpdate();
        }
    }

    private void rollbackQuietly(Connection connection) {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // the original error is more useful than a rollback error
        }
    }

    // Turns a technical SQL error into a DataAccessException with a message a user can read
    private DataAccessException translate(String action, SQLException e) {
        String text = String.valueOf(e.getMessage());
        if (text.contains("UNIQUE") && text.contains("students.student_id")) {
            return new DataAccessException("An account with this Student ID already exists.", e);
        }
        if (text.contains("UNIQUE") && text.contains("students.email")) {
            return new DataAccessException("An account with this email address already exists.", e);
        }
        return new DataAccessException(action + ": " + text, e);
    }
}
