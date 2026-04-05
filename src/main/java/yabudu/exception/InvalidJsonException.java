package yabudu.exception;

// Ошибка JSON не распарсился
public class InvalidJsonException extends RuntimeException {

    public InvalidJsonException(Throwable cause) {
        super("Invalid JSON", cause);
    }
}
