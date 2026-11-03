package ui;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.JButton;
import javax.swing.JPanel;
import model.ExchangeRequest;
import model.Student;
import service.AppServices;

// Screen 8: Exchange Requests. Incoming and outgoing requests in two tables.
// The receiver accepts/rejects; the sender cancels. ExchangeService enforces the rules.
public class RequestsPanel extends BasePanel {

    private final TableWithEmptyState incomingTable = new TableWithEmptyState(
            "No incoming requests. When someone asks you for an exchange, it appears here.",
            "#", "From", "They will teach you", "You would teach", "Date", "Status");
    private final TableWithEmptyState outgoingTable = new TableWithEmptyState(
            "No outgoing requests. Send one from Discover, Matches or Saved.",
            "#", "To", "You will teach", "You will learn", "Date", "Status");
    private List<ExchangeRequest> incoming = new ArrayList<>();
    private List<ExchangeRequest> outgoing = new ArrayList<>();

    public RequestsPanel(AppServices services, Consumer<String> navigator) {
        super(services, "Exchange Requests", "Answer requests sent to you and follow the ones you sent");

        incomingTable.setColumnWidths(35, 140, 150, 150, 90, 80);
        outgoingTable.setColumnWidths(35, 140, 150, 150, 90, 80);

        JButton acceptButton = UITheme.createPrimaryButton("Accept");
        JButton rejectButton = UITheme.createSecondaryButton("Reject");
        JButton scheduleButton1 = UITheme.createSecondaryButton("Schedule Session...");
        JPanel incomingButtons = UITheme.createPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        incomingButtons.add(acceptButton);
        incomingButtons.add(rejectButton);
        incomingButtons.add(scheduleButton1);

        JButton cancelButton = UITheme.createSecondaryButton("Cancel Request");
        JButton scheduleButton2 = UITheme.createSecondaryButton("Schedule Session...");
        JPanel outgoingButtons = UITheme.createPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
        outgoingButtons.add(cancelButton);
        outgoingButtons.add(scheduleButton2);

        JPanel body = UITheme.createPanel(new GridLayout(2, 1, 0, 14));
        body.add(createSection("Incoming Requests", incomingTable, incomingButtons));
        body.add(createSection("Outgoing Requests", outgoingTable, outgoingButtons));
        setBody(body);

        acceptButton.addActionListener(e -> {
            ExchangeRequest r = selected(incomingTable, incoming);
            if (r != null) {
                try {
                    services.exchanges.accept(r.getId(), services.me());
                    showInfo("Request accepted. You can now schedule a session in the Sessions screen.");
                } catch (IllegalArgumentException ex) {
                    showError(ex.getMessage());
                }
            }
        });
        rejectButton.addActionListener(e -> {
            ExchangeRequest r = selected(incomingTable, incoming);
            if (r != null && confirm("Reject the request from "
                    + services.exchanges.nameOf(r.getRequesterId()) + "?", "Reject request")) {
                try {
                    services.exchanges.reject(r.getId(), services.me());
                } catch (IllegalArgumentException ex) {
                    showError(ex.getMessage());
                }
            }
        });
        cancelButton.addActionListener(e -> {
            ExchangeRequest r = selected(outgoingTable, outgoing);
            if (r != null && confirm("Cancel your request to "
                    + services.exchanges.nameOf(r.getReceiverId()) + "?", "Cancel request")) {
                try {
                    services.exchanges.cancel(r.getId(), services.me());
                } catch (IllegalArgumentException ex) {
                    showError(ex.getMessage());
                }
            }
        });
        scheduleButton1.addActionListener(e -> navigator.accept(MainFrame.SESSIONS));
        scheduleButton2.addActionListener(e -> navigator.accept(MainFrame.SESSIONS));

        refresh();
    }

    private JPanel createSection(String title, TableWithEmptyState table, JPanel buttons) {
        JPanel inner = UITheme.createPanel(new BorderLayout(0, 8));
        inner.add(table, BorderLayout.CENTER);
        inner.add(buttons, BorderLayout.SOUTH);
        return UITheme.createCard(title, inner);
    }

    private ExchangeRequest selected(TableWithEmptyState table, List<ExchangeRequest> list) {
        int index = table.getSelectedIndex();
        if (index < 0 || index >= list.size()) {
            showInfo("Please select a request in the table first.");
            return null;
        }
        return list.get(index);
    }

    @Override
    public void refresh() {
        Student me = me();
        if (me == null) {
            return;
        }
        incoming = services.exchanges.getIncoming(me.getId());
        outgoing = services.exchanges.getOutgoing(me.getId());

        incomingTable.clear();
        for (ExchangeRequest r : incoming) {
            incomingTable.addRow(r.getId(), services.exchanges.nameOf(r.getRequesterId()),
                    r.getSkillOffered(), r.getSkillWanted(), r.getCreatedDate(), r.getStatus().getLabel());
        }
        incomingTable.finishLoading();

        outgoingTable.clear();
        for (ExchangeRequest r : outgoing) {
            outgoingTable.addRow(r.getId(), services.exchanges.nameOf(r.getReceiverId()),
                    r.getSkillOffered(), r.getSkillWanted(), r.getCreatedDate(), r.getStatus().getLabel());
        }
        outgoingTable.finishLoading();
    }
}
