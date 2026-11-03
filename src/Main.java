import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import dao.AppMetaDAO;
import dao.StudentDAO;
import database.DataAccessException;
import database.DatabaseConnection;
import database.DatabaseInitializer;
import service.AppServices;
import service.DemoDataSeeder;
import ui.LoginPage;
import ui.MainFrame;
import ui.RegistrationPage;

// Application entry point.
//
//   java -cp "out;lib\*" Main                 start normally
//   java -cp "out;lib\*" Main --seed-demo     also add the 5 demo students (only once)
//
// run.ps1 passes --seed-demo for you; run.ps1 -NoDemo leaves it out.
//
// Data is saved in the SQLite file <project folder>\data\skillcircle.db, so accounts,
// skills, requests and sessions are still there the next time you start the program.
public class Main {

    public static void main(String[] args) {

        boolean seedDemo = false;
        for (String arg : args) {
            if ("--seed-demo".equals(arg)) {
                seedDemo = true;
            }
        }

        // If a database operation fails anywhere inside a button click, show a friendly
        // message (never a stack trace) and keep the program running. The success message of
        // that action was never reached, so the user is never told something was saved when
        // it was not. The technical details go to the console for the developer.
        Thread.setDefaultUncaughtExceptionHandler((thread, error) -> {
            error.printStackTrace();
            String message = (error instanceof DataAccessException)
                    ? error.getMessage()
                    : "Something unexpected went wrong. Your last change may not have been saved.";
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null, message,
                    "SkillCircle", JOptionPane.ERROR_MESSAGE));
        });

        String seedMessage = null;       // warning to show if the demo seeder failed

        // STEP 1: make sure the SQLite database and its tables exist (or are upgraded).
        // This creates data\skillcircle.db on the first run and NEVER deletes existing data.
        try {
            DatabaseInitializer.initialize();
            System.out.println("SkillCircle database ready: " + DatabaseConnection.getDatabasePath()
                    + " (schema version " + DatabaseInitializer.getSchemaVersion() + ")");
        } catch (DataAccessException e) {
            System.err.println("Database startup failed: " + e.getMessage());
            SwingUtilities.invokeLater(() -> {
                JOptionPane.showMessageDialog(null,
                        "SkillCircle could not open its database.\n\n" + e.getMessage(),
                        "Database Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            });
            return;
        }

        // STEP 2 (only with --seed-demo): add the demo students, once.
        if (seedDemo) {
            try {
                DemoDataSeeder seeder = new DemoDataSeeder(new StudentDAO(), new AppMetaDAO());
                int added = seeder.seedIfNeeded();
                if (added > 0) {
                    System.out.println("Demo data: added " + added
                            + " demo students (password for all of them: " + DemoDataSeeder.DEMO_PASSWORD + ").");
                } else {
                    System.out.println("Demo data: already added earlier, nothing to do.");
                }
            } catch (DataAccessException e) {
                System.err.println("Demo data could not be added: " + e.getMessage());
                seedMessage = "The demo students could not be added:\n\n" + e.getMessage()
                        + "\n\nSkillCircle will still start.";
            }
        }
        final String seedWarning = seedMessage;

        // STEP 3: build the windows. Swing windows must be created on the Event Dispatch
        // Thread (EDT), the thread Swing uses for all GUI work. invokeLater does that.
        SwingUtilities.invokeLater(() -> {
            try {
                // One object with every DAO and service. All screens share it.
                AppServices services = new AppServices();

                LoginPage loginPage = new LoginPage(services.auth);
                RegistrationPage registrationPage = new RegistrationPage(services.auth);

                // The main window is created once, hidden. After logout it shows Login again.
                MainFrame mainFrame = new MainFrame(services, loginPage::showPage);

                // Connect the pages so they can open each other
                loginPage.setRegistrationPage(registrationPage);
                registrationPage.setLoginPage(loginPage);

                // After a successful login, open the main window for that student
                loginPage.setLoginSuccessListener(mainFrame::openFor);

                loginPage.setVisible(true);   // the app starts on the Login page

                if (seedWarning != null) {
                    JOptionPane.showMessageDialog(loginPage, seedWarning,
                            "Demo Data", JOptionPane.WARNING_MESSAGE);
                }
            } catch (RuntimeException ex) {
                ex.printStackTrace();
                JOptionPane.showMessageDialog(null,
                        "SkillCircle could not start:\n" + ex.getMessage(),
                        "Startup Error", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
        });
    }
}
