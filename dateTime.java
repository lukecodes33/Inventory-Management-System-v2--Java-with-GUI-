import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Display-format timestamps for DB and UI audit fields ({@code dd-MM-yyyy HH:mm:ss}).
 * Prefer {@link #nowDisplayString()} to avoid allocating a throwaway instance.
 */
public final class dateTime {

    private static final DateTimeFormatter DISPLAY = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

    /**
     * Legacy no-arg constructor; prefer {@link #nowDisplayString()} to avoid extra allocation.
     */
    public dateTime() {
    }

    /**
     * Current wall-clock time in application display format.
     *
     * @return formatted {@code dd-MM-yyyy HH:mm:ss} string
     */
    public static String nowDisplayString() {
        return LocalDateTime.now().format(DISPLAY);
    }

    /**
     * Same output as {@link #nowDisplayString()}; retained for callers that still use {@code new dateTime()}.
     *
     * @return formatted {@code dd-MM-yyyy HH:mm:ss} string
     */
    public String formattedDateTime() {
        return nowDisplayString();
    }

}