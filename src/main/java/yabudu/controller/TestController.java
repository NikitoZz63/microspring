package yabudu.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import yabudu.annotation.*;
import yabudu.dto.Task;
import yabudu.dto.User;
import yabudu.service.TaskService;
import yabudu.service.UserService;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@MyController
public class TestController {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MyAutowired
    private UserService userService;

    @MyAutowired
    private TaskService taskService;

    public TestController() {
    }

    // ================= USERS =================

    @MyRequestMapping(path = "/users", method = "GET")
    public String getAllUsers() throws SQLException, JsonProcessingException {
        // params это значения, которые подставляются в SQL вместо "?"
        // rs это одна строка результата из базы, из неё мы собираем объект User
        List<User> users = userService.getAllUsers();
        return objectMapper.writeValueAsString(users);
    }

    @MyRequestMapping(path = "/users", method = "POST")
    public String createUser(@MyRequestBody User dto) throws SQLException, JsonProcessingException {
        userService.createUser(dto);
        return objectMapper.writeValueAsString(
                Map.of("status", "user created")
        );
    }

    // ================= TASKS =================

    // Получаем задачи по userId через query param (?userId=1)
    @MyRequestMapping(path = "/tasks", method = "GET")
    public String getUserTasks(@MyRequestParam("userId") Long userId) throws SQLException, JsonProcessingException {
        List<Task> tasks = taskService.getUserTasks(userId);
        return objectMapper.writeValueAsString(tasks);
    }

    @MyRequestMapping(path = "/tasks", method = "POST")
    public String createTask(@MyRequestBody Task task) throws SQLException, JsonProcessingException {
        taskService.createTask(task);
        return objectMapper.writeValueAsString(
                Map.of("status", "task created")
        );
    }

    @MyRequestMapping(path = "/tasks/{id}", method = "PUT")
    public String updateTask(
            @MyPathVariable("id") Long id,
            @MyRequestBody Task task
    ) throws SQLException, JsonProcessingException {
        taskService.updateTask(task, id);
        return objectMapper.writeValueAsString(
                Map.of("status", "task updated")
        );
    }

    @MyRequestMapping(path = "/tasks/{id}", method = "DELETE")
    public String deleteTask(@MyPathVariable("id") Long id) throws SQLException, JsonProcessingException {
        taskService.deleteTask(id);
        return objectMapper.writeValueAsString(
                Map.of("status", "task deleted")
        );
    }

}
