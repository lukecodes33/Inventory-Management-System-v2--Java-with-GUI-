import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
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
import javax.swing.table.TableRowSorter;
import javax.swing.event.DocumentListener;
import javax.swing.event.DocumentEvent;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.CategoryAxis;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.CategoryPlot;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.chart.renderer.category.BarRenderer;
import org.jfree.data.category.DefaultCategoryDataset;
import org.jfree.chart.title.TextTitle;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.Window;
import java.awt.Toolkit;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.time.temporal.WeekFields;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;
import java.util.Comparator;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
 * image uses the same maximum width and height (420×420 pixels): admin item rail, read-only item detail, and the
 * Photo and Notes upload preview. Images are never upscaled beyond their file resolution
 * (see {@link #loadScaledItemPhotoIcon(Path, int, int)}).
 * </p>
 * <p>{@link AccountActions} provides backup flows and modal password reset from the sidebar.
 * </p>
 */
public final class WorkspaceShell {
    private static final int INPUT_HEIGHT = 34;
    /** Shorter fields on Add Item batch entry row. */
    private static final int ADD_ITEM_INPUT_HEIGHT = 26;
    private static final int TABLE_ROW_HEIGHT = 28;
    private static final Path COMPANY_NAME_FILE = Paths.get("company.txt");
    /** Optional centered image on the initial workspace card (PNG/JPG/GIF); omitted when missing. */
    private static final Path WORKSPACE_WELCOME_IMAGE = Paths.get("workspace_welcome.png");
    /** On-disk JPEG per item: {@code item_images/<Item Code>.jpeg}. */
    private static final Path ITEM_IMAGES_DIR = Paths.get("item_images");
    /** Max width when downscaling item JPEGs for display (shared by rail, detail, and upload preview). */
    private static final int ITEM_PHOTO_DISPLAY_MAX_W = 420;
    /** Max height when downscaling item JPEGs for display (shared by rail, detail, and upload preview). */
    private static final int ITEM_PHOTO_DISPLAY_MAX_H = 420;
    /** Default frame height scale; width uses the same base percent then {@link #MAIN_FRAME_WIDTH_EXTRA_FACTOR}. */
    private static final int MAIN_FRAME_BASE_W = 1180;
    private static final int MAIN_FRAME_BASE_H = 760;
    private static final int MAIN_FRAME_HEIGHT_SCALE_PERCENT = 125;
    /** Multiplies default width after height-scale (e.g. 1.25 → 25% wider than the height-scaled width). */
    private static final double MAIN_FRAME_WIDTH_EXTRA_FACTOR = 1.25;
    private static final int SIDEBAR_TARGET_WIDTH = 280;
    private static final int WORKSPACE_MIN_WIDTH = 360;
    /** Metrics rail: scroll viewport + divider (admin layout). */
    private static final int ADMIN_METRICS_RAIL_OUTER_PX = 304;
    private static final int MAIN_AREA_MIN_FOR_METRICS = 420;

    /** {@link JPanel#getClientProperty(Object)} key for sidebar nav selection sync. */
    private static final String CLIENT_NAV_SIDEBAR_SELECTOR = "ims.NavSidebarSelector";

    /** Workspace card key and sidebar label for creating POs and viewing pending lines (reference / tracking). */
    private static final String VIEW_PO_TRACKING = "PO/Tracking Number";

    /** Sidebar nav: default (matches {@link AppUI} surface). */
    private static final Color SIDEBAR_NAV_DEFAULT_BG = new Color(255, 255, 255);
    private static final Color SIDEBAR_NAV_DEFAULT_FG = new Color(30, 41, 59);
    /** Sidebar nav: selected tab (darker than default). */
    private static final Color SIDEBAR_NAV_SELECTED_BG = new Color(71, 85, 105);
    private static final Color SIDEBAR_NAV_SELECTED_FG = new Color(248, 250, 252);

    private static final int MAX_SALE_TRANSACTION_NOTE_LENGTH = 2000;

    /**
     * Normalizes optional per-checkout sale note for storage (trim, empty → {@code null}, max length).
     *
     * @param raw user-entered note, may be null
     * @return trimmed text or {@code null} when absent
     */
    private static String normalizedSaleTransactionNote(String raw) {
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

    /** US currency with grouping; negatives as -$1,234.56 (not parentheses). */
    private static String formatUsdMoney(double amount) {
        DecimalFormatSymbols sym = new DecimalFormatSymbols(Locale.US);
        DecimalFormat df = new DecimalFormat("$#,##0.00;-$#,##0.00", sym);
        return df.format(amount);
    }

    /**
     * Parses a currency-style or plain decimal market price; blank input means the caller should leave the DB unchanged.
     */
    private static Double parseOptionalMarketPriceInput(String raw) throws NumberFormatException {
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

    /** Single-line company name from {@code company.txt} (project working directory). */
    private static String readCompanyDisplayName() {
        try {
            if (Files.isReadable(COMPANY_NAME_FILE)) {
                String raw = Files.readString(COMPANY_NAME_FILE, StandardCharsets.UTF_8);
                String s = raw.replace('\r', '\n').split("\n")[0].trim();
                if (!s.isEmpty()) {
                    return s;
                }
            }
        } catch (IOException ignored) {
            // fall through
        }
        return "Made Up Company";
    }

    private static JPanel buildSessionTopBar(User user) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, new Color(0xcbd5e1)),
                BorderFactory.createEmptyBorder(10, 18, 10, 18)));
        AppUI.applyPanelBackground(bar);

        String line = user.getUsername() + " - " + readCompanyDisplayName();
        JLabel brand = new JLabel(line, SwingConstants.CENTER);
        Font base = brand.getFont();
        brand.setFont(base.deriveFont(Font.BOLD, 16f));
        brand.setForeground(new Color(0x1e293b));
        bar.add(brand, BorderLayout.CENTER);

        return bar;
    }

    /** Live session strip; cleared when the workspace frame closes. */
    private static volatile FinancialMetricsStrip activeMetricsStrip;

    /** Admin layout only: right rail toggles between financial metrics and selected item photo. */
    private static volatile AdminMetricsRailHost adminMetricsRailHost;

    private static final class SaleDraftLine {
        int quantity;
        double unitSalePrice;

        SaleDraftLine(int quantity, double unitSalePrice) {
            this.quantity = quantity;
            this.unitSalePrice = unitSalePrice;
        }
    }

    /** One draft row for batch Add Item (item codes assigned on commit). */
    private static final class AddItemDraftLine {
        final String itemName;
        final int stock;
        final int reorder;
        final String supplier;
        final Integer leadTime;
        /** Optional item note (shown on View Items photo rail only). */
        final String notes;
        /** Optional JPEG chosen before commit; copied to {@code item_images/<code>.jpeg} after insert. */
        final Path pendingPhoto;

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

    private static void requestMetricsRefresh() {
        FinancialMetricsStrip strip = activeMetricsStrip;
        if (strip != null) {
            SwingUtilities.invokeLater(strip::refresh);
        }
    }

    /** Re-runs metrics query and label colors on the EDT (not deferred). */
    private static void refreshActiveMetricsStripNow() {
        FinancialMetricsStrip strip = activeMetricsStrip;
        if (strip != null) {
            strip.refresh();
        }
    }

    /** Keeps the sidebar/workspace divider sane when the frame grows or shrinks. */
    private static void syncSidebarWorkspaceSplit(JSplitPane splitPane) {
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
    private static void syncAdminMetricsSplit(JSplitPane triplePane) {
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
    private static final class FinancialMetricsStrip extends JPanel {
        private static final long serialVersionUID = 1L;

        private static final Color CARD_BORDER = new Color(0xe2e8f0);
        private static final Color CARD_HEAD = new Color(0xf8fafc);
        private static final Color ROW_ZEBRA = new Color(0xf8fafc);
        private static final Color MUTED = new Color(0x64748b);
        private static final Color LABEL = new Color(0x475569);

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
                    BorderFactory.createMatteBorder(0, 1, 0, 0, new Color(0xcbd5e1)),
                    BorderFactory.createEmptyBorder(14, 12, 22, 12)));
            AppUI.applyPanelBackground(this);

            Font base = marketStockValueLabel.getFont();

            JLabel title = new JLabel("Financials");
            title.setFont(base.deriveFont(Font.BOLD, 17f));
            title.setForeground(new Color(0x0f172a));
            title.setAlignmentX(Component.LEFT_ALIGNMENT);
            add(title);
            add(Box.createVerticalStrut(14));

            JPanel moversCard = new JPanel(new BorderLayout(0, 0));
            AppUI.applyPanelBackground(moversCard);
            moversCard.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
            moversCard.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel moversHead = new JLabel("Top 10 · this month");
            moversHead.setOpaque(true);
            moversHead.setBackground(CARD_HEAD);
            moversHead.setFont(base.deriveFont(Font.BOLD, 11f));
            moversHead.setForeground(new Color(0x334155));
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
            addMetricLine(totalsBody, base, "Value on order", poExposureLabel, true);
            addMetricLine(totalsBody, base, "Market value · stock", marketStockValueLabel, true);
            addMetricLine(totalsBody, base, "Total P/L", profitLabel, true);
            addMetricLine(totalsBody, base, "Total P/L %", marginLabel, false);
            totalsCard.add(totalsBody, BorderLayout.CENTER);
            add(totalsCard);
            add(Box.createVerticalStrut(20));

            setMinimumSize(new Dimension(220, 80));

            for (JLabel dynamicPl : new JLabel[]{plTodayLabel, plWeekLabel, plMonthLabel, profitLabel, marginLabel}) {
                dynamicPl.putClientProperty("ims.preserveForeground", Boolean.TRUE);
            }
        }

        private static JPanel newCardShell() {
            JPanel p = new JPanel(new BorderLayout(0, 0));
            AppUI.applyPanelBackground(p);
            p.setBorder(BorderFactory.createLineBorder(CARD_BORDER, 1));
            p.setAlignmentX(Component.LEFT_ALIGNMENT);
            return p;
        }

        private static JLabel sectionHeading(Font base, String text) {
            JLabel h = new JLabel(text);
            h.setOpaque(true);
            h.setBackground(CARD_HEAD);
            h.setFont(base.deriveFont(Font.BOLD, 11f));
            h.setForeground(new Color(0x334155));
            h.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 0, 1, 0, CARD_BORDER),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            return h;
        }

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

        /** Same typography and row chrome as {@link #addPeriodRowInto} so values line up. */
        private void addMetricLine(JPanel parent, Font base, String caption, JLabel valueLabel, boolean dividerBelow) {
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

        private JPanel buildMoverRow(int rank, InventoryFifo.TopMoverRow row, Font base, boolean zebra) {
            JPanel line = new JPanel(new BorderLayout(8, 0));
            line.setOpaque(true);
            line.setBackground(zebra ? ROW_ZEBRA : Color.WHITE);
            line.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
            line.setAlignmentX(Component.LEFT_ALIGNMENT);
            JLabel rankLab = new JLabel(String.format(Locale.US, "%2d", rank));
            rankLab.setFont(base.deriveFont(Font.BOLD, 11f));
            rankLab.setForeground(new Color(0x94a3b8));
            rankLab.setHorizontalAlignment(SwingConstants.RIGHT);
            rankLab.setPreferredSize(new Dimension(22, 18));
            JLabel codeLab = new JLabel(row.itemCode);
            codeLab.setFont(base.deriveFont(Font.BOLD, 12f));
            codeLab.setForeground(new Color(0x0f172a));
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

        private static void setPlColor(JLabel label, double pl) {
            label.setForeground(pl >= 0 ? new Color(0x15803d) : new Color(0xb91c1c));
        }

        private void fillMoversError(Font base, String message) {
            topMoversRows.removeAll();
            JLabel err = new JLabel(message);
            err.setFont(base.deriveFont(Font.PLAIN, 11f));
            err.setForeground(new Color(0xb91c1c));
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
                profitLabel.setForeground(plLife >= 0 ? new Color(0x15803d) : new Color(0xb91c1c));

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
                    marginLabel.setForeground(pct >= 0 ? new Color(0x15803d) : new Color(0xb91c1c));
                }

                double poExp = InventoryFifo.openPurchaseOrderExposure(connection);
                poExposureLabel.setText(formatUsdMoney(poExp));
                poExposureLabel.setForeground(new Color(0x1e293b));

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
                profitLabel.setForeground(new Color(0xb91c1c));
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
     * Opens the main desktop shell with sidebar navigation and center workspace.
     *
     * @param user active signed-in user
     * @param connection open database connection for workspace session
     * @param accountActions account-related modal flows (backup, password reset)
     * @param whenWindowClosed optional callback after the frame is disposed and the DB connection is closed
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
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                activeMetricsStrip = null;
                adminMetricsRailHost = null;
                closeConnectionQuietly(connection);
                if (whenWindowClosed != null) {
                    SwingUtilities.invokeLater(whenWindowClosed);
                }
            }
        });

        JPanel workspaceContainer = new JPanel(new CardLayout());
        AppUI.applyPanelBackground(workspaceContainer);
        workspaceContainer.add(buildEmptyWorkspaceCanvas(), "home");

        JPanel sidebar = buildSidebar(user, frame, workspaceContainer, connection, accountActions);

        final JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebar, workspaceContainer);
        splitPane.setDividerLocation(SIDEBAR_TARGET_WIDTH);
        splitPane.setResizeWeight(0);
        splitPane.setContinuousLayout(true);
        splitPane.setBorder(null);

        java.awt.Component center;
        final JSplitPane[] adminMetricsTripleHolder = new JSplitPane[1];
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
            photoRailImage.setForeground(new Color(0x64748b));
            JScrollPane photoRailScroll = new JScrollPane(photoRailImage);
            photoRailScroll.setBorder(AppUI.newRoundedBorder(8));
            photoRailScroll.getVerticalScrollBar().setUnitIncrement(16);

            JTextArea photoRailStats = new JTextArea();
            photoRailStats.setEditable(false);
            photoRailStats.setOpaque(false);
            photoRailStats.setLineWrap(true);
            photoRailStats.setWrapStyleWord(true);
            photoRailStats.setRows(12);
            photoRailStats.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
            photoRailStats.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
            JScrollPane photoRailStatsScroll = new JScrollPane(photoRailStats);
            photoRailStatsScroll.setBorder(AppUI.newRoundedBorder(8));
            photoRailStatsScroll.getVerticalScrollBar().setUnitIncrement(16);

            JPanel photoRailTop = new JPanel(new BorderLayout());
            AppUI.applyPanelBackground(photoRailTop);
            photoRailTop.add(photoRailScroll, BorderLayout.CENTER);
            JSplitPane photoRailSplit = new JSplitPane(JSplitPane.VERTICAL_SPLIT, photoRailTop, photoRailStatsScroll);
            photoRailSplit.setResizeWeight(0.52);
            photoRailSplit.setContinuousLayout(true);
            photoRailSplit.setBorder(null);
            photoRailSplit.setDividerSize(5);
            SwingUtilities.invokeLater(() -> photoRailSplit.setDividerLocation(0.52));

            photoRailCard.add(photoRailTitle, BorderLayout.NORTH);
            photoRailCard.add(photoRailSplit, BorderLayout.CENTER);
            metricsRailHost.add(photoRailCard, "photo");
            adminMetricsRailHost = new AdminMetricsRailHost(connection, metricsRailHost, photoRailTitle, photoRailImage, photoRailStats);

            final JSplitPane triplePane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, splitPane, metricsRailHost);
            triplePane.setResizeWeight(1.0);
            triplePane.setBorder(null);
            triplePane.setContinuousLayout(true);
            center = triplePane;
            adminMetricsTripleHolder[0] = triplePane;
        } else {
            activeMetricsStrip = null;
            adminMetricsRailHost = null;
            center = splitPane;
        }

        JPanel root = new JPanel(new BorderLayout());
        root.add(buildSessionTopBar(user), BorderLayout.NORTH);
        root.add(center, BorderLayout.CENTER);
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

        AppUI.styleWindow(frame);
        refreshActiveMetricsStripNow();
        frame.setVisible(true);
        SwingUtilities.invokeLater(() -> {
            JSplitPane outer = adminMetricsTripleHolder[0];
            if (outer != null) {
                syncAdminMetricsSplit(outer);
            }
            syncSidebarWorkspaceSplit(splitPane);
        });
    }

    /**
     * Builds the left sidebar containing role-aware navigation actions.
     *
     * @return sidebar panel
     */
    private static JPanel buildSidebar(
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
            button.setMaximumSize(new Dimension(Integer.MAX_VALUE, 34));
            button.setHorizontalAlignment(SwingConstants.LEFT);
            button.setBorder(AppUI.newRoundedBorder(8));
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

        container.add(header, BorderLayout.NORTH);
        container.add(scrollPane, BorderLayout.CENTER);
        return container;
    }

    /**
     * Initial workspace card: blank themed canvas so the center feels empty until a function is chosen.
     * If {@link #WORKSPACE_WELCOME_IMAGE} exists and loads, it is shown centered (e.g. logo); otherwise the panel stays empty.
     */
    private static JPanel buildEmptyWorkspaceCanvas() {
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
    private static List<NavItem> getItems(
            User user,
            Connection connection,
            AccountActions accountActions,
            JFrame frame,
            JPanel workspaceContainer
    ) {
        List<NavItem> items = new ArrayList<>();
        boolean admin = user.hasAdminRights();

        items.add(new NavItem("View Items", () -> buildInventoryTablePanel(user, connection)));
        if (admin) {
            items.add(new NavItem("Add Item", () -> buildAddItemPanel(user, connection, workspaceContainer)));
        }
        if (admin) {
            items.add(new NavItem("Market Prices", () -> buildMarketPricesBulkPanel(user, connection, workspaceContainer)));
        }
        items.add(new NavItem(VIEW_PO_TRACKING, () -> buildPurchaseOrdersPanel(user, connection, workspaceContainer)));
        items.add(new NavItem("Receive Order", () -> buildReceiveOrderPanel(user, connection, workspaceContainer)));
        items.add(new NavItem("Low Stock Check", () -> buildLowStockPanel(connection)));
        if (admin) {
            items.add(new NavItem("Change Reorder Triggers", () -> buildAdjustReorderPanel(user, connection)));
        }
        items.add(new NavItem("Process Sale", () -> buildProcessSalePanel(user, connection, workspaceContainer)));
        items.add(new NavItem("Return Item", () -> buildReturnPanel(user, connection, workspaceContainer)));
        items.add(new NavItem("View Sales Transaction", () -> buildSalesPanel(connection)));
        if (admin) {
            items.add(new NavItem("Write Off Stock", () -> buildWriteOffPanel(user, connection)));
            items.add(new NavItem("Generate Reports", () -> buildReportsPanel(user, connection)));
            items.add(new NavItem("Photo and Notes uploads", () -> buildPhotoNotesUploadPanel(user, connection)));
            items.add(new NavItem("User Management", () -> buildUserManagementPanel(user, connection)));
            items.add(new NavItem("Create Local Backup", () -> buildBackupPanel(user, connection, accountActions, frame)));
        }
        items.add(new NavItem("Reset Password", () -> buildResetPasswordPanel(user, frame, accountActions)));
        items.add(new NavItem("Log Out", frame::dispose));
        return items;
    }

    /**
     * Displays a panel in the workspace card container under a stable key.
     *
     * @param workspaceContainer card layout host panel
     * @param key unique card key
     * @param panel panel to display
     */
    private static void showView(JPanel workspaceContainer, String key, JPanel panel) {
        AppUI.applyPanelBackground(panel);
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

    private static void restoreMetricsRailToDefault() {
        AdminMetricsRailHost host = adminMetricsRailHost;
        if (host != null) {
            host.showMetrics();
        }
    }

    private static void applyNavButtonDefaultStyle(JButton button) {
        button.setBackground(SIDEBAR_NAV_DEFAULT_BG);
        button.setForeground(SIDEBAR_NAV_DEFAULT_FG);
        button.setBorder(AppUI.newRoundedBorder(8));
    }

    private static void applyNavButtonSelectedStyle(JButton button) {
        button.setBackground(SIDEBAR_NAV_SELECTED_BG);
        button.setForeground(SIDEBAR_NAV_SELECTED_FG);
        button.setBorder(AppUI.newRoundedBorder(8));
    }

    /**
     * Tracks which workspace card key is active and paints the matching sidebar button darker.
     */
    private static final class NavSidebarSelector {
        private final Map<String, JButton> keyToButton = new HashMap<>();
        private String selectedKey;

        private void registerNavViewButton(String cardKey, JButton button) {
            keyToButton.put(cardKey, button);
            applyNavButtonDefaultStyle(button);
        }

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
    }

    /**
     * Swaps the admin right rail between the financial metrics card and the per-item photo card (JPEG + stats text).
     * The photo uses the same scaling bounds as the rest of the workspace
     * ({@link WorkspaceShell#ITEM_PHOTO_DISPLAY_MAX_W}×{@link WorkspaceShell#ITEM_PHOTO_DISPLAY_MAX_H}).
     */
    private static final class AdminMetricsRailHost {
        private final Connection connection;
        private final JPanel host;
        private final JLabel titleLabel;
        private final JLabel imageLabel;
        private final JTextArea statsArea;

        private AdminMetricsRailHost(
                Connection connection,
                JPanel host,
                JLabel titleLabel,
                JLabel imageLabel,
                JTextArea statsArea
        ) {
            this.connection = connection;
            this.host = host;
            this.titleLabel = titleLabel;
            this.imageLabel = imageLabel;
            this.statsArea = statsArea;
        }

        private void showMetrics() {
            CardLayout cl = (CardLayout) host.getLayout();
            cl.show(host, "metrics");
            host.revalidate();
            host.repaint();
        }

        private void showItemPhoto(String itemCode) {
            titleLabel.setText(itemCode);
            try {
                statsArea.setText(buildItemRailStatsText(connection, itemCode));
            } catch (SQLException ex) {
                statsArea.setText("Could not load stats:\n" + ex.getMessage());
            }
            statsArea.setCaretPosition(0);
            Path p = itemImagePath(itemCode);
            ImageIcon icon = loadScaledItemPhotoIcon(p, ITEM_PHOTO_DISPLAY_MAX_W, ITEM_PHOTO_DISPLAY_MAX_H);
            if (icon != null) {
                imageLabel.setIcon(icon);
                imageLabel.setText(null);
                imageLabel.setForeground(new Color(0x1e293b));
            } else {
                imageLabel.setIcon(null);
                imageLabel.setForeground(new Color(0x64748b));
                imageLabel.setText("<html><center>No JPEG on file for this item.<br>"
                        + "<span style='font-size:11px'>item_images/" + itemCode + ".jpeg</span></center></html>");
            }
            CardLayout cl = (CardLayout) host.getLayout();
            cl.show(host, "photo");
            host.revalidate();
            host.repaint();
        }
    }

    private static final int ITEM_NOTES_MAX_CHARS = 4000;

    /** Summary text for the View Items photo rail (inventory + lifetime sales aggregates + notes). */
    private static String buildItemRailStatsText(Connection connection, String itemCode) throws SQLException {
        int stock = 0;
        String notes = "";
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `Stock`, COALESCE(`Notes`, '') AS n FROM inventory WHERE `Item Code` = ?"
        )) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    stock = rs.getInt("Stock");
                    notes = Objects.toString(rs.getString("n"), "");
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
        sb.append("Total P/L:\n").append(formatUsdMoney(sumRev - sumCost)).append("\n\n");
        sb.append("Notes:\n");
        if (notes.isEmpty()) {
            sb.append("—");
        } else {
            sb.append(notes);
        }
        return sb.toString();
    }

    /**
     * Builds a generic launcher panel for simple one-click actions.
     *
     * @param title panel title
     * @param description supporting text
     * @param action action to run
     * @return launcher panel
     */
    private static JPanel buildLauncherPanel(String title, String description, CheckedAction action) {
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

    private static void ensureItemImagesDir() throws IOException {
        Files.createDirectories(ITEM_IMAGES_DIR);
    }

    private static Path itemImagePath(String itemCode) {
        return ITEM_IMAGES_DIR.resolve(itemCode + ".jpeg");
    }

    private static boolean isJpegFileName(String name) {
        if (name == null) {
            return false;
        }
        String n = name.toLowerCase(Locale.ROOT);
        return n.endsWith(".jpeg") || n.endsWith(".jpg");
    }

    private static void copySourceJpegToItemPhoto(Path source, String itemCode) throws IOException {
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
    }

    /**
     * Loads an item JPEG and returns an icon scaled down to fit a square box, preserving aspect ratio.
     *
     * @return icon, or {@code null} if the file is missing or not a readable image
     */
    private static ImageIcon loadScaledItemPhotoIcon(Path path, int maxSide) {
        return loadScaledItemPhotoIcon(path, maxSide, maxSide);
    }

    /**
     * Loads an item JPEG and returns an icon whose size fits inside {@code maxWidth}×{@code maxHeight} without
     * upscaling (aspect ratio preserved). Missing or invalid files yield {@code null}.
     */
    private static ImageIcon loadScaledItemPhotoIcon(Path path, int maxWidth, int maxHeight) {
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

    private static void addDetailFieldRow(JPanel grid, String label, String value) {
        JLabel l = new JLabel(label);
        l.setFont(l.getFont().deriveFont(Font.BOLD));
        JLabel v = new JLabel(value == null ? "" : value);
        grid.add(l);
        grid.add(v);
    }

    /** Modal item summary with optional JPEG; admins can replace the photo. */
    private static void showItemDetailDialog(Component parent, User user, Connection connection, String itemCode) {
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
        photoLabel.setForeground(new Color(0x64748b));

        JPanel fields = new JPanel(new GridLayout(0, 2, 10, 8));
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
        dialog.setContentPane(root);
        dialog.pack();
        dialog.setLocationRelativeTo(parent);
        dialog.setVisible(true);
    }

    /**
     * Builds the inventory table view panel with column filters.
     * Administrators: selecting a row temporarily replaces the right-hand financial rail with that item's JPEG (or a placeholder).
     * Double-click a row for the full item detail dialog (including admin photo change).
     *
     * @param user         signed-in user (for admin-only photo change in detail dialog)
     * @param connection   active database connection
     * @return inventory table panel
     * @throws SQLException when query fails
     */
    private static JPanel buildInventoryTablePanel(User user, Connection connection) throws SQLException {
        BiConsumer<JTable, Integer> onDouble = (table, modelRow) -> {
            Object codeObj = table.getModel().getValueAt(modelRow, 0);
            String code = codeObj == null ? "" : codeObj.toString().trim();
            if (!code.isEmpty()) {
                showItemDetailDialog(table, user, connection, code);
            }
        };
        return buildFilterableTablePanel(
                "Inventory Items",
                new String[]{"Item Code", "Item Name", "Stock", "On Order", "Supplier", "Lead Time", "Market Price"},
                "SELECT `Item Code`, `Item Name`, `Stock`, `On Order`, COALESCE(`Supplier`, 'N/A') AS `Supplier`, "
                        + "COALESCE(CAST(`Lead Time` AS TEXT), 'N/A') AS `Lead Time`, "
                        + "CASE WHEN `Market Price` IS NULL THEN '' ELSE printf('%.2f', `Market Price`) END AS `Market Price` "
                        + "FROM inventory",
                connection,
                onDouble,
                WorkspaceShell::registerInventoryPhotoRailListener
        );
    }

    /** Builds the pending orders table view panel. */
    private static JPanel buildPendingOrdersPanel(Connection connection) throws SQLException {
        return buildFilterableTablePanel(
                "Pending Orders",
                new String[]{"Item Code", "Amount", "Purchase Price", "Reference", "Date"},
                "SELECT `Item Code`, `Amount`, `Purchase Price`, `Reference`, `Date` FROM pendingOrders",
                connection
        );
    }

    /**
     * Builds low-stock and replenishment recommendation view.
     *
     * @param connection active database connection
     * @return replenishment panel
     * @throws SQLException when query fails
     */
    private static JPanel buildLowStockPanel(Connection connection) throws SQLException {
        return buildFilterableTablePanel(
                "Low Stock and Replenishment",
                new String[]{
                        "Item Code",
                        "Item Name",
                        "Supplier",
                        "Lead Time",
                        "Stock",
                        "On Order",
                        "Recent Sales (14d)",
                        "Suggested Reorder Qty"
                },
                """
                SELECT
                    i.`Item Code` AS `Item Code`,
                    i.`Item Name` AS `Item Name`,
                    COALESCE(i.`Supplier`, 'N/A') AS `Supplier`,
                    CASE
                        WHEN i.`Lead Time` IS NULL THEN 'N/A'
                        ELSE CAST(i.`Lead Time` AS TEXT)
                    END AS `Lead Time`,
                    i.`Stock` AS `Stock`,
                    i.`On Order` AS `On Order`,
                    COALESCE(s.recent_sales, 0) AS `Recent Sales (14d)`,
                    (
                        CASE
                            WHEN (i.`ReOrder Trigger` - (i.`Stock` + i.`On Order`)) > 0
                            THEN (i.`ReOrder Trigger` - (i.`Stock` + i.`On Order`))
                            ELSE 0
                        END
                    ) +
                    (
                        CASE
                            WHEN COALESCE(i.`Lead Time`, 0) > 0
                            THEN CAST(((COALESCE(s.recent_sales, 0) / 14.0) * i.`Lead Time`) + 0.9999 AS INTEGER)
                            ELSE 0
                        END
                    ) AS `Suggested Reorder Qty`
                FROM Inventory i
                LEFT JOIN (
                    SELECT `Item Code`, SUM(`Amount`) AS recent_sales
                    FROM sales
                    WHERE `DateISO` >= datetime('now', '-14 days')
                    GROUP BY `Item Code`
                ) s ON s.`Item Code` = i.`Item Code`
                WHERE i.`ReOrder Trigger` >= i.`Stock`
                ORDER BY `Suggested Reorder Qty` DESC, i.`Item Code` ASC
                """,
                connection
        );
    }

    /** Builds sales transaction history table panel. */
    private static JPanel buildSalesPanel(Connection connection) throws SQLException {
        return buildFilterableTablePanel(
                "Sales Transactions",
                new String[]{"Item Code", "Item Name", "Amount", "Total Price", "Reference", "User", "Date", "Note"},
                "SELECT sales.`Item Code`, COALESCE(inventory.`Item Name`, 'N/A') AS `Item Name`, sales.`Amount`, sales.`Total Price`, sales.`Reference`, sales.`User`, sales.`Date`, COALESCE(sales.`Note`, '') AS `Note` FROM sales LEFT JOIN inventory ON sales.`Item Code` = inventory.`Item Code`",
                connection
        );
    }

    /** Builds a reusable filterable table panel from SQL query results. */
    private static JPanel buildFilterableTablePanel(String title, String[] columns, String query, Connection connection) throws SQLException {
        return buildFilterableTablePanel(title, columns, query, connection, null, null);
    }

    /**
     * Builds a reusable filterable table panel from SQL query results.
     *
     * @param onRowDoubleClickModelIndex when non-null, invoked on double-click with the table and model row index
     * @param onTableBuilt               when non-null, invoked with the table after listeners are attached (e.g. View Items photo rail)
     */
    private static JPanel buildFilterableTablePanel(
            String title,
            String[] columns,
            String query,
            Connection connection,
            BiConsumer<JTable, Integer> onRowDoubleClickModelIndex,
            Consumer<JTable> onTableBuilt
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
        for (int i = 0; i < columns.length; i++) {
            JTextField filter = new JTextField();
            filter.setBorder(AppUI.newRoundedBorder(8));
            final int index = i;
            filter.addCaretListener(e -> {
                String text = filter.getText();
                if (text == null || text.trim().isEmpty()) {
                    sorter.setRowFilter(null);
                } else {
                    sorter.setRowFilter(RowFilter.regexFilter("(?i)" + text, index));
                }
            });
            filterPanel.add(filter);
        }

        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(AppUI.newRoundedBorder(8));
        panel.add(tableScroll, BorderLayout.CENTER);
        panel.add(filterPanel, BorderLayout.SOUTH);
        if (onTableBuilt != null) {
            onTableBuilt.accept(table);
        }
        return panel;
    }

    /**
     * When an admin selects a row in View Items, the right rail shows that item's JPEG (or a blank message).
     * Clears back to financial metrics when no row is selected or when leaving View Items.
     */
    private static void registerInventoryPhotoRailListener(JTable table) {
        table.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            AdminMetricsRailHost rail = adminMetricsRailHost;
            if (rail == null) {
                return;
            }
            int viewRow = table.getSelectedRow();
            if (viewRow < 0) {
                rail.showMetrics();
                return;
            }
            int modelRow;
            try {
                modelRow = table.convertRowIndexToModel(viewRow);
            } catch (Exception ex) {
                rail.showMetrics();
                return;
            }
            Object codeObj = table.getModel().getValueAt(modelRow, 0);
            String code = codeObj == null ? "" : codeObj.toString().trim();
            if (code.isEmpty()) {
                rail.showMetrics();
            } else {
                rail.showItemPhoto(code);
            }
        });
    }

    /** Builds admin-only add-item workflow panel with compact fields and batch draft lines (like Process Sale). */
    private static JPanel buildAddItemPanel(User user, Connection connection, JPanel workspaceContainer) {
        ensureAdmin(user, "Add Item");
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 20, 20, 20));
        AppUI.applyPanelBackground(panel);

        JPanel form = new JPanel(new GridLayout(0, 2, 8, 8));
        AppUI.applyPanelBackground(form);
        JTextField nextItemCodeField = new JTextField();
        nextItemCodeField.setEditable(false);
        nextItemCodeField.setFocusable(false);
        nextItemCodeField.setToolTipText(
                "Next available ITM code. It is assigned to the first new row when you click Add all; each extra row uses the following codes in order."
        );
        try {
            nextItemCodeField.setText(getNextItemCode(connection));
        } catch (SQLException ex) {
            nextItemCodeField.setText("UNAVAILABLE");
        }
        styleInputCompact(nextItemCodeField);
        form.add(new JLabel("Item code (next)"));
        form.add(nextItemCodeField);

        JTextField itemName = new JTextField();
        JTextField stock = new JTextField();
        JTextField reorder = new JTextField();
        JTextField supplier = new JTextField();
        JTextField leadTime = new JTextField();
        styleInputCompact(itemName, stock, reorder, supplier, leadTime);
        form.add(new JLabel("Item name *"));
        form.add(itemName);
        form.add(new JLabel("Stock count *"));
        form.add(stock);
        form.add(new JLabel("Reorder trigger *"));
        form.add(reorder);
        form.add(new JLabel("Supplier (optional)"));
        form.add(supplier);
        form.add(new JLabel("Lead time (days, optional)"));
        form.add(leadTime);

        JTextArea addItemNotesArea = new JTextArea(3, 24);
        addItemNotesArea.setLineWrap(true);
        addItemNotesArea.setWrapStyleWord(true);
        addItemNotesArea.setToolTipText("Optional. Shown only on the View Items photo pane for this SKU (max " + ITEM_NOTES_MAX_CHARS + " characters).");
        JScrollPane addItemNotesScroll = new JScrollPane(addItemNotesArea);
        addItemNotesScroll.setBorder(AppUI.newRoundedBorder(8));
        form.add(new JLabel("Notes (optional)"));
        form.add(addItemNotesScroll);

        JLabel photoPickLabel = new JLabel("None");
        final Path[] pendingPhotoPick = new Path[1];
        JButton choosePhoto = new JButton("Choose JPEG…");
        JButton clearPhotoPick = new JButton("Clear photo");
        styleSecondaryButton(choosePhoto);
        styleSecondaryButton(clearPhotoPick);
        clearPhotoPick.setEnabled(false);
        choosePhoto.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new FileNameExtensionFilter("JPEG images (.jpg, .jpeg)", "jpg", "jpeg"));
            if (fc.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                pendingPhotoPick[0] = fc.getSelectedFile().toPath();
                photoPickLabel.setText(fc.getSelectedFile().getName());
                clearPhotoPick.setEnabled(true);
            }
        });
        clearPhotoPick.addActionListener(e -> {
            pendingPhotoPick[0] = null;
            photoPickLabel.setText("None");
            clearPhotoPick.setEnabled(false);
        });
        form.add(new JLabel("Photo (optional)"));
        JPanel photoRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(photoRow);
        photoRow.add(choosePhoto);
        photoRow.add(clearPhotoPick);
        photoRow.add(photoPickLabel);
        form.add(photoRow);

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
        draftTable.setRowHeight(ADD_ITEM_INPUT_HEIGHT + 6);
        installTableCopyMenu(draftTable);

        JButton addLine = new JButton("Add line");
        JButton removeLine = new JButton("Remove line");
        JButton clearDraft = new JButton("Clear draft");
        styleSecondaryButton(addLine);
        styleSecondaryButton(removeLine);
        styleSecondaryButton(clearDraft);

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
                String notesRaw = addItemNotesArea.getText();
                if (notesRaw != null && notesRaw.length() > ITEM_NOTES_MAX_CHARS) {
                    JOptionPane.showMessageDialog(panel, "Notes must be at most " + ITEM_NOTES_MAX_CHARS + " characters.");
                    return;
                }
                draftLines.add(new AddItemDraftLine(
                        itemNameValue, stockCount, reorderTrigger, supplierValue, leadTimeDays, notesRaw == null ? "" : notesRaw, pendingPhotoPick[0]));
                refreshAddItemDraftTable(draftModel, draftLines);
                itemName.setText("");
                stock.setText("");
                reorder.setText("");
                supplier.setText("");
                leadTime.setText("");
                addItemNotesArea.setText("");
                pendingPhotoPick[0] = null;
                photoPickLabel.setText("None");
                clearPhotoPick.setEnabled(false);
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
                refreshAddItemDraftTable(draftModel, draftLines);
            }
        });

        clearDraft.addActionListener(e -> {
            if (draftLines.isEmpty()) {
                return;
            }
            int ok = JOptionPane.showConfirmDialog(panel, "Clear all draft lines?", "Clear draft", JOptionPane.OK_CANCEL_OPTION);
            if (ok == JOptionPane.OK_OPTION) {
                draftLines.clear();
                refreshAddItemDraftTable(draftModel, draftLines);
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
                    String itemCodeValue = getNextItemCode(connection);
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
                            copySourceJpegToItemPhoto(line.pendingPhoto, itemCodeValue);
                        } catch (IOException ioe) {
                            throw new SQLException("Photo save failed for " + itemCodeValue + ": " + ioe.getMessage(), ioe);
                        }
                    }
                    added++;
                }
                connection.commit();
                draftLines.clear();
                refreshAddItemDraftTable(draftModel, draftLines);
                JOptionPane.showMessageDialog(
                        panel,
                        "Added " + added + " item(s). Sale price is entered at checkout. Use purchase orders for landed cost."
                );
                showView(workspaceContainer, "View Items", buildInventoryTablePanel(user, connection));
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

        JPanel northStack = new JPanel(new BorderLayout(0, 6));
        AppUI.applyPanelBackground(northStack);
        northStack.add(form, BorderLayout.NORTH);
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

    /**
     * Admin-only: load an item by ITM code, replace optional JPEG on disk, and insert or update {@code Inventory.Notes}.
     */
    private static JPanel buildPhotoNotesUploadPanel(User user, Connection connection) {
        ensureAdmin(user, "Photo and Notes uploads");
        JPanel panel = buildFormPanel("Photo and Notes uploads");

        JPanel content = buildSectionPanel();
        content.add(buildSectionText(
                "Enter an ITM code, then Load item to pull the current note and preview the on-disk JPEG. Choose a new JPEG to replace "
                        + "item_images/<ItemCode>.jpeg on save when a file is selected. Save always updates the note (leave empty to clear it)."
        ));
        content.add(Box.createVerticalStrut(12));

        JPanel codeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(codeRow);
        JTextField itemCodeField = new JTextField(14);
        itemCodeField.setToolTipText("Example: ITM0001");
        styleInputCompact(itemCodeField);
        JButton loadItemBtn = new JButton("Load item");
        styleSecondaryButton(loadItemBtn);
        JLabel loadStatus = new JLabel(" ");
        loadStatus.setFont(loadStatus.getFont().deriveFont(Font.PLAIN, 12f));
        codeRow.add(new JLabel("Item code *"));
        codeRow.add(itemCodeField);
        codeRow.add(loadItemBtn);
        content.add(codeRow);
        content.add(loadStatus);
        content.add(Box.createVerticalStrut(10));

        final Path[] pendingPhotoPath = new Path[1];
        JLabel photoPickLabel = new JLabel("None");
        JButton choosePhotoBtn = new JButton("Choose JPEG…");
        JButton clearPhotoBtn = new JButton("Clear new photo");
        styleSecondaryButton(choosePhotoBtn);
        styleSecondaryButton(clearPhotoBtn);
        clearPhotoBtn.setEnabled(false);
        choosePhotoBtn.addActionListener(e -> {
            JFileChooser fc = new JFileChooser();
            fc.setFileFilter(new javax.swing.filechooser.FileNameExtensionFilter("JPEG images (.jpg, .jpeg)", "jpg", "jpeg"));
            if (fc.showOpenDialog(panel) == JFileChooser.APPROVE_OPTION) {
                pendingPhotoPath[0] = fc.getSelectedFile().toPath();
                photoPickLabel.setText(fc.getSelectedFile().getName());
                clearPhotoBtn.setEnabled(true);
            }
        });
        clearPhotoBtn.addActionListener(e -> {
            pendingPhotoPath[0] = null;
            photoPickLabel.setText("None");
            clearPhotoBtn.setEnabled(false);
        });
        JPanel photoPickRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(photoPickRow);
        photoPickRow.add(choosePhotoBtn);
        photoPickRow.add(clearPhotoBtn);
        photoPickRow.add(photoPickLabel);
        content.add(photoPickRow);
        content.add(Box.createVerticalStrut(8));

        JLabel previewHeading = buildSectionTitle("Current photo preview");
        previewHeading.setFont(previewHeading.getFont().deriveFont(Font.BOLD, 14f));
        content.add(previewHeading);
        JLabel previewLabel = new JLabel("Load an item to preview.", SwingConstants.CENTER);
        previewLabel.setVerticalAlignment(SwingConstants.CENTER);
        previewLabel.setForeground(new Color(0x64748b));
        previewLabel.setPreferredSize(new Dimension(ITEM_PHOTO_DISPLAY_MAX_W, ITEM_PHOTO_DISPLAY_MAX_H));
        previewLabel.setMinimumSize(new Dimension(ITEM_PHOTO_DISPLAY_MAX_W, ITEM_PHOTO_DISPLAY_MAX_H));
        JPanel previewWrap = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(previewWrap);
        previewWrap.setBorder(AppUI.newRoundedBorder(8));
        previewWrap.add(previewLabel, BorderLayout.CENTER);
        content.add(previewWrap);
        content.add(Box.createVerticalStrut(12));

        JLabel notesHeading = buildSectionTitle("Notes");
        notesHeading.setFont(notesHeading.getFont().deriveFont(Font.BOLD, 14f));
        content.add(notesHeading);
        JTextArea notesArea = new JTextArea(10, 36);
        notesArea.setLineWrap(true);
        notesArea.setWrapStyleWord(true);
        notesArea.setToolTipText("Stored on the inventory row (max " + ITEM_NOTES_MAX_CHARS + " characters). Shown on the View Items photo rail.");
        JScrollPane notesScroll = new JScrollPane(notesArea);
        notesScroll.setBorder(AppUI.newRoundedBorder(8));
        content.add(notesScroll);

        JButton saveBtn = new JButton("Save changes");
        AppUI.stylePrimaryButton(saveBtn);
        saveBtn.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(Box.createVerticalStrut(12));
        content.add(saveBtn);

        loadItemBtn.addActionListener(e -> {
            String code = itemCodeField.getText().trim().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Enter an item code.");
                return;
            }
            try {
                if (!inventoryItemExists(connection, code)) {
                    JOptionPane.showMessageDialog(panel, "No inventory row for: " + code);
                    loadStatus.setText(" ");
                    return;
                }
                String itemName;
                String existingNotes;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT `Item Name`, COALESCE(`Notes`, '') AS n FROM inventory WHERE `Item Code` = ?"
                )) {
                    ps.setString(1, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            JOptionPane.showMessageDialog(panel, "Item disappeared: " + code);
                            return;
                        }
                        itemName = Objects.toString(rs.getString("Item Name"), "");
                        existingNotes = Objects.toString(rs.getString("n"), "");
                    }
                }
                notesArea.setText(existingNotes);
                notesArea.setCaretPosition(0);
                pendingPhotoPath[0] = null;
                photoPickLabel.setText("None");
                clearPhotoBtn.setEnabled(false);
                Path imgPath = itemImagePath(code);
                ImageIcon icon = loadScaledItemPhotoIcon(imgPath, ITEM_PHOTO_DISPLAY_MAX_W, ITEM_PHOTO_DISPLAY_MAX_H);
                if (icon != null) {
                    previewLabel.setIcon(icon);
                    previewLabel.setText(null);
                    previewLabel.setForeground(new Color(0x1e293b));
                } else {
                    previewLabel.setIcon(null);
                    previewLabel.setForeground(new Color(0x64748b));
                    previewLabel.setText("<html><center>No JPEG on file.<br><span style='font-size:11px'>"
                            + "item_images/" + code + ".jpeg</span></center></html>");
                }
                loadStatus.setText("Loaded: " + (itemName.isEmpty() ? code : itemName + " (" + code + ")"));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        saveBtn.addActionListener(e -> {
            String code = itemCodeField.getText().trim().toUpperCase(Locale.ROOT);
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Enter an item code.");
                return;
            }
            String rawNotes = notesArea.getText();
            if (rawNotes != null && rawNotes.length() > ITEM_NOTES_MAX_CHARS) {
                JOptionPane.showMessageDialog(panel, "Notes must be at most " + ITEM_NOTES_MAX_CHARS + " characters.");
                return;
            }
            String notesTrimmed = rawNotes == null ? "" : rawNotes.trim();
            try {
                if (!inventoryItemExists(connection, code)) {
                    JOptionPane.showMessageDialog(panel, "No inventory row for: " + code);
                    return;
                }
                boolean replacePhoto = pendingPhotoPath[0] != null;
                try (PreparedStatement ps = connection.prepareStatement("UPDATE inventory SET `Notes` = ? WHERE `Item Code` = ?")) {
                    if (notesTrimmed.isEmpty()) {
                        ps.setNull(1, java.sql.Types.VARCHAR);
                    } else {
                        ps.setString(1, notesTrimmed);
                    }
                    ps.setString(2, code);
                    ps.executeUpdate();
                }
                if (replacePhoto) {
                    copySourceJpegToItemPhoto(pendingPhotoPath[0], code);
                }
                pendingPhotoPath[0] = null;
                photoPickLabel.setText("None");
                clearPhotoBtn.setEnabled(false);
                ImageIcon refreshed = loadScaledItemPhotoIcon(itemImagePath(code), ITEM_PHOTO_DISPLAY_MAX_W, ITEM_PHOTO_DISPLAY_MAX_H);
                if (refreshed != null) {
                    previewLabel.setIcon(refreshed);
                    previewLabel.setText(null);
                    previewLabel.setForeground(new Color(0x1e293b));
                } else {
                    previewLabel.setIcon(null);
                    previewLabel.setForeground(new Color(0x64748b));
                    previewLabel.setText("<html><center>No JPEG on file.<br><span style='font-size:11px'>"
                            + "item_images/" + code + ".jpeg</span></center></html>");
                }
                JOptionPane.showMessageDialog(
                        panel,
                        replacePhoto ? "Saved notes and replaced the photo on disk." : "Saved notes."
                );
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(panel, "Photo save failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        content.setAlignmentX(Component.LEFT_ALIGNMENT);
        panel.add(content, BorderLayout.CENTER);
        return panel;
    }

    /**
     * @param connection JDBC connection
     * @param itemCode   exact {@code Inventory.Item Code} value (already normalized by caller if needed)
     * @return {@code true} when a row exists
     */
    private static boolean inventoryItemExists(Connection connection, String itemCode) throws SQLException {
        try (PreparedStatement ps = connection.prepareStatement("SELECT 1 FROM inventory WHERE `Item Code` = ? LIMIT 1")) {
            ps.setString(1, itemCode);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    /**
     * Builds a combined admin user-management panel with add/delete workflows.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @return user management panel
     */
    private static JPanel buildUserManagementPanel(User user, Connection connection) {
        ensureAdmin(user, "User Management");
        JPanel panel = buildFormPanel("User Management");

        JPanel addSection = buildSectionPanel();
        addSection.add(buildSectionTitle("Add User"));
        addSection.add(Box.createVerticalStrut(8));
        JPanel addForm = new JPanel(new GridLayout(0, 2, 10, 10));
        AppUI.applyPanelBackground(addForm);
        JTextField addUsername = new JTextField();
        JPasswordField addPassword = new JPasswordField();
        JPasswordField addConfirm = new JPasswordField();
        JCheckBox addAdminRights = new JCheckBox("Grant administrator rights");
        AppUI.applyPanelBackground(addAdminRights);
        styleInput(addUsername);
        stylePasswordInput(addPassword, addConfirm);
        addForm.add(new JLabel("Username *"));
        addForm.add(addUsername);
        addForm.add(new JLabel("Password *"));
        addForm.add(addPassword);
        addForm.add(new JLabel("Confirm Password *"));
        addForm.add(addConfirm);
        addForm.add(new JLabel("Access"));
        addForm.add(addAdminRights);
        addSection.add(addForm);
        addSection.add(Box.createVerticalStrut(10));
        JButton createUser = new JButton("Create User");
        AppUI.stylePrimaryButton(createUser);
        createUser.setAlignmentX(Component.LEFT_ALIGNMENT);
        createUser.addActionListener(e -> {
            String enteredUsername = addUsername.getText().trim();
            char[] enteredPassword = addPassword.getPassword();
            char[] enteredConfirm = addConfirm.getPassword();
            try {
                if (enteredUsername.isEmpty()) {
                    JOptionPane.showMessageDialog(panel, "Username is required.");
                    return;
                }
                if (!enteredUsername.matches("[A-Za-z0-9._-]{3,32}")) {
                    JOptionPane.showMessageDialog(panel, "Username must be 3-32 chars and only use letters, numbers, ., _, or -.");
                    return;
                }
                if (!Arrays.equals(enteredPassword, enteredConfirm)) {
                    JOptionPane.showMessageDialog(panel, "Password and confirm password do not match.");
                    return;
                }
                String policyError = AccountActions.validatePasswordPolicy(enteredPassword);
                if (policyError != null) {
                    JOptionPane.showMessageDialog(panel, policyError);
                    return;
                }
                try (PreparedStatement check = connection.prepareStatement("SELECT COUNT(*) AS count FROM users WHERE username = ?")) {
                    check.setString(1, enteredUsername);
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next() && rs.getInt("count") > 0) {
                            JOptionPane.showMessageDialog(panel, "That username already exists.");
                            return;
                        }
                    }
                }
                String hash = SecurityUtils.hashPassword(enteredPassword);
                try (PreparedStatement insert = connection.prepareStatement(
                        "INSERT INTO users (first_name, last_name, username, password, admin_rights, first_login) VALUES (?, ?, ?, ?, ?, ?)"
                )) {
                    insert.setString(1, null);
                    insert.setString(2, null);
                    insert.setString(3, enteredUsername);
                    insert.setString(4, hash);
                    insert.setInt(5, addAdminRights.isSelected() ? 1 : 0);
                    insert.setInt(6, 1);
                    insert.executeUpdate();
                }
                DatabaseManager.logSecurityEvent(
                        connection,
                        user.getUsername(),
                        "USER_CREATED",
                        "Created user '" + enteredUsername + "' (admin=" + (addAdminRights.isSelected() ? "1" : "0") + ")"
                );
                JOptionPane.showMessageDialog(panel, "User created successfully. They will reset password on first sign-in.");
                addUsername.setText("");
                addPassword.setText("");
                addConfirm.setText("");
                addAdminRights.setSelected(false);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            } finally {
                Arrays.fill(enteredPassword, '\0');
                Arrays.fill(enteredConfirm, '\0');
            }
        });
        addSection.add(createUser);

        JPanel deleteSection = buildSectionPanel();
        deleteSection.add(buildSectionTitle("Delete User"));
        deleteSection.add(Box.createVerticalStrut(8));
        JPanel deleteForm = new JPanel(new GridLayout(0, 2, 10, 10));
        AppUI.applyPanelBackground(deleteForm);
        JTextField deleteUsername = new JTextField();
        JTextField deleteReason = new JTextField();
        styleInput(deleteUsername, deleteReason);
        deleteForm.add(new JLabel("Username to Delete *"));
        deleteForm.add(deleteUsername);
        deleteForm.add(new JLabel("Deletion Reason *"));
        deleteForm.add(deleteReason);
        deleteSection.add(deleteForm);
        deleteSection.add(Box.createVerticalStrut(10));
        JButton deleteUser = new JButton("Delete User");
        AppUI.stylePrimaryButton(deleteUser);
        deleteUser.setAlignmentX(Component.LEFT_ALIGNMENT);
        deleteUser.addActionListener(e -> {
            String targetUsername = deleteUsername.getText().trim();
            String reasonText = deleteReason.getText().trim();
            if (targetUsername.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Username is required.");
                return;
            }
            if (reasonText.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Deletion reason is required.");
                return;
            }
            if (targetUsername.equalsIgnoreCase(user.getUsername())) {
                JOptionPane.showMessageDialog(panel, "You cannot delete your own account while signed in.");
                return;
            }
            if ("Admin".equalsIgnoreCase(targetUsername)) {
                JOptionPane.showMessageDialog(panel, "Default Admin account cannot be deleted.");
                return;
            }
            int confirm = JOptionPane.showConfirmDialog(
                    panel,
                    "Delete user '" + targetUsername + "'?\nReason: " + reasonText,
                    "Confirm Account Deletion",
                    JOptionPane.YES_NO_OPTION,
                    JOptionPane.WARNING_MESSAGE
            );
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
            try (PreparedStatement deleteStmt = connection.prepareStatement("DELETE FROM users WHERE username = ?")) {
                deleteStmt.setString(1, targetUsername);
                int deleted = deleteStmt.executeUpdate();
                if (deleted == 0) {
                    JOptionPane.showMessageDialog(panel, "No user found with that username.");
                    return;
                }
                DatabaseManager.logSecurityEvent(
                        connection,
                        user.getUsername(),
                        "USER_DELETED",
                        "Deleted user '" + targetUsername + "' | reason: " + reasonText
                );
                JOptionPane.showMessageDialog(panel, "User deleted successfully.");
                deleteUsername.setText("");
                deleteReason.setText("");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        deleteSection.add(deleteUser);

        JPanel sections = new JPanel();
        sections.setLayout(new BoxLayout(sections, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(sections);
        addSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        deleteSection.setAlignmentX(Component.LEFT_ALIGNMENT);
        sections.add(addSection);
        sections.add(Box.createVerticalStrut(16));
        sections.add(deleteSection);

        panel.add(sections, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Builds purchase order create-and-review panel.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param workspaceContainer workspace card container
     * @return purchase-order panel
     * @throws SQLException when pending data fails to load
     */
    private static JPanel buildPurchaseOrdersPanel(User user, Connection connection, JPanel workspaceContainer) throws SQLException {
        JPanel panel = buildFormPanel(VIEW_PO_TRACKING);
        JPanel form = new JPanel(new GridLayout(0, 2, 12, 12));
        AppUI.applyPanelBackground(form);

        JTextField referenceField = new JTextField();
        JTextField itemCodeField = new JTextField();
        JTextField quantityField = new JTextField();
        JTextField purchasePriceField = new JTextField();
        styleInput(referenceField, itemCodeField, quantityField, purchasePriceField);

        form.add(new JLabel("Reference (Optional)"));
        form.add(referenceField);
        form.add(new JLabel("Item Code *"));
        form.add(itemCodeField);
        form.add(new JLabel("Quantity *"));
        form.add(quantityField);
        form.add(new JLabel("Purchase Price *"));
        form.add(purchasePriceField);

        DefaultTableModel pendingModel = new DefaultTableModel(new String[]{"Item Code", "Amount", "Purchase Price", "Reference", "Date"}, 0);
        JTable pendingTable = new JTable(pendingModel);
        installTableCopyMenu(pendingTable);
        loadPendingOrders(pendingModel, connection);

        JButton submit = new JButton("Create Purchase Order");
        JButton refreshPending = new JButton("Refresh Pending Orders");
        JButton receiveSelected = new JButton("Receive Selected Line");
        AppUI.stylePrimaryButton(submit);
        styleSecondaryButton(refreshPending);
        styleSecondaryButton(receiveSelected);
        submit.setPreferredSize(new Dimension(220, 36));
        refreshPending.setPreferredSize(new Dimension(190, 36));
        receiveSelected.setPreferredSize(new Dimension(190, 36));
        submit.addActionListener(e -> {
            try {
                String enteredCode = itemCodeField.getText().trim();
                String enteredQtyText = quantityField.getText().trim();
                String enteredPurchasePriceText = purchasePriceField.getText().trim();
                if (enteredCode.isEmpty() || enteredQtyText.isEmpty() || enteredPurchasePriceText.isEmpty()) {
                    JOptionPane.showMessageDialog(panel, "Item code, quantity, and purchase price are required.");
                    return;
                }
                int enteredQty = Integer.parseInt(enteredQtyText);
                double enteredPurchasePrice = Double.parseDouble(enteredPurchasePriceText);
                if (enteredQty <= 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be greater than zero.");
                    return;
                }
                if (enteredPurchasePrice < 0) {
                    JOptionPane.showMessageDialog(panel, "Purchase price must be zero or greater.");
                    return;
                }
                try (PreparedStatement exists = connection.prepareStatement("SELECT COUNT(*) AS count FROM inventory WHERE `Item Code` = ?")) {
                    exists.setString(1, enteredCode);
                    try (ResultSet rs = exists.executeQuery()) {
                        if (rs.next() && rs.getInt("count") == 0) {
                            JOptionPane.showMessageDialog(panel, "That item code was not found.");
                            return;
                        }
                    }
                }

                String reference = referenceField.getText().trim();
                if (reference.isEmpty()) {
                    reference = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                }
                String now = new dateTime().formattedDateTime();

                try (PreparedStatement insertPending = connection.prepareStatement("INSERT INTO pendingOrders (`Item Code`, `Amount`, `Purchase Price`, `Reference`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                    insertPending.setString(1, enteredCode);
                    insertPending.setInt(2, enteredQty);
                    insertPending.setDouble(3, enteredPurchasePrice);
                    insertPending.setString(4, reference);
                    insertPending.setString(5, user.getUsername());
                    insertPending.setString(6, now);
                    insertPending.executeUpdate();
                }
                try (PreparedStatement updateInventory = connection.prepareStatement("UPDATE inventory SET `On Order` = `On Order` + ? WHERE `Item Code` = ?")) {
                    updateInventory.setInt(1, enteredQty);
                    updateInventory.setString(2, enteredCode);
                    updateInventory.executeUpdate();
                }
                try (PreparedStatement movement = connection.prepareStatement("INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                    movement.setString(1, enteredCode);
                    movement.setString(2, String.valueOf(enteredQty));
                    movement.setString(3, "ORDERED");
                    movement.setString(4, "PURCHASE_ORDER_CREATED");
                    movement.setString(5, user.getUsername());
                    movement.setString(6, now);
                    movement.executeUpdate();
                }

                JOptionPane.showMessageDialog(panel, "Purchase order created. Reference: " + reference);
                referenceField.setText("");
                itemCodeField.setText("");
                quantityField.setText("");
                purchasePriceField.setText("");
                loadPendingOrders(pendingModel, connection);
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter valid numeric values for quantity and purchase price.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });
        refreshPending.addActionListener(e -> {
            try {
                loadPendingOrders(pendingModel, connection);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Unable to refresh pending orders: " + ex.getMessage());
            }
        });
        receiveSelected.addActionListener(e -> {
            int selectedRow = pendingTable.getSelectedRow();
            if (selectedRow < 0) {
                JOptionPane.showMessageDialog(panel, "Select a pending order line first.");
                return;
            }
            int modelRow = pendingTable.convertRowIndexToModel(selectedRow);
            String selectedCode = String.valueOf(pendingModel.getValueAt(modelRow, 0));
            String selectedReference = String.valueOf(pendingModel.getValueAt(modelRow, 3));
            try {
                showView(workspaceContainer, "Receive Order", buildReceiveOrderPanel(user, connection, workspaceContainer, selectedReference, selectedCode));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Unable to open receive workflow: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel top = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(top);
        top.add(buildSectionText("Create a new purchase order and review existing pending orders in one place."), BorderLayout.NORTH);
        top.add(form, BorderLayout.CENTER);
        top.add(buildActionBar(null, submit), BorderLayout.SOUTH);

        JPanel pendingSection = new JPanel(new BorderLayout(8, 8));
        AppUI.applyPanelBackground(pendingSection);
        JScrollPane pendingScroll = new JScrollPane(pendingTable);
        pendingScroll.setBorder(AppUI.newRoundedBorder(8));
        pendingSection.add(pendingScroll, BorderLayout.CENTER);
        pendingSection.add(buildActionBar(receiveSelected, refreshPending), BorderLayout.SOUTH);

        panel.add(top, BorderLayout.NORTH);
        panel.add(pendingSection, BorderLayout.CENTER);
        return panel;
    }

    /**
     * Builds receive-order panel: receipts post to Stock and FIFO layers; reduces open PO quantities and {@code On Order}.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param workspaceContainer workspace card container
     * @return receive-order panel
     * @throws SQLException when pending-order table fails to load
     */
    private static JPanel buildReceiveOrderPanel(User user, Connection connection, JPanel workspaceContainer) throws SQLException {
        return buildReceiveOrderPanel(user, connection, workspaceContainer, null, null);
    }

    /**
     * Builds receive-order panel with optional prefilled reference and item code.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param workspaceContainer workspace card container
     * @param prefillReference optional purchase order reference
     * @param prefillItemCode optional item code
     * @return receive-order panel
     * @throws SQLException when pending-order table fails to load
     */
    private static JPanel buildReceiveOrderPanel(
            User user,
            Connection connection,
            JPanel workspaceContainer,
            String prefillReference,
            String prefillItemCode
    ) throws SQLException {
        JPanel panel = buildFormPanel("Receive Order");
        JPanel form = new JPanel(new GridLayout(0, 2, 12, 12));
        AppUI.applyPanelBackground(form);
        JTextField reference = new JTextField();
        JTextField itemCode = new JTextField();
        JTextField received = new JTextField();
        styleInput(reference, itemCode, received);
        if (prefillReference != null) {
            reference.setText(prefillReference);
        }
        if (prefillItemCode != null) {
            itemCode.setText(prefillItemCode);
        }
        form.add(new JLabel("Reference *"));
        form.add(reference);
        form.add(new JLabel("Item Code *"));
        form.add(itemCode);
        form.add(new JLabel("Received Quantity *"));
        form.add(received);

        DefaultTableModel pendingModel = new DefaultTableModel(new String[]{"Item Code", "Amount", "Purchase Price", "Reference", "Date"}, 0);
        JTable pendingTable = new JTable(pendingModel);
        installTableCopyMenu(pendingTable);
        TableRowSorter<DefaultTableModel> pendingSorter = new TableRowSorter<>(pendingModel);
        pendingTable.setRowSorter(pendingSorter);
        loadPendingOrders(pendingModel, connection);
        pendingTable.getSelectionModel().addListSelectionListener(e -> {
            if (e.getValueIsAdjusting()) {
                return;
            }
            int selectedRow = pendingTable.getSelectedRow();
            if (selectedRow < 0) {
                return;
            }
            int modelRow = pendingTable.convertRowIndexToModel(selectedRow);
            itemCode.setText(String.valueOf(pendingModel.getValueAt(modelRow, 0)));
            reference.setText(String.valueOf(pendingModel.getValueAt(modelRow, 3)));
        });
        JScrollPane pendingScroll = new JScrollPane(pendingTable);
        pendingScroll.setBorder(AppUI.newRoundedBorder(8));
        JPanel searchPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        AppUI.applyPanelBackground(searchPanel);
        JTextField pendingSearch = new JTextField();
        pendingSearch.setBorder(AppUI.newRoundedBorder(8));
        searchPanel.add(new JLabel("Search Pending (Item Code / Reference)"));
        searchPanel.add(pendingSearch);
        pendingSearch.getDocument().addDocumentListener(new DocumentListener() {
            private void applyFilter() {
                String text = pendingSearch.getText();
                if (text == null || text.trim().isEmpty()) {
                    pendingSorter.setRowFilter(null);
                    return;
                }
                String like = text.trim();
                pendingSorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(like), 0, 3));
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        JButton refreshPending = new JButton("Refresh Pending Orders");
        styleSecondaryButton(refreshPending);
        refreshPending.addActionListener(e -> {
            try {
                loadPendingOrders(pendingModel, connection);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Unable to refresh pending orders: " + ex.getMessage());
            }
        });

        JButton submit = new JButton("Receive");
        AppUI.stylePrimaryButton(submit);
        submit.addActionListener(e -> {
            String ref = reference.getText().trim();
            String code = itemCode.getText().trim();
            if (ref.isEmpty() || code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Reference and item code are required.");
                return;
            }
            try {
                int qty = Integer.parseInt(received.getText().trim());
                if (qty <= 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be greater than zero.");
                    return;
                }
                int ordered;
                double purchasePrice;
                try (PreparedStatement ps = connection.prepareStatement("SELECT Amount, `Purchase Price` FROM pendingOrders WHERE `Reference` = ? AND `Item Code` = ?")) {
                    ps.setString(1, ref);
                    ps.setString(2, code);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            JOptionPane.showMessageDialog(panel, "No pending order line found for that reference and item.");
                            return;
                        }
                        ordered = rs.getInt("Amount");
                        purchasePrice = rs.getDouble("Purchase Price");
                    }
                }
                if (qty > ordered) {
                    JOptionPane.showMessageDialog(panel, "Received quantity cannot exceed ordered amount.");
                    return;
                }
                applyReceive(user, connection, ref, code, qty, purchasePrice);
                JOptionPane.showMessageDialog(panel, "Items received successfully.");
                showView(workspaceContainer, VIEW_PO_TRACKING, buildPurchaseOrdersPanel(user, connection, workspaceContainer));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter a valid quantity.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });

        JPanel footer = buildActionBar(refreshPending, submit);
        panel.add(form, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(center);
        center.add(pendingScroll, BorderLayout.CENTER);
        center.add(searchPanel, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /** Builds admin-only reorder trigger update panel. */
    private static JPanel buildAdjustReorderPanel(User user, Connection connection) {
        ensureAdmin(user, "Adjust Reorder Trigger");
        JPanel panel = buildFormPanel("Adjust Reorder Trigger");
        JPanel form = new JPanel(new GridLayout(0, 2, 12, 12));
        AppUI.applyPanelBackground(form);
        JTextField itemCode = new JTextField();
        JTextField trigger = new JTextField();
        styleInput(itemCode, trigger);
        form.add(new JLabel("Item Code *"));
        form.add(itemCode);
        form.add(new JLabel("New Trigger *"));
        form.add(trigger);
        JButton submit = new JButton("Update Trigger");
        AppUI.stylePrimaryButton(submit);
        submit.addActionListener(e -> {
            String code = itemCode.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Item code is required.");
                return;
            }
            try {
                int newTrigger = Integer.parseInt(trigger.getText().trim());
                if (newTrigger < 0) {
                    JOptionPane.showMessageDialog(panel, "Trigger must be zero or greater.");
                    return;
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE Inventory SET `ReOrder Trigger` = ? WHERE `Item Code` = ?")) {
                    update.setInt(1, newTrigger);
                    update.setString(2, code);
                    if (update.executeUpdate() == 0) {
                        JOptionPane.showMessageDialog(panel, "No matching item found.");
                        return;
                    }
                }
                try (PreparedStatement movement = connection.prepareStatement("INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                    movement.setString(1, code);
                    movement.setString(2, " ");
                    movement.setString(3, "UPDATED TRIGGER");
                    movement.setString(4, "REORDER_TRIGGER_UPDATE");
                    movement.setString(5, user.getUsername());
                    movement.setString(6, new dateTime().formattedDateTime());
                    movement.executeUpdate();
                }
                JOptionPane.showMessageDialog(panel, "Reorder trigger updated.");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter a valid whole number.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });
        JPanel footer = buildActionBar(null, submit);
        panel.add(form, BorderLayout.NORTH);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /** One grid cell: bold code · abbreviated name — price field (single line); stretches with column width. */
    private static JPanel buildMarketPriceItemSlot(String code, String name, JTextField priceField) {
        JPanel outer = new JPanel(new BorderLayout(6, 0));
        outer.setOpaque(false);
        JPanel codeName = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        codeName.setOpaque(false);
        JLabel jc = new JLabel(code);
        jc.setFont(jc.getFont().deriveFont(Font.BOLD, 12f));
        codeName.add(jc);
        JLabel jn = new JLabel(abbreviateForTableCell(name, 22));
        jn.setForeground(new Color(0x475569));
        codeName.add(jn);

        JPanel east = new JPanel(new FlowLayout(FlowLayout.RIGHT, 2, 0));
        east.setOpaque(false);
        styleInput(priceField);
        int pw = Math.max(priceField.getPreferredSize().width, 76);
        priceField.setPreferredSize(new Dimension(pw, INPUT_HEIGHT));
        priceField.setMaximumSize(new Dimension(105, INPUT_HEIGHT));
        east.add(priceField);

        outer.add(codeName, BorderLayout.CENTER);
        outer.add(east, BorderLayout.EAST);
        outer.setMinimumSize(new Dimension(100, INPUT_HEIGHT + 4));
        return outer;
    }

    /** Cell wrapper with a thin vertical rule after each column except the last (Item 1 | Item 2 | Item 3 | Item 4). */
    private static JPanel marketPriceColumnDividerWrap(JPanel inner, boolean drawRightDivider) {
        JPanel cell = new JPanel(new BorderLayout());
        cell.setOpaque(false);
        int ri = drawRightDivider ? 1 : 0;
        javax.swing.border.Border outer = BorderFactory.createMatteBorder(0, 0, 0, ri, new Color(0xe2e8f0));
        javax.swing.border.Border padded = BorderFactory.createCompoundBorder(outer,
                BorderFactory.createEmptyBorder(6, 6, 6, 6));
        cell.setBorder(padded);
        if (inner != null) {
            cell.add(inner, BorderLayout.CENTER);
        }
        return cell;
    }

    /** Bulk-edit {@code Inventory.Market Price} for SKUs with {@code Stock > 0}; blank fields leave existing DB values. */
    private static JPanel buildMarketPricesBulkPanel(User user, Connection connection, JPanel workspaceContainer)
            throws SQLException {
        ensureAdmin(user, "Market Prices");
        JPanel panel = buildFormPanel("Market prices");
        JPanel centerWrap = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(centerWrap);

        JPanel intro = buildSectionPanel();
        intro.add(buildSectionText(
                "Only items with stock on hand are listed. Leave the price box blank to keep the current saved value; "
                        + "enter a unit amount (for example 12.99 or $12.99) to replace it."));
        centerWrap.add(intro, BorderLayout.NORTH);

        LinkedHashMap<String, JTextField> codeToPriceField = new LinkedHashMap<>();
        List<String> codesOrdered = new ArrayList<>();
        List<String> namesOrdered = new ArrayList<>();

        boolean anyRows = false;
        try (PreparedStatement ps = connection.prepareStatement(
                "SELECT `Item Code`, `Item Name`, `Stock`, `Market Price` FROM inventory WHERE `Stock` > 0 ORDER BY `Item Code` ASC"
        )) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    anyRows = true;
                    String code = rs.getString("Item Code");
                    String nm = Objects.toString(rs.getString("Item Name"), "");
                    double mp = rs.getDouble("Market Price");
                    boolean mpNull = rs.wasNull();

                    JTextField priceField = new JTextField(mpNull ? "" : String.format(Locale.US, "%.2f", mp), 8);
                    priceField.setBorder(AppUI.newRoundedBorder(8));
                    codeToPriceField.put(code, priceField);
                    codesOrdered.add(code);
                    namesOrdered.add(nm);
                }
            }
        }

        if (!anyRows) {
            centerWrap.add(buildSectionText("No items with stock on hand."), BorderLayout.CENTER);
        } else {
            JPanel scrollBody = new JPanel(new GridBagLayout());
            scrollBody.setOpaque(true);
            AppUI.applyPanelBackground(scrollBody);

            int n = codesOrdered.size();
            int numRows = (n + 3) / 4;

            GridBagConstraints gbc = new GridBagConstraints();
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.anchor = GridBagConstraints.NORTH;
            gbc.weightx = 1.0 / 4.0;
            gbc.weighty = 0;

            for (int r = 0; r < numRows; r++) {
                gbc.gridy = r;
                for (int c = 0; c < 4; c++) {
                    int idx = r * 4 + c;
                    gbc.gridx = c;
                    gbc.insets = new Insets(r == 0 ? 2 : 8, 0, 0, 0);

                    JPanel slot = null;
                    if (idx < n) {
                        slot = buildMarketPriceItemSlot(
                                codesOrdered.get(idx),
                                namesOrdered.get(idx),
                                codeToPriceField.get(codesOrdered.get(idx)));
                    }

                    JPanel cell = marketPriceColumnDividerWrap(slot, c < 3);
                    scrollBody.add(cell, gbc);
                }
            }

            GridBagConstraints glue = new GridBagConstraints();
            glue.gridy = numRows;
            glue.gridx = 0;
            glue.gridwidth = 4;
            glue.weighty = 1.0;
            glue.weightx = 1.0;
            glue.fill = GridBagConstraints.VERTICAL;
            JPanel spacer = new JPanel();
            spacer.setOpaque(false);
            scrollBody.add(spacer, glue);

            JScrollPane scroll = new JScrollPane(scrollBody,
                    JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED,
                    JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            scroll.setBorder(AppUI.newRoundedBorder(8));
            scroll.getViewport().setBackground(scrollBody.getBackground());
            scroll.getVerticalScrollBar().setUnitIncrement(16);
            scroll.setPreferredSize(new Dimension(1080, Math.min(MAIN_FRAME_BASE_H - 260, 520)));
            centerWrap.add(scroll, BorderLayout.CENTER);
        }

        JButton submit = new JButton("Submit price updates");
        AppUI.stylePrimaryButton(submit);
        submit.setEnabled(anyRows);
        submit.addActionListener(e -> {
            List<Object[]> updates = new ArrayList<>();
            for (Map.Entry<String, JTextField> entry : codeToPriceField.entrySet()) {
                try {
                    Double v = parseOptionalMarketPriceInput(entry.getValue().getText());
                    if (v != null) {
                        updates.add(new Object[]{entry.getKey(), v});
                    }
                } catch (NumberFormatException nf) {
                    JOptionPane.showMessageDialog(panel,
                            "Invalid price for " + entry.getKey() + ": " + nf.getMessage(),
                            "Input error",
                            JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
            if (updates.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Nothing to update — every price field was left blank.");
                return;
            }
            boolean savedAc = true;
            try {
                savedAc = connection.getAutoCommit();
                connection.setAutoCommit(false);
                try (PreparedStatement ps = connection.prepareStatement(
                        "UPDATE inventory SET `Market Price` = ? WHERE `Item Code` = ?")) {
                    for (Object[] row : updates) {
                        ps.setDouble(1, (Double) row[1]);
                        ps.setString(2, (String) row[0]);
                        ps.executeUpdate();
                    }
                }
                connection.commit();
                JOptionPane.showMessageDialog(panel, "Updated market price for " + updates.size() + " item(s).");
                refreshActiveMetricsStripNow();
                try {
                    showView(workspaceContainer, "Market Prices", buildMarketPricesBulkPanel(user, connection, workspaceContainer));
                } catch (SQLException ex) {
                    JOptionPane.showMessageDialog(panel, "Prices saved, but the screen could not refresh: " + ex.getMessage());
                }
            } catch (SQLException ex) {
                try {
                    connection.rollback();
                } catch (SQLException ignored) {
                    // ignore rollback failure
                }
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            } finally {
                try {
                    connection.setAutoCommit(savedAc);
                } catch (SQLException ignored) {
                    // ignore
                }
            }
        });

        JPanel footer = buildActionBar(null, submit);
        panel.add(centerWrap, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /** Builds admin-only write-off panel for stock reductions. */
    private static JPanel buildWriteOffPanel(User user, Connection connection) {
        ensureAdmin(user, "Write Off Stock");
        JPanel panel = buildFormPanel("Write Off Stock");
        JPanel form = new JPanel(new GridLayout(0, 2, 12, 12));
        AppUI.applyPanelBackground(form);
        JTextField itemCode = new JTextField();
        JTextField quantity = new JTextField();
        JComboBox<String> reasonCode = new JComboBox<>(new String[]{
                "DAMAGED",
                "EXPIRED",
                "COUNT_CORRECTION",
                "THEFT_LOSS",
                "RETURN_DAMAGED",
                "OTHER"
        });
        reasonCode.setBorder(AppUI.newRoundedBorder(8));
        styleInput(itemCode, quantity);
        form.add(new JLabel("Item Code *"));
        form.add(itemCode);
        form.add(new JLabel("Write-off Quantity *"));
        form.add(quantity);
        form.add(new JLabel("Reason Code *"));
        form.add(reasonCode);

        DefaultTableModel writeOffHistoryModel = new DefaultTableModel(
                new String[]{"Item Code", "Amount", "Reason", "User", "Date"},
                0
        );
        JTable writeOffHistoryTable = new JTable(writeOffHistoryModel);
        installTableCopyMenu(writeOffHistoryTable);
        TableRowSorter<DefaultTableModel> writeOffSorter = new TableRowSorter<>(writeOffHistoryModel);
        writeOffHistoryTable.setRowSorter(writeOffSorter);
        try {
            loadWriteOffHistory(writeOffHistoryModel, connection);
        } catch (SQLException ex) {
            JOptionPane.showMessageDialog(panel, "Unable to load write-off history: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        JScrollPane historyScroll = new JScrollPane(writeOffHistoryTable);
        historyScroll.setBorder(AppUI.newRoundedBorder(8));

        JPanel searchPanel = new JPanel(new GridLayout(1, 2, 8, 8));
        AppUI.applyPanelBackground(searchPanel);
        JTextField historySearch = new JTextField();
        historySearch.setBorder(AppUI.newRoundedBorder(8));
        searchPanel.add(new JLabel("Search Write-offs (Item / Reason / User)"));
        searchPanel.add(historySearch);
        historySearch.getDocument().addDocumentListener(new DocumentListener() {
            private void applyFilter() {
                String text = historySearch.getText();
                if (text == null || text.trim().isEmpty()) {
                    writeOffSorter.setRowFilter(null);
                    return;
                }
                String like = text.trim();
                writeOffSorter.setRowFilter(RowFilter.regexFilter("(?i)" + java.util.regex.Pattern.quote(like), 0, 2, 3));
            }

            @Override
            public void insertUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                applyFilter();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                applyFilter();
            }
        });

        JButton refreshHistory = new JButton("Refresh Write-off History");
        styleSecondaryButton(refreshHistory);
        refreshHistory.addActionListener(e -> {
            try {
                loadWriteOffHistory(writeOffHistoryModel, connection);
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Unable to refresh write-off history: " + ex.getMessage());
            }
        });

        JButton submit = new JButton("Write Off");
        AppUI.stylePrimaryButton(submit);
        submit.addActionListener(e -> {
            String code = itemCode.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Item code is required.");
                return;
            }
            String selectedReason = (String) reasonCode.getSelectedItem();
            if (selectedReason == null || selectedReason.isBlank()) {
                JOptionPane.showMessageDialog(panel, "Reason code is required.");
                return;
            }
            try {
                int qty = Integer.parseInt(quantity.getText().trim());
                if (qty < 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be zero or greater.");
                    return;
                }
                try (PreparedStatement update = connection.prepareStatement("UPDATE Inventory SET Stock = Stock - ? WHERE `Item Code` = ? AND Stock >= ?")) {
                    update.setInt(1, qty);
                    update.setString(2, code);
                    update.setInt(3, qty);
                    if (update.executeUpdate() == 0) {
                        JOptionPane.showMessageDialog(panel, "Write-off failed. Check item code and available stock.");
                        return;
                    }
                }
                try (PreparedStatement movement = connection.prepareStatement("INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                    movement.setString(1, code);
                    movement.setString(2, String.valueOf(qty));
                    movement.setString(3, "WRITE OFF");
                    movement.setString(4, selectedReason);
                    movement.setString(5, user.getUsername());
                    movement.setString(6, new dateTime().formattedDateTime());
                    movement.executeUpdate();
                }
                JOptionPane.showMessageDialog(panel, "Stock write-off completed.");
                loadWriteOffHistory(writeOffHistoryModel, connection);
                requestMetricsRefresh();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter a valid whole number.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });
        JPanel footer = buildActionBar(refreshHistory, submit);
        panel.add(form, BorderLayout.NORTH);
        JPanel center = new JPanel(new BorderLayout(0, 8));
        AppUI.applyPanelBackground(center);
        center.add(historyScroll, BorderLayout.CENTER);
        center.add(searchPanel, BorderLayout.SOUTH);
        panel.add(center, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Builds process-sale panel with multi-line sale draft table.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param workspaceContainer card layout host panel
     * @return sale processing panel
     */
    private static JPanel buildProcessSalePanel(User user, Connection connection, JPanel workspaceContainer) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        AppUI.applyPanelBackground(panel);
        JPanel headerCol = new JPanel();
        headerCol.setLayout(new BoxLayout(headerCol, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(headerCol);
        headerCol.add(buildSectionTitle("Process Sale"));
        headerCol.add(Box.createVerticalStrut(6));
        JLabel posHint = new JLabel("<html><body style='width:420px'>Enter the <b>unit sale price</b> for each line. Revenue and profit in the top bar use these prices minus FIFO cost.</body></html>");
        posHint.setAlignmentX(Component.LEFT_ALIGNMENT);
        headerCol.add(posHint);
        headerCol.add(Box.createVerticalStrut(12));

        JPanel form = new JPanel(new GridLayout(0, 2, 12, 12));
        AppUI.applyPanelBackground(form);
        JTextField itemCode = new JTextField();
        JTextField quantity = new JTextField();
        JTextField unitSalePrice = new JTextField();
        styleInput(itemCode, quantity, unitSalePrice);
        form.add(new JLabel("Item Code *"));
        form.add(itemCode);
        form.add(new JLabel("Quantity *"));
        form.add(quantity);
        form.add(new JLabel("Unit sale price *"));
        form.add(unitSalePrice);

        Map<String, SaleDraftLine> items = new LinkedHashMap<>();
        DefaultTableModel model = new DefaultTableModel(new String[]{"Item Code", "Quantity", "Unit sale price"}, 0);
        JTable table = new JTable(model);
        installTableCopyMenu(table);

        JButton addLine = new JButton("Add Line");
        JButton submit = new JButton("Complete Sale");
        AppUI.stylePrimaryButton(submit);

        JTextField transactionNote = new JTextField();
        styleInput(transactionNote);
        transactionNote.setToolTipText("Optional — one note for this entire sale (e.g. discount reason).");

        addLine.addActionListener(e -> {
            String code = itemCode.getText().trim();
            if (code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Item code is required.");
                return;
            }
            try {
                int qty = Integer.parseInt(quantity.getText().trim());
                if (qty <= 0) {
                    JOptionPane.showMessageDialog(panel, "Quantity must be greater than zero.");
                    return;
                }
                double unitPrice = Double.parseDouble(unitSalePrice.getText().trim());
                if (unitPrice < 0) {
                    JOptionPane.showMessageDialog(panel, "Unit sale price cannot be negative.");
                    return;
                }
                int stock = getIntValue(connection, "SELECT `Stock` FROM inventory WHERE `Item Code` = ?", code);
                if (stock <= 0) {
                    JOptionPane.showMessageDialog(panel, "Item is out of stock or not found.");
                    return;
                }
                int already = items.containsKey(code) ? items.get(code).quantity : 0;
                if (already + qty > stock) {
                    JOptionPane.showMessageDialog(panel, "Not enough stock available for this line (including lines already in the sale).");
                    return;
                }
                if (items.containsKey(code)) {
                    SaleDraftLine existing = items.get(code);
                    if (Double.compare(existing.unitSalePrice, unitPrice) != 0) {
                        JOptionPane.showMessageDialog(panel, "This item is already on the sale at a different unit price. Remove it first or use the same price.");
                        return;
                    }
                    existing.quantity += qty;
                } else {
                    items.put(code, new SaleDraftLine(qty, unitPrice));
                }
                refreshSaleDraftTable(model, items);
                itemCode.setText("");
                quantity.setText("");
                unitSalePrice.setText("");
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter a valid quantity and unit sale price.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });

        submit.addActionListener(e -> {
            if (items.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Add at least one item first.");
                return;
            }
            String rawNote = transactionNote.getText();
            if (rawNote != null && rawNote.length() > MAX_SALE_TRANSACTION_NOTE_LENGTH) {
                JOptionPane.showMessageDialog(panel,
                        "Transaction note must be at most " + MAX_SALE_TRANSACTION_NOTE_LENGTH + " characters.");
                return;
            }
            String reference = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
            String now = new dateTime().formattedDateTime();
            try {
                processSaleTransaction(connection, user, items, reference, now, rawNote);
                JOptionPane.showMessageDialog(panel, "Sale completed. Reference: " + reference);
                transactionNote.setText("");
                showView(workspaceContainer, "View Sales Transaction", buildSalesPanel(connection));
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });

        JPanel top = new JPanel(new BorderLayout(0, 12));
        AppUI.applyPanelBackground(top);
        top.add(form, BorderLayout.NORTH);
        top.add(buildActionBar(addLine, null), BorderLayout.SOUTH);

        JPanel footer = new JPanel(new BorderLayout(12, 0));
        footer.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        AppUI.applyPanelBackground(footer);
        JPanel noteAndComplete = new JPanel(new BorderLayout(10, 0));
        AppUI.applyPanelBackground(noteAndComplete);
        noteAndComplete.add(new JLabel("Note (optional)"), BorderLayout.WEST);
        noteAndComplete.add(transactionNote, BorderLayout.CENTER);
        footer.add(noteAndComplete, BorderLayout.CENTER);
        footer.add(submit, BorderLayout.EAST);

        JPanel northStack = new JPanel(new BorderLayout(0, 0));
        AppUI.applyPanelBackground(northStack);
        northStack.add(headerCol, BorderLayout.NORTH);
        northStack.add(top, BorderLayout.CENTER);

        panel.add(northStack, BorderLayout.NORTH);
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setBorder(AppUI.newRoundedBorder(8));
        panel.add(tableScroll, BorderLayout.CENTER);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /**
     * Executes a sale transaction atomically across all related tables.
     *
     * @param connection active database connection
     * @param user active signed-in user
     * @param items map of item codes to quantity and unit sale price captured at checkout
     * @param reference sale reference
     * @param now formatted timestamp
     * @param transactionNote optional single note for the whole checkout (stored on each line row)
     * @throws SQLException when transaction fails
     */
    private static void processSaleTransaction(
            Connection connection,
            User user,
            Map<String, SaleDraftLine> items,
            String reference,
            String now,
            String transactionNote
    ) throws SQLException {
        String noteForDb = normalizedSaleTransactionNote(transactionNote);
        boolean originalAutoCommit = connection.getAutoCommit();
        connection.setAutoCommit(false);
        try {
            for (Map.Entry<String, SaleDraftLine> entry : items.entrySet()) {
                String code = entry.getKey();
                SaleDraftLine line = entry.getValue();
                int qty = line.quantity;
                double unitSalePrice = line.unitSalePrice;
                String itemName;
                try (PreparedStatement fetch = connection.prepareStatement("SELECT `Item Name` FROM inventory WHERE `Item Code` = ?")) {
                    fetch.setString(1, code);
                    try (ResultSet rs = fetch.executeQuery()) {
                        if (!rs.next()) {
                            throw new SQLException("Item not found: " + code);
                        }
                        itemName = rs.getString("Item Name");
                    }
                }

                double fifoCost = InventoryFifo.fifoCostWithLatestLayerFallback(connection, code, qty);

                String dateIso = toIsoDateTime(now);
                try (PreparedStatement insertSale = connection.prepareStatement(
                        "INSERT INTO sales (`Item Code`, `Item Name`, `Amount`, `Total Price`, `Total Cost`, `Reference`, `User`, `Date`, `DateISO`, `Note`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
                )) {
                    insertSale.setString(1, code);
                    insertSale.setString(2, itemName);
                    insertSale.setInt(3, qty);
                    insertSale.setDouble(4, unitSalePrice * qty);
                    insertSale.setDouble(5, fifoCost);
                    insertSale.setString(6, reference);
                    insertSale.setString(7, user.getUsername());
                    insertSale.setString(8, now);
                    insertSale.setString(9, dateIso);
                    insertSale.setString(10, noteForDb);
                    insertSale.executeUpdate();
                }
                try (PreparedStatement updateStock = connection.prepareStatement("UPDATE inventory SET `Stock` = `Stock` - ? WHERE `Item Code` = ? AND `Stock` >= ?")) {
                    updateStock.setInt(1, qty);
                    updateStock.setString(2, code);
                    updateStock.setInt(3, qty);
                    int affected = updateStock.executeUpdate();
                    if (affected == 0) {
                        throw new SQLException("Insufficient stock while processing sale for item: " + code);
                    }
                }
                try (PreparedStatement movement = connection.prepareStatement("INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                    movement.setString(1, code);
                    movement.setString(2, String.valueOf(qty));
                    movement.setString(3, "SALE");
                    movement.setString(4, "CUSTOMER_SALE");
                    movement.setString(5, user.getUsername());
                    movement.setString(6, now);
                    movement.executeUpdate();
                }
            }

            connection.commit();
        } catch (SQLException ex) {
            connection.rollback();
            throw ex;
        } finally {
            connection.setAutoCommit(originalAutoCommit);
        }
    }

    /** Builds return workflow panel to reverse sale quantities and values. */
    private static JPanel buildReturnPanel(User user, Connection connection, JPanel workspaceContainer) {
        JPanel panel = buildFormPanel("Return Item");
        JPanel form = new JPanel(new GridLayout(0, 2, 12, 12));
        AppUI.applyPanelBackground(form);
        JTextField reference = new JTextField();
        JTextField itemCode = new JTextField();
        JTextField quantity = new JTextField();
        JComboBox<String> returnCondition = new JComboBox<>(new String[]{"RESALABLE", "DAMAGED"});
        returnCondition.setBorder(AppUI.newRoundedBorder(8));
        JComboBox<String> damagedReason = new JComboBox<>(new String[]{
                "DAMAGED_IN_TRANSIT",
                "DAMAGED_BY_CUSTOMER",
                "DEFECTIVE",
                "EXPIRED",
                "OTHER"
        });
        damagedReason.setBorder(AppUI.newRoundedBorder(8));
        styleInput(reference, itemCode, quantity);
        form.add(new JLabel("Sales Reference *"));
        form.add(reference);
        form.add(new JLabel("Item Code *"));
        form.add(itemCode);
        form.add(new JLabel("Return Quantity *"));
        form.add(quantity);
        form.add(new JLabel("Return Condition *"));
        form.add(returnCondition);
        form.add(new JLabel("Damaged Reason *"));
        form.add(damagedReason);
        damagedReason.setEnabled(false);
        returnCondition.addActionListener(e -> damagedReason.setEnabled("DAMAGED".equals(returnCondition.getSelectedItem())));
        JButton submit = new JButton("Process Return");
        AppUI.stylePrimaryButton(submit);
        submit.addActionListener(e -> {
            String ref = reference.getText().trim();
            String code = itemCode.getText().trim();
            if (ref.isEmpty() || code.isEmpty()) {
                JOptionPane.showMessageDialog(panel, "Reference and item code are required.");
                return;
            }
            try {
                int qty = Integer.parseInt(quantity.getText().trim());
                if (qty <= 0) {
                    JOptionPane.showMessageDialog(panel, "Return quantity must be greater than zero.");
                    return;
                }
                String condition = (String) returnCondition.getSelectedItem();
                if (condition == null || condition.isBlank()) {
                    JOptionPane.showMessageDialog(panel, "Return condition is required.");
                    return;
                }
                String reason = "RESALABLE";
                if ("DAMAGED".equals(condition)) {
                    reason = (String) damagedReason.getSelectedItem();
                    if (reason == null || reason.isBlank()) {
                        JOptionPane.showMessageDialog(panel, "Damaged reason is required.");
                        return;
                    }
                }
                int sold;
                double totalPrice;
                double totalCost;
                try (PreparedStatement saleLine = connection.prepareStatement("SELECT `Amount`, `Total Price`, `Total Cost` FROM sales WHERE `Reference` = ? AND `Item Code` = ?")) {
                    saleLine.setString(1, ref);
                    saleLine.setString(2, code);
                    try (ResultSet rs = saleLine.executeQuery()) {
                        if (!rs.next()) {
                            JOptionPane.showMessageDialog(panel, "Sale line was not found for that reference and item.");
                            return;
                        }
                        sold = rs.getInt("Amount");
                        totalPrice = rs.getDouble("Total Price");
                        totalCost = rs.getDouble("Total Cost");
                    }
                }
                if (sold <= 0 || qty > sold) {
                    JOptionPane.showMessageDialog(panel, "Return quantity exceeds sold amount or sale line not found.");
                    return;
                }

                double unitPrice = sold == 0 ? 0 : totalPrice / sold;
                double priceReduction = unitPrice * qty;
                double unitCost = sold == 0 ? 0 : totalCost / sold;
                double costReduction = unitCost * qty;
                try (PreparedStatement updateSales = connection.prepareStatement(
                        "UPDATE sales SET Amount = Amount - ?, `Total Price` = CASE WHEN `Total Price` - ? < 0 THEN 0 ELSE `Total Price` - ? END, `Total Cost` = CASE WHEN `Total Cost` - ? < 0 THEN 0 ELSE `Total Cost` - ? END WHERE `Reference` = ? AND `Item Code` = ?"
                )) {
                    updateSales.setInt(1, qty);
                    updateSales.setDouble(2, priceReduction);
                    updateSales.setDouble(3, priceReduction);
                    updateSales.setDouble(4, costReduction);
                    updateSales.setDouble(5, costReduction);
                    updateSales.setString(6, ref);
                    updateSales.setString(7, code);
                    updateSales.executeUpdate();
                }
                if ("RESALABLE".equals(condition)) {
                    try (PreparedStatement updateStock = connection.prepareStatement("UPDATE Inventory SET `Stock` = `Stock` + ? WHERE `Item Code` = ?")) {
                        updateStock.setInt(1, qty);
                        updateStock.setString(2, code);
                        updateStock.executeUpdate();
                    }
                }
                String movementType = "RESALABLE".equals(condition) ? "RETURN" : "RETURN_DAMAGED";
                try (PreparedStatement movement = connection.prepareStatement("INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
                    movement.setString(1, code);
                    movement.setString(2, String.valueOf(qty));
                    movement.setString(3, movementType);
                    movement.setString(4, reason);
                    movement.setString(5, user.getUsername());
                    movement.setString(6, new dateTime().formattedDateTime());
                    movement.executeUpdate();
                }
                try (PreparedStatement delete = connection.prepareStatement("DELETE FROM sales WHERE `Reference` = ? AND `Item Code` = ? AND Amount = 0")) {
                    delete.setString(1, ref);
                    delete.setString(2, code);
                    delete.executeUpdate();
                }
                if ("RESALABLE".equals(condition)) {
                    JOptionPane.showMessageDialog(panel, "Return completed and stock restored.");
                } else {
                    JOptionPane.showMessageDialog(panel, "Damaged return recorded. Item was not restored to sellable stock.");
                }
                showView(workspaceContainer, "View Sales Transaction", buildSalesPanel(connection));
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(panel, "Enter a valid quantity.");
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error: " + ex.getMessage());
            }
        });
        JPanel footer = buildActionBar(null, submit);
        panel.add(form, BorderLayout.NORTH);
        panel.add(footer, BorderLayout.SOUTH);
        return panel;
    }

    /** Opens the shared password-reset dialog; on success closes the workspace so the user can sign in again. */
    private static JPanel buildResetPasswordPanel(User user, JFrame frame, AccountActions accountActions) {
        JPanel panel = buildFormPanel("Reset Password");
        JPanel content = buildSectionPanel();
        content.add(buildSectionText("Update your password. After a successful change you will return to the sign-in screen."));
        content.add(Box.createVerticalStrut(14));
        JButton openDialog = new JButton("Change password...");
        AppUI.stylePrimaryButton(openDialog);
        openDialog.setAlignmentX(Component.LEFT_ALIGNMENT);
        openDialog.addActionListener(e -> {
            AccountActions.PasswordResetOutcome outcome = accountActions.showPasswordResetDialog(frame, user);
            if (outcome == AccountActions.PasswordResetOutcome.SUCCESS) {
                frame.dispose();
            }
        });
        content.add(openDialog);
        panel.add(content, BorderLayout.NORTH);
        return panel;
    }

    /**
     * Builds admin-only reporting panel with charts, table, and export.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @return report panel
     */
    private static JPanel buildReportsPanel(User user, Connection connection) {
        ensureAdmin(user, "Generate Reports");
        JPanel panel = buildFormPanel("Generate Reports");
        JPanel filterPanel = new JPanel(new GridLayout(0, 2, 12, 12));
        AppUI.applyPanelBackground(filterPanel);

        JComboBox<String> reportType = user.hasAdminRights()
                ? new JComboBox<>(new String[]{"Sales", "Users", "Movements"})
                : new JComboBox<>(new String[]{"Sales"});
        reportType.setBorder(AppUI.newRoundedBorder(8));
        JTextField search = new JTextField();
        JTextField fromDate = new JTextField();
        JTextField toDate = new JTextField();
        styleInput(search, fromDate, toDate);

        fromDate.setText(LocalDate.now().minusDays(30).toString());
        toDate.setText(LocalDate.now().toString());

        filterPanel.add(new JLabel("Report Type"));
        filterPanel.add(reportType);
        filterPanel.add(new JLabel("Search (Optional)"));
        filterPanel.add(search);
        filterPanel.add(new JLabel("From Date (yyyy-MM-dd)"));
        filterPanel.add(fromDate);
        filterPanel.add(new JLabel("To Date (yyyy-MM-dd)"));
        filterPanel.add(toDate);

        DefaultTableModel salesTableModel = new DefaultTableModel(new String[]{
                "Item Code", "Item Name", "Amount", "Total Price", "Total Cost", "Gross Profit", "Reference", "User", "Date", "Note"
        }, 0);
        JTable salesTable = new JTable(salesTableModel);
        installTableCopyMenu(salesTable);
        JScrollPane salesScroll = new JScrollPane(salesTable);
        salesScroll.setBorder(AppUI.newRoundedBorder(8));

        DefaultTableModel usersTableModel = new DefaultTableModel(new String[]{"Username", "Event Type", "Details", "Date"}, 0);
        JTable usersTable = new JTable(usersTableModel);
        installTableCopyMenu(usersTable);
        JScrollPane usersScroll = new JScrollPane(usersTable);
        usersScroll.setBorder(AppUI.newRoundedBorder(8));

        JPanel weeklyChartHost = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(weeklyChartHost);
        weeklyChartHost.setBorder(AppUI.newRoundedBorder(8));
        weeklyChartHost.add(new JLabel("Weekly sales histogram appears here.", SwingConstants.CENTER), BorderLayout.CENTER);

        JPanel monthlyChartHost = new JPanel(new BorderLayout());
        AppUI.applyPanelBackground(monthlyChartHost);
        monthlyChartHost.setBorder(AppUI.newRoundedBorder(8));
        monthlyChartHost.add(new JLabel("Monthly sales histogram appears here.", SwingConstants.CENTER), BorderLayout.CENTER);

        JPanel salesCharts = new JPanel(new GridLayout(1, 2, 12, 12));
        AppUI.applyPanelBackground(salesCharts);
        salesCharts.add(weeklyChartHost);
        salesCharts.add(monthlyChartHost);

        javax.swing.JSplitPane salesSplit = new javax.swing.JSplitPane(javax.swing.JSplitPane.VERTICAL_SPLIT, salesCharts, salesScroll);
        salesSplit.setResizeWeight(0.58);
        salesSplit.setBorder(null);

        JPanel contentCards = new JPanel(new CardLayout());
        AppUI.applyPanelBackground(contentCards);
        contentCards.add(salesSplit, "Sales");
        contentCards.add(usersScroll, "Users");

        final ReportData[] latestReport = new ReportData[1];
        final String[] latestReportType = new String[]{"Sales"};

        JButton generate = new JButton("Generate Report");
        AppUI.stylePrimaryButton(generate);
        generate.setPreferredSize(new Dimension(190, 36));
        JButton export = new JButton("Export Report");
        styleSecondaryButton(export);
        export.setPreferredSize(new Dimension(180, 36));

        generate.addActionListener(e -> {
            LocalDate from;
            LocalDate to;
            try {
                from = LocalDate.parse(fromDate.getText().trim());
                to = LocalDate.parse(toDate.getText().trim());
            } catch (DateTimeParseException ex) {
                JOptionPane.showMessageDialog(panel, "Enter valid dates using yyyy-MM-dd.");
                return;
            }
            if (from.isAfter(to)) {
                JOptionPane.showMessageDialog(panel, "From date must be before or equal to To date.");
                return;
            }
            try {
                String selected = (String) reportType.getSelectedItem();
                CardLayout cardLayout = (CardLayout) contentCards.getLayout();
                if ("Users".equals(selected)) {
                    ReportData userData = buildUsersReportData(connection, search.getText().trim(), from, to);
                    populateReportTable(usersTableModel, userData);
                    cardLayout.show(contentCards, "Users");
                    latestReport[0] = userData;
                    latestReportType[0] = "users";
                } else if ("Movements".equals(selected)) {
                    ReportData movementData = buildMovementsReportData(connection, search.getText().trim(), from, to);
                    populateReportTable(usersTableModel, movementData);
                    cardLayout.show(contentCards, "Users");
                    latestReport[0] = movementData;
                    latestReportType[0] = "movements";
                } else {
                    ReportData salesData = buildSalesReportData(connection, search.getText().trim(), from, to);
                    renderReport(salesTableModel, weeklyChartHost, monthlyChartHost, salesData);
                    cardLayout.show(contentCards, "Sales");
                    latestReport[0] = salesData;
                    latestReportType[0] = "sales";
                }
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(panel, "Database error while building report: " + ex.getMessage(), "Report Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        export.addActionListener(e -> {
            if (latestReport[0] == null) {
                JOptionPane.showMessageDialog(panel, "Generate a report first, then export.");
                return;
            }
            String from = fromDate.getText().trim();
            String to = toDate.getText().trim();
            try {
                JFileChooser chooser = new JFileChooser();
                chooser.setDialogTitle("Choose export folder");
                chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
                if (chooser.showSaveDialog(panel) != JFileChooser.APPROVE_OPTION) {
                    return;
                }
                Path dir = chooser.getSelectedFile().toPath();
                Files.createDirectories(dir);

                String base = sanitizeFileName(System.currentTimeMillis() + "_" + user.getUsername() + "_" + latestReportType[0] + "_from_" + from + "_to_" + to);
                Path csv = dir.resolve(base + ".csv");
                writeReportCsv(csv, latestReport[0]);

                JOptionPane.showMessageDialog(panel, "Export complete:\\n" + csv.toString());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(panel, "Export failed: " + ex.getMessage(), "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        });

        JPanel top = new JPanel(new BorderLayout(12, 12));
        AppUI.applyPanelBackground(top);
        top.add(filterPanel, BorderLayout.NORTH);
        top.add(buildActionBar(export, generate), BorderLayout.SOUTH);

        panel.add(top, BorderLayout.NORTH);
        panel.add(contentCards, BorderLayout.CENTER);
        return panel;
    }

    /** Builds admin-only backup management panel (create, list, restore, prune, open folder, day-one reset). */
    private static JPanel buildBackupPanel(User user, Connection connection, AccountActions accountActions, JFrame frame) {
        ensureAdmin(user, "Create Local Backup");
        JPanel panel = buildFormPanel("Database Backups");
        JPanel content = buildSectionPanel();

        DefaultTableModel tableModel = new DefaultTableModel(new Object[]{"Backup folder"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(tableModel);
        installTableCopyMenu(table);
        JScrollPane scroll = new JScrollPane(table);
        scroll.setBorder(AppUI.newRoundedBorder(8));
        scroll.setPreferredSize(new Dimension(520, 220));

        Runnable refreshBackups = () -> {
            tableModel.setRowCount(0);
            try {
                for (String name : accountActions.listDatabaseBackupFolderNames()) {
                    tableModel.addRow(new Object[]{name});
                }
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Could not list backups: " + ex.getMessage());
            }
        };

        JButton refreshButton = new JButton("Refresh List");
        styleSecondaryButton(refreshButton);
        refreshButton.addActionListener(e -> refreshBackups.run());

        JButton backupNow = new JButton("Create backup now");
        AppUI.stylePrimaryButton(backupNow);
        backupNow.addActionListener(e -> new Thread(() -> runAction(() -> accountActions.backUpDatabase(user, frame)), "ims-backup").start());

        JButton openFolder = new JButton("Open backup folder");
        styleSecondaryButton(openFolder);
        openFolder.addActionListener(e -> new Thread(() -> runAction(() -> accountActions.openBackupsFolder(user, frame)), "ims-open-backup").start());

        JButton restoreButton = new JButton("Restore selected backup");
        AppUI.stylePrimaryButton(restoreButton);
        restoreButton.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row < 0) {
                JOptionPane.showMessageDialog(frame, "Select a backup folder in the table.");
                return;
            }
            String folder = (String) tableModel.getValueAt(row, 0);
            new Thread(() -> runAction(() -> accountActions.restoreDatabaseFromBackup(user, frame, folder)), "ims-restore").start();
        });

        JPanel pruneRow = new JPanel(new GridLayout(1, 3, 8, 8));
        AppUI.applyPanelBackground(pruneRow);
        JLabel pruneLabel = new JLabel("Delete backups older than (days):");
        JTextField pruneDays = new JTextField("30", 6);
        styleInput(pruneDays);
        JButton pruneButton = new JButton("Prune old backups");
        styleSecondaryButton(pruneButton);
        pruneRow.add(pruneLabel);
        pruneRow.add(pruneDays);
        pruneRow.add(pruneButton);
        pruneButton.addActionListener(e -> {
            try {
                int days = Integer.parseInt(pruneDays.getText().trim());
                new Thread(() -> runAction(() -> accountActions.pruneDatabaseBackups(user, frame, days)), "ims-prune").start();
            } catch (NumberFormatException ex) {
                JOptionPane.showMessageDialog(frame, "Enter a valid number of days.");
            }
        });

        content.add(buildSectionTitle("Backup copies"));
        content.add(Box.createVerticalStrut(6));
        content.add(buildSectionText("Backups live under database_backups. Restoring replaces the live database folder; restart the app afterward."));
        content.add(Box.createVerticalStrut(10));
        content.add(scroll);
        content.add(Box.createVerticalStrut(10));

        JPanel actions = new JPanel();
        actions.setLayout(new BoxLayout(actions, BoxLayout.Y_AXIS));
        AppUI.applyPanelBackground(actions);
        java.awt.FlowLayout flowLeft = new java.awt.FlowLayout(java.awt.FlowLayout.LEFT, 8, 0);
        JPanel row1 = new JPanel(flowLeft);
        AppUI.applyPanelBackground(row1);
        row1.add(refreshButton);
        row1.add(backupNow);
        row1.add(openFolder);
        actions.add(row1);
        actions.add(Box.createVerticalStrut(8));
        JPanel row2 = new JPanel(flowLeft);
        AppUI.applyPanelBackground(row2);
        row2.add(restoreButton);
        actions.add(row2);
        actions.add(Box.createVerticalStrut(8));
        actions.add(pruneRow);

        content.add(actions);
        content.add(Box.createVerticalStrut(24));
        content.add(buildSectionTitle("Factory reset (day one)"));
        content.add(Box.createVerticalStrut(6));
        content.add(buildSectionText(
                "Removes all inventory, sales, purchase orders, movements, login history, security audit rows, and all "
                        + "user accounts except Admin. Resets Admin password to firstLogin with forced password change on next sign-in. "
                        + "Deletes files in item_images/ only (company.txt and workspace_welcome.png in the project root are not touched)."
        ));
        content.add(Box.createVerticalStrut(10));
        JButton resetDayOne = new JButton("Reset database to day one…");
        resetDayOne.setForeground(new Color(0xb91c1c));
        styleSecondaryButton(resetDayOne);
        resetDayOne.addActionListener(e -> {
            int ok = JOptionPane.showConfirmDialog(
                    frame,
                    "This permanently deletes business data and extra users.\n\n"
                            + "• Admin stays; password becomes: firstLogin (must change on next login)\n"
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
            try {
                DatabaseManager.resetEnterpriseDataToDayOne(connection);
                refreshActiveMetricsStripNow();
                JOptionPane.showMessageDialog(
                        frame,
                        "Reset complete. Log out, then sign in as Admin with password: firstLogin\n"
                                + "(you will be prompted to set a new password)."
                );
            } catch (SQLException ex) {
                JOptionPane.showMessageDialog(frame, "Database reset failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Item images cleanup failed: " + ex.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        });
        JPanel resetRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        AppUI.applyPanelBackground(resetRow);
        resetRow.add(resetDayOne);
        content.add(resetRow);

        panel.add(content, BorderLayout.NORTH);
        refreshBackups.run();
        return panel;
    }

    /**
     * Queries sales data, aggregates metrics, and prepares chart points.
     *
     * @param connection active database connection
     * @param search free-text search filter (item code, name, reference, or transaction note)
     * @param from inclusive start date
     * @param to inclusive end date
     * @return computed sales report data
     * @throws SQLException when query fails
     */
    private static ReportData buildSalesReportData(Connection connection, String search, LocalDate from, LocalDate to) throws SQLException {
        ReportData data = new ReportData("Sales Report");
        String sql = "SELECT \"Item Code\", \"Item Name\", \"Amount\", \"Total Price\", \"Total Cost\", \"Reference\", \"User\", \"Date\", COALESCE(\"Note\", '') AS \"Note\" "
                + "FROM sales WHERE \"DateISO\" BETWEEN ? AND ? "
                + "AND (? = '' OR lower(\"Item Code\") LIKE lower(?) OR lower(\"Item Name\") LIKE lower(?) OR lower(\"Reference\") LIKE lower(?) "
                + "OR lower(COALESCE(\"Note\", '')) LIKE lower(?))";

        int rowCount = 0;
        int totalUnits = 0;
        double totalRevenue = 0;
        double totalCost = 0;
        Map<String, Double> revenueByWeek = new HashMap<>();
        Map<String, Double> revenueByMonth = new HashMap<>();

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String like = "%" + search + "%";
            ps.setString(1, from.toString() + " 00:00:00");
            ps.setString(2, to.toString() + " 23:59:59");
            ps.setString(3, search);
            ps.setString(4, like);
            ps.setString(5, like);
            ps.setString(6, like);
            ps.setString(7, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    rowCount++;
                    int amount = rs.getInt("Amount");
                    double price = rs.getDouble("Total Price");
                    double cost = rs.getDouble("Total Cost");
                    totalUnits += amount;
                    totalRevenue += price;
                    totalCost += cost;
                    data.rows.add(new Object[]{
                            rs.getString("Item Code"),
                            rs.getString("Item Name"),
                            amount,
                            price,
                            cost,
                            price - cost,
                            rs.getString("Reference"),
                            rs.getString("User"),
                            rs.getString("Date"),
                            rs.getString("Note")
                    });
                    String day = toIsoDay(rs.getString("Date"));
                    String week = toIsoWeek(day);
                    String month = toIsoMonth(day);
                    revenueByWeek.put(week, revenueByWeek.getOrDefault(week, 0.0) + price);
                    revenueByMonth.put(month, revenueByMonth.getOrDefault(month, 0.0) + price);
                }
            }
        }

        data.columns = new String[]{
                "Item Code", "Item Name", "Amount", "Total Price", "Total Cost", "Gross Profit", "Reference", "User", "Date", "Note"
        };
        data.summary.put("Rows", String.valueOf(rowCount));
        data.summary.put("Total Units Sold", String.valueOf(totalUnits));
        data.summary.put("Total Revenue", formatUsdMoney(totalRevenue));
        data.summary.put("Total Cost", formatUsdMoney(totalCost));
        data.summary.put("Gross Profit", formatUsdMoney(totalRevenue - totalCost));
        data.summary.put("Date Range", from + " to " + to);
        data.weeklyChartTitle = "Revenue by Week";
        data.weeklyChartPoints = sortPoints(revenueByWeek);
        data.monthlyChartTitle = "Revenue by Month";
        data.monthlyChartPoints = sortPoints(revenueByMonth);
        return data;
    }

    /**
     * Queries user/security event data for the users report table.
     *
     * @param connection active database connection
     * @param search free-text search filter
     * @param from inclusive start date
     * @param to inclusive end date
     * @return computed users report data
     * @throws SQLException when query fails
     */
    private static ReportData buildUsersReportData(Connection connection, String search, LocalDate from, LocalDate to) throws SQLException {
        ReportData data = new ReportData("Users Report");
        String normalizedAuditDate = "(substr(created_at, 7, 4) || '-' || substr(created_at, 4, 2) || '-' || substr(created_at, 1, 2))";
        String normalizedLoginDate = "(substr(Time, 7, 4) || '-' || substr(Time, 4, 2) || '-' || substr(Time, 1, 2))";
        String sql =
                "SELECT username, event_type, details, created_at " +
                "FROM security_audit " +
                "WHERE " + normalizedAuditDate + " BETWEEN ? AND ? " +
                "AND (? = '' OR lower(username) LIKE lower(?) OR lower(event_type) LIKE lower(?) OR lower(details) LIKE lower(?)) " +
                "UNION ALL " +
                "SELECT Name AS username, 'LOGIN' AS event_type, 'Login record' AS details, Time AS created_at " +
                "FROM Logins " +
                "WHERE " + normalizedLoginDate + " BETWEEN ? AND ? " +
                "AND (? = '' OR lower(Name) LIKE lower(?)) " +
                "ORDER BY created_at DESC";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String like = "%" + search + "%";
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            ps.setString(3, search);
            ps.setString(4, like);
            ps.setString(5, like);
            ps.setString(6, like);
            ps.setString(7, from.toString());
            ps.setString(8, to.toString());
            ps.setString(9, search);
            ps.setString(10, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    data.rows.add(new Object[]{
                            rs.getString("username"),
                            rs.getString("event_type"),
                            rs.getString("details"),
                            rs.getString("created_at")
                    });
                }
            }
        }

        data.columns = new String[]{"Username", "Event Type", "Details", "Date"};
        return data;
    }

    /**
     * Queries inventory movement data for audit-style movement reporting.
     *
     * @param connection active database connection
     * @param search free-text search filter
     * @param from inclusive start date
     * @param to inclusive end date
     * @return computed movement report data
     * @throws SQLException when query fails
     */
    private static ReportData buildMovementsReportData(Connection connection, String search, LocalDate from, LocalDate to) throws SQLException {
        ReportData data = new ReportData("Movements Report");
        String normalizedDate = "(substr(`Date`, 7, 4) || '-' || substr(`Date`, 4, 2) || '-' || substr(`Date`, 1, 2))";
        String sql =
                "SELECT `Item`, `Amount`, `Type`, COALESCE(`Reason`, '') AS `Reason`, `User`, `Date` " +
                "FROM movements " +
                "WHERE " + normalizedDate + " BETWEEN ? AND ? " +
                "AND (? = '' OR lower(`Item`) LIKE lower(?) OR lower(`Type`) LIKE lower(?) OR lower(COALESCE(`Reason`, '')) LIKE lower(?) OR lower(`User`) LIKE lower(?)) " +
                "ORDER BY `Date` DESC";

        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            String like = "%" + search + "%";
            ps.setString(1, from.toString());
            ps.setString(2, to.toString());
            ps.setString(3, search);
            ps.setString(4, like);
            ps.setString(5, like);
            ps.setString(6, like);
            ps.setString(7, like);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    data.rows.add(new Object[]{
                            rs.getString("Item"),
                            rs.getInt("Amount"),
                            rs.getString("Type"),
                            rs.getString("Reason"),
                            rs.getString("User"),
                            rs.getString("Date")
                    });
                }
            }
        }

        data.columns = new String[]{"Item Code", "Amount", "Type", "Reason", "User", "Date"};
        return data;
    }

    /** Converts display datetime text into ISO day format for grouping. */
    private static String toIsoDay(String raw) {
        if (raw == null || raw.length() < 10) {
            return "Unknown";
        }
        return raw.substring(6, 10) + "-" + raw.substring(3, 5) + "-" + raw.substring(0, 2);
    }

    /** Converts ISO day string into ISO week key. */
    private static String toIsoWeek(String isoDay) {
        if ("Unknown".equals(isoDay)) {
            return "Unknown";
        }
        LocalDate d = LocalDate.parse(isoDay);
        WeekFields wf = WeekFields.ISO;
        int week = d.get(wf.weekOfWeekBasedYear());
        int year = d.get(wf.weekBasedYear());
        return String.format("%d-W%02d", year, week);
    }

    /** Converts ISO day string into year-month key. */
    private static String toIsoMonth(String isoDay) {
        if ("Unknown".equals(isoDay)) {
            return "Unknown";
        }
        return isoDay.substring(0, 7);
    }

    /** Converts display datetime (dd-MM-yyyy HH:mm:ss) into ISO datetime. */
    private static String toIsoDateTime(String displayDateTime) {
        if (displayDateTime == null || displayDateTime.length() < 19) {
            return null;
        }
        return displayDateTime.substring(6, 10) + "-" +
                displayDateTime.substring(3, 5) + "-" +
                displayDateTime.substring(0, 2) +
                displayDateTime.substring(10);
    }

    /** Sorts chart points by key for consistent chart ordering. */
    private static List<Map.Entry<String, Double>> sortPoints(Map<String, Double> map) {
        List<Map.Entry<String, Double>> points = new ArrayList<>(map.entrySet());
        points.sort(Comparator.comparing(Map.Entry::getKey));
        return points;
    }

    /** Renders report table and weekly/monthly chart panels. */
    private static void renderReport(DefaultTableModel model, JPanel weeklyChartHost, JPanel monthlyChartHost, ReportData data) {
        populateReportTable(model, data);

        weeklyChartHost.removeAll();
        weeklyChartHost.add(buildJFreeBarChartPanel(data.weeklyChartTitle, data.weeklyChartPoints), BorderLayout.CENTER);
        weeklyChartHost.revalidate();
        weeklyChartHost.repaint();

        monthlyChartHost.removeAll();
        monthlyChartHost.add(buildJFreeBarChartPanel(data.monthlyChartTitle, data.monthlyChartPoints), BorderLayout.CENTER);
        monthlyChartHost.revalidate();
        monthlyChartHost.repaint();
    }

    /** Replaces table model columns/rows with report output. */
    private static void populateReportTable(DefaultTableModel model, ReportData data) {
        model.setColumnCount(0);
        for (String col : data.columns) {
            model.addColumn(col);
        }
        model.setRowCount(0);
        for (Object[] row : data.rows) {
            model.addRow(row);
        }
    }

    /** Sanitizes filename tokens for export-safe output paths. */
    private static String sanitizeFileName(String input) {
        return input.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    /**
     * Writes report table data to CSV with quoted cells.
     *
     * @param path output CSV path
     * @param data report data to export
     * @throws IOException when writing fails
     */
    private static void writeReportCsv(Path path, ReportData data) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(path)) {
            writer.write(String.join(",", data.columns));
            writer.newLine();
            for (Object[] row : data.rows) {
                StringBuilder line = new StringBuilder();
                for (int i = 0; i < row.length; i++) {
                    if (i > 0) {
                        line.append(',');
                    }
                    String cell = row[i] == null ? "" : row[i].toString().replace("\"", "\"\"");
                    line.append('"').append(cell).append('"');
                }
                writer.write(line.toString());
                writer.newLine();
            }
        }
    }

    /** Builds a dark-themed, non-zoomable bar chart panel. */
    private static ChartPanel buildJFreeBarChartPanel(String title, List<Map.Entry<String, Double>> points) {
        DefaultCategoryDataset dataset = new DefaultCategoryDataset();
        if (points != null) {
            for (Map.Entry<String, Double> entry : points) {
                dataset.addValue(entry.getValue(), "Sales", entry.getKey());
            }
        }

        JFreeChart chart = ChartFactory.createBarChart(
                title,
                "",
                "Value",
                dataset,
                PlotOrientation.VERTICAL,
                false,
                true,
                false
        );

        CategoryPlot plot = chart.getCategoryPlot();
        chart.setBackgroundPaint(new java.awt.Color(241, 245, 249));
        TextTitle titleObj = chart.getTitle();
        if (titleObj != null) {
            titleObj.setPaint(new java.awt.Color(30, 41, 59));
        }
        plot.setBackgroundPaint(new java.awt.Color(255, 255, 255));
        plot.setDomainGridlinePaint(new java.awt.Color(203, 213, 225));
        plot.setRangeGridlinePaint(new java.awt.Color(203, 213, 225));
        plot.getDomainAxis().setTickLabelPaint(new java.awt.Color(30, 41, 59));
        plot.getDomainAxis().setLabelPaint(new java.awt.Color(30, 41, 59));
        plot.getRangeAxis().setTickLabelPaint(new java.awt.Color(30, 41, 59));
        plot.getRangeAxis().setLabelPaint(new java.awt.Color(30, 41, 59));

        BarRenderer renderer = (BarRenderer) plot.getRenderer();
        renderer.setSeriesPaint(0, new java.awt.Color(59, 130, 246));
        renderer.setMaximumBarWidth(0.08);

        CategoryAxis domainAxis = plot.getDomainAxis();
        domainAxis.setMaximumCategoryLabelLines(2);
        NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createStandardTickUnits());

        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setMouseWheelEnabled(false);
        chartPanel.setDomainZoomable(false);
        chartPanel.setRangeZoomable(false);
        chartPanel.setMouseZoomable(false);
        chartPanel.setBackground(new java.awt.Color(241, 245, 249));
        return chartPanel;
    }

    /** Creates a standard form container panel with section heading. */
    private static JPanel buildFormPanel(String title) {
        JPanel panel = new JPanel(new BorderLayout(12, 12));
        panel.setBorder(BorderFactory.createEmptyBorder(22, 22, 22, 22));
        AppUI.applyPanelBackground(panel);
        JLabel heading = buildSectionTitle(title);
        panel.add(heading, BorderLayout.NORTH);
        return panel;
    }

    /** Creates a vertically stacked section panel for form content. */
    private static JPanel buildSectionPanel() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        AppUI.applyPanelBackground(panel);
        return panel;
    }

    /** Creates a section title label with consistent heading style. */
    private static JLabel buildSectionTitle(String text) {
        JLabel heading = new JLabel(text, SwingConstants.LEFT);
        heading.setFont(heading.getFont().deriveFont(Font.BOLD, 21f));
        return heading;
    }

    /** Creates a section body text label with standard style. */
    private static JLabel buildSectionText(String text) {
        JLabel label = new JLabel(text, SwingConstants.LEFT);
        label.setFont(label.getFont().deriveFont(Font.PLAIN, 13f));
        return label;
    }

    /** Creates a left/right aligned action button bar. */
    private static JPanel buildActionBar(JButton leftButton, JButton rightButton) {
        JPanel actionBar = new JPanel(new BorderLayout());
        actionBar.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));
        AppUI.applyPanelBackground(actionBar);
        if (leftButton != null) {
            actionBar.add(leftButton, BorderLayout.WEST);
        }
        if (rightButton != null) {
            actionBar.add(rightButton, BorderLayout.EAST);
        }
        return actionBar;
    }

    /** Applies shared secondary button styling. */
    private static void styleSecondaryButton(JButton button) {
        button.setBorder(AppUI.newRoundedBorder(8));
        button.setFocusPainted(false);
    }

    /** Rebuilds draft line table rows from map-backed values. */
    private static void refreshSaleDraftTable(DefaultTableModel model, Map<String, SaleDraftLine> map) {
        model.setRowCount(0);
        for (Map.Entry<String, SaleDraftLine> entry : map.entrySet()) {
            SaleDraftLine line = entry.getValue();
            model.addRow(new Object[]{entry.getKey(), line.quantity, line.unitSalePrice});
        }
    }

    /** Rebuilds Add Item draft table from list order. */
    private static void refreshAddItemDraftTable(DefaultTableModel model, List<AddItemDraftLine> lines) {
        model.setRowCount(0);
        for (AddItemDraftLine line : lines) {
            String notesCell = line.notes.isEmpty() ? "" : abbreviateForTableCell(line.notes, 48);
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
    }

    /** Single-line preview for draft tables; newlines become spaces and long text ends with an ellipsis. */
    private static String abbreviateForTableCell(String text, int maxChars) {
        String t = text.replace('\n', ' ').trim();
        if (t.length() <= maxChars) {
            return t;
        }
        return t.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    /**
     * Inserts one new inventory row, optional FIFO layer for initial stock, and movement log.
     *
     * @param supplierValue trimmed supplier; empty becomes SQL NULL
     * @param notesValue    optional item note (trimmed; empty becomes SQL NULL); capped to ITEM_NOTES_MAX_CHARS
     */
    private static void insertNewInventoryItemRow(
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
        if (notesForDb.length() > ITEM_NOTES_MAX_CHARS) {
            notesForDb = notesForDb.substring(0, ITEM_NOTES_MAX_CHARS);
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
        if (stockCount > 0) {
            try (PreparedStatement addLayer = connection.prepareStatement(
                    "INSERT INTO inventory_cost_layers (item_code, reference, unit_cost, qty_received, qty_remaining, created_at) VALUES (?, ?, ?, ?, ?, ?)"
            )) {
                addLayer.setString(1, itemCodeValue);
                addLayer.setString(2, "INITIAL_STOCK");
                addLayer.setDouble(3, 0);
                addLayer.setInt(4, stockCount);
                addLayer.setInt(5, stockCount);
                addLayer.setString(6, new dateTime().formattedDateTime());
                addLayer.executeUpdate();
            }
        }
        try (PreparedStatement movement = connection.prepareStatement(
                "INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            movement.setString(1, itemCodeValue);
            movement.setString(2, String.valueOf(stockCount));
            movement.setString(3, "ADD");
            movement.setString(4, "INITIAL_STOCK");
            movement.setString(5, user.getUsername());
            movement.setString(6, new dateTime().formattedDateTime());
            movement.executeUpdate();
        }
    }

    /** Loads pending order lines into the supplied table model. */
    private static void loadPendingOrders(DefaultTableModel model, Connection connection) throws SQLException {
        model.setRowCount(0);
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT `Item Code`, `Amount`, `Purchase Price`, `Reference`, `Date` FROM pendingOrders")) {
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getString("Item Code"),
                        rs.getInt("Amount"),
                        rs.getDouble("Purchase Price"),
                        rs.getString("Reference"),
                        rs.getString("Date")
                });
            }
        }
    }

    /**
     * Loads historical write-off movements into the supplied table model.
     *
     * @param model target table model
     * @param connection active database connection
     * @throws SQLException when query fails
     */
    private static void loadWriteOffHistory(DefaultTableModel model, Connection connection) throws SQLException {
        model.setRowCount(0);
        String sql =
                "SELECT `Item`, `Amount`, COALESCE(`Reason`, '') AS `Reason`, `User`, `Date` " +
                "FROM movements " +
                "WHERE `Type` = 'WRITE OFF' " +
                "ORDER BY `Date` DESC";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                model.addRow(new Object[]{
                        rs.getString("Item"),
                        rs.getInt("Amount"),
                        rs.getString("Reason"),
                        rs.getString("User"),
                        rs.getString("Date")
                });
            }
        }
    }

    /** Installs right-click copy-cell menu behavior for a table. */
    private static void installTableCopyMenu(JTable table) {
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
    }

    /** Copies selected table cell text to the system clipboard. */
    private static void copySelectedCell(JTable table) {
        int row = table.getSelectedRow();
        int col = table.getSelectedColumn();
        if (row < 0 || col < 0) {
            return;
        }
        Object value = table.getValueAt(row, col);
        String text = value == null ? "" : value.toString();
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
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
    private static int getIntValue(Connection connection, String query, String... params) throws SQLException {
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

    /**
     * Applies receive updates: increments sellable Stock, records FIFO layer, clears completed PO lines.
     *
     * @param user active signed-in user
     * @param connection active database connection
     * @param referenceNumber purchase order reference
     * @param codeReceived item code received
     * @param amountReceived quantity received
     * @param purchasePrice received unit cost
     * @throws SQLException when updates fail
     */
    private static void applyReceive(User user, Connection connection, String referenceNumber, String codeReceived, int amountReceived, double purchasePrice) throws SQLException {
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
        try (PreparedStatement addLayer = connection.prepareStatement(
                "INSERT INTO inventory_cost_layers (item_code, reference, unit_cost, qty_received, qty_remaining, created_at) VALUES (?, ?, ?, ?, ?, ?)"
        )) {
            addLayer.setString(1, codeReceived);
            addLayer.setString(2, referenceNumber);
            addLayer.setDouble(3, purchasePrice);
            addLayer.setInt(4, amountReceived);
            addLayer.setInt(5, amountReceived);
            addLayer.setString(6, new dateTime().formattedDateTime());
            addLayer.executeUpdate();
        }
        try (PreparedStatement movement = connection.prepareStatement("INSERT INTO movements (`Item`, `Amount`, `Type`, `Reason`, `User`, `Date`) VALUES (?, ?, ?, ?, ?, ?)")) {
            movement.setString(1, codeReceived);
            movement.setString(2, String.valueOf(amountReceived));
            movement.setString(3, "RECEIVED");
            movement.setString(4, "PURCHASE_ORDER_RECEIPT");
            movement.setString(5, user.getUsername());
            movement.setString(6, new dateTime().formattedDateTime());
            movement.executeUpdate();
        }
        try (PreparedStatement deleteCompletedOrder = connection.prepareStatement("DELETE FROM pendingOrders WHERE `Reference` = ? AND `Item Code` = ? AND Amount = 0")) {
            deleteCompletedOrder.setString(1, referenceNumber);
            deleteCompletedOrder.setString(2, codeReceived);
            deleteCompletedOrder.executeUpdate();
        }
    }

    /** Applies shared rounded-border styling to input fields. */
    private static void styleInput(JTextField... fields) {
        for (JTextField field : fields) {
            field.setBorder(AppUI.newRoundedBorder(8));
            field.setPreferredSize(new Dimension(field.getPreferredSize().width, INPUT_HEIGHT));
        }
    }

    /** Compact height for Add Item line entry fields. */
    private static void styleInputCompact(JTextField... fields) {
        for (JTextField field : fields) {
            field.setBorder(AppUI.newRoundedBorder(6));
            field.setPreferredSize(new Dimension(field.getPreferredSize().width, ADD_ITEM_INPUT_HEIGHT));
        }
    }

    /**
     * Applies consistent border and control height styling for password fields.
     *
     * @param fields password field controls to style
     */
    private static void stylePasswordInput(JPasswordField... fields) {
        for (JPasswordField field : fields) {
            field.setBorder(AppUI.newRoundedBorder(8));
            field.setPreferredSize(new Dimension(field.getPreferredSize().width, INPUT_HEIGHT));
        }
    }

    /**
     * Returns the next available sequential item code in ITM0001-ITM9999 format.
     *
     * @param connection active database connection
     * @return next available item code
     * @throws SQLException when query fails or all item codes are exhausted
     */
    private static String getNextItemCode(Connection connection) throws SQLException {
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
    private static void runAction(CheckedAction action) {
        try {
            action.run();
        } catch (SecurityException e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Access Denied", JOptionPane.WARNING_MESSAGE);
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, "Unable to complete action: " + e.getMessage(), "Action Error", JOptionPane.ERROR_MESSAGE);
        }
    }

    /** Ensures admin rights before opening restricted workflows. */
    private static void ensureAdmin(User user, String actionName) {
        if (user == null || !user.hasAdminRights()) {
            throw new SecurityException("Access denied. Administrator rights are required for: " + actionName);
        }
    }

    /** Closes database connection and suppresses shutdown-time failures. */
    private static void closeConnectionQuietly(Connection connection) {
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

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    private static final class NavItem {
        private final String label;
        private final CheckedAction action;
        private final ViewBuilder viewBuilder;

        private NavItem(String label, CheckedAction action) {
            this.label = label;
            this.action = action;
            this.viewBuilder = null;
        }

        private NavItem(String label, ViewBuilder viewBuilder) {
            this.label = label;
            this.action = () -> {};
            this.viewBuilder = viewBuilder;
        }
    }

    @FunctionalInterface
    private interface ViewBuilder {
        JPanel build() throws SQLException;
    }

    private static final class ReportData {
        private final String title;
        private final Map<String, String> summary = new LinkedHashMap<>();
        private String[] columns = new String[0];
        private final List<Object[]> rows = new ArrayList<>();
        private String weeklyChartTitle = "";
        private List<Map.Entry<String, Double>> weeklyChartPoints = new ArrayList<>();
        private String monthlyChartTitle = "";
        private List<Map.Entry<String, Double>> monthlyChartPoints = new ArrayList<>();

        private ReportData(String title) {
            this.title = title;
        }
    }

}
