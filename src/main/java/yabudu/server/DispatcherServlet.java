package yabudu.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import yabudu.ApplicationContext;
import yabudu.annotation.*;
import yabudu.exception.EmptyBodyException;
import yabudu.exception.InvalidJsonException;
import yabudu.exception.MissingPathVariableException;
import yabudu.exception.MissingRequestParamException;

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

    // Подготовка
    private void initMapping() {
        try {
            // Список контроллеров
            Collection<Object> beans = context.getAllBeans();

            for (Object bean : beans) {

                // Берём класс
                Class<?> controllerClass = bean.getClass();

                // Если это proxy — поднимаемся до реального класса
                while (controllerClass.getName().contains("$$")) {
                    controllerClass = controllerClass.getSuperclass();
                }

                // Проверяем аннотацию
                if (!controllerClass.isAnnotationPresent(MyController.class)) {
                    continue;
                }

                // Проходим по всем методам класса
                for (Method method : controllerClass.getDeclaredMethods()) {

                    // Ищем методы с MyRequestMapping
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

    // Обработка каждого запроса
    public void handle(HttpExchange exchange) throws IOException {
        int status = 200;

        // Получаем HTTP метод (GET, POST итд)
        String requestMethod = exchange.getRequestMethod();

        // Получаем путь (/hello, /users итд)
        String requestPath = exchange.getRequestURI().getPath();

        System.out.println("REQUEST: " + requestMethod + " " + requestPath);

        // Получаем строку query
        String query = exchange.getRequestURI().getQuery();

        System.out.println("QUERY PARAMS: " + query);

        // Сюда парсим query параметры
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

        // Читаем заголовок Accept
        String acceptHeader = exchange.getRequestHeaders().getFirst("Accept");

        String requestBody = null;

        // ЧИтаем body только если это POST / PUT / PATCH
        if ("POST".equals(requestMethod) || "PUT".equals(requestMethod) || "PATCH".equals(requestMethod)) {
            requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        }

        if (requestBody != null) {
            System.out.println("BODY: " + requestBody);
        }

        String response;

        if (handler == null) {
            response = "Not Found";

            // Отправляем 404
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

                // Получаем параметры метода
                Parameter[] parameters = handler.method.getParameters();

                // Массив аргументов
                Object[] args = new Object[parameters.length];

                for (int i = 0; i < parameters.length; i++) {
                    Parameter parameter = parameters[i];

                    // Проверяем есть ли аннотация MyRequestParam
                    if (parameter.isAnnotationPresent(MyRequestParam.class)) {

                        MyRequestParam paramAnnotation = parameter.getAnnotation(MyRequestParam.class);
                        boolean required = paramAnnotation.required();
                        String paramName = paramAnnotation.value();

                        // Достаём значение из query
                        String value = queryParams.get(paramName);
                        if (value == null && required) {
                            throw new MissingRequestParamException(paramName);
                        }
                        args[i] = value;

                    } else if (parameter.isAnnotationPresent(MyPathVariable.class)) {
                        MyPathVariable pathVariableAnnotation = parameter.getAnnotation(MyPathVariable.class);
                        String variableName = pathVariableAnnotation.value();

                        // Достаём значение из path variables
                        String value = pathVariables.get(variableName);

                        if (value == null) {
                            throw new MissingPathVariableException(variableName);
                        }
                        args[i] = value;
                    } else if (parameter.isAnnotationPresent(MyRequestBody.class)) {
                        if (requestBody == null || requestBody.isEmpty()) {
                            throw new EmptyBodyException();
                        }
                        try {
                            // Десериализация JSON в объект
                            args[i] = JSON_MAPPER.readValue(requestBody, parameter.getType());
                        } catch (Exception e) {
                            throw new InvalidJsonException(e);
                        }
                    }
                }

                // Вызываем метод контроллера с аргументами
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

                if (e instanceof MissingRequestParamException ||
                        e instanceof MissingPathVariableException ||
                        e instanceof EmptyBodyException ||
                        e instanceof InvalidJsonException) {

                    status = 400;
                    response = e.getMessage();
                } else {
                    status = 500;
                    response = "Internal Server Error: " + e.getMessage();
                }

                // возвращаем текст
                exchange.getResponseHeaders().set("Content-Type", "text/plain");
            }
        }
        byte[] responseBytes = response.getBytes();

        // Лог Ответа
        System.out.println("RESPONSE: " + status + " " + response);
        exchange.sendResponseHeaders(status, responseBytes.length);
        exchange.getResponseBody().write(responseBytes);
        exchange.getResponseBody().close();
    }

    // Этот метод пытается найти подходящий handler, по точному совпадению и по шаблону пути
    private HandlerMethod findHandler(String requestMethod, String requestPath) {
        // Сначала пробуем точное совпадение.
        String exactKey = requestMethod + ":" + requestPath;
        HandlerMethod exactHandler = mappings.get(exactKey);

        if (exactHandler != null) {
            return exactHandler;
        }

        // Если точного совпадения нет,
        // Проходим по всем зарегистрированным маршрутам и пытаемся найти шаблонный путь.
        for (Map.Entry<String, HandlerMethod> entry : mappings.entrySet()) {
            String key = entry.getKey();

            // Делим ключ на 2 части
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

    // Проверяет, подходит ли реальный путь, под шаблонный маршрут.
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

    // Достаёт значения path variable из URL.
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
