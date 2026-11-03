package model;

// The six numbers shown on the Dashboard, bundled in one read-only object.
// All numbers come from the database (see DashboardDAO); none are typed in by hand.
public final class DashboardStats {

    private final int totalStudents;
    private final int skillsOffered;
    private final int skillsWanted;
    private final int pendingRequests;
    private final int acceptedExchanges;
    private final int upcomingSessions;

    public DashboardStats(int totalStudents, int skillsOffered, int skillsWanted,
                          int pendingRequests, int acceptedExchanges, int upcomingSessions) {
        this.totalStudents = totalStudents;
        this.skillsOffered = skillsOffered;
        this.skillsWanted = skillsWanted;
        this.pendingRequests = pendingRequests;
        this.acceptedExchanges = acceptedExchanges;
        this.upcomingSessions = upcomingSessions;
    }

    public int getTotalStudents()     { return totalStudents; }
    public int getSkillsOffered()     { return skillsOffered; }
    public int getSkillsWanted()      { return skillsWanted; }
    public int getPendingRequests()   { return pendingRequests; }
    public int getAcceptedExchanges() { return acceptedExchanges; }
    public int getUpcomingSessions()  { return upcomingSessions; }
}
