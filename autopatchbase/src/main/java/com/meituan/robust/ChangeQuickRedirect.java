package com.meituan.robust;

/**
 * Created by c_kunwu on 16/5/10.
 *
 * 我们在代码中插入的字段就是这个接口，同时这个接口也是上述xxcontrol的实现接口
 */
public interface ChangeQuickRedirect {
    //accessDispatch在自动化补丁中这是对补丁方法的转发。
    Object accessDispatch(String methodName, Object[] paramArrayOfObject);

    //方法isSupport是用来判断方法是否需要被替换，如果被转发，则会调用accessDispatch方法，accessDispatch方法会对环境进行一些初始化之后调用补丁类中的响应方法
    boolean isSupport(String methodName, Object[] paramArrayOfObject);
}
