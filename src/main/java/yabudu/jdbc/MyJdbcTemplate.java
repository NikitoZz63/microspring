package yabudu.jdbc;

import yabudu.annotation.MyAutowired;
import yabudu.annotation.MyComponent;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@MyComponent
public class MyJdbcTemplate {

    @MyAutowired
    private DataSource dataSource;

    public MyJdbcTemplate() {
    }

    // Получаем connection
    // если есть транзакция - берём из ThreadLocal
    // если нет берём новый из DataSource
    private Connection getConnection() throws SQLException {
        Connection connection = ConnectionHolder.getConnection();
        if (connection != null) {
            return connection;
        }
        return dataSource.getConnection();
    }

    public <T> List<T> query(String sql, Object[] params, RowMapper<T> mapper) throws SQLException {
        // Список для хранения результата (объектов, которые мы соберём из ResultSet)
        List<T> result = new ArrayList<>();

        // Получаем connection (либо из транзакции, либо новый)
        Connection conn = getConnection();

        // Есть ли активная транзакция (если да — connection закрывать нельзя)
        boolean isTransactional = (ConnectionHolder.getConnection() != null);

        // Создаём PreparedStatement на основе SQL запроса
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Устанавливаем параметры в SQL (подставляем значения вместо "?")
            setParams(stmt, params);

            // Выполняем SELECT запрос - получаем ResultSet (табличный результат)
            try (ResultSet rs = stmt.executeQuery()) {

                // Проходим по всем строкам результата
                while (rs.next()) {

                    // mapper превращает одну строку ResultSet в объект (например User или Task)
                    T obj = mapper.mapRow(rs);

                    // Добавляем объект в итоговый список
                    result.add(obj);
                }
            }

        } catch (Exception e) {

            // Любую ошибку оборачиваем в RuntimeException
            throw new RuntimeException(e);

        } finally {
            // Если мы не внутри транзакции - закрываем connection
            // Если внутри — закрывать нельзя (его закроет транзакция)
            if (!isTransactional) {
                conn.close();
            }
        }

        // Возвращаем список объектов
        return result;
    }

    // INSERT / UPDATE / DELETE
    public int update(String sql, Object[] params) throws SQLException {
        // Получаем connection (из транзакции или новый)
        Connection conn = getConnection();

        // Проверяем, есть ли активная транзакция
        boolean isTransactional = (ConnectionHolder.getConnection() != null);

        // Создаём PreparedStatement для INSERT / UPDATE / DELETE
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {

            // Подставляем параметры вместо "?"
            setParams(stmt, params);

            // Выполняем изменение в базе
            // Возвращает количество изменённых строк
            return stmt.executeUpdate();

        } catch (Exception e) {
            // Оборачиваем ошибку
            throw new RuntimeException(e);

        } finally {
            // Закрываем connection только если нет транзакции
            if (!isTransactional) {
                conn.close();
            }
        }
    }


    // Установка параметров в PreparedStatement
    private void setParams(PreparedStatement stmt, Object[] params) throws SQLException {
        // Если параметров нет — ничего не делаем
        if (params == null) return;

        // Проходим по всем параметрам
        for (int i = 0; i < params.length; i++) {

            // Устанавливаем параметр в PreparedStatement
            stmt.setObject(i + 1, params[i]);
        }
    }

}
