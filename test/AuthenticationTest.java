import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import dao.AppMetaDAO;
import dao.DashboardDAO;
import dao.StudentDAO;
import database.DataAccessException;
import database.DatabaseConnection;
import database.DatabaseInitializer;
import model.DashboardStats;
import model.PasswordHash;
import model.Skill;
import model.Student;
import service.AppContext;
import service.AuthenticationService;
import service.DashboardService;
import service.DemoDataSeeder;
import service.PasswordHasher;
import service.StudentService;

// Phase 3 tests (no testing library needed). Every test uses a TEMPORARY database file,
// so your real data in the data folder is never touched. Run with run-tests.ps1.
//
// What is covered:
//   * password hashing          * StudentDAO (insert, find, update, delete, search, rollback)
//   * registration + login      * duplicate Student ID / email, wrong password
//   * the demo seeder           * Dashboard numbers from the database
//   * "restart" persistence, both simulated (new objects, same file) and REAL
//     (separate Java programs started one after another on the same database file)
public class AuthenticationTest {

    private static int passed = 0;
    private static int failed = 0;
    private static Path tempFolder;
    private static int dbCounter = 0;

    private static void check(String name, boolean condition) {
        System.out.println((condition ? "PASS  " : "FAIL  ") + name);
        if (condition) {
            passed++;
        } else {
            failed++;
        }
    }

    // Passes only if the code throws the expected exception type with the message part in it
    private static void checkThrows(String name, Class<? extends Throwable> type,
                                    String messagePart, Runnable code) {
        try {
            code.run();
            check(name + " (nothing was thrown)", false);
        } catch (Throwable t) {
            boolean ok = type.isInstance(t) && t.getMessage() != null
                    && t.getMessage().toLowerCase().contains(messagePart.toLowerCase());
            if (!ok) {
                System.out.println("      got " + t.getClass().getSimpleName() + ": " + t.getMessage());
            }
            check(name, ok);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0 && args[0].equals("--child")) {
            System.exit(runChild(args));
            return;
        }

        tempFolder = Files.createTempDirectory("skillcircle-phase3");

        hashingTests();
        daoTests();
        passwordStorageTests();
        authenticationTests();
        seederTests();
        dashboardTests();
        restartTests();

        deleteRecursively(tempFolder);
        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        System.exit(failed == 0 ? 0 : 1);
    }

    // ================= helpers =================

    // A brand-new empty database file; every test group gets its own
    private static Path freshDb() {
        dbCounter++;
        Path file = tempFolder.resolve("test" + dbCounter + ".db");
        DatabaseConnection.setDatabasePath(file);
        DatabaseInitializer.initialize();
        return file;
    }

    private static Student student(String id, String name, String email) {
        return new Student(id, name, email, Student.DEPARTMENTS[0], Student.YEARS[0], "bio of " + name);
    }

    private static List<String> list(String... items) {
        return new ArrayList<>(Arrays.asList(items));
    }

    private static List<String> names(List<Skill> skills) {
        List<String> result = new ArrayList<>();
        for (Skill skill : skills) {
            result.add(skill.getName());
        }
        return result;
    }

    private static void exec(String sql) {
        try (Connection c = DatabaseConnection.open(); Statement s = c.createStatement()) {
            s.execute(sql);
        } catch (SQLException e) {
            throw new RuntimeException("Test setup SQL failed: " + sql + " -> " + e.getMessage(), e);
        }
    }

    private static long scalar(String sql) {
        try (Connection c = DatabaseConnection.open();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getLong(1) : -1;
        } catch (SQLException e) {
            throw new RuntimeException("Test query failed: " + sql + " -> " + e.getMessage(), e);
        }
    }

    private static String text(String sql) {
        try (Connection c = DatabaseConnection.open();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery(sql)) {
            return r.next() ? r.getString(1) : null;
        } catch (SQLException e) {
            throw new RuntimeException("Test query failed: " + sql + " -> " + e.getMessage(), e);
        }
    }

    private static AuthenticationService newAuth(StudentDAO dao, AppContext context) {
        return new AuthenticationService(dao, context);
    }

