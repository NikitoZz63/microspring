package yabudu.service;

import yabudu.annotation.MyAutowired;
import yabudu.annotation.MyComponent;
import yabudu.annotation.MyLogged;
import yabudu.annotation.MyTransactional;
import yabudu.dto.Task;
import yabudu.repo.TaskRepo;

import java.sql.SQLException;
import java.util.List;

@MyComponent
public class TaskService {

    @MyAutowired
    private TaskRepo taskRepo;

    @MyTransactional
    @MyLogged
    public void createTask(Task task) throws SQLException {
        taskRepo.createTask(task);
    }

    public List<Task> getUserTasks(Long userId) throws SQLException {
        return taskRepo.getUserTasks(userId);
    }

    public void deleteTask(Long id) throws SQLException {
        taskRepo.deleteTask(id);
    }

    public void updateTask(Task task, Long id) throws SQLException {
        taskRepo.updateTask(task, id);
    }

}
