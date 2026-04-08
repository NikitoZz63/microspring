package yabudu.repo;

import yabudu.annotation.MyAutowired;
import yabudu.annotation.MyComponent;
import yabudu.dto.Task;
import yabudu.jdbc.AutoRowMapper;
import yabudu.jdbc.MyJdbcTemplate;

import java.sql.SQLException;
import java.util.List;

@MyComponent
public class TaskRepo {

    @MyAutowired
    private MyJdbcTemplate myJdbcTemplate;

    public void createTask(Task task) throws SQLException {
        myJdbcTemplate.update(
                "INSERT INTO tasks(title, user_id) VALUES (?, ?)",
                new Object[]{task.getTitle(), task.getUserId()}
        );
    }

    public List<Task> getUserTasks(Long userId) throws SQLException {
        return myJdbcTemplate.query(
                "SELECT * FROM tasks WHERE user_id = ?",
                new Object[]{userId},
                new AutoRowMapper<>(Task.class)
        );
    }

    public void deleteTask(Long id) throws SQLException {
        myJdbcTemplate.update(
                "DELETE FROM tasks WHERE id = ?",
                new Object[]{id}
        );
    }

    public void updateTask(Task task, Long id) throws SQLException {
        myJdbcTemplate.update(
                "UPDATE tasks SET title = ?, user_id = ? WHERE id = ?",
                new Object[]{task.getTitle(), task.getUserId(), id}
        );
    }

}
