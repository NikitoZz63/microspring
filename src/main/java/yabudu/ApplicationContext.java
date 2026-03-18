package yabudu;

import yabudu.annotation.*;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;


public class ApplicationContext {

    // Основное хранилище бинов
    private final Map<String, Object> beanContainer = new HashMap<>();
    // список всех прроцессоров, которые контейнер зарегистрировал
    private final List<MyBeanPostProcessor> beanPostProcessors = new ArrayList<>();
    //список бинов, которые уже успешно прошли lifecycle
    private final List<Object> initializedBeans = new ArrayList<>();
    //мапа(граф) зависимостей
    private final Map<Class<?>, List<Class<?>>> dependGraph = new HashMap<>();
    //хранилище бинов отдельно для каждого потока
    private final Map<String, ThreadLocal<Object>> threadLocalBeans = new HashMap<>();
    //имя-класс
    private final Map<String, Class<?>> threadScopedClasses = new HashMap<>();
    // пакет, который мы будем сканировать
    private final String basePackage;
    private final String noNameBeanFlag = "__UNSPECIFIED__";
    // список всех найденных классов во время сканирования
    private final List<Class<?>> scannedClasses = new ArrayList<>();

    // Конструктор контейнера, запускает все.
    public ApplicationContext(String basePackager) {
        this.basePackage = basePackager;

        try {

            // Находим физическую папку пакета в classpath
            File baseDir = findPackageDirectory(basePackager);

            // Сканируем папку и находим .class файлы
            scanDirectory(baseDir.getAbsolutePath(), basePackager);

            // Строим граф зависимостей
            buildDependencyGraph();

            // Создаём и инициализируем все BeanPostProcessor,
            registerBeanPostProcessors();

            // Создаём объекты (бины)
            createBeans();


        } catch (Exception e) {
            close();
            throw new RuntimeException("Failed to initialize ApplicationContext", e);
        }
    }

    public String getPackagePath(String basePackager) {
        return basePackager.replace(".", "/");
    }


    // Находит папку пакета внутри classpath.
    public File findPackageDirectory(String path) throws MalformedURLException {

        // ClassLoader умеет находить классы и ресурсы
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();

        // ищем путь пакета
        URL url = classLoader.getResource(getPackagePath(path));

        if (url == null) {
            throw new RuntimeException("Package not found in classpath: " + path);
        }

        // превращаем URL в File
        return new File(url.getFile());
    }


    //Сканирует директорию и находит все .class файлы. Если встречается подпапка — вызываем метод рекурсивно
    public void scanDirectory(String currentDirectory, String currentPackageName) {

        File directory = new File(currentDirectory);

        // если папки не существует или это не папка — выходим
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }

        // получаем все файлы внутри
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {

            // проверяем директория ли
            if (file.isDirectory()) {

                String folderName = file.getName();

                // создаём новое имя пакета
                String newPackage = currentPackageName + "." + folderName;

                // рекурсивно сканируем подпапку
                scanDirectory(file.getAbsolutePath(), newPackage);
            }

            // если это .class файл
            else if (file.getName().endsWith(".class")) {

                // убираем .class из имени
                String className = file.getName().replace(".class", "");

                // создаём полное имя класса
                String fullClassName = currentPackageName + "." + className;

                try {

                    // загружаем класс в JVM через reflection и кладем в clazz
                    Class<?> clazz = Class.forName(fullClassName);

                    // сохраняем найденный класс
                    scannedClasses.add(clazz);

                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }


    //Создаём объекты (бины) для всех классов с аннотацией @MyComponent.
    public void createBeans() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        String beanName;
        for (Class<?> scannedClass : scannedClasses) {

            // проверяем есть ли аннотация @MyComponent
            if (scannedClass.isAnnotationPresent(MyComponent.class)) {

                // генерируем имя бина
                MyComponent myComponent = scannedClass.getAnnotation(MyComponent.class);

                //проверяем не указано ли имя бина в аннотации
                if (!myComponent.beanName().equals(noNameBeanFlag)) {
                    beanName = myComponent.beanName();
                } else {
                    beanName = generateBeanName(scannedClass);
                }

                MyScope scope = scannedClass.getAnnotation(MyScope.class);

                String scopeValue;

                if (scope == null) {
                    scopeValue = "singleton";
                } else {
                    scopeValue = scope.scope();
                }

                if ("singleton".equals(scopeValue)) {
                    // создаём новый объект бин
                    Object bean = createBeanInstance(scannedClass);

                    // проверяем, что такого бина ещё нет
                    if (beanContainer.containsKey(beanName)) {
                        throw new IllegalStateException("Duplicate bean name: " + beanName);
                    }

                    bean = initializeBean(bean, beanName);

                    // сохраняем бин в контейнер
                    beanContainer.put(beanName, bean);
                }

                if ("thread".equals(scopeValue)) {
                    threadLocalBeans.put(beanName, new ThreadLocal<>());
                    threadScopedClasses.put(beanName, scannedClass);
                }
            }
        }
    }


    //Генерирует имя бина. UserService -> userService
    public String generateBeanName(Class<?> clazz) {
        String simpleName = clazz.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }


    //Контейнер проходит по всем бинам, ищет поля с @MyAutowired и вставляет нужный бин.
    public void injectDependencies() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        for (Object bean : beanContainer.values()) {
            injectDependencies(bean);
        }
    }

