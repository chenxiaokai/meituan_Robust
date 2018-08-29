package com.meituan.robust;

/**
 * Created by hedex on 16/6/3.
 * a map record the class name before ProGuard and after ProGuard
 */
//混淆后的类名和补丁中转发器的映射关系
public class PatchedClassInfo {
    //patchedClassName中的所有方法 转发到 patchClassName中去
    //patchedClassName 是混淆的类名
    public String patchedClassName;
    //patchClassName 是补丁中 实现了ChangeQuickRedirect 的类
    public String patchClassName;

    //PatchedClassInfo 是在 打补丁的时候 用 javassist 在字符串中 调用构造函数 来 构建 PatchedClassInfo 对象
    public PatchedClassInfo(String patchedClassName, String patchClassName) {
        this.patchedClassName = patchedClassName;
        this.patchClassName = patchClassName;
    }
}
