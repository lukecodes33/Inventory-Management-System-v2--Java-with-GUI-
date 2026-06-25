import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

public final class AddItemPanel {
    private AddItemPanel() {}

    /** Builds admin-only add-item workflow panel with compact fields and batch draft lines (like Process Sale). */
    public static JPanel build(User user, Connection connection, JPanel workspaceContainer, JFrame frame) {
        WorkspaceShell.ensureAdmin(user, "Add Item");
        JPanel panel = new JPanel(new BorderLayout(10, 6));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 16, 14, 16));
        AppUI.applyPanelBackground(panel);

        final Path[] pendingPhotoPick = new Path[1];
        JLabel photoPreviewLabel = new JLabel("", SwingConstants.CENTER);
        photoPreviewLabel.setVerticalAlignment(SwingConstants.CENTER);
        int previewBox = WorkspaceShell.ADD_ITEM_PHOTO_PREVIEW_MAX;
        photoPreviewLabel.setPreferredSize(new Dimension(previewBox, previewBox));
        photoPreviewLabel.setMinimumSize(new Dimension(previewBox, previewBox));
        photoPreviewLabel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppUI.BORDER, 1),
                BorderFactory.createEmptyBorder(6, 6, 6, 6)));

        JLabel stagedFileName = new JLabel(" ");
        stagedFileName.setFont(stagedFileName.getFont().deriveFont(Font.PLAIN, 11f));
        stagedFileName.setForeground(AppUI.TEXT_MUTED);

        Runnable refreshPhotoPreview = () -> {
            Path p = pendingPhotoPick[0];
            if (p != null && Files.isRegularFile(p)) {
                ImageIcon icon = WorkspaceShell.loadScaledItemPhotoIcon(p, previewBox, previewBox);
                photoPreviewLabel.setIcon(icon);
                photoPreviewLabel.setText(null);
                stagedFileName.setText(p.getFileName().toString());
            } else {
                photoPreviewLabel.setIcon(null);
                photoPreviewLabel.setText("No JPEG");
                stagedFileName.setText(" ");
            }
        };

        JPanel form = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(form);
        GridBagConstraints gl = new GridBagConstraints();
        gl.insets = new Insets(2, 0, 2, 6);
        gl.anchor = GridBagConstraints.LINE_END;

        JTextField nextItemCodeField = new JTextField();
        nextItemCodeField.setEditable(false);
        nextItemCodeField.setFocusable(false);
        nextItemCodeField.setToolTipText(
                "Next available ITM code. It is assigned to the first new row when you click Add all; each extra row uses the following codes in order."
        );
        try {
            nextItemCodeField.setText(WorkspaceShell.getNextItemCode(connection));
        } catch (SQLException ex) {
            nextItemCodeField.setText("UNAVAILABLE");
        }
        WorkspaceShell.styleInputCompact(nextItemCodeField);

        JTextField itemName = new JTextField();
        JTextField stock = new JTextField();
        JTextField reorder = new JTextField();
        JTextField supplier = new JTextField();
        JTextField leadTime = new JTextField();
        WorkspaceShell.styleInputCompact(itemName, stock, reorder, supplier, leadTime);

        JTextField addItemNotesField = new JTextField();
        WorkspaceShell.styleInputCompact(addItemNotesField);
        addItemNotesField.setToolTipText(
                "Optional notes. Shown on the View Items photo pane for this SKU (max "
                        + WorkspaceShell.ITEM_NOTES_MAX_CHARS + " characters)."
        );

        int row = 0;
        gl.gridx = 0;
        gl.gridy = row;
        gl.gridwidth = 1;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        gl.anchor = GridBagConstraints.LINE_END;
        form.add(new JLabel("Item code (next)"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(nextItemCodeField, gl);

        row++;
        gl.gridx = 0;
        gl.gridy = row;
        gl.anchor = GridBagConstraints.LINE_END;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        form.add(new JLabel("Item name *"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(itemName, gl);

        row++;
        gl.gridx = 0;
        gl.gridy = row;
        gl.anchor = GridBagConstraints.LINE_END;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        form.add(new JLabel("Stock count *"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(stock, gl);

        row++;
        gl.gridx = 0;
        gl.gridy = row;
        gl.anchor = GridBagConstraints.LINE_END;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        form.add(new JLabel("Reorder trigger *"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(reorder, gl);

        row++;
        gl.gridx = 0;
        gl.gridy = row;
        gl.anchor = GridBagConstraints.LINE_END;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        form.add(new JLabel("Supplier (optional)"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(supplier, gl);

        row++;
        gl.gridx = 0;
        gl.gridy = row;
        gl.anchor = GridBagConstraints.LINE_END;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        form.add(new JLabel("Lead time (days, optional)"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(leadTime, gl);

        row++;
        gl.gridx = 0;
        gl.gridy = row;
        gl.anchor = GridBagConstraints.LINE_END;
        gl.fill = GridBagConstraints.NONE;
        gl.weightx = 0;
        form.add(new JLabel("Notes (optional)"), gl);
        gl.gridx = 1;
        gl.anchor = GridBagConstraints.LINE_START;
        gl.fill = GridBagConstraints.HORIZONTAL;
        gl.weightx = 1;
        form.add(addItemNotesField, gl);

        JButton choosePhoto = new JButton("Choose JPEG…");
        JButton clearPhotoPick = new JButton("Clear photo");
        WorkspaceShell.styleSecondaryButton(choosePhoto);
        WorkspaceShell.styleSecondaryButton(clearPhotoPick);
        clearPhotoPick.setEnabled(false);
        choosePhoto.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("JPEG images (.jpg, .jpeg)", "jpg", "jpeg"));
            if (fc.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                pendingPhotoPick[0] = fc.getSelectedFile().toPath();
                clearPhotoPick.setEnabled(true);
                refreshPhotoPreview.run();
            }
        });
        clearPhotoPick.addActionListener(e -> {
            pendingPhotoPick[0] = null;
            clearPhotoPick.setEnabled(false);
            refreshPhotoPreview.run();
        });

        JPanel photoControls = new JPanel();
        photoControls.setLayout(new BoxLayout(photoControls, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(photoControls);
        JLabel photoTitle = new JLabel("Photo (optional)", SwingConstants.CENTER);
        photoTitle.setAlignmentX(Component.CENTER_ALIGNMENT);
        photoControls.add(photoTitle);
        photoControls.add(Box.createVerticalStrut(6));
        photoPreviewLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        photoControls.add(photoPreviewLabel);
        photoControls.add(Box.createVerticalStrut(6));
        stagedFileName.setAlignmentX(Component.CENTER_ALIGNMENT);
        photoControls.add(stagedFileName);
        photoControls.add(Box.createVerticalStrut(8));
        JPanel photoBtnRow = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 0));
        AppUI.applyPanelBackground(photoBtnRow);
        photoBtnRow.add(choosePhoto);
        photoBtnRow.add(clearPhotoPick);
        photoControls.add(photoBtnRow);

        JPanel formAndPhoto = new JPanel(new BorderLayout(16, 0));
        AppUI.applyPanelBackground(formAndPhoto);
        formAndPhoto.add(form, BorderLayout.CENTER);
        formAndPhoto.add(photoControls, BorderLayout.EAST);
        refreshPhotoPreview.run();

        List<AddItemDraftLine> draftLines = new ArrayList<>();
        DefaultTableModel draftModel = new DefaultTableModel(
                new String[]{"Item name", "Stock", "Reorder", "Supplier", "Lead time", "Photo", "Notes"}, 0
        ) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable draftTable = new JTable(draftModel);
        draftTable.setRowHeight(WorkspaceShell.ADD_ITEM_INPUT_HEIGHT + 10);
        WorkspaceShell.installTableCopyMenu(draftTable);

        JButton addLine = new JButton("Add line");
        JButton removeLine = new JButton("Remove line");
        JButton clearDraft = new JButton("Clear draft");
        WorkspaceShell.styleSecondaryButton(addLine);
        WorkspaceShell.styleSecondaryButton(removeLine);
        WorkspaceShell.styleSecondaryButton(clearDraft);

        addLine.addActionListener(e -> {
            String itemNameValue = itemName.getText().trim();
            if (itemNameValue.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Item name is required.");
                return;
            }
            try {
                int stockCount = Integer.parseInt(stock.getText().trim());
                int reorderTrigger = Integer.parseInt(reorder.getText().trim());
                if (stockCount < 0 || reorderTrigger < 0) {
                    JOptionPane.showMessageDialog(panel, "Stock and reorder trigger must be zero or greater.");
                    return;
                }
                String supplierValue = supplier.getText().trim();
                Integer leadTimeDays = null;
                if (!leadTime.getText().trim().isEmpty()) {
                    leadTimeDays = Integer.parseInt(leadTime.getText().trim());
                    if (leadTimeDays < 0) {
                        JOptionPane.showMessageDialog(panel, "Lead time must be zero or greater.");
                        return;
                    }
                }
                String notesRaw = addItemNotesField.getText();
                if (notesRaw != null && notesRaw.length() > WorkspaceShell.ITEM_NOTES_MAX_CHARS) {
                    JOptionPane.showMessageDialog(panel, "Notes must be at most " + WorkspaceShell.ITEM_NOTES_MAX_CHARS + " characters.");
                    return;
                }
                draftLines.add(new AddItemDraftLine(
                        itemNameValue, stockCount, reorderTrigger, supplierValue, leadTimeDays, notesRaw == null ? "" : notesRaw, pendingPhotoPick[0]));
                refreshAddItemDraftTable(draftModel, draftLines, draftTable);
                itemName.setText("");
                stock.setText("");
                reorder.setText("");
                supplier.setText("");
                leadTime.setText("");
                addItemNotesField.setText("");
                pendingPhotoPick[0] = null;
                clearPhotoPick.setEnabled(false);
                refreshPhotoPreview.run();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter valid whole numbers for stock, reorder trigger, and optional lead time.");
            }
        });

        removeLine.addActionListener(e -> {
            int viewRow = draftTable.getSelectedRow();
            if (viewRow < 0) {
                JOptionPane.showMessageDialog(panel, "Select a row in the draft table to remove.");
                return;
            }
            int modelRow = draftTable.convertRowIndexToModel(viewRow);
            if (modelRow >= 0 && modelRow < draftLines.size()) {
                draftLines.remove(modelRow);
                refreshAddItemDraftTable(draftModel, draftLines, draftTable);
            }
        });

        clearDraft.addActionListener(e -> {
            if (draftLines.isEmpty()) {
                return;
            }
            int ok = JOptionPane.showConfirmDialog(panel, "Clear all draft lines?", "Clear draft", JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                draftLines.clear();
                refreshAddItemDraftTable(draftModel, draftLines, draftTable);
            }
        });

        JButton submit = new JButton("Add all to inventory");
        AppUI.stylePrimaryButton(submit);
        submit.addActionListener(e -> {
            if (draftLines.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Add at least one line to the draft first.");
                return;
            }
            int added = 0;
            boolean originalAutoCommit = true;
            try {
                originalAutoCommit = connection.getAutoCommit();
                connection.setAutoCommit(false);
                List<AddItemDraftLine> snapshot = new ArrayList<>(draftLines);
                for (AddItemDraftLine line : snapshot) {
                    String itemCodeValue = WorkspaceShell.getNextItemCode(connection);
                    try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) AS count FROM inventory WHERE `Item Code` = ?")) {
                        check.setString(1, itemCodeValue);
                        try (ResultSet rs = check.executeQuery()) {
                            if (rs.next() && rs.getInt("count") > 0) {
                                throw new SQLException("Item code already exists: " + itemCodeValue + ". Try again.");
                            }
                        }
                    }
                    insertNewInventoryItemRow(
                            connection,
                            user,
                            itemCodeValue,
                            line.itemName,
                            line.stock,
                            line.reorder,
                            line.supplier,
                            line.leadTime,
                            line.notes
                    );
                    if (line.pendingPhoto != null && Files.isRegularFile(line.pendingPhoto)) {
                        try {
                            WorkspaceShell.copySourceJpegToItemPhoto(line.pendingPhoto, itemCodeValue);
                        } catch (IOException ioe) {
                            throw new SQLException("Photo save failed for " + itemCodeValue + ": " + ioe.getMessage(), ioe);
                        }
                    }
                    added++;
                }
                connection.commit();
                draftLines.clear();
                refreshAddItemDraftTable(draftModel, draftLines, draftTable);
                JOptionPane.showMessageDialog(
                        panel,
                        "Added " + added + " item(s). Sale price is entered at checkout. Use purchase orders for landed cost."
                );
                WorkspaceShell.showView(workspaceContainer, "View Items", ViewItemsPanel.build(user, connection, frame, workspaceContainer));
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException suppressed) {
                    ex.addSuppressed(suppressed);
                }
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                try {
                    connection.setAutoCommit(originalAutoCommit);
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(panel, "Unable to restore connection state: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(buttonRow);
        buttonRow.add(addLine);
        buttonRow.add(removeLine);
        buttonRow.add(clearDraft);

        JPanel northStack = new JPanel(new BorderLayout(0, 4));
        AppUI.applyPanelBackground(northStack);
        northStack.add(formAndPhoto, BorderLayout.NORTH);
        northStack.add(buttonRow, BorderLayout.SOUTH);

        JPanel footer = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(footer);
        footer.add(submit, BorderLayout.EAST);

        panel.add(northStack, BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(draftTable);
        tableScroll.setBorder(AppUI.newRoundedBorder(8));
        panel.add(tableScroll, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /** One draft row for batch Add Item (item codes assigned on commit). */
    static final class AddItemDraftLine {
        final String itemName;
        final int stock;
        final int reorder;
        final String supplier;
        final Integer leadTime;
    /** Optional item note (shown on View Items photo rail only). */
        final String notes;
    /** Optional JPEG chosen before commit; copied to {@code item_images/<code>.jpeg} after insert. */
        final Path pendingPhoto;

    /**
         * @param itemName      display name until {@code ITM} code is assigned at commit
         * @param stock         initial stock count
         * @param reorder       reorder trigger threshold
         * @param supplier      supplier label (may be empty)
         * @param leadTime      supplier lead time days, or null
         * @param notes         free-text note (may be null)
         * @param pendingPhoto  optional JPEG path staged for copy after insert
         */
        AddItemDraftLine(String itemName, int stock, int reorder, String supplier, Integer leadTime, String notes, Path pendingPhoto) {
            this.itemName = itemName;
            this.stock = stock;
            this.reorder = reorder;
            this.supplier = supplier == null ? "" : supplier;
            this.leadTime = leadTime;
            this.notes = notes == null ? "" : notes;
            this.pendingPhoto = pendingPhoto;
        }
    }

    /** Rebuilds Add Item draft table from list order. */
    static void refreshAddItemDraftTable(DefaultTableModel model, List<AddItemDraftLine> lines, JTable table) {
        model.setRowCount(0);
        for (AddItemDraftLine line : lines) {
            String notesCell = line.notes.isEmpty() ? "" : WorkspaceShell.abbreviateForTableCell(line.notes, 48);
            model.addRow(new Object[]{
                    line.itemName,
                    line.stock,
                    line.reorder,
                    line.supplier.isEmpty() ? "" : line.supplier,
                    line.leadTime == null ? "" : line.leadTime,
                    line.pendingPhoto != null ? "Yes" : "",
                    notesCell
            });
        }
        WorkspaceShell.deferPackTableColumns(table);
    }

    /**
     * Inserts one new inventory row, optional FIFO layer for initial stock, and movement log.
     *
     * @param supplierValue trimmed supplier; empty becomes SQL NULL
     * @param notesValue    optional item note (trimmed; empty becomes SQL NULL); capped to WorkspaceShell.ITEM_NOTES_MAX_CHARS
     */
    static void insertNewInventoryItemRow(
            Connection connection,
            User user,
            String itemCodeValue,
            String itemNameValue,
            int stockCount,
            int reorderTrigger,
            String supplierValue,
            Integer leadTimeDays,
            String notesValue
    ) throws SQLException {
        String notesForDb = notesValue == null ? "" : notesValue.trim();
        if (notesForDb.length() > WorkspaceShell.ITEM_NOTES_MAX_CHARS) {
            notesForDb = notesForDb.substring(0, WorkspaceShell.ITEM_NOTES_MAX_CHARS);
        }
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO Inventory (`Item Code`, `Item Name`, `Stock`, `On Order`, `ReOrder Trigger`, `Supplier`, `Lead Time`, `Notes`, `Market Price`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)"
        )) {
            insert.setString(1, itemCodeValue);
            insert.setString(2, itemNameValue);
            insert.setInt(3, stockCount);
            insert.setInt(4, 0);
            insert.setInt(5, reorderTrigger);
            if (supplierValue == null || supplierValue.isEmpty()) {
                insert.setNull(6, java.sql.Types.VARCHAR);
            } else {
                insert.setString(6, supplierValue);
            }
            if (leadTimeDays == null) {
                insert.setNull(7, java.sql.Types.INTEGER);
            } else {
                insert.setInt(7, leadTimeDays);
            }
            if (notesForDb.isEmpty()) {
                insert.setNull(8, java.sql.Types.VARCHAR);
            } else {
                insert.setString(8, notesForDb);
            }
            insert.setNull(9, java.sql.Types.REAL);
            insert.executeUpdate();
        }
        if (supplierValue != null && !supplierValue.trim().isEmpty()) {
            int sid = DatabaseManager.ensureSupplier(connection, supplierValue.trim());
            try (PreparedStatement up = connection.prepareStatement(
                    "UPDATE Inventory SET supplier_id = ? WHERE `Item Code` = ?")) {
                up.setInt(1, sid);
                up.setString(2, itemCodeValue);
                up.executeUpdate();
            }
        }
        if (stockCount > 0) {
            try (PreparedStatement addLayer = connection.prepareStatement(
                    "INSERT INTO inventory_cost_layers (item_code, reference, unit_cost, qty_received, qty_remaining, created_at) VALUES (?, ?, ?, ?, ?, ?)"
            )) {
                addLayer.setString(1, itemCodeValue);
                addLayer.setString(2, "INITIAL_STOCK");
                addLayer.setDouble(3, 0);
                addLayer.setInt(4, stockCount);
                addLayer.setInt(5, stockCount);
                addLayer.setString(6, dateTime.nowDisplayString());
                addLayer.executeUpdate();
            }
            WorkspaceShell.incrementInventoryStorageQty(connection, itemCodeValue, DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID, stockCount);
        }
        try (PreparedStatement movement = connection.prepareStatement(
                "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            movement.setString(1, itemCodeValue);
            movement.setString(2, String.valueOf(stockCount));
            movement.setString(3, "ADD");
            movement.setString(4, "INITIAL_STOCK");
            movement.setString(5, user.getUsername());
            movement.setString(6, dateTime.nowDisplayString());
            movement.executeUpdate();
        }
    }
}
