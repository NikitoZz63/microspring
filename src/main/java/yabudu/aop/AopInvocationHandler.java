package yabudu.aop;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;

// Этот класс используется, когда бин ИМЕЕТ интерфейсы.
// JDK Proxy создаёт объект, реализующий интерфейс, и все вызовы попадают сюда (в invoke).
public class AopInvocationHandler implements InvocationHandler {

    // Оригинальный объект (настоящий бин)
    private final Object target;

    // Общий исполнитель AOP-логики
    private final AopExecutor executor;

    // Конструктор: сохраняем бин и создаём executor
    public AopInvocationHandler(Object target) {
        this.target = target;                    // сохраняем реальный объект
        this.executor = new AopExecutor(target); // создаём исполнитель AOP-логики
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        // proxy — сам прокси-объект (реализация интерфейса)
        // method— вызываемый метод
        // args  — аргументы метода

        // Мы НЕ вызываем метод напрямую здесь - делегируем её в AopExecutor.

        return executor.execute(
                method,        // какой метод вызвали
                args,          // аргументы

                // Передаём способ вызова оригинального метода
                (AopExecutor.Invocation) () -> {
                    // 🔥 JDK Proxy: вызываем оригинальный метод через reflection
                    // В отличие от CGLIB тут нет invokeSuper, поэтому используем method.invoke
                    return method.invoke(target, args);
                }
        );
    }
}