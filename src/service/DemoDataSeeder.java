package service;

import dao.AppMetaDAO;
import dao.ExchangeRequestDAO;
import dao.SessionDAO;
import dao.StudentDAO;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import model.ActivityEntry;
import model.ExchangeRequest;
import model.RequestStatus;
import model.Session;
import model.Skill;
import model.Student;

// Adds 5 clearly labelled DEMO students (plus a few demo requests and sessions) to the
// database so you have something to try. It only runs when you start the program with the
// --seed-demo argument (run.ps1 does that for you; run.ps1 -NoDemo turns it off).
//
// REPEAT-SAFE:
//   * After the students are added it writes a note into the app_meta table
//     ("demo_data_seeded"); after the requests and sessions are added it writes a second note
//     ("demo_activity_seeded"). From then on seedIfNeeded() does nothing, so demo records are
//     never duplicated, and a demo student you delete does not come back.
//   * If a student with the same Student ID or email already exists (for example you
//     registered CS2024001 yourself), that demo student is skipped. Your account is
//     never overwritten, and the demo requests are not created.
//   * Demo students are saved with is_sample = 1, so screens can label them "Sample".
//
// All demo accounts use the password "demo123".
//
// Planned matches (rule-based, not AI):
//   Demo Student <-> Aarav   (Java <-> Graphic Design)
//   Demo Student <-> Priya   (Python <-> Public Speaking)
//   Rohan        <-> Sneha   (Guitar <-> Java)
// Rohan wants Java, which Demo offers, but Demo does not want Guitar,
// so Rohan is NOT a mutual match for Demo.
public class DemoDataSeeder {

    public static final String FLAG_KEY = "demo_data_seeded";
    public static final String ACTIVITY_FLAG_KEY = "demo_activity_seeded";
    public static final String DEMO_PASSWORD = "demo123";

    private final StudentDAO studentDAO;
    private final AppMetaDAO appMetaDAO;
    private final ExchangeRequestDAO requestDAO;
    private final SessionDAO sessionDAO;

    public DemoDataSeeder(StudentDAO studentDAO, AppMetaDAO appMetaDAO) {
        this(studentDAO, appMetaDAO, new ExchangeRequestDAO(), new SessionDAO());
    }

    public DemoDataSeeder(StudentDAO studentDAO, AppMetaDAO appMetaDAO,
                          ExchangeRequestDAO requestDAO, SessionDAO sessionDAO) {
        this.studentDAO = studentDAO;
        this.appMetaDAO = appMetaDAO;
        this.requestDAO = requestDAO;
        this.sessionDAO = sessionDAO;
    }

    public boolean alreadySeeded() {
        return appMetaDAO.get(FLAG_KEY) != null;
    }

    // Returns how many demo students were added (0 if demo data was added before)
    public int seedIfNeeded() {
        if (alreadySeeded()) {
            return 0;
        }
        int added = 0;
        for (Student demo : createDemoStudents()) {
            if (studentDAO.existsStudentId(demo.getId()) || studentDAO.existsEmail(demo.getEmail())) {
                continue;   // never touch a student that already exists
            }
            demo.markAsSample();
            // every demo student gets their OWN salt, so the stored hashes all differ
            studentDAO.insert(demo, PasswordHasher.hash(DEMO_PASSWORD));
            added++;
        }
        // Written after the students: if the program crashed halfway, the next run finishes the job.
        appMetaDAO.set(FLAG_KEY, LocalDateTime.now().toString());

        if (added == 5 && appMetaDAO.get(ACTIVITY_FLAG_KEY) == null) {
            seedRequestsAndSessions();
            appMetaDAO.set(ACTIVITY_FLAG_KEY, LocalDateTime.now().toString());
        }
        return added;
    }

