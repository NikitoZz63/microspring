package yabudu.aop;

import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

import java.lang.reflect.Method;

// CGLIB обработчик
// Этот класс используется, когда бин НЕ имеет интерфейсов.
// CGLIB создаёт наследника класса и все вызовы методов попадают сюда (в intercept).
public class AopMethodInterceptor implements MethodInterceptor {

    // Оригинальный объект (настоящий бин) к которому в итоге будет делегирован вызов
    private final Object target;

    // Общий исполнитель AOP-логики
    private final AopExecutor executor;

    // Конструктор: сохраняем оригинальный бин и создаём executor под этот бин
    public AopMethodInterceptor(Object target) {
        this.target = target;                    // сохраняем ссылку на реальный объект
        this.executor = new AopExecutor(target); // создаём исполнитель AOP-логики
    }

    @Override
    public Object intercept(Object obj, Method method, Object[] args, MethodProxy proxy) throws Throwable {

        // obj — сам прокси-объект (наследник, созданный CGLIB)
        // method— метод, который был вызван у прокси
        // args — аргументы метода
        // proxy — объект CGLIB для вызова оригинального метода

        // Мы НЕ вызываем метод напрямую здесь.
        // Вместо этого передаём управление в AopExecutor, который выполнит общую логику.

        return executor.execute(
                method,        // какой метод вызвали
                args,          // с какими аргументами

                // Передаём способо вызова реального метода в виде функционального интерфейса Invocation
                (AopExecutor.Invocation) () -> {
                    // 🔥 CGLIB: invokeSuper вызывает оригинальный метод через proxy
                    // Это критично — если вызвать target напрямую, AOP сломается
                    return proxy.invokeSuper(obj, args);
                }
        );
    }
}