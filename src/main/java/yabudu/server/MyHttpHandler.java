package yabudu.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

public class MyHttpHandler implements HttpHandler {
    private final DispatcherServlet servlet;

    public MyHttpHandler(DispatcherServlet servlet) {
        this.servlet = servlet;
    }

    //вызываем встроенный сервер JDK, когда приходит запрос
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        servlet.handle(exchange);
    }
}
