package service;

import dao.ExchangeRequestDAO;
import dao.SessionDAO;
import dao.StudentDAO;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;
import model.ActivityEntry;
import model.ExchangeRequest;
import model.Session;
import model.Skill;
import model.Student;

// Everything about students: lookup, search, edit, remove, skills, availability, saved
// students and the Discover search. All data lives in the SQLite database (StudentDAO).
//
// Pattern used for every change:  load the student -> check the rules -> change the object
// -> save it with one DAO call (one database transaction) -> reload the logged-in student ->
// tell every screen to refresh. If saving fails, a DataAccessException is thrown and NO
// success message is ever shown.
public class StudentService {

    private static final int MAX_SKILL_LENGTH = 40;
    private static final int MAX_NAME_LENGTH = 60;
    private static final int MAX_BIO_LENGTH = 300;

    private final StudentDAO studentDAO;
    private final ExchangeRequestDAO requestDAO;
    private final SessionDAO sessionDAO;
    private final AppContext context;

    public StudentService(StudentDAO studentDAO, AppContext context) {
        this(studentDAO, new ExchangeRequestDAO(), new SessionDAO(), context);
    }

    public StudentService(StudentDAO studentDAO, ExchangeRequestDAO requestDAO,
                          SessionDAO sessionDAO, AppContext context) {
        this.studentDAO = studentDAO;
        this.requestDAO = requestDAO;
        this.sessionDAO = sessionDAO;
        this.context = context;
    }

    // ================= Validation helpers =================

    // Beginner-friendly email check: exactly one '@', text before it,
    // a '.' after it with text around it, and no spaces.
    public static boolean isValidEmail(String email) {
        if (email == null) {
            return false;
        }
        int atIndex = email.indexOf('@');
        int lastDotIndex = email.lastIndexOf('.');
        return atIndex > 0
                && atIndex == email.lastIndexOf('@')
                && lastDotIndex > atIndex + 1
                && lastDotIndex < email.length() - 1
                && !email.contains(" ");
    }

    // ================= Finding students =================

    // The student with this ID (upper/lower case ignored), or null
    public Student findById(String studentId) {
        if (studentId == null || studentId.trim().isEmpty()) {
            return null;
        }
        return studentDAO.findByStudentId(studentId.trim());
    }

    // All students, sorted by name
    public List<Student> getAllStudents() {
        return studentDAO.findAll();
    }

    // Search by name or Student ID. An empty query returns everyone.
    public List<Student> search(String query) {
        return studentDAO.search(query);
    }

    public int countStudents() {
        return studentDAO.count();
    }

    public Student getCurrentStudent() {
        return context.getCurrentStudent();
    }

    // Re-reads the logged-in student from the database, so the program holds the
    // newest skills and profile.
    public Student reloadCurrentStudent() {
        String id = context.getCurrentStudentId();
        if (id == null) {
            return null;
        }
        Student fresh = studentDAO.findByStudentId(id);
        context.setCurrentStudent(fresh);   // null if the student no longer exists
        return fresh;
    }

    // ================= Edit / remove =================

    public void updateStudent(String studentId, String fullName, String email,
                              String department, String year, String bio) {
        Student student = requireStudent(studentId);
        String name = fullName == null ? "" : fullName.trim();
        String mail = email == null ? "" : email.trim();
        String about = bio == null ? "" : bio.trim();

        if (name.isEmpty()) {
            throw new IllegalArgumentException("Full name is required.");
        }
        if (name.length() > MAX_NAME_LENGTH) {
            throw new IllegalArgumentException("Full name must be " + MAX_NAME_LENGTH
                    + " characters or fewer.");
        }
        if (!isValidEmail(mail)) {
            throw new IllegalArgumentException(
                    "Please enter a valid email address (example: name@college.edu).");
        }
        Student owner = studentDAO.findByEmail(mail);
        if (owner != null && !owner.getId().equalsIgnoreCase(student.getId())) {
            throw new IllegalArgumentException("That email is already used by " + owner.getFullName() + ".");
        }
        if (!Arrays.asList(Student.DEPARTMENTS).contains(department)) {
            throw new IllegalArgumentException("Please choose a Department from the list.");
        }
        if (!Arrays.asList(Student.YEARS).contains(year)) {
            throw new IllegalArgumentException("Please choose a Year of Study from the list.");
        }
        if (about.length() > MAX_BIO_LENGTH) {
            throw new IllegalArgumentException("Bio must be " + MAX_BIO_LENGTH + " characters or fewer.");
        }

        student.setFullName(name);
        student.setEmail(mail);
        student.setDepartment(department);
        student.setYearOfStudy(year);
        student.setBio(about);
        save(student);
    }

