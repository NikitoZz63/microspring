package yabudu.testClasses;

import yabudu.annotation.*;

@MyComponent
public class User {

    @MyAutowired
    private Person person;

    @MyAutowired
    private User self;

    @MyLogged
    public void methodA() {
        System.out.println("A start");
        self.methodB();
    }

    @MyLogged
    public void methodB() {
        System.out.println("B work");
    }

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
