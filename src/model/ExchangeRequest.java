package model;

import java.time.LocalDate;

// A request from one student (requester) to another (receiver) to swap skills:
//   the requester TEACHES skillOffered and wants to LEARN skillWanted.
public class ExchangeRequest {

    private final int id;
    private final String requesterId;
    private final String receiverId;
    private final Skill skillOffered;   // what the requester will teach
    private final Skill skillWanted;    // what the requester wants to learn
    private RequestStatus status;
    private final LocalDate createdDate;

    // Creating a NEW request: always starts as PENDING, dated today
    public ExchangeRequest(int id, String requesterId, String receiverId,
                           Skill skillOffered, Skill skillWanted) {
        this(id, requesterId, receiverId, skillOffered, skillWanted,
             RequestStatus.PENDING, LocalDate.now());
    }

    // Full constructor (also used later when loading from a file)
    public ExchangeRequest(int id, String requesterId, String receiverId,
                           Skill skillOffered, Skill skillWanted,
                           RequestStatus status, LocalDate createdDate) {
        this.id = id;
        this.requesterId = requesterId;
        this.receiverId = receiverId;
        this.skillOffered = skillOffered;
        this.skillWanted = skillWanted;
        this.status = status;
        this.createdDate = createdDate;
    }

    public int getId()                 { return id; }
    public String getRequesterId()     { return requesterId; }
    public String getReceiverId()      { return receiverId; }
    public Skill getSkillOffered()     { return skillOffered; }
    public Skill getSkillWanted()      { return skillWanted; }
    public RequestStatus getStatus()   { return status; }
    public LocalDate getCreatedDate()  { return createdDate; }

    // The ONLY way to change the status. Invalid changes throw an exception.
    public void changeStatus(RequestStatus next) {
        if (!status.canChangeTo(next)) {
            throw new IllegalStateException("A " + status.getLabel()
                    + " request cannot be changed to " + next.getLabel() + ".");
        }
        status = next;
    }

    public boolean involves(String studentId) {
        return requesterId.equalsIgnoreCase(studentId) || receiverId.equalsIgnoreCase(studentId);
    }

    // True if this request is between the two students, in either direction
    public boolean isBetween(String studentA, String studentB) {
        return (requesterId.equalsIgnoreCase(studentA) && receiverId.equalsIgnoreCase(studentB))
                || (requesterId.equalsIgnoreCase(studentB) && receiverId.equalsIgnoreCase(studentA));
    }
}