    // Safeguards: you cannot remove yourself, and you cannot remove a student who still has
    // a pending/accepted exchange or an upcoming session. Otherwise the student is deleted and
    // the database removes their old requests, sessions and every "saved" entry pointing to
    // them (ON DELETE CASCADE).
    public void removeStudent(String studentId, String actingStudentId) {
        Student student = requireStudent(studentId);
        if (student.getId().equalsIgnoreCase(actingStudentId)) {
            throw new IllegalArgumentException("You cannot remove the account you are logged in with.");
        }
        for (ExchangeRequest r : requestDAO.findForStudent(student.getId())) {
            if (r.getStatus().isActive()) {
                throw new IllegalArgumentException(student.getFullName()
                        + " still has a pending or accepted exchange request.\n"
                        + "Finish or cancel it first.");
            }
        }
        for (Session s : sessionDAO.findForStudent(student.getId())) {
            if (s.isUpcoming()) {
                throw new IllegalArgumentException(student.getFullName()
                        + " still has an upcoming session.\nCancel it first.");
            }
        }
        if (!studentDAO.delete(student.getId())) {
            throw new IllegalArgumentException("That student no longer exists.");
        }
        reloadCurrentStudent();
        context.fireDataChanged();
    }

    // ================= Skills =================

    public Skill addOfferedSkill(String studentId, String rawName) {
        Student student = requireStudent(studentId);
        Skill skill = makeSkill(rawName);
        if (student.wantsSkill(skill)) {
            throw new IllegalArgumentException("You already want to learn " + skill
                    + ". Remove it from your wanted skills first.");
        }
        if (!student.addOfferedSkill(skill)) {
            throw new IllegalArgumentException("You already offer " + skill + ".");
        }
        save(student, new ActivityEntry("You added " + skill + " to the skills you offer.", student.getId()));
        return skill;
    }

    public Skill addWantedSkill(String studentId, String rawName) {
        Student student = requireStudent(studentId);
        Skill skill = makeSkill(rawName);
        if (student.offersSkill(skill)) {
            throw new IllegalArgumentException("You already offer " + skill
                    + ". Remove it from your offered skills first.");
        }
        if (!student.addWantedSkill(skill)) {
            throw new IllegalArgumentException("You already want to learn " + skill + ".");
        }
        save(student, new ActivityEntry("You added " + skill + " to the skills you want to learn.", student.getId()));
        return skill;
    }

    public void removeOfferedSkill(String studentId, Skill skill) {
        Student student = requireStudent(studentId);
        if (skill == null || !student.removeOfferedSkill(skill)) {
            throw new IllegalArgumentException("Please select a skill to remove.");
        }
        save(student);
    }

    public void removeWantedSkill(String studentId, Skill skill) {
        Student student = requireStudent(studentId);
        if (skill == null || !student.removeWantedSkill(skill)) {
            throw new IllegalArgumentException("Please select a skill to remove.");
        }
        save(student);
    }

    private Skill makeSkill(String rawName) {
        String name = rawName == null ? "" : rawName.trim().replaceAll("\\s+", " ");
        if (name.isEmpty()) {
            throw new IllegalArgumentException("Please type a skill name first.");
        }
        if (name.length() > MAX_SKILL_LENGTH) {
            throw new IllegalArgumentException("A skill name must be " + MAX_SKILL_LENGTH
                    + " characters or fewer.");
        }
        return new Skill(name);
    }

    // ================= Availability =================

