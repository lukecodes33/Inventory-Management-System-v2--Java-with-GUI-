import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableModel;

public final class AdministrationToolsPanel {
    private AdministrationToolsPanel() {}

    /** Admin workspace: users, signed-in reset, then backup on one scrollable column. */
    public static JPanel build(
            User user, Connection connection, JFrame frame, AccountActions accountActions
    ) {
        WorkspaceShell.ensureAdmin(user, WorkspaceShell.VIEW_ADMIN_TOOLS);
        JPanel shell = WorkspaceShell.buildFormPanel(WorkspaceShell.VIEW_ADMIN_TOOLS);
        JPanel column = WorkspaceShell.buildSectionPanel();

        JPanel userMgmt = UserManagementPanel.build(user, connection);
        userMgmt.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(userMgmt);

        column.add(Box.createVerticalStrut(22));
        JPanel resetStrip = ResetPasswordPanel.buildInlineForAdminTools(user, frame, accountActions);
        resetStrip.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(resetStrip);

        column.add(Box.createVerticalStrut(22));
        JPanel profitAlert = buildProfitAlertAdminToolsSection(user, connection, frame);
        profitAlert.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(profitAlert);

        column.add(Box.createVerticalStrut(22));
        JPanel changeHistory = buildChangeHistoryAdminToolsSection(connection, frame);
        changeHistory.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(changeHistory);

        column.add(Box.createVerticalStrut(22));
        JPanel backupSection = buildBackupSectionPanel(user, connection, accountActions, frame);
        backupSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        column.add(backupSection);

        column.setAlignmentX(Component.LEFT_ALIGNMENT);
        JScrollPane scroll = new JScrollPane(column);
        scroll.setBorder(null);
        scroll.getViewport().setBackground(shell.getBackground());
        scroll.getVerticalScrollBar().setUnitIncrement(16);
        shell.add(scroll, BorderLayout.CENTER);
        return shell;
    }

