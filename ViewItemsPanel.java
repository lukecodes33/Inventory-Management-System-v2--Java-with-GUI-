import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.regex.Pattern;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableModel;
import javax.swing.table.TableRowSorter;


/** Extracted from WorkspaceShell. */
public final class ViewItemsPanel {
    private ViewItemsPanel() {}

        /** One row in the View Items card shelf (stock or on-order must be &gt; 0). */
    record ViewItemShelfRow(
            String itemCode,
            String itemName,
            int stock,
            int onOrder,
            int reorderTrigger,
            Double marketPrice,
            Double unrealizedMarginPercent,
            boolean staleMarketPrice,
            boolean hasPhoto
    ) {
    }

        /**
     * Builds the View Items shelf: card grid for SKUs with stock on hand or units on order.
     * Each card shows the item JPEG (or a placeholder), code, stock, on-order qty, and market price.
     * Administrators: clicking a card swaps the right rail to that item's photo and stats.
     * Double-click a card for the full item detail dialog.
     *
     * @param user         signed-in user (for admin-only photo change in detail dialog)
     * @param connection   active database connection
     * @return inventory shelf panel
     * @throws SQLException when query fails
     */
    public static JPanel build(
            User user, Connection connection, JFrame frame, JPanel workspaceContainer
    ) throws SQLException {
        JPanel panel = WorkspaceShell.buildFormPanel("Inventory Items");
        JPanel centerWrap = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(centerWrap);

        JPanel intro = WorkspaceShell.buildSectionPanel();
        intro.add(WorkspaceShell.buildSectionText(
                "Only items with stock on hand or units on order are listed. "
                        + "Double-click a card for full item details. "
                        + "Use ⌘/Ctrl+1–9 for sidebar shortcuts."));
        if (user.hasAdminRights()) {
            intro.add(Box.createVerticalStrut(10));
            JPanel photoActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            photoActions.setOpaque(false);
            photoActions.setAlignmentX(Component.LEFT_ALIGNMENT);
            JButton fetchMissing = new JButton("Fetch missing photos");
            AppUI.stylePrimaryButton(fetchMissing);
            fetchMissing.setToolTipText(
                    "Download product images from the web using each item's description (skips items that already have a JPEG).");
            fetchMissing.addActionListener(e -> WorkspaceShell.startItemPhotoFetchTask(
                    frame, workspaceContainer, user, frame, connection, false));
            JButton refetchAll = new JButton("Re-fetch all photos");
            WorkspaceShell.styleSecondaryButton(refetchAll);
            refetchAll.setToolTipText("Replace every item_images/<code>.jpeg file (may take a minute).");
            refetchAll.addActionListener(e -> {
                int ok = JOptionPane.showConfirmDialog(
                        frame,
                        "Re-download photos for every inventory item?\nExisting JPEG files will be replaced.",
                        "Re-fetch all photos",
                        JOptionPane.OK_CANCEL_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (ok == JOptionPane.OK_OPTION) {
                    WorkspaceShell.startItemPhotoFetchTask(frame, workspaceContainer, user, frame, connection, true);
                }
            });
            photoActions.add(fetchMissing);
            photoActions.add(refetchAll);
            intro.add(photoActions);
        }
        centerWrap.add(intro, BorderLayout.NORTH);

        List<ViewItemShelfRow> allRows = loadViewItemShelfRows(connection);
        Set<String> favouriteCodes = readFavouriteItemCodes(connection, user.getUsername());
        Object prefillSearch = workspaceContainer.getClientProperty(WorkspaceShell.CLIENT_VIEW_ITEMS_SEARCH_TEXT);
        workspaceContainer.putClientProperty(WorkspaceShell.CLIENT_VIEW_ITEMS_SEARCH_TEXT, null);

        JTextField searchField = new JTextField(24);
        WorkspaceShell.styleInput(searchField);
        if (prefillSearch instanceof String s) {
            searchField.setText(s);
        }

        JComboBox<String> smartFilter = new JComboBox<>(WorkspaceShell.VIEW_ITEMS_FILTER_OPTIONS);
        WorkspaceShell.styleComboMatchInputRow(smartFilter);

        final List<ViewItemShelfRow>[] filteredRowsHolder = new List[]{new ArrayList<>()};
        JButton exportCsv = new JButton("Export CSV");
        WorkspaceShell.styleSecondaryButton(exportCsv);
        exportCsv.addActionListener(e -> exportViewItemsCsv(frame, filteredRowsHolder[0]));
        JLabel matchCount = new JLabel(" ");
        matchCount.setForeground(AppUI.TEXT_MUTED);

        JPanel filterRow = new JPanel();
        filterRow.setLayout(new BoxLayout(filterRow, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(filterRow);
        filterRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        JPanel searchRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        AppUI.applyPanelBackground(searchRow);
        searchRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        searchRow.add(new JLabel("Search code or name"));
        searchRow.add(searchField);
        JPanel filterSelectRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        AppUI.applyPanelBackground(filterSelectRow);
        filterSelectRow.setAlignmentX(Component.LEFT_ALIGNMENT);
        filterSelectRow.add(new JLabel("Filter"));
        filterSelectRow.add(smartFilter);
        filterSelectRow.add(exportCsv);
        filterSelectRow.add(matchCount);
        filterRow.add(searchRow);
        filterRow.add(filterSelectRow);
        intro.add(Box.createVerticalStrut(8));
        intro.add(filterRow);

        JPanel gridHost = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(gridHost);
        final JPanel[] selectedCard = new JPanel[1];
        final Runnable[] rebuildGridHolder = new Runnable[1];

        Runnable rebuildGrid = () -> {
            String q = searchField.getText().trim().toLowerCase(Locale.ROOT);
            String filter = Objects.toString(smartFilter.getSelectedItem(), WorkspaceShell.VIEW_ITEMS_FILTER_ALL);
            List<ViewItemShelfRow> filtered = new ArrayList<>();
            for (ViewItemShelfRow row : allRows) {
                if (!q.isEmpty()) {
                    String code = row.itemCode().toLowerCase(Locale.ROOT);
                    String name = row.itemName().toLowerCase(Locale.ROOT);
                    if (!code.contains(q) && !name.contains(q)) {
                        continue;
                    }
                }
                if (!matchesViewItemsSmartFilter(row, filter, favouriteCodes)) {
                    continue;
                }
                filtered.add(row);
            }
            sortViewItemsShelfRows(filtered, filter, favouriteCodes);
            filteredRowsHolder[0] = new ArrayList<>(filtered);
            selectedCard[0] = null;
            gridHost.removeAll();
            if (filtered.isEmpty()) {
                gridHost.add(WorkspaceShell.buildSectionText(
                        allRows.isEmpty()
                                ? "No items with stock on hand or on order."
                                : "No items match the current search and filter."), BorderLayout.CENTER);
                matchCount.setText(allRows.isEmpty() ? "" : "0 shown");
            } else {
                JPanel scrollBody = new JPanel(new GridBagLayout());
                scrollBody.setOpaque(true);
                AppUI.applyPanelBackground(scrollBody);
                populateViewItemsGrid(
                        scrollBody, user, connection, workspaceContainer, filtered, selectedCard,
                        favouriteCodes,
                        () -> {
                            try {
                                favouriteCodes.clear();
                                favouriteCodes.addAll(readFavouriteItemCodes(connection, user.getUsername()));
                            } catch (SQLException ex) {
                                JOptionPane.showMessageDialog(panel, "Could not reload favourites: " + ex.getMessage(),
                                        "View Items", JOptionPane.ERROR_MESSAGE);
                            }
                            rebuildGridHolder[0].run();
                        });
                JScrollPane scroll = new JScrollPane(scrollBody,
                        JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                scroll.setBorder(AppUI.newRoundedBorder(8));
                scroll.getViewport().setBackground(scrollBody.getBackground());
                scroll.getVerticalScrollBar().setUnitIncrement(16);
                scroll.setPreferredSize(new Dimension(1080, Math.min(WorkspaceShell.MAIN_FRAME_BASE_H - 260, 520)));
                gridHost.add(scroll, BorderLayout.CENTER);
                matchCount.setText(filtered.size() + " of " + allRows.size() + " shown");
            }
            gridHost.revalidate();
            gridHost.repaint();
        };
        rebuildGridHolder[0] = rebuildGrid;

        Timer searchDebounceTimer = new Timer(WorkspaceShell.VIEW_ITEMS_SEARCH_DEBOUNCE_MS, e -> rebuildGrid.run());
        searchDebounceTimer.setRepeats(false);

        DocumentListener filterListener = new DocumentListener() {
            private void schedule() {
                searchDebounceTimer.restart();
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                schedule();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                schedule();
            }
        };
        searchField.getDocument().addDocumentListener(filterListener);
        smartFilter.addActionListener(e -> {
            searchDebounceTimer.stop();
            rebuildGrid.run();
        });

        rebuildGrid.run();
        centerWrap.add(gridHost, BorderLayout.CENTER);
        panel.add(centerWrap, BorderLayout.CENTER);
        return panel;
    }

        static List<ViewItemShelfRow> loadViewItemShelfRows(Connection connection) throws SQLException {
        List<ViewItemShelfRow> rows = new ArrayList<>();
        int staleDays = readStaleMarketPriceDays(connection);
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT i.`Item Code`,
                       i.`Item Name`,
                       i.`Stock`,
                       i.`On Order`,
                       i.`ReOrder Trigger`,
                       i.`Market Price`,
                       i.market_price_updated_at,
                       fifo.avg_unit_cost
                FROM inventory i
                LEFT JOIN (
                    SELECT item_code,
                           SUM(CAST(qty_remaining AS REAL) * unit_cost)
                               / SUM(CAST(qty_remaining AS REAL)) AS avg_unit_cost
                    FROM inventory_cost_layers
                    WHERE qty_remaining > 0
                    GROUP BY item_code
                ) fifo ON fifo.item_code = i.`Item Code`
                WHERE i.`Stock` > 0 OR i.`On Order` > 0
                ORDER BY i.`Item Code` ASC
                """
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("Item Code");
                    String name = Objects.toString(rs.getString("Item Name"), "");
                    int stock = rs.getInt("Stock");
                    int onOrder = rs.getInt("On Order");
                    int reorder = rs.getInt("ReOrder Trigger");
                    double mp = rs.getDouble("Market Price");
                    Double market = rs.wasNull() ? null : mp;
                    double avgRaw = rs.getDouble("avg_unit_cost");
                    Double avgCost = rs.wasNull() ? null : avgRaw;
                    Double margin = computeUnrealizedMarginPercent(avgCost, market);
                    String updatedAt = rs.getString("market_price_updated_at");
                    boolean stale = market == null
                            || (stock > 0 && (updatedAt == null || updatedAt.isBlank()
                            || WorkspaceShell.sqlDaysSinceDisplayDate(updatedAt) > staleDays));
                    boolean hasPhoto = viewItemHasReadablePhoto(code);
                    rows.add(new ViewItemShelfRow(
                            code, name, stock, onOrder, reorder, market, margin, stale, hasPhoto));
                }
            }
        }
        return rows;
    }

        static Double computeUnrealizedMarginPercent(Double avgUnitCost, Double marketPrice) {
        if (avgUnitCost == null || marketPrice == null || avgUnitCost <= 1e-12) {
            return null;
        }
        return ((marketPrice - avgUnitCost) / avgUnitCost) * 100.0;
    }

        static void populateViewItemsGrid(
            JPanel scrollBody,
            User user,
            Connection connection,
            JPanel workspaceContainer,
            List<ViewItemShelfRow> rows,
            JPanel[] selectedCard,
            Set<String> favouriteCodes,
            Runnable onFavouritesChanged
    ) {
        int n = rows.size();
        final int cols = WorkspaceShell.VIEW_ITEM_CARD_COLUMNS;
        int numRows = (n + cols - 1) / cols;

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.anchor = GridBagConstraints.NORTH;
        gbc.weightx = 1.0 / cols;
        gbc.weighty = 0;

        for (int r = 0; r < numRows; r++) {
            gbc.gridy = r;
            for (int c = 0; c < cols; c++) {
                int idx = r * cols + c;
                gbc.gridx = c;
                gbc.insets = new Insets(r == 0 ? 2 : 10, 6, 0, 6);

                JPanel slot = null;
                if (idx < n) {
                    ViewItemShelfRow row = rows.get(idx);
                    slot = buildViewItemShelfCard(user, connection, workspaceContainer, row, favouriteCodes, onFavouritesChanged, card -> {
                        if (selectedCard[0] != null && selectedCard[0] != card) {
                            styleViewItemShelfCard(selectedCard[0], false);
                        }
                        selectedCard[0] = card;
                        styleViewItemShelfCard(card, true);
                        WorkspaceShell.notifyAdminItemSelected(row.itemCode());
                        WorkspaceShell.recordRecentItem(row.itemCode(), row.itemName());
                    });
                }

                JPanel cell = new JPanel(new BorderLayout());
                cell.setOpaque(false);
                if (slot != null) {
                    cell.add(slot, BorderLayout.CENTER);
                }
                scrollBody.add(cell, gbc);
            }
        }

        GridBagConstraints glue = new GridBagConstraints();
        glue.gridy = numRows;
        glue.gridx = 0;
        glue.gridwidth = cols;
        glue.weighty = 1.0;
        glue.weightx = 1.0;
        glue.fill = GridBagConstraints.VERTICAL;
        JPanel spacer = new JPanel();
        spacer.setOpaque(false);
        scrollBody.add(spacer, glue);
    }

        /** One View Items card: photo (or placeholder), code, stock, on order, market price. */
    static JPanel buildViewItemShelfCard(
            User user,
            Connection connection,
            JPanel workspaceContainer,
            ViewItemShelfRow row,
            Set<String> favouriteCodes,
            Runnable onFavouritesChanged,
            Consumer<JPanel> onSelect
    ) {
        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        AppUI.markCardSurface(card);
        card.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setOpaque(true);
        card.setBackground(viewItemMarginTint(row.unrealizedMarginPercent()));
        styleViewItemShelfCard(card, false);

        JPanel starRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        starRow.setOpaque(false);
        starRow.setAlignmentX(Component.CENTER_ALIGNMENT);
        starRow.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
        boolean isFavourite = favouriteCodes.contains(row.itemCode());
        JButton starBtn = new JButton(isFavourite ? "\u2605" : "\u2606");
        starBtn.setToolTipText(isFavourite ? "Remove from favourites" : "Add to favourites");
        starBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        starBtn.setFocusPainted(false);
        starBtn.setContentAreaFilled(false);
        starBtn.setForeground(isFavourite ? new Color(0xfbbf24) : AppUI.TEXT_MUTED);
        starBtn.addActionListener(e -> {
            try {
                toggleFavouriteItem(connection, user.getUsername(), row.itemCode());
                onFavouritesChanged.run();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(card, "Could not update favourite: " + ex.getMessage(),
                        "View Items", JOptionPane.ERROR_MESSAGE);
            }
        });
        starRow.add(starBtn);
        card.add(starRow);

        JComponent photo = buildViewItemPhotoThumb(row.itemCode(), WorkspaceShell.VIEW_ITEM_CARD_PHOTO_PX);
        photo.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(photo);
        card.add(Box.createVerticalStrut(10));

        JLabel codeLabel = new JLabel(row.itemCode(), SwingConstants.CENTER);
        codeLabel.setFont(codeLabel.getFont().deriveFont(Font.BOLD, 13f));
        codeLabel.setForeground(AppUI.TEXT);
        codeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(codeLabel);
        card.add(Box.createVerticalStrut(6));

        card.add(viewItemShelfStatLine("In stock:", Integer.toString(row.stock())));
        card.add(Box.createVerticalStrut(2));
        card.add(viewItemShelfStatLine("On order:", Integer.toString(row.onOrder())));
        card.add(Box.createVerticalStrut(2));
        String marketText = row.marketPrice() == null ? "—" : WorkspaceShell.formatUsdMoney(row.marketPrice());
        card.add(viewItemShelfStatLine("Market price:", marketText));
        card.add(Box.createVerticalStrut(2));
        String marginText = row.unrealizedMarginPercent() == null
                ? "—"
                : String.format(Locale.US, "%.1f%%", row.unrealizedMarginPercent());
        card.add(viewItemShelfStatLine("Margin:", marginText));
        if (row.staleMarketPrice()) {
            JLabel stale = new JLabel("Stale price", SwingConstants.CENTER);
            stale.setForeground(new Color(0xfbbf24));
            stale.setFont(stale.getFont().deriveFont(Font.BOLD, 11f));
            stale.setAlignmentX(Component.CENTER_ALIGNMENT);
            card.add(Box.createVerticalStrut(4));
            card.add(stale);
        }

        JPopupMenu menu = new JPopupMenu();
        if (user.hasAdminRights()) {
            JMenuItem editNote = new JMenuItem("Edit note");
            editNote.addActionListener(e -> openItemNoteEditor(user, connection, card, row.itemCode(), row.itemName()));
            menu.add(editNote);

            JMenuItem setMarketPrice = new JMenuItem("Set market price");
            setMarketPrice.addActionListener(e -> showSetMarketPriceDialog(user, connection, card, row.itemCode()));
            menu.add(setMarketPrice);
        }
        JMenuItem openLowStock = new JMenuItem("Open Low Stock Check");
        openLowStock.addActionListener(e -> {
            try {
                WorkspaceShell.showView(workspaceContainer, "Low Stock Check", LowStockPanel.build(connection));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(card, "Unable to open Low Stock Check: " + ex.getMessage(),
                        "View Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        menu.add(openLowStock);
        JMenuItem toggleFav = new JMenuItem(isFavourite ? "Remove from favourites" : "Add to favourites");
        toggleFav.addActionListener(e -> {
            try {
                toggleFavouriteItem(connection, user.getUsername(), row.itemCode());
                onFavouritesChanged.run();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(card, "Could not update favourite: " + ex.getMessage(),
                        "View Items", JOptionPane.ERROR_MESSAGE);
            }
        });
        menu.add(toggleFav);

        card.addMouseListener(new MouseAdapter() {
            private void maybeShowMenu(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    onSelect.accept(card);
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getSource() == starBtn) {
                    return;
                }
                if (e.isPopupTrigger()) {
                    maybeShowMenu(e);
                    return;
                }
                onSelect.accept(card);
                if (e.getClickCount() >= 2) {
                    WorkspaceShell.showItemDetailDialog(card, user, connection, row.itemCode());
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMenu(e);
            }
        });

        return card;
    }

        static JLabel viewItemShelfStatLine(String label, String value) {
        JLabel line = new JLabel("<html><center><span style='color:#a1a1a1'>" + label + "</span> "
                + "<b><span style='color:#fafafa'>" + value + "</span></b></center></html>", SwingConstants.CENTER);
        line.setFont(line.getFont().deriveFont(Font.PLAIN, 12f));
        line.setAlignmentX(Component.CENTER_ALIGNMENT);
        return line;
    }

        static void styleViewItemShelfCard(JPanel card, boolean selected) {
        Color border = selected ? AppUI.PRIMARY : AppUI.BORDER;
        int width = selected ? 2 : 1;
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(border, width),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
    }

        static Color viewItemMarginTint(Double marginPct) {
        if (marginPct == null) {
            return AppUI.SURFACE;
        }
        if (marginPct >= 10.0) {
            return new Color(0x1a2a1f);
        }
        if (marginPct < -5.0) {
            return new Color(0x2b1a1a);
        }
        return new Color(0x2c2619);
    }

        static void openItemNoteEditor(
            User user,
            Connection connection,
            Component parent,
            String itemCode,
            String itemName
    ) {
        WorkspaceShell.AdminMetricsRailHost rail = WorkspaceShell.adminMetricsRailHost;
        if (rail != null) {
            WorkspaceShell.notifyAdminItemSelected(itemCode);
            rail.openItemForNotesEdit(itemCode);
            return;
        }
        try {
            String current = WorkspaceShell.fetchInventoryNotes(connection, itemCode);
            JTextArea editor = new JTextArea(current, 10, 34);
            editor.setLineWrap(true);
            editor.setWrapStyleWord(true);
            JScrollPane scroll = new JScrollPane(editor);
            int ok = JOptionPane.showConfirmDialog(
                    parent,
                    scroll,
                    "Edit note - " + itemCode + (itemName == null || itemName.isBlank() ? "" : " (" + itemName + ")"),
                    JOptionPane.OK_CANCEL_OPTION,
                    JOptionPane.PLAIN_MESSAGE);
            if (ok != JOptionPane.OK_OPTION) {
                return;
            }
            String raw = editor.getText();
            if (raw != null && raw.length() > WorkspaceShell.ITEM_NOTES_MAX_CHARS) {
                JOptionPane.showMessageDialog(parent,
                        "Notes must be at most " + WorkspaceShell.ITEM_NOTES_MAX_CHARS + " characters.",
                        "Input",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            WorkspaceShell.persistInventoryNotes(connection, itemCode, raw);
            InventoryAudit.logChange(connection, user.getUsername(), itemCode,
                    InventoryAudit.CHANGE_NOTE, 0, "VIEW_ITEMS_NOTE_EDIT",
                    raw == null ? "" : raw.trim());
            JOptionPane.showMessageDialog(parent, "Note saved.", "Notes", JOptionPane.INFORMATION_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Database error: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

        static void showSetMarketPriceDialog(User user, Connection connection, Component parent, String itemCode) {
        String raw = JOptionPane.showInputDialog(parent, "Enter market price for " + itemCode + ":", "Set market price",
                JOptionPane.PLAIN_MESSAGE);
        if (raw == null) {
            return;
        }
        try {
            Double parsed = WorkspaceShell.parseOptionalMarketPriceInput(raw);
            if (parsed == null) {
                JOptionPane.showMessageDialog(parent, "Market price is required.", "Input", JOptionPane.WARNING_MESSAGE);
                return;
            }
            Double previous = null;
            try (PreparedStatement sel = connection.prepareStatement(
                    "SELECT `Market Price` FROM inventory WHERE `Item Code` = ?")) {
                sel.setString(1, itemCode);
                try (ResultSet rs = sel.executeQuery()) {
                    if (rs.next()) {
                        double v = rs.getDouble(1);
                        previous = rs.wasNull() ? null : v;
                    }
                }
            }
            try (PreparedStatement ps = connection.prepareStatement(
                    "UPDATE inventory SET `Market Price` = ? WHERE `Item Code` = ?")) {
                ps.setDouble(1, parsed);
                ps.setString(2, itemCode);
                ps.executeUpdate();
            }
            InventoryAudit.touchMarketPriceUpdated(connection, itemCode);
            String beforeText = previous == null ? "null" : String.format(Locale.US, "%.4f", previous);
            String afterText = String.format(Locale.US, "%.4f", parsed);
            InventoryAudit.logChange(connection, user.getUsername(), itemCode,
                    InventoryAudit.CHANGE_MARKET_PRICE, 0, "VIEW_ITEMS_CONTEXT_MENU",
                    "from=" + beforeText + " to=" + afterText);
            WorkspaceShell.refreshActiveMetricsStripNow();
            WorkspaceShell.notifyAdminItemSelected(itemCode);
            JOptionPane.showMessageDialog(parent, "Market price updated.", "Market price",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (NumberFormatException ex) {
            JOptionPane.showMessageDialog(parent, ex.getMessage(), "Input", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Database error: " + ex.getMessage(), "Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

        static void exportViewItemsCsv(Component parent, List<ViewItemShelfRow> rows) {
        if (rows == null || rows.isEmpty()) {
            JOptionPane.showMessageDialog(parent, "No filtered rows to export.", "Export CSV",
                    JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose export folder");
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        Path dir = chooser.getSelectedFile().toPath();
        String base = WorkspaceShell.sanitizeFileName("view_items_filtered_" + System.currentTimeMillis());
        Path csv = dir.resolve(base + ".csv");
        try {
            Files.createDirectories(dir);
            try (BufferedWriter writer = Files.newBufferedWriter(csv)) {
                writer.write("Item Code,Item Name,Stock,On Order,Market Price,Margin %");
                writer.newLine();
                for (ViewItemShelfRow row : rows) {
                    String market = row.marketPrice() == null ? "" : String.format(Locale.US, "%.2f", row.marketPrice());
                    String margin = row.unrealizedMarginPercent() == null ? ""
                            : String.format(Locale.US, "%.2f", row.unrealizedMarginPercent());
                    writer.write(csvCell(row.itemCode()) + "," + csvCell(row.itemName()) + ","
                            + row.stock() + "," + row.onOrder() + "," + market + "," + margin);
                    writer.newLine();
                }
            }
            JOptionPane.showMessageDialog(parent, "Export complete:\n" + csv, "Export CSV",
                    JOptionPane.INFORMATION_MESSAGE);
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(parent, "Could not export CSV: " + ex.getMessage(), "Export CSV",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

        static String csvCell(String value) {
        String v = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + v + "\"";
    }

        /** JPEG thumbnail for View Items cards, or a bordered “?” placeholder when no image is saved. */
    static JComponent buildViewItemPhotoThumb(String itemCode, int boxPx) {
        ImageIcon icon = WorkspaceShell.loadCachedViewItemThumbIcon(itemCode, boxPx);
        if (icon != null) {
            JLabel label = new JLabel(icon, SwingConstants.CENTER);
            label.setPreferredSize(new Dimension(boxPx, boxPx));
            label.setMinimumSize(new Dimension(boxPx, boxPx));
            label.setMaximumSize(new Dimension(boxPx, boxPx));
            label.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(AppUI.BORDER, 1),
                    BorderFactory.createEmptyBorder(4, 4, 4, 4)));
            return label;
        }
        return buildViewItemPhotoPlaceholder(boxPx);
    }

        static JLabel buildViewItemPhotoPlaceholder(int boxPx) {
        JLabel label = new JLabel("?", SwingConstants.CENTER);
        label.setFont(label.getFont().deriveFont(Font.BOLD, 42f));
        label.setForeground(AppUI.TEXT_MUTED);
        label.setPreferredSize(new Dimension(boxPx, boxPx));
        label.setMinimumSize(new Dimension(boxPx, boxPx));
        label.setMaximumSize(new Dimension(boxPx, boxPx));
        label.setOpaque(true);
        label.setBackground(AppUI.SURFACE_ELEVATED);
        label.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppUI.BORDER, 1),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        return label;
    }

        static int readStaleMarketPriceDays(Connection connection) throws SQLException {
        String raw = DatabaseManager.getAppMetadata(connection, DatabaseManager.META_STALE_MARKET_PRICE_DAYS);
        if (raw == null || raw.isBlank()) {
            return WorkspaceShell.DEFAULT_STALE_MARKET_PRICE_DAYS;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v < 1 ? WorkspaceShell.DEFAULT_STALE_MARKET_PRICE_DAYS : Math.min(v, 3650);
        } catch (NumberFormatException ex) {
            return WorkspaceShell.DEFAULT_STALE_MARKET_PRICE_DAYS;
        }
    }

        static String favouriteItemsMetadataKey(String username) {
        return "favourite_items:" + (username == null ? "" : username.trim().toLowerCase(Locale.ROOT));
    }

        static Set<String> readFavouriteItemCodes(Connection connection, String username) throws SQLException {
        String raw = DatabaseManager.getAppMetadata(connection, favouriteItemsMetadataKey(username));
        Set<String> codes = new LinkedHashSet<>();
        if (raw == null || raw.isBlank()) {
            return codes;
        }
        for (String part : raw.split(",")) {
            String code = part.trim();
            if (!code.isEmpty()) {
                codes.add(code);
            }
        }
        return codes;
    }

        static void writeFavouriteItemCodes(Connection connection, String username, Set<String> codes)
            throws SQLException {
        String value = String.join(",", codes);
        DatabaseManager.putAppMetadata(connection, favouriteItemsMetadataKey(username), value);
    }

        static boolean toggleFavouriteItem(Connection connection, String username, String itemCode)
            throws SQLException {
        String code = itemCode == null ? "" : itemCode.trim();
        if (code.isEmpty()) {
            return false;
        }
        Set<String> codes = readFavouriteItemCodes(connection, username);
        boolean nowFavourite;
        if (codes.remove(code)) {
            nowFavourite = false;
        } else {
            codes.add(code);
            nowFavourite = true;
        }
        writeFavouriteItemCodes(connection, username, codes);
        return nowFavourite;
    }

        static boolean viewItemHasReadablePhoto(String itemCode) {
        return Files.isReadable(WorkspaceShell.itemImagePath(itemCode));
    }

        static boolean matchesViewItemsSmartFilter(
            ViewItemShelfRow row,
            String filter,
            Set<String> favourites
    ) {
        if (WorkspaceShell.VIEW_ITEMS_FILTER_FAVOURITES.equals(filter)) {
            return favourites.contains(row.itemCode());
        }
        if (WorkspaceShell.VIEW_ITEMS_FILTER_LOW_STOCK.equals(filter)) {
            return row.reorderTrigger() > 0 && row.reorderTrigger() >= row.stock();
        }
        if (WorkspaceShell.VIEW_ITEMS_FILTER_STALE_PRICE.equals(filter)) {
            return row.staleMarketPrice();
        }
        if (WorkspaceShell.VIEW_ITEMS_FILTER_MISSING_PHOTO.equals(filter)) {
            return !row.hasPhoto();
        }
        if (WorkspaceShell.VIEW_ITEMS_FILTER_HIGH_MARGIN.equals(filter)) {
            return row.unrealizedMarginPercent() != null
                    && row.unrealizedMarginPercent() >= WorkspaceShell.VIEW_ITEMS_HIGH_MARGIN_THRESHOLD_PCT;
        }
        return true;
    }

        static void sortViewItemsShelfRows(List<ViewItemShelfRow> rows, String filter, Set<String> favourites) {
        if (WorkspaceShell.VIEW_ITEMS_FILTER_ALL.equals(filter)) {
            rows.sort((a, b) -> {
                boolean fa = favourites.contains(a.itemCode());
                boolean fb = favourites.contains(b.itemCode());
                if (fa != fb) {
                    return fa ? -1 : 1;
                }
                return a.itemCode().compareToIgnoreCase(b.itemCode());
            });
        } else {
            rows.sort(Comparator.comparing(ViewItemShelfRow::itemCode, String.CASE_INSENSITIVE_ORDER));
        }
    }
}
