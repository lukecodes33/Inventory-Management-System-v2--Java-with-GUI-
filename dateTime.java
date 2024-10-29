import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Utility class for handling date and time operations.
 *
 * This class provides methods to retrieve the current date and time
 * in a formatted string representation.
 */

public class dateTime {

    /**
     * Retrieves the current date and time formatted as a string.
     *
     * The date and time are formatted in the pattern "dd-MM-yyyy HH:mm:ss".
     *
     * @return A string representing the current date and time in the specified format.
     */

    public String formattedDateTime() {
        LocalDateTime dateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

        return dateTime.format(formatter);
    }

}