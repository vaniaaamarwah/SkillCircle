package service;

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

// Scheduling learning sessions for ACCEPTED exchanges, saved in the database (SessionDAO).
// Rules: only the two students of the exchange can schedule/change a session; the time must
// be in the future; nobody can be in two sessions at the same time; the student who did NOT
// make the latest proposal confirms it. Every change and its activity lines are ONE transaction.
public class SessionService {

    private final StudentDAO studentDAO;
    private final ExchangeRequestDAO requestDAO;
    private final SessionDAO sessionDAO;
    private final AppContext context;

    public SessionService(StudentDAO studentDAO, ExchangeRequestDAO requestDAO,
                          SessionDAO sessionDAO, AppContext context) {
        this.studentDAO = studentDAO;
        this.requestDAO = requestDAO;
        this.sessionDAO = sessionDAO;
        this.context = context;
    }

    // ================= Schedule =================

    public Session schedule(int requestId, String actingId, Skill skill,
                            LocalDate date, LocalTime time, String mode) {
        time = time == null ? null : time.withSecond(0).withNano(0);   // the database keeps HH:mm
        ExchangeRequest request = requestDAO.findById(requestId);
        if (request == null || request.getStatus() != RequestStatus.ACCEPTED) {
            throw new IllegalArgumentException("Sessions can only be scheduled for accepted exchanges.");
        }
        if (!request.involves(actingId)) {
            throw new IllegalArgumentException("You are not part of this exchange.");
        }
        if (skill == null) {
            throw new IllegalArgumentException("Please choose the skill to be taught.");
        }
        // The teacher is whoever offers the chosen skill in this exchange
        String teacherId;
        String learnerId;
        if (skill.equals(request.getSkillOffered())) {
            teacherId = request.getRequesterId();
            learnerId = request.getReceiverId();
        } else if (skill.equals(request.getSkillWanted())) {
            teacherId = request.getReceiverId();
            learnerId = request.getRequesterId();
        } else {
            throw new IllegalArgumentException("That skill is not part of this exchange.");
        }
        validateWhen(date, time, mode);
        checkConflicts(teacherId, learnerId, date, time, -1);

        String actor = canonicalId(request, actingId);
        Session draft = new Session(0, request.getId(), teacherId, learnerId, skill, date, time, mode, actor);
        String partnerId = draft.getPartnerId(actor);
        Session saved = sessionDAO.insert(draft,
                new ActivityEntry("You scheduled a " + skill + " session with " + nameOf(partnerId)
                        + " on " + date + ".", actor),
                new ActivityEntry(nameOf(actor) + " proposed a " + skill + " session on " + date
                        + ". Please confirm it.", partnerId));
        context.fireDataChanged();
        return saved;
    }

    // ================= Change =================

    public void reschedule(int sessionId, String actingId, LocalDate date, LocalTime time, String mode) {
        time = time == null ? null : time.withSecond(0).withNano(0);
        Session s = requireEditable(sessionId, actingId);
        validateWhen(date, time, mode);
        checkConflicts(s.getTeacherId(), s.getLearnerId(), date, time, s.getId());
        String actor = canonicalId(s, actingId);
        s.reschedule(date, time, mode, actor);
        String partnerId = s.getPartnerId(actor);
        save(s, new ActivityEntry("You moved the " + s.getSkill() + " session with " + nameOf(partnerId)
                        + " to " + date + ".", actor),
                new ActivityEntry(nameOf(actor) + " moved the " + s.getSkill() + " session to " + date
                        + ". Please confirm it.", partnerId));
    }

    // The OTHER student (not the one who proposed) confirms
    public void confirm(int sessionId, String actingId) {
        Session s = requireEditable(sessionId, actingId);
        if (s.getStatus() != Session.Status.PROPOSED) {
            throw new IllegalArgumentException("This session is already " + s.getStatus().getLabel() + ".");
        }
        if (s.getProposedById().equalsIgnoreCase(actingId)) {
            throw new IllegalArgumentException("You proposed this session. Your partner has to confirm it.");
        }
        String actor = canonicalId(s, actingId);
        s.setStatus(Session.Status.CONFIRMED);
        save(s, new ActivityEntry("You confirmed the " + s.getSkill() + " session with "
                        + nameOf(s.getPartnerId(actor)) + ".", actor),
                new ActivityEntry(nameOf(actor) + " confirmed your " + s.getSkill() + " session.",
                        s.getPartnerId(actor)));
    }

