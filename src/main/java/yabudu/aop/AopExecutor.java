package yabudu.aop;

import yabudu.annotation.MyLogged;
import yabudu.annotation.MyTransactional;

import java.lang.reflect.Method;

// Этот класс содержит AOP-логику
public class AopExecutor {

    private final Object target;
    private final Class<?> targetClass;

    public AopExecutor(Object target) {
        this.target = target;
        // 🔥 Если это CGLIB proxy — берём реальный класс (superclass)
        Class<?> clazz = target.getClass();
        if (clazz.getName().contains("$$")) {
            clazz = clazz.getSuperclass();
        }
        this.targetClass = clazz;
    }

    // 🔥 execute — центральная точка AOP
    // здесь происходит:
    // 1. проверка аннотаций
    // 2. логирование
    // 3. транзакции
    // 4. вызов оригинального метода через Invocation
    // invocation — это способ вызвать реальный метод (мы его передаём снаружи)
    public Object execute(Method method, Object[] args, Invocation invocation) throws Throwable {

        // Находим оригинальный метод в реальном классе
        Method originalMethod = targetClass.getMethod(
                method.getName(),
                method.getParameterTypes()
        );


        if (originalMethod.isAnnotationPresent(MyLogged.class)) {
            System.out.println("LOG START: " + method.getName());
        }

        if (originalMethod.isAnnotationPresent(MyTransactional.class)) {
            System.out.println("TRANSACTION BEGIN");
        }

        // результат вызова метода
        Object result;

        try {
            // Здесь вызывается реальный метод
            result = invocation.proceed();
        } catch (Exception e) {

            if (originalMethod.isAnnotationPresent(MyTransactional.class)) {
                System.out.println("ROLLBACK");
            }

            throw e.getCause() != null ? e.getCause() : e;
        }

        // После вызова

        if (originalMethod.isAnnotationPresent(MyTransactional.class)) {
            System.out.println("COMMIT");
        }

        if (originalMethod.isAnnotationPresent(MyLogged.class)) {
            System.out.println("LOG END: " + method.getName());
        }

        return result;
    }

    // Интерфейс — как вызвать метод
    public interface Invocation {
        Object proceed() throws Throwable;
    }
}