import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class dateTime {

    public String formattedDateTime() {
        LocalDateTime dateTime = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

        return dateTime.format(formatter);
    }

}