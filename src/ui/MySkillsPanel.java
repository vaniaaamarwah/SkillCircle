package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import model.Skill;
import model.Student;
import service.AppServices;

// Screen 5: My Skills & Availability. Edits the logged-in Student object (through
// StudentService), so Discover, Matches and the Dashboard change immediately.
public class MySkillsPanel extends BasePanel {

    private final DefaultListModel<Skill> offeredModel = new DefaultListModel<>();
    private final DefaultListModel<Skill> wantedModel = new DefaultListModel<>();
    private final JList<Skill> offeredList = new JList<>(offeredModel);
    private final JList<Skill> wantedList = new JList<>(wantedModel);
    private final JTextField offeredField = new JTextField();
    private final JTextField wantedField = new JTextField();

    private final JCheckBox[] dayBoxes = new JCheckBox[Student.WEEK_DAYS.length];
    private final JCheckBox[] slotBoxes = new JCheckBox[Student.TIME_SLOTS.length];
    private final JComboBox<String> modeBox = new JComboBox<>(
            new String[]{Student.MODE_ONLINE, Student.MODE_OFFLINE});

    public MySkillsPanel(AppServices services) {
        super(services, "My Skills & Availability",
                "Tell others what you can teach, what you want to learn, and when you are free");

        JPanel skills = UITheme.createPanel(new GridLayout(1, 2, 18, 0));
        skills.add(createSkillCard("Skills I Can Teach", offeredList, offeredField, true));
        skills.add(createSkillCard("Skills I Want to Learn", wantedList, wantedField, false));

        JPanel body = UITheme.createPanel(new BorderLayout(0, 16));
        body.add(skills, BorderLayout.CENTER);
        body.add(createAvailabilityCard(), BorderLayout.SOUTH);
        setBody(body);

        refresh();
    }

    private JPanel createSkillCard(String title, JList<Skill> list, JTextField field, boolean offered) {
        list.setFont(UITheme.INPUT_FONT);
        list.setForeground(UITheme.SLATE);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setSelectionBackground(UITheme.SAGE);
        list.setSelectionForeground(UITheme.SLATE);

        field.setFont(UITheme.INPUT_FONT);
        JButton addButton = UITheme.createPrimaryButton("Add");
        JButton removeButton = UITheme.createSecondaryButton("Remove Selected");

        JPanel addRow = UITheme.createPanel(new BorderLayout(8, 0));
        addRow.add(field, BorderLayout.CENTER);
        addRow.add(addButton, BorderLayout.EAST);

        JPanel bottom = UITheme.createPanel(new BorderLayout(0, 8));
        bottom.add(addRow, BorderLayout.NORTH);
        JPanel removeWrap = UITheme.createPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        removeWrap.add(removeButton);
        bottom.add(removeWrap, BorderLayout.SOUTH);

        JPanel content = UITheme.createPanel(new BorderLayout(0, 10));
        content.add(UITheme.createScroll(list), BorderLayout.CENTER);
        content.add(bottom, BorderLayout.SOUTH);

        Runnable add = () -> {
            try {
                if (offered) {
                    services.students.addOfferedSkill(services.me(), field.getText());
                } else {
                    services.students.addWantedSkill(services.me(), field.getText());
                }
                field.setText("");
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        };
        addButton.addActionListener(e -> add.run());
        field.addActionListener(e -> add.run());           // Enter key
        removeButton.addActionListener(e -> {
            Skill selected = list.getSelectedValue();
            if (selected == null) {
                showInfo("Please select a skill in the list first.");
                return;
            }
            try {
                if (offered) {
                    services.students.removeOfferedSkill(services.me(), selected);
                } else {
                    services.students.removeWantedSkill(services.me(), selected);
                }
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        });
        return UITheme.createCard(title, content);
    }

    private JPanel createAvailabilityCard() {
        JPanel days = UITheme.createPanel(new GridLayout(1, Student.WEEK_DAYS.length, 4, 0));
        for (int i = 0; i < dayBoxes.length; i++) {
            dayBoxes[i] = createCheckBox(Student.WEEK_DAYS[i]);
            days.add(dayBoxes[i]);
        }
        JPanel slots = UITheme.createPanel(new GridLayout(1, Student.TIME_SLOTS.length, 4, 0));
        for (int i = 0; i < slotBoxes.length; i++) {
            slotBoxes[i] = createCheckBox(Student.TIME_SLOTS[i]);
            slots.add(slotBoxes[i]);
        }

        JButton saveButton = UITheme.createPrimaryButton("Save Availability");
        saveButton.addActionListener(e -> saveAvailability());
        modeBox.setFont(UITheme.INPUT_FONT);

        JPanel modeRow = UITheme.createPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        JLabel modeLabel = new JLabel("Preferred mode:");
        modeLabel.setFont(UITheme.LABEL_FONT);
        modeLabel.setForeground(UITheme.SLATE);
        modeRow.add(modeLabel);
        modeRow.add(modeBox);
        modeRow.add(saveButton);

        JPanel content = UITheme.createPanel(new GridLayout(0, 1, 0, 6));
        content.add(smallLabel("Available days"));
        content.add(days);
        content.add(smallLabel("Preferred time slots"));
        content.add(slots);
        content.add(modeRow);
        return UITheme.createCard("My Availability", content);
    }

    private JCheckBox createCheckBox(String text) {
        JCheckBox box = new JCheckBox(text);
        box.setOpaque(false);
        box.setFont(UITheme.LINK_FONT);
        box.setForeground(UITheme.SLATE);
        return box;
    }

    private JLabel smallLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(UITheme.LABEL_FONT);
        label.setForeground(UITheme.SLATE);
        return label;
    }

    private void saveAvailability() {
        List<String> days = new ArrayList<>();
        for (JCheckBox box : dayBoxes) {
            if (box.isSelected()) {
                days.add(box.getText());
            }
        }
        List<String> slots = new ArrayList<>();
        for (JCheckBox box : slotBoxes) {
            if (box.isSelected()) {
                slots.add(box.getText());
            }
        }
        try {
            services.students.setAvailability(services.me(), days, slots, (String) modeBox.getSelectedItem());
            showInfo("Your availability was saved.");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    // Shows the CURRENT values of the logged-in student
    @Override
    public void refresh() {
        Student me = me();
        offeredModel.clear();
        wantedModel.clear();
        for (JCheckBox box : dayBoxes) {
            box.setSelected(false);
        }
        for (JCheckBox box : slotBoxes) {
            box.setSelected(false);
        }
        if (me == null) {
            return;
        }
        for (Skill skill : me.getOfferedSkills()) {
            offeredModel.addElement(skill);
        }
        for (Skill skill : me.getWantedSkills()) {
            wantedModel.addElement(skill);
        }
        for (JCheckBox box : dayBoxes) {
            box.setSelected(me.getAvailableDays().contains(box.getText()));
        }
        for (JCheckBox box : slotBoxes) {
            box.setSelected(me.getTimeSlots().contains(box.getText()));
        }
        modeBox.setSelectedItem(me.getLearningMode());
    }
}
