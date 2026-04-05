package yabudu.exception;

// Ошибка не передали path variable (/users/{id})
public class MissingPathVariableException extends RuntimeException {

    public MissingPathVariableException(String variableName) {
        super("Missing path variable: " + variableName);
    }
}
