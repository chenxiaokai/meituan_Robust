package com.meituan.robust.patch.annotaion;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Created by mivanzhang on 16/12/9.
 * annotaion used for modify classes or methods,classes and methods will be packed into patch.jar/patch.apk
 *
 * 这个注解用来标记被改动的方法或者类，如果这个注解是放在一个类A上面，自动化补丁会生成类APatch，APatch会被打入补丁，原始APK中的类A中每个方法都不会被执行，
 * 只会执行APatch中的方法，相当于把A类“替换为”APatch类（请注意这里只是和替换一个类有相同的效果，实际上A类依然在APK中，此时的A成为了一个傀儡，APatch才是幕后黑手）；
 * 如果注解标记的是方法，则表明这个方法是需要被打入补丁中的，只有被标注的方法会打入补丁，打入补丁之后，就会执行补丁的方法，原始方法不会在执行。
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.TYPE, ElementType.CONSTRUCTOR})
@Retention(RetentionPolicy.CLASS)
@Documented
public @interface Modify {
    String value() default "";
}