    public void injectDependencies(Object bean) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        //получаем текущий класс
        Class<?> currentClass = bean.getClass();

        //идем циклом по ирерахии классов(на случай если есть наследование)
        while (currentClass != Object.class) {

            // получаем все поля класса
            Field[] fields = currentClass.getDeclaredFields();
            for (Field field : fields) {
                // если поле не помечено @MyAutowired — пропускаем
                if (field.isAnnotationPresent(MyAutowired.class)) {

                    // ищем нужный бин
                    Object dependency = getObject(field);

                    try {
                        // разрешаем доступ к private полю
                        field.setAccessible(true);

                        // устанавливаем значение поля(внедряем зависимость)
                        field.set(bean, dependency);
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException("Failed to inject dependency: " + field.getName(), e);
                    }
                }
            }
            //получаем род класс
            currentClass = currentClass.getSuperclass();
        }
    }


    // поиск нужного бина для поля.
    private Object getObject(Field field) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {

        // проверяем есть ли @MyQualifier
        MyQualifier qualifier = field.getAnnotation(MyQualifier.class);

        if (qualifier != null) {

            return getBean(qualifier.name());
        }

        // если qualifier нет — ищем по типу
        Class<?> dependencyType = field.getType();

        return getBean(dependencyType);
    }


    public void buildDependencyGraph() {
        dependGraph.clear();
        //обход по всем просанированным классам и поиск анотации MyComponent
        for (Class<?> scannedClass : scannedClasses) {
            if (!scannedClass.isAnnotationPresent(MyComponent.class)) {
                continue;
            }


            List<Class<?>> depList = new ArrayList<>();
            Field[] fields = scannedClass.getDeclaredFields();

            //цикл по поляем класса и ищем MyAutowired
            for (Field field : fields) {
                if (!field.isAnnotationPresent(MyAutowired.class)) {
                    continue;
                }

                Class<?> dependencyClass = field.getType();
                //добавляем в лист с зависимыми классами если находим такой в scannedClasses
                if (scannedClasses.contains(dependencyClass)) {
                    depList.add(dependencyClass);
                }
            }
            dependGraph.put(scannedClass, depList);
        }
    }


    public void detectCycles() {
        Set<Class<?>> visited = new HashSet<>();
        Set<Class<?>> visiting = new HashSet<>();

        //Если класс уже проверяли на циклические зависимости, то проверять снова не нужно
        for (Class<?> aClass : dependGraph.keySet()) {
            if (!visited.contains(aClass)) {
                checkDependencies(aClass, visited, visiting);
            }
        }
    }

    //проверка циклической зависимости
    public void checkDependencies(Class<?> clazz, Set<Class<?>> visited, Set<Class<?>> visiting) {
        //если класс есть в visiting значит есть цикл зависимость
        if (visiting.contains(clazz)) {
            throw new IllegalStateException("Cyclic dependency detected: " + clazz);
        }

        //проверен ли класс ранее(убедились ли мы что нет там цикла)
        if (visited.contains(clazz)) {
            return;
        }

        visiting.add(clazz);

        //получаем все зависимости класса
        List<Class<?>> dependencies = dependGraph.get(clazz);

        if (dependencies == null) {
            return;
        }

        //цикл по завимостям, и рекурсия по этим же зависимостям
        for (Class<?> dependency : dependencies) {
            checkDependencies(dependency, visited, visiting);
        }

        visiting.remove(clazz);
        visited.add(clazz);
    }


    // Получить бин по имени
    public Object getBean(String name) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        // пытаемся получить бин из контейнера singleton-бинов.
        // beanContainer хранит уже созданные объекты (singleton).
        // Если бин там есть — значит он уже был создан ранее и его можно сразу вернуть.
        Object bean = beanContainer.get(name);
        if (bean != null) {
            return bean;
        }

        // Если бин не найден в контейнере, нужно определить,
        // какой класс соответствует этому имени бина.
        // Для этого проходим по всем ранее отсканированным классам.
        Class<?> beanClass = null;
        for (Class<?> clazz : scannedClasses) {

            // Пропускаем классы, которые не являются компонентами
            if (!clazz.isAnnotationPresent(MyComponent.class)) {
                continue;
            }

            // Получаем аннотацию компонента
            MyComponent component = clazz.getAnnotation(MyComponent.class);

            // Определяем имя бина.
            // Если имя указано в аннотации — используем его,
            // иначе генерируем имя автоматически
            String beanName;
            if (!component.beanName().equals(noNameBeanFlag)) {
                beanName = component.beanName();
            } else {
                beanName = generateBeanName(clazz);
            }

            // Если имя совпало с искомым — нашли нужный класс
            if (beanName.equals(name)) {
                beanClass = clazz;
                break;
            }
        }

        // Если ни один класс не соответствует этому имени это ошибка конфигурации
        if (beanClass == null) {
            throw new IllegalStateException("No bean found with name: " + name);
        }

        // Определяем scope
        // Если аннотация @MyScope отсутствует - по умолчанию считаем бин singleton.
        MyScope scope = beanClass.getAnnotation(MyScope.class);
        String scopeValue = scope == null ? "singleton" : scope.scope();

        // Обработка singleton scope.
        // Singleton-бин создаётся заранее в createBeans() и уже лежит в beanContainer.
        // Если мы дошли сюда и бин не найден — это ошибка, потому что singleton должен существовать в контейнере с самого начала
        if ("singleton".equals(scopeValue)) {
            if (bean != null) {
                return bean;
            }
            throw new IllegalStateException("Singleton bean not found: " + name);
        }

        // Обработка prototype scope.
        // Каждый вызов getBean создаёт новый объект.
        // НЕ сохраняем его в контейнере!!
        if ("prototype".equals(scopeValue)) {
            // Создаём новый объект
            Object newBean = createBeanInstance(beanClass);

            //инициализация бина
            newBean = initializeBean(newBean, name);

            // Возвращаем новый объект (не сохраняется в контейн)
            return newBean;
        }

        if ("thread".equals(scopeValue)) {
            ThreadLocal<Object> threadLocal = threadLocalBeans.get(name);

            //пытаемся получить бин для текущего потока
            bean = threadLocal.get();

            if (bean == null) {
                //српзу находим класс по имени в threadScopedClasses
                beanClass = threadScopedClasses.get(name);

                //создаем бин
                Object newBean = createBeanInstance(beanClass);
                //инициализация бина
                newBean = initializeBean(newBean, name);
                //записываем бин threadLocalBeans
                threadLocal.set(newBean);
            }
            return bean;
        }

        // Если указан неизвестный scope
        throw new IllegalStateException("Unknown scope: " + scopeValue);
    }

    //создаем бин через рефлексию
    private Object createBeanInstance(Class<?> beanClass) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        Constructor<?> constructor = beanClass.getDeclaredConstructor();

        constructor.setAccessible(true);

        return constructor.newInstance();
    }


    // Получить бин по типу
    public <T> T getBean(Class<T> type) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {

        List<String> candidates = new ArrayList<>();

        // Ищем подходящие классы среди всех найденных при сканировании
        for (Class<?> clazz : scannedClasses) {

            // Нас интересуют только классы, помеченные как компоненты
            if (!clazz.isAnnotationPresent(MyComponent.class)) {
                continue;
            }

            // Проверяем, подходит ли класс под требуемый тип
            if (type.isAssignableFrom(clazz)) {

                MyComponent component = clazz.getAnnotation(MyComponent.class);

                // Определяем имя бина
                String beanName;
                if (!component.beanName().equals(noNameBeanFlag)) {
                    beanName = component.beanName();
                } else {
                    beanName = generateBeanName(clazz);
                }

                candidates.add(beanName);
            }
        }

        // Если кандидатов нет — это ошибка
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No bean found for type: " + type.getName());
        }

        // Если кандидатов несколько — нужно использовать @MyQualifier
        if (candidates.size() > 1) {
            throw new IllegalStateException(
                    "Multiple beans found for type: " + type.getName() + ". Use @MyQualifier to specify which bean to inject.");
        }

        // Делегируем создание/получение бина методу getBean(String)
        return (T) getBean(candidates.get(0));
    }

    private void registerBeanPostProcessors() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        // по всем классам которые контейнер нашёл при сканировании пакета
        for (Class<?> scannedClass : scannedClasses) {

            // Если на классе нет @MyComponent, значит это не бин контейнера, пропускаем
            if (!scannedClass.isAnnotationPresent(MyComponent.class)) {
                continue;
            }

            // Проверяем, реализует ли класс интерфейс MyBeanPostProcessor.
            if (!MyBeanPostProcessor.class.isAssignableFrom(scannedClass)) {
                continue;
            }

            // Создаём экземпляр найденного BeanPostProcessor через reflection.
            Object processorBean = createBeanInstance(scannedClass);

            // Внедряем зависимости в сам BeanPostProcessor
            injectDependencies(processorBean);

            // Прогоняем процессор через все уже зарегистрированные before-процессоры.
            processorBean = applyBeanPostProcessorsBeforeInitialization(
                    processorBean,
                    generateBeanName(scannedClass)
            );

            // Вызываем метод с аннотацией @MyPostConstruct у самого BeanPostProcessor.
            invokePostConstruct(processorBean);

            // Прогоняем процессор через after-процессоры.
            processorBean = applyBeanPostProcessorsAfterInitialization(
                    processorBean,
                    generateBeanName(scannedClass)
            );

            // Кладём готовый объект в список зарегистрированных BeanPostProcessor.
            beanPostProcessors.add((MyBeanPostProcessor) processorBean);

            // Сохраняем этот бин в список успешно инициализированных бинов
            // Это нужно для корректного завершения контейнера, позже при close() контейнер сможет вызвать у него @MyPreDestroy.
            initializedBeans.add(processorBean);
        }
    }

    private void invokePostConstruct(Object bean) throws InvocationTargetException, IllegalAccessException {
        Method foundMethod = null;

        Class<?> currentClass = bean.getClass();

        while (currentClass != Object.class) {
            for (Method declaredMethod : currentClass.getDeclaredMethods()) {
                if (declaredMethod.isAnnotationPresent(MyPostConstruct.class)) {
                    if (foundMethod != null) {
                        throw new IllegalStateException("Multiple @MyPostConstruct methods found in class: " + bean.getClass().getName());
                    }
                    foundMethod = declaredMethod;
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        if (foundMethod != null) {
            foundMethod.setAccessible(true);
            foundMethod.invoke(bean);
        }

    }

    private void invokePreDestroy(Object bean) throws InvocationTargetException, IllegalAccessException {
        Method foundMethod = null;
        Class<?> currentClass = bean.getClass();

        while (currentClass != Object.class) {
            for (Method declaredMethod : currentClass.getDeclaredMethods()) {
                if (declaredMethod.isAnnotationPresent(MyPreDestroy.class)) {
                    if (foundMethod != null) {
                        throw new IllegalStateException("Multiple @MyPreDestroy methods found in class: " + bean.getClass().getName());
                    }
                    foundMethod = declaredMethod;
                }
            }
            currentClass = currentClass.getSuperclass();
        }

        if (foundMethod != null) {
            foundMethod.setAccessible(true);
            foundMethod.invoke(bean);
        }

    }

    private Object applyBeanPostProcessorsAfterInitialization(Object bean, String beanName) {
        Object currentBean = bean;
        for (MyBeanPostProcessor beanPostProcessor : beanPostProcessors) {
            currentBean = beanPostProcessor.postProcessAfterInitialization(currentBean, beanName);
        }
        return currentBean;
    }


    private Object applyBeanPostProcessorsBeforeInitialization(Object bean, String beanName) {
        Object currentBean = bean;
        for (MyBeanPostProcessor beanPostProcessor : beanPostProcessors) {
            currentBean = beanPostProcessor.postProcessBeforeInitialization(currentBean, beanName);
        }
        return currentBean;
    }

    private Object initializeBean(Object bean, String beanName) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        // Внедрение зависимостей
        injectDependencies(bean);

        // Вызов всех BeanPostProcessor ДО пользовательской инициализации
        bean = applyBeanPostProcessorsBeforeInitialization(bean, beanName);

        // Вызывается метод помеченный @MyPostConstruct.
        invokePostConstruct(bean);

        // Вызов всех BeanPostProcessor ПОСЛЕ инициализации
        bean = applyBeanPostProcessorsAfterInitialization(bean, beanName);

        // Сохраняем бин как успешно инициализированный
        // При вызове close контейнер пройдётся по этим бинам и вызовет у них методы с MyPreDestroy.
        initializedBeans.add(bean);

        return bean;
    }


    public void close() {
        ListIterator<Object> iterator = initializedBeans.listIterator(initializedBeans.size());

        while (iterator.hasPrevious()) {
            Object bean = iterator.previous();
            try {
                invokePreDestroy(bean);
            } catch (Exception e) {
                throw new RuntimeException("Failed to destroy bean: " + bean.getClass().getName(), e);
            }
        }
    }

}
