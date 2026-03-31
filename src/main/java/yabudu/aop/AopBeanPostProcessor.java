package yabudu.aop;

import net.sf.cglib.proxy.Enhancer;
import yabudu.annotation.MyBeanPostProcessor;
import yabudu.annotation.MyComponent;
import yabudu.annotation.MyLogged;
import yabudu.annotation.MyTransactional;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;

// Это BeanPostProcessor, который после полной инициализации бина
// проверяет, есть ли на его методах AOP-аннотации
// Если такие аннотации есть, он подменяет обычный бин на proxy-объект
@MyComponent
public class AopBeanPostProcessor implements MyBeanPostProcessor {

    @Override
    public Object postProcessBeforeInitialization(Object bean, String beanName) {
        return bean;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        // Получаем реальный класс текущего бина.
        Class<?> beanClass = bean.getClass();

        // Флаг покажет, есть ли у этого бина хотя бы один  методо на котором висит @MyLogged или @MyTransactional
        boolean hasAopAnnotations = false;

        // Проходим по всем публичным методам класса.
        for (Method method : beanClass.getMethods()) {

            // Если нашли хотя бы одну AOP-аннотацию,
            // значит бин нужно обернуть в proxy.
            if (method.isAnnotationPresent(MyLogged.class)
                    || method.isAnnotationPresent(MyTransactional.class)) {
                hasAopAnnotations = true;
                break;
            }
        }

        // Если AOP-аннотаций нет возвращаем бин без изменений
        if (!hasAopAnnotations) {
            return bean;
        }

        // JDK Proxy работает только с интерфейсами
        // Поэтому сначала проверяем, реализует ли класс хотя бы один интерфейс.
        if (beanClass.getInterfaces().length > 0) {
            return Proxy.newProxyInstance(
                    // ClassLoader нужен JVM для создания proxy-класса.
                    beanClass.getClassLoader(),

                    // Прокси будет реализовывать те же интерфейсы,
                    // что и исходный бин.
                    beanClass.getInterfaces(),

                    // Вся логика перехвата вызовов вынесена в отдельный класс.
                    new AopInvocationHandler(bean)
            );
        }

        // Если интерфейсов нет, используем CGLIB.
        // CGLIB создаёт proxy не через интерфейс, а через наследование от класса.
        // Enhancer - фабрика, которая создаёт CGLIB-прокси
        Enhancer enhancer = new Enhancer();

        // Говорим CGLIB, какой класс нужно проксировать.
        // Он создаст наследника этого класса.
        enhancer.setSuperclass(beanClass);

        // Передаём объект, который будет перехватывать вызовы методов.
        enhancer.setCallback(new AopMethodInterceptor(bean));

        // Создаём proxy-объект (НО это новый объект, не тот же самый бин)
        Object proxy = enhancer.create();

        // копируем все поля из оригинального бина в proxy
        // иначе зависимости (person, self) будут null и AOP не будет работать
        copyFields(bean, proxy);

        return proxy;
    }

    // Копирует все поля из оригинального объекта в proxy
    // Это нужно потому что CGLIB создаёт НОВЫЙ объект,
    // а не оборачивает существующий
    private void copyFields(Object source, Object target) {
        Class<?> clazz = source.getClass();

        while (clazz != Object.class) {
            for (java.lang.reflect.Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);
                try {
                    field.set(target, field.get(source));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
            clazz = clazz.getSuperclass();
        }
    }
}
