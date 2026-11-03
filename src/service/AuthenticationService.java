package service;

import dao.StudentDAO;
import java.util.Arrays;
import model.PasswordHash;
import model.Student;

// The RULES for creating an account and for logging in. Screens call these methods;
// they never touch the data lists or the hashing code themselves.
//
// Accounts are saved in the SQLite database (through StudentDAO), so they survive a restart.
//
// Error handling convention used by all our services:
//   * a broken rule (empty name, duplicate email...)  -> IllegalArgumentException
//     with a friendly message that a screen can show directly in a dialog
//   * a database failure                              -> DataAccessException (also friendly)
//   * a wrong Student ID or password                  -> login() simply returns null
public class AuthenticationService {

    public static final int MIN_PASSWORD_LENGTH = 6;
    public static final int MAX_PASSWORD_LENGTH = 100;
    private static final int MIN_ID_LENGTH = 3;
    private static final int MAX_ID_LENGTH = 20;
    private static final int MAX_NAME_LENGTH = 60;
    private static final int MAX_EMAIL_LENGTH = 100;

    private final StudentDAO studentDAO;
    private final AppContext context;

    public AuthenticationService(StudentDAO studentDAO, AppContext context) {
        this.studentDAO = studentDAO;
        this.context = context;
    }

    // ================= Register =================

    // Creates a new account. The Student ID and the email must both be unused
    // (upper/lower case is ignored). The password is hashed before it is kept.
    // Returns the new Student (without any password data).
    public Student register(String fullName, String studentId, String email,
                            String department, String yearOfStudy, String password) {

        String name = clean(fullName);
        String id = clean(studentId);
        String mail = clean(email);

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Full name must be " + MAX_NAME_LENGTH
                    + " characters or fewer.");
        }
        if (id.isEmpty()) {
            throw new IllegalArgumentException("Student ID is required.");
        }
        if (id.length() < MIN_ID_LENGTH || id.length() > MAX_ID_LENGTH || containsSpace(id)) {
            throw new IllegalArgumentException("Student ID must be " + MIN_ID_LENGTH + " to "
                    + MAX_ID_LENGTH + " characters with no spaces.");
        }
        if (!StudentService.isValidEmail(mail) || mail.length() > MAX_EMAIL_LENGTH) {
            throw new IllegalArgumentException(
                    "Please enter a valid email address (example: name@college.edu).");
        }
        if (!Arrays.asList(Student.DEPARTMENTS).contains(department)) {
            throw new IllegalArgumentException("Please choose a Department from the list.");
        }
        if (!Arrays.asList(Student.YEARS).contains(yearOfStudy)) {
            throw new IllegalArgumentException("Please choose a Year of Study from the list.");
        }
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Password is required.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be at least "
                    + MIN_PASSWORD_LENGTH + " characters long.");
        }
        if (password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("Password must be " + MAX_PASSWORD_LENGTH
                    + " characters or fewer.");
        }

        if (studentDAO.existsStudentId(id)) {
            throw new IllegalArgumentException(
                    "An account with Student ID " + id + " already exists.");
        }
        if (studentDAO.existsEmail(mail)) {
            throw new IllegalArgumentException(
                    "An account with the email " + mail + " already exists.");
        }

        Student student = new Student(id, name, mail, department, yearOfStudy, "");
        // (The database also enforces both rules with UNIQUE constraints, so even two programs
        // running at once cannot break them.)
        studentDAO.insert(student, PasswordHasher.hash(password));
        context.fireDataChanged();
        return student;
    }

    // ================= Login / logout =================

    // Returns the logged-in Student if the Student ID and password are right, otherwise null.
    // The caller cannot tell whether the ID or the password was wrong; that is deliberate.
    public Student login(String studentId, String password) {
        if (studentId == null || password == null
                || studentId.trim().isEmpty() || password.isEmpty()) {
            return null;
        }
        String id = studentId.trim();

        PasswordHash stored = studentDAO.findPasswordHash(id);
        if (stored == null) {
            // Unknown ID: do the same hashing work anyway, so the answer takes equally long
            PasswordHasher.hash(password);
            return null;
        }
        if (!PasswordHasher.verify(password, stored)) {
            return null;
        }

        Student student = studentDAO.findByStudentId(id);   // full profile from the database
        if (student == null) {
            return null;
        }
        context.setCurrentStudent(student);
        return student;
    }

    public void logout() {
        context.setCurrentStudent(null);
    }

    public Student getCurrentStudent() {
        return context.getCurrentStudent();
    }

    // ================= Helpers =================

    private static String clean(String text) {
        return text == null ? "" : text.trim();
    }

    private static boolean containsSpace(String text) {
        for (int i = 0; i < text.length(); i++) {
            if (Character.isWhitespace(text.charAt(i))) {
                return true;
            }
        }
        return false;
    }
}
