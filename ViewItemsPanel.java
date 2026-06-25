import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import java.awt.GridLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.swing.SwingConstants;
import javax.swing.SwingWorker;
import javax.swing.Timer;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableColumn;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public final class ViewItemsPanel {
    private ViewItemsPanel() {}

    private static final String[] VIEW_ITEMS_STYLE_OPTIONS = {"Cards", "List", "Table"};
    private static final int VIEW_ITEM_LIST_THUMB_PX = 56;

    /** One row in the View Items card shelf (stock or on-order must be &gt; 0). */
    record ViewItemShelfRow(
            String itemCode,
            String itemName,
            int stock,
            int onOrder,
            int reorderTrigger,
            Double marketPrice,
            Double unrealizedMarginPercent,
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
        JPanel panel = WorkspaceShell.buildFormPanel("Inventory Items",
                "Only items with stock on hand or units on order are listed. "
                        + "Switch between card, list, and table views. Double-click a row for details.");

        JPanel body = new JPanel(new BorderLayout(0, 0));
        AppUI.applyPanelBackground(body);

        if (user.hasAdminRights()) {
            JPanel photoActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            photoActions.setOpaque(false);
            JButton fetchMissing = new JButton("Fetch missing photos");
            AppUI.stylePrimaryButton(fetchMissing);
            fetchMissing.addActionListener(e -> WorkspaceShell.startItemPhotoFetchTask(
                    frame, workspaceContainer, user, frame, connection, false));
            JButton refetchAll = new JButton("Re-fetch all photos");
            WorkspaceShell.styleSecondaryButton(refetchAll);
            refetchAll.addActionListener(e -> {
                int ok = JOptionPane.showConfirmDialog(frame,
                        "Re-download photos for every inventory item?",
                        "Re-fetch all photos", JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE);
                if (ok == JOptionPane.OK_OPTION) {
                    WorkspaceShell.startItemPhotoFetchTask(frame, workspaceContainer, user, frame, connection, true);
                }
            });
            photoActions.add(fetchMissing);
            photoActions.add(refetchAll);
            body.add(photoActions, BorderLayout.NORTH);
        }

        Object prefillSearch = workspaceContainer.getClientProperty(WorkspaceShell.CLIENT_VIEW_ITEMS_SEARCH_TEXT);
        workspaceContainer.putClientProperty(WorkspaceShell.CLIENT_VIEW_ITEMS_SEARCH_TEXT, null);

        JTextField searchField = new JTextField(24);
        WorkspaceShell.styleInput(searchField);
        AppUI.setPlaceholder(searchField, "Search code or name\u2026");
        if (prefillSearch instanceof String s) {
            searchField.setText(s);
        }

        JComboBox<String> smartFilter = new JComboBox<>(WorkspaceShell.VIEW_ITEMS_FILTER_OPTIONS);
        WorkspaceShell.styleComboMatchInputRow(smartFilter);

        JComboBox<String> viewStyle = new JComboBox<>(VIEW_ITEMS_STYLE_OPTIONS);
        WorkspaceShell.styleComboMatchInputRow(viewStyle);

        JComboBox<String> density = new JComboBox<>(new String[]{
                "Compact (5 cols)", "Comfortable (4 cols)", "Large (3 cols)"});
        WorkspaceShell.styleComboMatchInputRow(density);
        density.setSelectedIndex(1);
        JLabel densityLabel = new JLabel("Density");
        densityLabel.setForeground(AppUI.TEXT_MUTED);

        final List<ViewItemShelfRow>[] filteredRowsHolder = new List[]{new ArrayList<>()};
        final List<ViewItemShelfRow>[] allRowsHolder = new List[]{new ArrayList<>()};
        Set<String> favouriteCodes = readFavouriteItemCodes(connection, user.getUsername());

        JButton exportCsv = new JButton("Export CSV");
        WorkspaceShell.styleSecondaryButton(exportCsv);
        exportCsv.addActionListener(e -> exportViewItemsCsv(frame, filteredRowsHolder[0]));
        JLabel matchCount = new JLabel(" ");
        matchCount.setForeground(AppUI.TEXT_MUTED);
        matchCount.setFont(AppUI.fontCaption(12));

        JPanel stickyFilter = new JPanel(new BorderLayout());
        stickyFilter.setOpaque(true);
        stickyFilter.setBackground(AppUI.SURFACE);
        stickyFilter.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppUI.BORDER_SOFT),
                BorderFactory.createEmptyBorder(12, 0, 12, 0)));

        JPanel filterRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        filterRow.setOpaque(false);
        filterRow.add(new JLabel("Search"));
        filterRow.add(searchField);
        filterRow.add(new JLabel("Filter"));
        filterRow.add(smartFilter);
        filterRow.add(new JLabel("View"));
        filterRow.add(viewStyle);
        filterRow.add(densityLabel);
        filterRow.add(density);
        filterRow.add(exportCsv);
        filterRow.add(matchCount);
        stickyFilter.add(filterRow, BorderLayout.CENTER);

        Runnable syncViewStyleControls = () -> {
            boolean cards = viewStyle.getSelectedIndex() == 0;
            densityLabel.setVisible(cards);
            density.setVisible(cards);
            density.setEnabled(cards);
        };

        JPanel gridHost = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(gridHost);
        gridHost.add(buildSkeletonGrid(), BorderLayout.CENTER);

        final JPanel[] selectedCard = new JPanel[1];
        final JPanel[] selectedListRow = new JPanel[1];
        final Runnable[] rebuildGridHolder = new Runnable[1];

        Runnable rebuildGrid = () -> {
            syncViewStyleControls.run();
            int viewMode = viewStyle.getSelectedIndex();
            int cols = switch (density.getSelectedIndex()) {
                case 0 -> 5;
                case 2 -> 3;
                default -> 4;
            };
            if (viewMode == 0) {
                workspaceContainer.putClientProperty(WorkspaceShell.CLIENT_VIEW_ITEMS_COLUMNS, cols);
            }

            String q = searchField.getText().trim().toLowerCase(Locale.ROOT);
            String filter = Objects.toString(smartFilter.getSelectedItem(), WorkspaceShell.VIEW_ITEMS_FILTER_ALL);
            List<ViewItemShelfRow> allRows = allRowsHolder[0];
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
            selectedListRow[0] = null;
            gridHost.removeAll();
            if (filtered.isEmpty()) {
                gridHost.add(WorkspaceShell.buildSectionText(
                        allRows.isEmpty()
                                ? "No items with stock on hand or on order."
                                : "No items match the current search and filter."), BorderLayout.CENTER);
                matchCount.setText(allRows.isEmpty() ? "" : "0 shown");
            } else {
                JScrollPane scroll;
                if (viewMode == 2) {
                    scroll = buildViewItemsTableScroll(
                            user, connection, workspaceContainer, filtered, favouriteCodes,
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
                } else {
                    JPanel scrollBody = new JPanel();
                    scrollBody.setOpaque(true);
                    AppUI.applyPanelBackground(scrollBody);
                    if (viewMode == 1) {
                        populateViewItemsList(
                                scrollBody, user, connection, workspaceContainer, filtered, selectedListRow,
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
                    } else {
                        scrollBody.setLayout(new GridBagLayout());
                        populateViewItemsGrid(
                                scrollBody, user, connection, workspaceContainer, filtered, cols, selectedCard,
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
                    }
                    scroll = new JScrollPane(scrollBody,
                            JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                            JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                    AppUI.styleScrollPane(scroll);
                    scroll.getViewport().setBackground(scrollBody.getBackground());
                    if (viewMode == 0) {
                        animateGridStagger(scrollBody);
                    }
                }
                scroll.setPreferredSize(new Dimension(1080, Math.min(WorkspaceShell.MAIN_FRAME_BASE_H - 260, 520)));
                gridHost.add(scroll, BorderLayout.CENTER);
                matchCount.setText(filtered.size() + " of " + allRows.size() + " shown");
            }
            gridHost.revalidate();
            gridHost.repaint();
        };
        rebuildGridHolder[0] = rebuildGrid;

        new SwingWorker<List<ViewItemShelfRow>, Void>() {
            @Override
            protected List<ViewItemShelfRow> doInBackground() throws Exception {
                return loadViewItemShelfRows(connection);
            }

            @Override
            protected void done() {
                try {
                    allRowsHolder[0] = get();
                    rebuildGrid.run();
                } catch (Exception ex) {
                    gridHost.removeAll();
                    gridHost.add(WorkspaceShell.buildSectionText("Could not load items: " + ex.getMessage()),
                            BorderLayout.CENTER);
                    gridHost.revalidate();
                }
            }
        }.execute();

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
        density.addActionListener(e -> rebuildGrid.run());
        viewStyle.addActionListener(e -> rebuildGrid.run());
        syncViewStyleControls.run();

        body.add(stickyFilter, BorderLayout.NORTH);
        body.add(gridHost, BorderLayout.CENTER);

        panel.add(body, BorderLayout.CENTER);
        return panel;
    }

    static JPanel buildSkeletonGrid() {
        JPanel host = new JPanel(new GridLayout(2, 4, 12, 12));
        host.setOpaque(false);
        host.setBorder(BorderFactory.createEmptyBorder(8, 0, 0, 0));
        for (int i = 0; i < 8; i++) {
            JPanel sk = new JPanel() {
                private float pulse = 0.4f;
                private final Timer t = new Timer(40, e -> {
                    pulse += 0.03f;
                    if (pulse > 1f) {
                        pulse = 0.4f;
                    }
                    repaint();
                });

                {
                    t.start();
                    setPreferredSize(new Dimension(160, 200));
                    setOpaque(false);
                }

                @Override
                protected void paintComponent(Graphics g) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    int gray = (int) (100 + pulse * 40);
                    g2.setColor(new Color(gray, gray, gray));
                    g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppUI.RADIUS_MD, AppUI.RADIUS_MD);
                    g2.dispose();
                }
            };
            host.add(sk);
        }
        return host;
    }

    static void animateGridStagger(JPanel scrollBody) {
        Component[] cells = scrollBody.getComponents();
        int delay = 0;
        for (Component cell : cells) {
            if (!(cell instanceof JPanel)) {
                continue;
            }
            Component inner = ((JPanel) cell).getComponentCount() > 0 ? ((JPanel) cell).getComponent(0) : null;
            if (inner == null) {
                continue;
            }
            inner.setVisible(false);
            int d = delay;
            Timer t = new Timer(d, null);
            t.setRepeats(false);
            t.addActionListener(e -> inner.setVisible(true));
            t.start();
            delay += 35;
        }
    }

        static List<ViewItemShelfRow> loadViewItemShelfRows(Connection connection) throws SQLException {
        List<ViewItemShelfRow> rows = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement(
                """
                SELECT i.`Item Code`,
                       i.`Item Name`,
                       i.`Stock`,
                       i.`On Order`,
                       i.`ReOrder Trigger`,
                       i.`Market Price`,
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
                    boolean hasPhoto = viewItemHasReadablePhoto(code);
                    rows.add(new ViewItemShelfRow(
                            code, name, stock, onOrder, reorder, market, margin, hasPhoto));
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
            int cols,
            JPanel[] selectedCard,
            Set<String> favouriteCodes,
            Runnable onFavouritesChanged
    ) {
        int n = rows.size();
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
                            styleViewItemShelfCard(selectedCard[0], false, false);
                        }
                        selectedCard[0] = card;
                        card.putClientProperty("ims.card.selected", Boolean.TRUE);
                        styleViewItemShelfCard(card, true, false);
                        notifyViewItemSelected(row);
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

    static void notifyViewItemSelected(ViewItemShelfRow row) {
        WorkspaceShell.notifyAdminItemSelected(row.itemCode());
        WorkspaceShell.recordRecentItem(row.itemCode(), row.itemName());
    }

    static void populateViewItemContextMenu(
            JPopupMenu menu,
            User user,
            Connection connection,
            JPanel workspaceContainer,
            ViewItemShelfRow row,
            Set<String> favouriteCodes,
            Runnable onFavouritesChanged,
            Component parent
    ) {
        boolean isFavourite = favouriteCodes.contains(row.itemCode());
        if (user.hasAdminRights()) {
            JMenuItem editNote = new JMenuItem("Edit note");
            editNote.addActionListener(e -> openItemNoteEditor(user, connection, parent, row.itemCode(), row.itemName()));
            menu.add(editNote);

            JMenuItem setMarketPrice = new JMenuItem("Set market price");
            setMarketPrice.addActionListener(e -> showSetMarketPriceDialog(user, connection, parent, row.itemCode()));
            menu.add(setMarketPrice);
        }
        JMenuItem openLowStock = new JMenuItem("Open Low Stock Check");
        openLowStock.addActionListener(e -> {
            try {
                WorkspaceShell.showView(workspaceContainer, "Low Stock Check", LowStockPanel.build(connection));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(parent, "Unable to open Low Stock Check: " + ex.getMessage(),
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
                JOptionPane.showMessageDialog(parent, "Could not update favourite: " + ex.getMessage(),
                        "View Items", JOptionPane.ERROR_MESSAGE);
            }
        });
        menu.add(toggleFav);
    }

    static void populateViewItemsList(
            JPanel scrollBody,
            User user,
            Connection connection,
            JPanel workspaceContainer,
            List<ViewItemShelfRow> rows,
            JPanel[] selectedListRow,
            Set<String> favouriteCodes,
            Runnable onFavouritesChanged
    ) {
        scrollBody.setLayout(new BoxLayout(scrollBody, BoxLayout.Y_AXIS));
        for (ViewItemShelfRow row : rows) {
            scrollBody.add(buildViewItemListRow(
                    user, connection, workspaceContainer, row, selectedListRow, favouriteCodes, onFavouritesChanged));
            scrollBody.add(Box.createVerticalStrut(6));
        }
        scrollBody.add(Box.createVerticalGlue());
    }

    static JPanel buildViewItemListRow(
            User user,
            Connection connection,
            JPanel workspaceContainer,
            ViewItemShelfRow row,
            JPanel[] selectedListRow,
            Set<String> favouriteCodes,
            Runnable onFavouritesChanged
    ) {
        JPanel rowPanel = new JPanel(new BorderLayout(12, 0));
        rowPanel.setOpaque(true);
        rowPanel.setBackground(AppUI.SURFACE);
        rowPanel.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        rowPanel.setMaximumSize(new Dimension(Integer.MAX_VALUE, 88));
        styleViewItemListRow(rowPanel, false, false);

        JComponent thumb = buildViewItemPhotoThumb(row.itemCode(), row.itemName(), VIEW_ITEM_LIST_THUMB_PX);
        rowPanel.add(thumb, BorderLayout.WEST);

        JPanel center = new JPanel();
        center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
        center.setOpaque(false);
        JLabel code = new JLabel(row.itemCode());
        code.setFont(AppUI.fontSemiBold(13));
        code.setForeground(AppUI.TEXT);
        JLabel name = new JLabel(row.itemName());
        name.setFont(AppUI.fontCaption(12));
        name.setForeground(AppUI.TEXT_MUTED);
        center.add(code);
        center.add(Box.createVerticalStrut(2));
        center.add(name);
        center.add(Box.createVerticalStrut(6));
        JPanel badges = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        badges.setOpaque(false);
        badges.setAlignmentX(Component.LEFT_ALIGNMENT);
        Color stockBg = isViewItemLowStock(row) ? new Color(0x422006) : new Color(0x14532d);
        Color stockFg = isViewItemLowStock(row) ? AppUI.WARNING : AppUI.SUCCESS;
        badges.add(AppUI.createBadge("Stock " + row.stock(), stockBg, stockFg));
        if (isViewItemLowStock(row)) {
            badges.add(AppUI.createBadge("Low stock", new Color(0x422006), AppUI.WARNING));
        }
        if (row.onOrder() > 0) {
            badges.add(AppUI.createBadge("On order " + row.onOrder(), new Color(0x1e3a5f), AppUI.PRIMARY));
        }
        center.add(badges);
        rowPanel.add(center, BorderLayout.CENTER);

        JPanel east = new JPanel();
        east.setLayout(new BoxLayout(east, BoxLayout.Y_AXIS));
        east.setOpaque(false);
        String marketText = row.marketPrice() == null ? "\u2014" : WorkspaceShell.formatUsdMoney(row.marketPrice());
        JLabel market = new JLabel(marketText);
        market.setFont(AppUI.fontSemiBold(13));
        market.setForeground(AppUI.TEXT);
        market.setAlignmentX(Component.RIGHT_ALIGNMENT);
        String marginText = row.unrealizedMarginPercent() == null
                ? "\u2014"
                : String.format(Locale.US, "%.1f%% margin", row.unrealizedMarginPercent());
        JLabel margin = new JLabel(marginText);
        margin.setFont(AppUI.fontCaption(11));
        margin.setForeground(row.unrealizedMarginPercent() != null && row.unrealizedMarginPercent() >= 0
                ? AppUI.SUCCESS : AppUI.TEXT_MUTED);
        margin.setAlignmentX(Component.RIGHT_ALIGNMENT);
        east.add(market);
        east.add(Box.createVerticalStrut(2));
        east.add(margin);
        east.add(Box.createVerticalStrut(4));
        boolean isFavourite = favouriteCodes.contains(row.itemCode());
        JButton starBtn = new JButton(isFavourite ? "\u2605" : "\u2606");
        starBtn.setAlignmentX(Component.RIGHT_ALIGNMENT);
        starBtn.setBorder(BorderFactory.createEmptyBorder(0, 4, 0, 4));
        starBtn.setFocusPainted(false);
        starBtn.setContentAreaFilled(false);
        starBtn.setForeground(isFavourite ? AppUI.WARNING : AppUI.TEXT_MUTED);
        starBtn.addActionListener(e -> {
            animateStar(starBtn);
            try {
                toggleFavouriteItem(connection, user.getUsername(), row.itemCode());
                onFavouritesChanged.run();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(rowPanel, "Could not update favourite: " + ex.getMessage(),
                        "View Items", JOptionPane.ERROR_MESSAGE);
            }
        });
        east.add(starBtn);
        rowPanel.add(east, BorderLayout.EAST);

        JPopupMenu menu = new JPopupMenu();
        populateViewItemContextMenu(menu, user, connection, workspaceContainer, row, favouriteCodes, onFavouritesChanged, rowPanel);

        Runnable selectRow = () -> {
            if (selectedListRow[0] != null && selectedListRow[0] != rowPanel) {
                styleViewItemListRow(selectedListRow[0], false, false);
            }
            selectedListRow[0] = rowPanel;
            styleViewItemListRow(rowPanel, true, false);
            notifyViewItemSelected(row);
        };

        rowPanel.addMouseListener(new MouseAdapter() {
            private void maybeShowMenu(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    selectRow.run();
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (selectedListRow[0] != rowPanel) {
                    styleViewItemListRow(rowPanel, false, true);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (selectedListRow[0] != rowPanel) {
                    styleViewItemListRow(rowPanel, false, false);
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
                selectRow.run();
                if (e.getClickCount() >= 2) {
                    WorkspaceShell.showItemDetailDialog(rowPanel, user, connection, row.itemCode());
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

        return rowPanel;
    }

    static void styleViewItemListRow(JPanel row, boolean selected, boolean hover) {
        Color border = selected ? AppUI.PRIMARY : (hover ? AppUI.brighten(AppUI.BORDER_SOFT, 30) : AppUI.BORDER_SOFT);
        int width = selected ? 2 : 1;
        row.setBorder(new RoundedBorder(AppUI.RADIUS_SM, border, width, AppUI.SURFACE,
                new Insets(8, 10, 8, 10)));
        if (hover && !selected) {
            row.setBackground(AppUI.brighten(AppUI.SURFACE, 8));
        } else {
            row.setBackground(AppUI.SURFACE);
        }
    }

    static JScrollPane buildViewItemsTableScroll(
            User user,
            Connection connection,
            JPanel workspaceContainer,
            List<ViewItemShelfRow> rows,
            Set<String> favouriteCodes,
            Runnable onFavouritesChanged
    ) {
        String[] columns = {"", "Code", "Name", "Stock", "On order", "Market", "Margin", "Flags"};
        Object[][] data = new Object[rows.size()][columns.length];
        for (int i = 0; i < rows.size(); i++) {
            ViewItemShelfRow row = rows.get(i);
            data[i][0] = favouriteCodes.contains(row.itemCode()) ? "\u2605" : "\u2606";
            data[i][1] = row.itemCode();
            data[i][2] = row.itemName();
            data[i][3] = row.stock();
            data[i][4] = row.onOrder();
            data[i][5] = row.marketPrice() == null ? "\u2014" : WorkspaceShell.formatUsdMoney(row.marketPrice());
            data[i][6] = row.unrealizedMarginPercent() == null
                    ? "\u2014"
                    : String.format(Locale.US, "%.1f%%", row.unrealizedMarginPercent());
            StringBuilder flags = new StringBuilder();
            if (isViewItemLowStock(row)) {
                flags.append("Low");
            }
            data[i][7] = flags.length() == 0 ? "" : flags.toString();
        }

        DefaultTableModel model = new DefaultTableModel(data, columns) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int column) {
                if (column == 3 || column == 4) {
                    return Integer.class;
                }
                return String.class;
            }
        };

        JTable table = new JTable(model);
        table.setBackground(AppUI.SURFACE);
        table.setForeground(AppUI.TEXT);
        table.setRowHeight(38);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setSelectionBackground(AppUI.SELECTION);
        table.setSelectionForeground(AppUI.TEXT);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setFont(AppUI.fontBody(13));
        JTableHeader header = table.getTableHeader();
        if (header != null) {
            header.setBackground(AppUI.SURFACE_ELEVATED);
            header.setForeground(AppUI.TEXT);
            header.setFont(AppUI.fontSemiBold(12));
            header.setPreferredSize(new Dimension(header.getPreferredSize().width, AppUI.TABLE_ROW_HEIGHT + 4));
            header.setReorderingAllowed(false);
        }

        TableColumn starCol = table.getColumnModel().getColumn(0);
        starCol.setMaxWidth(36);
        starCol.setMinWidth(36);
        starCol.setCellRenderer(viewItemsTableRenderer(SwingConstants.CENTER, (renderer, value, modelRow, isSelected) -> {
            if (!isSelected && "\u2605".equals(String.valueOf(value))) {
                renderer.setForeground(AppUI.WARNING);
            }
        }));
        table.getColumnModel().getColumn(1).setPreferredWidth(88);
        table.getColumnModel().getColumn(1).setCellRenderer(viewItemsTableRenderer(SwingConstants.LEFT, null));
        table.getColumnModel().getColumn(2).setPreferredWidth(220);
        table.getColumnModel().getColumn(2).setCellRenderer(viewItemsTableRenderer(SwingConstants.LEFT, null));
        table.getColumnModel().getColumn(3).setPreferredWidth(56);
        table.getColumnModel().getColumn(3).setCellRenderer(viewItemsTableRenderer(SwingConstants.RIGHT,
                (renderer, value, modelRow, isSelected) -> {
                    if (!isSelected && modelRow >= 0 && modelRow < rows.size()
                            && isViewItemLowStock(rows.get(modelRow))) {
                        renderer.setForeground(AppUI.WARNING);
                    }
                }));
        table.getColumnModel().getColumn(4).setPreferredWidth(64);
        table.getColumnModel().getColumn(4).setCellRenderer(viewItemsTableRenderer(SwingConstants.RIGHT, null));
        table.getColumnModel().getColumn(5).setPreferredWidth(96);
        table.getColumnModel().getColumn(5).setCellRenderer(viewItemsTableRenderer(SwingConstants.RIGHT, null));
        table.getColumnModel().getColumn(6).setPreferredWidth(72);
        table.getColumnModel().getColumn(6).setCellRenderer(viewItemsTableRenderer(SwingConstants.RIGHT,
                (renderer, value, modelRow, isSelected) -> {
                    if (isSelected || !(value instanceof String s) || "\u2014".equals(s)) {
                        return;
                    }
                    try {
                        double pct = Double.parseDouble(s.replace("%", "").trim());
                        renderer.setForeground(pct >= 0 ? AppUI.SUCCESS : AppUI.DANGER);
                    } catch (NumberFormatException ignored) {
                        renderer.setForeground(AppUI.TEXT);
                    }
                }));
        table.getColumnModel().getColumn(7).setPreferredWidth(88);
        table.getColumnModel().getColumn(7).setCellRenderer(viewItemsTableRenderer(SwingConstants.LEFT,
                (renderer, value, modelRow, isSelected) -> {
                    if (!isSelected && value instanceof String s && s.contains("Low")) {
                        renderer.setForeground(AppUI.WARNING);
                    }
                }));

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int rowIdx = table.rowAtPoint(e.getPoint());
                int colIdx = table.columnAtPoint(e.getPoint());
                if (rowIdx < 0 || rowIdx >= rows.size()) {
                    return;
                }
                ViewItemShelfRow itemRow = rows.get(rowIdx);
                if (colIdx == 0 && e.getClickCount() == 1) {
                    try {
                        toggleFavouriteItem(connection, user.getUsername(), itemRow.itemCode());
                        onFavouritesChanged.run();
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(table, "Could not update favourite: " + ex.getMessage(),
                                "View Items", JOptionPane.ERROR_MESSAGE);
                    }
                    return;
                }
                if (e.getClickCount() >= 2) {
                    WorkspaceShell.showItemDetailDialog(table, user, connection, itemRow.itemCode());
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTableContextMenu(e);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    showTableContextMenu(e);
                }
            }

            private void showTableContextMenu(MouseEvent e) {
                int rowIdx = table.rowAtPoint(e.getPoint());
                if (rowIdx < 0 || rowIdx >= rows.size()) {
                    return;
                }
                table.setRowSelectionInterval(rowIdx, rowIdx);
                ViewItemShelfRow itemRow = rows.get(rowIdx);
                JPopupMenu menu = new JPopupMenu();
                populateViewItemContextMenu(menu, user, connection, workspaceContainer, itemRow,
                        favouriteCodes, onFavouritesChanged, table);
                menu.show(table, e.getX(), e.getY());
            }
        });

        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int rowIdx = table.getSelectedRow();
            if (rowIdx >= 0 && rowIdx < rows.size()) {
                notifyViewItemSelected(rows.get(rowIdx));
            }
        });

        JScrollPane scroll = new JScrollPane(table,
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        AppUI.styleScrollPane(scroll);
        scroll.getViewport().setBackground(AppUI.SURFACE);
        return scroll;
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
        JPanel cardOuter = new JPanel(new BorderLayout());
        cardOuter.setOpaque(false);
        cardOuter.setAlignmentX(Component.CENTER_ALIGNMENT);
        cardOuter.setMaximumSize(new Dimension(240, Integer.MAX_VALUE));

        JPanel card = new JPanel();
        card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
        AppUI.markCardSurface(card);
        card.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        card.setOpaque(true);
        card.setBackground(AppUI.SURFACE);
        styleViewItemShelfCard(card, false, false);
        card.putClientProperty("ims.card.selected", Boolean.FALSE);

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
        starBtn.setForeground(isFavourite ? AppUI.WARNING : AppUI.TEXT_MUTED);
        starBtn.addActionListener(e -> {
            animateStar(starBtn);
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

        JComponent photo = buildViewItemPhotoThumb(row.itemCode(), row.itemName(), WorkspaceShell.VIEW_ITEM_CARD_PHOTO_PX);
        photo.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(photo);
        card.add(Box.createVerticalStrut(10));

        JLabel codeLabel = new JLabel(row.itemCode(), SwingConstants.CENTER);
        codeLabel.setFont(AppUI.fontSemiBold(13));
        codeLabel.setForeground(AppUI.TEXT);
        codeLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        card.add(codeLabel);
        card.add(Box.createVerticalStrut(8));

        JPanel badges = new JPanel(new FlowLayout(FlowLayout.CENTER, 6, 4));
        badges.setOpaque(false);
        badges.setAlignmentX(Component.CENTER_ALIGNMENT);
        Color stockBg = isViewItemLowStock(row) ? new Color(0x422006) : new Color(0x14532d);
        Color stockFg = isViewItemLowStock(row) ? AppUI.WARNING : AppUI.SUCCESS;
        badges.add(AppUI.createBadge("Stock " + row.stock(), stockBg, stockFg));
        if (isViewItemLowStock(row)) {
            badges.add(AppUI.createBadge("Low stock", new Color(0x422006), AppUI.WARNING));
        }
        if (row.onOrder() > 0) {
            badges.add(AppUI.createBadge("On order " + row.onOrder(), new Color(0x1e3a5f), AppUI.PRIMARY));
        }
        card.add(badges);

        String marketText = row.marketPrice() == null ? "\u2014" : WorkspaceShell.formatUsdMoney(row.marketPrice());
        card.add(viewItemShelfStatLine("Market:", marketText));
        String marginText = row.unrealizedMarginPercent() == null
                ? "\u2014"
                : String.format(Locale.US, "%.1f%%", row.unrealizedMarginPercent());
        card.add(viewItemShelfStatLine("Margin:", marginText));

        JPanel marginBar = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setColor(marginBarColor(row.unrealizedMarginPercent()));
                g2.fillRect(0, 0, getWidth(), getHeight());
                g2.dispose();
            }
        };
        marginBar.setPreferredSize(new Dimension(100, 4));
        marginBar.setMaximumSize(new Dimension(Integer.MAX_VALUE, 4));
        marginBar.setAlignmentX(Component.CENTER_ALIGNMENT);
        cardOuter.add(card, BorderLayout.CENTER);
        cardOuter.add(marginBar, BorderLayout.SOUTH);

        JPopupMenu menu = new JPopupMenu();
        populateViewItemContextMenu(menu, user, connection, workspaceContainer, row, favouriteCodes, onFavouritesChanged, card);

        card.addMouseListener(new MouseAdapter() {
            private void maybeShowMenu(MouseEvent e) {
                if (e.isPopupTrigger()) {
                    onSelect.accept(card);
                    menu.show(e.getComponent(), e.getX(), e.getY());
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (!Boolean.TRUE.equals(card.getClientProperty("ims.card.selected"))) {
                    styleViewItemShelfCard(card, false, true);
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (!Boolean.TRUE.equals(card.getClientProperty("ims.card.selected"))) {
                    styleViewItemShelfCard(card, false, false);
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

        return cardOuter;
    }

    static void animateStar(JButton starBtn) {
        Font base = starBtn.getFont();
        starBtn.setFont(base.deriveFont(base.getSize2D() + 4f));
        Timer t = new Timer(120, null);
        t.setRepeats(false);
        t.addActionListener(e -> starBtn.setFont(base));
        t.start();
    }

    static Color marginBarColor(Double marginPct) {
        if (marginPct == null) {
            return AppUI.BORDER_SOFT;
        }
        if (marginPct >= 10.0) {
            return AppUI.SUCCESS;
        }
        if (marginPct < 0) {
            return AppUI.DANGER;
        }
        return AppUI.WARNING;
    }

        static JLabel viewItemShelfStatLine(String label, String value) {
        JLabel line = new JLabel("<html><center><span style='color:#a1a1a1'>" + label + "</span> "
                + "<b><span style='color:#fafafa'>" + value + "</span></b></center></html>", SwingConstants.CENTER);
        line.setFont(AppUI.fontCaption(12));
        line.setAlignmentX(Component.CENTER_ALIGNMENT);
        return line;
    }

    static void styleViewItemShelfCard(JPanel card, boolean selected, boolean hover) {
        card.putClientProperty("ims.card.selected", selected);
        Color border = selected ? AppUI.PRIMARY : (hover ? AppUI.brighten(AppUI.BORDER_SOFT, 30) : AppUI.BORDER_SOFT);
        int width = selected ? 2 : 1;
        card.setBorder(BorderFactory.createCompoundBorder(
                new RoundedBorder(AppUI.RADIUS_MD, border, width, AppUI.SURFACE,
                        new Insets(12, 12, 12, 12)),
                BorderFactory.createEmptyBorder(0, 0, 0, 0)));
        if (hover && !selected) {
            card.setBackground(AppUI.brighten(AppUI.SURFACE, 8));
        } else {
            card.setBackground(AppUI.SURFACE);
        }
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

    /** JPEG thumbnail for View Items cards, or initials placeholder when no image is saved. */
    static JComponent buildViewItemPhotoThumb(String itemCode, String itemName, int boxPx) {
        ImageIcon icon = WorkspaceShell.loadCachedViewItemThumbIcon(itemCode, boxPx);
        if (icon != null) {
            JLabel label = new JLabel(icon, SwingConstants.CENTER);
            label.setPreferredSize(new Dimension(boxPx, boxPx));
            label.setMinimumSize(new Dimension(boxPx, boxPx));
            label.setMaximumSize(new Dimension(boxPx, boxPx));
            label.setBorder(new RoundedBorder(AppUI.RADIUS_SM, AppUI.BORDER_SOFT, 1, null,
                    new Insets(4, 4, 4, 4)));
            return label;
        }
        return buildViewItemPhotoPlaceholder(itemCode, itemName, boxPx);
    }

    static JLabel buildViewItemPhotoPlaceholder(String itemCode, String itemName, int boxPx) {
        String initials = itemInitials(itemCode, itemName);
        JLabel label = new JLabel(initials, SwingConstants.CENTER) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(new Color(0x1e293b));
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), AppUI.RADIUS_SM, AppUI.RADIUS_SM);
                g2.dispose();
                super.paintComponent(g);
            }
        };
        label.setFont(AppUI.fontSemiBold(Math.min(28f, boxPx / 3f)));
        label.setForeground(AppUI.PRIMARY);
        label.setPreferredSize(new Dimension(boxPx, boxPx));
        label.setMinimumSize(new Dimension(boxPx, boxPx));
        label.setMaximumSize(new Dimension(boxPx, boxPx));
        label.setOpaque(false);
        return label;
    }

    static String itemInitials(String itemCode, String itemName) {
        if (itemName != null && !itemName.isBlank()) {
            String[] parts = itemName.trim().split("\\s+");
            if (parts.length >= 2) {
                return ("" + parts[0].charAt(0) + parts[1].charAt(0)).toUpperCase(Locale.ROOT);
            }
            return parts[0].substring(0, Math.min(2, parts[0].length())).toUpperCase(Locale.ROOT);
        }
        if (itemCode != null && itemCode.length() >= 2) {
            return itemCode.substring(0, 2).toUpperCase(Locale.ROOT);
        }
        return "?";
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

    static boolean isViewItemLowStock(ViewItemShelfRow row) {
        return WorkspaceShell.isLowStockSku(row.stock(), row.reorderTrigger());
    }

    @FunctionalInterface
    interface ViewItemsTableCellStyler {
        void apply(DefaultTableCellRenderer renderer, Object value, int modelRow, boolean isSelected);
    }

    static DefaultTableCellRenderer viewItemsTableRenderer(int alignment, ViewItemsTableCellStyler styler) {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(
                    JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column
            ) {
                super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                setHorizontalAlignment(alignment);
                setFont(AppUI.fontBody(13));
                setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
                if (!isSelected) {
                    setBackground(row % 2 == 0 ? AppUI.SURFACE : AppUI.SURFACE_ELEVATED);
                    setForeground(AppUI.TEXT);
                } else {
                    setBackground(t.getSelectionBackground());
                    setForeground(t.getSelectionForeground());
                }
                if (styler != null) {
                    styler.apply(this, value, t.convertRowIndexToModel(row), isSelected);
                }
                return this;
            }
        };
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
            return isViewItemLowStock(row);
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
