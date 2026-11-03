package service;

import dao.ActivityDAO;
import dao.DashboardDAO;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import model.ActivityEntry;
import model.DashboardStats;

// Gives the Dashboard screen its numbers. The screen asks this service, the service asks
// the DAOs, the DAOs ask the database: every number is COUNTED from the stored data each time,
// so the Dashboard can never show an outdated or invented value.
public class DashboardService {

    private final DashboardDAO dashboardDAO;
    private final ActivityDAO activityDAO;

    public DashboardService(DashboardDAO dashboardDAO) {
        this(dashboardDAO, new ActivityDAO());
    }

    public DashboardService(DashboardDAO dashboardDAO, ActivityDAO activityDAO) {
        this.dashboardDAO = dashboardDAO;
        this.activityDAO = activityDAO;
    }

    // The six Dashboard numbers for this student (read from the database each time)
    public DashboardStats getStats(String studentId) {
        return dashboardDAO.load(studentId);
    }

    // The latest activity lines for this student, newest first, with a short date prefix
    public List<String> getRecentActivity(String studentId, int limit) {
        DateTimeFormatter format = DateTimeFormatter.ofPattern("dd MMM, HH:mm");
        List<String> lines = new ArrayList<>();
        for (ActivityEntry entry : activityDAO.recent(studentId, limit)) {
            lines.add(entry.getTime().format(format) + "  -  " + entry.getText());
        }
        return lines;
    }
}
