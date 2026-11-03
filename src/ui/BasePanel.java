package ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.GridLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.border.EmptyBorder;
import model.Student;
import service.AppServices;

// INHERITANCE + ABSTRACTION: every main screen "is a" BasePanel.
// The base class gives each screen the same look (cream background, title, subtitle),
// access to the services, small dialog helpers, and automatic refreshing:
// the constructor registers refresh() as a change listener, so whenever ANY screen
// changes the shared data, every screen reloads itself.
public abstract class BasePanel extends JPanel implements Refreshable {

    protected final AppServices services;
    protected final JLabel titleLabel = new JLabel();
    protected final JLabel subtitleLabel = new JLabel();

    protected BasePanel(AppServices services, String title, String subtitle) {
        this.services = services;

        setLayout(new BorderLayout(0, 15));
        setBackground(UITheme.CREAM);
        setBorder(new EmptyBorder(28, 35, 22, 35));

        titleLabel.setText(title);
        titleLabel.setFont(UITheme.TITLE_FONT);
        titleLabel.setForeground(UITheme.SLATE);
        subtitleLabel.setText(subtitle);
        subtitleLabel.setFont(UITheme.SUB_FONT);
        subtitleLabel.setForeground(UITheme.SLATE);

        JPanel heading = UITheme.createPanel(new GridLayout(2, 1, 0, 4));
        heading.add(titleLabel);
        heading.add(subtitleLabel);
        add(heading, BorderLayout.NORTH);

        // "Tell me whenever any data changes". Only a screen that is on show reloads at once
        // (each reload reads the database); MainFrame.showScreen() refreshes a screen when it
        // is opened, so a hidden screen is never out of date when the user sees it.
        services.context.addChangeListener(() -> {
            if (isShowing()) {
                refresh();
            }
        });
    }

    protected void setBody(JComponent body) {
        add(body, BorderLayout.CENTER);
    }

    // The logged-in student (null while nobody is logged in)
    protected Student me() {
        return services.context.getCurrentStudent();
    }

    protected Component dialogParent() {
        return this;
    }

    protected void showError(String message) {
        JOptionPane.showMessageDialog(dialogParent(), message, "Please check", JOptionPane.ERROR_MESSAGE);
    }

    protected void showInfo(String message) {
        JOptionPane.showMessageDialog(dialogParent(), message, "SkillCircle", JOptionPane.INFORMATION_MESSAGE);
    }

    protected boolean confirm(String message, String title) {
        return JOptionPane.showConfirmDialog(dialogParent(), message, title,
                JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE) == JOptionPane.YES_OPTION;
    }

    // Each screen says how to reload itself
    @Override
    public abstract void refresh();
}
