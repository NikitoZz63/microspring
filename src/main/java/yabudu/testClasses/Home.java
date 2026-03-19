package yabudu.testClasses;

import yabudu.annotation.MyAutowired;
import yabudu.annotation.MyComponent;
import yabudu.annotation.MyPostConstruct;
import yabudu.annotation.MyPreDestroy;

@MyComponent
public class Home {

    @MyAutowired
    private User user;

    public Home() {
        System.out.println("Home constructor");
    }

    @MyPostConstruct
    public void init() {
        System.out.println("Home init");
    }

    @MyPreDestroy
    public void destroy() {
        System.out.println("Home destroy");
    }
}
