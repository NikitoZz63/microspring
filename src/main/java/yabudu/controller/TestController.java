package yabudu.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import yabudu.annotation.*;
import yabudu.dto.Task;
import yabudu.dto.User;
import yabudu.jdbc.AutoRowMapper;
import yabudu.jdbc.MyJdbcTemplate;
import yabudu.jdbc.RowMapper;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@MyController
public class TestController {

    private final ObjectMapper objectMapper = new ObjectMapper();
    @MyAutowired
    private MyJdbcTemplate jdbcTemplate;

    public TestController() {
    }

    // ================= USERS =================

    @MyRequestMapping(path = "/users", method = "GET")
    public String getAllUsers() throws SQLException, JsonProcessingException {
        // params это значения, которые подставляются в SQL вместо "?"
        // rs это одна строка результата из базы, из неё мы собираем объект User
        List<User> users = jdbcTemplate.query(
                "SELECT * FROM users",
                null,
                new AutoRowMapper<>(User.class)
        );

        return objectMapper.writeValueAsString(users);
    }

    @MyRequestMapping(path = "/users", method = "POST")
    @MyTransactional
    public String createUser(@MyRequestBody User dto) throws SQLException, JsonProcessingException {
        jdbcTemplate.update(
                "INSERT INTO users(name) VALUES (?)",
                new Object[]{dto.getName()}
        );

        return objectMapper.writeValueAsString(
                Map.of("status", "user created")
        );
    }

    // ================= TASKS =================

    @MyRequestMapping(path = "/users/{id}/tasks", method = "GET")
    public String getUserTasks(@MyPathVariable("id") String userId) throws SQLException, JsonProcessingException {
        List<Task> tasks = jdbcTemplate.query(
                "SELECT * FROM tasks WHERE user_id = ?",
                new Object[]{userId},
                new AutoRowMapper<>(Task.class)
        );

        return objectMapper.writeValueAsString(tasks);
    }

    @MyRequestMapping(path = "/tasks", method = "POST")
    @MyTransactional
    public String createTask(@MyRequestBody Task dto) throws SQLException, JsonProcessingException {

        jdbcTemplate.update(
                "INSERT INTO tasks(title, user_id) VALUES (?, ?)",
                new Object[]{dto.getTitle(), dto.getUserId()}
        );

        return objectMapper.writeValueAsString(
                Map.of("status", "task created")
        );
    }

    @MyRequestMapping(path = "/tasks/{id}", method = "PUT")
    @MyTransactional
    public String updateTask(
            @MyPathVariable("id") String id,
            @MyRequestBody Task dto
    ) throws SQLException, JsonProcessingException {
        jdbcTemplate.update(
                "UPDATE tasks SET title = ? WHERE id = ?",
                new Object[]{dto.getTitle(), id}
        );

        return objectMapper.writeValueAsString(
                Map.of("status", "task updated")
        );
    }

    @MyRequestMapping(path = "/tasks/{id}", method = "DELETE")
    @MyTransactional
    public String deleteTask(@MyPathVariable("id") String id) throws SQLException, JsonProcessingException {
        jdbcTemplate.update(
                "DELETE FROM tasks WHERE id = ?",
                new Object[]{id}
        );

        return objectMapper.writeValueAsString(
                Map.of("status", "task deleted")
        );
    }

    // ================= OTHER (TEST / DEMO) =================

    @MyRequestMapping(path = "/create-test", method = "GET")
    @MyTransactional
    public String createTest() throws SQLException {
        jdbcTemplate.update(
                "INSERT INTO users(name) VALUES (?)",
                new Object[]{"Nikita"}
        );

        if (true) {
            throw new RuntimeException("FAIL");
        }

        return "OK";
    }

    @MyRequestMapping(path = "/users-all", method = "GET")
    public String allUsers() throws SQLException {
        return jdbcTemplate.query(
                "SELECT * FROM users",
                null,
                (RowMapper<String>) rs -> rs.getString("name")
        ).toString();
    }

    @MyRequestMapping(path = "/test-insert", method = "GET")
    public String testInsert() throws SQLException {
        jdbcTemplate.update(
                "INSERT INTO users(name) VALUES (?)",
                new Object[]{"TEST_USER"}
        );

        return "OK";
    }

    @MyRequestMapping(path = "/hello", method = "GET")
    public String hello(@MyRequestParam("name") String name) {
        return "Hello " + name;
    }


    @MyRequestMapping(path = "/users/{id}", method = "GET")
    public String getUser(@MyPathVariable("id") String id) {
        return "User id = " + id;
    }

    @MyRequestMapping(path = "/ping", method = "GET")
    public String ping() {
        return "pong";
    }

}
