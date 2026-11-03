package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JTextField;
import model.Student;
import service.AppServices;

// Screen 6: Discover Students. Every student except me, with search (name or skill) and a
// filter on offered skill. From here a student can be viewed, saved or sent a request.
public class DiscoverPanel extends BasePanel {

    private static final String ALL_SKILLS = "All skills";

    private final JTextField searchField = new JTextField();
    private final DefaultComboBoxModel<String> filterModel = new DefaultComboBoxModel<>();
    private final JComboBox<String> filterBox = new JComboBox<>(filterModel);
    private final TableWithEmptyState table = new TableWithEmptyState(
            "No students found. Try a different search or filter, or add more students.",
            "Name", "Department", "Year", "Offers", "Wants", "Saved");
    private List<Student> shown = new ArrayList<>();
    private boolean updating = false;   // true while refresh() is rebuilding the filter box

    public DiscoverPanel(AppServices services) {
        super(services, "Discover Students", "Find classmates to exchange skills with");

        filterBox.setFont(UITheme.INPUT_FONT);
        JButton searchButton = UITheme.createPrimaryButton("Search");
        JButton resetButton = UITheme.createSecondaryButton("Reset");

        JPanel top = UITheme.createPanel(new BorderLayout(12, 0));
        top.add(UITheme.createFieldGroup("Search by name or skill", searchField), BorderLayout.CENTER);
        JPanel right = UITheme.createPanel(new BorderLayout(10, 0));
        right.add(UITheme.createFieldGroup("Filter by offered skill", filterBox), BorderLayout.CENTER);
        JPanel rightButtons = UITheme.createPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        rightButtons.add(searchButton);
        rightButtons.add(resetButton);
        JPanel rightButtonsWrap = UITheme.createPanel(new BorderLayout());
        rightButtonsWrap.add(rightButtons, BorderLayout.SOUTH);
        right.add(rightButtonsWrap, BorderLayout.EAST);
        top.add(right, BorderLayout.EAST);

        table.setColumnWidths(130, 170, 70, 150, 150, 55);

        JButton viewButton = UITheme.createSecondaryButton("View Profile");
        JButton saveButton = UITheme.createSecondaryButton("Save Student");
        JButton unsaveButton = UITheme.createSecondaryButton("Unsave");
        JButton requestButton = UITheme.createPrimaryButton("Send Exchange Request");
        JPanel buttons = UITheme.createPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.add(viewButton);
        buttons.add(saveButton);
        buttons.add(unsaveButton);
        buttons.add(requestButton);

        JPanel body = UITheme.createPanel(new BorderLayout(0, 12));
        body.add(top, BorderLayout.NORTH);
        body.add(table, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);
        setBody(body);

        searchButton.addActionListener(e -> refresh());
        searchField.addActionListener(e -> refresh());
        filterBox.addActionListener(e -> {
            if (!updating) {
                refresh();
            }
        });
        resetButton.addActionListener(e -> {
            searchField.setText("");
            filterBox.setSelectedIndex(0);
            refresh();
        });
        viewButton.addActionListener(e -> {
            Student s = selected();
            if (s != null) {
                ProfileDialog.show(this, s);
            }
        });
        saveButton.addActionListener(e -> {
            Student s = selected();
            if (s != null) {
                try {
                    services.students.saveStudent(services.me(), s.getId());
                } catch (IllegalArgumentException ex) {
                    showError(ex.getMessage());
                }
            }
        });
        unsaveButton.addActionListener(e -> {
            Student s = selected();
            if (s != null) {
                try {
                    services.students.unsaveStudent(services.me(), s.getId());
                } catch (IllegalArgumentException ex) {
                    showError(ex.getMessage());
                }
            }
        });
        requestButton.addActionListener(e -> {
            Student s = selected();
            if (s != null) {
                RequestDialog.show(this, services, s, null, null);
            }
        });

        refresh();
    }

    private Student selected() {
        int index = table.getSelectedIndex();
        if (index < 0 || index >= shown.size()) {
            showInfo("Please select a student in the table first.");
            return null;
        }
        return shown.get(index);
    }

    @Override
    public void refresh() {
        Student me = me();
        if (me == null) {
            return;
        }

        // Rebuild the filter box (new skills may exist) but keep the current choice if possible
        updating = true;
        Object previous = filterBox.getSelectedItem();
        filterModel.removeAllElements();
        filterModel.addElement(ALL_SKILLS);
        for (String name : services.students.getOfferedSkillNamesOfOthers(me.getId())) {
            filterModel.addElement(name);
        }
        if (previous != null && filterModel.getIndexOf(previous) >= 0) {
            filterBox.setSelectedItem(previous);
        } else {
            filterBox.setSelectedIndex(0);
        }
        updating = false;

        String filter = filterBox.getSelectedIndex() <= 0 ? null : (String) filterBox.getSelectedItem();
        shown = services.students.discover(me.getId(), searchField.getText(), filter);

        table.clear();
        for (Student s : shown) {
            table.addRow(s.getFullName(), s.getDepartment(), s.getYearOfStudy(),
                    UITheme.joinSkills(s.getOfferedSkills()), UITheme.joinSkills(s.getWantedSkills()),
                    me.isSaved(s.getId()) ? "Yes" : "");
        }
        table.finishLoading();
    }
}
