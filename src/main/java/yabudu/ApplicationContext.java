package yabudu;

import yabudu.annotation.MyAutowired;
import yabudu.annotation.MyComponent;
import yabudu.annotation.MyQualifier;

import java.io.File;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;


public class ApplicationContext {

    // Основное хранилище бинов
    private final Map<String, Object> beanContainer = new HashMap<>();
    //мапа(граф) зависимостей
    private final Map<Class<?>, List<Class<?>>> dependGraph = new HashMap<>();
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

            // Проверяем граф на циклические зависимости
            detectCycles();

            // Создаём объекты (бины)
            createBeans();

            // Внедряем зависимости
            injectDependencies();

        } catch (Exception e) {
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

                // получаем конструктор без параметров
                Constructor<?> constructor = scannedClass.getDeclaredConstructor();

                // разрешаем доступ даже если он private
                constructor.setAccessible(true);

                // создаём новый объект бин
                Object bean = constructor.newInstance();

                // генерируем имя бина
                MyComponent myComponent = scannedClass.getAnnotation(MyComponent.class);

                //проверяем не указано ли имя бина в аннотации
                if (!myComponent.beanName().equals(noNameBeanFlag)) {
                    beanName = myComponent.beanName();
                } else {
                    beanName = generateBeanName(scannedClass);
                }

                // проверяем, что такого бина ещё нет
                if (beanContainer.containsKey(beanName)) {
                    throw new IllegalStateException("Duplicate bean name: " + beanName);
                }

                // сохраняем бин в контейнер
                beanContainer.put(beanName, bean);
            }
        }
    }


    //Генерирует имя бина. UserService -> userService
    public String generateBeanName(Class<?> clazz) {

        String simpleName = clazz.getSimpleName();

        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }


    //Контейнер проходит по всем бинам, ищет поля с @MyAutowired и вставляет нужный бин.
    public void injectDependencies() {

        for (Object bean : beanContainer.values()) {

            //получаем текущий класс
            Class<?> currentClass = bean.getClass();


            //идем циклом по ирерахии классов(на случай если есть наследование)
            while (currentClass != Object.class) {

                // получаем все поля класса
                Field[] fields = currentClass.getDeclaredFields();

                for (Field field : fields) {

                    // если поле не помечено @MyAutowired — пропускаем
                    if (!field.isAnnotationPresent(MyAutowired.class)) {
                        continue;
                    }

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
                //получаем род класс
                currentClass = currentClass.getSuperclass();
            }
        }
    }


    // поиск нужного бина для поля.
    private Object getObject(Field field) {

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
        for (Class<?> scannedClass : scannedClasses) {
            if (!scannedClass.isAnnotationPresent(MyComponent.class)) {
                continue;
            }
            List<Class<?>> depList = new ArrayList<>();
            Field[] fields = scannedClass.getDeclaredFields();

            for (Field field : fields) {
                if (!field.isAnnotationPresent(MyAutowired.class)) {
                    continue;
                }
                Class<?> dependencyClass = field.getType();
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

        //цикл по завимостям, и рекурсия по этим же зависимостям
        for (Class<?> dependency : dependencies) {
            checkDependencies(dependency, visited, visiting);
        }

        visiting.remove(clazz);
        visited.add(clazz);
    }


    // Получить бин по имени
    public Object getBean(String name) {

        Object bean = beanContainer.get(name);

        if (bean == null) {
            throw new IllegalStateException("No bean found with name: " + name);
        }

        return bean;
    }


    // Получить бин по типу

    public <T> T getBean(Class<T> type) {
        List<Object> candidates = new ArrayList<>();

        // ищем все бины подходящего типа
        for (Object bean : beanContainer.values()) {
            if (type.isAssignableFrom(bean.getClass())) {
                candidates.add(bean);
            }
        }

        // если ничего не нашли
        if (candidates.isEmpty()) {
            throw new IllegalStateException("No bean found for type: " + type.getName());
        }

        // если нашли несколько — ошибка
        if (candidates.size() > 1) {
            throw new IllegalStateException(
                    "Multiple beans found for type: " + type.getName() + ". Use @MyQualifier to specify which bean to inject.");
        }

        return type.cast(candidates.get(0));
    }

}
