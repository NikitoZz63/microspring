package yabudu.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@MyComponent
@Retention(RetentionPolicy.RUNTIME)
public @interface MyController {
}
