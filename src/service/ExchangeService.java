package service;

import dao.ExchangeRequestDAO;
import dao.StudentDAO;
import java.util.ArrayList;
import java.util.List;
import model.ActivityEntry;
import model.ExchangeRequest;
import model.RequestStatus;
import model.Skill;
import model.Student;

// Creating and answering exchange requests. The status rules live in RequestStatus;
// this class adds the "who is allowed to do it" rules. Requests are saved in the database
// (ExchangeRequestDAO); each change and its activity lines are ONE transaction.
public class ExchangeService {

    private final StudentDAO studentDAO;
    private final ExchangeRequestDAO requestDAO;
    private final AppContext context;

    public ExchangeService(StudentDAO studentDAO, ExchangeRequestDAO requestDAO, AppContext context) {
        this.studentDAO = studentDAO;
        this.requestDAO = requestDAO;
        this.context = context;
    }

    // ================= Create =================

    // requester TEACHES skillOffered and wants to LEARN skillWanted from receiver
    public ExchangeRequest createRequest(String requesterId, String receiverId,
                                         Skill skillOffered, Skill skillWanted) {
        Student requester = studentDAO.findByStudentId(requesterId);
        Student receiver = studentDAO.findByStudentId(receiverId);
        if (requester == null || receiver == null) {
            throw new IllegalArgumentException("Both students must exist.");
        }
        if (requester.getId().equalsIgnoreCase(receiver.getId())) {
            throw new IllegalArgumentException("You cannot send an exchange request to yourself.");
        }
        if (skillOffered == null || skillWanted == null) {
            throw new IllegalArgumentException("Please choose both the skill you will teach and the skill you want to learn.");
        }
        if (skillOffered.equals(skillWanted)) {
            throw new IllegalArgumentException("The two skills of an exchange must be different.");
        }
        if (!requester.offersSkill(skillOffered)) {
            throw new IllegalArgumentException("You do not offer " + skillOffered
                    + ". Add it in My Skills first.");
        }
        if (requester.offersSkill(skillWanted)) {
            throw new IllegalArgumentException("You already offer " + skillWanted
                    + ", so you cannot ask to learn it.");
        }
        if (!receiver.offersSkill(skillWanted)) {
            throw new IllegalArgumentException(receiver.getFullName() + " does not offer " + skillWanted + ".");
        }
        for (ExchangeRequest r : requestDAO.findBetween(requester.getId(), receiver.getId())) {
            if (r.getStatus().isActive()
                    && isSameSwap(r, requester.getId(), receiver.getId(), skillOffered, skillWanted)) {
                throw new IllegalArgumentException("There is already a "
                        + r.getStatus().getLabel().toLowerCase()
                        + " request for this exchange with " + receiver.getFullName() + ".");
            }
        }

        ExchangeRequest draft = new ExchangeRequest(0, requester.getId(), receiver.getId(),
                skillOffered, skillWanted);
        ExchangeRequest saved = requestDAO.insert(draft,
                new ActivityEntry("You sent " + receiver.getFullName() + " an exchange request ("
                        + skillOffered + " for " + skillWanted + ").", requester.getId()),
                new ActivityEntry(requester.getFullName() + " sent you an exchange request ("
                        + skillOffered + " for " + skillWanted + ").", receiver.getId()));
        context.fireDataChanged();
        return saved;
    }

    // Same swap = same two students and the same two skills, in either direction
    private boolean isSameSwap(ExchangeRequest r, String requesterId, String receiverId,
                               Skill offered, Skill wanted) {
        boolean sameDirection = r.getRequesterId().equalsIgnoreCase(requesterId)
                && r.getReceiverId().equalsIgnoreCase(receiverId)
                && r.getSkillOffered().equals(offered) && r.getSkillWanted().equals(wanted);
        boolean oppositeDirection = r.getRequesterId().equalsIgnoreCase(receiverId)
                && r.getReceiverId().equalsIgnoreCase(requesterId)
                && r.getSkillOffered().equals(wanted) && r.getSkillWanted().equals(offered);
        return sameDirection || oppositeDirection;
    }

    // ================= Answer =================

