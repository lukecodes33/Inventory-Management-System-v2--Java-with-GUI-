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
     *
     * @deprecated prefer {@link #nowDisplayString()}; retained for older {@link #formattedDateTime()} chaining
     */
    @Deprecated(forRemoval = false)
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
     * Same output as {@link #nowDisplayString()}; retained only for callers that still use {@code new dateTime()}.
     *
     * @return formatted {@code dd-MM-yyyy HH:mm:ss} string
     * @deprecated prefer {@link #nowDisplayString()} for static callers
     */
    @Deprecated(forRemoval = false)
    public String formattedDateTime() {
        return nowDisplayString();
    }

}