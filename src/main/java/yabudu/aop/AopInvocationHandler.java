package yabudu.aop;

import javax.sql.DataSource;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

// Этот класс используется, когда бин имеет интерфейсы.
// JDK Proxy создаёт объект, реализующий интерфейс, и все вызовы попадают сюда (в invoke).
public class AopInvocationHandler implements InvocationHandler {
    // Оригинальный объект (настоящий бин)
    private final Object target;

    // Общий исполнитель AOP-логики
    private final AopExecutor executor;

    private final DataSource dataSource;

    // Конструктор сохраняем бин и создаём executor
    public AopInvocationHandler(Object target, DataSource dataSource) {
        this.target = target;                    // сохраняем реальный объект
        this.dataSource = dataSource;
        this.executor = new AopExecutor(target, dataSource); // создаём исполнитель AOP-логики
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // proxy — сам прокси-объект (реализация интерфейса)
        // method — вызываемый метод
        // args — аргументы метода

        // Мы не вызываем метод напрямую здесь - делегируем её в AopExecutor.
        return executor.execute(
                method,
                args,
                // Передаём способ вызова оригинального метода
                (AopExecutor.Invocation) () -> {
                    // вызываем оригинальный метод через reflection
                    // В отличие от CGLIB тут нет invokeSuper, поэтому используем method.invoke
                    return method.invoke(target, args);
                }
        );
    }
}