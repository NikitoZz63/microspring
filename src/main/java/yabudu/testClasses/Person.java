package yabudu.testClasses;

import yabudu.annotation.MyComponent;
import yabudu.annotation.MyPostConstruct;
import yabudu.annotation.MyPreDestroy;

@MyComponent
public class Person {

    public Person() {
        System.out.println("Person constructor");
    }

    @MyPostConstruct
    public void init() {
        System.out.println("Person init");
    }

    @MyPreDestroy
    public void destroy() {
        System.out.println("Person destroy");
    }
}
