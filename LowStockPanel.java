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
public final class LowStockPanel {
    private LowStockPanel() {}

        /**
     * Builds low-stock and replenishment recommendation view.
     *
     * @param connection active database connection
     * @return replenishment panel
     * @throws SQLException when query fails
     */
    public static JPanel build(Connection connection) throws SQLException {
        return WorkspaceShell.buildFilterableTablePanel(
                "Low Stock - Items will show here if their stock levels fall below the re order trigger",
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
                connection,
                null,
                null,
                false,
                true,
                null
        );
    }
}
