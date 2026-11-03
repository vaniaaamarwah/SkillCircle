package ui;

import java.awt.CardLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;

// A read-only table that shows a friendly message instead when it has no rows.
// Usage: clear(), addRow(...) for every row, then finishLoading().
public class TableWithEmptyState extends JPanel {

    private final DefaultTableModel model;
    private final JTable table;
    private final CardLayout cards = new CardLayout();
    private final JLabel emptyLabel = new JLabel("", SwingConstants.CENTER);

    public TableWithEmptyState(String emptyMessage, String... columns) {
        setLayout(cards);
        setOpaque(false);

        model = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;   // tables are for viewing; changes happen through buttons
            }
        };
        table = new JTable(model);
        UITheme.styleTable(table);

        emptyLabel.setText("<html><div style='text-align:center;width:420px'>" + emptyMessage + "</div></html>");
        emptyLabel.setFont(UITheme.SUB_FONT);
        emptyLabel.setForeground(UITheme.SLATE);
        JPanel emptyPanel = new JPanel(new java.awt.BorderLayout());
        emptyPanel.setBackground(UITheme.WHITE);
        emptyPanel.setBorder(javax.swing.BorderFactory.createLineBorder(UITheme.LAVENDER.darker(), 1));
        emptyPanel.add(emptyLabel, java.awt.BorderLayout.CENTER);

        add(UITheme.createScroll(table), "table");
        add(emptyPanel, "empty");
        cards.show(this, "empty");
    }

    public JTable getTable() {
        return table;
    }

    public void clear() {
        model.setRowCount(0);
    }

    public void addRow(Object... values) {
        model.addRow(values);
    }

    // Call after adding all rows: shows the table, or the empty message
    public void finishLoading() {
        cards.show(this, model.getRowCount() == 0 ? "empty" : "table");
    }

    // Index of the selected row (same as the index in the list used to fill the table), or -1
    public int getSelectedIndex() {
        return table.getSelectedRow();
    }

    public void setColumnWidths(int... widths) {
        for (int i = 0; i < widths.length && i < table.getColumnCount(); i++) {
            table.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }
    }
}
