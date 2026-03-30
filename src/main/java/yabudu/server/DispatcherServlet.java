package yabudu.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import yabudu.ApplicationContext;
import yabudu.annotation.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class DispatcherServlet {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final XmlMapper XML_MAPPER = new XmlMapper();
    // здесь будем хранить все маршруты
    private final Map<String, HandlerMethod> mappings = new HashMap<>();
    private final ApplicationContext context;

    public DispatcherServlet(ApplicationContext context) throws IOException {
        this.context = context;
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);

        // Обрабатываем все запросы через MyHttpHandler
        server.createContext("/", new MyHttpHandler(this));

        // дефолтный пул потоков
        server.setExecutor(null);

        initMapping();
        server.start();

    }

    // подготовка
    private void initMapping() {
        try {
            //список контроллеров
            Collection<Object> beans = context.getAllBeans();

            for (Object bean : beans) {

                // берём класс
                Class<?> controllerClass = bean.getClass();

                // если это proxy — поднимаемся до реального класса
                while (controllerClass.getName().contains("$$")) {
                    controllerClass = controllerClass.getSuperclass();
                }

                // проверяем аннотацию
                if (!controllerClass.isAnnotationPresent(MyController.class)) {
                    continue;
                }

                // проходим по всем методам класса
                for (Method method : controllerClass.getDeclaredMethods()) {

                    // ищем методы с MyRequestMapping
                    if (!method.isAnnotationPresent(MyRequestMapping.class)) {
                        continue;
                    }

                    MyRequestMapping mapping = method.getAnnotation(MyRequestMapping.class);

                    String httpMethod = mapping.method();
                    String path = mapping.path();

                    String key = httpMethod + ":" + path;

                    HandlerMethod handlerMethod = new HandlerMethod();

                    // Сохраняем сам обьект, а не класс, что бы потом вызывать метод у обьекта
                    handlerMethod.controller = bean;
                    handlerMethod.method = method;

                    // Сохраняем HTTP-метод и путь, чтобы потом можно было работать с шаблонными маршрутами
                    handlerMethod.httpMethod = httpMethod;
                    handlerMethod.path = path;

                    if (httpMethod == null || httpMethod.isEmpty()) {
                        throw new RuntimeException("HTTP method is empty in " + method);
                    }
                    if (path == null || path.isEmpty()) {
                        throw new RuntimeException("Path is empty in " + method);
                    }

                    if (mappings.containsKey(key)) {
                        throw new RuntimeException("Duplicate mapping found: " + key);
                    }

                    mappings.put(key, handlerMethod);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize mappings", e);
        }
    }

    //обработка каждого запроса
    public void handle(HttpExchange exchange) throws IOException {
        int status = 200;

        // получаем HTTP метод (GET, POST итд)
        String requestMethod = exchange.getRequestMethod();

        // получаем путь (/hello, /users итд)
        String requestPath = exchange.getRequestURI().getPath();

        // получаем строку query
        String query = exchange.getRequestURI().getQuery();

        // сюда парсим query параметры
        Map<String, String> queryParams = new HashMap<>();

        if (query != null) {
            String[] pairs = query.split("&");

            for (String pair : pairs) {
                String[] keyValue = pair.split("=");

                if (keyValue.length == 2) {
                    //URL decoding
                    String key = URLDecoder.decode(keyValue[0], StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(keyValue[1], StandardCharsets.UTF_8);
                    queryParams.put(key, value);
                }
            }
        }

        // Ищем handler не только по точному совпадению,
        // но и по шаблонным путям вроде /users/{id}
        HandlerMethod handler = findHandler(requestMethod, requestPath);

        // читаем заголовок Accept
        String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");

        String requestBody = null;

        // читаем body только если это POST / PUT / PATCH
        if ("POST".equals(requestMethod) || "PUT".equals(requestMethod) || "PATCH".equals(requestMethod)) {
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        }

        String response;

        if (handler == null) {
            response = "Not Found";

            // отправляем 404
            exchange.sendResponseHeaders(404, response.getBytes().length);
            exchange.getResponseBody().write(response.getBytes());
            exchange.getResponseBody().close();

            return;
        } else {
            try {

                // Сюда положим path variables из URL
                // Например для /users/15 и шаблона /users/{id}
                // получится map: id -> 15
                Map<String, String> pathVariables = extractPathVariables(handler.path, requestPath);

                //получаем параметры метода
                Parameter[] parameters = handler.method.getParameters();

                // массив аргументов
                Object[] args = new Object[parameters.length];

                for (int i = 0; i < parameters.length; i++) {
                    Parameter parameter = parameters[i];

                    // проверяем есть ли аннотация MyRequestParam
                    if (parameter.isAnnotationPresent(MyRequestParam.class)) {
                        MyRequestParam paramAnnotation = parameter.getAnnotation(MyRequestParam.class);
                        String paramName = paramAnnotation.value();

                        // достаём значение из query
                        String value = queryParams.get(paramName);
                        if (value == null) {
                            throw new RuntimeException("Missing query param: " + paramName);
                        }
                        args[i] = value;
                    } else if (parameter.isAnnotationPresent(MyPathVariable.class)) {
                        MyPathVariable pathVariableAnnotation = parameter.getAnnotation(MyPathVariable.class);
                        String variableName = pathVariableAnnotation.value();

                        // Достаём значение из path variables
                        String value = pathVariables.get(variableName);

                        if (value == null) {
                            throw new RuntimeException("Missing path variable: " + variableName);
                        }
                        args[i] = value;
                    } else if (parameter.isAnnotationPresent(MyRequestBody.class)) {
                        if (requestBody == null || requestBody.isEmpty()) {
                            throw new RuntimeException("Request body is empty");
                        }
                        try {
                            // десериализация JSON в объект
                            args[i] = JSON_MAPPER.readValue(requestBody, parameter.getType());
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                }

                // вызываем метод контроллера с аргументами
                Object result;

                try {
                    result = handler.method.invoke(handler.controller, args);
                } catch (InvocationTargetException e) {
                    throw new RuntimeException(e.getTargetException());
                }

                if (result == null) {
                    exchange.getResponseHeaders().set("Content-Type", "application/json");
                    response = "";
                } else {
                    try {
                        // если XML
                        if (acceptHeader != null && acceptHeader.toLowerCase().contains("application/xml")) {
                            response = XML_MAPPER.writeValueAsString(result);
                            exchange.getResponseHeaders().set("Content-Type", "application/xml");
                        } else {
                            // если JSON
                            response = JSON_MAPPER.writeValueAsString(result);
                            exchange.getResponseHeaders().set("Content-Type", "application/json");
                        }
                    } catch (Exception e) {
                        response = "Serialization error: " + e.getMessage();
                        status = 500;
                        exchange.getResponseHeaders().set("Content-Type", "text/plain");
                    }
                }
            } catch (Exception e) {
                response = "Error: " + e.getMessage();
                status = 500;
                //это просто текст
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
            }
        }
        byte[] responseBytes = response.getBytes();
        exchange.sendResponseHeaders(status, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.getResponseBody().close();
    }

    // этот метод пытается найти подходящий handler, по точному совпадению и по шаблону пути
    private HandlerMethod findHandler(String requestMethod, String requestPath) {
        // Сначала пробуем точное совпадение.
        String exactKey = requestMethod + ":" + requestPath;
        HandlerMethod exactHandler = mappings.get(exactKey);

        if (exactHandler != null) {
            return exactHandler;
        }

        // если точного совпадения нет,
        // проходим по всем зарегистрированным маршрутам и пытаемся найти шаблонный путь.
        for (Map.Entry<String, HandlerMethod> entry : mappings.entrySet()) {
            String key = entry.getKey();

            //делим ключ на 2 части
            String[] parts = key.split(":", 2);

            String mappedMethod = parts[0];
            String mappedPath = parts[1];

            // HTTP-метод должен совпадать
            if (!mappedMethod.equals(requestMethod)) {
                continue;
            }

            // Проверяем, совпадает ли requestPath с шаблоном mappedPath
            if (matchPath(mappedPath, requestPath)) {
                return entry.getValue();
            }
        }
        return null;
    }

    // проверяет, подходит ли реальный путь, под шаблонный маршрут.
    // templatePath = /users/{id}
    // requestPath  = /users/15
    // результат = true
    private boolean matchPath(String templatePath, String requestPath) {
        String[] templateParts = templatePath.split("/");
        String[] requestParts = requestPath.split("/");

        // Если количество частей разное - точно не тот маршрут
        if (templateParts.length != requestParts.length) {
            return false;
        }

        for (int i = 0; i < templateParts.length; i++) {
            String templatePart = templateParts[i];
            String requestPart = requestParts[i];

            // Если часть шаблона вида {id}, значит туда может подойти любое значение
            if (templatePart.startsWith("{") && templatePart.endsWith("}")) {
                continue;
            }
            // Если это не переменная часть, тогда должно быть точное совпадение
            if (!templatePart.equals(requestPart)) {
                return false;
            }
        }
        return true;
    }

    // достаёт значения path variable из URL.
    // templatePath = /users/{id}
    // requestPath  = /users/15
    // результат {id=15}
    private Map<String, String> extractPathVariables(String templatePath, String requestPath) {
        Map<String, String> pathVariables = new HashMap<>();

        String[] templateParts = templatePath.split("/");
        String[] requestParts = requestPath.split("/");

        for (int i = 0; i < templateParts.length; i++) {
            String templatePart = templateParts[i];
            String requestPart = requestParts[i];

            // Ищем части шаблона вида {id}
            if (templatePart.startsWith("{") && templatePart.endsWith("}")) {
                // Убираем фигурные скобки: {id} -> id
                String variableName = templatePart.substring(1, templatePart.length() - 1);

                // Сохраняем значение из реального пути
                pathVariables.put(variableName, requestPart);
            }
        }
        return pathVariables;
    }
}
