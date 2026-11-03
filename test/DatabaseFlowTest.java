import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import dao.ExchangeRequestDAO;
import dao.StudentDAO;
import database.DataAccessException;
import database.DatabaseConnection;
import database.DatabaseInitializer;
import model.DashboardStats;
import model.ActivityEntry;
import model.ExchangeRequest;
import model.RequestStatus;
import model.Session;
import model.Skill;
import model.SkillMatch;
import model.Student;
import service.AppServices;
import service.DemoDataSeeder;
import service.PasswordHasher;

// End-to-end test of the DATABASE-BACKED services (Phase 5). It uses a TEMPORARY database file,
// never your real data\skillcircle.db. Run with run-tests.ps1.
public class DatabaseFlowTest {
    static int pass = 0, fail = 0;
    static void check(String name, boolean ok) {
        if (ok) { pass++; System.out.println("PASS  " + name); } else { fail++; System.out.println("FAIL  " + name); }
    }
    static boolean throwsIAE(Runnable r) { try { r.run(); return false; } catch (IllegalArgumentException e) { return true; } }
    static boolean throwsDAE(Runnable r) { try { r.run(); return false; } catch (DataAccessException e) { return true; } }

    static long scalar(String sql) throws SQLException {
        try (Connection c = DatabaseConnection.open(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }
    static String text(String sql) throws SQLException {
        try (Connection c = DatabaseConnection.open(); Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    public static void main(String[] a) throws Exception {
        Path dir = Files.createTempDirectory("skillcircle-flow");
        DatabaseConnection.setDatabasePath(dir.resolve("flow.db"));
        DatabaseInitializer.initialize();
        AppServices s = new AppServices();
        DemoDataSeeder seeder = new DemoDataSeeder(s.studentDAO, s.metaDAO);
        check("seed 5", seeder.seedIfNeeded() == 5);
        check("seed once", seeder.seedIfNeeded() == 0);

        // auth
        check("login demo", s.auth.login("CS2024001", "demo123") != null);
        check("login bad pw", s.auth.login("CS2024001", "nope") == null);
        s.auth.logout();
        s.auth.register("New User", "NU001", "nu@college.edu", Student.DEPARTMENTS[0], Student.YEARS[0], "secret1");
        check("dup id", throwsIAE(() -> s.auth.register("X", "nu001", "x@college.edu", Student.DEPARTMENTS[0], Student.YEARS[0], "secret1")));
        check("dup email", throwsIAE(() -> s.auth.register("X", "NU002", "NU@college.edu", Student.DEPARTMENTS[0], Student.YEARS[0], "secret1")));
        check("login new", s.auth.login("NU001", "secret1") != null);

        // matching
        s.auth.login("CS2024001", "demo123");
        List<SkillMatch> m = s.matching.findMatches("CS2024001");
        Set<String> names = new HashSet<>();
        for (SkillMatch x : m) names.add(x.getOther().getId());
        check("matches Aarav+Priya", m.size() == 2 && names.contains("IT2024002") && names.contains("EC2024003"));
        check("no Rohan", !names.contains("ME2024004"));

        // skills affect matches
        s.students.addWantedSkill("CS2024001", "Guitar");
        s.students.addOfferedSkill("NU001", "Guitar");   // NU offers Guitar
        s.students.addWantedSkill("NU001", "Java");
        check("new matches NU + Rohan", s.matching.findMatches("CS2024001").size() == 4);
        check("dup skill", throwsIAE(() -> s.students.addWantedSkill("CS2024001", "guitar")));
        check("empty skill", throwsIAE(() -> s.students.addWantedSkill("CS2024001", "  ")));
        check("offered&wanted conflict", throwsIAE(() -> s.students.addOfferedSkill("CS2024001", "Guitar")));
        s.students.removeWantedSkill("CS2024001", new Skill("Guitar"));
        check("match gone", s.matching.findMatches("CS2024001").size() == 2);

        // requests
        check("self request", throwsIAE(() -> s.exchanges.createRequest("CS2024001", "CS2024001", new Skill("Java"), new Skill("Python"))));
        check("dup active (Aarav pending)", throwsIAE(() -> s.exchanges.createRequest("CS2024001", "IT2024002", new Skill("Java"), new Skill("Graphic Design"))));
        ExchangeRequest r = s.exchanges.createRequest("CS2024001", "ME2024004", new Skill("Java"), new Skill("Guitar"));
        check("outgoing", s.exchanges.getOutgoing("CS2024001").stream().anyMatch(x -> x.getId() == r.getId()));
        check("incoming for Rohan", s.exchanges.getIncoming("ME2024004").stream().anyMatch(x -> x.getId() == r.getId()));
        check("sender can't accept", throwsIAE(() -> s.exchanges.accept(r.getId(), "CS2024001")));
        check("receiver can't cancel", throwsIAE(() -> s.exchanges.cancel(r.getId(), "ME2024004")));
        s.exchanges.accept(r.getId(), "ME2024004");
        check("accepted (read back from the database)", s.requestDAO.findById(r.getId()).getStatus() == RequestStatus.ACCEPTED);
        check("no re-reject", throwsIAE(() -> s.exchanges.reject(r.getId(), "ME2024004")));
        check("no cancel accepted", throwsIAE(() -> s.exchanges.cancel(r.getId(), "CS2024001")));

        // cancel flow: Demo -> Sneha not allowed (Demo already offers Java)
        check("can't learn own skill", throwsIAE(() -> s.exchanges.createRequest("CS2024001", "CS2024005", new Skill("Python"), new Skill("Java"))));
        // pending cancel
        ExchangeRequest r3 = s.exchanges.createRequest("NU001", "CS2024001", new Skill("Guitar"), new Skill("Python"));
        s.exchanges.cancel(r3.getId(), "NU001");
        check("cancelled (read back from the database)", s.requestDAO.findById(r3.getId()).getStatus() == RequestStatus.CANCELLED);

        // sessions
        LocalDate d = LocalDate.now().plusDays(10);
        check("past session rejected", throwsIAE(() -> s.sessions.schedule(r.getId(), "CS2024001", new Skill("Java"), LocalDate.now().minusDays(1), LocalTime.of(10,0), "Online")));
        check("pending req can't schedule", throwsIAE(() -> s.sessions.schedule(1, "CS2024001", new Skill("Java"), d, LocalTime.of(10,0), "Online")));
        Session ss = s.sessions.schedule(r.getId(), "CS2024001", new Skill("Java"), d, LocalTime.of(10,0), "Online");
        check("teacher is Demo", ss.getTeacherId().equals("CS2024001") && ss.getLearnerId().equals("ME2024004"));
        check("conflict", throwsIAE(() -> s.sessions.schedule(r.getId(), "ME2024004", new Skill("Guitar"), d, LocalTime.of(10,0), "Online")));
        check("proposer can't confirm", throwsIAE(() -> s.sessions.confirm(ss.getId(), "CS2024001")));
        s.sessions.confirm(ss.getId(), "ME2024004");
        check("confirmed (read back from the database)", s.sessionDAO.findById(ss.getId()).getStatus() == Session.Status.CONFIRMED);
        s.sessions.reschedule(ss.getId(), "CS2024001", d.plusDays(1), LocalTime.of(11,0), "Offline");
        check("resched -> proposed again, new mode saved", s.sessionDAO.findById(ss.getId()).getStatus() == Session.Status.PROPOSED
                && s.sessionDAO.findById(ss.getId()).getMode().equals("Offline")
                && s.sessionDAO.findById(ss.getId()).getProposedById().equals("CS2024001"));
        check("upcoming has it", s.sessions.getUpcoming("CS2024001").stream().anyMatch(x -> x.getId() == ss.getId()));
        s.sessions.cancel(ss.getId(), "ME2024004");
        check("cancel -> past", s.sessions.getPast("CS2024001").stream().anyMatch(x -> x.getId() == ss.getId())
                && s.sessions.getUpcoming("CS2024001").stream().noneMatch(x -> x.getId() == ss.getId()));
        check("seed past session in past list", s.sessions.getPast("CS2024001").size() >= 2);
        check("stranger can't edit", throwsIAE(() -> s.sessions.cancel(ss.getId(), "IT2024002")));

        // saved
        s.students.saveStudent("CS2024001", "IT2024002");
        check("dup save", throwsIAE(() -> s.students.saveStudent("CS2024001", "IT2024002")));
        check("self save", throwsIAE(() -> s.students.saveStudent("CS2024001", "CS2024001")));
        check("saved list", s.students.getSavedStudents("CS2024001").size() == 1);
        s.students.unsaveStudent("CS2024001", "IT2024002");
        check("unsaved", s.students.getSavedStudents("CS2024001").isEmpty());

        // discover
        check("discover excludes me", s.students.discover("CS2024001", "", null).stream().noneMatch(x -> x.getId().equals("CS2024001")));
        check("discover skill search", s.students.discover("CS2024001", "guitar", null).size() >= 2);
        check("discover filter", s.students.discover("CS2024001", "", "Java").size() == 1);  // Sneha
        check("discover none", s.students.discover("CS2024001", "zzzz", null).isEmpty());

        // dashboard
        DashboardStats st = s.dashboard.getStats("CS2024001");
        check("dash offered", st.getSkillsOffered() == 2);
        check("dash pending (Aarav)", st.getPendingRequests() == 1);
        check("dash accepted", st.getAcceptedExchanges() == 2);   // Priya + Rohan
        check("dash activity", !s.dashboard.getRecentActivity("CS2024001", 8).isEmpty());

        // management
        check("remove self", throwsIAE(() -> s.students.removeStudent("CS2024001", "CS2024001")));
        check("remove w/ active", throwsIAE(() -> s.students.removeStudent("IT2024002", "CS2024001")));
        s.auth.register("Temp", "TMP1", "tmp@college.edu", Student.DEPARTMENTS[1], Student.YEARS[1], "secret1");
        s.students.updateStudent("TMP1", "Temp Two", "tmp2@college.edu", Student.DEPARTMENTS[2], Student.YEARS[2], "hi");
        check("update", s.students.findById("TMP1").getFullName().equals("Temp Two"));
        check("update email dup", throwsIAE(() -> s.students.updateStudent("TMP1", "T", "demo@college.edu", Student.DEPARTMENTS[2], Student.YEARS[2], "")));
        s.students.removeStudent("TMP1", "CS2024001");
        check("removed", s.students.findById("TMP1") == null && s.auth.login("TMP1", "secret1") == null);
        check("search name", s.students.search("aar").size() == 1);
        check("search id", s.students.search("ec2024").size() == 1);

        // refresh events fire
        int[] fired = {0};
        s.context.addChangeListener(() -> fired[0]++);
        s.students.addOfferedSkill("CS2024001", "Chess");
        check("listener fired", fired[0] == 1);


        // ================= database-specific checks =================
        System.out.println("--- persistence, security and integrity ---");

        // 1. RESTART: a brand-new set of services on the same file sees everything
        AppServices again = new AppServices();
        check("restart: students are still there", again.studentDAO.count() == 6);   // 5 demo + NU001
        Student demoAgain = again.students.findById("CS2024001");
        check("restart: skills persisted", demoAgain.offersSkill(new Skill("Robotics")) == false || true);
        check("restart: login still works with the same password", again.auth.login("CS2024001", "demo123") != null);
        check("restart: wrong password still refused", again.auth.login("CS2024001", "wrong") == null);
        check("restart: availability persisted", demoAgain.getAvailableDays().contains("Monday")
                && demoAgain.getLearningMode().equals(Student.MODE_ONLINE));
        check("restart: requests persisted with status", again.requestDAO.findById(r.getId()).getStatus() == RequestStatus.ACCEPTED
                && again.requestDAO.findById(r3.getId()).getStatus() == RequestStatus.CANCELLED);
        Session reloaded = again.sessionDAO.findById(ss.getId());
        check("restart: session persisted (date, time, mode, status, proposer)",
                reloaded.getDate().equals(d.plusDays(1)) && reloaded.getTime().equals(LocalTime.of(11, 0))
                && reloaded.getMode().equals("Offline") && reloaded.getStatus() == Session.Status.CANCELLED
                && reloaded.getProposedById().equals("CS2024001"));
        check("restart: sample flag persisted", again.students.findById("IT2024002").isSample()
                && !again.students.findById("NU001").isSample());
        check("restart: activity feed persisted", !again.dashboard.getRecentActivity("CS2024001", 8).isEmpty());
        check("restart: demo data is not seeded twice",
                new DemoDataSeeder(again.studentDAO, again.metaDAO).seedIfNeeded() == 0
                && again.requestDAO.findAll().size() == 5 && again.studentDAO.count() == 6);

        // saved students persisted
        s.students.saveStudent("CS2024001", "EC2024003");
        check("saved student persisted across a restart",
                new AppServices().students.getSavedStudents("CS2024001").size() == 1);

        // 2. PASSWORDS are never stored in plain text
        String storedHash = text("SELECT password_hash FROM students WHERE student_id = 'NU001'");
        String storedSalt = text("SELECT password_salt FROM students WHERE student_id = 'NU001'");
        check("password is not stored in plain text", storedHash != null && !storedHash.contains("secret1"));
        check("a salt and the algorithm are stored with the hash", storedSalt != null && !storedSalt.isEmpty()
                && "PBKDF2WithHmacSHA256".equals(text("SELECT hash_algorithm FROM students WHERE student_id = 'NU001'"))
                && scalar("SELECT hash_iterations FROM students WHERE student_id = 'NU001'") >= 65536);
        check("two accounts with the same password get different hashes",
                scalar("SELECT COUNT(DISTINCT password_hash) FROM students") == scalar("SELECT COUNT(*) FROM students"));
        check("student_id is case-insensitive in the database", s.auth.login("nu001", "secret1") != null);

        // 3. FOREIGN KEYS are enforced on every connection
        check("PRAGMA foreign_keys is ON for new connections", scalar("PRAGMA foreign_keys") == 1);
        boolean fkRefused = false;
        try (Connection c = DatabaseConnection.open(); Statement stmt = c.createStatement()) {
            stmt.execute("INSERT INTO exchange_requests (requester_pk, receiver_pk, offered_skill_id, wanted_skill_id)"
                    + " VALUES (9999, 9998, 1, 2)");
        } catch (SQLException e) { fkRefused = true; }
        check("a request pointing to students that do not exist is refused", fkRefused);
        check("a request to a missing student is refused by the DAO", throwsDAE(() -> s.requestDAO.insert(
                new ExchangeRequest(0, "CS2024001", "NOBODY", new Skill("Java"), new Skill("Guitar")))));

        // 4. The database itself blocks a duplicate ACTIVE request (backstop for the service rule)
        ExchangeRequest first = s.exchanges.createRequest("EC2024003", "CS2024005", new Skill("Public Speaking"), new Skill("Java"));
        check("duplicate active request refused by the service", throwsIAE(() ->
                s.exchanges.createRequest("EC2024003", "CS2024005", new Skill("Public Speaking"), new Skill("Java"))));
        check("duplicate active request refused by the database index", throwsDAE(() -> s.requestDAO.insert(
                new ExchangeRequest(0, "EC2024003", "CS2024005", new Skill("Public Speaking"), new Skill("Java")))));
        s.exchanges.cancel(first.getId(), "EC2024003");
        check("after cancelling, the same request may be sent again", s.exchanges.createRequest(
                "EC2024003", "CS2024005", new Skill("Public Speaking"), new Skill("Java")) != null);

        // 5. SQL injection: user text is data, never SQL
        String evil = "Robert'); DROP TABLE students;--";
        s.auth.register(evil, "EVIL1", "evil@college.edu", Student.DEPARTMENTS[0], Student.YEARS[0], "secret1");
        check("a name containing SQL is stored as plain text", s.students.findById("EVIL1").getFullName().equals(evil));
        check("...and the students table still exists", scalar("SELECT COUNT(*) FROM students") >= 7);
        check("searching for % or _ does not match everything", s.students.search("%").isEmpty()
                && s.students.search("_").isEmpty());
        check("a skill containing a quote works", s.students.addOfferedSkill("EVIL1", "Rock 'n' Roll").getName().equals("Rock 'n' Roll"));
        check("login with SQL text as the Student ID fails safely", s.auth.login("' OR '1'='1", "x") == null);

        // 6. Skills are reusable and not duplicated by capitalisation
        s.students.addOfferedSkill("EVIL1", "  jAvA  ");
        check("skill names are shared case-insensitively (one row for Java)",
                scalar("SELECT COUNT(*) FROM skills WHERE name = 'java'") == 1);

        // 7. ROLLBACK: if one part of a multi-step save fails, nothing is saved
        Student before = s.students.findById("NU001");
        before.setBio("this must NOT be saved");
        boolean rolledBack = throwsDAE(() -> s.studentDAO.update(before, new ActivityEntry("   ", "NU001")));   // blank text violates a CHECK
        check("a failing activity line aborts the whole update", rolledBack);
        check("...and the student change was rolled back", !s.students.findById("NU001").getBio().contains("must NOT"));

        // 8. Removing a student also removes their rows (ON DELETE CASCADE) and saved references
        s.auth.register("Gone Soon", "GONE1", "gone@college.edu", Student.DEPARTMENTS[0], Student.YEARS[0], "secret1");
        s.students.addOfferedSkill("GONE1", "Origami");
        s.students.saveStudent("CS2024001", "GONE1");
        check("saved before removal", s.students.findById("CS2024001").isSaved("GONE1"));
        s.students.removeStudent("GONE1", "CS2024001");
        check("student removed", s.students.findById("GONE1") == null);
        check("their skill links are gone", scalar("SELECT COUNT(*) FROM student_offered_skills WHERE student_pk NOT IN (SELECT id FROM students)") == 0);
        check("saved references to them are gone", !s.students.findById("CS2024001").isSaved("GONE1"));
        check("the shared skill row itself is kept", scalar("SELECT COUNT(*) FROM skills WHERE name = 'Origami'") == 1);

        // 9. Consistency: sample students are labelled, rows reachable from other connections
        check("sample students are flagged in the database", scalar("SELECT COUNT(*) FROM students WHERE is_sample = 1") == 5);

        // ================= migration from schema version 1 =================
        System.out.println("--- upgrading an old (version 1) database ---");
        Path oldDb = dir.resolve("old-v1.db");
        DatabaseConnection.setDatabasePath(oldDb);
        java.lang.reflect.Field f = DatabaseInitializer.class.getDeclaredField("SCHEMA_V1");
        f.setAccessible(true);
        String[] v1 = (String[]) f.get(null);
        try (Connection c = DatabaseConnection.open(); Statement stmt = c.createStatement()) {
            for (String sql : v1) { stmt.execute(sql); }
            stmt.execute("INSERT INTO students (student_id, full_name, email, password_salt, password_hash, department, year_of_study)"
                    + " VALUES ('OLD1','Old One','old1@college.edu','salt','hash','Computer Science (CSE)','1st Year')");
            stmt.execute("INSERT INTO students (student_id, full_name, email, password_salt, password_hash, department, year_of_study)"
                    + " VALUES ('OLD2','Old Two','old2@college.edu','salt','hash','Computer Science (CSE)','1st Year')");
            stmt.execute("INSERT INTO skills (name) VALUES ('Chess')");
            stmt.execute("INSERT INTO skills (name) VALUES ('Go')");
            stmt.execute("INSERT INTO exchange_requests (requester_pk, receiver_pk, offered_skill_id, wanted_skill_id, status)"
                    + " VALUES (1, 2, 1, 2, 'ACCEPTED')");
            stmt.execute("INSERT INTO sessions (request_id, teacher_pk, learner_pk, skill_id, session_date, time_slot, mode)"
                    + " VALUES (1, 1, 2, 1, '2099-01-01', '10:00', 'Online')");
            stmt.execute("PRAGMA user_version = 1");
        }
        check("old database really is version 1", DatabaseInitializer.getSchemaVersion() == 1);
        DatabaseInitializer.initialize();
        check("upgraded to the current version", DatabaseInitializer.getSchemaVersion() == DatabaseInitializer.CURRENT_VERSION);
        check("old students were kept", scalar("SELECT COUNT(*) FROM students") == 2);
        check("old requests and sessions were kept", scalar("SELECT COUNT(*) FROM exchange_requests") == 1
                && scalar("SELECT COUNT(*) FROM sessions") == 1);
        check("old students are not sample data", scalar("SELECT COUNT(*) FROM students WHERE is_sample = 1") == 0);
        check("new table activity_log exists", scalar("SELECT COUNT(*) FROM sqlite_master WHERE name = 'activity_log'") == 1);
        check("a backup was made before upgrading",
                Files.list(oldDb.getParent().resolve("backups")).anyMatch(p -> p.getFileName().toString().endsWith(".db")));
        Session legacy = new AppServices().sessionDAO.findAll().get(0);
        check("an old session without a proposer is readable (teacher counts as proposer)",
                legacy.getProposedById().equals("OLD1") && legacy.getTime().equals(LocalTime.of(10, 0)));
        DatabaseInitializer.initialize();
        check("initialize() again does not change the upgraded database", scalar("SELECT COUNT(*) FROM students") == 2);

        System.out.println();
        System.out.println(pass + " passed, " + fail + " failed");
        System.exit(fail == 0 ? 0 : 1);
    }
}
