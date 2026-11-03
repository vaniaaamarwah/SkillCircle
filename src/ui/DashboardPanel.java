package ui;

import java.awt.BorderLayout;
import java.awt.Font;
import java.awt.GridLayout;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import model.DashboardStats;
import model.Student;
import service.AppServices;

// Screen 1: the Dashboard. Every number is counted from the shared in-memory data
// (through DashboardService); nothing here is hardcoded. It refreshes itself whenever the
// data changes and every time the Dashboard is shown.
public class DashboardPanel extends BasePanel {

    private final JLabel totalStudentsValue = new JLabel("0");
    private final JLabel offeredValue = new JLabel("0");
    private final JLabel wantedValue = new JLabel("0");
    private final JLabel pendingValue = new JLabel("0");
    private final JLabel acceptedValue = new JLabel("0");
    private final JLabel upcomingValue = new JLabel("0");
    private final JTextArea activityArea = new JTextArea();

    // navigator = a piece of code that switches the main window to another screen
    public DashboardPanel(AppServices services, Consumer<String> navigator) {
        super(services, "Welcome", "Here is your SkillCircle overview");

        JPanel body = UITheme.createPanel(new BorderLayout(0, 20));
        body.add(createCards(), BorderLayout.NORTH);

        JPanel lower = UITheme.createPanel(new GridLayout(1, 2, 20, 0));
        lower.add(createActivityCard());
        lower.add(createQuickActions(navigator));
        body.add(lower, BorderLayout.CENTER);
        setBody(body);

        refresh();
    }

    // 2 rows x 3 columns of summary cards
    private JPanel createCards() {
        JPanel cards = UITheme.createPanel(new GridLayout(2, 3, 16, 16));
        cards.add(createCard("Total Students", totalStudentsValue));
        cards.add(createCard("Skills I Offer", offeredValue));
        cards.add(createCard("Skills I Want", wantedValue));
        cards.add(createCard("Pending Requests", pendingValue));
        cards.add(createCard("Accepted Exchanges", acceptedValue));
        cards.add(createCard("Upcoming Sessions", upcomingValue));
        return cards;
    }

    // One white card: a small title above a big number
    private JPanel createCard(String title, JLabel valueLabel) {
        JLabel cardTitle = new JLabel(title);
        cardTitle.setFont(UITheme.LABEL_FONT);
        cardTitle.setForeground(UITheme.SLATE);

        valueLabel.setFont(new Font("Segoe UI", Font.BOLD, 30));
        valueLabel.setForeground(UITheme.SLATE);

        JPanel card = new JPanel(new BorderLayout(0, 4));
        card.setBackground(UITheme.WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(4, 0, 0, 0, UITheme.SAGE.darker()),
                BorderFactory.createCompoundBorder(
                        BorderFactory.createLineBorder(UITheme.LAVENDER.darker(), 1),
                        new EmptyBorder(10, 16, 10, 16))));
        card.add(cardTitle, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);
        return card;
    }

    private JPanel createActivityCard() {
        activityArea.setEditable(false);
        activityArea.setLineWrap(true);          // long lines wrap instead of being cut off
        activityArea.setWrapStyleWord(true);
        activityArea.setFont(UITheme.LINK_FONT);
        activityArea.setForeground(UITheme.SLATE);
        activityArea.setBackground(UITheme.WHITE);
        activityArea.setMargin(new java.awt.Insets(6, 6, 6, 6));
        return UITheme.createCard("Recent Activity", UITheme.createScroll(activityArea));
    }

    private JPanel createQuickActions(Consumer<String> navigator) {
        JPanel buttons = UITheme.createPanel(new GridLayout(3, 2, 10, 10));
        buttons.add(createActionButton("Add Student", MainFrame.STUDENTS, navigator));
        buttons.add(createActionButton("My Skills", MainFrame.MY_SKILLS, navigator));
        buttons.add(createActionButton("Discover", MainFrame.DISCOVER, navigator));
        buttons.add(createActionButton("Skill Matches", MainFrame.MATCHES, navigator));
        buttons.add(createActionButton("Requests", MainFrame.REQUESTS, navigator));
        buttons.add(createActionButton("Sessions", MainFrame.SESSIONS, navigator));
        return UITheme.createCard("Quick Actions", buttons);
    }

    private JButton createActionButton(String text, String targetScreen, Consumer<String> navigator) {
        JButton button = UITheme.createPrimaryButton(text);
        button.setFont(UITheme.LABEL_FONT);
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(UITheme.SLATE, 1), new EmptyBorder(8, 4, 8, 4)));
        button.addActionListener(e -> navigator.accept(targetScreen));
        return button;
    }

    // Recounts every number from the shared data
    @Override
    public void refresh() {
        Student me = me();
        activityArea.setText("");
        if (me == null) {
            titleLabel.setText("Welcome");
            setValues("0", "0", "0", "0", "0", "0");
            return;
        }

        titleLabel.setText("Welcome, " + me.getFullName());
        DashboardStats stats = services.dashboard.getStats(me.getId());
        setValues(String.valueOf(stats.getTotalStudents()),
                String.valueOf(stats.getSkillsOffered()),
                String.valueOf(stats.getSkillsWanted()),
                String.valueOf(stats.getPendingRequests()),
                String.valueOf(stats.getAcceptedExchanges()),
                String.valueOf(stats.getUpcomingSessions()));

        List<String> lines = services.dashboard.getRecentActivity(me.getId(), 8);
        StringBuilder text = new StringBuilder();
        if (lines.isEmpty()) {
            text.append("No activity yet. Add skills or send a request to get started.");
        }
        for (String line : lines) {
            text.append(line).append("\n\n");
        }
        activityArea.setText(text.toString().trim());
        activityArea.setCaretPosition(0);
    }

    private void setValues(String total, String offered, String wanted,
                           String pending, String accepted, String upcoming) {
        totalStudentsValue.setText(total);
        offeredValue.setText(offered);
        wantedValue.setText(wanted);
        pendingValue.setText(pending);
        acceptedValue.setText(accepted);
        upcomingValue.setText(upcoming);
    }
}
