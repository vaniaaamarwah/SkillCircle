package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextField;
import model.Student;
import service.AppServices;

// Screen 10: Saved Students. The logged-in student's favourites (stored as Student IDs, so
// no student is ever duplicated). Profiles can be opened and requests sent from here.
public class SavedStudentsPanel extends BasePanel {

    private final TableWithEmptyState table = new TableWithEmptyState(
            "You have not saved anyone yet.<br>Save students from the Discover screen, "
                    + "or type a Student ID above.",
            "Name", "Student ID", "Department", "Year", "Offers", "Wants");
    private final JTextField idField = new JTextField();
    private List<Student> shown = new ArrayList<>();

    public SavedStudentsPanel(AppServices services) {
        super(services, "Saved Students", "Your shortlist of students to exchange skills with");

        JButton saveButton = UITheme.createPrimaryButton("Save Student");
        JPanel top = UITheme.createPanel(new BorderLayout(10, 0));
        top.add(UITheme.createFieldGroup("Save by Student ID", idField), BorderLayout.CENTER);
        JPanel saveWrap = UITheme.createPanel(new BorderLayout());
        saveWrap.add(saveButton, BorderLayout.SOUTH);
        top.add(saveWrap, BorderLayout.EAST);

        table.setColumnWidths(130, 90, 170, 70, 150, 150);

        JButton viewButton = UITheme.createSecondaryButton("View Profile");
        JButton requestButton = UITheme.createPrimaryButton("Send Exchange Request");
        JButton unsaveButton = UITheme.createSecondaryButton("Unsave");
        JPanel buttons = UITheme.createPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.add(viewButton);
        buttons.add(requestButton);
        buttons.add(unsaveButton);

        JPanel body = UITheme.createPanel(new BorderLayout(0, 12));
        body.add(top, BorderLayout.NORTH);
        body.add(table, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);
        setBody(body);

        Runnable saveById = () -> {
            String id = idField.getText().trim();
            if (id.isEmpty()) {
                showError("Please type a Student ID.");
                return;
            }
            Student other = services.students.findById(id);
            if (other == null) {
                showError("No student with ID " + id + " was found.");
                return;
            }
            try {
                services.students.saveStudent(services.me(), other.getId());
                idField.setText("");
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        };
        saveButton.addActionListener(e -> saveById.run());
        idField.addActionListener(e -> saveById.run());

        viewButton.addActionListener(e -> {
            Student s = selected();
            if (s != null) {
                ProfileDialog.show(this, s);
            }
        });
        requestButton.addActionListener(e -> {
            Student s = selected();
            if (s != null) {
                RequestDialog.show(this, services, s, null, null);
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

        refresh();
    }

    private Student selected() {
        int index = table.getSelectedIndex();
        if (index < 0 || index >= shown.size()) {
            showInfo("Please select a saved student first.");
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
        shown = services.students.getSavedStudents(me.getId());
        table.clear();
        for (Student s : shown) {
            table.addRow(s.getFullName(), s.getId(), s.getDepartment(), s.getYearOfStudy(),
                    UITheme.joinSkills(s.getOfferedSkills()), UITheme.joinSkills(s.getWantedSkills()));
        }
        table.finishLoading();
    }
}
