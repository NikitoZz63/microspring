package yabudu.testClasses;

import yabudu.annotation.MyAutowired;
import yabudu.annotation.MyComponent;
import yabudu.annotation.MyPostConstruct;
import yabudu.annotation.MyPreDestroy;

@MyComponent
public class User {

    @MyAutowired
    private Person person;

    public User() {
        System.out.println("User constructor");
    }

    @MyPostConstruct
    public void init() {
        System.out.println("User init");
    }

    @MyPreDestroy
    public void destroy() {
        System.out.println("User destroy");
    }

}
