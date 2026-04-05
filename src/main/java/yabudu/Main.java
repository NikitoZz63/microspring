package yabudu;

import yabudu.server.DispatcherServlet;
import yabudu.testClasses.User;

import javax.sql.DataSource;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public class Main {
    public static void main(String[] args) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, IOException, SQLException {
        // сканируем всю базовую папку, а не только testClasses
        ApplicationContext context = new ApplicationContext("yabudu");
        // сначала получаем бин и вызываем методы
        User user = context.getBean(User.class);
        user.methodA();
        // потом закрываем контекст (вызов @PreDestroy)
        context.close();
        new DispatcherServlet(context);

        DataSource ds = context.getBean(DataSource.class);

        Connection cn = ds.getConnection();
        Statement stmt = cn.createStatement();

        stmt.execute("""
                CREATE TABLE users (
                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                     name VARCHAR(255)
                 );
                
                CREATE TABLE tasks (
                     id BIGINT AUTO_INCREMENT PRIMARY KEY,
                     title VARCHAR(255),
                     user_id BIGINT,
                     FOREIGN KEY (user_id) REFERENCES users(id)
                 );
                """);

        stmt.close();
        cn.close();

    }
}