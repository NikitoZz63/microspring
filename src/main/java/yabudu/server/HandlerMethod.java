package yabudu.server;

import java.lang.reflect.Method;

public class HandlerMethod {

    // HTTP-метод маршрута (GET, POST и т.д.)
    public String httpMethod;
    // Шаблон пути маршрута (/users/{id})
    public String path;
    // Объект контроллера у которого будем вызывать метод
    Object controller;
    // Метод контроллера, который будет вызываться через reflection
    Method method;
}
