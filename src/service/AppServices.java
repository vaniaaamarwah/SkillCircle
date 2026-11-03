package service;

import dao.ActivityDAO;
import dao.AppMetaDAO;
import dao.DashboardDAO;
import dao.ExchangeRequestDAO;
import dao.SessionDAO;
import dao.StudentDAO;

// One object that holds every DAO and every service, so a screen only needs ONE constructor
// argument to reach everything. Main creates it once (after the database is ready) and hands
// it to all screens. The DAOs hold no data themselves: each call reads or writes the SQLite
// database, so all screens always see the same stored data.
public class AppServices {

    public final AppContext context = new AppContext();

    // Data access (the only classes that talk to the database)
    public final StudentDAO studentDAO = new StudentDAO();
    public final ExchangeRequestDAO requestDAO = new ExchangeRequestDAO();
    public final SessionDAO sessionDAO = new SessionDAO();
    public final ActivityDAO activityDAO = new ActivityDAO();
    public final AppMetaDAO metaDAO = new AppMetaDAO();
    public final DashboardDAO dashboardDAO = new DashboardDAO();

    // Services (the business rules)
    public final AuthenticationService auth = new AuthenticationService(studentDAO, context);
    public final StudentService students = new StudentService(studentDAO, requestDAO, sessionDAO, context);
    public final MatchingService matching = new MatchingService(studentDAO);
    public final ExchangeService exchanges = new ExchangeService(studentDAO, requestDAO, context);
    public final SessionService sessions = new SessionService(studentDAO, requestDAO, sessionDAO, context);
    public final DashboardService dashboard = new DashboardService(dashboardDAO, activityDAO);

    // The Student ID of the logged-in student (null if nobody is logged in)
    public String me() {
        return context.getCurrentStudentId();
    }
}
