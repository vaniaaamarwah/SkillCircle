package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import model.ExchangeRequest;
import model.Session;
import model.Skill;
import model.Student;
import service.AppServices;

// Screen 9: Sessions. Schedule learning sessions for ACCEPTED exchanges, see upcoming and
// past sessions, confirm, reschedule and cancel. No video calls or calendar integration.
public class SessionsPanel extends BasePanel {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    private final TableWithEmptyState upcomingTable = new TableWithEmptyState(
            "No upcoming sessions. Schedule one from an accepted exchange.",
            "Partner", "Skill / Topic", "Your role", "Date", "Time", "Mode", "Status");
    private final TableWithEmptyState pastTable = new TableWithEmptyState(
            "No past or cancelled sessions yet.",
            "Partner", "Skill / Topic", "Your role", "Date", "Time", "Mode", "Status");
    private List<Session> upcoming = new ArrayList<>();
    private List<Session> past = new ArrayList<>();

    public SessionsPanel(AppServices services) {
        super(services, "Sessions", "Schedule and manage your learning sessions");

        upcomingTable.setColumnWidths(120, 110, 90, 85, 55, 65, 190);
        pastTable.setColumnWidths(120, 110, 90, 85, 55, 65, 190);

        JButton scheduleButton = UITheme.createPrimaryButton("Schedule Session");
        JButton confirmButton = UITheme.createSecondaryButton("Confirm");
        JButton rescheduleButton = UITheme.createSecondaryButton("Reschedule");
        JButton cancelButton = UITheme.createSecondaryButton("Cancel Session");
        JPanel buttons = UITheme.createPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.add(scheduleButton);
        buttons.add(confirmButton);
        buttons.add(rescheduleButton);
        buttons.add(cancelButton);

        JPanel tables = UITheme.createPanel(new GridLayout(2, 1, 0, 12));
        tables.add(UITheme.createCard("Upcoming Sessions", upcomingTable));
        tables.add(UITheme.createCard("Past & Cancelled Sessions", pastTable));

        JPanel body = UITheme.createPanel(new BorderLayout(0, 12));
        body.add(buttons, BorderLayout.NORTH);
        body.add(tables, BorderLayout.CENTER);
        setBody(body);

        scheduleButton.addActionListener(e -> scheduleSession());
        confirmButton.addActionListener(e -> {
            Session s = selectedUpcoming();
            if (s != null) {
                try {
                    services.sessions.confirm(s.getId(), services.me());
                    showInfo("The session is confirmed.");
                } catch (IllegalArgumentException ex) {
                    showError(ex.getMessage());
                }
            }
        });
        rescheduleButton.addActionListener(e -> rescheduleSession());
        cancelButton.addActionListener(e -> {
            Session s = selectedUpcoming();
            if (s != null && confirm("Cancel the " + s.getSkill() + " session with "
                    + services.exchanges.nameOf(s.getPartnerId(services.me())) + "?", "Cancel session")) {
                try {
                    services.sessions.cancel(s.getId(), services.me());
                } catch (IllegalArgumentException ex) {
                    showError(ex.getMessage());
                }
            }
        });

        refresh();
    }

    private Session selectedUpcoming() {
        int index = upcomingTable.getSelectedIndex();
        if (index < 0 || index >= upcoming.size()) {
            showInfo("Please select an upcoming session first.");
            return null;
        }
        return upcoming.get(index);
    }

    // ----- Schedule -----

    // One entry of the "exchange" combo box (shows a readable sentence)
    private class ExchangeChoice {
        final ExchangeRequest request;
        final String label;

