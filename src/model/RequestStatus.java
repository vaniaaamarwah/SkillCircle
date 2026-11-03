package model;

// The four states of an exchange request.
// The enum itself knows which changes are allowed.
public enum RequestStatus {
    PENDING("Pending"),
    ACCEPTED("Accepted"),
    REJECTED("Rejected"),
    CANCELLED("Cancelled");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    // Rule: only a PENDING request can change. It can become
    // ACCEPTED, REJECTED or CANCELLED. The other three states are final.
    public boolean canChangeTo(RequestStatus next) {
        if (this == PENDING) {
            return next == ACCEPTED || next == REJECTED || next == CANCELLED;
        }
        return false;
    }

    // "Active" requests block a duplicate request for the same skill swap
    public boolean isActive() {
        return this == PENDING || this == ACCEPTED;
    }

    @Override
    public String toString() {
        return label;
    }
}
