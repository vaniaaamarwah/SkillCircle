package model;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// One line of the "Recent Activity" feed on the Dashboard, for example
// "Aarav Mehta sent you an exchange request". It remembers which students it concerns,
// so each student only sees their own activity.
public class ActivityEntry {

    private final LocalDateTime time;
    private final String text;
    private final List<String> studentIds = new ArrayList<>();

    public ActivityEntry(String text, String... studentIds) {
        this.time = LocalDateTime.now();
        this.text = text;
        for (String id : studentIds) {
            this.studentIds.add(id);
        }
    }

    // For rows loaded from the database (the time was stored earlier)
    public ActivityEntry(LocalDateTime time, String text) {
        this.time = time;
        this.text = text;
    }

    public LocalDateTime getTime() { return time; }

    public List<String> getStudentIds() { return new ArrayList<>(studentIds); }
    public String getText()        { return text; }

    public boolean involves(String studentId) {
        for (String id : studentIds) {
            if (id.equalsIgnoreCase(studentId)) {
                return true;
            }
        }
        return false;
    }
}
