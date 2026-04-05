package yabudu.exception;

// Ошибка не передали обязательный query параметр
public class MissingRequestParamException extends RuntimeException {

    public MissingRequestParamException(String paramName) {
        super("Missing query param: " + paramName);
    }
}