    public void cancel(int sessionId, String actingId) {
        Session s = requireEditable(sessionId, actingId);
        String actor = canonicalId(s, actingId);
        s.setStatus(Session.Status.CANCELLED);
        save(s, new ActivityEntry("You cancelled the " + s.getSkill() + " session with "
                        + nameOf(s.getPartnerId(actor)) + ".", actor),
                new ActivityEntry(nameOf(actor) + " cancelled your " + s.getSkill() + " session.",
                        s.getPartnerId(actor)));
    }

    private void save(Session s, ActivityEntry... notes) {
        if (!sessionDAO.update(s, notes)) {
            throw new IllegalArgumentException("That session no longer exists.");
        }
        context.fireDataChanged();
    }

    // ================= Lookups =================

    public List<Session> getUpcoming(String studentId) {
        List<Session> list = new ArrayList<>();
        for (Session s : sessionDAO.findForStudent(studentId)) {
            if (s.isUpcoming()) {
                list.add(s);
            }
        }
        list.sort((a, b) -> a.getStart().compareTo(b.getStart()));   // soonest first
        return list;
    }

    public List<Session> getPast(String studentId) {
        List<Session> list = new ArrayList<>();
        for (Session s : sessionDAO.findForStudent(studentId)) {
            if (!s.isUpcoming()) {
                list.add(s);
            }
        }
        list.sort((a, b) -> b.getStart().compareTo(a.getStart()));   // most recent first
        return list;
    }

    // ================= Helpers =================

    private Session requireEditable(int sessionId, String actingId) {
        Session s = sessionDAO.findById(sessionId);
        if (s == null) {
            throw new IllegalArgumentException("That session no longer exists.");
        }
        if (!s.involves(actingId)) {
            throw new IllegalArgumentException("You are not part of this session.");
        }
        if (!s.isUpcoming()) {
            throw new IllegalArgumentException("Only upcoming sessions can be changed.");
        }
        return s;
    }

    // The Student ID exactly as stored (the typed one may differ in upper/lower case)
    private String canonicalId(ExchangeRequest r, String actingId) {
        return r.getRequesterId().equalsIgnoreCase(actingId) ? r.getRequesterId() : r.getReceiverId();
    }

    private String canonicalId(Session s, String actingId) {
        return s.getTeacherId().equalsIgnoreCase(actingId) ? s.getTeacherId() : s.getLearnerId();
    }

    private void validateWhen(LocalDate date, LocalTime time, String mode) {
        if (date == null || time == null) {
            throw new IllegalArgumentException("Please enter a date and a time.");
        }
        if (!Student.MODE_ONLINE.equals(mode) && !Student.MODE_OFFLINE.equals(mode)) {
            throw new IllegalArgumentException("Please choose Online or Offline.");
        }
        if (LocalDateTime.of(date, time).isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("The session must be in the future.");
        }
    }

    // Nobody can have two active sessions starting at the same date and time
    private void checkConflicts(String teacherId, String learnerId, LocalDate date, LocalTime time, int ignoreId) {
        List<Session> candidates = new ArrayList<>(sessionDAO.findForStudent(teacherId));
        candidates.addAll(sessionDAO.findForStudent(learnerId));
        for (Session other : candidates) {
            if (other.getId() == ignoreId || other.getStatus() == Session.Status.CANCELLED) {
                continue;
            }
            if (other.getDate().equals(date) && other.getTime().equals(time.withSecond(0).withNano(0))) {
                throw new IllegalArgumentException(
                        "One of the two students already has a session at that date and time.");
            }
        }
    }

    private String nameOf(String studentId) {
        String name = studentDAO.findFullName(studentId);
        return name == null ? studentId : name;
    }
}
