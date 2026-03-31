package yabudu.jdbc;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

public class MyJdbcTemplate {

    private final DataSource dataSource;

    public MyJdbcTemplate(DataSource dataSource) {
        this.dataSource = dataSource;
    }


     // Получаем connection
     // если есть транзакция - берём из ThreadLocal
     // если нет - берём новый из DataSource

    private Connection getConnection() throws SQLException {
        Connection connection = ConnectionHolder.getConnection();
        if (connection != null) {
            return connection;
        }
        return dataSource.getConnection();
    }



}
