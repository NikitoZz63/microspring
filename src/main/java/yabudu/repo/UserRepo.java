package yabudu.repo;

import yabudu.annotation.MyAutowired;
import yabudu.annotation.MyComponent;
import yabudu.dto.User;
import yabudu.jdbc.AutoRowMapper;
import yabudu.jdbc.MyJdbcTemplate;

import java.sql.SQLException;
import java.util.List;

@MyComponent
public class UserRepo {

    @MyAutowired
    private MyJdbcTemplate myJdbcTemplate;

    public void createUser(User user) throws SQLException {
        myJdbcTemplate.update(
                "INSERT INTO users(name) VALUES (?)",
                new Object[]{user.getName()}
        );
    }

    public List<User> getAllUsers() throws SQLException {
        return myJdbcTemplate.query(
                "SELECT * FROM users",
                null,
                new AutoRowMapper<>(User.class)
        );
    }


}