    private void seedRequestsAndSessions() {
        String demo = "CS2024001";
        String aarav = "IT2024002";
        String priya = "EC2024003";
        String rohan = "ME2024004";
        String sneha = "CS2024005";

        // Aarav -> Demo (pending): Aarav teaches Graphic Design, wants Java
        requestDAO.insert(new ExchangeRequest(0, aarav, demo, new Skill("Graphic Design"), new Skill("Java"),
                RequestStatus.PENDING, LocalDate.now().minusDays(1)),
                new ActivityEntry("Aarav Mehta sent you an exchange request (Graphic Design for Java).", demo));

        // Priya -> Demo (accepted): Priya teaches Public Speaking, wants Python
        ExchangeRequest accepted = requestDAO.insert(new ExchangeRequest(0, priya, demo,
                new Skill("Public Speaking"), new Skill("Python"),
                RequestStatus.ACCEPTED, LocalDate.now().minusDays(9)),
                new ActivityEntry("Priya Nair accepted your exchange. A Public Speaking session is confirmed.", demo));

        // Rohan -> Sneha (pending): Rohan teaches Guitar, wants Java
        requestDAO.insert(new ExchangeRequest(0, rohan, sneha, new Skill("Guitar"), new Skill("Java"),
                RequestStatus.PENDING, LocalDate.now().minusDays(2)));

        // One finished and one upcoming session for the accepted exchange
        sessionDAO.insert(new Session(0, accepted.getId(), demo, priya, new Skill("Python"),
                LocalDate.now().minusDays(7), LocalTime.of(17, 0), Student.MODE_ONLINE,
                Session.Status.CONFIRMED, demo));
        sessionDAO.insert(new Session(0, accepted.getId(), priya, demo, new Skill("Public Speaking"),
                LocalDate.now().plusDays(3), LocalTime.of(17, 0), Student.MODE_ONLINE,
                Session.Status.CONFIRMED, priya));
    }

    private List<Student> createDemoStudents() {
        List<Student> list = new ArrayList<>();

        Student demo = new Student("CS2024001", "Demo Student", "demo@college.edu",
                "Computer Science (CSE)", "2nd Year",
                "Second-year CSE student who enjoys backend coding.");
        demo.addOfferedSkill(new Skill("Java"));
        demo.addOfferedSkill(new Skill("Python"));
        demo.addWantedSkill(new Skill("Graphic Design"));
        demo.addWantedSkill(new Skill("Public Speaking"));
        demo.setAvailability(listOf("Monday", "Wednesday", "Friday"),
                listOf(Student.TIME_SLOTS[2]), Student.MODE_ONLINE);
        list.add(demo);

        Student aarav = new Student("IT2024002", "Aarav Mehta", "aarav@college.edu",
                "Information Technology (IT)", "3rd Year",
                "Designs posters for college fests and wants to learn programming.");
        aarav.addOfferedSkill(new Skill("Graphic Design"));
        aarav.addWantedSkill(new Skill("Java"));
        aarav.setAvailability(listOf("Wednesday", "Saturday"),
                listOf(Student.TIME_SLOTS[1], Student.TIME_SLOTS[2]), Student.MODE_OFFLINE);
        list.add(aarav);

        Student priya = new Student("EC2024003", "Priya Nair", "priya@college.edu",
                "Electronics (ECE)", "2nd Year",
                "Debate club member who wants to try data scripting.");
        priya.addOfferedSkill(new Skill("Public Speaking"));
        priya.addWantedSkill(new Skill("Python"));
        priya.setAvailability(listOf("Tuesday", "Friday"),
                listOf(Student.TIME_SLOTS[2]), Student.MODE_ONLINE);
        list.add(priya);

        Student rohan = new Student("ME2024004", "Rohan Verma", "rohan@college.edu",
                "Mechanical (ME)", "4th Year",
                "Plays guitar in the college band and is curious about coding.");
        rohan.addOfferedSkill(new Skill("Guitar"));
        rohan.addWantedSkill(new Skill("Java"));
        rohan.setAvailability(listOf("Thursday", "Sunday"),
                listOf(Student.TIME_SLOTS[0]), Student.MODE_OFFLINE);
        list.add(rohan);

        Student sneha = new Student("CS2024005", "Sneha Iyer", "sneha@college.edu",
                "Computer Science (CSE)", "3rd Year",
                "Likes teaching Java and wants to learn guitar.");
        sneha.addOfferedSkill(new Skill("Java"));
        sneha.addWantedSkill(new Skill("Guitar"));
        sneha.setAvailability(listOf("Thursday", "Saturday"),
                listOf(Student.TIME_SLOTS[0], Student.TIME_SLOTS[1]), Student.MODE_ONLINE);
        list.add(sneha);

        return list;
    }

    private static List<String> listOf(String... items) {
        List<String> list = new ArrayList<>();
        for (String item : items) {
            list.add(item);
        }
        return list;
    }
}
