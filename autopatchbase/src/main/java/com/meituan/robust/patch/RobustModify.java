package com.meituan.robust.patch;

/**
 * Created by mivanzhang on 16/12/9.
 *
 * A backup(支持) for annotation Modify, in some situation Modify will not work, such as Generic
 *
 *上面的Modify注解并没有完美的标记所有的方法，这是因为在泛型、匿名内部类等问题上，注解会由于泛型的擦除等问题，会产生移动（我称之为注解的漂移），\
 * 感兴趣的可以自己写一个泛型的方法，使用命令javac -p -v +your.class查看一下泛型和匿名内部类到底是如何实现的，RobustModify这个类就是为了解决注解移动的问题，
 * 在这个类中有一个方法modify，针对泛型、匿名内部类等需要在泛型方法里面使用RobustModify.modify();来标注需要打入补丁中的方法。
 */

/*
1): Java编译器的优化处理工作:
        泛型方法和lambda方法问题以及处理
            Java编译器的优化工作包括Java编译器会自动生成一些桥方法以及移动代码的位置等，比较典型的就是泛型方法、内部类和Lambda表达式。补丁自动化的过程中使用注解来标注需要补丁的方法，
            所以当Java编译器针对泛型移动代码时，注解也会被移动，直接导致补丁上线后无法修复问题。以Java编译器对泛型方法的处理为例，Java编译器会为泛型方法生成一个桥方法（在桥方法里面调用真正的方法，
            桥方法的参数是object的类型，注意这类桥方法Robust热更新系统并没有对其插桩），同时Java编译器把原方法上的注解移动到桥方法上，针对泛型方法制作补丁时，就变成了针对泛型方法的桥方法制作补丁了。
            Lambda表达式也与此类似，编译器把Lambda表达式的内容，移到了一个新的方法（Java编译器为我们生成的access开头的方法）里面去，而且我们还无法给Lambda表达式加上注解。

            为了解决上述的问题，自动化提供了一个静态方法（Robust.modify()），支持在泛型或者Lambda表达式里面调用这个静态方法，自动化扫描所有的方法调用，
            检测到这个静态方法的调用就就可以找到找到需要制作补丁的方法。这样就可以避免由于Java编译器做的一些优化工作导致我们无法修复预期的bug

        Java内部类问题已经处理
            与这个问题类似的，还有内部类的问题，这个问题和ProGuard交织在一起。对于构造方法是私有的内部类，Java编译器也会生成一个包访问性的构造方法，以便于外部类访问。
            只好采取一种保守的措施，制作补丁的时候把内部类构造方法的访问性改为public，然后直接反射这个public的构造函数。这样做就避免了编译器优化这一步，确保可以反射到正确的构造方法。
 */
public final class RobustModify {
    public static final void modify() {

    }
}
