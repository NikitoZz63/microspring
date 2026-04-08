package yabudu.jdbc;

import java.lang.reflect.Field;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;

public class AutoRowMapper<T> implements RowMapper<T> {
    private final Class<T> clazz;

    public AutoRowMapper(Class<T> clazz) {
        this.clazz = clazz;
    }

    @Override
    public T mapRow(ResultSet rs) throws SQLException {
        try {
            // создаём новый объект (например User)
            T obj = clazz.getDeclaredConstructor().newInstance();

            // получаем описание колонок
            ResultSetMetaData metaData = rs.getMetaData();
            // сколько колонок в SELECT
            int columnCount = metaData.getColumnCount();

            for (int i = 1; i <= columnCount; i++) {
                String columnName = metaData.getColumnLabel(i).toLowerCase();
                Object value = rs.getObject(i);

                // Преобразуем (user_id - userId)
                String fieldName = toCamelCase(columnName);

                Field field;
                try {
                    field = clazz.getDeclaredField(fieldName);
                } catch (NoSuchFieldException e) {
                    // если поля нет — пропускаем (например лишняя колонка)
                    continue;
                }
                field.setAccessible(true);
                if (value != null) {
                    Class<?> fieldType = field.getType();

                    // Приводим типы из ResultSet к типам полей
                    if (fieldType == Long.class || fieldType == long.class) {
                        field.set(obj, ((Number) value).longValue());
                    } else if (fieldType == Integer.class || fieldType == int.class) {
                        field.set(obj, ((Number) value).intValue());
                    } else if (fieldType == String.class) {
                        field.set(obj, value.toString());
                    } else {
                        field.set(obj, value);
                    }
                }
            }
            return obj;
        } catch (Exception e) {
            throw new RuntimeException("Error mapping row to object", e);
        }
    }

    // Преобразует  в camelCase
    // user_id - userId
    private String toCamelCase(String columnName) {
        StringBuilder result = new StringBuilder();
        boolean toUpper = false;

        for (char c : columnName.toCharArray()) {
            if (c == '_') {
                toUpper = true;
            } else {
                result.append(toUpper ? Character.toUpperCase(c) : c);
                toUpper = false;
            }
        }

        return result.toString();
    }
}
