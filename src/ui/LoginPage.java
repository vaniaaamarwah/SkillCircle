package ui;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.border.EmptyBorder;
import database.DataAccessException;
import model.Student;
import service.AuthenticationService;

// The Login window. It "is a" JFrame and also "is an" ActionListener,
// so it can react to its own button clicks.
public class LoginPage extends JFrame implements ActionListener {

    private final AuthenticationService authService;   // all login rules live here
    private RegistrationPage registrationPage;     // set later by Main

    // What to do after a successful login. Main sets this to "open the main window".
    // Consumer<Student> means: a piece of code that receives the logged-in Student.
    private Consumer<Student> loginSuccessListener;

    private JTextField studentIdField;
    private JPasswordField passwordField;
    private JButton loginButton;
    private JButton registerLinkButton;

    public LoginPage(AuthenticationService authService) {
        this.authService = authService;

        setTitle("SkillCircle - Login");
        setSize(900, 600);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);              // center on screen
        setResizable(false);
        setLayout(new BorderLayout());

        add(UITheme.createSidebar(), BorderLayout.WEST);
        add(createFormPanel(), BorderLayout.CENTER);

        // Pressing Enter anywhere in the window clicks Login
        getRootPane().setDefaultButton(loginButton);
    }

    // Main calls this to connect the two pages
    public void setRegistrationPage(RegistrationPage registrationPage) {
        this.registrationPage = registrationPage;
    }

    // Main calls this to say what happens after a successful login
    public void setLoginSuccessListener(Consumer<Student> loginSuccessListener) {
        this.loginSuccessListener = loginSuccessListener;
    }

    // Builds the right-hand cream form area
    private JPanel createFormPanel() {
        JPanel panel = new JPanel(new BorderLayout(0, 25));
        panel.setBackground(UITheme.CREAM);
        panel.setBorder(new EmptyBorder(90, 70, 40, 70));

        panel.add(UITheme.createHeadingPanel("Welcome Back", "Login to your account"),
                  BorderLayout.NORTH);

        studentIdField = new JTextField();
        passwordField = new JPasswordField();

        JPanel fields = UITheme.createPanel(new GridLayout(2, 1, 0, 18));
        fields.add(UITheme.createFieldGroup("Student ID", studentIdField));
        fields.add(UITheme.createFieldGroup("Password", passwordField));

        // Wrapper keeps the fields at their natural height (top-aligned)
        JPanel fieldsWrapper = UITheme.createPanel(new BorderLayout());
        fieldsWrapper.add(fields, BorderLayout.NORTH);
        panel.add(fieldsWrapper, BorderLayout.CENTER);

        loginButton = UITheme.createPrimaryButton("Login");
        registerLinkButton = UITheme.createLinkButton("Don't have an account? Register");
        loginButton.addActionListener(this);          // 'this' = this page handles clicks
        registerLinkButton.addActionListener(this);

        JPanel buttons = UITheme.createPanel(new GridLayout(2, 1, 0, 10));
        buttons.add(loginButton);
        buttons.add(registerLinkButton);
        panel.add(buttons, BorderLayout.SOUTH);

        return panel;
    }

    // Called automatically by Swing whenever a registered button is clicked
    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == loginButton) {
            handleLogin();
        } else if (e.getSource() == registerLinkButton) {
            openRegistrationPage();
        }
    }

    private void handleLogin() {
        String studentId = studentIdField.getText().trim();
        String password = new String(passwordField.getPassword());

        // 1. Both fields must be filled
        if (studentId.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter both Student ID and Password.",
                    "Missing Information", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // 2. Ask the service to check the ID and password against the database.
        //    A database problem (file locked, driver missing...) comes back as a
        //    DataAccessException with a readable message.
        Student student;
        try {
            student = authService.login(studentId, password);
        } catch (DataAccessException ex) {
            JOptionPane.showMessageDialog(this, ex.getMessage(),
                    "Database Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        if (student != null) {
            // showMessageDialog is MODAL: this method pauses here until the user clicks OK.
            JOptionPane.showMessageDialog(this,
                    "Login successful! Welcome, " + student.getFullName() + ".",
                    "Login Successful", JOptionPane.INFORMATION_MESSAGE);
            passwordField.setText("");

            // The user clicked OK, so now we move on to the main application.
            // (Before this fix, the method simply ended after the dialog.)
            if (loginSuccessListener != null) {
                setVisible(false);                    // hide Login
                loginSuccessListener.accept(student); // open MainFrame for this student
            }
        } else {
            JOptionPane.showMessageDialog(this,
                    "Incorrect Student ID or password. Please try again.",
                    "Login Failed", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void openRegistrationPage() {
        setVisible(false);                // hide Login
        registrationPage.showPage();      // show Registration
    }

    // Registration page (and the main window's Logout) call this to show Login again
    public void showPage() {
        studentIdField.setText("");
        passwordField.setText("");
        setVisible(true);
    }
}
