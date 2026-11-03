package model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

// One learning session: a teacher teaches one skill to a learner.
// Sessions belong to an ACCEPTED exchange request (requestId).
public class Session {

    public enum Status {
        PROPOSED("Proposed"),
        CONFIRMED("Confirmed"),
        CANCELLED("Cancelled");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private final int id;
    private final int requestId;
    private final String teacherId;
    private final String learnerId;
    private final Skill skill;
    private LocalDate date;
    private LocalTime time;
    private String mode;          // Online or Offline
    private Status status;
    private String proposedById;  // who made the latest proposal (the OTHER student confirms)

    // A NEW session starts as PROPOSED by the student who scheduled it
    public Session(int id, int requestId, String teacherId, String learnerId, Skill skill,
                   LocalDate date, LocalTime time, String mode, String proposedById) {
        this(id, requestId, teacherId, learnerId, skill, date, time, mode,
             Status.PROPOSED, proposedById);
    }

    public Session(int id, int requestId, String teacherId, String learnerId, Skill skill,
                   LocalDate date, LocalTime time, String mode, Status status, String proposedById) {
        this.id = id;
        this.requestId = requestId;
        this.teacherId = teacherId;
        this.learnerId = learnerId;
        this.skill = skill;
        this.date = date;
        this.time = time;
        this.mode = mode;
        this.status = status;
        this.proposedById = proposedById;
    }

    public int getId()              { return id; }
    public int getRequestId()       { return requestId; }
    public String getTeacherId()    { return teacherId; }
    public String getLearnerId()    { return learnerId; }
    public Skill getSkill()         { return skill; }
    public LocalDate getDate()      { return date; }
    public LocalTime getTime()      { return time; }
    public String getMode()         { return mode; }
    public Status getStatus()       { return status; }
    public String getProposedById() { return proposedById; }

    public void setStatus(Status status) { this.status = status; }

    // Changing the schedule puts the session back to PROPOSED:
    // the other student must confirm the new plan.
    public void reschedule(LocalDate newDate, LocalTime newTime, String newMode, String editorId) {
        this.date = newDate;
        this.time = newTime;
        this.mode = newMode;
        this.proposedById = editorId;
        this.status = Status.PROPOSED;
    }

    public boolean involves(String studentId) {
        return teacherId.equalsIgnoreCase(studentId) || learnerId.equalsIgnoreCase(studentId);
    }

    // The other student in this session
    public String getPartnerId(String myId) {
        return teacherId.equalsIgnoreCase(myId) ? learnerId : teacherId;
    }

    public LocalDateTime getStart() {
        return LocalDateTime.of(date, time);
    }

    // Upcoming = starts now or later, and not cancelled
    public boolean isUpcoming() {
        return status != Status.CANCELLED
                && !getStart().isBefore(LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES));
    }

    // The label shown in tables: a confirmed/proposed session whose time has passed
    // is shown as "Completed"
    public String getStatusLabel() {
        if (status == Status.CANCELLED) {
            return "Cancelled";
        }
        if (!isUpcoming()) {
            return status == Status.CONFIRMED ? "Completed" : "Expired (never confirmed)";
        }
        return status.getLabel();
    }
}
