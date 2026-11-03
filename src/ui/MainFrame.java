package ui;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import model.Student;
import service.AppServices;

// The ONE main application window: a lavender sidebar on the left and a
// CardLayout on the right that switches between the eight screens.
// It is created once (hidden) at startup and shown after a successful login.
public class MainFrame extends JFrame {

    // Screen names. They are also the sidebar button labels and CardLayout keys.
    public static final String DASHBOARD = "Dashboard";
    public static final String STUDENTS  = "Students";
    public static final String MY_SKILLS = "My Skills";
    public static final String DISCOVER  = "Discover";
    public static final String MATCHES   = "Matches";
    public static final String REQUESTS  = "Requests";
    public static final String SESSIONS  = "Sessions";
    public static final String SAVED     = "Saved";

    private static final String[] SCREEN_ORDER = {
            DASHBOARD, STUDENTS, MY_SKILLS, DISCOVER, MATCHES, REQUESTS, SESSIONS, SAVED
    };

    private final AppServices services;
    private final Runnable onLogout;      // what to do after logout (show the Login page)

    private final CardLayout cardLayout = new CardLayout();
    private final JPanel contentPanel = new JPanel(cardLayout);
    private final Map<String, JButton> navButtons = new LinkedHashMap<>();
    private final Map<String, Refreshable> screens = new LinkedHashMap<>();
    private final JLabel userLabel = new JLabel();

    public MainFrame(AppServices services, Runnable onLogout) {
        this.services = services;
        this.onLogout = onLogout;

        setTitle("SkillCircle - Campus Skill Exchange Platform");
        setSize(1100, 720);
        setMinimumSize(new Dimension(1000, 680));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setLayout(new BorderLayout());

        contentPanel.setBackground(UITheme.CREAM);
        addScreen(DASHBOARD, new DashboardPanel(services, this::showScreen));
        addScreen(STUDENTS, new StudentManagementPanel(services));
        addScreen(MY_SKILLS, new MySkillsPanel(services));
        addScreen(DISCOVER, new DiscoverPanel(services));
        addScreen(MATCHES, new MatchesPanel(services));
        addScreen(REQUESTS, new RequestsPanel(services, this::showScreen));
        addScreen(SESSIONS, new SessionsPanel(services));
        addScreen(SAVED, new SavedStudentsPanel(services));

        add(createSidebar(), BorderLayout.WEST);
        add(contentPanel, BorderLayout.CENTER);

        showScreen(DASHBOARD);
    }

    // Every screen is a JPanel AND Refreshable (POLYMORPHISM: we treat them all alike)
    private <T extends JPanel & Refreshable> void addScreen(String name, T screen) {
        contentPanel.add(screen, name);
        screens.put(name, screen);
    }

    // Called by Main after a successful login
    public void openFor(Student student) {
        // Swing windows must be changed on the Event Dispatch Thread (EDT).
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> openFor(student));
            return;
        }
        userLabel.setText("<html>Logged in as<br><b>" + escapeHtml(student.getFullName())
                + "</b><br>" + escapeHtml(student.getId()) + "</html>");
        refreshAll();           // the screens must now show THIS student's data
        showScreen(DASHBOARD);
        setVisible(true);
        toFront();
    }

    private void refreshAll() {
        for (Refreshable screen : screens.values()) {
            screen.refresh();
        }
    }

    // Switches the visible card, highlights the matching sidebar button and reloads that screen
    public void showScreen(String screenName) {
        cardLayout.show(contentPanel, screenName);
        for (Map.Entry<String, JButton> entry : navButtons.entrySet()) {
            styleNavButton(entry.getValue(), entry.getKey().equals(screenName));
        }
        Refreshable screen = screens.get(screenName);
        if (screen != null) {
            screen.refresh();
        }
    }

    // ----- Sidebar -----
    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout(0, 25));
        sidebar.setBackground(UITheme.LAVENDER);
        sidebar.setPreferredSize(new Dimension(230, 0));
        sidebar.setBorder(new EmptyBorder(30, 15, 20, 15));

        // Branding
        JLabel name = new JLabel("SkillCircle", SwingConstants.CENTER);
        name.setFont(new Font("Segoe UI", Font.BOLD, 26));
        name.setForeground(UITheme.SLATE);
        JLabel tagline = new JLabel("Campus Skill Exchange", SwingConstants.CENTER);
        tagline.setFont(UITheme.LINK_FONT);
        tagline.setForeground(UITheme.SLATE);
        JPanel brand = UITheme.createPanel(new GridLayout(2, 1, 0, 2));
        brand.add(name);
        brand.add(tagline);
        sidebar.add(brand, BorderLayout.NORTH);

        // Navigation buttons
        JPanel nav = UITheme.createPanel(new GridLayout(SCREEN_ORDER.length, 1, 0, 6));
        for (String screenName : SCREEN_ORDER) {
            JButton button = createNavButton(screenName);
            navButtons.put(screenName, button);
            nav.add(button);
        }
        JPanel navWrapper = UITheme.createPanel(new BorderLayout());
        navWrapper.add(nav, BorderLayout.NORTH);
        sidebar.add(navWrapper, BorderLayout.CENTER);

        // Logged-in user + Logout button
        userLabel.setFont(UITheme.LINK_FONT);
        userLabel.setForeground(UITheme.SLATE);
        JButton logoutButton = UITheme.createPrimaryButton("Logout");
        logoutButton.addActionListener(e -> handleLogout());

        JPanel bottom = UITheme.createPanel(new BorderLayout(0, 12));
        bottom.add(userLabel, BorderLayout.NORTH);
        bottom.add(logoutButton, BorderLayout.CENTER);
        sidebar.add(bottom, BorderLayout.SOUTH);

        return sidebar;
    }

    private JButton createNavButton(String screenName) {
        JButton button = new JButton(screenName);
        button.setHorizontalAlignment(SwingConstants.LEFT);
        button.setForeground(UITheme.SLATE);
        button.setBorder(new EmptyBorder(10, 18, 10, 18));
        button.setBorderPainted(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.addActionListener(e -> showScreen(screenName));
        styleNavButton(button, false);
        return button;
    }

    // The selected button gets a sage-green background and bold text
    private void styleNavButton(JButton button, boolean selected) {
        button.setContentAreaFilled(selected);
        button.setOpaque(selected);
        button.setBackground(selected ? UITheme.SAGE : UITheme.LAVENDER);
        button.setFont(new Font("Segoe UI", selected ? Font.BOLD : Font.PLAIN, 15));
    }

    private void handleLogout() {
        int choice = JOptionPane.showConfirmDialog(this,
                "Do you want to log out?", "Logout", JOptionPane.YES_NO_OPTION);
        if (choice == JOptionPane.YES_OPTION) {
            services.auth.logout();    // forget who was logged in
            setVisible(false);         // hide the main window
            onLogout.run();            // show the Login page again
        }
    }

    // Stops names containing < or & from breaking the HTML label
    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
