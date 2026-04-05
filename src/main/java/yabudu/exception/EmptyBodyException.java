package yabudu.exception;

// Ошибка тело запроса пустое
public class EmptyBodyException extends RuntimeException {

    public EmptyBodyException() {
        super("Request body is empty");
    }
}
