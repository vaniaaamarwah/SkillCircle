package database;

// Thrown when something goes wrong while using the database.
// It is an UNCHECKED exception (it extends RuntimeException), so methods do not
// need "throws" everywhere. The message is written for the user, so a screen can
// show it directly in a dialog. The original technical error is kept as the "cause".
public class DataAccessException extends RuntimeException {

    public DataAccessException(String message) {
        super(message);
    }

    public DataAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
