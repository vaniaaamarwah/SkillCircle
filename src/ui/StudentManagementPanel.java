package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import model.Student;
import service.AppServices;

// Screen 4: Student Management. A table of all students with search, add, view, edit and
// remove. Passwords are never shown here.
public class StudentManagementPanel extends BasePanel {

    private final JTextField searchField = new JTextField();
    private final TableWithEmptyState table = new TableWithEmptyState(
            "No students match your search.", "Student ID", "Name", "Email", "Department", "Year",
            "Offers", "Record");
    private List<Student> shown = new ArrayList<>();   // the students currently in the table rows

    public StudentManagementPanel(AppServices services) {
        super(services, "Student Management", "View, search, add, edit and remove student records");

        JPanel searchRow = UITheme.createPanel(new BorderLayout(10, 0));
        searchField.setFont(UITheme.INPUT_FONT);
        searchRow.add(UITheme.createFieldGroup("Search by name or Student ID", searchField), BorderLayout.CENTER);
        JButton searchButton = UITheme.createPrimaryButton("Search");
        JButton clearButton = UITheme.createSecondaryButton("Clear");
        JPanel searchButtons = UITheme.createPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        searchButtons.add(searchButton);
        searchButtons.add(clearButton);
        JPanel searchButtonsWrap = UITheme.createPanel(new BorderLayout());
        searchButtonsWrap.add(searchButtons, BorderLayout.SOUTH);
        searchRow.add(searchButtonsWrap, BorderLayout.EAST);

        table.setColumnWidths(90, 130, 160, 210, 70, 90, 70);

        JButton addButton = UITheme.createPrimaryButton("Add Student");
        JButton viewButton = UITheme.createSecondaryButton("View Details");
        JButton editButton = UITheme.createSecondaryButton("Edit");
        JButton removeButton = UITheme.createSecondaryButton("Remove");
        JPanel buttons = UITheme.createPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.add(addButton);
        buttons.add(viewButton);
        buttons.add(editButton);
        buttons.add(removeButton);

        JPanel body = UITheme.createPanel(new BorderLayout(0, 12));
        body.add(searchRow, BorderLayout.NORTH);
        body.add(table, BorderLayout.CENTER);
        body.add(buttons, BorderLayout.SOUTH);
        setBody(body);

        searchButton.addActionListener(e -> refresh());
        searchField.addActionListener(e -> refresh());     // Enter key
        clearButton.addActionListener(e -> {
            searchField.setText("");
            refresh();
        });
        addButton.addActionListener(e -> addStudent());
        viewButton.addActionListener(e -> {
            Student s = selected();
            if (s != null) {
                ProfileDialog.show(this, s);
            }
        });
        editButton.addActionListener(e -> editStudent());
        removeButton.addActionListener(e -> removeStudent());

        refresh();
    }

    @Override
    public void refresh() {
        shown = services.students.search(searchField.getText());
        table.clear();
        for (Student s : shown) {
            table.addRow(s.getId(), s.getFullName(), s.getEmail(), s.getDepartment(),
                    s.getYearOfStudy(), s.getOfferedSkills().size() + " skill(s)",
                    s.isSample() ? "Sample" : "Registered");
        }
        table.finishLoading();
    }

    // The selected student, or null (after telling the user to select one)
    private Student selected() {
        int index = table.getSelectedIndex();
        if (index < 0 || index >= shown.size()) {
            showInfo("Please select a student in the table first.");
            return null;
        }
        return shown.get(index);
    }

    // ----- Add -----
    private void addStudent() {
        JTextField nameField = new JTextField();
        JTextField idField = new JTextField();
        JTextField emailField = new JTextField();
        JComboBox<String> departmentBox = new JComboBox<>(Student.DEPARTMENTS);
        JComboBox<String> yearBox = new JComboBox<>(Student.YEARS);
        JPasswordField passwordField = new JPasswordField();

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(new JLabel("Full name"));
        form.add(nameField);
        form.add(new JLabel("Student ID"));
        form.add(idField);
        form.add(new JLabel("College email"));
        form.add(emailField);
        form.add(new JLabel("Department"));
        form.add(departmentBox);
        form.add(new JLabel("Year of study"));
        form.add(yearBox);
        form.add(new JLabel("Starting password (the student can log in with it)"));
        form.add(passwordField);

        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, form, "Add Student",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            try {
                Student added = services.auth.register(nameField.getText(), idField.getText(),
                        emailField.getText(), (String) departmentBox.getSelectedItem(),
                        (String) yearBox.getSelectedItem(), new String(passwordField.getPassword()));
                showInfo(added.getFullName() + " was added.");
                return;
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());   // the dialog opens again with the same text
            }
        }
    }

    // ----- Edit -----
    private void editStudent() {
        Student s = selected();
        if (s == null) {
            return;
        }
        JTextField nameField = new JTextField(s.getFullName());
        JTextField emailField = new JTextField(s.getEmail());
        JComboBox<String> departmentBox = new JComboBox<>(Student.DEPARTMENTS);
        departmentBox.setSelectedItem(s.getDepartment());
        JComboBox<String> yearBox = new JComboBox<>(Student.YEARS);
        yearBox.setSelectedItem(s.getYearOfStudy());
        JTextArea bioArea = new JTextArea(s.getBio() == null ? "" : s.getBio(), 3, 25);
        bioArea.setLineWrap(true);
        bioArea.setWrapStyleWord(true);

        JPanel form = new JPanel(new GridLayout(0, 1, 0, 4));
        form.add(new JLabel("Student ID (cannot be changed): " + s.getId()));
        form.add(new JLabel("Full name"));
        form.add(nameField);
        form.add(new JLabel("College email"));
        form.add(emailField);
        form.add(new JLabel("Department"));
        form.add(departmentBox);
        form.add(new JLabel("Year of study"));
        form.add(yearBox);
        form.add(new JLabel("Bio"));
        form.add(new JScrollPane(bioArea));

        while (true) {
            int choice = JOptionPane.showConfirmDialog(this, form, "Edit Student",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (choice != JOptionPane.OK_OPTION) {
                return;
            }
            try {
                services.students.updateStudent(s.getId(), nameField.getText(), emailField.getText(),
                        (String) departmentBox.getSelectedItem(), (String) yearBox.getSelectedItem(),
                        bioArea.getText());
                showInfo("The details were updated.");
                return;
            } catch (IllegalArgumentException ex) {
                showError(ex.getMessage());
            }
        }
    }

    // ----- Remove -----
    private void removeStudent() {
        Student s = selected();
        if (s == null) {
            return;
        }
        if (!confirm("Remove " + s.getDisplayName() + "?\n\nTheir old requests and sessions will also be "
                + "deleted. This cannot be undone.", "Confirm removal")) {
            return;
        }
        try {
            services.students.removeStudent(s.getId(), services.me());
            showInfo(s.getFullName() + " was removed.");
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }
}
