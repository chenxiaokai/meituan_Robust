package com.meituan.robust.autopatch

import com.meituan.robust.Constants
import com.meituan.robust.patch.annotaion.Add
import com.meituan.robust.patch.annotaion.Modify
import com.meituan.robust.utils.JavaUtils
import javassist.CannotCompileException
import javassist.CtClass
import javassist.CtMethod
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import org.codehaus.groovy.GroovyException
import org.gradle.api.logging.Logger
import robust.gradle.plugin.AutoPatchTransform

class ReadAnnotation {
    static Logger logger

    public static void readAnnotation(List<CtClass> box, Logger log) {
        logger = log;
        //储存有@Modify 注解的 方法 签名(签名就是:parameter types such as javassist.CtMethod.setBody(String))
        Set patchMethodSignureSet = new HashSet<String>();
        synchronized (AutoPatchTransform.class) {
            if (Constants.ModifyAnnotationClass == null) {
                //Modify 注解的 Class 对象
                Constants.ModifyAnnotationClass = box.get(0).getClassPool().get(Constants.MODIFY_ANNOTATION).toClass();
            }
            if (Constants.AddAnnotationClass == null) {
                //Add 注解的 Class 对象
                Constants.AddAnnotationClass = box.get(0).getClassPool().get(Constants.ADD_ANNOTATION).toClass();
            }
        }
        box.forEach {
            ctclass ->
                try {
                                              //检测类上ctclass是否带有 @Add注解 有则把类加入到 集合中 返回true
                    boolean isNewlyAddClass = scanClassForAddClassAnnotation(ctclass);
                    //newly add class donnot need scann for modify
                    //如果有@Add注解 就不需要扫描@Modify注解
                    if (!isNewlyAddClass) {
                        //如果没有@Add注解走这里逻辑    scanClassForModifyMethod 扫描ctclass是否有modify注解
                        patchMethodSignureSet.addAll(scanClassForModifyMethod(ctclass));
                        //检测方法中ctclass 是否有@Add注解
                        scanClassForAddMethodAnnotation(ctclass);
                    }
                } catch (NullPointerException e) {
                    logger.warn("something wrong when readAnnotation, " + e.getMessage() + " cannot find class name " + ctclass.name)
                    e.printStackTrace();
                } catch (RuntimeException e) {
                    logger.warn("something wrong when readAnnotation, " + e.getMessage() + " cannot find class name " + ctclass.name)
                    e.printStackTrace();
                }
        }
        println("new add methods  list is ")
        JavaUtils.printList(Config.newlyAddedMethodSet.toList())
        println("new add classes list is ")
        JavaUtils.printList(Config.newlyAddedClassNameList)
        println(" patchMethodSignatureSet is printed below ")
        JavaUtils.printList(patchMethodSignureSet.asList())
        Config.patchMethodSignatureSet.addAll(patchMethodSignureSet);
    }

    //扫描ctclass是否有 @Add 注解
    public static boolean scanClassForAddClassAnnotation(CtClass ctclass) {
        //ctClass是否有 Add注解
        Add addClassAnootation = ctclass.getAnnotation(Constants.AddAnnotationClass) as Add;
        if (addClassAnootation != null && !Config.newlyAddedClassNameList.contains(ctclass.name)) {
            //增加带有 Add注解的类 到 集合中
            Config.newlyAddedClassNameList.add(ctclass.name);
            return true;
        }

        return false;
    }

    public static void scanClassForAddMethodAnnotation(CtClass ctclass) {

        ctclass.defrost();
        ctclass.declaredMethods.each { method ->
            if (null != method.getAnnotation(Constants.AddAnnotationClass)) {
                Config.newlyAddedMethodSet.add(method.longName)
            }
        }
    }

