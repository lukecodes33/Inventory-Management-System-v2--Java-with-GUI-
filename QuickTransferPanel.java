import java.awt.BorderLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.sql.Connection;
import java.sql.SQLException;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class QuickTransferPanel {
    private QuickTransferPanel() {}

    /** Short bin-to-bin move flow starting from item code (same backend as Stock by Location). */
    public static JPanel build(User user, Connection connection) throws SQLException {
        DatabaseManager.ensureStorageLocationsAndBuckets(connection);
        JPanel panel = WorkspaceShell.buildFormPanel("Quick Transfer");
        JPanel intro = WorkspaceShell.buildSectionPanel();
        intro.add(WorkspaceShell.buildSectionText(
                "Move quantity between bins without searching the full stock-by-location table. "
                        + "Total stock on hand is unchanged — only shelf placement updates."));
        panel.add(intro, BorderLayout.NORTH);

        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            panel.add(WorkspaceShell.buildSectionText("Storage tables are not available."), BorderLayout.CENTER);
            return panel;
        }

        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints gb = new GridBagConstraints();
        gb.insets = new Insets(4, 0, 4, 10);

        JTextField itemCode = new JTextField();
        JTextField itemDesc = new JTextField();
        JComboBox<WorkspaceShell.StorageLocationPick> fromCombo = new JComboBox<>();
        JComboBox<WorkspaceShell.StorageLocationPick> toCombo = new JComboBox<>();
        JTextField qtyField = new JTextField();
        WorkspaceShell.styleInput(itemCode, itemDesc, qtyField);
        WorkspaceShell.styleAutoFilledInventoryField(itemDesc);
        WorkspaceShell.styleComboMatchInputRow(fromCombo, toCombo);

        int r = 0;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Item Code *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(itemCode, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Item Description"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(itemDesc, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("From bin *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(fromCombo, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("To bin *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(toCombo, gb);

        r++;
        gb.gridx = 0;
        gb.gridy = r;
        gb.anchor = GridBagConstraints.LINE_END;
        gb.fill = GridBagConstraints.NONE;
        gb.weightx = 0;
        form.add(new JLabel("Quantity *"), gb);
        gb.gridx = 1;
        gb.anchor = GridBagConstraints.LINE_START;
        gb.fill = GridBagConstraints.HORIZONTAL;
        gb.weightx = 1;
        form.add(qtyField, gb);

        WorkspaceShell.wireInventoryItemDescriptionLookup(connection, itemCode, itemDesc);

        final int[] maxAtSource = {0};

        Runnable reloadDestination = () -> {
            WorkspaceShell.StorageLocationPick from = (WorkspaceShell.StorageLocationPick) fromCombo.getSelectedItem();
            int fromId = from != null ? from.id : -1;
            toCombo.removeAllItems();
            if (fromId > 0) {
                try {
                    WorkspaceShell.fillStorageLocationComboExcluding(toCombo, connection, fromId);
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(panel, "Could not load destination bins: " + ex.getMessage(),
                            "Quick Transfer", JOptionPane.ERROR_MESSAGE);
                }
            }
        };

        Runnable reloadSourceBins = () -> {
            String code = itemCode.getText().trim();
            fromCombo.removeAllItems();
            toCombo.removeAllItems();
            maxAtSource[0] = 0;
            qtyField.setText("");
            if (code.isEmpty()) {
                return;
            }
            try {
                WorkspaceShell.fillStorageLocationComboForItemBins(fromCombo, connection, code);
                if (fromCombo.getItemCount() == 1) {
                    fromCombo.setSelectedIndex(0);
                    WorkspaceShell.StorageLocationPick pick = (WorkspaceShell.StorageLocationPick) fromCombo.getSelectedItem();
                    if (pick != null) {
                        maxAtSource[0] = pick.qtyAvailable;
                        qtyField.setText(String.valueOf(maxAtSource[0]));
                    }
                    reloadDestination.run();
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Could not load source bins: " + ex.getMessage(),
                        "Quick Transfer", JOptionPane.ERROR_MESSAGE);
            }
        };

        DocumentListener codeListener = new DocumentListener() {
            private void go() {
                reloadSourceBins.run();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                go();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                go();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                go();
            }
        };
        itemCode.getDocument().addDocumentListener(codeListener);

        fromCombo.addActionListener(e -> {
            WorkspaceShell.StorageLocationPick pick = (WorkspaceShell.StorageLocationPick) fromCombo.getSelectedItem();
            maxAtSource[0] = pick != null ? pick.qtyAvailable : 0;
            if (maxAtSource[0] > 0) {
                qtyField.setText(String.valueOf(maxAtSource[0]));
            }
            reloadDestination.run();
        });

        JButton transfer = new JButton("Transfer");
        AppUI.stylePrimaryButton(transfer);
        transfer.addActionListener(e -> {
            String code = itemCode.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Item code is required.");
                return;
            }
            WorkspaceShell.StorageLocationPick from = (WorkspaceShell.StorageLocationPick) fromCombo.getSelectedItem();
            WorkspaceShell.StorageLocationPick to = (WorkspaceShell.StorageLocationPick) toCombo.getSelectedItem();
            if (from == null || from.id <= 0) {
                JOptionPane.showMessageDialog(panel, "Choose a source bin that holds this item.");
                return;
            }
            if (to == null || to.id <= 0) {
                JOptionPane.showMessageDialog(panel, "Choose a destination bin.");
                return;
            }
            try {
                int qty = Integer.parseInt(qtyField.getText().trim());
                if (qty <= 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be greater than zero.");
                    return;
                }
                if (qty > maxAtSource[0]) {
                    JOptionPane.showMessageDialog(panel,
                            "Source bin only has " + maxAtSource[0] + " units.");
                    return;
                }
                WorkspaceShell.moveInventoryBetweenStorageLocations(connection, user, code, from.id, to.id, qty);
                JOptionPane.showMessageDialog(panel,
                        "Moved " + qty + " from " + from.label + " to " + to.label + ".",
                        "Quick Transfer", JOptionPane.INFORMATION_MESSAGE);
                WorkspaceShell.recordRecentItem(code, WorkspaceShell.queryInventoryItemDescription(connection, code));
                reloadSourceBins.run();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter a valid whole number for quantity.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Could not transfer: " + ex.getMessage(),
                        "Quick Transfer", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel center = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(center);
        center.add(form, BorderLayout.NORTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(WorkspaceShell.buildActionBar(null, transfer), BorderLayout.SOUTH);
        return panel;
    }
}