    private static void deleteRecursively(Path folder) {
        try (Stream<Path> paths = Files.walk(folder)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // a leftover temp file is harmless
                }
            });
        } catch (IOException ignored) {
            // nothing to clean up
        }
    }

    // ================= 1. hashing =================

    private static void hashingTests() {
        System.out.println("--- password hashing ---");
        PasswordHash a = PasswordHasher.hash("Secret#123");
        check("hash is not the plain password", !a.getHash().equals("Secret#123")
                && !a.getHash().contains("Secret#123") && !a.getSalt().contains("Secret#123"));
        check("salt and hash are not empty", !a.getSalt().isEmpty() && !a.getHash().isEmpty());
        check("algorithm and iterations are recorded",
                a.getAlgorithm().equals("PBKDF2WithHmacSHA256") && a.getIterations() == 65536);
        check("correct password verifies", PasswordHasher.verify("Secret#123", a));
        check("wrong password is rejected", !PasswordHasher.verify("Secret#124", a));
        check("different upper/lower case is rejected", !PasswordHasher.verify("secret#123", a));
        check("empty password is rejected", !PasswordHasher.verify("", a));
        check("null password is rejected (no crash)", !PasswordHasher.verify(null, a));
        check("null stored hash is rejected (no crash)", !PasswordHasher.verify("x", null));

        PasswordHash b = PasswordHasher.hash("Secret#123");
        check("same password hashed twice -> different salts", !a.getSalt().equals(b.getSalt()));
        check("same password hashed twice -> different hashes", !a.getHash().equals(b.getHash()));
        check("both hashes still verify the same password",
                PasswordHasher.verify("Secret#123", a) && PasswordHasher.verify("Secret#123", b));

        PasswordHash fast = PasswordHasher.hash("abc123", 1000);
        check("verify uses the STORED iteration count (1000)",
                fast.getIterations() == 1000 && PasswordHasher.verify("abc123", fast));
        PasswordHash tampered = new PasswordHash(a.getSalt(), a.getHash(), a.getAlgorithm(), 1000);
        check("a wrong iteration count makes verification fail", !PasswordHasher.verify("Secret#123", tampered));
        PasswordHash unknownAlgorithm = new PasswordHash(a.getSalt(), a.getHash(), "MD5", 65536);
        check("an unknown algorithm is rejected", !PasswordHasher.verify("Secret#123", unknownAlgorithm));
        PasswordHash garbage = new PasswordHash("%%%not base64%%%", "%%%", PasswordHasher.ALGORITHM, 65536);
        check("corrupted stored data is rejected (no crash)", !PasswordHasher.verify("Secret#123", garbage));
        check("PasswordHash.toString() hides the hash",
                !a.toString().contains(a.getHash()) && !a.toString().contains(a.getSalt()));
        checkThrows("hashing a null password is refused", IllegalArgumentException.class,
                "required", () -> PasswordHasher.hash(null));
    }

    // ================= 2. StudentDAO =================

    private static void daoTests() {
        System.out.println("--- StudentDAO ---");
        freshDb();
        StudentDAO dao = new StudentDAO();

        check("empty database: count is 0 and findAll is empty", dao.count() == 0 && dao.findAll().isEmpty());

        Student asha = student("CS100", "Asha Rao", "asha@college.edu");
        asha.addOfferedSkill(new Skill("Java"));
        asha.addOfferedSkill(new Skill("Python"));
        asha.addWantedSkill(new Skill("Graphic Design"));
        asha.setAvailability(list("Friday", "Monday"),
                list(Student.TIME_SLOTS[2], Student.TIME_SLOTS[0]), Student.MODE_OFFLINE);
        dao.insert(asha, PasswordHasher.hash("secret1"));

        Student found = dao.findByStudentId("cs100");     // lower case on purpose
        check("insert + find by Student ID (case ignored)", found != null && found.getId().equals("CS100"));
        check("profile fields round-trip", found != null && found.getFullName().equals("Asha Rao")
                && found.getEmail().equals("asha@college.edu")
                && found.getDepartment().equals(Student.DEPARTMENTS[0])
                && found.getYearOfStudy().equals(Student.YEARS[0])
                && found.getBio().equals("bio of Asha Rao"));
        check("offered skills round-trip in the order they were added",
                found != null && names(found.getOfferedSkills()).equals(list("Java", "Python")));
        check("wanted skills round-trip",
                found != null && names(found.getWantedSkills()).equals(list("Graphic Design")));
        check("available days come back in week order",
                found != null && found.getAvailableDays().equals(list("Monday", "Friday")));
        check("time slots come back in day-time order",
                found != null && found.getTimeSlots().equals(list(Student.TIME_SLOTS[0], Student.TIME_SLOTS[2])));
        check("learning mode round-trips", found != null && found.getLearningMode().equals(Student.MODE_OFFLINE));
        check("saved ids start empty", found != null && found.getSavedStudentIds().isEmpty());
        check("find by email (case ignored)", dao.findByEmail("ASHA@College.EDU") != null);
        check("find unknown Student ID -> null", dao.findByStudentId("NOPE") == null);
        check("find unknown email -> null", dao.findByEmail("nobody@x.com") == null);
        check("find with null -> null (no crash)", dao.findByStudentId(null) == null && dao.findByEmail(null) == null);
        check("existsStudentId / existsEmail", dao.existsStudentId("Cs100") && !dao.existsStudentId("CS999")
                && dao.existsEmail("ASHA@COLLEGE.EDU") && !dao.existsEmail("zzz@x.com"));

        // a second student who uses the same skill with different upper/lower case
        Student ben = student("CS101", "Ben Thomas", "ben@college.edu");
        ben.addOfferedSkill(new Skill("java"));
        dao.insert(ben, PasswordHasher.hash("secret2"));
        check("skill names are stored once (case ignored)", scalar("SELECT COUNT(*) FROM skills WHERE name = 'Java'") == 1
                && scalar("SELECT COUNT(*) FROM skills") == 3);
        check("a shared skill comes back with its stored spelling",
                names(dao.findByStudentId("CS101").getOfferedSkills()).equals(list("Java")));

        // duplicates are refused by the database too
        long before = dao.count();
        checkThrows("insert with a duplicate Student ID (different case) is refused",
                DataAccessException.class, "Student ID",
                () -> dao.insert(student("cs100", "Other", "other@college.edu"), PasswordHasher.hash("secret3")));
        checkThrows("insert with a duplicate email (different case) is refused",
                DataAccessException.class, "email",
                () -> dao.insert(student("CS555", "Other", "ASHA@COLLEGE.EDU"), PasswordHasher.hash("secret3")));
        check("refused inserts leave the table unchanged", dao.count() == before);

        // a failure halfway through must undo everything (transaction)
        Student broken = student("CS777", "Broken One", "broken@college.edu");
        broken.addOfferedSkill(new Skill("   "));     // an empty skill name is invalid
        checkThrows("insert that fails halfway is refused", DataAccessException.class, "skill",
                () -> dao.insert(broken, PasswordHasher.hash("secret4")));
        check("...and the half-saved student row was rolled back",
                dao.findByStudentId("CS777") == null && dao.count() == before);

        // update
        Student edit = dao.findByStudentId("CS100");
        edit.setFullName("Asha R. Rao");
        edit.setEmail("asha.rao@college.edu");
        edit.setDepartment(Student.DEPARTMENTS[2]);
        edit.setYearOfStudy(Student.YEARS[3]);
        edit.setBio("Updated bio");
        edit.removeOfferedSkill(new Skill("Python"));
        edit.addOfferedSkill(new Skill("Guitar"));
        edit.addWantedSkill(new Skill("Public Speaking"));
        edit.setAvailability(list("Sunday"), list(Student.TIME_SLOTS[1]), Student.MODE_ONLINE);
        check("update returns true for an existing student", dao.update(edit));
        Student after = dao.findByStudentId("CS100");
        check("updated profile is saved", after.getFullName().equals("Asha R. Rao")
                && after.getEmail().equals("asha.rao@college.edu")
                && after.getDepartment().equals(Student.DEPARTMENTS[2])
                && after.getYearOfStudy().equals(Student.YEARS[3])
                && after.getBio().equals("Updated bio"));
        check("updated skills replace the old ones",
                names(after.getOfferedSkills()).equals(list("Java", "Guitar"))
                && names(after.getWantedSkills()).equals(list("Graphic Design", "Public Speaking")));
        check("updated availability and mode are saved", after.getAvailableDays().equals(list("Sunday"))
                && after.getTimeSlots().equals(list(Student.TIME_SLOTS[1]))
                && after.getLearningMode().equals(Student.MODE_ONLINE));
        check("update does not change the password",
                PasswordHasher.verify("secret1", dao.findPasswordHash("CS100")));
        check("update of an unknown Student ID returns false", !dao.update(student("GHOST", "Ghost", "g@x.com")));

        Student clash = dao.findByStudentId("CS101");
        clash.setEmail("asha.rao@college.edu");     // already used by CS100
        checkThrows("update to an email that is already used is refused", DataAccessException.class,
                "email", () -> dao.update(clash));
        check("...and the original data is unchanged",
                dao.findByStudentId("CS101").getEmail().equals("ben@college.edu"));

        // list, order, count
        Student cara = student("CS102", "cara Dutt", "cara@college.edu");
        dao.insert(cara, PasswordHasher.hash("secret5"));
        List<String> order = new ArrayList<>();
        for (Student s : dao.findAll()) {
            order.add(s.getFullName());
        }
        check("findAll is sorted by name (case ignored)",
                order.equals(list("Asha R. Rao", "Ben Thomas", "cara Dutt")));
        check("count matches findAll", dao.count() == 3);

        // search
        Student pct = student("PCT1", "50%off Pat", "pat@college.edu");
        Student under = student("UND_1", "Under Score", "under@college.edu");
        dao.insert(pct, PasswordHasher.hash("secret6"));
        dao.insert(under, PasswordHasher.hash("secret6"));
        check("search by part of a name (case ignored)", dao.search("BEN").size() == 1
                && dao.search("ben").get(0).getId().equals("CS101"));
        check("search by part of a Student ID", dao.search("cs10").size() == 3);
        check("empty / blank / null search returns everyone",
                dao.search("").size() == 5 && dao.search("   ").size() == 5 && dao.search(null).size() == 5);
        check("search with no match returns an empty list", dao.search("zzzzz").isEmpty());
        check("search treats % as a normal character",
                dao.search("50%").size() == 1 && dao.search("%").size() == 1);
        check("search treats _ as a normal character",
                dao.search("_").size() == 1 && dao.search("_").get(0).getId().equals("UND_1"));
        check("search results are complete Student objects",
                !dao.search("Ben").get(0).getOfferedSkills().isEmpty());
        check("a quote in the search text does not break SQL", dao.search("o'brien' OR '1'='1").isEmpty());

        // delete (cascade)
        long pk = scalar("SELECT id FROM students WHERE student_id = 'CS100'");
        exec("INSERT INTO saved_students (owner_pk, saved_pk) VALUES ("
                + scalar("SELECT id FROM students WHERE student_id = 'CS101'") + "," + pk + ")");
        check("saved ids are loaded", dao.findByStudentId("CS101").getSavedStudentIds().equals(list("CS100")));
        check("delete returns true", dao.delete("cs100"));
        check("deleted student is gone", dao.findByStudentId("CS100") == null);
        check("delete cascades to skills and availability",
                scalar("SELECT COUNT(*) FROM student_offered_skills WHERE student_pk = " + pk) == 0
                && scalar("SELECT COUNT(*) FROM student_availability WHERE student_pk = " + pk) == 0);
        check("delete cascades to bookmarks, and the other student loads without them",
                scalar("SELECT COUNT(*) FROM saved_students") == 0
                && dao.findByStudentId("CS101").getSavedStudentIds().isEmpty());
        check("delete of an unknown Student ID returns false", !dao.delete("CS100"));
        check("other students are untouched by the delete", dao.count() == 4);
    }

    // ================= 3. what is really stored =================

    private static void passwordStorageTests() {
        System.out.println("--- what the database stores ---");
        freshDb();
        StudentDAO dao = new StudentDAO();
        AppContext context = new AppContext();
        AuthenticationService auth = newAuth(dao, context);
        String plain = "MyPlainPw!9";
        auth.register("Eva One", "EV001", "eva1@college.edu", Student.DEPARTMENTS[0], Student.YEARS[0], plain);
        auth.register("Eva Two", "EV002", "eva2@college.edu", Student.DEPARTMENTS[0], Student.YEARS[0], plain);

        boolean plainFoundAnywhere = false;
        try (Connection c = DatabaseConnection.open();
             Statement s = c.createStatement();
             ResultSet r = s.executeQuery("SELECT * FROM students")) {
            int columns = r.getMetaData().getColumnCount();
            while (r.next()) {
                for (int i = 1; i <= columns; i++) {
                    String value = r.getString(i);
                    if (value != null && value.contains(plain)) {
                        plainFoundAnywhere = true;
                    }
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        check("the plain password is not stored in ANY column", !plainFoundAnywhere);
        check("stored algorithm is PBKDF2WithHmacSHA256",
                "PBKDF2WithHmacSHA256".equals(text("SELECT hash_algorithm FROM students WHERE student_id='EV001'")));
        check("stored iteration count is 65536",
                scalar("SELECT hash_iterations FROM students WHERE student_id='EV001'") == 65536);
        check("same password, two students -> different salts",
                !text("SELECT password_salt FROM students WHERE student_id='EV001'")
                        .equals(text("SELECT password_salt FROM students WHERE student_id='EV002'")));
        check("same password, two students -> different hashes",
                !text("SELECT password_hash FROM students WHERE student_id='EV001'")
                        .equals(text("SELECT password_hash FROM students WHERE student_id='EV002'")));
    }

    // ================= 4. registration and login =================

    private static void authenticationTests() {
        System.out.println("--- registration and login ---");
        freshDb();
        StudentDAO dao = new StudentDAO();
        AppContext context = new AppContext();
        AuthenticationService auth = newAuth(dao, context);
        String dept = Student.DEPARTMENTS[0];
        String year = Student.YEARS[0];

        AtomicInteger changes = new AtomicInteger();
        context.addChangeListener(changes::incrementAndGet);

        Student registered = auth.register("  Mia Khan  ", " MK001 ", " mia@college.edu ", dept, year, "pass123");
        check("register returns the saved student with trimmed values", registered.getId().equals("MK001")
                && registered.getFullName().equals("Mia Khan") && registered.getEmail().equals("mia@college.edu"));
        check("register saves the student in the database", dao.findByStudentId("MK001") != null
                && dao.findByStudentId("MK001").getBio().isEmpty());
        check("register tells listening screens that data changed", changes.get() == 1);
        check("registering does not log the user in", auth.getCurrentStudent() == null);

        // duplicates
        long count = dao.count();
        checkThrows("duplicate Student ID is refused", IllegalArgumentException.class, "Student ID MK001",
                () -> auth.register("Other", "MK001", "other@college.edu", dept, year, "pass123"));
        checkThrows("duplicate Student ID with different case is refused", IllegalArgumentException.class,
                "already exists", () -> auth.register("Other", "mk001", "other@college.edu", dept, year, "pass123"));
        checkThrows("duplicate email is refused", IllegalArgumentException.class, "email",
                () -> auth.register("Other", "MK002", "mia@college.edu", dept, year, "pass123"));
        checkThrows("duplicate email with different case is refused", IllegalArgumentException.class, "already exists",
                () -> auth.register("Other", "MK002", "MIA@College.EDU", dept, year, "pass123"));
        check("refused registrations created no rows", dao.count() == count);

        // validation
        checkThrows("empty name is refused", IllegalArgumentException.class, "Full name",
                () -> auth.register("  ", "MK010", "a@b.com", dept, year, "pass123"));
        checkThrows("empty Student ID is refused", IllegalArgumentException.class, "Student ID is required",
                () -> auth.register("Name", " ", "a@b.com", dept, year, "pass123"));
        checkThrows("too-short Student ID is refused", IllegalArgumentException.class, "Student ID must be",
                () -> auth.register("Name", "ab", "a@b.com", dept, year, "pass123"));
        checkThrows("Student ID with a space is refused", IllegalArgumentException.class, "no spaces",
                () -> auth.register("Name", "AB 123", "a@b.com", dept, year, "pass123"));
        checkThrows("invalid email is refused", IllegalArgumentException.class, "valid email",
                () -> auth.register("Name", "MK011", "not-an-email", dept, year, "pass123"));
        checkThrows("department outside the list is refused", IllegalArgumentException.class, "Department",
                () -> auth.register("Name", "MK012", "a@b.com", "Astrology", year, "pass123"));
        checkThrows("year outside the list is refused", IllegalArgumentException.class, "Year of Study",
                () -> auth.register("Name", "MK013", "a@b.com", dept, "9th Year", "pass123"));
        checkThrows("empty password is refused", IllegalArgumentException.class, "Password is required",
                () -> auth.register("Name", "MK014", "a@b.com", dept, year, ""));
        checkThrows("short password is refused", IllegalArgumentException.class, "at least 6",
                () -> auth.register("Name", "MK015", "a@b.com", dept, year, "abc12"));
        checkThrows("null password is refused", IllegalArgumentException.class, "Password is required",
                () -> auth.register("Name", "MK016", "a@b.com", dept, year, null));
        check("none of the invalid registrations created a row", dao.count() == count);

        // login
        Student me = auth.login("MK001", "pass123");
        check("login with the right ID and password works", me != null && me.getFullName().equals("Mia Khan"));
        check("login sets the current student", auth.getCurrentStudent() != null
                && context.getCurrentStudentId().equals("MK001"));
        check("the logged-in student is the full database profile", me != null && me.getBio() != null);

        auth.register("Noah Lee", "NL002", "noah@college.edu", dept, year, "noahpass");
        check("login with a wrong password fails", auth.login("NL002", "wrong") == null);
        check("a failed login does not change who is logged in", context.getCurrentStudentId().equals("MK001"));
        check("password is case sensitive", auth.login("NL002", "NOAHPASS") == null);
        check("unknown Student ID fails", auth.login("NOBODY", "pass123") == null);
        check("blank / null login values fail without crashing",
                auth.login("", "x") == null && auth.login("MK001", "") == null
                && auth.login(null, "x") == null && auth.login("MK001", null) == null
                && auth.login("   ", "   ") == null);
        check("a password cannot be used with another student's ID", auth.login("NL002", "pass123") == null);

        Student byLowerCase = auth.login("nl002", "noahpass");
        check("Student ID at login is case-insensitive and returns the stored ID",
                byLowerCase != null && byLowerCase.getId().equals("NL002"));
        check("successful login switches the current student", context.getCurrentStudentId().equals("NL002"));
        Student padded = auth.login("  NL002  ", "noahpass");
        check("spaces around the Student ID at login are ignored", padded != null);
        check("SQL text in the login box is just text", auth.login("' OR '1'='1", "' OR '1'='1") == null
                && auth.login("MK001' --", "x") == null);

        auth.logout();
        check("logout clears the current student", auth.getCurrentStudent() == null && context.getCurrentStudentId() == null);

        // StudentService (read side)
        StudentService students = new StudentService(dao, context);
        check("StudentService.findById", students.findById(" mk001 ") != null && students.findById("zzz") == null
                && students.findById(null) == null && students.findById("  ") == null);
        check("StudentService.getAllStudents / countStudents / search", students.getAllStudents().size() == 2
                && students.countStudents() == 2 && students.search("noah").size() == 1);
        auth.login("MK001", "pass123");
        exec("INSERT INTO skills (name) VALUES ('Chess')");
        exec("INSERT INTO student_offered_skills (student_pk, skill_id) VALUES ("
                + "(SELECT id FROM students WHERE student_id='MK001'), (SELECT id FROM skills WHERE name='Chess'))");
        Student reloaded = students.reloadCurrentStudent();
        check("reloadCurrentStudent picks up changes made in the database",
                reloaded != null && names(reloaded.getOfferedSkills()).equals(list("Chess"))
                && context.getCurrentStudent() == reloaded);
        check("isValidEmail", StudentService.isValidEmail("a@b.co") && !StudentService.isValidEmail("a@b")
                && !StudentService.isValidEmail("a b@c.com") && !StudentService.isValidEmail(null));
    }

    // ================= 5. demo seeder =================

    private static void seederTests() {
        System.out.println("--- demo data seeder ---");

        // first run on an empty database
        freshDb();
        StudentDAO dao = new StudentDAO();
        AppMetaDAO meta = new AppMetaDAO();
        DemoDataSeeder seeder = new DemoDataSeeder(dao, meta);
        check("before seeding: not seeded, no students", !seeder.alreadySeeded() && dao.count() == 0);
        check("first run adds 5 demo students", seeder.seedIfNeeded() == 5 && dao.count() == 5);
        check("the 'seeded' note is saved", seeder.alreadySeeded() && meta.get(DemoDataSeeder.FLAG_KEY) != null);

        AuthenticationService auth = newAuth(dao, new AppContext());
        Student demo = auth.login("CS2024001", "demo123");
        check("demo student can log in with demo123", demo != null && demo.getFullName().equals("Demo Student"));
        check("demo student's skills are as designed", demo != null
                && names(demo.getOfferedSkills()).equals(list("Java", "Python"))
                && names(demo.getWantedSkills()).equals(list("Graphic Design", "Public Speaking")));
        Student aarav = dao.findByStudentId("IT2024002");
        check("Aarav offers what Demo wants and wants what Demo offers (mutual)",
                names(aarav.getOfferedSkills()).equals(list("Graphic Design"))
                && names(aarav.getWantedSkills()).equals(list("Java")));
        Student rohan = dao.findByStudentId("ME2024004");
        check("Rohan wants Java but offers Guitar, which Demo does not want (not mutual)",
                names(rohan.getOfferedSkills()).equals(list("Guitar"))
                && !names(demo.getWantedSkills()).contains("Guitar")
                && names(rohan.getWantedSkills()).equals(list("Java")));
        check("all 5 demo students can log in",
                auth.login("IT2024002", "demo123") != null && auth.login("EC2024003", "demo123") != null
                && auth.login("ME2024004", "demo123") != null && auth.login("CS2024005", "demo123") != null);
        check("demo students have different salts although the password is the same",
                scalar("SELECT COUNT(DISTINCT password_salt) FROM students") == 5
                && scalar("SELECT COUNT(DISTINCT password_hash) FROM students") == 5);

        // repeat-safe
        check("second run adds nothing", seeder.seedIfNeeded() == 0 && dao.count() == 5);
        check("a new seeder object (after a restart) also adds nothing",
                new DemoDataSeeder(new StudentDAO(), new AppMetaDAO()).seedIfNeeded() == 0 && dao.count() == 5);
        dao.delete("EC2024003");
        check("a deleted demo student does NOT come back", seeder.seedIfNeeded() == 0 && dao.count() == 4
                && dao.findByStudentId("EC2024003") == null);

        // a real student already uses a demo Student ID / email
        freshDb();
        StudentDAO dao2 = new StudentDAO();
        AuthenticationService auth2 = newAuth(dao2, new AppContext());
        auth2.register("Real Person", "CS2024001", "real@college.edu", Student.DEPARTMENTS[0], Student.YEARS[0], "realpass");
        auth2.register("Mail Owner", "ZZ999", "aarav@college.edu", Student.DEPARTMENTS[0], Student.YEARS[0], "mailpass");
        DemoDataSeeder seeder2 = new DemoDataSeeder(dao2, new AppMetaDAO());
        int added = seeder2.seedIfNeeded();
        check("a demo student whose ID is taken is skipped, one whose email is taken is skipped, the other 3 are added",
                added == 3 && dao2.count() == 5);
        check("the real account with a demo Student ID is NOT overwritten",
                auth2.login("CS2024001", "realpass") != null && auth2.login("CS2024001", "demo123") == null
                && dao2.findByStudentId("CS2024001").getFullName().equals("Real Person"));
        check("the real account with a demo email is NOT overwritten",
                dao2.findByEmail("aarav@college.edu").getId().equals("ZZ999"));
    }

    // ================= 6. dashboard numbers =================

    private static void dashboardTests() {
        System.out.println("--- dashboard numbers (from the database) ---");
        freshDb();
        StudentDAO dao = new StudentDAO();
        new DemoDataSeeder(dao, new AppMetaDAO()).seedIfNeeded();
        // The demo data now also contains sample requests and sessions (Phase 5). This section
        // checks the counting itself, so it starts from a baseline without them.
        exec("DELETE FROM exchange_requests");   // sessions go with them (ON DELETE CASCADE)
        DashboardService service = new DashboardService(new DashboardDAO());

        DashboardStats start = service.getStats("CS2024001");
        check("total students comes from the database", start.getTotalStudents() == 5);
        check("Demo offers 2 and wants 2 skills", start.getSkillsOffered() == 2 && start.getSkillsWanted() == 2);
        check("no requests or sessions yet -> honest zeros",
                start.getPendingRequests() == 0 && start.getAcceptedExchanges() == 0 && start.getUpcomingSessions() == 0);
        check("another student's numbers differ (Aarav offers 1, wants 1)",
                service.getStats("IT2024002").getSkillsOffered() == 1);

        String demo = "(SELECT id FROM students WHERE student_id='CS2024001')";
        String aaravPk = "(SELECT id FROM students WHERE student_id='IT2024002')";
        String priya = "(SELECT id FROM students WHERE student_id='EC2024003')";
        String rohan = "(SELECT id FROM students WHERE student_id='ME2024004')";
        String java = "(SELECT id FROM skills WHERE name='Java')";
        String design = "(SELECT id FROM skills WHERE name='Graphic Design')";
        String python = "(SELECT id FROM skills WHERE name='Python')";
        String speaking = "(SELECT id FROM skills WHERE name='Public Speaking')";
        String guitar = "(SELECT id FROM skills WHERE name='Guitar')";
        String insert = "INSERT INTO exchange_requests (requester_pk, receiver_pk, offered_skill_id,"
                + " wanted_skill_id, status) VALUES ";

        exec(insert + "(" + demo + "," + aaravPk + "," + java + "," + design + ",'PENDING')");     // Demo -> Aarav
        exec(insert + "(" + priya + "," + demo + "," + speaking + "," + python + ",'ACCEPTED')");   // Priya -> Demo
        exec(insert + "(" + rohan + "," + demo + "," + guitar + "," + java + ",'REJECTED')");       // not counted
        exec(insert + "(" + rohan + "," + aaravPk + "," + guitar + "," + design + ",'PENDING')");   // not Demo's

        DashboardStats afterRequests = service.getStats("CS2024001");
        check("pending requests count both sent and received", afterRequests.getPendingRequests() == 1);
        check("accepted exchanges are counted", afterRequests.getAcceptedExchanges() == 1);
        check("rejected requests are not counted as pending or accepted",
                afterRequests.getPendingRequests() + afterRequests.getAcceptedExchanges() == 2);
        check("the receiver sees the request too, plus the one addressed only to them",
                service.getStats("IT2024002").getPendingRequests() == 2);
        check("a student with no requests sees 0", service.getStats("CS2024005").getPendingRequests() == 0);

        String requestId = "(SELECT id FROM exchange_requests WHERE status='ACCEPTED')";
        String future = LocalDate.now().plusDays(5).toString();
        String past = LocalDate.now().minusDays(5).toString();
        String sessionInsert = "INSERT INTO sessions (request_id, teacher_pk, learner_pk, skill_id,"
                + " session_date, time_slot, mode, status) VALUES (" + requestId + "," + priya + "," + demo + ","
                + speaking + ",'%s','Evening','Online','%s')";
        exec(String.format(sessionInsert, future, "CONFIRMED"));
        exec(String.format(sessionInsert, LocalDate.now().toString(), "PROPOSED"));
        exec(String.format(sessionInsert, past, "CONFIRMED"));
        exec(String.format(sessionInsert, future, "CANCELLED"));
        check("upcoming sessions = today or later, not cancelled (past and cancelled are excluded)",
                service.getStats("CS2024001").getUpcomingSessions() == 2);
        check("the teacher sees the same upcoming sessions", service.getStats("EC2024003").getUpcomingSessions() == 2);
        check("an uninvolved student sees none", service.getStats("CS2024005").getUpcomingSessions() == 0);

        DashboardStats unknown = service.getStats("NOBODY");
        check("unknown student: total still correct, personal numbers are 0", unknown.getTotalStudents() == 5
                && unknown.getSkillsOffered() == 0 && unknown.getPendingRequests() == 0);
        check("null Student ID does not crash", service.getStats(null).getTotalStudents() == 5);
    }

    // ================= 7. restart persistence =================

    private static void restartTests() throws Exception {
        System.out.println("--- restart persistence ---");

        // (a) simulated restart: throw away every object, keep only the database file
        Path file = freshDb();
        {
            StudentDAO dao = new StudentDAO();
            AppContext context = new AppContext();
            newAuth(dao, context).register("Ravi Shah", "RS001", "ravi@college.edu",
                    Student.DEPARTMENTS[1], Student.YEARS[2], "ravipass");
        }
        DatabaseConnection.setDatabasePath(file);          // "start the program again"
        DatabaseInitializer.initialize();
        StudentDAO newDao = new StudentDAO();
        AppContext newContext = new AppContext();
        AuthenticationService newAuth = newAuth(newDao, newContext);
        Student back = newAuth.login("RS001", "ravipass");
        check("simulated restart: the registered student can log in again", back != null
                && back.getFullName().equals("Ravi Shah") && back.getDepartment().equals(Student.DEPARTMENTS[1])
                && back.getYearOfStudy().equals(Student.YEARS[2]));
        check("simulated restart: wrong password still fails", newAuth.login("RS001", "wrong!") == null);
        check("simulated restart: schema version is unchanged", DatabaseInitializer.getSchemaVersion() == DatabaseInitializer.CURRENT_VERSION);

        // (b) REAL restart: separate Java programs, one after another, same database file
        Path realFile = tempFolder.resolve("real-restart.db");
        int register = runChildProcess("register", realFile, "RR001", "firstpass");
        check("separate program #1 registers a student", register == 0);
        int loginOk = runChildProcess("login", realFile, "RR001", "firstpass");
        check("separate program #2 (a new start) logs in with the same password", loginOk == 0);
        int loginLower = runChildProcess("login", realFile, "rr001", "firstpass");
        check("separate program #3 logs in with a lower-case Student ID", loginLower == 0);
        int loginBad = runChildProcess("login", realFile, "RR001", "wrongpass");
        check("separate program #4: wrong password is refused", loginBad == 2);
        int duplicate = runChildProcess("register", realFile, "rr001", "otherpass");
        check("separate program #5: registering the same Student ID again is refused", duplicate == 3);
        int stillOk = runChildProcess("login", realFile, "RR001", "firstpass");
        check("separate program #6: the original password still works after the refused duplicate", stillOk == 0);
        DatabaseConnection.setDatabasePath(realFile);
        check("the database file written by the child programs holds exactly one student",
                scalar("SELECT COUNT(*) FROM students") == 1);
    }

    // Starts "java AuthenticationTest --child <action> <db> <id> <password>" and returns its exit code
    private static int runChildProcess(String action, Path dbFile, String id, String password) throws Exception {
        String javaExe = Paths.get(System.getProperty("java.home"), "bin", "java").toString();
        List<String> command = new ArrayList<>();
        command.add(javaExe);
        command.add("-cp");
        command.add(System.getProperty("java.class.path"));
        command.add("AuthenticationTest");
        command.add("--child");
        command.add(action);
        command.add(dbFile.toString());
        command.add(id);
        command.add(password);
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
        Process process = builder.start();
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly();
            return -1;
        }
        return process.exitValue();
    }

    // The code that runs INSIDE the separate program.
    // Exit codes: 0 = it worked, 2 = login failed, 3 = a rule was broken, 4 = anything else
    private static int runChild(String[] args) {
        try {
            String action = args[1];
            DatabaseConnection.setDatabasePath(Paths.get(args[2]));
            DatabaseInitializer.initialize();
            AuthenticationService auth = new AuthenticationService(new StudentDAO(), new AppContext());
            if (action.equals("register")) {
                auth.register("Child User", args[3], args[3].toLowerCase() + "@college.edu",
                        Student.DEPARTMENTS[0], Student.YEARS[0], args[4]);
                return 0;
            }
            return auth.login(args[3], args[4]) != null ? 0 : 2;
        } catch (IllegalArgumentException e) {
            return 3;
        } catch (Exception e) {
            e.printStackTrace();
            return 4;
        }
    }
}
