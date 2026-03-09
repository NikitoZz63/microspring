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

    private final Map<String, Object> beanContainer = new HashMap<>();
    private String basePackager;
    List<Class<?>> scannedClasses = new ArrayList<>();

    public ApplicationContext(String basePackager) {
        this.basePackager = basePackager;

        try {
            File baseDir = findPackageDirectory(basePackager);

            scanDirectory(baseDir.getAbsolutePath(), basePackager);

            createBeans();

            injectDependencies();

        } catch (Exception e) {
            throw new RuntimeException("Failed to initialize ApplicationContext", e);
        }
    }


    public String getPackagePath(String basePackager) {
        return basePackager.replace(".", "/");
    }

    public File findPackageDirectory(String path) throws MalformedURLException {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL url = classLoader.getResource(getPackagePath(path));
        if (url == null) {
            throw new RuntimeException("Package not found in classpath: " + path);
        }
        return new File(url.getFile());
    }


    public void scanDirectory(String currentDirectory,String currentPackageName) {
        File directory = new File(currentDirectory);
        if (!directory.exists() || !directory.isDirectory()) {
            return;
        }
        File[] files = directory.listFiles();
        if (files == null) return;

        for (File file : files) {
            if (file.isDirectory()) {
                String folderName = file.getName();
                String newPackage = currentPackageName + "." + folderName;
                scanDirectory(file.getAbsolutePath(), newPackage);
            }

            else if (file.getName().endsWith(".class")) {
                String className = file.getName().replace(".class", "");
                String fullClassName = currentPackageName + "." + className;
                try {
                    Class<?> clazz = Class.forName(fullClassName);
                    scannedClasses.add(clazz);
                    System.out.println("Found class: " + clazz.getName());
                } catch (ClassNotFoundException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public void createBeans() throws NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        for (Class<?> scannedClass : scannedClasses) {
            if (scannedClass.isAnnotationPresent(MyComponent.class)) {
                Constructor<?> constructor = scannedClass.getDeclaredConstructor();
                constructor.setAccessible(true);
                Object bean = constructor.newInstance();
                System.out.println(bean.getClass().getName());
                String beanName = generateBeanName(scannedClass);
                if (beanContainer.containsKey(beanName)) {
                    throw new IllegalStateException("Duplicate bean name: " + beanName);
                }
                beanContainer.put(beanName, bean);
            }
        }
    }

    public String generateBeanName(Class<?> clazz) {
        String simpleName = clazz.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    public void injectDependencies() {
        for (Object bean : beanContainer.values()) {

            Field[] fields = bean.getClass().getDeclaredFields();

            for (Field field : fields) {

                if (!field.isAnnotationPresent(MyAutowired.class)) {
                    continue;
                }

                Object dependency = getObject(field);

                try {
                    field.setAccessible(true);
                    field.set(bean, dependency);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Failed to inject dependency: " + field.getName(), e);
                }
            }
        }
    }

    private Object getObject(Field field) {

        MyQualifier qualifier = field.getAnnotation(MyQualifier.class);
        if (qualifier != null) {

            String beanName = qualifier.name();
            Object bean = beanContainer.get(beanName);

            if (bean == null) {
                throw new IllegalStateException("No bean found with name: " + beanName);
            }
            return bean;
        }

        Class<?> dependencyType = field.getType();
        List<Object> candidates = new ArrayList<>();

        for (Object value : beanContainer.values()) {
            if (dependencyType.isAssignableFrom(value.getClass())) {
                candidates.add(value);
            }
        }

        if (candidates.isEmpty()) {
            throw new IllegalStateException("No bean found for type: " + dependencyType.getName());
        }

        if (candidates.size() > 1) {
            throw new IllegalStateException("Multiple beans found for type: " + dependencyType.getName() +
                            ". Use @MyQualifier.");
        }

        return candidates.get(0);
    }

    public Object getBean(String name) {
        Object bean = beanContainer.get(name);

        if (bean == null) {
            throw new IllegalStateException("No bean found with name: " + name);
        }

        return bean;
    }

    public <T> T getBean(Class<T> type) {

        for (Object bean : beanContainer.values()) {
            if (type.isAssignableFrom(bean.getClass())) {
                return type.cast(bean);
            }
        }

        throw new IllegalStateException("No bean found for type: " + type.getName());
    }



}
