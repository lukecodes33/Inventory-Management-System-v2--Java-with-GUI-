import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.DefaultListModel;
import javax.swing.ListSelectionModel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.RowFilter;
import javax.swing.JComboBox;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableColumn;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableModel;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;
import javax.swing.Timer;
import javax.swing.AbstractAction;
import javax.swing.ActionMap;
import javax.swing.InputMap;
import javax.swing.KeyStroke;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.Toolkit;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.StringSelection;
import java.awt.event.KeyEvent;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.JDialog;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.imageio.ImageIO;

/**
 * Main Swing workspace after sign-in: session bar, sidebar navigation, card-based views for inventory and sales,
 * admin-only metrics rail and item photo rail, embedded flows (purchase orders, returns, reports), and helpers
 * for item images and CSV export.
 * <p>
 * Each item may have one on-disk JPEG at {@code item_images/&lt;Item Code&gt;.jpeg}. Every place that shows that
 * image uses the same maximum width and height (420×420 pixels): admin item rail and read-only item detail.
 * Images are never upscaled beyond their file resolution (see {@link #loadScaledItemPhotoIcon(Path, int, int)}).
 * </p>
 * <p>{@link AccountActions} provides backup flows and modal password reset from the sidebar.
 * </p>
 * <p>Larger flows are grouped in {@code build*} panel factories (inventory, PO tracking, receipts, sales, reporting,
 * user admin) plus {@link #open} wiring the shell frame split layout.</p>
 * <p>Hundreds of small {@code static} Swing helpers compose those screens; undocumented helpers use
 * descriptive names aligned with sidebar labels {@code (“buildXxxPanel”)}, reserving block comments where behavior is subtle.</p>
 */
public final class WorkspaceShell {
    static final int INPUT_HEIGHT = AppUI.CONTROL_HEIGHT;
    /** Add Item row field height (~20% above prior compact height); horizontal size uses preferred width. */
    static final int ADD_ITEM_INPUT_HEIGHT = 38;
    /** Preferred side length for staged JPEG preview on Add Item (square). */
    static final int ADD_ITEM_PHOTO_PREVIEW_MAX = 300;
    static final int TABLE_ROW_HEIGHT = 32;
    static final int FORM_LABEL_MIN_WIDTH = 148;
    static final Insets FORM_GRID_INSETS = new Insets(6, 0, 6, 14);
    /** Max characters stored on {@code movements.Reason} for purchase-order cancellations. */
    static final int PO_CANCEL_REASON_MAX_CHARS = 500;
    static final int ITEM_DESC_COLUMN_MIN_WIDTH = 80;
    static final int ITEM_DESC_COLUMN_MAX_WIDTH = 560;
    /** Optional centered image on the initial workspace card (PNG/JPG/GIF); omitted when missing. */
    static final Path WORKSPACE_WELCOME_IMAGE = Paths.get("workspace_welcome.png");
    /** On-disk JPEG per item: {@code item_images/<Item Code>.jpeg}. */
    static final Path ITEM_IMAGES_DIR = Paths.get("item_images");
    /** Max width when downscaling item JPEGs for display (shared by rail, detail, and upload preview). */
    static final int ITEM_PHOTO_DISPLAY_MAX_W = 420;
    /** Max height when downscaling item JPEGs for display (shared by rail, detail, and upload preview). */
    static final int ITEM_PHOTO_DISPLAY_MAX_H = 420;
    /** Default frame height scale; width uses the same base percent then {@link #MAIN_FRAME_WIDTH_EXTRA_FACTOR}. */
    static final int MAIN_FRAME_BASE_W = 1180;
    static final int MAIN_FRAME_BASE_H = 760;
    static final int MAIN_FRAME_HEIGHT_SCALE_PERCENT = 125;
    /** Multiplies default width after height-scale (e.g. 1.25 → 25% wider than the height-scaled width). */
    static final double MAIN_FRAME_WIDTH_EXTRA_FACTOR = 1.25;
    static final int SIDEBAR_TARGET_WIDTH = 300;
    static final int WORKSPACE_MIN_WIDTH = 360;
    /** Metrics / photo rail width (admin layout); fits {@link #ITEM_PHOTO_DISPLAY_MAX_W} plus padding. */
    static final int ADMIN_METRICS_RAIL_OUTER_PX = 448;
    static final int MAIN_AREA_MIN_FOR_METRICS = 420;
    /** Max length for {@code Inventory.Notes} (photo rail and Add Item). */
    static final int ITEM_NOTES_MAX_CHARS = 4000;
    /** Thumbnail box in View Items card grid. */
    static final int VIEW_ITEM_CARD_PHOTO_PX = 148;
    static final int VIEW_ITEM_CARD_COLUMNS = 4;
    static final int VIEW_ITEMS_SEARCH_DEBOUNCE_MS = 200;

    /** Cached View Items card thumbnails; {@link #VIEW_ITEM_THUMB_CACHE_MISS} marks a known-missing file. */
    static final ConcurrentHashMap<String, Object> viewItemThumbCache = new ConcurrentHashMap<>();
    static final Object VIEW_ITEM_THUMB_CACHE_MISS = new Object();

    /** {@link JPanel#getClientProperty(Object)} key for sidebar nav selection sync. */
    static final String CLIENT_NAV_SIDEBAR_SELECTOR = "ims.NavSidebarSelector";
    /** View Items: pre-fill search field text. */
    static final String CLIENT_VIEW_ITEMS_SEARCH_TEXT = "ims.viewItemsSearchText";
    static final String CLIENT_RECENT_REFRESHER = "ims.recentRefresher";
    static final int DEFAULT_BACKUP_REMINDER_DAYS = 7;
    static final int DEFAULT_STALE_MARKET_PRICE_DAYS = 90;
    static final double VIEW_ITEMS_HIGH_MARGIN_THRESHOLD_PCT = 20.0;
    static final String VIEW_ITEMS_FILTER_ALL = "All items";
    static final String VIEW_ITEMS_FILTER_FAVOURITES = "Favourites only";
    static final String VIEW_ITEMS_FILTER_LOW_STOCK = "Low stock";
    static final String VIEW_ITEMS_FILTER_STALE_PRICE = "Stale market price";
    static final String VIEW_ITEMS_FILTER_MISSING_PHOTO = "Missing photo";
    static final String VIEW_ITEMS_FILTER_HIGH_MARGIN = "High margin";
    static final String[] VIEW_ITEMS_FILTER_OPTIONS = {
            VIEW_ITEMS_FILTER_ALL,
            VIEW_ITEMS_FILTER_FAVOURITES,
            VIEW_ITEMS_FILTER_LOW_STOCK,
            VIEW_ITEMS_FILTER_STALE_PRICE,
            VIEW_ITEMS_FILTER_MISSING_PHOTO,
            VIEW_ITEMS_FILTER_HIGH_MARGIN
    };

    /** CardLayout orphans prior components if the same sidebar key builds a new panel; we remove the old instance before re-adding. */
    static final String CLIENT_WORKSPACE_CARD_REGISTRY = "ims.workspaceCardRegistry";

    /** Workspace card key and sidebar label for creating POs and viewing pending lines (reference / tracking). */
    static final String VIEW_PO_TRACKING = "PO/Tracking Number";
    /** Admin workspace card: user management, password reset, and backups in tabs. */
    static final String VIEW_ADMIN_TOOLS = "Administration Tools";

    /** Sentinel {@code StorageLocationPick.id} meaning "every location" in Stock by Location. */
    static final int STOCK_REPORT_ALL_LOCATIONS_ID = -1;

    /** Sidebar nav: default (card surface). */
    static final Color SIDEBAR_NAV_DEFAULT_BG = AppUI.SURFACE;
    static final Color SIDEBAR_NAV_DEFAULT_FG = AppUI.TEXT;
    /** Sidebar nav: selected tab (elevated + teal label). */
    static final Color SIDEBAR_NAV_SELECTED_BG = AppUI.SURFACE_ELEVATED;
    static final Color SIDEBAR_NAV_SELECTED_FG = AppUI.PRIMARY;

    static final int MAX_SALE_TRANSACTION_NOTE_LENGTH = 2000;

    /**
     * Normalizes optional per-checkout sale note for storage (trim, empty → {@code null}, max length).
     *
     * @param raw user-entered note, may be null
     * @return trimmed text or {@code null} when absent
     */
    static String normalizedSaleTransactionNote(String raw) {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        if (t.length() > MAX_SALE_TRANSACTION_NOTE_LENGTH) {
            return t.substring(0, MAX_SALE_TRANSACTION_NOTE_LENGTH);
        }
        return t;
    }