    // Only the RECEIVER can accept
    public void accept(int requestId, String actingId) {
        ExchangeRequest r = requireRequest(requestId);
        requireReceiver(r, actingId, "accept");
        change(r, RequestStatus.ACCEPTED,
                new ActivityEntry("You accepted " + nameOf(r.getRequesterId()) + "'s request.", r.getReceiverId()),
                new ActivityEntry(nameOf(r.getReceiverId()) + " accepted your exchange request.", r.getRequesterId()));
    }

    // Only the RECEIVER can reject
    public void reject(int requestId, String actingId) {
        ExchangeRequest r = requireRequest(requestId);
        requireReceiver(r, actingId, "reject");
        change(r, RequestStatus.REJECTED,
                new ActivityEntry("You rejected " + nameOf(r.getRequesterId()) + "'s request.", r.getReceiverId()),
                new ActivityEntry(nameOf(r.getReceiverId()) + " rejected your exchange request.", r.getRequesterId()));
    }

    // Only the SENDER can cancel, and only while the request is still pending
    public void cancel(int requestId, String actingId) {
        ExchangeRequest r = requireRequest(requestId);
        if (!r.getRequesterId().equalsIgnoreCase(actingId)) {
            throw new IllegalArgumentException("Only the student who sent a request can cancel it.");
        }
        change(r, RequestStatus.CANCELLED,
                new ActivityEntry("You cancelled your request to " + nameOf(r.getReceiverId()) + ".", r.getRequesterId()),
                new ActivityEntry(nameOf(r.getRequesterId()) + " cancelled their exchange request.", r.getReceiverId()));
    }

    private void change(ExchangeRequest r, RequestStatus next, ActivityEntry... notes) {
        if (!r.getStatus().canChangeTo(next)) {
            throw new IllegalArgumentException("This request is already " + r.getStatus().getLabel()
                    + " and can no longer be changed.");
        }
        // The DAO only changes the row if it still has the status we just read
        if (!requestDAO.updateStatus(r.getId(), r.getStatus(), next, notes)) {
            throw new IllegalArgumentException("This request was changed in the meantime. Please refresh and try again.");
        }
        context.fireDataChanged();
    }

    private void requireReceiver(ExchangeRequest r, String actingId, String verb) {
        if (!r.getReceiverId().equalsIgnoreCase(actingId)) {
            throw new IllegalArgumentException("Only the student who received a request can " + verb + " it.");
        }
    }

    private ExchangeRequest requireRequest(int requestId) {
        ExchangeRequest r = requestDAO.findById(requestId);
        if (r == null) {
            throw new IllegalArgumentException("That request no longer exists.");
        }
        return r;
    }

    // ================= Lookups =================

    public List<ExchangeRequest> getIncoming(String studentId) {
        List<ExchangeRequest> list = new ArrayList<>();
        for (ExchangeRequest r : requestDAO.findForStudent(studentId)) {
            if (r.getReceiverId().equalsIgnoreCase(studentId)) {
                list.add(r);
            }
        }
        return list;   // newest first
    }

    public List<ExchangeRequest> getOutgoing(String studentId) {
        List<ExchangeRequest> list = new ArrayList<>();
        for (ExchangeRequest r : requestDAO.findForStudent(studentId)) {
            if (r.getRequesterId().equalsIgnoreCase(studentId)) {
                list.add(r);
            }
        }
        return list;
    }

    public List<ExchangeRequest> getAcceptedFor(String studentId) {
        List<ExchangeRequest> list = new ArrayList<>();
        for (ExchangeRequest r : requestDAO.findForStudent(studentId)) {
            if (r.getStatus() == RequestStatus.ACCEPTED) {
                list.add(r);
            }
        }
        return list;
    }

    // An active (pending or accepted) request between the two students, or null
    public ExchangeRequest findActiveBetween(String a, String b) {
        for (ExchangeRequest r : requestDAO.findBetween(a, b)) {
            if (r.getStatus().isActive()) {
                return r;
            }
        }
        return null;
    }

    public String nameOf(String studentId) {
        String name = studentDAO.findFullName(studentId);
        return name == null ? studentId : name;
    }
}
