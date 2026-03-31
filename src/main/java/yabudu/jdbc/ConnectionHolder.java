package yabudu.jdbc;

import java.sql.Connection;

// хранилище для текущего потока
public class ConnectionHolder {

    // У каждого потока свое значение
    private static final ThreadLocal<Connection> connectionHolder = new ThreadLocal<>();

    public static void setConnection(Connection connection) {
        connectionHolder.set(connection);
    }

    public static Connection getConnection() {
        return connectionHolder.get();
    }

    public static void clear() {
        connectionHolder.remove();
    }

}