    /** Compact backup list, actions, prune, and day-one reset for Administration Tools. */
    public static JPanel buildBackupSectionPanel(User user, Connection connection, AccountActions accountActions, JFrame frame) {
        WorkspaceShell.ensureAdmin(user, "Create Local Backup");
        JPanel content = WorkspaceShell.buildSectionPanel();

        content.add(WorkspaceShell.adminToolsSectionTitle("Database backup"));
        content.add(Box.createVerticalStrut(6));
        JLabel blurb = WorkspaceShell.buildSectionText("Backups live under database_backups. Restoring replaces the live database — restart the app afterward.");
        blurb.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(blurb);
        content.add(Box.createVerticalStrut(8));

        JTextField reminderDaysField = new JTextField(5);
        WorkspaceShell.styleInputCompact(reminderDaysField);
        try {
            reminderDaysField.setText(Integer.toString(WorkspaceShell.readBackupReminderDays(connection)));
        } catch (SQLException ex) {
            reminderDaysField.setText(Integer.toString(WorkspaceShell.DEFAULT_BACKUP_REMINDER_DAYS));
        }
        JButton saveReminderDays = new JButton("Save reminder");
        WorkspaceShell.styleSecondaryButton(saveReminderDays);
        saveReminderDays.addActionListener(e -> {
            try {
                int days = Integer.parseInt(reminderDaysField.getText().trim());
                if (days < 1 || days > 365) {
                    JOptionPane.showMessageDialog(frame, "Enter reminder days between 1 and 365.");
                    return;
                }
                DatabaseManager.putAppMetadata(connection, DatabaseManager.META_BACKUP_REMINDER_DAYS,
                        Integer.toString(days));
                JOptionPane.showMessageDialog(frame, "Backup reminder saved.", "Backup",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Enter a whole number of days.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Could not save reminder: " + ex.getMessage());
            }
        });
        JPanel reminderRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(reminderRow);
        reminderRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        reminderRow.add(new JLabel("Remind if no backup within (days):"));
        reminderRow.add(reminderDaysField);
        reminderRow.add(saveReminderDays);
        content.add(reminderRow);

        JCheckBox backupOnLogout = new JCheckBox("Prompt backup on logout when reminder threshold is exceeded");
        backupOnLogout.setOpaque(false);
        try {
            backupOnLogout.setSelected(WorkspaceShell.readBackupOnLogoutEnabled(connection));
        } catch (SQLException ex) {
            backupOnLogout.setSelected(false);
        }
        backupOnLogout.addActionListener(e -> {
            try {
                DatabaseManager.putAppMetadata(connection, DatabaseManager.META_BACKUP_ON_LOGOUT_ENABLED,
                        backupOnLogout.isSelected() ? "1" : "0");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Could not save backup-on-logout setting: " + ex.getMessage());
            }
        });
        backupOnLogout.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(backupOnLogout);
        content.add(Box.createVerticalStrut(8));

        DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"Backup folder"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(tableModel);
        WorkspaceShell.installTableCopyMenu(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(AppUI.newRoundedBorder(8));
        scroll.setPreferredSize(new Dimension(520, 110));
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, 120));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        Runnable refreshBackups = () -> {
            tableModel.setRowCount(0);
            try {
                for (String name : accountActions.listDatabaseBackupFolderNames()) {
                    tableModel.addRow(new Object[]{name});
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Could not list backups: " + ex.getMessage());
            }
            WorkspaceShell.deferPackTableColumns(table);
        };

        JButton refreshButton = new JButton("Refresh list");
        WorkspaceShell.styleSecondaryButton(refreshButton);
        refreshButton.addActionListener(e -> refreshBackups.run());

        JButton backupNow = new JButton("Create backup now");
        AppUI.stylePrimaryButton(backupNow);
        backupNow.addActionListener(e -> new Thread(() -> WorkspaceShell.runAction(() -> accountActions.backUpDatabase(user, frame)), "ims-backup").start());

        JButton openFolder = new JButton("Open backup folder");
        WorkspaceShell.styleSecondaryButton(openFolder);
        openFolder.addActionListener(e -> new Thread(() -> WorkspaceShell.runAction(() -> accountActions.openBackupsFolder(user, frame)), "ims-open-backup").start());

        JButton restoreButton = new JButton("Restore selected");
        AppUI.stylePrimaryButton(restoreButton);
        restoreButton.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(frame, "Select a backup folder in the table.");
                return;
            }
            String folder = (String) tableModel.getValueAt(row, 0);
            new Thread(() -> WorkspaceShell.runAction(() -> accountActions.restoreDatabaseFromBackup(user, frame, folder)), "ims-restore").start();
        });

        JTextField pruneDays = new JTextField("30", 5);
        WorkspaceShell.styleInputCompact(pruneDays);
        JButton pruneButton = new JButton("Prune old backups");
        WorkspaceShell.styleSecondaryButton(pruneButton);
        pruneButton.addActionListener(e -> {
            try {
                int days = Integer.parseInt(pruneDays.getText().trim());
                new Thread(() -> WorkspaceShell.runAction(() -> accountActions.pruneDatabaseBackups(user, frame, days)), "ims-prune").start();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Enter a valid number of days.");
            }
        });

        content.add(scroll);
        content.add(Box.createVerticalStrut(8));

        JPanel dbActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(dbActions);
        dbActions.setAlignmentX(Component.LEFT_ALIGNMENT);
        dbActions.add(refreshButton);
        dbActions.add(backupNow);
        dbActions.add(openFolder);
        dbActions.add(restoreButton);
        dbActions.add(new JLabel("Prune older than (days):"));
        dbActions.add(pruneDays);
        dbActions.add(pruneButton);
        content.add(dbActions);

        content.add(Box.createVerticalStrut(14));

        JPanel factoryBlock = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(factoryBlock);
        factoryBlock.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel resetColumn = new JPanel();
        resetColumn.setLayout(new BoxLayout(resetColumn, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(resetColumn);

        JLabel resetHeading = WorkspaceShell.adminToolsSectionTitle("Factory reset (day one)");
        resetHeading.setAlignmentX(Component.RIGHT_ALIGNMENT);
        resetColumn.add(resetHeading);
        resetColumn.add(Box.createVerticalStrut(4));

        JLabel resetBlurb = WorkspaceShell.buildSectionText(
                "Removes all business data and user accounts; next launch creates a new first administrator. Clears item_images/ only."
        );
        resetBlurb.setHorizontalAlignment(SwingConstants.RIGHT);
        resetBlurb.setAlignmentX(Component.RIGHT_ALIGNMENT);
        resetColumn.add(resetBlurb);
        resetColumn.add(Box.createVerticalStrut(8));

        JButton resetDayOne = new JButton("Reset database to day one…");
        resetDayOne.setForeground(AppUI.DANGER);
        WorkspaceShell.styleSecondaryButton(resetDayOne);
        resetDayOne.setAlignmentX(Component.RIGHT_ALIGNMENT);
        resetDayOne.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(
                    frame,
                    "This permanently deletes business data and every user login.\n\n"
                            + "• Next app start: you create a new administrator (name + password)\n"
                            + "• All item JPEGs under item_images/ are removed\n"
                            + "• company.txt and workspace_welcome.png are kept\n\n"
                            + "Continue?",
                    "Confirm reset",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (ok != JOptionPane.YES_OPTION) {
                return;
            }
            int ok2 = JOptionPane.showConfirmDialog(
                    frame,
                    "This cannot be undone. Perform day-one reset now?",
                    "Final confirmation",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (ok2 != JOptionPane.YES_OPTION) {
                return;
            }
            if (!WorkspaceShell.verifySignedInAdministratorPassword(frame, connection, user, "Day-one reset")) {
                return;
            }
            try {
                DatabaseManager.resetEnterpriseDataToDayOne(connection);
                WorkspaceShell.refreshActiveMetricsStripNow();
                JOptionPane.showMessageDialog(
                        frame,
                        "Reset complete. Log out (or close the app). On the next launch you will set up a new first administrator."
                );
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Database reset failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Item images cleanup failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        JPanel resetActions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        AppUI.applyPanelBackground(resetActions);
        resetActions.setAlignmentX(Component.RIGHT_ALIGNMENT);
        resetActions.add(resetDayOne);
        resetColumn.add(resetActions);

        factoryBlock.add(resetColumn, BorderLayout.EAST);
        content.add(factoryBlock);

        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        refreshBackups.run();
        return content;
    }

        static JPanel buildChangeHistoryAdminToolsSection(Connection connection, JFrame frame) {
        JPanel block = WorkspaceShell.buildSectionPanel();
        block.add(WorkspaceShell.adminToolsSectionTitle("Change history"));
        block.add(Box.createVerticalStrut(6));
        block.add(WorkspaceShell.buildSectionText("Recent stock adjustments, market-price edits, and note saves."));
        block.add(Box.createVerticalStrut(8));

        DefaultTableModel model = new DefaultTableModel(
                new String[]{"When", "Item Code", "User", "Type", "Delta", "Reason", "Details"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        WorkspaceShell.installTableCopyMenu(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(AppUI.newRoundedBorder(8));
        scroll.setPreferredSize(new Dimension(780, 180));
        scroll.setAlignmentX(Component.LEFT_ALIGNMENT);

        Runnable reload = () -> {
            model.setRowCount(0);
            try {
                for (InventoryAudit.ChangeLogRow row : InventoryAudit.loadRecentChanges(connection, 200)) {
                    model.addRow(new Object[]{
                            row.createdAt(),
                            row.itemCode(),
                            row.username(),
                            row.changeType(),
                            row.quantityDelta(),
                            row.reason(),
                            row.details()
                    });
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Could not load change history: " + ex.getMessage(),
                        WorkspaceShell.VIEW_ADMIN_TOOLS, JOptionPane.WARNING_MESSAGE);
            }
            WorkspaceShell.deferPackTableColumns(table);
        };
        reload.run();

        JButton refresh = new JButton("Refresh change history");
        WorkspaceShell.styleSecondaryButton(refresh);
        refresh.setAlignmentX(Component.LEFT_ALIGNMENT);
        refresh.addActionListener(e -> reload.run());
        block.add(scroll);
        block.add(Box.createVerticalStrut(8));
        block.add(refresh);
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        return block;
    }

        static JPanel buildProfitAlertAdminToolsSection(User user, Connection connection, JFrame frame) {
        WorkspaceShell.ensureAdmin(user, WorkspaceShell.VIEW_ADMIN_TOOLS);
        JPanel block = WorkspaceShell.buildSectionPanel();
        block.add(WorkspaceShell.adminToolsSectionTitle("Profit alert banner"));
        block.add(Box.createVerticalStrut(6));
        block.add(WorkspaceShell.buildSectionText(
                "Enter a profit goal as a percent above weighted-average FIFO unit cost on remaining receipts "
                        + "(for example 100 means market price ≥ double that unit cost). "
                        + "Stocked SKUs that qualify scroll along the bottom on a green stripe until you hide the banner."));
        block.add(Box.createVerticalStrut(10));

        JTextField goalField = new JTextField(12);
        WorkspaceShell.styleInput(goalField);
        String goalLoaded = "";
        boolean hideLoaded = false;
        try {
            String g = DatabaseManager.getAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_GOAL_PCT);
            goalLoaded = g == null ? "" : g.trim();
            hideLoaded = "1".equals(DatabaseManager.getAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_BANNER_DISABLED));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(frame, "Could not load profit alert settings: " + ex.getMessage(),
                    WorkspaceShell.VIEW_ADMIN_TOOLS, JOptionPane.WARNING_MESSAGE);
        }
        goalField.setText(goalLoaded);

        JCheckBox hideBanner = new JCheckBox("Hide profit alert banner (bottom of workspace)");
        hideBanner.setOpaque(false);
        hideBanner.setSelected(hideLoaded);

        JPanel goalRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 4));
        AppUI.applyPanelBackground(goalRow);
        goalRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        goalRow.add(new JLabel("Profit goal (%)"));
        goalRow.add(goalField);

        block.add(goalRow);
        block.add(Box.createVerticalStrut(6));
        hideBanner.setAlignmentX(Component.LEFT_ALIGNMENT);
        block.add(hideBanner);
        block.add(Box.createVerticalStrut(12));

        JButton save = new JButton("Save profit alert settings");
        AppUI.stylePrimaryButton(save);
        save.setAlignmentX(Component.LEFT_ALIGNMENT);
        save.addActionListener(e -> {
            try {
                String raw = goalField.getText().trim();
                if (raw.isEmpty()) {
                    DatabaseManager.deleteAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_GOAL_PCT);
                } else {
                    int v = Integer.parseInt(raw);
                    if (v < 0 || v > 10_000_000) {
                        JOptionPane.showMessageDialog(frame, "Enter a non-negative whole percent, or leave blank to clear.",
                                "Profit alert", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    DatabaseManager.putAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_GOAL_PCT, Integer.toString(v));
                }
                DatabaseManager.putAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_BANNER_DISABLED,
                        hideBanner.isSelected() ? "1" : "0");
                JOptionPane.showMessageDialog(frame, "Profit alert settings saved.", "Profit alert",
                        JOptionPane.INFORMATION_MESSAGE);
                WorkspaceShell.scheduleProfitAlertBannerRefresh(connection);
            } catch (NumberFormatException nf) {
                JOptionPane.showMessageDialog(frame, "Profit goal must be a whole number.", "Profit alert",
                        JOptionPane.WARNING_MESSAGE);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Database error: " + ex.getMessage(), "Profit alert",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
        block.add(save);
        block.setAlignmentX(Component.LEFT_ALIGNMENT);
        return block;
    }
}
