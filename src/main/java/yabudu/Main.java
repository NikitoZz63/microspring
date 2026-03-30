package yabudu;

import yabudu.server.DispatcherServlet;
import yabudu.testClasses.User;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
public class Main {
    public static void main(String[] args) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException, IOException {
        // 🔥 ВАЖНО: сканируем ВСЮ базовую папку, а не только testClasses
        // иначе AopBeanPostProcessor (лежит в yabudu) не найдётся
        ApplicationContext context = new ApplicationContext("yabudu");

        // сначала получаем бин и вызываем методы (AOP проверяется здесь)
        User user = context.getBean(User.class);
        user.methodA();

        // потом закрываем контекст (вызов @PreDestroy)
        context.close();

        new DispatcherServlet(context);
    }
}