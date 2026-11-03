package ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.LayoutManager;
import java.awt.RenderingHints;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.table.JTableHeader;
import java.util.List;
import model.Skill;

// Holds the SkillCircle colors, fonts and small "builder" methods
// so LoginPage and RegistrationPage look identical.
public class UITheme {

    // ----- Color palette -----
    public static final Color LAVENDER = new Color(0xE9E5F7);
    public static final Color SAGE     = new Color(0xDDEDE2);
    public static final Color CREAM    = new Color(0xFAF7F0);
    public static final Color SLATE    = new Color(0x30344B);
    public static final Color WHITE    = new Color(0xFFFFFF);

    // ----- Fonts -----
    public static final Font TITLE_FONT  = new Font("Segoe UI", Font.BOLD, 28);
    public static final Font SUB_FONT    = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font LABEL_FONT  = new Font("Segoe UI", Font.BOLD, 13);
    public static final Font INPUT_FONT  = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font BUTTON_FONT = new Font("Segoe UI", Font.BOLD, 15);
    public static final Font LINK_FONT   = new Font("Segoe UI", Font.PLAIN, 13);

    private UITheme() { }   // nobody should create a UITheme object

    // A transparent panel, so the cream background shows through
    public static JPanel createPanel(LayoutManager layout) {
        JPanel panel = new JPanel(layout);
        panel.setOpaque(false);
        return panel;
    }

    // Left lavender sidebar with the logo, name and tagline
    public static JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(LAVENDER);
        sidebar.setPreferredSize(new Dimension(280, 0));
        sidebar.setBorder(new EmptyBorder(100, 20, 20, 20));

        JPanel brand = createPanel(new GridLayout(3, 1, 0, 5));

        JLabel name = new JLabel("SkillCircle", SwingConstants.CENTER);
        name.setFont(new Font("Segoe UI", Font.BOLD, 30));
        name.setForeground(SLATE);

        JLabel tagline = new JLabel("Campus Skill Exchange Platform", SwingConstants.CENTER);
        tagline.setFont(SUB_FONT);
        tagline.setForeground(SLATE);

        brand.add(createLogo());
        brand.add(name);
        brand.add(tagline);
        sidebar.add(brand, BorderLayout.NORTH);
        return sidebar;
    }

    // Simple logo: two overlapping circles ("circle" of skills), drawn with AWT
    private static JPanel createLogo() {
        JPanel logo = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                    RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setStroke(new BasicStroke(3));
                int x = (getWidth() - 90) / 2;      // center the 90px-wide shape

                g2.setColor(WHITE);
                g2.fillOval(x, 10, 50, 50);
                g2.setColor(SAGE);
                g2.fillOval(x + 40, 10, 50, 50);

                g2.setColor(SLATE);
                g2.drawOval(x, 10, 50, 50);
                g2.drawOval(x + 40, 10, 50, 50);
            }
        };
        logo.setOpaque(false);
        logo.setPreferredSize(new Dimension(100, 70));
        return logo;
    }

    // Big heading + small subtitle
    public static JPanel createHeadingPanel(String title, String subtitle) {
        JPanel panel = createPanel(new GridLayout(2, 1, 0, 4));
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(TITLE_FONT);
        titleLabel.setForeground(SLATE);
        JLabel subLabel = new JLabel(subtitle);
        subLabel.setFont(SUB_FONT);
        subLabel.setForeground(SLATE);
        panel.add(titleLabel);
        panel.add(subLabel);
        return panel;
    }

    // A label placed above an input component, with the input styled
    public static JPanel createFieldGroup(String labelText, JComponent field) {
        JLabel label = new JLabel(labelText);
        label.setFont(LABEL_FONT);
        label.setForeground(SLATE);

        field.setFont(INPUT_FONT);
        field.setForeground(SLATE);
        field.setBackground(WHITE);

        if (field instanceof JTextField) {        // also true for JPasswordField
            JTextField textField = (JTextField) field;
            textField.setCaretColor(SLATE);
            textField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(LAVENDER.darker(), 1),
                    new EmptyBorder(6, 8, 6, 8)));
        }

        JPanel group = createPanel(new BorderLayout(0, 5));
        group.add(label, BorderLayout.NORTH);
        group.add(field, BorderLayout.CENTER);
        return group;
    }

    // Sage-green main button
    public static JButton createPrimaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(BUTTON_FONT);
        button.setBackground(SAGE);
        button.setForeground(SLATE);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SLATE, 1),
                new EmptyBorder(10, 20, 10, 20)));
        return button;
    }

    // Text-only button that looks like a link
    public static JButton createLinkButton(String text) {
        JButton button = new JButton(text);
        button.setFont(LINK_FONT);
        button.setForeground(SLATE);
        button.setBorderPainted(false);
        button.setContentAreaFilled(false);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        return button;
    }

    // ================= Helpers added for the main screens =================

    // White button with a slate border (less important than the sage-green primary button)
    public static JButton createSecondaryButton(String text) {
        JButton button = new JButton(text);
        button.setFont(LABEL_FONT);
        button.setBackground(WHITE);
        button.setForeground(SLATE);
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(SLATE, 1),
                new EmptyBorder(8, 16, 8, 16)));
        return button;
    }

    // A white "card" with a small bold title above the content
    public static JPanel createCard(String title, JComponent content) {
        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(SLATE);

        JPanel card = new JPanel(new BorderLayout(0, 10));
        card.setBackground(WHITE);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(LAVENDER.darker(), 1),
                new EmptyBorder(14, 16, 14, 16)));
        card.add(titleLabel, BorderLayout.NORTH);
        card.add(content, BorderLayout.CENTER);
        return card;
    }

    // Styles a JTable with the SkillCircle colors
    public static void styleTable(JTable table) {
        table.setFont(INPUT_FONT);
        table.setForeground(SLATE);
        table.setBackground(WHITE);
        table.setRowHeight(28);
        table.setGridColor(LAVENDER);
        table.setSelectionBackground(SAGE);
        table.setSelectionForeground(SLATE);
        table.setFillsViewportHeight(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        JTableHeader header = table.getTableHeader();
        header.setFont(LABEL_FONT);
        header.setBackground(LAVENDER);
        header.setForeground(SLATE);
        header.setReorderingAllowed(false);
    }

    public static JScrollPane createScroll(Component view) {
        JScrollPane scroll = new JScrollPane(view);
        scroll.setBorder(BorderFactory.createLineBorder(LAVENDER.darker(), 1));
        scroll.getViewport().setBackground(WHITE);
        return scroll;
    }

    // "Java, Python" or "-" when the list is empty
    public static String joinSkills(List<Skill> skills) {
        if (skills == null || skills.isEmpty()) {
            return "-";
        }
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < skills.size(); i++) {
            if (i > 0) {
                text.append(", ");
            }
            text.append(skills.get(i).getName());
        }
        return text.toString();
    }
}