    public void setAvailability(String studentId, List<String> days, List<String> slots, String mode) {
        Student student = requireStudent(studentId);
        if (days == null || days.isEmpty()) {
            throw new IllegalArgumentException("Please tick at least one available day.");
        }
        if (slots == null || slots.isEmpty()) {
            throw new IllegalArgumentException("Please tick at least one preferred time slot.");
        }
        if (!Student.MODE_ONLINE.equals(mode) && !Student.MODE_OFFLINE.equals(mode)) {
            throw new IllegalArgumentException("Please choose Online or Offline.");
        }
        student.setAvailability(days, slots, mode);
        save(student);
    }

    // ================= Saved students =================

    public void saveStudent(String myId, String otherId) {
        Student me = requireStudent(myId);
        Student other = requireStudent(otherId);
        if (me.getId().equalsIgnoreCase(other.getId())) {
            throw new IllegalArgumentException("You cannot save your own profile.");
        }
        if (me.isSaved(other.getId())) {
            throw new IllegalArgumentException(other.getFullName() + " is already in your saved students.");
        }
        if (!studentDAO.addSaved(me.getId(), other.getId())) {
            throw new IllegalArgumentException(other.getFullName() + " is already in your saved students.");
        }
        afterChange();
    }

    public void unsaveStudent(String myId, String otherId) {
        Student me = requireStudent(myId);
        if (!me.isSaved(otherId) || !studentDAO.removeSaved(me.getId(), otherId)) {
            throw new IllegalArgumentException("That student is not in your saved list.");
        }
        afterChange();
    }

    // The saved profiles of this student (students that no longer exist are skipped)
    public List<Student> getSavedStudents(String myId) {
        List<Student> result = new ArrayList<>();
        Student me = studentDAO.findByStudentId(myId);
        if (me == null || me.getSavedStudentIds().isEmpty()) {
            return result;
        }
        for (Student s : studentDAO.findAll()) {
            if (me.isSaved(s.getId())) {
                result.add(s);
            }
        }
        return result;   // findAll() is already sorted by name
    }

    // ================= Discover =================

    // All students except me, matching the text (name or any skill) and, if offeredSkill is
    // not null/empty, offering exactly that skill.
    public List<Student> discover(String myId, String query, String offeredSkill) {
        String q = query == null ? "" : query.trim().toLowerCase();
        List<Student> result = new ArrayList<>();
        for (Student s : studentDAO.findAll()) {
            if (s.getId().equalsIgnoreCase(myId)) {
                continue;   // never show myself as a potential partner
            }
            if (offeredSkill != null && !offeredSkill.isEmpty() && !s.offersSkill(new Skill(offeredSkill))) {
                continue;
            }
            if (q.isEmpty() || s.getFullName().toLowerCase().contains(q)
                    || hasSkillContaining(s.getOfferedSkills(), q)
                    || hasSkillContaining(s.getWantedSkills(), q)) {
                result.add(s);
            }
        }
        return result;
    }

    // Names of every skill offered by students other than me (for the Discover filter box)
    public List<String> getOfferedSkillNamesOfOthers(String myId) {
        TreeSet<String> names = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        for (Student s : studentDAO.findAll()) {
            if (!s.getId().equalsIgnoreCase(myId)) {
                for (Skill skill : s.getOfferedSkills()) {
                    names.add(skill.getName());
                }
            }
        }
        return new ArrayList<>(names);
    }

    private static boolean hasSkillContaining(List<Skill> skills, String lowerQuery) {
        for (Skill skill : skills) {
            if (skill.getName().toLowerCase().contains(lowerQuery)) {
                return true;
            }
        }
        return false;
    }

    // ================= Helpers =================

    // One database transaction: the student's data plus optional activity lines
    private void save(Student student, ActivityEntry... notes) {
        if (!studentDAO.update(student, notes)) {
            throw new IllegalArgumentException("That student no longer exists.");
        }
        afterChange();
    }

    private void afterChange() {
        reloadCurrentStudent();
        context.fireDataChanged();
    }

    private Student requireStudent(String studentId) {
        Student student = findById(studentId);
        if (student == null) {
            throw new IllegalArgumentException("That student no longer exists.");
        }
        return student;
    }

    // Used by other classes that want an empty list instead of null
    public static <T> List<T> safe(List<T> list) {
        return list == null ? Collections.<T>emptyList() : list;
    }
}