    /**
     * @param amount USD scalar (may be negative for losses)
     * @return localized currency string ({@code $} prefix, separators)
     */
    static String formatUsdMoney(double amount) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("$#,##0.00;-$#,##0.00", sym);
        return df.format(amount);
    }

    /**
     * Parses a currency-like or decimal market price ({@code $}, commas stripped). Blank or null ⇒ caller leaves DB untouched.
     *
     * @param raw field text supplied by admins
     * @return boxed price or {@code null} when intentionally blank
     * @throws NumberFormatException negative, NaN, infinite, or non-numeric text
     */
    static Double parseOptionalMarketPriceInput(String raw) throws NumberFormatException {
        if (raw == null) {
            return null;
        }
        String t = raw.trim();
        if (t.isEmpty()) {
            return null;
        }
        t = t.replace("$", "").replace(",", "").trim();
        if (t.isEmpty()) {
            return null;
        }
        double v = Double.parseDouble(t);
        if (v < 0 || Double.isNaN(v) || Double.isInfinite(v)) {
            throw new NumberFormatException("Use a non-negative number for market price.");
        }
        return v;
    }

    /**
     * Top session strip labeled with signed-in username and optional backup status.
     *
     * @param user session identity
     * @return bordered north strip component ready for frame attachment
     */
    static JPanel buildSessionTopBar(
            User user,
            Connection connection,
            JFrame frame,
            JPanel workspaceContainer,
            AccountActions accountActions
    ) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, AppUI.BORDER),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)));
        AppUI.applyPanelBackground(bar);

        JLabel brand = new JLabel(user.getUsername(), SwingConstants.CENTER);
        Font base = brand.getFont();
        brand.setFont(base.deriveFont(Font.BOLD, 16f));
        brand.setForeground(AppUI.TEXT);
        bar.add(brand, BorderLayout.CENTER);

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 14, 0));
        AppUI.applyPanelBackground(east);
        east.setOpaque(false);
        if (user.hasAdminRights()) {
            try {
                int reminderDays = readBackupReminderDays(connection);
                int since = accountActions.daysSinceLatestBackup();
                if (since < 0 || since > reminderDays) {
                    String backupText = since < 0 ? "No backups found" : "Backup " + since + "d ago";
                    JButton backupLink = new JButton(backupText);
                    backupLink.setBorderPainted(false);
                    backupLink.setContentAreaFilled(false);
                    backupLink.setFocusPainted(false);
                    backupLink.setForeground(SETUP_FG);
                    backupLink.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
                    backupLink.addActionListener(e -> showView(workspaceContainer, VIEW_ADMIN_TOOLS,
                            AdministrationToolsPanel.build(user, connection, frame, accountActions)));
                    east.add(backupLink);
                }
            } catch (SQLException | IOException ignored) {
                // Omit backup chip when status cannot be loaded.
            }
        }
        if (east.getComponentCount() > 0) {
            bar.add(east, BorderLayout.EAST);
        }

        return bar;
    }

    static final Color SETUP_FG = new Color(0xfbbf24);

    /** Live session strip; cleared when the workspace frame closes. */
    static volatile FinancialMetricsStrip activeMetricsStrip;

    /** Admin layout only: right rail toggles between financial metrics and selected item photo. */
    static volatile AdminMetricsRailHost adminMetricsRailHost;

    /** While the workspace frame is open: recomputes the bottom profit-alert banner from {@code app_metadata}. */
    static volatile Consumer<Connection> profitAlertBannerRefreshAction;
    /** Sidebar card key for the view currently shown in the workspace (persisted on close). */
    static volatile String activeWorkspaceViewKey = "home";
    static final int RECENT_ITEMS_MAX = 10;
    static final Deque<RecentItemEntry> recentItems = new ArrayDeque<>();
    static final List<Runnable> recentItemRefreshers = new ArrayList<>();

    private record RecentItemEntry(String itemCode, String label) {
    }

    /**
     * Refreshes the scrolling profit-alert banner after settings change (invokes on the EDT).
     *
     * @param connection active session DB connection
     */
    public static void scheduleProfitAlertBannerRefresh(Connection connection) {
        Consumer<Connection> action = profitAlertBannerRefreshAction;
        if (action == null || connection == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> action.accept(connection));
    }

    static int readBackupReminderDays(Connection connection) throws SQLException {
        String raw = DatabaseManager.getAppMetadata(connection, DatabaseManager.META_BACKUP_REMINDER_DAYS);
        if (raw == null || raw.isBlank()) {
            return DEFAULT_BACKUP_REMINDER_DAYS;
        }
        try {
            int v = Integer.parseInt(raw.trim());
            return v < 1 ? DEFAULT_BACKUP_REMINDER_DAYS : Math.min(v, 365);
        } catch (NumberFormatException ex) {
            return DEFAULT_BACKUP_REMINDER_DAYS;
        }
    }

    static boolean readBackupOnLogoutEnabled(Connection connection) throws SQLException {
        String raw = DatabaseManager.getAppMetadata(connection, DatabaseManager.META_BACKUP_ON_LOGOUT_ENABLED);
        if (raw == null || raw.isBlank()) {
            return false;
        }
        return "1".equals(raw.trim()) || "true".equalsIgnoreCase(raw.trim());
    }









    static synchronized List<RecentItemEntry> snapshotRecentItems() {
        return new ArrayList<>(recentItems);
    }

    static synchronized void registerRecentItemRefresher(Runnable refresher) {
        if (refresher == null) {
            return;
        }
        recentItemRefreshers.add(refresher);
    }

    static synchronized void unregisterRecentItemRefresher(Runnable refresher) {
        recentItemRefreshers.remove(refresher);
    }

    static void recordRecentItem(String itemCode, String label) {
        String code = itemCode == null ? "" : itemCode.trim();
        if (code.isEmpty()) {
            return;
        }
        String cleanedLabel = label == null ? "" : label.trim();
        synchronized (WorkspaceShell.class) {
            recentItems.removeIf(e -> code.equalsIgnoreCase(e.itemCode()));
            recentItems.addFirst(new RecentItemEntry(code, cleanedLabel));
            while (recentItems.size() > RECENT_ITEMS_MAX) {
                recentItems.removeLast();
            }
        }
        List<Runnable> refreshers;
        synchronized (WorkspaceShell.class) {
            refreshers = new ArrayList<>(recentItemRefreshers);
        }
        for (Runnable refresher : refreshers) {
            SwingUtilities.invokeLater(refresher);
        }
    }

    static boolean isLowStockSku(int stock, int reorderTrigger) {
        return reorderTrigger > 0 && reorderTrigger >= stock;
    }

    static int parseMetadataInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    static void restoreWorkspaceLayout(
            Connection connection,
            JSplitPane sidebarSplit,
            JSplitPane adminTripleSplit,
            JSplitPane photoSplit
    ) {
        try {
            int sidebar = parseMetadataInt(
                    DatabaseManager.getAppMetadata(connection, DatabaseManager.META_WORKSPACE_SIDEBAR_DIVIDER),
                    SIDEBAR_TARGET_WIDTH);
            if (sidebarSplit.getWidth() > 0) {
                sidebarSplit.setDividerLocation(Math.max(160, Math.min(sidebar, sidebarSplit.getWidth() - WORKSPACE_MIN_WIDTH)));
            } else {
                sidebarSplit.setDividerLocation(sidebar);
            }
            if (adminTripleSplit != null) {
                int rail = parseMetadataInt(
                        DatabaseManager.getAppMetadata(connection, DatabaseManager.META_WORKSPACE_ADMIN_RAIL_DIVIDER),
                        -1);
                if (rail > 0 && adminTripleSplit.getWidth() > 0) {
                    adminTripleSplit.setDividerLocation(rail);
                }
            }
            if (photoSplit != null) {
                int photoDiv = parseMetadataInt(
                        DatabaseManager.getAppMetadata(connection, DatabaseManager.META_WORKSPACE_PHOTO_SPLIT_DIVIDER),
                        -1);
                if (photoDiv > 0) {
                    photoSplit.setDividerLocation(photoDiv);
                }
            }
        } catch (SQLException ignored) {
            // Keep defaults when metadata cannot be read.
        }
    }

    static void persistWorkspaceLayout(
            Connection connection,
            JSplitPane sidebarSplit,
            JSplitPane adminTripleSplit,
            JSplitPane photoSplit,
            String lastViewKey
    ) {
        try {
            if (lastViewKey != null && !lastViewKey.isBlank() && !"home".equals(lastViewKey)) {
                DatabaseManager.putAppMetadata(connection, DatabaseManager.META_WORKSPACE_LAST_VIEW, lastViewKey);
            }
            DatabaseManager.putAppMetadata(connection, DatabaseManager.META_WORKSPACE_SIDEBAR_DIVIDER,
                    Integer.toString(sidebarSplit.getDividerLocation()));
            if (adminTripleSplit != null) {
                DatabaseManager.putAppMetadata(connection, DatabaseManager.META_WORKSPACE_ADMIN_RAIL_DIVIDER,
                        Integer.toString(adminTripleSplit.getDividerLocation()));
            }
            if (photoSplit != null) {
                DatabaseManager.putAppMetadata(connection, DatabaseManager.META_WORKSPACE_PHOTO_SPLIT_DIVIDER,
                        Integer.toString(photoSplit.getDividerLocation()));
            }
        } catch (SQLException ignored) {
            // Best-effort persistence on close.
        }
    }

    static void installDividerPersistence(Connection connection, JSplitPane... splits) {
        for (JSplitPane split : splits) {
            if (split == null) {
                continue;
            }
            split.addPropertyChangeListener(JSplitPane.DIVIDER_LOCATION_PROPERTY, evt -> {
                if (connection == null) {
                    return;
                }
                try {
                    if (split.getName() != null) {
                        DatabaseManager.putAppMetadata(connection, split.getName(),
                                Integer.toString(split.getDividerLocation()));
                    }
                } catch (SQLException ignored) {
                    // Ignore intermittent write failures while dragging.
                }
            });
        }
    }

    static void restoreLastWorkspaceView(
            User user,
            Connection connection,
            JFrame frame,
            JPanel workspaceContainer,
            AccountActions accountActions
    ) {
        try {
            String last = DatabaseManager.getAppMetadata(connection, DatabaseManager.META_WORKSPACE_LAST_VIEW);
            if (last == null || last.isBlank() || "home".equals(last) || "Log Out".equals(last)) {
                return;
            }
            for (NavItem item : getItems(user, connection, accountActions, frame, workspaceContainer)) {
                if (last.equals(item.label) && item.viewBuilder != null) {
                    showView(workspaceContainer, item.label, item.viewBuilder.build());
                    return;
                }
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(frame, "Could not restore last view: " + ex.getMessage(),
                    "Workspace", JOptionPane.WARNING_MESSAGE);
        }
    }

    static void installWorkspaceKeyboardShortcuts(
            JFrame frame,
            JPanel root,
            User user,
            Connection connection,
            JPanel workspaceContainer,
            AccountActions accountActions
    ) {
        InputMap im = root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = root.getActionMap();
        List<NavItem> items = getItems(user, connection, accountActions, frame, workspaceContainer);
        int shortcut = 1;
        for (NavItem item : items) {
            if (item.viewBuilder == null || shortcut > 9) {
                continue;
            }
            String actionKey = "ims.nav." + shortcut;
            int keyCode = KeyEvent.VK_0 + shortcut;
            int mask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
            im.put(KeyStroke.getKeyStroke(keyCode, mask), actionKey);
            final String label = item.label;
            am.put(actionKey, new AbstractAction() {
                @Override
                public void actionPerformed(java.awt.event.ActionEvent e) {
                    try {
                        showView(workspaceContainer, label, item.viewBuilder.build());
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(frame, "Unable to open " + label + ": " + ex.getMessage(),
                                "View Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
            shortcut++;
        }

        int paletteMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMaskEx();
        im.put(KeyStroke.getKeyStroke(KeyEvent.VK_K, paletteMask), "ims.command.palette");
        am.put("ims.command.palette", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                showCommandPalette(frame, user, connection, workspaceContainer, accountActions);
            }
        });
    }

    /** Command palette: fuzzy-search commands and inventory items (⌘K / Ctrl+K). */
    static void showCommandPalette(
            JFrame frame,
            User user,
            Connection connection,
            JPanel workspaceContainer,
            AccountActions accountActions
    ) {
        final int maxItemResults = 8;
        final List<NavItem> navItems = getItems(user, connection, accountActions, frame, workspaceContainer);
        List<ViewItemsPanel.ViewItemShelfRow> loadedRows;
        try {
            loadedRows = ViewItemsPanel.loadViewItemShelfRows(connection);
        } catch (SQLException ex) {
            loadedRows = new ArrayList<>();
        }
        final List<ViewItemsPanel.ViewItemShelfRow> itemRows = loadedRows;

        final JDialog dialog = new JDialog(frame, "Command Palette", true);
        dialog.setUndecorated(true);

        JPanel content = new JPanel(new BorderLayout(0, 8));
        content.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(AppUI.ACCENT, 1),
                BorderFactory.createEmptyBorder(12, 12, 12, 12)));
        content.setBackground(AppUI.SURFACE);

        final JTextField search = new JTextField();
        AppUI.applyInputField(search);
        search.setFont(search.getFont().deriveFont(Font.PLAIN, 16f));
        search.putClientProperty("JTextField.placeholderText", "Search commands and items\u2026");

        final DefaultListModel<CommandPaletteEntry> model = new DefaultListModel<>();
        final JList<CommandPaletteEntry> list = new JList<>(model);
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setBackground(AppUI.SURFACE);
        list.setForeground(AppUI.TEXT);
        list.setSelectionBackground(AppUI.ACCENT);
        list.setSelectionForeground(AppUI.TEXT);
        list.setFixedCellHeight(34);
        list.setCellRenderer((jList, value, index, isSelected, cellHasFocus) -> {
            JPanel cell = new JPanel(new BorderLayout(10, 0));
            cell.setBorder(BorderFactory.createEmptyBorder(0, 10, 0, 10));
            cell.setBackground(isSelected ? AppUI.ACCENT : AppUI.SURFACE);
            JLabel main = new JLabel(value.label);
            main.setForeground(AppUI.TEXT);
            JLabel hint = new JLabel(value.hint);
            hint.setForeground(isSelected ? AppUI.TEXT : AppUI.TEXT_MUTED);
            hint.setFont(hint.getFont().deriveFont(Font.PLAIN, 11f));
            cell.add(main, BorderLayout.CENTER);
            cell.add(hint, BorderLayout.EAST);
            return cell;
        });

        final Runnable refilter = () -> {
            String query = search.getText().trim().toLowerCase();
            model.clear();
            for (NavItem item : navItems) {
                if (commandPaletteMatches(item.label.toLowerCase(), query)) {
                    final NavItem navItem = item;
                    Runnable open = () -> {
                        if (navItem.viewBuilder != null) {
                            try {
                                showView(workspaceContainer, navItem.label, navItem.viewBuilder.build());
                            } catch (SQLException ex) {
                                JOptionPane.showMessageDialog(frame, "Unable to open " + navItem.label + ": "
                                        + ex.getMessage(), "View Error", JOptionPane.ERROR_MESSAGE);
                            }
                        } else {
                            new Thread(() -> runAction(navItem.action),
                                    "ims-action-" + navItem.label.replace(" ", "-")).start();
                        }
                    };
                    model.addElement(new CommandPaletteEntry(item.label, "Go to", open));
                }
            }
            if (!query.isEmpty()) {
                int added = 0;
                for (ViewItemsPanel.ViewItemShelfRow row : itemRows) {
                    if (added >= maxItemResults) {
                        break;
                    }
                    String haystack = (row.itemCode() + " " + row.itemName()).toLowerCase();
                    if (haystack.contains(query) || commandPaletteMatches(haystack, query)) {
                        final ViewItemsPanel.ViewItemShelfRow itemRow = row;
                        String label = (itemRow.itemName() == null || itemRow.itemName().isBlank())
                                ? itemRow.itemCode()
                                : itemRow.itemCode() + " \u2014 " + itemRow.itemName();
                        Runnable open = () -> {
                            workspaceContainer.putClientProperty(CLIENT_VIEW_ITEMS_SEARCH_TEXT, itemRow.itemCode());
                            try {
                                showView(workspaceContainer, "View Items",
                                        ViewItemsPanel.build(user, connection, frame, workspaceContainer));
                            } catch (SQLException ex) {
                                JOptionPane.showMessageDialog(frame, "Unable to open item: " + ex.getMessage(),
                                        "View Error", JOptionPane.ERROR_MESSAGE);
                            }
                        };
                        model.addElement(new CommandPaletteEntry(label, "Item", open));
                        added++;
                    }
                }
            }
            if (!model.isEmpty()) {
                list.setSelectedIndex(0);
            }
        };
        refilter.run();

        final Runnable runSelected = () -> {
            CommandPaletteEntry selected = list.getSelectedValue();
            if (selected == null) {
                return;
            }
            dialog.dispose();
            selected.onSelect.run();
        };

        search.getDocument().addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { refilter.run(); }
            @Override public void removeUpdate(DocumentEvent e) { refilter.run(); }
            @Override public void changedUpdate(DocumentEvent e) { refilter.run(); }
        });

        search.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyPressed(java.awt.event.KeyEvent e) {
                int size = model.getSize();
                switch (e.getKeyCode()) {
                    case KeyEvent.VK_ESCAPE -> dialog.dispose();
                    case KeyEvent.VK_ENTER -> runSelected.run();
                    case KeyEvent.VK_DOWN -> {
                        if (size > 0) {
                            list.setSelectedIndex(Math.min(size - 1, list.getSelectedIndex() + 1));
                            list.ensureIndexIsVisible(list.getSelectedIndex());
                        }
                    }
                    case KeyEvent.VK_UP -> {
                        if (size > 0) {
                            list.setSelectedIndex(Math.max(0, list.getSelectedIndex() - 1));
                            list.ensureIndexIsVisible(list.getSelectedIndex());
                        }
                    }
                    default -> { }
                }
            }
        });

        list.addMouseListener(new java.awt.event.MouseAdapter() {
            @Override
            public void mouseClicked(java.awt.event.MouseEvent e) {
                if (e.getClickCount() == 2) {
                    runSelected.run();
                }
            }
        });

        JScrollPane listScroll = new JScrollPane(list);
        listScroll.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 0));
        listScroll.getViewport().setBackground(AppUI.SURFACE);
        listScroll.setPreferredSize(new Dimension(460, 320));

        content.add(search, BorderLayout.NORTH);
        content.add(listScroll, BorderLayout.CENTER);
        dialog.setContentPane(content);
        dialog.pack();
        dialog.setLocationRelativeTo(frame);
        SwingUtilities.invokeLater(search::requestFocusInWindow);
        dialog.setVisible(true);
    }

    static boolean commandPaletteMatches(String label, String query) {
        if (query.isEmpty()) {
            return true;
        }
        int qi = 0;
        for (int li = 0; li < label.length() && qi < query.length(); li++) {
            if (label.charAt(li) == query.charAt(qi)) {
                qi++;
            }
        }
        return qi == query.length();
    }

    static void maybeShowBackupReminderDialog(
            User user,
            Connection connection,
            JFrame frame,
            AccountActions accountActions
    ) {
        if (!user.hasAdminRights()) {
            return;
        }
        try {
            int reminderDays = readBackupReminderDays(connection);
            int since = accountActions.daysSinceLatestBackup();
            if (since >= 0 && since <= reminderDays) {
                return;
            }
            String msg = since < 0
                    ? "No database backups were found under database_backups.\n"
                    + "Create a backup from Admin tools → Database backup."
                    : "Your newest backup is " + since + " days old (reminder threshold: " + reminderDays + " days).\n"
                    + "Consider creating a backup from Admin tools.";
            JOptionPane.showMessageDialog(frame, msg, "Backup reminder", JOptionPane.WARNING_MESSAGE);
        } catch (SQLException | IOException ex) {
            // Skip dialog when reminder status cannot be determined.
        }
    }

    static void maybePromptBackupOnLogout(
            User user,
            Connection connection,
            JFrame frame,
            AccountActions accountActions
    ) {
        if (user == null || !user.hasAdminRights()) {
            return;
        }
        try {
            if (!readBackupOnLogoutEnabled(connection)) {
                return;
            }
            int reminderDays = readBackupReminderDays(connection);
            int since = accountActions.daysSinceLatestBackup();
            if (since < 0 || since > reminderDays) {
                String msg = since < 0
                        ? "No backups were found. Run a backup before logging out?"
                        : "Latest backup is " + since + " days old (threshold " + reminderDays + ").\nRun a backup before logging out?";
                int result = JOptionPane.showConfirmDialog(
                        frame,
                        msg,
                        "Backup before logout",
                        JOptionPane.YES_NO_OPTION,
                        JOptionPane.WARNING_MESSAGE);
                if (result == JOptionPane.YES_OPTION) {
                    accountActions.backUpDatabase(user, frame);
                }
            }
        } catch (SQLException | IOException ex) {
            // Skip the prompt when metadata or backup status cannot be read.
        }
    }




    /** Schedules a deferred refresh of the admin financial metrics strip (noop when strip absent). */
    static void requestMetricsRefresh() {
        FinancialMetricsStrip strip = activeMetricsStrip;
        if (strip != null) {
            SwingUtilities.invokeLater(strip::refresh);
        }
    }

    /** Re-runs metrics query and label colors on the EDT (not deferred). */
    static void refreshActiveMetricsStripNow() {
        FinancialMetricsStrip strip = activeMetricsStrip;
        if (strip != null) {
            strip.refresh();
        }
    }

    /** Keeps the sidebar/workspace divider sane when the frame grows or shrinks. */
    static void syncSidebarWorkspaceSplit(JSplitPane splitPane) {
        int w = splitPane.getWidth();
        if (w <= 0) {
            return;
        }
        int div = splitPane.getDividerSize();
        int loc = Math.min(SIDEBAR_TARGET_WIDTH, w - WORKSPACE_MIN_WIDTH - div);
        loc = Math.max(160, loc);
        splitPane.setDividerLocation(loc);
    }

    /** Keeps the admin metrics rail ~fixed width so the main block stretches with the window. */
    static void syncAdminMetricsSplit(JSplitPane triplePane) {
        int w = triplePane.getWidth();
        if (w <= 0) {
            return;
        }
        int div = triplePane.getDividerSize();
        int loc = w - ADMIN_METRICS_RAIL_OUTER_PX - div;
        loc = Math.max(MAIN_AREA_MIN_FOR_METRICS, loc);
        int maxLeft = w - div - 96;
        if (maxLeft < MAIN_AREA_MIN_FOR_METRICS) {
            loc = Math.max(200, Math.min(loc, maxLeft));
        } else {
            loc = Math.min(loc, maxLeft);
        }
        triplePane.setDividerLocation(loc);
    }

    /**
     * Admin-only right column: on-order exposure (open PO value), market-valued on-hand stock, period and lifetime P/L,
     * margin, and top movers for the current calendar month. Refreshes from {@link InventoryFifo} on the EDT.
     */
    static final class FinancialMetricsStrip extends JPanel {
        static final long serialVersionUID = 1L;

        static final Color CARD_BORDER = AppUI.BORDER;
        static final Color CARD_HEAD = AppUI.SURFACE_ELEVATED;
        static final Color ROW_ZEBRA = AppUI.SURFACE_ELEVATED;
        static final Color MUTED = AppUI.TEXT_MUTED;
        static final Color LABEL = AppUI.TEXT_MUTED;

        private final JLabel marketStockValueLabel = new JLabel("—");
        private final JLabel profitLabel = new JLabel("—");
        private final JLabel plTodayLabel = new JLabel("—");
        private final JLabel plWeekLabel = new JLabel("—");
        private final JLabel plMonthLabel = new JLabel("—");
        private final JLabel marginLabel = new JLabel("—");
        private final JLabel poExposureLabel = new JLabel("—");
        private final JPanel topMoversRows = new JPanel();
        private final Connection connection;

        /**
         * @param connection open enterprise connection; retained for repeated {@link #refresh()} queries
         */
        FinancialMetricsStrip(Connection connection) {
            this.connection = connection;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 1, 0, 0, AppUI.BORDER),
                    BorderFactory.createEmptyBorder(14, 12, 22, 12)));
            AppUI.applyPanelBackground(this);

            Font base = marketStockValueLabel.getFont();

            JLabel title = new JLabel("Financials");
            title.setFont(base.deriveFont(Font.BOLD, 17f));
            title.setForeground(AppUI.TEXT);
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(title);
            add(Box.createVerticalStrut(14));

            JPanel moversCard = new JPanel(new BorderLayout(0, 0));
            AppUI.markCardSurface(moversCard);
            moversCard.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
            moversCard.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel moversHead = new JLabel("Top 10 · this month");
            moversHead.setOpaque(true);
            moversHead.setBackground(CARD_HEAD);
            moversHead.setFont(base.deriveFont(Font.BOLD, 11f));
            moversHead.setForeground(AppUI.TEXT_MUTED);
            moversHead.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            moversCard.add(moversHead, BorderLayout.NORTH);
            topMoversRows.setLayout(new BoxLayout(topMoversRows, BoxLayout.Y_AXIS));
            topMoversRows.setOpaque(false);
            moversCard.add(topMoversRows, BorderLayout.CENTER);
            add(moversCard);
            add(Box.createVerticalStrut(20));

            JPanel periodCard = newCardShell();
            periodCard.add(sectionHeading(base, "P/L by period"), BorderLayout.NORTH);
            JPanel periodBody = new JPanel();
            periodBody.setLayout(new BoxLayout(periodBody, BoxLayout.Y_AXIS));
            periodBody.setOpaque(false);
            AppUI.applyPanelBackground(periodBody);
            addPeriodRowInto(periodBody, base, "Today", plTodayLabel, true);
            addPeriodRowInto(periodBody, base, "This week (ISO)", plWeekLabel, true);
            addPeriodRowInto(periodBody, base, "This month", plMonthLabel, false);
            periodCard.add(periodBody, BorderLayout.CENTER);
            add(periodCard);
            add(Box.createVerticalStrut(20));

            JPanel totalsCard = newCardShell();
            totalsCard.add(sectionHeading(base, "Exposure & totals"), BorderLayout.NORTH);
            JPanel totalsBody = new JPanel();
            totalsBody.setLayout(new BoxLayout(totalsBody, BoxLayout.Y_AXIS));
            totalsBody.setOpaque(false);
            AppUI.applyPanelBackground(totalsBody);
            addPeriodRowInto(totalsBody, base, "Value on order", poExposureLabel, true);
            addPeriodRowInto(totalsBody, base, "Market value · stock", marketStockValueLabel, true);
            addPeriodRowInto(totalsBody, base, "Total P/L", profitLabel, true);
            addPeriodRowInto(totalsBody, base, "Total P/L %", marginLabel, false);
            totalsCard.add(totalsBody, BorderLayout.CENTER);
            add(totalsCard);
            add(Box.createVerticalStrut(20));

            setMinimumSize(new Dimension(220, 80));

            for (JLabel dynamicPl : new JLabel[]{plTodayLabel, plWeekLabel, plMonthLabel, profitLabel, marginLabel}) {
                dynamicPl.putClientProperty("ims.preserveForeground", Boolean.TRUE);
            }
        }

        /** Bordered card panel used for period and totals sections. */
        static JPanel newCardShell() {
            JPanel p = new JPanel(new BorderLayout(0, 0));
            AppUI.markCardSurface(p);
            p.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            return p;
        }

        /** Card section title bar (gray header band). */
        static JLabel sectionHeading(Font base, String text) {
            JLabel h = new JLabel(text);
            h.setOpaque(true);
            h.setBackground(CARD_HEAD);
            h.setFont(base.deriveFont(Font.BOLD, 11f));
            h.setForeground(AppUI.TEXT_MUTED);
            h.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            return h;
        }

        /**
         * Adds a caption / value row to a metrics card (period P/L lines and exposure totals share the same chrome).
         *
         * @param parent        vertical box host
         * @param base          reference font for sizing
         * @param caption       left label
         * @param valueLabel    right-aligned value control
         * @param dividerBelow  when true, paints a bottom divider on the row
         */
        private void addPeriodRowInto(JPanel parent, Font base, String caption, JLabel valueLabel, boolean dividerBelow) {
            JPanel row = new JPanel(new BorderLayout(10, 0));
            row.setOpaque(false);
            AppUI.applyPanelBackground(row);
            if (dividerBelow) {
                row.setBorder(BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER),
                        BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            } else {
                row.setBorder(BorderFactory.createEmptyBorder(8, 10, 10, 10));
            }
            JLabel cap = new JLabel(caption);
            cap.setFont(base.deriveFont(Font.PLAIN, 11f));
            cap.setForeground(MUTED);
            valueLabel.setFont(base.deriveFont(Font.BOLD, 14f));
            valueLabel.setHorizontalAlignment(SwingConstants.RIGHT);
            valueLabel.setVerticalAlignment(SwingConstants.CENTER);
            row.add(cap, BorderLayout.WEST);
            row.add(valueLabel, BorderLayout.EAST);
            parent.add(row);
        }

        /** One ranked line in the “Top movers” list. */
        private JPanel buildMoverRow(int rank, InventoryFifo.TopMoverRow row, Font base, boolean zebra) {
            JPanel line = new JPanel(new BorderLayout(8, 0));
            line.setOpaque(true);
            line.setBackground(zebra ? ROW_ZEBRA : AppUI.SURFACE);
            line.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel rankLab = new JLabel(String.format(Locale.US, "%2d", rank));
            rankLab.setFont(base.deriveFont(Font.BOLD, 11f));
            rankLab.setForeground(AppUI.TEXT_MUTED);
            rankLab.setHorizontalAlignment(SwingConstants.RIGHT);
            rankLab.setPreferredSize(new Dimension(22, 18));
            JLabel codeLab = new JLabel(row.itemCode);
            codeLab.setFont(base.deriveFont(Font.BOLD, 12f));
            codeLab.setForeground(AppUI.TEXT);
            JLabel unitLab = new JLabel(row.units + " sold");
            unitLab.setFont(base.deriveFont(Font.PLAIN, 11f));
            unitLab.setForeground(LABEL);
            unitLab.setHorizontalAlignment(SwingConstants.RIGHT);
            JPanel mid = new JPanel(new BorderLayout());
            mid.setOpaque(false);
            AppUI.applyPanelBackground(mid);
            mid.add(codeLab, BorderLayout.CENTER);
            line.add(rankLab, BorderLayout.WEST);
            line.add(mid, BorderLayout.CENTER);
            line.add(unitLab, BorderLayout.EAST);
            return line;
        }

        /** Colors a P/L label green (≥0) or red (&lt;0). */
        static void setPlColor(JLabel label, double pl) {
            label.setForeground(pl >= 0 ? AppUI.SUCCESS : AppUI.DANGER);
        }

        /** Replaces the movers list with a single error message. */
        private void fillMoversError(Font base, String message) {
            topMoversRows.removeAll();
            JLabel err = new JLabel(message);
            err.setFont(base.deriveFont(Font.PLAIN, 11f));
            err.setForeground(AppUI.DANGER);
            err.setBorder(BorderFactory.createEmptyBorder(10, 10, 12, 10));
            err.setAlignmentX(Component.LEFT_ALIGNMENT);
            topMoversRows.add(err);
            topMoversRows.revalidate();
            topMoversRows.repaint();
        }

        /** Re-queries SQLite and updates all labels and the top-movers list (safe to call on the EDT). */
        void refresh() {
            Font base = marketStockValueLabel.getFont();
            try {
                double marketVal = InventoryFifo.totalMarketValueOfOnHandStock(connection);
                double plLife = InventoryFifo.lifetimeProfitLoss(connection);
                marketStockValueLabel.setText(formatUsdMoney(marketVal));
                profitLabel.setText(formatUsdMoney(plLife));
                profitLabel.setForeground(plLife >= 0 ? AppUI.SUCCESS : AppUI.DANGER);

                LocalDate today = LocalDate.now();
                double plToday = InventoryFifo.profitLossBetweenDates(connection, today, today);
                double plWeek = InventoryFifo.profitLossBetweenDates(
                        connection,
                        InventoryFifo.startOfIsoWeek(today),
                        InventoryFifo.endOfIsoWeek(today)
                );
                double plMonth = InventoryFifo.profitLossBetweenDates(
                        connection,
                        InventoryFifo.firstDayOfMonth(today),
                        InventoryFifo.lastDayOfMonth(today)
                );

                plTodayLabel.setText(formatUsdMoney(plToday));
                setPlColor(plTodayLabel, plToday);
                plWeekLabel.setText(formatUsdMoney(plWeek));
                setPlColor(plWeekLabel, plWeek);
                plMonthLabel.setText(formatUsdMoney(plMonth));
                setPlColor(plMonthLabel, plMonth);

                double revenue = InventoryFifo.lifetimeTotalRevenue(connection);
                if (revenue <= 1e-9) {
                    marginLabel.setText("—");
                    marginLabel.setForeground(MUTED);
                } else {
                    double pct = (plLife / revenue) * 100.0;
                    marginLabel.setText(String.format(Locale.US, "%.1f%%", pct));
                    marginLabel.setForeground(pct >= 0 ? AppUI.SUCCESS : AppUI.DANGER);
                }

                double poExp = InventoryFifo.openPurchaseOrderExposure(connection);
                poExposureLabel.setText(formatUsdMoney(poExp));
                poExposureLabel.setForeground(AppUI.TEXT);

                List<InventoryFifo.TopMoverRow> movers = InventoryFifo.topMoversByUnitsBetweenDates(
                        connection,
                        InventoryFifo.firstDayOfMonth(today),
                        InventoryFifo.lastDayOfMonth(today),
                        10
                );
                topMoversRows.removeAll();
                if (movers.isEmpty()) {
                    JLabel empty = new JLabel("No sales with DateISO this month");
                    empty.setFont(base.deriveFont(Font.PLAIN, 11f));
                    empty.setForeground(MUTED);
                    empty.setBorder(BorderFactory.createEmptyBorder(10, 10, 12, 10));
                    empty.setAlignmentX(Component.LEFT_ALIGNMENT);
                    topMoversRows.add(empty);
                } else {
                    int i = 0;
                    for (InventoryFifo.TopMoverRow m : movers) {
                        i++;
                        topMoversRows.add(buildMoverRow(i, m, base, (i & 1) == 0));
                    }
                }
                topMoversRows.revalidate();
                topMoversRows.repaint();
            } catch (SQLException ex) {
                marketStockValueLabel.setText("error");
                profitLabel.setText("error");
                profitLabel.setForeground(AppUI.DANGER);
                plTodayLabel.setText("error");
                plWeekLabel.setText("error");
                plMonthLabel.setText("error");
                marginLabel.setText("error");
                poExposureLabel.setText("error");
                fillMoversError(base, "Could not load metrics");
            }
        }
    }

    /** Utility holder for workspace shell UI composition and actions. */
    private WorkspaceShell() {
    }

    /**
     * Opens the signed-in Swing workspace frame (sidebar + stacked cards; optional metrics/photo rails for admins).
     * Runs on the EDT; must receive an open JDBC connection whose lifecycle usually ends inside the frame dispose hook.
     *
     * @param user active signed-in user
     * @param connection enterprise DB connection reused for sidebar views during the session
     * @param accountActions dialogs for backups and modal password resets
     * @param whenWindowClosed optional EDT callback invoked after dispose (e.g. return to login); may be {@code null}
     */
    public static void open(
            User user,
            Connection connection,
            AccountActions accountActions,
            Runnable whenWindowClosed
    ) {
        JFrame frame = new JFrame("Inventory Management System");
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int scaledH = MAIN_FRAME_BASE_H * MAIN_FRAME_HEIGHT_SCALE_PERCENT / 100;
        int scaledW = (int) Math.round(
                MAIN_FRAME_BASE_W * (MAIN_FRAME_HEIGHT_SCALE_PERCENT / 100.0) * MAIN_FRAME_WIDTH_EXTRA_FACTOR
        );
        int capW = Math.max(MAIN_FRAME_BASE_W, usable.width - 24);
        int capH = Math.max(MAIN_FRAME_BASE_H, usable.height - 24);
        frame.setSize(Math.min(scaledW, capW), Math.min(scaledH, capH));
        frame.setLocationRelativeTo(null);
        frame.setLayout(new BorderLayout());

        JPanel workspaceContainer = new JPanel(new CardLayout());
        AppUI.applyPanelBackground(workspaceContainer);
        workspaceContainer.add(buildEmptyWorkspaceCanvas(), "home");

        JPanel sidebar = buildSidebar(user, frame, workspaceContainer, connection, accountActions);

        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, workspaceContainer);
        splitPane.setDividerLocation(SIDEBAR_TARGET_WIDTH);
        splitPane.setResizeWeight(0);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);
        splitPane.setOpaque(true);
        splitPane.setBackground(AppUI.BACKGROUND);

        java.awt.Component center;
        final JSplitPane[] adminMetricsTripleHolder = new JSplitPane[1];
        final JSplitPane[] photoSplitHolder = new JSplitPane[1];
        splitPane.setName(DatabaseManager.META_WORKSPACE_SIDEBAR_DIVIDER);
        if (user.hasAdminRights()) {
            FinancialMetricsStrip metricsStrip = new FinancialMetricsStrip(connection);
            activeMetricsStrip = metricsStrip;

            JScrollPane metricsScroll = new JScrollPane(metricsStrip);
            metricsScroll.setBorder(null);
            metricsScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            metricsScroll.getVerticalScrollBar().setUnitIncrement(16);

            JPanel metricsRailHost = new JPanel(new CardLayout());
            AppUI.applyPanelBackground(metricsRailHost);
            metricsRailHost.add(metricsScroll, "metrics");

            JPanel photoRailCard = new JPanel(new BorderLayout(8, 8));
            AppUI.applyPanelBackground(photoRailCard);
            photoRailCard.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            JLabel photoRailTitle = new JLabel(" ", SwingConstants.CENTER);
            photoRailTitle.setFont(photoRailTitle.getFont().deriveFont(Font.BOLD, 14f));
            JLabel photoRailImage = new JLabel(" ", SwingConstants.CENTER);
            photoRailImage.setVerticalAlignment(SwingConstants.CENTER);
            photoRailImage.setHorizontalAlignment(SwingConstants.CENTER);
            photoRailImage.setForeground(AppUI.TEXT_MUTED);
            JScrollPane photoRailScroll = new JScrollPane(photoRailImage);
            photoRailScroll.setBorder(AppUI.lineBorder());
            photoRailScroll.getVerticalScrollBar().setUnitIncrement(16);
            photoRailScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            photoRailScroll.setPreferredSize(new Dimension(ITEM_PHOTO_DISPLAY_MAX_W + 16, ITEM_PHOTO_DISPLAY_MAX_H + 16));

            JTextArea photoRailStats = new JTextArea();
            photoRailStats.setEditable(false);
            photoRailStats.setOpaque(false);
            photoRailStats.setLineWrap(true);
            photoRailStats.setWrapStyleWord(true);
            photoRailStats.setRows(8);
            photoRailStats.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            photoRailStats.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
            photoRailStats.setForeground(AppUI.TEXT);
            JScrollPane photoRailStatsScroll = new JScrollPane(photoRailStats);
            photoRailStatsScroll.setBorder(AppUI.lineBorder());
            photoRailStatsScroll.getVerticalScrollBar().setUnitIncrement(16);

            JTextArea photoRailNotes = new JTextArea(4, 18);
            photoRailNotes.setLineWrap(true);
            photoRailNotes.setWrapStyleWord(true);
            photoRailNotes.setToolTipText("Inventory note (max " + ITEM_NOTES_MAX_CHARS + " characters).");
            photoRailNotes.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
            photoRailNotes.setFont(photoRailNotes.getFont().deriveFont(Font.PLAIN, 13f));
            photoRailNotes.setForeground(AppUI.TEXT);
            JScrollPane photoRailNotesScroll = new JScrollPane(photoRailNotes);
            photoRailNotesScroll.setBorder(AppUI.lineBorder());
            photoRailNotesScroll.getVerticalScrollBar().setUnitIncrement(16);

            JLabel photoRailNotesHeading = new JLabel("Notes");
            photoRailNotesHeading.setFont(photoRailNotesHeading.getFont().deriveFont(Font.BOLD, 12f));

            JButton railUpdateNote = new JButton("Update note");
            JButton railUpdateImage = new JButton("Update image");
            JButton railSaveNote = new JButton("Save note");
            JButton railCancelNote = new JButton("Cancel");
            styleSecondaryButton(railUpdateNote);
            styleSecondaryButton(railUpdateImage);
            styleSecondaryButton(railSaveNote);
            styleSecondaryButton(railCancelNote);
            railSaveNote.setVisible(false);
            railCancelNote.setVisible(false);

            JPanel railNoteActions = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
            AppUI.applyPanelBackground(railNoteActions);
            railNoteActions.setOpaque(false);
            railNoteActions.add(railUpdateNote);
            railNoteActions.add(railSaveNote);
            railNoteActions.add(railCancelNote);
            railNoteActions.add(railUpdateImage);

            JPanel photoRailNotesBlock = new JPanel(new BorderLayout(0, 4));
            AppUI.applyPanelBackground(photoRailNotesBlock);
            photoRailNotesBlock.setOpaque(false);
            photoRailNotesBlock.add(photoRailNotesHeading, BorderLayout.NORTH);
            photoRailNotesBlock.add(photoRailNotesScroll, BorderLayout.CENTER);
            photoRailNotesBlock.add(railNoteActions, BorderLayout.SOUTH);

            JPanel photoRailLower = new JPanel(new BorderLayout(0, 6));
            AppUI.applyPanelBackground(photoRailLower);
            photoRailLower.add(photoRailStatsScroll, BorderLayout.CENTER);
            photoRailLower.add(photoRailNotesBlock, BorderLayout.SOUTH);

            JPanel photoRailPhotoActions = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
            AppUI.applyPanelBackground(photoRailPhotoActions);
            photoRailPhotoActions.setOpaque(false);
            JButton railNextWebPhoto = new JButton("Next web photo");
            styleSecondaryButton(railNextWebPhoto);
            photoRailPhotoActions.add(railNextWebPhoto);

            JPanel photoRailTop = new JPanel(new BorderLayout());
            AppUI.applyPanelBackground(photoRailTop);
            photoRailTop.add(photoRailScroll, BorderLayout.CENTER);
            photoRailTop.add(photoRailPhotoActions, BorderLayout.SOUTH);
            JSplitPane photoRailSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, photoRailTop, photoRailLower);
            photoRailSplit.setResizeWeight(0.66);
            photoRailSplit.setContinuousLayout(true);
            photoRailSplit.setBorder(null);
            photoRailSplit.setDividerSize(5);
            photoRailSplit.setName(DatabaseManager.META_WORKSPACE_PHOTO_SPLIT_DIVIDER);
            photoSplitHolder[0] = photoRailSplit;
            SwingUtilities.invokeLater(() -> photoRailSplit.setDividerLocation(0.66));

            photoRailCard.add(photoRailTitle, BorderLayout.NORTH);
            photoRailCard.add(photoRailSplit, BorderLayout.CENTER);
            metricsRailHost.add(photoRailCard, "photo");
            adminMetricsRailHost = new AdminMetricsRailHost(
                    connection,
                    user.getUsername(),
                    metricsRailHost,
                    photoRailTitle,
                    photoRailImage,
                    photoRailStats,
                    photoRailNotes,
                    railUpdateNote,
                    railSaveNote,
                    railCancelNote,
                    railUpdateImage,
                    railNextWebPhoto,
                    () -> refreshViewItemsIfOpen(workspaceContainer, user, connection, frame));

            final JSplitPane triplePane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitPane, metricsRailHost);
            triplePane.setResizeWeight(1.0);
            triplePane.setBorder(null);
            triplePane.setContinuousLayout(true);
            triplePane.setName(DatabaseManager.META_WORKSPACE_ADMIN_RAIL_DIVIDER);
            center = triplePane;
            adminMetricsTripleHolder[0] = triplePane;
        } else {
            activeMetricsStrip = null;
            adminMetricsRailHost = null;
            center = splitPane;
        }

        JPanel root = new JPanel(new BorderLayout());
        root.setOpaque(true);
        root.setBackground(AppUI.BACKGROUND);

        JPanel northStack = new JPanel();
        northStack.setLayout(new BoxLayout(northStack, BoxLayout.Y_AXIS));
        northStack.setOpaque(false);
        if (AppUI.usesEmbeddedTitleBar()) {
            northStack.add(AppUI.createApplicationTitleBar("Inventory Management System"));
        }
        northStack.add(buildSessionTopBar(user, connection, frame, workspaceContainer, accountActions));
        root.add(northStack, BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);

        ProfitAlertMarqueeBanner profitBanner = new ProfitAlertMarqueeBanner();
        root.add(profitBanner, BorderLayout.SOUTH);
        profitAlertBannerRefreshAction = profitBanner::refreshFromDatabase;
        scheduleProfitAlertBannerRefresh(connection);

        installDividerPersistence(connection, splitPane, adminMetricsTripleHolder[0], photoSplitHolder[0]);
        installWorkspaceKeyboardShortcuts(frame, root, user, connection, workspaceContainer, accountActions);
        activeWorkspaceViewKey = "home";

        final boolean[] backupPromptHandled = new boolean[]{false};
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (backupPromptHandled[0]) {
                    return;
                }
                backupPromptHandled[0] = true;
                maybePromptBackupOnLogout(user, connection, frame, accountActions);
            }

            @Override
            public void windowClosed(WindowEvent e) {
                persistWorkspaceLayout(connection, splitPane, adminMetricsTripleHolder[0], photoSplitHolder[0],
                        activeWorkspaceViewKey);
                Object refresher = sidebar.getClientProperty(CLIENT_RECENT_REFRESHER);
                if (refresher instanceof Runnable r) {
                    unregisterRecentItemRefresher(r);
                }
                activeMetricsStrip = null;
                adminMetricsRailHost = null;
                profitAlertBannerRefreshAction = null;
                activeWorkspaceViewKey = "home";
                profitBanner.stopTimer();
                closeConnectionQuietly(connection);
                if (whenWindowClosed != null) {
                    SwingUtilities.invokeLater(whenWindowClosed);
                }
            }
        });

        root.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                SwingUtilities.invokeLater(() -> {
                    JSplitPane outer = adminMetricsTripleHolder[0];
                    if (outer != null) {
                        syncAdminMetricsSplit(outer);
                    }
                    syncSidebarWorkspaceSplit(splitPane);
                });
            }
        });
        frame.setContentPane(root);

        AppUI.applyWindowChrome(frame);
        AppUI.styleWindow(frame);
        refreshActiveMetricsStripNow();
        frame.setVisible(true);
        maybeShowBackupReminderDialog(user, connection, frame, accountActions);
        SwingUtilities.invokeLater(() -> {
            restoreWorkspaceLayout(connection, splitPane, adminMetricsTripleHolder[0], photoSplitHolder[0]);
            JSplitPane outer = adminMetricsTripleHolder[0];
            if (outer != null) {
                syncAdminMetricsSplit(outer);
            }
            syncSidebarWorkspaceSplit(splitPane);
            restoreLastWorkspaceView(user, connection, frame, workspaceContainer, accountActions);
        });
    }

    /**
     * Builds the left sidebar containing role-aware navigation actions.
     *
     * @return sidebar panel
     */
    static JPanel buildSidebar(
            User user,
            JFrame frame,
            JPanel workspaceContainer,
            Connection connection,
            AccountActions accountActions
    ) {
        JPanel container = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(container);
        container.setBorder(BorderFactory.createEmptyBorder(14, 12, 14, 12));

        JLabel title = new JLabel("Please select a function:", SwingConstants.LEFT);
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));

        JPanel header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(header);
        header.add(title);
        header.add(Box.createVerticalStrut(16));

        JPanel nav = new JPanel();
        nav.setLayout(new BoxLayout(nav, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(nav);

        int betweenNavButtons = user.hasAdminRights() ? 5 : 8;

        NavSidebarSelector navSelector = new NavSidebarSelector();
        workspaceContainer.putClientProperty(CLIENT_NAV_SIDEBAR_SELECTOR, navSelector);

        for (NavItem item : getItems(user, connection, accountActions, frame, workspaceContainer)) {
            JButton button = new JButton(item.label);
            button.setAlignmentX(Component.LEFT_ALIGNMENT);
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, AppUI.CONTROL_HEIGHT + 6));
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setFocusPainted(false);
            button.setOpaque(true);
            button.setContentAreaFilled(true);
            if (item.viewBuilder != null) {
                navSelector.registerNavViewButton(item.label, button);
            } else {
                applyNavButtonDefaultStyle(button);
            }
            button.addActionListener(e -> {
                if (item.viewBuilder != null) {
                    try {
                        showView(workspaceContainer, item.label, item.viewBuilder.build());
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(frame, "Unable to open view: " + ex.getMessage(), "View Error", JOptionPane.ERROR_MESSAGE);
                    }
                    return;
                }
                new Thread(() -> runAction(item.action), "ims-action-" + item.label.replace(" ", "-")).start();
            });
            nav.add(button);
            nav.add(Box.createVerticalStrut(betweenNavButtons));
        }

        JScrollPane scrollPane = new JScrollPane(nav);
        scrollPane.setBorder(null);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);

        JPanel recentBlock = new JPanel();
        recentBlock.setLayout(new BoxLayout(recentBlock, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(recentBlock);
        recentBlock.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        Runnable refreshRecent = () -> rebuildRecentSidebarBlock(
                recentBlock, frame, workspaceContainer, user, connection, accountActions);
        registerRecentItemRefresher(refreshRecent);
        container.putClientProperty(CLIENT_RECENT_REFRESHER, refreshRecent);
        refreshRecent.run();

        container.add(header, BorderLayout.NORTH);
        container.add(scrollPane, BorderLayout.CENTER);
        container.add(recentBlock, BorderLayout.SOUTH);
        return container;
    }

    static void rebuildRecentSidebarBlock(
            JPanel recentBlock,
            JFrame frame,
            JPanel workspaceContainer,
            User user,
            Connection connection,
            AccountActions accountActions
    ) {
        recentBlock.removeAll();
        JLabel heading = new JLabel("Recent");
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 13f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        recentBlock.add(heading);
        recentBlock.add(Box.createVerticalStrut(6));
        List<RecentItemEntry> snapshot = snapshotRecentItems();
        if (snapshot.isEmpty()) {
            JLabel none = new JLabel("No recent items");
            none.setForeground(AppUI.TEXT_MUTED);
            none.setAlignmentX(Component.LEFT_ALIGNMENT);
            recentBlock.add(none);
        } else {
            for (RecentItemEntry entry : snapshot) {
                String label = entry.label() == null || entry.label().isBlank()
                        ? entry.itemCode()
                        : entry.itemCode() + " - " + entry.label();
                JButton recentButton = new JButton(label);
                recentButton.setToolTipText("Open View Items filtered by " + entry.itemCode());
                recentButton.setAlignmentX(Component.LEFT_ALIGNMENT);
                recentButton.setHorizontalAlignment(SwingConstants.LEFT);
                recentButton.setMaximumSize(new Dimension(Integer.MAX_VALUE, AppUI.CONTROL_HEIGHT + 4));
                styleSecondaryButton(recentButton);
                recentButton.addActionListener(e -> {
                    workspaceContainer.putClientProperty(CLIENT_VIEW_ITEMS_SEARCH_TEXT, entry.itemCode());
                    try {
                        showView(workspaceContainer, "View Items",
                                ViewItemsPanel.build(user, connection, frame, workspaceContainer));
                    } catch (SQLException ex) {
                        JOptionPane.showMessageDialog(frame, "Unable to open view: " + ex.getMessage(),
                                "View Error", JOptionPane.ERROR_MESSAGE);
                    }
                });
                recentBlock.add(recentButton);
                recentBlock.add(Box.createVerticalStrut(4));
            }
        }
        recentBlock.revalidate();
        recentBlock.repaint();
    }

    /**
     * Initial workspace card: blank themed canvas so the center feels empty until a function is chosen.
     * If {@link #WORKSPACE_WELCOME_IMAGE} exists and loads, it is shown centered (e.g. logo); otherwise the panel stays empty.
     */
    static JPanel buildEmptyWorkspaceCanvas() {
        JPanel canvas = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(canvas);
        if (!Files.isReadable(WORKSPACE_WELCOME_IMAGE)) {
            return canvas;
        }
        try {
            ImageIcon icon = new ImageIcon(WORKSPACE_WELCOME_IMAGE.toAbsolutePath().toString());
            if (icon.getIconWidth() <= 0) {
                return canvas;
            }
            JLabel picture = new JLabel(icon);
            picture.setHorizontalAlignment(SwingConstants.CENTER);
            picture.setVerticalAlignment(SwingConstants.CENTER);
            canvas.add(picture, BorderLayout.CENTER);
        } catch (Exception ignored) {
            // Keep empty canvas on any load failure.
        }
        return canvas;
    }

    /**
     * Returns navigation items for the signed-in role: one flat list in product order; admin-only entries omitted for standard users.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param accountActions account dialogs (backup, password reset)
     * @param frame parent workspace frame
     * @param workspaceContainer workspace card container
     * @return navigation item list
     */
    static List<NavItem> getItems(
            User user,
            Connection connection,
            AccountActions accountActions,
            JFrame frame,
            JPanel workspaceContainer
    ) {
        List<NavItem> items = new ArrayList<>();
        boolean admin = user.hasAdminRights();

        if (admin) {
            items.add(new NavItem("Add Item", () -> AddItemPanel.build(user, connection, workspaceContainer, frame)));
            items.add(new NavItem("Storage Locations", () -> StorageLocationsPanel.build(connection)));
            items.add(new NavItem("View Items", () -> ViewItemsPanel.build(user, connection, frame, workspaceContainer)));
        } else {
            items.add(new NavItem("View Items", () -> ViewItemsPanel.build(user, connection, frame, workspaceContainer)));
            items.add(new NavItem("Storage Locations", () -> StorageLocationsPanel.build(connection)));
        }
        items.add(new NavItem("Stock by Location", () -> StockByLocationPanel.build(user, connection)));
        items.add(new NavItem("Quick Transfer", () -> QuickTransferPanel.build(user, connection)));
        items.add(new NavItem(VIEW_PO_TRACKING, () -> PurchaseOrdersPanel.build(user, connection, workspaceContainer)));
        if (admin) {
            items.add(new NavItem("Suppliers", () -> SuppliersPanel.build(user, connection)));
        }
        items.add(new NavItem("Receive Order", () -> ReceiveOrderPanel.build(user, connection, workspaceContainer)));
        if (!admin) {
            items.add(new NavItem("Low Stock Check", () -> LowStockPanel.build(connection)));
        }
        items.add(new NavItem("Process Sale", () -> ProcessSalePanel.build(user, connection, workspaceContainer)));
        items.add(new NavItem("View Sales Transaction", () -> SalesPanel.build(user, connection, workspaceContainer)));
        if (admin) {
            items.add(new NavItem("Write Off Stock", () -> WriteOffPanel.build(user, connection)));
            items.add(new NavItem("Pricing & Reorder", () -> PricingReorderPanel.build(user, connection, workspaceContainer)));
            items.add(new NavItem("Low Stock Check", () -> LowStockPanel.build(connection)));
            items.add(new NavItem("Generate Reports", () -> ReportsPanel.build(user, connection)));
            items.add(new NavItem(VIEW_ADMIN_TOOLS, () -> AdministrationToolsPanel.build(user, connection, frame, accountActions)));
        }
        if (!admin) {
            items.add(new NavItem("Reset Password", () -> ResetPasswordPanel.build(user, frame, accountActions)));
        }
        items.add(new NavItem("Log Out", frame::dispose));
        return items;
    }

    /**
     * CardLayout registry keyed by sidebar label — prior component removed before re-add to avoid duplicate hidden cards.
     */
    @SuppressWarnings("unchecked")
    static Map<String, Component> workspaceCardRegistry(JPanel workspaceContainer) {
        Object raw = workspaceContainer.getClientProperty(CLIENT_WORKSPACE_CARD_REGISTRY);
        if (raw instanceof Map<?, ?> map) {
            return (Map<String, Component>) map;
        }
        Map<String, Component> created = new HashMap<>();
        workspaceContainer.putClientProperty(CLIENT_WORKSPACE_CARD_REGISTRY, created);
        return created;
    }

    /**
     * Displays a panel in the workspace card container under a stable key (replaces any prior panel for that key).
     *
     * @param workspaceContainer card layout host panel
     * @param key unique card key (matches sidebar nav label)
     * @param panel panel to display
     */
    static void showView(JPanel workspaceContainer, String key, JPanel panel) {
        AppUI.applyPanelBackground(panel);
        activeWorkspaceViewKey = key;
        Map<String, Component> cards = workspaceCardRegistry(workspaceContainer);
        Component prior = cards.get(key);
        if (prior != null && prior.getParent() == workspaceContainer) {
            workspaceContainer.remove(prior);
        }
        cards.put(key, panel);
        workspaceContainer.add(panel, key);
        CardLayout layout = (CardLayout) workspaceContainer.getLayout();
        layout.show(workspaceContainer, key);
        workspaceContainer.revalidate();
        workspaceContainer.repaint();
        Object selector = workspaceContainer.getClientProperty(CLIENT_NAV_SIDEBAR_SELECTOR);
        if (selector instanceof NavSidebarSelector navSidebarSelector) {
            navSidebarSelector.setSelectedCardKey(key);
        }
        if (!"View Items".equals(key)) {
            restoreMetricsRailToDefault();
        }
        requestMetricsRefresh();
    }

    /** Restores the admin right rail to the financial metrics card after leaving item-specific views. */
    static void restoreMetricsRailToDefault() {
        AdminMetricsRailHost host = adminMetricsRailHost;
        if (host != null) {
            host.showMetrics();
        }
    }

    /** Default (unselected) sidebar navigation button styling. */
    static void applyNavButtonDefaultStyle(JButton button) {
        AppUI.styleNavButton(button, false);
    }

    /** Highlights the active sidebar workspace card tab. */
    static void applyNavButtonSelectedStyle(JButton button) {
        AppUI.styleNavButton(button, true);
    }

    /**
     * Tracks which workspace card key is active and paints the matching sidebar button darker.
     */
    static final class NavSidebarSelector {
        private final Map<String, JButton> keyToButton = new HashMap<>();
        private String selectedKey;

        /** Associates a sidebar button with a workspace card key and applies default styling. */
        private void registerNavViewButton(String cardKey, JButton button) {
            keyToButton.put(cardKey, button);
            applyNavButtonDefaultStyle(button);
        }

        /** Updates which sidebar button renders as selected for the visible card key. */
        private void setSelectedCardKey(String key) {
            JButton previous = selectedKey == null ? null : keyToButton.get(selectedKey);
            if (previous != null) {
                applyNavButtonDefaultStyle(previous);
            }
            String nextKey = key != null && keyToButton.containsKey(key) ? key : null;
            selectedKey = nextKey;
            JButton next = nextKey == null ? null : keyToButton.get(nextKey);
            if (next != null) {
                applyNavButtonSelectedStyle(next);
            }
        }

        private boolean isCardActive(String key) {
            return key != null && key.equals(selectedKey);
        }
    }

    /**
     * Swaps the admin right rail between financial metrics and the View Items shelf (JPEG, stats, editable notes/actions).
     */
    static final class AdminMetricsRailHost {
        private final Connection connection;
        private final String username;
        private final JPanel host;
        private final JLabel titleLabel;
        private final JLabel imageLabel;
        private final JTextArea statsArea;
        private final JTextArea notesArea;
        private final JButton btnUpdateNote;
        private final JButton btnSaveNote;
        private final JButton btnCancelNote;
        private final JButton btnUpdateImage;
        private final Runnable refreshViewItemsIfOpen;

        /** Active SKU when showing the photo card; reused by note/image actions. */
        private String selectedItemCode;

        private AdminMetricsRailHost(
                Connection connection,
                String username,
                JPanel host,
                JLabel titleLabel,
                JLabel imageLabel,
                JTextArea statsArea,
                JTextArea notesArea,
                JButton btnUpdateNote,
                JButton btnSaveNote,
                JButton btnCancelNote,
                JButton btnUpdateImage,
                JButton btnNextWebPhoto,
                Runnable refreshViewItemsIfOpen
        ) {
            this.connection = connection;
            this.username = username;
            this.host = host;
            this.titleLabel = titleLabel;
            this.imageLabel = imageLabel;
            this.statsArea = statsArea;
            this.notesArea = notesArea;
            this.btnUpdateNote = btnUpdateNote;
            this.btnSaveNote = btnSaveNote;
            this.btnCancelNote = btnCancelNote;
            this.btnUpdateImage = btnUpdateImage;
            this.refreshViewItemsIfOpen = refreshViewItemsIfOpen;

            btnUpdateNote.addActionListener(e -> enterNotesEditMode());
            btnSaveNote.addActionListener(e -> commitRailNotes());
            btnCancelNote.addActionListener(e -> cancelNotesEdit());
            btnUpdateImage.addActionListener(e -> chooseAndReplacePhoto());
            btnNextWebPhoto.addActionListener(e -> cycleNextWebPhoto());

            applyNotesViewerStyle();
        }

        private void showMetrics() {
            exitNotesQuiet();
            CardLayout cl = (CardLayout) host.getLayout();
            cl.show(host, "metrics");
            host.revalidate();
            host.repaint();
        }

        private void showItemPhoto(String rawCode) {
            selectedItemCode = rawCode == null ? "" : rawCode.trim();
            if (selectedItemCode.isEmpty()) {
                showMetrics();
                return;
            }
            titleLabel.setText(selectedItemCode);
            exitNotesQuiet();
            refreshStatsPhotoAndNotes();
            CardLayout cl = (CardLayout) host.getLayout();
            cl.show(host, "photo");
            host.revalidate();
            host.repaint();
        }

        private void refreshStatsPhotoAndNotes() {
            if (selectedItemCode == null || selectedItemCode.isEmpty()) {
                return;
            }
            try {
                statsArea.setText(buildItemRailStatsText(connection, selectedItemCode));
            } catch (SQLException ex) {
                statsArea.setText("Could not load stats:\n" + ex.getMessage());
            }
            statsArea.setCaretPosition(0);
            refillNotesAreaFromDb();
            updatePhotoRailImageLabel(imageLabel, selectedItemCode);
        }

        /** Reloads the JPEG preview when the on-disk file changed (e.g. after a bulk photo fetch). */
        private void refreshSelectedItemPhoto() {
            refreshStatsPhotoAndNotes();
        }

        private void refillNotesAreaFromDb() {
            try {
                notesArea.setText(fetchInventoryNotes(connection, selectedItemCode));
            } catch (SQLException ex) {
                notesArea.setText("(Could not load notes: " + ex.getMessage() + ")");
            }
            notesArea.setCaretPosition(0);
        }

        private void applyNotesViewerStyle() {
            notesArea.setEditable(false);
            notesArea.setOpaque(false);
            notesArea.setBackground(host.getBackground());
        }

        private void enterNotesEditMode() {
            if (selectedItemCode == null || selectedItemCode.isEmpty()) {
                return;
            }
            btnUpdateNote.setEnabled(false);
            btnSaveNote.setVisible(true);
            btnCancelNote.setVisible(true);
            notesArea.setEditable(true);
            notesArea.setOpaque(true);
            notesArea.setBackground(AppUI.INPUT);
            notesArea.requestFocusInWindow();
        }

        /** Exits edit mode without reloading displayed text from the DB. */
        private void exitNotesQuiet() {
            btnUpdateNote.setEnabled(true);
            btnSaveNote.setVisible(false);
            btnCancelNote.setVisible(false);
            applyNotesViewerStyle();
        }

        private void cancelNotesEdit() {
            refillNotesAreaFromDb();
            exitNotesQuiet();
        }

        private void commitRailNotes() {
            if (selectedItemCode == null || selectedItemCode.isEmpty()) {
                return;
            }
            try {
                String raw = notesArea.getText();
                if (raw != null && raw.length() > ITEM_NOTES_MAX_CHARS) {
                    JOptionPane.showMessageDialog(
                            host,
                            "Notes must be at most " + ITEM_NOTES_MAX_CHARS + " characters.",
                            "Input",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
                if (!inventoryItemExists(connection, selectedItemCode)) {
                    JOptionPane.showMessageDialog(host, "That item code is no longer in inventory.", "Change note", JOptionPane.WARNING_MESSAGE);
                    cancelNotesEdit();
                    return;
                }
                persistInventoryNotes(connection, selectedItemCode, raw);
                InventoryAudit.logChange(
                        connection,
                        username,
                        selectedItemCode,
                        InventoryAudit.CHANGE_NOTE,
                        0,
                        "RAIL_NOTE_SAVE",
                        raw == null ? "" : raw.trim());
                JOptionPane.showMessageDialog(host, "Note saved.", "Notes", JOptionPane.INFORMATION_MESSAGE);
                exitNotesQuiet();
                refillNotesAreaFromDb();
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(host, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        void openItemForNotesEdit(String itemCode) {
            if (itemCode == null || itemCode.isBlank()) {
                return;
            }
            showItemPhoto(itemCode.trim());
            enterNotesEditMode();
        }

        private void chooseAndReplacePhoto() {
            if (selectedItemCode == null || selectedItemCode.isEmpty()) {
                return;
            }
            try {
                if (!inventoryItemExists(connection, selectedItemCode)) {
                    JOptionPane.showMessageDialog(host, "That item code is no longer in inventory.", "Photo", JOptionPane.WARNING_MESSAGE);
                    return;
                }
                JFileChooser fc = new JFileChooser();
                fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter(
                        "JPEG images (.jpg, .jpeg)", "jpg", "jpeg"));
                if (fc.showOpenDialog(host) != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                Path src = fc.getSelectedFile().toPath();
                copySourceJpegToItemPhoto(src, selectedItemCode);
                ItemPhotoFetcher.clearPhotoCycle(selectedItemCode);
                updatePhotoRailImageLabel(imageLabel, selectedItemCode);
                if (refreshViewItemsIfOpen != null) {
                    refreshViewItemsIfOpen.run();
                }
                JOptionPane.showMessageDialog(host, "Photo updated on disk.", "Photo", JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(host, "Photo replace failed:\n" + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(host, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        /** Fetches the next DuckDuckGo image for the selected SKU and replaces its on-disk JPEG. */
        private void cycleNextWebPhoto() {
            if (selectedItemCode == null || selectedItemCode.isEmpty()) {
                JOptionPane.showMessageDialog(host, "Select an item first.", "Next web photo", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            try {
                if (!inventoryItemExists(connection, selectedItemCode)) {
                    JOptionPane.showMessageDialog(host, "That item code is no longer in inventory.", "Next web photo", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(host, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String itemName = queryInventoryItemDescription(connection, selectedItemCode);
            if (itemName.isEmpty()) {
                JOptionPane.showMessageDialog(
                        host,
                        "This item has no description to search with.",
                        "Next web photo",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }

            final String code = selectedItemCode;
            Window window = SwingUtilities.getWindowAncestor(host);
            if (window != null) {
                window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
            }
            new Thread(() -> {
                boolean saved;
                String error = null;
                try {
                    saved = ItemPhotoFetcher.saveNextWebPhoto(code, itemName);
                } catch (Exception ex) {
                    saved = false;
                    error = ex.getMessage();
                }
                final boolean ok = saved;
                final String err = error;
                SwingUtilities.invokeLater(() -> {
                    if (window != null) {
                        window.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    }
                    if (err != null) {
                        JOptionPane.showMessageDialog(
                                host,
                                "Photo search failed:\n" + err,
                                "Next web photo",
                                JOptionPane.ERROR_MESSAGE);
                        return;
                    }
                    if (!ok) {
                        JOptionPane.showMessageDialog(
                                host,
                                "No more web images found for:\n" + itemName,
                                "Next web photo",
                                JOptionPane.INFORMATION_MESSAGE);
                        return;
                    }
                    invalidateViewItemThumbCache(code);
                    updatePhotoRailImageLabel(imageLabel, code);
                    if (refreshViewItemsIfOpen != null) {
                        refreshViewItemsIfOpen.run();
                    }
                });
            }, "ims-photo-cycle").start();
        }
    }

    /** Summary metrics for View Items rail (notes are edited in their own pane). */
    static String buildItemRailStatsText(Connection connection, String itemCode) throws SQLException {
        int stock = 0;
        int reorderTrigger = 0;
        Double marketPrice = null;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `Stock`, `ReOrder Trigger`, `Market Price` FROM inventory WHERE `Item Code` = ?"
        )) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stock = rs.getInt("Stock");
                    reorderTrigger = rs.getInt("ReOrder Trigger");
                    double mp = rs.getDouble("Market Price");
                    marketPrice = rs.wasNull() ? null : mp;
                }
            }
        }
        long soldQty = 0;
        double sumRev = 0;
        double sumCost = 0;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(SUM(`Amount`),0), COALESCE(SUM(`Total Price`),0), COALESCE(SUM(`Total Cost`),0) "
                        + "FROM sales WHERE `Item Code` = ?"
        )) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    soldQty = rs.getLong(1);
                    sumRev = rs.getDouble(2);
                    sumCost = rs.getDouble(3);
                }
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append("Current Stock:\n").append(stock).append("\n\n");
        if (reorderTrigger > 0) {
            sb.append("Reorder trigger:\n").append(reorderTrigger);
            if (isLowStockSku(stock, reorderTrigger)) {
                sb.append("  (low stock)");
            }
            sb.append("\n\n");
        }
        sb.append("Stored at:\n").append(buildItemStorageLocationsText(connection, itemCode)).append("\n");
        Double fifoUnitCost = InventoryFifo.weightedAverageFifoUnitCost(connection, itemCode);
        if (fifoUnitCost != null) {
            sb.append("Avg FIFO unit cost:\n").append(formatUsdMoney(fifoUnitCost)).append("\n\n");
        }
        if (marketPrice != null) {
            sb.append("Market price:\n").append(formatUsdMoney(marketPrice)).append("\n\n");
            if (fifoUnitCost != null && fifoUnitCost > 1e-12) {
                double marginPct = ((marketPrice - fifoUnitCost) / fifoUnitCost) * 100.0;
                sb.append("Unrealized margin:\n")
                        .append(String.format(Locale.US, "%.1f%%", marginPct))
                        .append(" (")
                        .append(formatUsdMoney(marketPrice - fifoUnitCost))
                        .append(" per unit)\n\n");
            }
        }
        sb.append("Amount Sold:\n").append(soldQty).append("\n\n");
        if (soldQty > 0) {
            double avgPurchase = sumCost / soldQty;
            double avgSale = sumRev / soldQty;
            sb.append("Average Purchase Price:\n").append(formatUsdMoney(avgPurchase)).append("\n\n");
            sb.append("Average Sale Price:\n").append(formatUsdMoney(avgSale)).append("\n\n");
        } else {
            sb.append("Average Purchase Price:\n—\n\n");
            sb.append("Average Sale Price:\n—\n\n");
        }
        sb.append("Total Revenue:\n").append(formatUsdMoney(sumRev)).append("\n\n");
        sb.append("Total P/L:\n").append(formatUsdMoney(sumRev - sumCost));
        return sb.toString();
    }

    static String buildItemStorageLocationsText(Connection connection, String itemCode) throws SQLException {
        StringBuilder sb = new StringBuilder();
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT sl.name AS name, s.qty AS qty "
                        + "FROM inventory_storage_qty s "
                        + "JOIN storage_locations sl ON sl.id = s.location_id "
                        + "WHERE s.item_code = ? AND s.qty > 0 "
                        + "ORDER BY s.qty DESC, sl.name"
        )) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    sb.append(rs.getString("name")).append(": ").append(rs.getInt("qty")).append('\n');
                }
            }
        }
        return sb.length() == 0 ? "(no location assigned)\n" : sb.toString();
    }

    /** Loads {@code Inventory.Notes} text for an item code. */
    static String fetchInventoryNotes(Connection connection, String itemCode) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT COALESCE(`Notes`, '') AS n FROM inventory WHERE `Item Code` = ? LIMIT 1"
        )) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Objects.toString(rs.getString("n"), "") : "";
            }
        }
    }

    /** Persists trimmed notes; empty clears the column. */
    static void persistInventoryNotes(Connection connection, String itemCode, String rawNotesText)
            throws SQLException {
        String t = rawNotesText == null ? "" : rawNotesText.trim();
        try (PreparedStatement ps = connection.prepareStatement(
                "UPDATE inventory SET `Notes` = ? WHERE `Item Code` = ?")) {
            if (t.isEmpty()) {
                ps.setNull(1, java.sql.Types.VARCHAR);
            } else {
                ps.setString(1, t);
            }
            ps.setString(2, itemCode);
            ps.executeUpdate();
        }
    }

    /** Refreshes JPEG preview on an image label for the rail. */
    static void updatePhotoRailImageLabel(JLabel imageLabel, String itemCode) {
        Path p = itemImagePath(itemCode);
        ImageIcon icon = loadScaledItemPhotoIcon(p, ITEM_PHOTO_DISPLAY_MAX_W, ITEM_PHOTO_DISPLAY_MAX_H);
        if (icon != null) {
            imageLabel.setIcon(icon);
            imageLabel.setText(null);
            imageLabel.setForeground(AppUI.TEXT);
        } else {
            imageLabel.setIcon(null);
            imageLabel.setForeground(AppUI.TEXT_MUTED);
            imageLabel.setText("<html><center>No JPEG on file for this item.<br>"
                    + "<span style='font-size:11px'>item_images/" + itemCode + ".jpeg</span></center></html>");
        }
    }
    /**
     * Builds a generic launcher panel for simple one-click actions.
     *
     * @param title panel title
     * @param description supporting text
     * @param action action to run
     * @return launcher panel
     */
    static JPanel buildLauncherPanel(String title, String description, CheckedAction action) {
        JPanel panel = buildFormPanel(title);
        JPanel content = buildSectionPanel();

        JLabel heading = buildSectionTitle(title);
        JLabel desc = buildSectionText(description);

        JButton runButton = new JButton("Run " + title);
        AppUI.stylePrimaryButton(runButton);
        runButton.setAlignmentX(Component.LEFT_ALIGNMENT);
        runButton.addActionListener(e -> new Thread(() -> runAction(action), "ims-runner").start());

        content.add(heading);
        content.add(Box.createVerticalStrut(8));
        content.add(desc);
        content.add(Box.createVerticalStrut(14));
        content.add(runButton);
        panel.add(content, BorderLayout.NORTH);
        return panel;
    }

    /** Ensures {@link #ITEM_IMAGES_DIR} exists on disk (no-op when already present). */
    static void ensureItemImagesDir() throws IOException {
        Files.createDirectories(ITEM_IMAGES_DIR);
    }

    /**
     * Downloads item JPEGs on a worker thread (see {@link ItemPhotoFetcher}), then refreshes the photo rail
     * and View Items when that screen is open.
     */
    static void startItemPhotoFetchTask(
            Component parent,
            JPanel workspaceContainer,
            User user,
            JFrame frame,
            Connection connection,
            boolean replaceExisting
    ) {
        if (user != null) {
            ensureAdmin(user, "Fetch item photos");
        }
        Window window = parent instanceof Window w ? w : SwingUtilities.getWindowAncestor(parent);
        if (window != null) {
            window.setCursor(Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR));
        }
        new Thread(() -> {
            ItemPhotoFetcher.FetchResult result;
            try {
                result = ItemPhotoFetcher.fetchAll(replaceExisting);
            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    if (window != null) {
                        window.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                    }
                    JOptionPane.showMessageDialog(
                            parent,
                            "Photo fetch failed:\n" + ex.getMessage(),
                            "Fetch photos",
                            JOptionPane.ERROR_MESSAGE);
                });
                return;
            }
            SwingUtilities.invokeLater(() -> {
                if (window != null) {
                    window.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
                }
                JOptionPane.showMessageDialog(
                        parent,
                        "Photos saved: " + result.saved()
                                + "\nSkipped (already on file): " + result.skipped()
                                + "\nFailed: " + result.failed(),
                        "Fetch photos",
                        result.failed() > 0 ? JOptionPane.WARNING_MESSAGE : JOptionPane.INFORMATION_MESSAGE);
                if (result.saved() > 0) {
                    clearViewItemThumbCache();
                }
                AdminMetricsRailHost rail = adminMetricsRailHost;
                if (rail != null) {
                    rail.refreshSelectedItemPhoto();
                }
                refreshViewItemsIfOpen(workspaceContainer, user, connection, frame);
            });
        }, "ims-photo-fetch").start();
    }

    static void refreshViewItemsIfOpen(
            JPanel workspaceContainer, User user, Connection connection, JFrame frame
    ) {
        if (workspaceContainer == null || user == null || connection == null) {
            return;
        }
        Object selector = workspaceContainer.getClientProperty(CLIENT_NAV_SIDEBAR_SELECTOR);
        if (!(selector instanceof NavSidebarSelector nav) || !nav.isCardActive("View Items")) {
            return;
        }
        try {
            showView(workspaceContainer, "View Items", ViewItemsPanel.build(user, connection, frame, workspaceContainer));
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(frame, "Could not refresh View Items: " + ex.getMessage());
        }
    }

    /** Canonical on-disk JPEG path for an item code ({@code .jpeg}). */
    static Path itemImagePath(String itemCode) {
        return ITEM_IMAGES_DIR.resolve(itemCode + ".jpeg");
    }

    /** Accepts {@code .jpg}/{@code .jpeg} filenames (case-insensitive). */
    static boolean isJpegFileName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpeg") || n.endsWith(".jpg");
    }

    /**
     * Validates and copies {@code source} into {@code item_images/&lt;itemCode&gt;.jpeg}.
     *
     * @throws IOException when the source is unreadable or not a valid JPEG
     */
    static void copySourceJpegToItemPhoto(Path source, String itemCode) throws IOException {
        if (!Files.isRegularFile(source)) {
            throw new IOException("Photo path is not a readable file.");
        }
        if (!isJpegFileName(source.getFileName().toString())) {
            throw new IOException("Only JPEG files (.jpg or .jpeg) are allowed.");
        }
        if (ImageIO.read(source.toFile()) == null) {
            throw new IOException("File is not a valid JPEG image.");
        }
        ensureItemImagesDir();
        Files.copy(source, itemImagePath(itemCode), StandardCopyOption.REPLACE_EXISTING);
        invalidateViewItemThumbCache(itemCode);
    }

    static String viewItemThumbCacheKey(String itemCode, int boxPx) {
        return itemCode + "\u0001" + boxPx;
    }

    static void invalidateViewItemThumbCache(String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            return;
        }
        String prefix = itemCode + "\u0001";
        viewItemThumbCache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    static void clearViewItemThumbCache() {
        viewItemThumbCache.clear();
    }

    /**
     * Loads a View Items card thumbnail, using an in-memory cache (including negative hits for missing files).
     */
    static ImageIcon loadCachedViewItemThumbIcon(String itemCode, int boxPx) {
        String key = viewItemThumbCacheKey(itemCode, boxPx);
        Object cached = viewItemThumbCache.get(key);
        if (cached == VIEW_ITEM_THUMB_CACHE_MISS) {
            return null;
        }
        if (cached instanceof ImageIcon icon) {
            return icon;
        }
        ImageIcon loaded = loadScaledItemPhotoIcon(itemImagePath(itemCode), boxPx, boxPx);
        viewItemThumbCache.put(key, loaded == null ? VIEW_ITEM_THUMB_CACHE_MISS : loaded);
        return loaded;
    }

    /**
     * Loads an item JPEG and returns an icon scaled down to fit a square box, preserving aspect ratio.
     *
     * @return icon, or {@code null} if the file is missing or not a readable image
     */
    static ImageIcon loadScaledItemPhotoIcon(Path path, int maxSide) {
        return loadScaledItemPhotoIcon(path, maxSide, maxSide);
    }

    /**
     * Loads an item JPEG and returns an icon whose size fits inside {@code maxWidth}×{@code maxHeight} without
     * upscaling (aspect ratio preserved). Missing or invalid files yield {@code null}.
     */
    static ImageIcon loadScaledItemPhotoIcon(Path path, int maxWidth, int maxHeight) {
        try {
            if (!Files.isRegularFile(path)) {
                return null;
            }
            BufferedImage raw = ImageIO.read(path.toFile());
            if (raw == null) {
                return null;
            }
            int w = raw.getWidth();
            int h = raw.getHeight();
            if (w <= 0 || h <= 0) {
                return null;
            }
            double scale = Math.min(1.0, Math.min((double) maxWidth / w, (double) maxHeight / h));
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));
            Image scaled = raw.getScaledInstance(nw, nh, Image.SCALE_SMOOTH);
            BufferedImage out = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g2 = out.createGraphics();
            g2.drawImage(scaled, 0, 0, null);
            g2.dispose();
            return new ImageIcon(out);
        } catch (IOException e) {
            return null;
        }
    }

    /** Appends label/value pair rows to the item detail dialog grid. */
    static void addDetailFieldRow(JPanel grid, String label, String value) {
        int row = grid.getComponentCount() / 2;
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 0, 5, 14);
        gbc.gridy = row;
        gbc.gridx = 0;
        gbc.anchor = GridBagConstraints.LINE_END;
        gbc.weightx = 0;
        gbc.fill = GridBagConstraints.NONE;
        JLabel l = new JLabel(label, SwingConstants.RIGHT);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        l.setForeground(AppUI.TEXT_MUTED);
        l.setMinimumSize(new Dimension(FORM_LABEL_MIN_WIDTH, 22));
        grid.add(l, gbc);
        gbc.gridx = 1;
        gbc.anchor = GridBagConstraints.LINE_START;
        gbc.weightx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        JLabel v = new JLabel(value == null ? "" : value);
        grid.add(v, gbc);
    }

    /** Modal item summary with optional JPEG; admins can replace the photo. */
    static void showItemDetailDialog(Component parent, User user, Connection connection, String itemCode) {
        String itemName;
        int stock;
        int onOrder;
        String supplier;
        int leadVal;
        boolean leadNull;
        int reorder;
        double marketUnit;
        boolean marketNull;
        double latestLayerUnit;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `Item Name`, `Stock`, `On Order`, COALESCE(`Supplier`, '') AS sup, "
                        + "`Lead Time`, `ReOrder Trigger`, `Market Price` FROM inventory WHERE `Item Code` = ?"
        )) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(parent, "Item not found: " + itemCode);
                    return;
                }
                itemName = rs.getString("Item Name");
                stock = rs.getInt("Stock");
                onOrder = rs.getInt("On Order");
                supplier = rs.getString("sup");
                leadVal = rs.getInt("Lead Time");
                leadNull = rs.wasNull();
                reorder = rs.getInt("ReOrder Trigger");
                marketUnit = rs.getDouble("Market Price");
                marketNull = rs.wasNull();
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Unable to load item: " + ex.getMessage());
            return;
        }
        try {
            latestLayerUnit = InventoryFifo.latestRecordedUnitCost(connection, itemCode);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Unable to load cost layers: " + ex.getMessage());
            return;
        }

        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner);
        dialog.setTitle("Item: " + itemCode);
        dialog.setModal(true);
        dialog.setDefaultCloseOperation(JDialog.DISPOSE_ON_CLOSE);
        JPanel root = new JPanel(new BorderLayout(12, 12));
        root.setBorder(BorderFactory.createEmptyBorder(14, 14, 14, 14));
        AppUI.applyPanelBackground(root);

        JLabel photoLabel = new JLabel(" ", SwingConstants.CENTER);
        photoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        photoLabel.setVerticalAlignment(SwingConstants.CENTER);
        photoLabel.setForeground(AppUI.TEXT_MUTED);

        JPanel fields = new JPanel(new GridBagLayout());
        AppUI.applyPanelBackground(fields);
        addDetailFieldRow(fields, "Item code:", itemCode);
        addDetailFieldRow(fields, "Item name:", itemName == null ? "" : itemName);
        addDetailFieldRow(fields, "Supplier:", supplier == null || supplier.isEmpty() ? "—" : supplier);
        addDetailFieldRow(fields, "Stock:", String.valueOf(stock));
        addDetailFieldRow(fields, "On order:", String.valueOf(onOrder));
        addDetailFieldRow(fields, "Lead time (days):", leadNull ? "—" : String.valueOf(leadVal));
        addDetailFieldRow(fields, "Reorder trigger:", String.valueOf(reorder));
        addDetailFieldRow(fields, "Market price (ea.):", marketNull ? "—" : formatUsdMoney(marketUnit));
        addDetailFieldRow(fields, "Latest receipt unit cost (layers):",
                Double.isNaN(latestLayerUnit) ? "—" : formatUsdMoney(latestLayerUnit));

        Runnable reloadPhoto = () -> {
            Path p = itemImagePath(itemCode);
            ImageIcon icon = loadScaledItemPhotoIcon(p, ITEM_PHOTO_DISPLAY_MAX_W, ITEM_PHOTO_DISPLAY_MAX_H);
            if (icon != null) {
                photoLabel.setIcon(icon);
                photoLabel.setText(null);
            } else {
                photoLabel.setIcon(null);
                photoLabel.setText("<html><center>No photo on file.<br>JPEG path: <code>item_images/" + itemCode + ".jpeg</code></center></html>");
            }
        };

        reloadPhoto.run();

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        AppUI.applyPanelBackground(south);
        JButton close = new JButton("Close");
        styleSecondaryButton(close);
        close.addActionListener(e -> dialog.dispose());
        south.add(close);
        if (user != null && user.hasAdminRights()) {
            JButton changePhoto = new JButton("Change photo…");
            styleSecondaryButton(changePhoto);
            changePhoto.addActionListener(e -> {
                JFileChooser fc = new JFileChooser();
                fc.setFileFilter(new FileNameExtensionFilter("JPEG images (.jpg, .jpeg)", "jpg", "jpeg"));
                if (fc.showOpenDialog(dialog) == JFileChooser.APPROVE_OPTION) {
                    try {
                        copySourceJpegToItemPhoto(fc.getSelectedFile().toPath(), itemCode);
                        reloadPhoto.run();
                        photoLabel.revalidate();
                        JOptionPane.showMessageDialog(dialog, "Photo updated.");
                    } catch (IOException ex) {
                        JOptionPane.showMessageDialog(dialog, ex.getMessage(), "Photo error", JOptionPane.WARNING_MESSAGE);
                    }
                }
            });
            south.add(changePhoto);
        }

        root.add(photoLabel, BorderLayout.NORTH);
        root.add(fields, BorderLayout.CENTER);
        root.add(south, BorderLayout.SOUTH);

        JPanel shell = new JPanel(new BorderLayout());
        shell.setOpaque(true);
        shell.setBackground(AppUI.BACKGROUND);
        if (AppUI.usesEmbeddedTitleBar()) {
            shell.add(AppUI.createApplicationTitleBar("Item: " + itemCode), BorderLayout.NORTH);
        }
        shell.add(root, BorderLayout.CENTER);
        dialog.setContentPane(shell);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        AppUI.applyWindowChrome(dialog);
        AppUI.styleWindow(dialog);
        dialog.setVisible(true);
    }
















    /** Updates the admin right rail for a selected inventory SKU (or restores metrics when code is blank). */
    static void notifyAdminItemSelected(String itemCode) {
        AdminMetricsRailHost rail = adminMetricsRailHost;
        if (rail == null) {
            return;
        }
        if (itemCode == null || itemCode.isEmpty()) {
            rail.showMetrics();
        } else {
            recordRecentItem(itemCode, "");
            rail.showItemPhoto(itemCode);
        }
    }






    /** Builds a reusable filterable table panel from SQL query results. */
    static JPanel buildFilterableTablePanel(String title, String[] columns, String query, Connection connection) throws SQLException {
        return buildFilterableTablePanel(title, columns, query, connection, null, null, true, false, null);
    }

    /**
     * Builds a reusable filterable table panel from SQL query results.
     *
     * @param onRowDoubleClickModelIndex when non-null, invoked on double-click with the table and model row index
     * @param onTableBuilt               when non-null, invoked with the table after listeners are attached (e.g. View Items photo rail)
     * @param includeBottomRowFilterFields when true, a row of per-column filter fields is shown under the table
     * @param includeHeaderPopupFilters    when true, column headers gain a right-click filter menu (combined with bottom row filters if both enabled)
     * @param supplementalSouth            optional strip below the table (e.g. sales return form); mutually exclusive layout-wise with the bottom row filter bar
     */
    static JPanel buildFilterableTablePanel(
            String title,
            String[] columns,
            String query,
            Connection connection,
            BiConsumer<JTable, Integer> onRowDoubleClickModelIndex,
            Consumer<JTable> onTableBuilt,
            boolean includeBottomRowFilterFields,
            boolean includeHeaderPopupFilters,
            JComponent supplementalSouth
    ) throws SQLException {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
        AppUI.applyPanelBackground(panel);

        JLabel heading = buildSectionTitle(title);
        panel.add(heading, BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(columns, 0);
        JTable table = new JTable(model);
        installTableCopyMenu(table);
        TableRowSorter<DefaultTableModel> sorter = new TableRowSorter<>(model);
        table.setRowSorter(sorter);
        if (onRowDoubleClickModelIndex != null) {
            table.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() != 2) {
                        return;
                    }
                    int viewRow = table.rowAtPoint(e.getPoint());
                    if (viewRow < 0) {
                        return;
                    }
                    int modelRow = table.convertRowIndexToModel(viewRow);
                    onRowDoubleClickModelIndex.accept(table, modelRow);
                }
            });
        }

        try (Statement stmt = connection.createStatement(); ResultSet rs = stmt.executeQuery(query)) {
            while (rs.next()) {
                Object[] row = new Object[columns.length];
                for (int i = 0; i < columns.length; i++) {
                    row[i] = rs.getObject(columns[i]);
                }
                model.addRow(row);
            }
        }

        JPanel filterPanel = new JPanel(new GridLayout(1, columns.length, 8, 8));
        AppUI.applyPanelBackground(filterPanel);
        JTextField[] bottomFilters = includeBottomRowFilterFields ? new JTextField[columns.length] : null;
        Map<Integer, String> headerFilters = includeHeaderPopupFilters ? new HashMap<>() : null;

        Runnable applyCombinedRowFilter = () -> {
            if (!includeBottomRowFilterFields && !includeHeaderPopupFilters) {
                return;
            }
            List<RowFilter<Object, Integer>> parts = new ArrayList<>();
            if (bottomFilters != null) {
                for (int i = 0; i < bottomFilters.length; i++) {
                    if (bottomFilters[i] == null) {
                        continue;
                    }
                    String text = bottomFilters[i].getText();
                    if (text != null && !text.trim().isEmpty()) {
                        parts.add(RowFilter.regexFilter("(?i)" + Pattern.quote(text.trim()), i));
                    }
                }
            }
            if (headerFilters != null && !headerFilters.isEmpty()) {
                for (Map.Entry<Integer, String> e : headerFilters.entrySet()) {
                    int col = e.getKey();
                    if (col < 0 || col >= columns.length) {
                        continue;
                    }
                    String raw = e.getValue();
                    if (raw != null && !raw.trim().isEmpty()) {
                        parts.add(RowFilter.regexFilter("(?i)" + Pattern.quote(raw.trim()), col));
                    }
                }
            }
            sorter.setRowFilter(parts.isEmpty() ? null : RowFilter.andFilter(parts));
        };

        if (includeBottomRowFilterFields) {
            for (int i = 0; i < columns.length; i++) {
                JTextField filter = new JTextField();
                styleInput(filter);
                bottomFilters[i] = filter;
                filter.addCaretListener(e -> applyCombinedRowFilter.run());
                filterPanel.add(filter);
            }
        }

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(AppUI.newRoundedBorder(8));
        JPanel tableStack = new JPanel(new BorderLayout(0, 0));
        AppUI.applyPanelBackground(tableStack);
        tableStack.add(tableScroll, BorderLayout.CENTER);
        if (supplementalSouth != null) {
            tableStack.add(supplementalSouth, BorderLayout.SOUTH);
        }
        panel.add(tableStack, BorderLayout.CENTER);
        if (includeBottomRowFilterFields) {
            panel.add(filterPanel, BorderLayout.SOUTH);
        }
        if (includeHeaderPopupFilters && headerFilters != null) {
            installExcelStyleHeaderColumnFilters(table, headerFilters, applyCombinedRowFilter);
        }
        if (onTableBuilt != null) {
            onTableBuilt.accept(table);
        }
        return panel;
    }

    /** Right-click a column header: set a text contains filter; combines with other columns using AND. */
    static void installExcelStyleHeaderColumnFilters(
            JTable table,
            Map<Integer, String> activeByModelColumn,
            Runnable refreshFilter
    ) {
        JTableHeader header = table.getTableHeader();
        if (header == null) {
            return;
        }
        header.setToolTipText("Right-click a column header to filter (like Excel).");

        header.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybePopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybePopup(e);
            }

            private void maybePopup(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int viewCol = header.columnAtPoint(e.getPoint());
                if (viewCol < 0) {
                    return;
                }
                int modelCol = table.convertColumnIndexToModel(viewCol);
                Object headerVal = table.getColumnModel().getColumn(viewCol).getHeaderValue();
                String colName = headerVal == null ? ("Column " + modelCol) : headerVal.toString();
                String current = activeByModelColumn.getOrDefault(modelCol, "");

                JPopupMenu menu = new JPopupMenu();
                JMenuItem filterItem = new JMenuItem("Filter \"" + colName + "\"...");
                filterItem.addActionListener(ev -> {
                    Object input = JOptionPane.showInputDialog(
                            table.getRootPane(),
                            "Show rows containing (text):",
                            "Filter: " + colName,
                            JOptionPane.PLAIN_MESSAGE,
                            null,
                            null,
                            current
                    );
                    if (input == null) {
                        return;
                    }
                    String t = input.toString();
                    if (t.trim().isEmpty()) {
                        activeByModelColumn.remove(modelCol);
                    } else {
                        activeByModelColumn.put(modelCol, t.trim());
                    }
                    refreshFilter.run();
                });
                menu.add(filterItem);

                JMenuItem clearCol = new JMenuItem("Clear filter for this column");
                clearCol.addActionListener(ev -> {
                    activeByModelColumn.remove(modelCol);
                    refreshFilter.run();
                });
                menu.add(clearCol);

                JMenuItem clearAll = new JMenuItem("Clear all header filters");
                clearAll.addActionListener(ev -> {
                    activeByModelColumn.clear();
                    refreshFilter.run();
                });
                menu.add(clearAll);

                menu.show(e.getComponent(), e.getX(), e.getY());
            }
        });
    }


    /**
     * @param connection JDBC connection
     * @param itemCode   exact {@code Inventory.Item Code} value (already normalized by caller if needed)
     * @return {@code true} when a row exists
     */
    static boolean inventoryItemExists(Connection connection, String itemCode) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM inventory WHERE `Item Code` = ? LIMIT 1")) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }


    /**
     * Loads {@code Item Name} from {@code inventory} for a given item code (empty when not found).
     */
    static String queryInventoryItemDescription(Connection connection, String rawCode) {
        if (rawCode == null) {
            return "";
        }
        String code = rawCode.trim();
        if (code.isEmpty()) {
            return "";
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `Item Name` FROM inventory WHERE `Item Code` = ? LIMIT 1"
        )) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return "";
                }
                String n = rs.getString(1);
                return n == null ? "" : n.trim();
            }
        } catch (SQLException ex) {
            return "";
        }
    }

    /** Loads {@code Market Price} for a SKU, or {@code null} when missing or not set. */
    static Double queryInventoryMarketPrice(Connection connection, String rawCode) {
        if (rawCode == null) {
            return null;
        }
        String code = rawCode.trim();
        if (code.isEmpty()) {
            return null;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `Market Price` FROM inventory WHERE `Item Code` = ? LIMIT 1"
        )) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                double v = rs.getDouble(1);
                return rs.wasNull() ? null : v;
            }
        } catch (SQLException ex) {
            return null;
        }
    }


    /** Keeps a read-only description field in sync as the item code is edited. */
    static void wireInventoryItemDescriptionLookup(Connection connection, JTextField itemCodeField, JTextField descriptionTarget) {
        descriptionTarget.setEditable(false);
        DocumentListener dl = new DocumentListener() {
            private void apply() {
                SwingUtilities.invokeLater(() ->
                        descriptionTarget.setText(queryInventoryItemDescription(connection, itemCodeField.getText()))
                );
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                apply();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                apply();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                apply();
            }
        };
        itemCodeField.getDocument().addDocumentListener(dl);
        SwingUtilities.invokeLater(() -> descriptionTarget.setText(queryInventoryItemDescription(connection, itemCodeField.getText())));
    }






























    static final String PROFIT_ALERT_BANNER_SETUP_MESSAGE =
            "Go to admin tools to setup profit alert feature or disable banner";

    static final int PROFIT_ALERT_BANNER_MAX_ITEMS_LISTED = 35;

    static String profitAlertItemListPhrase(List<String> labels) {
        if (labels.isEmpty()) {
            return "none currently meet this threshold";
        }
        int n = labels.size();
        if (n > PROFIT_ALERT_BANNER_MAX_ITEMS_LISTED) {
            List<String> sub = new ArrayList<>(labels.subList(0, PROFIT_ALERT_BANNER_MAX_ITEMS_LISTED));
            return String.join(", ", sub) + " — +" + (n - PROFIT_ALERT_BANNER_MAX_ITEMS_LISTED) + " more SKU(s)";
        }
        return String.join(", ", labels);
    }

    static List<String> profitAlertQualifyingItemLabels(Connection connection, int goalPct) throws SQLException {
        double mult = 1.0 + goalPct / 100.0;
        List<String> labels = new ArrayList<>();
        String sql = """
                SELECT i.`Item Code` AS ic,
                       i.`Item Name` AS iname
                FROM inventory i
                INNER JOIN (
                    SELECT item_code AS icode,
                           SUM(CAST(qty_remaining AS REAL) * unit_cost) AS w,
                           SUM(qty_remaining) AS r
                    FROM inventory_cost_layers
                    WHERE qty_remaining > 0
                    GROUP BY item_code
                ) layer_tot ON layer_tot.icode = i.`Item Code`
                WHERE i.`Stock` > 0
                  AND i.`Market Price` IS NOT NULL
                  AND layer_tot.r > 0
                  AND (CAST(layer_tot.w AS REAL) / layer_tot.r) > 0
                  AND i.`Market Price` >= (CAST(layer_tot.w AS REAL) / layer_tot.r) * ?
                ORDER BY i.`Item Code` COLLATE NOCASE ASC
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            ps.setDouble(1, mult);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String code = rs.getString("ic");
                    String nm = Objects.toString(rs.getString("iname"), "").trim();
                    String shortNm = nm.length() > 28 ? nm.substring(0, 27) + "…" : nm;
                    if (shortNm.isEmpty()) {
                        labels.add(code);
                    } else {
                        labels.add(code + " (" + shortNm + ")");
                    }
                }
            }
        }
        return labels;
    }

    /** Single horizontal scrolling stripe (profit green or amber setup prompt). */
    static final class AlertMarqueeStripe extends JPanel {
        static final int STRIPE_HEIGHT_PX = 34;
        static final int TIMER_MS = 32;
        static final int SCROLL_STEP_PX = 2;
        static final String MARQUEE_GAP = "     ";

        private final Color stripeFg;
        private final Timer timer;
        private int scrollPx;
        private volatile String marqueeSegment = "";
        private volatile int marqueeLoopWidthPx = 400;

        AlertMarqueeStripe(Color stripeBg, Color stripeFg, Color stripeBorderTop) {
            this.stripeFg = stripeFg;
            setOpaque(true);
            setBackground(stripeBg);
            setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(1, 0, 0, 0, stripeBorderTop),
                    BorderFactory.createEmptyBorder(4, 0, 4, 0)));
            setPreferredSize(new Dimension(480, STRIPE_HEIGHT_PX));
            setMinimumSize(new Dimension(80, STRIPE_HEIGHT_PX));
            timer = new Timer(TIMER_MS, e -> {
                if (!isShowing()) {
                    return;
                }
                int w = Math.max(1, marqueeLoopWidthPx);
                scrollPx = (scrollPx + SCROLL_STEP_PX) % w;
                repaint();
            });
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, STRIPE_HEIGHT_PX);
        }

        void stopTimer() {
            timer.stop();
        }

        void startTimerIfShowing() {
            if (isShowing()) {
                timer.start();
            }
        }

        void prepareMarquee(String segment) {
            marqueeSegment = segment == null || segment.isEmpty() ? " " : segment;
            Font font = getFont().deriveFont(Font.BOLD, 13f);
            FontMetrics fm = getFontMetrics(font);
            String unit = marqueeSegment + MARQUEE_GAP;
            marqueeLoopWidthPx = Math.max(80, fm.stringWidth(unit));
            scrollPx = 0;
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            if (marqueeSegment.isEmpty()) {
                return;
            }
            Graphics2D g2 = (Graphics2D) g.create();
            try {
                g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                Font font = getFont().deriveFont(Font.BOLD, 13f);
                g2.setFont(font);
                FontMetrics fm = g2.getFontMetrics();
                String unit = marqueeSegment + MARQUEE_GAP;
                int h = getHeight();
                int baseline = (h + fm.getAscent() - fm.getDescent()) / 2;
                int wPanel = getWidth();
                int lw = Math.max(1, marqueeLoopWidthPx);
                int offset = scrollPx % lw;
                g2.setColor(getBackground());
                g2.fillRect(0, 0, wPanel, h);
                g2.setColor(stripeFg);
                for (int x = -offset; x < wPanel + lw; x += lw) {
                    g2.drawString(unit, x, baseline);
                }
            } finally {
                g2.dispose();
            }
        }
    }

    /**
     * Bottom-of-frame marquee driven by {@link DatabaseManager#META_PROFIT_ALERT_GOAL_PCT} and banner disabled flag.
     */
    static final class ProfitAlertMarqueeBanner extends JPanel {
        static final Color SETUP_BG = new Color(0x2a2418);
        static final Color SETUP_FG = new Color(0xfbbf24);
        static final Color SETUP_BORDER = new Color(0xb45309);
        static final Color PROFIT_BG = new Color(0x0f2419);
        static final Color PROFIT_FG = AppUI.SUCCESS;
        static final Color PROFIT_BORDER = new Color(0x059669);

        private final AlertMarqueeStripe setupStripe;
        private final AlertMarqueeStripe profitStripe;

        ProfitAlertMarqueeBanner() {
            setLayout(new BorderLayout());
            setupStripe = new AlertMarqueeStripe(SETUP_BG, SETUP_FG, SETUP_BORDER);
            profitStripe = new AlertMarqueeStripe(PROFIT_BG, PROFIT_FG, PROFIT_BORDER);
        }

        @Override
        public Dimension getMaximumSize() {
            return new Dimension(Integer.MAX_VALUE, super.getPreferredSize().height);
        }

        void stopTimer() {
            setupStripe.stopTimer();
            profitStripe.stopTimer();
        }

        void refreshFromDatabase(Connection connection) {
            setupStripe.stopTimer();
            profitStripe.stopTimer();
            removeAll();
            setLayout(new BorderLayout());
            try {
                if ("1".equals(DatabaseManager.getAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_BANNER_DISABLED))) {
                    setVisible(false);
                    revalidate();
                    repaint();
                    return;
                }
                String goalRaw = DatabaseManager.getAppMetadata(connection, DatabaseManager.META_PROFIT_ALERT_GOAL_PCT);
                int profitGoal = -1;
                if (goalRaw != null && !goalRaw.isBlank()) {
                    try {
                        profitGoal = Integer.parseInt(goalRaw.trim());
                    } catch (NumberFormatException ex) {
                        profitGoal = -1;
                    }
                }
                boolean profitOk = profitGoal >= 0 && profitGoal <= 10_000_000;
                final AlertMarqueeStripe activeStripe;
                if (!profitOk) {
                    add(setupStripe, BorderLayout.CENTER);
                    setupStripe.prepareMarquee(PROFIT_ALERT_BANNER_SETUP_MESSAGE);
                    activeStripe = setupStripe;
                } else {
                    List<String> plabels = profitAlertQualifyingItemLabels(connection, profitGoal);
                    String profitMsg = "Goal of " + profitGoal + "% profit on the following items: "
                            + profitAlertItemListPhrase(plabels);
                    add(profitStripe, BorderLayout.CENTER);
                    profitStripe.prepareMarquee(profitMsg);
                    activeStripe = profitStripe;
                }
                setVisible(true);
                revalidate();
                repaint();
                SwingUtilities.invokeLater(activeStripe::startTimerIfShowing);
            } catch (SQLException ex) {
                removeAll();
                setLayout(new BorderLayout());
                add(setupStripe, BorderLayout.CENTER);
                setupStripe.prepareMarquee("Profit alert: could not load data.");
                setVisible(true);
                revalidate();
                repaint();
                SwingUtilities.invokeLater(setupStripe::startTimerIfShowing);
            }
        }
    }





    /**
     * Requires the signed-in administrator's login password against {@code users} before destructive flows.
     *
     * @param parent dialog parent frame
     * @param connection open JDBC session
     * @param adminUser currently signed-in user (verified as admin separately)
     * @param title short context phrase for dialogs (shown in captions)
     * @return {@code true} only when verification succeeds or user cancels is false and password matches
     */
    static boolean verifySignedInAdministratorPassword(
            Window parent,
            Connection connection,
            User adminUser,
            String title
    ) {
        final String caption = title == null || title.isBlank() ? "Administrator password" : title;
        final String username = adminUser.getUsername();
        String storedHash;
        try (PreparedStatement ps = connection.prepareStatement("SELECT password FROM users WHERE username = ?")) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    JOptionPane.showMessageDialog(
                            parent, "Cannot verify password — user record not found.", caption, JOptionPane.WARNING_MESSAGE);
                    return false;
                }
                storedHash = rs.getString("password");
            }
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(parent, "Database error: " + ex.getMessage(), caption, JOptionPane.ERROR_MESSAGE);
            return false;
        }

        JPanel pwPanel = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(pwPanel);
        pwPanel.add(
                new JLabel("Enter administrator password for \"" + username + "\" to continue:"),
                BorderLayout.NORTH);
        JPasswordField pwField = new JPasswordField();
        stylePasswordInput(pwField);
        pwField.setPreferredSize(new Dimension(320, INPUT_HEIGHT));
        pwPanel.add(pwField, BorderLayout.CENTER);

        int dlg = JOptionPane.showConfirmDialog(
                parent,
                pwPanel,
                caption + " — password required",
                JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE);

        char[] typed = dlg == JOptionPane.OK_OPTION ? pwField.getPassword() : new char[0];
        pwField.setText("");
        try {
            if (dlg != JOptionPane.OK_OPTION) {
                return false;
            }
            if (!SecurityUtils.verifyPassword(typed, storedHash)) {
                JOptionPane.showMessageDialog(parent, "Incorrect password. Reset cancelled.", caption, JOptionPane.WARNING_MESSAGE);
                return false;
            }
            return true;
        } finally {
            Arrays.fill(typed, '\0');
        }
    }










    /** Converts display datetime (dd-MM-yyyy HH:mm:ss) into ISO datetime. */
    static String toIsoDateTime(String displayDateTime) {
        if (displayDateTime == null || displayDateTime.length() < 19) {
            return null;
        }
        return displayDateTime.substring(6, 10) + "-" +
                displayDateTime.substring(3, 5) + "-" +
                displayDateTime.substring(0, 2) +
                displayDateTime.substring(10);
    }

    static int sqlDaysSinceDisplayDate(String displayDateTime) {
        if (displayDateTime == null || displayDateTime.length() < 10) {
            return Integer.MAX_VALUE;
        }
        try {
            LocalDate d = LocalDate.parse(
                    displayDateTime.substring(6, 10) + "-"
                            + displayDateTime.substring(3, 5) + "-"
                            + displayDateTime.substring(0, 2));
            return (int) Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(d, LocalDate.now()));
        } catch (RuntimeException ex) {
            return Integer.MAX_VALUE;
        }
    }


    /** Sanitizes filename tokens for export-safe output paths. */
    static String sanitizeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }


    /** Creates a standard form container panel with section heading. */
    static JPanel buildFormPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        AppUI.applyPanelBackground(panel);
        JLabel heading = buildSectionTitle(title);
        panel.add(heading, BorderLayout.NORTH);
        return panel;
    }

    /** Creates a vertically stacked section panel for form content. */
    static JPanel buildSectionPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        AppUI.applyPanelBackground(panel);
        return panel;
    }

    /** Creates a section title label with consistent heading style. */
    static JLabel buildSectionTitle(String text) {
        JLabel heading = new JLabel(text, SwingConstants.LEFT);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 21f));
        return heading;
    }

    /** Section heading for stacked blocks on Administration Tools (smaller than page titles). */
    static JLabel adminToolsSectionTitle(String text) {
        JLabel heading = new JLabel(text, SwingConstants.LEFT);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 14f));
        heading.setAlignmentX(Component.LEFT_ALIGNMENT);
        return heading;
    }

    /** Creates a section body text label with standard style; wraps long copy for readability. */
    static JLabel buildSectionText(String text) {
        String body = text == null ? "" : text.trim();
        JLabel label;
        if (body.length() > 96 || body.contains("\n")) {
            String html = htmlEscapePlainTextForJLabel(body).replace("\n", "<br>");
            label = new JLabel("<html><body style='width:720px;color:#a1a1a1;'>" + html + "</body></html>");
        } else {
            label = new JLabel(body, SwingConstants.LEFT);
            label.setForeground(AppUI.TEXT_MUTED);
        }
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
        label.setAlignmentX(Component.LEFT_ALIGNMENT);
        return label;
    }

    /** Form label with consistent width for GridBag right-column alignment; grows to fit long captions so text is never clipped. */
    static JLabel buildFormLabel(String text) {
        JLabel label = new JLabel(text, SwingConstants.RIGHT);
        label.setForeground(AppUI.TEXT_MUTED);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
        int natural = label.getPreferredSize().width;
        int w = Math.max(natural, FORM_LABEL_MIN_WIDTH);
        label.setMinimumSize(new Dimension(w, AppUI.CONTROL_HEIGHT));
        label.setPreferredSize(new Dimension(w, AppUI.CONTROL_HEIGHT));
        return label;
    }

    /** Creates a left/right aligned action button bar. */
    static JPanel buildActionBar(JButton leftButton, JButton rightButton) {
        JPanel actionBar = new JPanel(new BorderLayout(12, 0));
        actionBar.setBorder(BorderFactory.createEmptyBorder(14, 0, 0, 0));
        AppUI.applyPanelBackground(actionBar);
        if (leftButton != null) {
            leftButton.setMinimumSize(new Dimension(120, AppUI.CONTROL_HEIGHT));
            actionBar.add(leftButton, BorderLayout.WEST);
        }
        if (rightButton != null) {
            rightButton.setMinimumSize(new Dimension(120, AppUI.CONTROL_HEIGHT));
            actionBar.add(rightButton, BorderLayout.EAST);
        }
        return actionBar;
    }

    /** Applies shared secondary button styling. */
    static void styleSecondaryButton(JButton button) {
        AppUI.styleSecondaryButton(button);
    }



    /** Single-line preview for draft tables; newlines become spaces and long text ends with an ellipsis. */
    static String abbreviateForTableCell(String text, int maxChars) {
        String t = text.replace('\n', ' ').trim();
        if (t.length() <= maxChars) {
            return t;
        }
        return t.substring(0, Math.max(0, maxChars - 1)) + "…";
    }








    /** After data is present: widens Item Description / Item Name to fit sampled content; other columns use default resizing. */
    static void deferPackTableColumns(JTable table) {
        if (table == null) {
            return;
        }
        SwingUtilities.invokeLater(() -> applyItemDescriptionColumnSizing(table));
    }

    /** Model index of {@code Item Description}, else {@code Item Name} / {@code Item name}; {@code -1} if none. */
    static int findDescriptionOrNameModelColumn(TableModel tm) {
        for (String want : new String[]{"Item Description", "Item Name", "Item name"}) {
            for (int i = 0; i < tm.getColumnCount(); i++) {
                if (want.equals(tm.getColumnName(i))) {
                    return i;
                }
            }
        }
        return -1;
    }

    /**
     * Sets preferred width on the Item Description / Item Name column from the header label and widest cell text
     * across all model rows ({@link FontMetrics} estimate). Other columns use
     * {@link JTable#AUTO_RESIZE_SUBSEQUENT_COLUMNS}.
     */
    static void applyItemDescriptionColumnSizing(JTable table) {
        TableModel tm = table.getModel();
        if (tm == null || tm.getColumnCount() <= 0) {
            return;
        }
        int modelCol = findDescriptionOrNameModelColumn(tm);
        if (modelCol < 0) {
            return;
        }
        int viewCol;
        try {
            viewCol = table.convertColumnIndexToView(modelCol);
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (viewCol < 0) {
            return;
        }
        JTableHeader header = table.getTableHeader();
        TableColumn col = table.getColumnModel().getColumn(viewCol);
        int width = ITEM_DESC_COLUMN_MIN_WIDTH;

        TableCellRenderer headerRenderer = col.getHeaderRenderer();
        if (headerRenderer == null && header != null) {
            headerRenderer = header.getDefaultRenderer();
        }
        if (headerRenderer != null) {
            Component headerComp = headerRenderer.getTableCellRendererComponent(table, col.getHeaderValue(), false,
                    false, -1, viewCol);
            width = Math.max(width, headerComp.getPreferredSize().width + 16);
        } else {
            FontMetrics hfm = table.getFontMetrics(table.getFont());
            Object hv = col.getHeaderValue();
            String hs = hv == null ? "" : hv.toString();
            width = Math.max(width, hfm.stringWidth(hs.isEmpty() ? " " : hs) + 20);
        }

        FontMetrics fm = table.getFontMetrics(table.getFont());
        final int measureCapChars = 4000;
        for (int mr = 0; mr < tm.getRowCount(); mr++) {
            Object v = tm.getValueAt(mr, modelCol);
            String s = v == null ? "" : v.toString();
            if (s.length() > measureCapChars) {
                s = s.substring(0, measureCapChars);
            }
            width = Math.max(width, fm.stringWidth(s.isEmpty() ? " " : s) + 24);
            if (width >= ITEM_DESC_COLUMN_MAX_WIDTH) {
                width = ITEM_DESC_COLUMN_MAX_WIDTH;
                break;
            }
        }

        width = Math.min(width, ITEM_DESC_COLUMN_MAX_WIDTH);
        width = Math.max(width, ITEM_DESC_COLUMN_MIN_WIDTH);
        col.setPreferredWidth(width);
        table.invalidate();
        if (header != null) {
            header.revalidate();
        }
        table.revalidate();
        Component scrollAncestor = SwingUtilities.getAncestorOfClass(JScrollPane.class, table);
        if (scrollAncestor != null) {
            scrollAncestor.revalidate();
        }
    }


    /** Installs right-click copy-cell menu behavior for a table. */
    static void installTableCopyMenu(JTable table) {
        table.setRowHeight(TABLE_ROW_HEIGHT);
        table.setFillsViewportHeight(true);
        table.setAutoResizeMode(JTable.AUTO_RESIZE_SUBSEQUENT_COLUMNS);
        table.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        if (table.getTableHeader() != null) {
            table.getTableHeader().setReorderingAllowed(false);
            table.getTableHeader().setResizingAllowed(true);
        }
        JPopupMenu popupMenu = new JPopupMenu();
        JMenuItem copyCell = new JMenuItem("Copy Cell");
        copyCell.addActionListener(e -> copySelectedCell(table));
        popupMenu.add(copyCell);

        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowMenu(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowMenu(e);
            }

            private void maybeShowMenu(MouseEvent e) {
                if (!e.isPopupTrigger()) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row >= 0 && col >= 0) {
                    table.setRowSelectionInterval(row, row);
                    table.setColumnSelectionInterval(col, col);
                    popupMenu.show(e.getComponent(), e.getX(), e.getY());
                }
            }
        });
        deferPackTableColumns(table);
    }

    /** Copies selected table cell text to the system clipboard. */
    static void copySelectedCell(JTable table) {
        int row = table.getSelectedRow();
        int col = table.getSelectedColumn();
        if (row < 0 || col < 0) {
            return;
        }
        Object value = table.getValueAt(row, col);
        String text = value == null ? "" : value.toString();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
    }

    static final class StorageLocationPick {
        final int id;
        final String label;
        final int qtyAvailable;

        StorageLocationPick(int id, String label) {
            this(id, label, 0);
        }

        StorageLocationPick(int id, String label, int qtyAvailable) {
            this.id = id;
            this.label = label;
            this.qtyAvailable = qtyAvailable;
        }

        @Override
        public String toString() {
            return qtyAvailable > 0 ? label + " (" + qtyAvailable + ")" : label;
        }
    }

    static void refreshActiveStorageLocationCombo(JComboBox<StorageLocationPick> combo, Connection connection) throws SQLException {
        combo.removeAllItems();
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        String sql = """
                SELECT id, name FROM storage_locations
                WHERE active = 1 OR id = ?
                ORDER BY sort_order ASC, name COLLATE NOCASE ASC
                """;
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            int uid = DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID;
            ps.setInt(1, uid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    combo.addItem(new StorageLocationPick(rs.getInt("id"), rs.getString("name")));
                }
            }
        }
        if (combo.getItemCount() == 0) {
            combo.addItem(new StorageLocationPick(DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID, "Unassigned"));
        }
    }

    static void incrementInventoryStorageQty(Connection connection, String itemCode, int locationId, int qty) throws SQLException {
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection) || qty <= 0) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                INSERT INTO inventory_storage_qty (item_code, location_id, qty) VALUES (?,?,?)
                ON CONFLICT(item_code, location_id) DO UPDATE SET
                  qty = inventory_storage_qty.qty + excluded.qty
                """)) {
            ps.setString(1, itemCode);
            ps.setInt(2, locationId);
            ps.setInt(3, qty);
            ps.executeUpdate();
        }
    }

    /** Quantity of {@code itemCode} in a single bin, or zero when none. */
    static int getInventoryStorageQtyAtLocation(Connection connection, String itemCode, int locationId) throws SQLException {
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return 0;
        }
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT qty FROM inventory_storage_qty WHERE item_code = ? AND location_id = ?")) {
            ps.setString(1, itemCode);
            ps.setInt(2, locationId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return 0;
                }
                return rs.getInt(1);
            }
        }
    }

    static String htmlEscapePlainTextForJLabel(String raw) {
        if (raw == null || raw.isEmpty()) {
            return "";
        }
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    static int resolveStorageLocationIdForItemBin(
            Connection connection,
            String itemCode,
            String locationName
    ) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT s.location_id
                FROM inventory_storage_qty s
                INNER JOIN storage_locations sl ON sl.id = s.location_id
                WHERE s.item_code = ? AND sl.name = ? COLLATE NOCASE
                """)) {
            ps.setString(1, itemCode);
            ps.setString(2, locationName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("Could not resolve the source bin for this row.");
                }
                return rs.getInt(1);
            }
        }
    }

    /**
     * Loads every storage location into the combo, omitting {@code excludeLocationId} (typically the move source).
     */
    static void fillStorageLocationComboExcluding(
            JComboBox<StorageLocationPick> combo,
            Connection connection,
            int excludeLocationId
    ) throws SQLException {
        combo.removeAllItems();
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT id, name FROM storage_locations
                ORDER BY sort_order ASC, name COLLATE NOCASE ASC
                """)) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id");
                    if (id != excludeLocationId) {
                        combo.addItem(new StorageLocationPick(id, rs.getString("name")));
                    }
                }
            }
        }
    }

    /** Bins that currently hold positive quantity for one SKU (for Quick Transfer source picker). */
    static void fillStorageLocationComboForItemBins(
            JComboBox<StorageLocationPick> combo,
            Connection connection,
            String itemCode
    ) throws SQLException {
        combo.removeAllItems();
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection)) {
            return;
        }
        String code = itemCode == null ? "" : itemCode.trim();
        if (code.isEmpty()) {
            return;
        }
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT sl.id, sl.name, isq.qty
                FROM inventory_storage_qty isq
                JOIN storage_locations sl ON sl.id = isq.location_id
                WHERE isq.item_code = ? AND isq.qty > 0
                ORDER BY sl.sort_order ASC, sl.name COLLATE NOCASE ASC
                """)) {
            ps.setString(1, code);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    combo.addItem(new StorageLocationPick(
                            rs.getInt("id"),
                            rs.getString("name"),
                            rs.getInt("qty")));
                }
            }
        }
    }

    /**
     * Moves sellable quantity between {@code inventory_storage_qty} rows without changing {@code Inventory.Stock}.
     */
    static void moveInventoryBetweenStorageLocations(
            Connection connection,
            User user,
            String itemCode,
            int fromLocationId,
            int toLocationId,
            int quantity
    ) throws SQLException {
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection) || quantity <= 0) {
            throw new SQLException("Quantity must be positive.");
        }
        if (fromLocationId == toLocationId) {
            throw new SQLException("Choose a different destination bin.");
        }
        boolean saved = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            int have;
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT qty FROM inventory_storage_qty WHERE item_code = ? AND location_id = ?")) {
                ps.setString(1, itemCode);
                ps.setInt(2, fromLocationId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("No quantity in the source bin for this item.");
                    }
                    have = rs.getInt(1);
                }
            }
            if (have < quantity) {
                throw new SQLException("Source bin only has " + have + " units.");
            }
            int remaining = have - quantity;
            if (remaining == 0) {
                try (PreparedStatement del = connection.prepareStatement(
                        "DELETE FROM inventory_storage_qty WHERE item_code = ? AND location_id = ?")) {
                    del.setString(1, itemCode);
                    del.setInt(2, fromLocationId);
                    del.executeUpdate();
                }
            } else {
                try (PreparedStatement up = connection.prepareStatement(
                        "UPDATE inventory_storage_qty SET qty = ? WHERE item_code = ? AND location_id = ?")) {
                    up.setInt(1, remaining);
                    up.setString(2, itemCode);
                    up.setInt(3, fromLocationId);
                    up.executeUpdate();
                }
            }
            incrementInventoryStorageQty(connection, itemCode, toLocationId, quantity);

            String reason = "BIN_TRANSFER fromLocId=" + fromLocationId + " toLocId=" + toLocationId;
            try (PreparedStatement movement = connection.prepareStatement(
                    "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                movement.setString(1, itemCode);
                movement.setString(2, String.valueOf(quantity));
                movement.setString(3, "TRANSFER");
                movement.setString(4, reason);
                movement.setString(5, user.getUsername());
                movement.setString(6, dateTime.nowDisplayString());
                movement.executeUpdate();
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(saved);
        }
    }

    /**
     * Removes sellable quantity from one bin row during checkout or adjustments (never crosses bins).
     */
    static void deductInventoryStorageQtyAtLocation(Connection connection, String itemCode, int locationId, int qtyToRemove) throws SQLException {
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection) || qtyToRemove <= 0) {
            return;
        }
        long rowid;
        int have;
        try (PreparedStatement sel = connection.prepareStatement("""
                SELECT rowid, qty FROM inventory_storage_qty WHERE item_code = ? AND location_id = ?
                """)) {
            sel.setString(1, itemCode);
            sel.setInt(2, locationId);
            try (ResultSet rs = sel.executeQuery()) {
                if (!rs.next()) {
                    throw new SQLException("No quantity in the selected bin for item " + itemCode + ".");
                }
                rowid = rs.getLong(1);
                have = rs.getInt(2);
            }
        }
        if (have < qtyToRemove) {
            throw new SQLException("Selected bin only has " + have + " units for item " + itemCode + ".");
        }
        int newQty = have - qtyToRemove;
        if (newQty <= 0) {
            try (PreparedStatement del = connection.prepareStatement("DELETE FROM inventory_storage_qty WHERE rowid = ?")) {
                del.setLong(1, rowid);
                del.executeUpdate();
            }
        } else {
            try (PreparedStatement up = connection.prepareStatement("UPDATE inventory_storage_qty SET qty = ? WHERE rowid = ?")) {
                up.setInt(1, newQty);
                up.setLong(2, rowid);
                up.executeUpdate();
            }
        }
    }

    static void deductInventoryStorageQtySpread(Connection connection, String itemCode, int qtyToRemove) throws SQLException {
        if (!DatabaseManager.hasInventoryStorageQtyTable(connection) || qtyToRemove <= 0) {
            return;
        }
        int remaining = qtyToRemove;
        while (remaining > 0) {
            long rowid = -1;
            int have = 0;
            try (PreparedStatement sel = connection.prepareStatement("""
                    SELECT isq.rowid, isq.qty FROM inventory_storage_qty isq
                    INNER JOIN storage_locations sl ON sl.id = isq.location_id
                    WHERE isq.item_code = ?
                    ORDER BY CASE WHEN isq.location_id = ? THEN 0 ELSE 1 END,
                             sl.sort_order ASC,
                             sl.name COLLATE NOCASE ASC
                    LIMIT 1
                    """)) {
                sel.setString(1, itemCode);
                sel.setInt(2, DatabaseManager.STORAGE_LOCATION_UNASSIGNED_ID);
                try (ResultSet rs = sel.executeQuery()) {
                    if (!rs.next()) {
                        throw new SQLException("No warehouse bin rows remain for item " + itemCode
                                + ". Storage totals lost sync with Stock — reconnect after repair or reload data.");
                    }
                    rowid = rs.getLong(1);
                    have = rs.getInt(2);
                }
            }
            int take = Math.min(remaining, have);
            int newQty = have - take;
            if (newQty <= 0) {
                try (PreparedStatement del = connection.prepareStatement("DELETE FROM inventory_storage_qty WHERE rowid = ?")) {
                    del.setLong(1, rowid);
                    del.executeUpdate();
                }
            } else {
                try (PreparedStatement up = connection.prepareStatement("UPDATE inventory_storage_qty SET qty = ? WHERE rowid = ?")) {
                    up.setInt(1, newQty);
                    up.setLong(2, rowid);
                    up.executeUpdate();
                }
            }
            remaining -= take;
        }
    }

    /**
     * Executes integer scalar query helper with string parameters.
     *
     * @param connection active database connection
     * @param query SQL query returning one integer cell
     * @param params string parameters for the query
     * @return integer result or zero when no row exists
     * @throws SQLException when query fails
     */
    static int getIntValue(Connection connection, String query, String... params) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement(query)) {
            for (int i = 0; i < params.length; i++) {
                ps.setString(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1);
                }
                return 0;
            }
        }
    }

    record PendingReceiveLine(String itemCode, String itemName, int amount, double purchasePrice) {
    }

    static List<PendingReceiveLine> loadPendingReceiveLinesForReference(
            Connection connection,
            String reference
    ) throws SQLException {
        List<PendingReceiveLine> lines = new ArrayList<>();
        try (PreparedStatement ps = connection.prepareStatement("""
                SELECT po.`Item Code`,
                       COALESCE(inv.`Item Name`, '') AS iname,
                       po.`Amount`,
                       po.`Purchase Price`
                FROM pendingOrders po
                LEFT JOIN inventory inv ON inv.`Item Code` = po.`Item Code`
                WHERE po.`Reference` = ? AND po.`Amount` > 0
                ORDER BY po.`Item Code` ASC
                """)) {
            ps.setString(1, reference);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(new PendingReceiveLine(
                            rs.getString("Item Code"),
                            rs.getString("iname"),
                            rs.getInt("Amount"),
                            rs.getDouble("Purchase Price")));
                }
            }
        }
        return lines;
    }

    static void receiveEntirePurchaseOrder(
            User user,
            Connection connection,
            String reference,
            int storageLocationId,
            List<PendingReceiveLine> lines
    ) throws SQLException {
        boolean saved = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (PendingReceiveLine line : lines) {
                if (line.amount() > 0) {
                    applyReceive(user, connection, reference, line.itemCode(), line.amount(), line.purchasePrice(),
                            storageLocationId);
                }
            }
            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(saved);
        }
    }


    /**
     * Applies receive updates: increments sellable Stock, records FIFO layer, clears completed PO lines.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param referenceNumber purchase order reference
     * @param codeReceived item code received
     * @param amountReceived quantity received
     * @param purchasePrice received unit cost
     * @param storageLocationId physical bin ({@link DatabaseManager#STORAGE_LOCATION_UNASSIGNED_ID} when not labeled)
     * @throws SQLException when updates fail
     */
    static void applyReceive(
            User user,
            Connection connection,
            String referenceNumber,
            String codeReceived,
            int amountReceived,
            double purchasePrice,
            int storageLocationId
    ) throws SQLException {
        try (PreparedStatement updateReceivingLog = connection.prepareStatement("UPDATE pendingOrders SET Amount = Amount - ? WHERE `Reference` = ? AND `Item Code` = ?")) {
            updateReceivingLog.setInt(1, amountReceived);
            updateReceivingLog.setString(2, referenceNumber);
            updateReceivingLog.setString(3, codeReceived);
            updateReceivingLog.executeUpdate();
        }
        try (PreparedStatement updateOnOrder = connection.prepareStatement("UPDATE Inventory SET `On Order` = `On Order` - ? WHERE `Item Code` = ?")) {
            updateOnOrder.setInt(1, amountReceived);
            updateOnOrder.setString(2, codeReceived);
            updateOnOrder.executeUpdate();
        }
        try (PreparedStatement updateStock = connection.prepareStatement("UPDATE Inventory SET Stock = Stock + ? WHERE `Item Code` = ?")) {
            updateStock.setInt(1, amountReceived);
            updateStock.setString(2, codeReceived);
            updateStock.executeUpdate();
        }
        incrementInventoryStorageQty(connection, codeReceived, storageLocationId, amountReceived);
        try (PreparedStatement addLayer = connection.prepareStatement(
                "INSERT INTO inventory_cost_layers (item_code, reference, unit_cost, qty_received, qty_remaining, created_at) VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            addLayer.setString(1, codeReceived);
            addLayer.setString(2, referenceNumber);
            addLayer.setDouble(3, purchasePrice);
            addLayer.setInt(4, amountReceived);
            addLayer.setInt(5, amountReceived);
            addLayer.setString(6, dateTime.nowDisplayString());
            addLayer.executeUpdate();
        }
        String movementReason = "PURCHASE_ORDER_RECEIPT locId=" + storageLocationId;
        try (PreparedStatement movement = connection.prepareStatement("INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
            movement.setString(1, codeReceived);
            movement.setString(2, String.valueOf(amountReceived));
            movement.setString(3, "RECEIVED");
            movement.setString(4, movementReason);
            movement.setString(5, user.getUsername());
            movement.setString(6, dateTime.nowDisplayString());
            movement.executeUpdate();
        }
        try (PreparedStatement deleteCompletedOrder = connection.prepareStatement("DELETE FROM pendingOrders WHERE `Reference` = ? AND `Item Code` = ? AND Amount = 0")) {
            deleteCompletedOrder.setString(1, referenceNumber);
            deleteCompletedOrder.setString(2, codeReceived);
            deleteCompletedOrder.executeUpdate();
        }
    }

    /** Sizes combo boxes like {@link #styleInput(JTextField...)} for aligned form rows. */
    static void styleComboMatchInputRow(JComboBox<?>... combos) {
        for (JComboBox<?> combo : combos) {
            AppUI.applyComboField(combo);
        }
    }

    /** Applies shared rounded-border styling to input fields. */
    static void styleInput(JTextField... fields) {
        for (JTextField field : fields) {
            AppUI.applyInputField(field);
        }
    }

    /** Compact height for Add Item fields; horizontal size uses preferred width so columns align with defaults. */
    static void styleInputCompact(JTextField... fields) {
        for (JTextField field : fields) {
            AppUI.applyInputField(field);
            Dimension pref = field.getPreferredSize();
            field.setPreferredSize(new Dimension(pref.width, ADD_ITEM_INPUT_HEIGHT));
            field.setMinimumSize(new Dimension(Math.min(pref.width, AppUI.INPUT_MIN_WIDTH), ADD_ITEM_INPUT_HEIGHT));
        }
    }

    /** Read-only value from inventory: muted look so it reads as filled data, not a normal text box. */
    static void styleAutoFilledInventoryField(JTextField field) {
        field.setEditable(false);
        field.setOpaque(true);
        field.setBackground(AppUI.SURFACE_ELEVATED);
        field.setForeground(AppUI.TEXT_MUTED);
    }

    /**
     * Applies consistent border and control height styling for password fields.
     *
     * @param fields password field controls to style
     */
    static void stylePasswordInput(JPasswordField... fields) {
        for (JPasswordField field : fields) {
            AppUI.applyPasswordField(field);
        }
    }

    /** Matches {@link #styleInputCompact(JTextField...)} height for aligned password rows. */
    static void stylePasswordInputCompact(JPasswordField... fields) {
        for (JPasswordField field : fields) {
            AppUI.applyPasswordField(field);
            Dimension pref = field.getPreferredSize();
            field.setPreferredSize(new Dimension(pref.width, ADD_ITEM_INPUT_HEIGHT));
            field.setMinimumSize(new Dimension(Math.min(pref.width, AppUI.INPUT_MIN_WIDTH), ADD_ITEM_INPUT_HEIGHT));
        }
    }

    /**
     * Returns the next available sequential item code in ITM0001-ITM9999 format.
     *
     * @param connection active database connection
     * @return next available item code
     * @throws SQLException when query fails or all item codes are exhausted
     */
    static String getNextItemCode(Connection connection) throws SQLException {
        boolean[] usedCodes = new boolean[10000];
        String query = "SELECT `Item Code` FROM inventory";
        try (PreparedStatement statement = connection.prepareStatement(query);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                String code = resultSet.getString("Item Code");
                if (code == null || code.length() != 7 || !code.startsWith("ITM")) {
                    continue;
                }
                String suffix = code.substring(3);
                if (!suffix.chars().allMatch(Character::isDigit)) {
                    continue;
                }
                int value = Integer.parseInt(suffix);
                if (value >= 1 && value <= 9999) {
                    usedCodes[value] = true;
                }
            }
        }
        for (int i = 1; i <= 9999; i++) {
            if (!usedCodes[i]) {
                return String.format("ITM%04d", i);
            }
        }
        throw new SQLException("No item codes available. ITM0001-ITM9999 are all in use.");
    }

    /** Runs an action with standardized user-facing error handling. */
    static void runAction(CheckedAction action) {
        try {
            action.run();
        } catch (SecurityException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Access Denied", JOptionPane.WARNING_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Unable to complete action: " + e.getMessage(), "Action Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Ensures admin rights before opening restricted workflows. */
    static void ensureAdmin(User user, String actionName) {
        if (user == null || !user.hasAdminRights()) {
            throw new SecurityException("Access denied. Administrator rights are required for: " + actionName);
        }
    }

    /** Closes database connection and suppresses shutdown-time failures. */
    static void closeConnectionQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            if (!connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException ignored) {
            // Ignore close failures when exiting workspace.
        }
    }

    /** Runnable executed on worker threads where checked exceptions propagate to error handling. */
    @FunctionalInterface
    interface CheckedAction {
        void run() throws Exception;
    }

    static final class CommandPaletteEntry {
        private final String label;
        private final String hint;
        private final Runnable onSelect;

        private CommandPaletteEntry(String label, String hint, Runnable onSelect) {
            this.label = label;
            this.hint = hint;
            this.onSelect = onSelect;
        }
    }

    /** Sidebar entry that either opens a lazily-built panel view or invokes a modal action thread. */
    static final class NavItem {
        private final String label;
        private final CheckedAction action;
        private final ViewBuilder viewBuilder;

        /** Sidebar button that launches a background {@link CheckedAction} (logout, seeded runners, etc.). */
        private NavItem(String label, CheckedAction action) {
            this.label = label;
            this.action = action;
            this.viewBuilder = null;
        }

        /** Sidebar button whose click shows a synchronous SQL-backed panel ({@link #build}). */
        private NavItem(String label, ViewBuilder viewBuilder) {
            this.label = label;
            this.action = () -> {};
            this.viewBuilder = viewBuilder;
        }
    }

    /** Builds a workspace center panel lazily inside {@link WorkspaceShell#getItems} navigation clicks. */
    @FunctionalInterface
    private interface ViewBuilder {
        JPanel build() throws SQLException;
    }


}