    public static Set scanClassForModifyMethod(CtClass ctclass) {
        Set patchMethodSignureSet = new HashSet<String>();  //储存每个ctclass里面有@Modify 注解的 方法 签名(签名就是:parameter types such as javassist.CtMethod.setBody(String))
        boolean isAllMethodsPatch = true;

        //遍历所有方法上 @Modify 注解的 方法
        //declaredMethods返回所有方法，不包括继承方法。  groovy数组findAll表示过滤数组中方法中有@Modify注解方法，过滤的新数组再each遍历
        ctclass.declaredMethods.findAll {
            return it.hasAnnotation(Constants.ModifyAnnotationClass);
        }.each {
            method ->
                isAllMethodsPatch = false;
                addPatchMethodAndModifiedClass(patchMethodSignureSet, method);
        }

        //do with lamda expression
        ctclass.defrost(); //解冻

        //遍历ctclass所有打桩的方法
        ctclass.declaredMethods.findAll {
            return Config.methodMap.get(it.longName) != null;
        }.each { method ->

            //方法里面，有调用RobustModify.modify()的泛型 lambda 的方法，这个方法需要添加进来
            method.instrument(new ExprEditor() {
                @Override
                public void edit(MethodCall m) throws CannotCompileException {
                    try {
                        if (Constants.LAMBDA_MODIFY.equals(m.method.declaringClass.name)) {
                            //遍历method每一行的方法调用，m.method 就是每一行的方法CtMethod，m.method.declaringClass就是每一行的方法CtMethod所在的类CtClass，
                            //m.method.delcaringClass.name 就是每一行的方法CtMethod所在类的名称
                            isAllMethodsPatch = false;
                            addPatchMethodAndModifiedClass(patchMethodSignureSet, method);
                        }
                    } catch (javassist.NotFoundException e) {
                        e.printStackTrace()
                        logger.warn("  cannot find class  " + method.longName + " line number " + m.lineNumber + " this class may never used ,please remove this class");
                    }
                }
            });
        }

        //如果类上有 @Modify注解 则所有打桩的方法都需要加入 patchMethodSignureSet 集合
        Modify classModifyAnootation = ctclass.getAnnotation(Constants.ModifyAnnotationClass) as Modify;
        if (classModifyAnootation != null) {
            if (isAllMethodsPatch) {
                if (classModifyAnootation.value().length() < 1) {
                    ctclass.declaredMethods.findAll {
                        return Config.methodMap.get(it.longName) != null;
                    }.each { method ->
                        addPatchMethodAndModifiedClass(patchMethodSignureSet, method);
                    }
                } else {
                    ctclass.getClassPool().get(classModifyAnootation.value()).declaredMethods.findAll {
                        return Config.methodMap.get(it.longName) != null;
                    }.each { method ->
                        addPatchMethodAndModifiedClass(patchMethodSignureSet, method);
                    }
                }
            }
        }
        return patchMethodSignureSet;
    }

    public static Set addPatchMethodAndModifiedClass(Set patchMethodSignureSet, CtMethod method) {
        //Config.mehtodMap 是读取methodsMap.robust里面的内容记录每个方法的唯一方法序列
        if (Config.methodMap.get(method.longName) == null) { //等于null表明方法没有打桩
            print("addPatchMethodAndModifiedClass pint methodmap ");
            JavaUtils.printMap(Config.methodMap);
            throw new GroovyException("patch method " + method.longName + " haven't insert code by Robust.Cannot patch this method, method.signature  " + method.signature + "  ");
        }
        //返回方法上的 @Modify 注解对象
        Modify methodModifyAnootation = method.getAnnotation(Constants.ModifyAnnotationClass) as Modify;
        //method声明的类上是否有@Modify注解对象
        Modify classModifyAnootation = method.declaringClass.getAnnotation(Constants.ModifyAnnotationClass) as Modify;

        if ((methodModifyAnootation == null || methodModifyAnootation.value().length() < 1)) {
            //no annotation value 没有注解value值
            patchMethodSignureSet.add(method.longName);
            if (!Config.modifiedClassNameList.contains(method.declaringClass.name))
                Config.modifiedClassNameList.add(method.declaringClass.name);
        } else {
            //use value in annotation  使用注解value值
            patchMethodSignureSet.add(methodModifyAnootation.value());
        }

        if (classModifyAnootation == null || classModifyAnootation.value().length() < 1) {
            if (!Config.modifiedClassNameList.contains(method.declaringClass.name)) {
                Config.modifiedClassNameList.add(method.declaringClass.name);
            }
        } else {
            if (!Config.modifiedClassNameList.contains(classModifyAnootation.value())) {
                Config.modifiedClassNameList.add(classModifyAnootation.value());
            }
        }
        return patchMethodSignureSet;
    }
}
