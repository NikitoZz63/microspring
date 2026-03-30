package yabudu.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Target(ElementType.METHOD)
@Retention(RUNTIME)
public @interface MyRequestMapping {
    // путь (/hello)
    String path();

    // HTTP метод (GET, POST)
    String method();
}
