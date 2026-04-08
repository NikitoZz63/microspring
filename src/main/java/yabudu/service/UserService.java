package yabudu.service;

import yabudu.annotation.MyAutowired;
import yabudu.annotation.MyComponent;
import yabudu.annotation.MyLogged;
import yabudu.annotation.MyTransactional;
import yabudu.dto.User;
import yabudu.repo.UserRepo;

import java.sql.SQLException;
import java.util.List;

@MyComponent
public class UserService {

    @MyAutowired
    private UserRepo userRepo;

    @MyTransactional
    @MyLogged
    public void createUser(User user) throws SQLException {
        userRepo.createUser(user);
    }

    public List<User> getAllUsers() throws SQLException {
        return userRepo.getAllUsers();
    }


}