        ExchangeChoice(ExchangeRequest request, String myId) {
            this.request = request;
            boolean iAmRequester = request.getRequesterId().equalsIgnoreCase(myId);
            String partner = services.exchanges.nameOf(iAmRequester ? request.getReceiverId() : request.getRequesterId());
            Skill iTeach = iAmRequester ? request.getSkillOffered() : request.getSkillWanted();
            Skill iLearn = iAmRequester ? request.getSkillWanted() : request.getSkillOffered();
            this.label = partner + ":  you teach " + iTeach + ", you learn " + iLearn;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    // One entry of the "skill" combo box
    private class SkillChoice {
        final Skill skill;
        final String label;

        SkillChoice(Skill skill, String label) {
            this.skill = skill;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private void fillSkillChoices(JComboBox<SkillChoice> box, ExchangeChoice choice, String myId) {
        box.removeAllItems();
        if (choice == null) {
            return;
        }
        ExchangeRequest r = choice.request;
        boolean iAmRequester = r.getRequesterId().equalsIgnoreCase(myId);
        String partner = services.exchanges.nameOf(iAmRequester ? r.getReceiverId() : r.getRequesterId());
        box.addItem(new SkillChoice(r.getSkillOffered(), r.getSkillOffered() + (iAmRequester
                ? " (you teach " + partner + ")" : " (" + partner + " teaches you)")));
        box.addItem(new SkillChoice(r.getSkillWanted(), r.getSkillWanted() + (iAmRequester
                ? " (" + partner + " teaches you)" : " (you teach " + partner + ")")));
    }

    private void scheduleSession() {
        Student me = me();
        List<ExchangeRequest> accepted = services.exchanges.getAcceptedFor(me.getId());
        if (accepted.isEmpty()) {
            showInfo("You have no accepted exchanges yet.\n"
                    + "Accept an incoming request (or wait for yours to be accepted) in the Requests screen.");
            return;
        }

        JComboBox<ExchangeChoice> exchangeBox = new JComboBox<>();
        for (ExchangeRequest r : accepted) {
            exchangeBox.addItem(new ExchangeChoice(r, me.getId()));
        }
        JComboBox<SkillChoice> skillBox = new JComboBox<>();
        fillSkillChoices(skillBox, (ExchangeChoice) exchangeBox.getSelectedItem(), me.getId());
        exchangeBox.addActionListener(e ->
                fillSkillChoices(skillBox, (ExchangeChoice) exchangeBox.getSelectedItem(), me.getId()));

        JTextField dateField = new JTextField(LocalDate.now().plusDays(1).toString());
        JTextField timeField = new JTextField("17:00");
        JComboBox<String> modeBox = new JComboBox<>(new String[]{Student.MODE_ONLINE, Student.MODE_OFFLINE});

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(new JLabel("Accepted exchange"));
        form.add(exchangeBox);
        form.add(new JLabel("Skill / topic to be taught"));
        form.add(skillBox);
        form.add(new JLabel("Date (yyyy-MM-dd)"));
        form.add(dateField);
        form.add(new JLabel("Time (24-hour HH:mm, for example 17:30)"));
        form.add(timeField);
        form.add(new JLabel("Mode"));
        form.add(modeBox);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, form, "Schedule Session",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            try {
                ExchangeChoice exchange = (ExchangeChoice) exchangeBox.getSelectedItem();
                SkillChoice skill = (SkillChoice) skillBox.getSelectedItem();
                if (exchange == null || skill == null) {
                    throw new IllegalArgumentException("Please choose an exchange and a skill.");
                }
                services.sessions.schedule(exchange.request.getId(), me.getId(), skill.skill,
                        parseDate(dateField.getText()), parseTime(timeField.getText()),
                        (String) modeBox.getSelectedItem());
                showInfo("The session was scheduled. Your partner needs to confirm it.");
                return;
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        }
    }

    // ----- Reschedule -----
    private void rescheduleSession() {
        Session s = selectedUpcoming();
        if (s == null) {
            return;
        }
        JTextField dateField = new JTextField(s.getDate().toString());
        JTextField timeField = new JTextField(s.getTime().format(TIME_FORMAT));
        JComboBox<String> modeBox = new JComboBox<>(new String[]{Student.MODE_ONLINE, Student.MODE_OFFLINE});
        modeBox.setSelectedItem(s.getMode());

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(new JLabel(s.getSkill() + " with " + services.exchanges.nameOf(s.getPartnerId(services.me()))));
        form.add(new JLabel("New date (yyyy-MM-dd)"));
        form.add(dateField);
        form.add(new JLabel("New time (HH:mm)"));
        form.add(timeField);
        form.add(new JLabel("Mode"));
        form.add(modeBox);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, form, "Reschedule Session",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            try {
                services.sessions.reschedule(s.getId(), services.me(), parseDate(dateField.getText()),
                        parseTime(timeField.getText()), (String) modeBox.getSelectedItem());
                showInfo("The session was rescheduled. Your partner needs to confirm the new time.");
                return;
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        }
    }

    private LocalDate parseDate(String text) {
        try {
            return LocalDate.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Please enter a real date in the format yyyy-MM-dd, for example "
                    + LocalDate.now().plusDays(1) + ".");
        }
    }

    private LocalTime parseTime(String text) {
        try {
            return LocalTime.parse(text.trim());
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException("Please enter a real time in 24-hour format HH:mm, for example 17:30.");
        }
    }

    // ----- Refresh -----
    @Override
    public void refresh() {
        Student me = me();
        if (me == null) {
            return;
        }
        upcoming = services.sessions.getUpcoming(me.getId());
        past = services.sessions.getPast(me.getId());
        fill(upcomingTable, upcoming, me);
        fill(pastTable, past, me);
    }

    private void fill(TableWithEmptyState table, List<Session> sessions, Student me) {
        table.clear();
        for (Session s : sessions) {
            boolean iTeach = s.getTeacherId().equalsIgnoreCase(me.getId());
            table.addRow(services.exchanges.nameOf(s.getPartnerId(me.getId())), s.getSkill(),
                    iTeach ? "You teach" : "You learn", s.getDate(), s.getTime().format(TIME_FORMAT),
                    s.getMode(), statusText(s, me));
        }
        table.finishLoading();
    }

    private String statusText(Session s, Student me) {
        if (s.isUpcoming() && s.getStatus() == Session.Status.PROPOSED) {
            return s.getProposedById().equalsIgnoreCase(me.getId())
                    ? "Proposed (waiting for partner)" : "Proposed (needs your confirmation)";
        }
        return s.getStatusLabel();
    }
}
