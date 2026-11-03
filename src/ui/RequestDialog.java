package ui;

import java.awt.Component;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import model.Skill;
import model.Student;
import service.AppServices;

// The "Send Exchange Request" dialog, shared by Discover, Skill Matches and Saved Students.
// The student picks which of THEIR skills they will teach and which of the OTHER student's
// skills they want to learn. ExchangeService checks every rule.
public final class RequestDialog {

    private RequestDialog() { }

    // preTeach / preLearn may be null. Returns true if a request was sent.
    public static boolean show(Component parent, AppServices services, Student other,
                               Skill preTeach, Skill preLearn) {
        Student me = services.context.getCurrentStudent();
        if (me == null || other == null) {
            return false;
        }
        if (me.getId().equalsIgnoreCase(other.getId())) {
            JOptionPane.showMessageDialog(parent, "You cannot send an exchange request to yourself.",
                    "Please check", JOptionPane.ERROR_MESSAGE);
            return false;
        }
        if (me.getOfferedSkills().isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    "You need to offer at least one skill before you can send a request.\n"
                            + "Add one in the My Skills screen first.",
                    "No skill to offer", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }
        List<Skill> learnable = new ArrayList<>();
        for (Skill skill : other.getOfferedSkills()) {
            if (!me.offersSkill(skill)) {
                learnable.add(skill);
            }
        }
        if (learnable.isEmpty()) {
            JOptionPane.showMessageDialog(parent,
                    other.getFullName() + " does not offer any skill you could learn from them.",
                    "Nothing to learn", JOptionPane.INFORMATION_MESSAGE);
            return false;
        }

        JComboBox<Skill> teachBox = new JComboBox<>(me.getOfferedSkills().toArray(new Skill[0]));
        JComboBox<Skill> learnBox = new JComboBox<>(learnable.toArray(new Skill[0]));
        if (preTeach != null) {
            teachBox.setSelectedItem(preTeach);
        }
        if (preLearn != null) {
            learnBox.setSelectedItem(preLearn);
        }

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 6));
        form.add(new JLabel("Exchange request to " + other.getFullName()));
        form.add(new JLabel("I will teach:"));
        form.add(teachBox);
        form.add(new JLabel("I want to learn:"));
        form.add(learnBox);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(parent, form, "Send Exchange Request",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return false;
            }
            try {
                services.exchanges.createRequest(me.getId(), other.getId(),
                        (Skill) teachBox.getSelectedItem(), (Skill) learnBox.getSelectedItem());
            } catch (IllegalArgumentException ex) {
                JOptionPane.showMessageDialog(parent, ex.getMessage(), "Request not sent",
                        JOptionPane.ERROR_MESSAGE);
                continue;   // show the dialog again with the same choices
            }
            JOptionPane.showMessageDialog(parent,
                    "Your request was sent to " + other.getFullName() + ".\n"
                            + "You can follow it in the Requests screen.",
                    "Request sent", JOptionPane.INFORMATION_MESSAGE);
            return true;
        }
    }
}
