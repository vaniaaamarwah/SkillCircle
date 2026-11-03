package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import model.ExchangeRequest;
import model.SkillMatch;
import model.Student;
import service.AppServices;

// Screen 7: Skill Matches. Shows every MUTUAL match (rule-based, not AI) found by
// MatchingService. The list is recalculated whenever any data changes.
public class MatchesPanel extends BasePanel {

    private final TableWithEmptyState table = new TableWithEmptyState(
            "No mutual matches yet.<br>Add skills you can teach and skills you want to learn in "
                    + "<b>My Skills</b>. A match needs someone who wants what you offer AND offers what you want.",
            "Student", "Department", "They can teach you", "You can teach them", "Request");
    private final JTextArea whyArea = new JTextArea(3, 40);
    private List<SkillMatch> shown = new ArrayList<>();

    public MatchesPanel(AppServices services) {
        super(services, "Skill Matches",
                "Rule-based matching: you offer what they want, and they offer what you want");

        table.setColumnWidths(130, 170, 160, 160, 110);
        table.getTable().getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                showExplanation();
            }
        });

        whyArea.setEditable(false);
        whyArea.setLineWrap(true);
        whyArea.setWrapStyleWord(true);
        whyArea.setFont(UITheme.INPUT_FONT);
        whyArea.setForeground(UITheme.SLATE);
        whyArea.setBackground(UITheme.WHITE);
        JPanel whyCard = UITheme.createCard("Why do you match?", whyArea);

        JButton viewButton = UITheme.createSecondaryButton("View Profile");
        JButton requestButton = UITheme.createPrimaryButton("Send Exchange Request");
        JPanel buttons = UITheme.createPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        buttons.add(viewButton);
        buttons.add(requestButton);

        JPanel south = UITheme.createPanel(new BorderLayout(0, 10));
        south.add(whyCard, BorderLayout.CENTER);
        south.add(buttons, BorderLayout.SOUTH);

        JPanel body = UITheme.createPanel(new BorderLayout(0, 12));
        body.add(table, BorderLayout.CENTER);
        body.add(south, BorderLayout.SOUTH);
        setBody(body);

        viewButton.addActionListener(e -> {
            SkillMatch m = selected();
            if (m != null) {
                ProfileDialog.show(this, m.getOther());
            }
        });
        requestButton.addActionListener(e -> {
            SkillMatch m = selected();
            if (m != null) {
                // the first matching skills are pre-selected in the dialog
                RequestDialog.show(this, services, m.getOther(),
                        m.getSkillsICanTeach().get(0), m.getSkillsTheyTeach().get(0));
            }
        });

        refresh();
    }

    private SkillMatch selected() {
        int index = table.getSelectedIndex();
        if (index < 0 || index >= shown.size()) {
            showInfo("Please select a match in the table first.");
            return null;
        }
        return shown.get(index);
    }

    private void showExplanation() {
        int index = table.getSelectedIndex();
        if (index >= 0 && index < shown.size()) {
            whyArea.setText(shown.get(index).getExplanation());
        } else {
            whyArea.setText("Select a match to see why you match.");
        }
    }

    @Override
    public void refresh() {
        Student me = me();
        if (me == null) {
            return;
        }
        shown = services.matching.findMatches(me.getId());
        table.clear();
        for (SkillMatch m : shown) {
            Student other = m.getOther();
            ExchangeRequest active = services.exchanges.findActiveBetween(me.getId(), other.getId());
            table.addRow(other.getFullName(), other.getDepartment(),
                    UITheme.joinSkills(m.getSkillsTheyTeach()), UITheme.joinSkills(m.getSkillsICanTeach()),
                    active == null ? "-" : active.getStatus().getLabel());
        }
        table.finishLoading();
        showExplanation();
    }
}
