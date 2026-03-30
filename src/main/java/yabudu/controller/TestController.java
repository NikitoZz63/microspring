package yabudu.controller;

import yabudu.annotation.MyController;
import yabudu.annotation.MyPathVariable;
import yabudu.annotation.MyRequestMapping;
import yabudu.annotation.MyRequestParam;

@MyController
public class TestController {

    @MyRequestMapping(path = "/hello", method = "GET")
    public String hello(@MyRequestParam("name") String name) {
        return "Hello " + name;
    }


    @MyRequestMapping(path = "/users/{id}", method = "GET")
    public String getUser(@MyPathVariable("id") String id) {
        return "User id = " + id;
    }

    @MyRequestMapping(path = "/users", method = "POST")
    public String createUser(@yabudu.annotation.MyRequestBody yabudu.dto.UserDto dto) {
        return "Created user: " + dto.getName();
    }

    @MyRequestMapping(path = "/ping", method = "GET")
    public String ping() {
        return "pong";
    }

    @MyRequestMapping(path = "/sum", method = "GET")
    public String sum(
            @MyRequestParam("a") String a,
            @MyRequestParam("b") String b
    ) {
        return a + " + " + b;
    }

    @MyRequestMapping(path = "/mix/{id}", method = "GET")
    public String mix(
            @MyPathVariable("id") String id,
            @MyRequestParam("name") String name
    ) {
        return "id=" + id + ", name=" + name;
    }

}
