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
    // отсортированный список бинов по зависимостям
    private final List<Class<?>> sortedBeans = new ArrayList<>();

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

            detectCycles();

            topologicalSort();

            // Создаём и инициализируем все BeanPostProcessor,
            registerBeanPostProcessors();

            // Создаём объекты (бины)
            createBeans();


        } catch (Exception e) {
            close();
            throw new RuntimeException("Failed to initialize ApplicationContext", e);
        }
    }

    private void topologicalSort() {
        sortedBeans.clear();
        // inDegree - сколько зависимостей у каждого бина
        Map<Class<?>, Integer> inDegree = new HashMap<>();

        // reverseGraph - обратный граф зависимостей
        // ключ = бин (зависимость)
        // значение = список бинов, которые зависят от него
        Map<Class<?>, List<Class<?>>> reverseGraph = new HashMap<>();

        // для каждого бина:
        // inDegree = 0 (пока считаем, что зависимостей нет)
        // создаём пустой список в reverseGraph
        for (Class<?> clazz : dependGraph.keySet()) {
            inDegree.put(clazz, 0);
            reverseGraph.put(clazz, new ArrayList<>());
        }

        // аполняем inDegree и reverseGraph
        // dependGraph (A зависит от B и C)
        for (Map.Entry<Class<?>, List<Class<?>>> entry : dependGraph.entrySet()) {
            Class<?> clazz = entry.getKey();          // текущий бин
            List<Class<?>> dependencies = entry.getValue(); // его зависимости

            inDegree.put(clazz, dependencies.size());

            // строим reverseGraph, если B готов - теперь A может работать
            for (Class<?> dependency : dependencies) {
                reverseGraph.get(dependency).add(clazz);
            }
        }

        // сюда кладём бины, которые можно создать прямо сейчас
        Queue<Class<?>> queue = new LinkedList<>();

        for (Map.Entry<Class<?>, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        //пока есть кто-то готовый к созданию
        while (!queue.isEmpty()) {

            // достаём из очереди бин без зависимостей
            Class<?> clazz = queue.poll();

            // добавляем его в итоговый список
            sortedBeans.add(clazz);

            // получаем все бины, которые зависят от текущего
            List<Class<?>> dependents = reverseGraph.get(clazz);

            // если никто не зависит — идём дальше
            if (dependents == null) {
                continue;
            }

            // уменьшаем inDegree у зависимых бинов
            for (Class<?> dependent : dependents) {

                // уменьшаем количество оставшихся зависимостей
                inDegree.put(dependent, inDegree.get(dependent) - 1);

                // если зависимостей больше нет → можно создавать бин
                if (inDegree.get(dependent) == 0) {
                    queue.add(dependent);
                }
            }
        }

        // если не все бины обработались → есть циклическая зависимость
        if (sortedBeans.size() != dependGraph.size()) {
            throw new IllegalStateException("Cycle detected in dependency graph");
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

    public List<Class<?>> getScannedClasses() {
        return scannedClasses;
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


    // сначала создаём proxy (через BeanPostProcessor), а только потом делаем injectDependencies.
    // Это для решения проблемы self-invocation:
    // если сначала внедрить зависимости, то self-ссылки будут указывать на обычный объект,
    // а не на proxy, и AOP внутри бина работать не будет.

    //Создаём объекты (бины) для всех классов с аннотацией @MyComponent.
    public void createBeans() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        String beanName;
        // используем отсортированный список, чтобы сначала создавались зависимости
        for (Class<?> scannedClass : sortedBeans) {

            // проверяем есть ли аннотация @MyComponent
            if (isComponent(scannedClass)) {

                if (MyBeanPostProcessor.class.isAssignableFrom(scannedClass)) {
                    continue;
                }

                // генерируем имя бина
                MyComponent myComponent = findMyComponentAnnotation(scannedClass);

                if (myComponent == null) {
                    throw new IllegalStateException("Component annotation not found for: " + scannedClass.getName());
                }
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
                    // Создаём обычный экземпляр класса без зависимостей и без proxy
                    Object bean = createBeanInstance(scannedClass);

                    if (beanContainer.containsKey(beanName)) {
                        throw new IllegalStateException("Duplicate bean name: " + beanName);
                    }

                    // Кладём сырой бин в контейнер ДО инициализации
                    // другие бины найдут его во время injectDependencies
                    // корректно обработаем циклические зависимости
                    // поддержим self-injection
                    beanContainer.put(beanName, bean);

                    // Запускаем lifecycle бина (proxy + inject + @PostConstruct)
                    Object initializedBean = initializeBean(bean, beanName);

                    // Заменяем сырой бин на финальный объект (proxy)
                    beanContainer.put(beanName, initializedBean);
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

    //  получение имени бина для любого класса
    private String resolveBeanName(Class<?> clazz) {
        // Если это proxy (CGLIB), берём оригинальный класс
        if (!clazz.isAnnotationPresent(MyComponent.class)) {
            clazz = clazz.getSuperclass();
        }

        MyComponent component = clazz.getAnnotation(MyComponent.class);

        if (component == null) {
            throw new IllegalStateException("Class is not a bean: " + clazz.getName());
        }

        // если имя задано явно — используем его
        if (!component.beanName().equals(noNameBeanFlag)) {
            return component.beanName();
        }

        // иначе генерируем автоматически
        return generateBeanName(clazz);
    }

    // Контейнер проходит по всем бинам, ищет поля с @MyAutowired и вставляет нужный бин.
    public void injectDependencies() throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        for (Object bean : beanContainer.values()) {
            injectDependencies(bean);
        }
    }

    public void injectDependencies(Object bean) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        // Получаем текущий класс
        Class<?> currentClass = bean.getClass();

        // Идем циклом по иерархии классов(на случай если есть наследование)
        while (currentClass != null && currentClass != Object.class) {

            // получаем все поля класса
            Field[] fields = currentClass.getDeclaredFields();
            for (Field field : fields) {
                // если поле не помечено @MyAutowired — пропускаем
                if (field.isAnnotationPresent(MyAutowired.class)) {

                    // Ищем нужный бин для текущего поля.
                    // Передаём не только само поле, но и бин-владелец,
                    // чтобы внутри getObject можно было проверить что singleton-бин не должен напрямую получать thread-scoped бин
                    Object dependency = getObject(field, bean);

                    // SELF-INJECTION: корректно работаем с proxy
                    Class<?> beanClass = bean.getClass();
                    // proxy не имеет анотации MyComponent(проверяем настоящий класс или proxy)
                    if (!beanClass.isAnnotationPresent(MyComponent.class)) {
                        beanClass = beanClass.getSuperclass();
                    }

                    // Тип поля совместим с текущим бинoм?
                    if (field.getType().isAssignableFrom(beanClass)) {
                        String selfBeanName = resolveBeanName(beanClass);

                        // self-injection: подставляем proxy из контейнера
                        dependency = beanContainer.get(selfBeanName);
                    }

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


    // Поиск нужного бина для поля с учётом владельца
    private Object getObject(Field field, Object ownerBean) throws InvocationTargetException, NoSuchMethodException, InstantiationException, IllegalAccessException {
        // Сначала определяем scope бина-владельца - того объекта, в который мы сейчас пытаемся внедрить зависимость.
        // singleton-бин НЕ должен напрямую содержать ссылку на thread-scoped бин.
        Class<?> ownerClass = ownerBean.getClass();
        MyScope ownerScope = ownerClass.getAnnotation(MyScope.class);
        String ownerScopeValue = ownerScope == null ? "singleton" : ownerScope.scope();

        // Сначала проверяем, есть ли у поля @MyQualifier.
        MyQualifier qualifier = field.getAnnotation(MyQualifier.class);

        if (qualifier != null) {
            String qualifiedBeanName = qualifier.name();

            // используем общий метод поиска бина по имени
            Class<?> qualifiedClass = findBeanClassByName(qualifiedBeanName);

            // Определяем scope выбранного бина
            MyScope dependencyScope = qualifiedClass.getAnnotation(MyScope.class);
            String dependencyScopeValue = dependencyScope == null ? "singleton" : dependencyScope.scope();

            // запрещаем singleton -> thread
            if ("singleton".equals(ownerScopeValue) && "thread".equals(dependencyScopeValue)) {
                throw new IllegalStateException(
                        "Cannot inject thread-scoped bean '" + qualifiedBeanName +
                                "' into singleton bean '" + ownerClass.getName() + "'");
            }

            return getBean(qualifiedBeanName);
        }

        // Если qualifier нет — ищем бин по типу поля.
        Class<?> dependencyType = field.getType();
        List<Class<?>> candidates = new ArrayList<>();

        // Ищем все классы-кандидаты, подходящие по типу.
        for (Class<?> candidate : scannedClasses) {
            if (!isComponent(candidate)) {
                continue;
            }

            if (dependencyType.isAssignableFrom(candidate)) {
                candidates.add(candidate);
            }
        }

        // Если подходящих кандидатов нет — контейнер не сможет внедрить зависимость.
        if (candidates.isEmpty()) {
            throw new IllegalStateException(
                    "No bean found for field '" + field.getName() +
                            "' in class " + ownerClass.getName());
        }

        // Если кандидат ровно один, можно заранее проверить его scope.
        // Это позволяет отловить запрещённую ситуацию singleton - thread до фактического получения бина.
        if (candidates.size() == 1) {
            Class<?> dependencyClass = candidates.get(0);
            MyScope dependencyScope = dependencyClass.getAnnotation(MyScope.class);
            String dependencyScopeValue = dependencyScope == null ? "singleton" : dependencyScope.scope();

            if ("singleton".equals(ownerScopeValue) && "thread".equals(dependencyScopeValue)) {
                throw new IllegalStateException(
                        "Cannot inject thread-scoped bean '" + dependencyClass.getName() +
                                "' into singleton bean '" + ownerClass.getName() + "'");
            }
        }

        // либо вернёт единственный бин, либо бросит ошибку, если кандидатов несколько.
        return getBean(dependencyType);
    }

    private Class<?> findBeanClassByName(String beanName) {
        // Проходим по всем найденным классам
        for (Class<?> candidate : scannedClasses) {

            // Берём только бины
            if (!isComponent(candidate)) {
                continue;
            }

            String candidateBeanName = resolveBeanName(candidate);

            // Если имя совпало — нашли бин
            if (candidateBeanName.equals(beanName)) {
                return candidate;
            }
        }

        // Если не нашли — это ошибка
        throw new IllegalStateException("No bean found with name: " + beanName);
    }


    public void buildDependencyGraph() {
        // Перед построением очищаем старый граф, чтобы при повторном вызове не смешать старые и новые зависимости.
        dependGraph.clear();

        // Проходим по всем найденным классам.
        for (Class<?> scannedClass : scannedClasses) {

            // Граф строим только для классов, которые являются бинами контейнера
            if (!isComponent(scannedClass)) {
                continue;
            }

            // Используем Set, чтобы одна и та же зависимость не добавилась дважды.
            Set<Class<?>> depSet = new LinkedHashSet<>();

            // Поднимаемся по иерархии классов, чтобы увидеть поля родительских классов.
            Class<?> currentClass = scannedClass;

            while (currentClass != null && currentClass != Object.class) {

                // Берём только поля текущего уровня иерархии
                Field[] fields = currentClass.getDeclaredFields();

                for (Field field : fields) {

                    // Нас интересуют только поля, которые контейнер должен инжектить
                    if (!field.isAnnotationPresent(MyAutowired.class)) {
                        continue;
                    }

                    // Сначала проверяем, не указали ли имя бина через @MyQualifier
                    MyQualifier qualifier = field.getAnnotation(MyQualifier.class);

                    if (qualifier != null) {
                        String qualifiedBeanName = qualifier.name();
                        Class<?> qualifiedClass = findBeanClassByName(qualifiedBeanName);

                        // Дополнительно проверяем, что найденный бин вообще совместим с типом поля
                        Class<?> dependencyType = field.getType();
                        if (!dependencyType.isAssignableFrom(qualifiedClass)) {
                            throw new IllegalStateException(
                                    "Bean '" + qualifiedBeanName + "' is not assignable to field '" +
                                            field.getName() + "' in class " + scannedClass.getName());
                        }

                        // игнорируем self-зависимость
                        if (!qualifiedClass.equals(scannedClass)) {
                            depSet.add(qualifiedClass);
                        }

                        // При наличии qualifier не нужно дополнительно искать всех кандидатов по типу
                        continue;
                    }

                    // Если qualifier нет, тогда ищем все подходящие реализации по типу поля
                    Class<?> dependencyType = field.getType();

                    for (Class<?> candidate : scannedClasses) {

                        // Учитываем только реальные бины контейнера.
                        if (!isComponent(candidate)) {
                            continue;
                        }

                        // Проверяем совместимость типа.
                        if (dependencyType.isAssignableFrom(candidate)) {
                            // игнорируем self-зависимость (класс не должен считаться зависимым сам от себя)
                            if (!candidate.equals(scannedClass)) {
                                depSet.add(candidate);
                            }
                        }
                    }
                }

                // Переходим к родительскому классу и продолжаем искать зависимости выше по иерархии.
                currentClass = currentClass.getSuperclass();
            }

            // Преобразуем Set в List и сохраняем зависимости текущего бина в граф.
            dependGraph.put(scannedClass, new ArrayList<>(depSet));
        }
    }


    public void detectCycles() {
        Set<Class<?>> visited = new HashSet<>();
        Set<Class<?>> visiting = new HashSet<>();

        //Если класс уже проверяли на циклические зависимости, то проверять снова не нужно
        for (Class<?> aClass : dependGraph.keySet()) {
            if (!visited.contains(aClass)) {
                checkDependencies(aClass, visited, visiting, new ArrayDeque<>());
            }
        }
    }

    // Проверка циклической зависимости
    public void checkDependencies(Class<?> clazz, Set<Class<?>> visited, Set<Class<?>> visiting, Deque<Class<?>> path) {
        // Если текущий класс уже находится в visiting, значит мы снова пришли в него во время обхода - это цикл.
        if (visiting.contains(clazz)) {
            // StringBuilder для формирования строки цикла
            StringBuilder cycle = new StringBuilder();

            // С какого момента начинать выводить путь цикла
            boolean startAdding = false;

            // path хранит путь обхода:
            for (Class<?> c : path) {

                // Когда встречаем тот же класс где обнаружили цикл, значит отсюда начинается замкнутая часть
                if (c.equals(clazz)) {
                    startAdding = true;
                }

                // Добавляем в строку только часть, которая относится к циклу
                if (startAdding) {
                    cycle.append(c.getSimpleName()).append(" -> ");
                }
            }

            // Замыкаем цикл, добавляя исходный класс ещё раз в конец, чтобы получилось: A -> B -> C -> A
            cycle.append(clazz.getSimpleName());

            // Бросаем исключение с полной цепочкой зависимостей
            throw new IllegalStateException("Cyclic dependency detected: " + cycle);
        }

        // Проверен ли класс ранее(убедились ли мы что нет там цикла)
        if (visited.contains(clazz)) {
            return;
        }
        path.addLast(clazz);

        visiting.add(clazz);

        // Получаем все зависимости класса
        List<Class<?>> dependencies = dependGraph.get(clazz);

        if (dependencies == null) {
            return;
        }

        // Цикл по завимостям, и рекурсия по этим же зависимостям
        for (Class<?> dependency : dependencies) {

            // Игнорируем self-зависимость
            if (dependency.equals(clazz)) {
                continue;
            }
            checkDependencies(dependency, visited, visiting, path);
        }

        visiting.remove(clazz);
        visited.add(clazz);
        path.removeLast();
    }


    // Получить бин по имени
    public Object getBean(String name) throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        // Пытаемся получить бин из контейнера singleton-бинов.
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
            if (!isComponent(clazz)) {
                continue;
            }

            // Унифицированное определение имени бина
            String beanName = resolveBeanName(clazz);

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
        // Если аннотация @MyScope отсутствует - по умолчанию считаем бин singleton
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
        // Не сохраняем его в контейнере!
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
                // Сразу находим класс по имени в threadScopedClasses
                beanClass = threadScopedClasses.get(name);

                // Создаем бин
                Object newBean = createBeanInstance(beanClass);
                // Инициализация бина
                newBean = initializeBean(newBean, name);
                // Записываем бин threadLocalBeans
                threadLocal.set(newBean);
            }
            // Возвращаем актуальный объект из ThreadLocal.
            // Если бин уже существовал для текущего потока — вернётся старый экземпляр.
            // Если бин только что создали — вернётся новый экземпляр, который мы положили через set().
            return threadLocal.get();
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
            if (!isComponent(clazz)) {
                continue;
            }

            // Проверяем, подходит ли класс под требуемый тип
            if (type.isAssignableFrom(clazz)) {
                String beanName = resolveBeanName(clazz);
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
        for (Class<?> scannedClass : scannedClasses) {

            if (!isComponent(scannedClass)) {
                continue;
            }

            if (!MyBeanPostProcessor.class.isAssignableFrom(scannedClass)) {
                continue;
            }

            String beanName = resolveBeanName(scannedClass);

            // Создаём процессор один раз
            Object processorBean = createBeanInstance(scannedClass);

            // Внедряем зависимости
            injectDependencies(processorBean);

            // Добавляем в список процессоров
            beanPostProcessors.add((MyBeanPostProcessor) processorBean);

            // Кладём в контейнер
            beanContainer.put(beanName, processorBean);
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
        // Создаём proxy
        Object earlyProxy = applyBeanPostProcessorsAfterInitialization(bean, beanName);

        // Кладём proxy в контейнер до  injectDependencies, чтобы self (@MyAutowired User self) получил именно proxy
        beanContainer.put(beanName, earlyProxy);

        // Внедряем зависимости в proxy
        injectDependencies(earlyProxy);

        // Вызываем postProcessBeforeInitialization
        Object currentBean = applyBeanPostProcessorsBeforeInitialization(earlyProxy, beanName);

        // Вызываем @PostConstruct
        invokePostConstruct(currentBean);

        Object finalBean = currentBean;

        // Сохраняем ТОЛЬКО финальный объект (proxy) для destroy
        Class<?> beanClass = bean.getClass();
        MyScope scope = beanClass.getAnnotation(MyScope.class);
        String scopeValue = scope == null ? "singleton" : scope.scope();

        if ("singleton".equals(scopeValue)) {
            // Сохраняем proxy, а не оригинальный bean
            initializedBeans.add(finalBean);
        }

        return finalBean;
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


    //Проверяет, является ли класс компонентом (бином) контейнера.
    private boolean isComponent(Class<?> clazz) {

        // Отсекаем аннотации как классы
        if (clazz.isAnnotation()) {
            return false;
        }

        // Проверяем прямое наличие аннотации
        if (clazz.isAnnotationPresent(MyComponent.class)) {
            return true;
        }

        // Проверяем meta-аннотации (аннотации на аннотациях)
        for (var annotation : clazz.getAnnotations()) {

            // annotation.annotationType() это класс аннотации
            // проверяем помечена ли сама аннотация как @MyComponent
            if (annotation.annotationType().isAnnotationPresent(MyComponent.class)) {
                return true;
            }
        }

        return false;
    }

     // Находит аннотацию @MyComponent для класса, если она есть (прямая или meta-аннотация).
    private MyComponent findMyComponentAnnotation(Class<?> clazz) {

        //  Прямое наличие @MyComponent
        if (clazz.isAnnotationPresent(MyComponent.class)) {
            return clazz.getAnnotation(MyComponent.class);
        }

        //  Ищем meta-аннотацию
        for (var annotation : clazz.getAnnotations()) {

            // Получаем тип аннотации
            Class<?> annotationType = annotation.annotationType();

            // Проверяем есть ли у этой аннотации @MyComponent
            if (annotationType.isAnnotationPresent(MyComponent.class)) {

                // Возвращаем саму @MyComponent, которая висит на аннотации
                return annotationType.getAnnotation(MyComponent.class);
            }
        }

        return null;
    }

    public Collection<Object> getAllBeans() {
        return beanContainer.values();
    }

}
