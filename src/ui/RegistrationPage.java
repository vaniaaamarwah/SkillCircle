package ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import database.DataAccessException;
import model.Student;
import service.AuthenticationService;
import service.StudentService;

public class RegistrationPage extends JFrame implements ActionListener {

    private final AuthenticationService authService;   // same service the LoginPage uses
    private LoginPage loginPage;                   // set later by Main

    private JTextField fullNameField;
    private JTextField studentIdField;
    private JTextField emailField;
    private JComboBox<String> departmentBox;
    private JComboBox<String> yearBox;
    private JPasswordField passwordField;
    private JPasswordField confirmPasswordField;
    private JButton registerButton;
    private JButton loginLinkButton;

    public RegistrationPage(AuthenticationService authService) {
        this.authService = authService;

        setTitle("SkillCircle - Register");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setResizable(false);
        setLayout(new BorderLayout());

        add(UITheme.createSidebar(), BorderLayout.WEST);
        add(createFormPanel(), BorderLayout.CENTER);

        getRootPane().setDefaultButton(registerButton);
    }

    public void setLoginPage(LoginPage loginPage) {
        this.loginPage = loginPage;
    }

    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 15));
        panel.setBackground(UITheme.CREAM);
        panel.setBorder(new EmptyBorder(30, 50, 30, 50));

        panel.add(UITheme.createHeadingPanel("Create Account",
                  "Join the SkillCircle campus community"), BorderLayout.NORTH);

        // Create the input components
        fullNameField = new JTextField();
        studentIdField = new JTextField();
        emailField = new JTextField();
        passwordField = new JPasswordField();
        confirmPasswordField = new JPasswordField();

        // The first item of each combo box is a placeholder, not a real choice.
        // The real choices come from the Student class, so every screen uses the same lists.
        departmentBox = new JComboBox<>(withPlaceholder("Select Department", Student.DEPARTMENTS));
        yearBox = new JComboBox<>(withPlaceholder("Select Year", Student.YEARS));

        // 4 rows x 2 columns grid; the 8th cell is left empty
        JPanel fields = UITheme.createPanel(new GridLayout(4, 2, 20, 12));
        fields.add(UITheme.createFieldGroup("Full Name", fullNameField));
        fields.add(UITheme.createFieldGroup("Student ID", studentIdField));
        fields.add(UITheme.createFieldGroup("College Email", emailField));
        fields.add(UITheme.createFieldGroup("Department", departmentBox));
        fields.add(UITheme.createFieldGroup("Year of Study", yearBox));
        fields.add(UITheme.createFieldGroup("Password", passwordField));
        fields.add(UITheme.createFieldGroup("Confirm Password", confirmPasswordField));
        fields.add(UITheme.createPanel(new BorderLayout()));   // empty filler cell

        JPanel fieldsWrapper = UITheme.createPanel(new BorderLayout());
        fieldsWrapper.add(fields, BorderLayout.NORTH);
        panel.add(fieldsWrapper, BorderLayout.CENTER);

        registerButton = UITheme.createPrimaryButton("Register");
        loginLinkButton = UITheme.createLinkButton("Already have an account? Login");
        registerButton.addActionListener(this);
        loginLinkButton.addActionListener(this);

        JPanel buttons = UITheme.createPanel(new GridLayout(2, 1, 0, 8));
        buttons.add(registerButton);
        buttons.add(loginLinkButton);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    // Builds a new array: the placeholder first, then all the real choices
    private static String[] withPlaceholder(String placeholder, String[] items) {
        String[] result = new String[items.length + 1];
        result[0] = placeholder;
        System.arraycopy(items, 0, result, 1, items.length);
        return result;
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == registerButton) {
            handleRegistration();
        } else if (e.getSource() == loginLinkButton) {
            goToLoginPage();
        }
    }

    private void handleRegistration() {
        String fullName = fullNameField.getText().trim();
        String studentId = studentIdField.getText().trim();
        String email = emailField.getText().trim();
        String password = new String(passwordField.getPassword());
        String confirmPassword = new String(confirmPasswordField.getPassword());

        // 1. All fields required (index 0 of a combo box is the placeholder)
        if (fullName.isEmpty() || studentId.isEmpty() || email.isEmpty()
                || password.isEmpty() || confirmPassword.isEmpty()
                || departmentBox.getSelectedIndex() == 0
                || yearBox.getSelectedIndex() == 0) {
            showError("Please fill in all fields, including Department and Year of Study.");
            return;
        }

        // 2. Email format
        if (!StudentService.isValidEmail(email)) {
            showError("Please enter a valid email address (example: name@college.edu).");
            return;
        }

        // 3. Passwords must match
        if (!password.equals(confirmPassword)) {
            showError("Password and Confirm Password do not match.");
            return;
        }

        // 4. Ask the service to create the account in the database.
        //    IllegalArgumentException = a rule was broken (e.g. the Student ID or email
        //    is already taken, or the password is too short).
        //    DataAccessException = a database problem. Both messages are readable.
        try {
            authService.register(fullName, studentId, email,
                    (String) departmentBox.getSelectedItem(),
                    (String) yearBox.getSelectedItem(),
                    password);
        } catch (IllegalArgumentException | DataAccessException ex) {
            showError(ex.getMessage());
            return;
        }

        JOptionPane.showMessageDialog(this,
                "Registration successful! Please login with your new account.",
                "Registration Successful", JOptionPane.INFORMATION_MESSAGE);

        goToLoginPage();
    }

    private void showError(String message) {
        JOptionPane.showMessageDialog(this, message,
                "Registration Error", JOptionPane.ERROR_MESSAGE);
    }

    private void goToLoginPage() {
        setVisible(false);
        loginPage.showPage();
    }

    // Login page calls this to open Registration with a clean form
    public void showPage() {
        fullNameField.setText("");
        studentIdField.setText("");
        emailField.setText("");
        passwordField.setText("");
        confirmPasswordField.setText("");
        departmentBox.setSelectedIndex(0);
        yearBox.setSelectedIndex(0);
        setVisible(true);
    }
}
