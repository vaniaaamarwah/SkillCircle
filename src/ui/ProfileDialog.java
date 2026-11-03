package ui;

import java.awt.Component;
import java.awt.Dimension;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import model.Student;

// Shows a student's profile in a dialog. Used by Student Management, Discover, Matches and
// Saved Students, so a profile looks the same everywhere. It NEVER shows a password.
public final class ProfileDialog {

    private ProfileDialog() { }

    public static void show(Component parent, Student s) {
        StringBuilder t = new StringBuilder();
        t.append("Name:          ").append(s.getFullName()).append('\n');
        t.append("Student ID:    ").append(s.getId()).append('\n');
        t.append("Email:         ").append(s.getEmail()).append('\n');
        t.append("Department:    ").append(s.getDepartment()).append('\n');
        t.append("Year:          ").append(s.getYearOfStudy()).append('\n');
        t.append("Record type:   ").append(s.isSample() ? "Sample (demo) student" : "Registered student").append('\n');
        t.append('\n');
        t.append("About:\n  ").append(isBlank(s.getBio()) ? "(no bio yet)" : s.getBio()).append('\n');
        t.append('\n');
        t.append("Skills offered:  ").append(UITheme.joinSkills(s.getOfferedSkills())).append('\n');
        t.append("Skills wanted:   ").append(UITheme.joinSkills(s.getWantedSkills())).append('\n');
        t.append('\n');
        t.append("Available days:  ").append(s.getAvailableDays().isEmpty() ? "-" : String.join(", ", s.getAvailableDays())).append('\n');
        t.append("Time slots:      ").append(s.getTimeSlots().isEmpty() ? "-" : String.join(", ", s.getTimeSlots())).append('\n');
        t.append("Learning mode:   ").append(s.getLearningMode()).append('\n');

        JTextArea area = new JTextArea(t.toString());
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(UITheme.INPUT_FONT);
        area.setForeground(UITheme.SLATE);
        area.setBackground(UITheme.CREAM);
        JScrollPane scroll = new JScrollPane(area);
        scroll.setPreferredSize(new Dimension(520, 340));
        JOptionPane.showMessageDialog(parent, scroll, "Profile: " + s.getFullName(), JOptionPane.PLAIN_MESSAGE);
    }

    private static boolean isBlank(String text) {
        return text == null || text.trim().isEmpty();
    }
}
