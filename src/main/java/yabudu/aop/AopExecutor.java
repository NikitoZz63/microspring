package yabudu.aop;

import yabudu.annotation.MyLogged;
import yabudu.annotation.MyTransactional;
import yabudu.jdbc.ConnectionHolder;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.sql.Connection;

// Этот класс содержит AOP-логику
public class AopExecutor {
    private final Object target;
    private final Class<?> targetClass;
    private final DataSource dataSource;

    public AopExecutor(Object target, DataSource dataSource) {
        this.target = target;
        this.dataSource = dataSource;

        // берём реальный класс (superclass)
        Class<?> clazz = target.getClass();

        if (clazz.getName().contains("$$")) {
            clazz = clazz.getSuperclass();
        }
        this.targetClass = clazz;
    }

    // Центральная точка AOP
    // 1. проверка аннотаций
    // 2. логирование
    // 3. транзакции
    // 4. вызов оригинального метода через Invocation
    // invocation — это способ вызвать реальный метод (мы его передаём снаружи)
    public Object execute(Method method, Object[] args, Invocation invocation) throws Throwable {

        Method originalMethod = targetClass.getMethod(
                method.getName(),
                method.getParameterTypes()
        );

        boolean isLogged = originalMethod.isAnnotationPresent(MyLogged.class);
        boolean isTransactional = originalMethod.isAnnotationPresent(MyTransactional.class);

        if (isLogged) {
            System.out.println("LOG START: " + method.getName());
        }

        Connection connection = null;
        boolean alreadyInTransaction = (ConnectionHolder.getConnection() != null);

        try {
            // НАЧАЛО ТРАНЗАКЦИИ
            if (isTransactional && !alreadyInTransaction) {
                connection = dataSource.getConnection();
                connection.setAutoCommit(false);
                ConnectionHolder.setConnection(connection);

                System.out.println("TRANSACTION BEGIN");
            }

            // вызов метода
            Object result = invocation.proceed();

            // КОММИТ
            if (isTransactional && !alreadyInTransaction) {
                connection.commit();
                System.out.println("COMMIT");
            }

            return result;

        } catch (Throwable e) {

            // РОЛЛБЕК
            if (isTransactional && !alreadyInTransaction && connection != null) {
                connection.rollback();
                System.out.println("ROLLBACK");
            }

            throw e.getCause() != null ? e.getCause() : e;

        } finally {
            if (isTransactional && !alreadyInTransaction && connection != null) {
                ConnectionHolder.clear();
                connection.close();
            }

            if (isLogged) {
                System.out.println("LOG END: " + method.getName());
            }
        }
    }

    // Интерфейс — как вызвать метод
    public interface Invocation {
        Object proceed() throws Throwable;
    }
}