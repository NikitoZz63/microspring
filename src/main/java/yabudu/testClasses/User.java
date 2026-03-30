package yabudu.testClasses;

import yabudu.annotation.*;

@MyComponent
public class User {

    @MyAutowired
    private Person person;

    @MyAutowired
    private User self;

    public User() {
        System.out.println("User constructor");
    }

    @MyLogged
    public void methodA() {
        System.out.println("A start");
        self.methodB();
    }

    @MyLogged
    public void methodB() {
        System.out.println("B work");
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
