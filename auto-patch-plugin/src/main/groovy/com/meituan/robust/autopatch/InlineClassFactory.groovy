package com.meituan.robust.autopatch

import com.meituan.robust.utils.JavaUtils
import javassist.CannotCompileException
import javassist.CtClass
import javassist.CtMethod
import javassist.expr.ExprEditor
import javassist.expr.MethodCall

class InlineClassFactory {
    //内联类为key，value 是内类中的所有内联方法集合
    private HashMap<String, List<String>> classInLineMethodsMap = new HashMap<>();
    private static InlineClassFactory inlineClassFactory = new InlineClassFactory();

    private InlineClassFactory() {

    }

    public static void init() {
        inlineClassFactory = new InlineClassFactory();;
    }

    //所有内联方法
    public static Set getAllInLineMethodLongname() {
        Set<String> set=new HashSet<>();
        for(String key:inlineClassFactory.classInLineMethodsMap.keySet()){
            set.addAll(inlineClassFactory.classInLineMethodsMap.get(key));
        }
        return set;
    }

    def dealInLineClass(String patchPath) {
        //pay attention to order
        Set usedClass = new HashSet();
        //有@Add注解在类上 的类名称集合
        usedClass.addAll(Config.newlyAddedClassNameList);

        //@Add注解在类调用的所有内联类集合
        Set newlyAddedClassInlineSet = getAllInlineClasses(usedClass, null);

        usedClass.addAll(newlyAddedClassInlineSet);
        usedClass.addAll(Config.modifiedClassNameList) //有@Modify注解类名称的集合

        //usedClass 包含有 1)@Add注解在类上 的类名称集合 2)@Add注解在类调用的所有内联类集合  3)@Modify注解类名称集合
        //Config.patchMethodSignatureSet 有@Modify注解方法 和 RobustModify.modify() 调用方法  的 方法签名集合
        Set inLineClassNameSet = getAllInlineClasses(usedClass, Config.patchMethodSignatureSet);  //1)得到有@Modify注解方法和RobustModify.modify() 调用方法里面包含内联方法的内联类 和 2)usedClass中包含内联方法，遍历方法每一行得到内联类
        inLineClassNameSet.removeAll(newlyAddedClassInlineSet)
        inLineClassNameSet.addAll(classInLineMethodsMap.keySet())

        //all inline patch class   inLineClassNameSet为所有内联类集合,里面内联类是有些方法内联并被消除了
        createHookInlineClass(inLineClassNameSet)

        //针对修改类的linepatch
        for (String fullClassName : inLineClassNameSet) {
            CtClass inlineClass = Config.classPool.get(fullClassName);
            //得到fullClassName中的所有内联方法
            List<String> inlineMethod = classInLineMethodsMap.getOrDefault(fullClassName, new ArrayList<String>());
            // NameManager.getInstance().getInlinePatchName(inlineClass.name) 返回 com.meituan.robust.patch+"."+SecondActivity+InLinePatch 以InLinePatch的后缀
            CtClass inlinePatchClass = PatchesFactory.createPatch(patchPath, inlineClass, true, NameManger.getInstance().getInlinePatchName(inlineClass.name), inlineMethod.toSet())
            inlinePatchClass.writeFile(patchPath)
        }
    }


    def dealInLineMethodInNewAddClass(String patchPath, List newAddClassList) {
        for (String fullClassName : newAddClassList) {
            CtClass newlyAddClass = Config.classPool.get(fullClassName);
            newlyAddClass.defrost();
            newlyAddClass.declaredMethods.each { method ->
                method.instrument(new ExprEditor() {
                    public void edit(MethodCall m) throws CannotCompileException {
                        repalceInlineMethod(m, method, true);
                    }
                })
            }
            newlyAddClass.writeFile(patchPath);
        }
    }

    def createHookInlineClass(Set inLineClassNameSet) {
        for (String fullClassName : inLineClassNameSet) {
            CtClass inlineClass = Config.classPool.get(fullClassName);
            // NameManager.getInstance().getInlinePatchName(inlineClass.name) 返回 com.meituan.robust.patch+"."+SecondActivity+InLinePatch 以InLinePatch的后缀
            CtClass inlinePatchClass = PatchesFactory.cloneClass(inlineClass, NameManger.getInstance().getInlinePatchName(inlineClass.name), null)
            //内联类增加构造函数
            inlinePatchClass = JavaUtils.addPatchConstruct(inlinePatchClass, inlineClass)
            PatchesFactory.createPublicMethodForPrivate(inlinePatchClass)
        }
    }
/***
 *
 * @param usedClass
 * @param patchMethodSignureSet 只查找指定的方法体来确认内联类，如果全部的类则传递null
 * @return
 */
    def Set getAllInlineClasses(Set usedClass, Set patchMethodSignureSet) {
        //temInLineFirstSet包含usedClass内联方法的类集合
        HashSet temInLineFirstSet = initInLineClass(usedClass, patchMethodSignureSet);

        //继续遍历temInLineFirstSet 中所有方法调用 是否还有内联方法集合类
        HashSet temInLineSecondSet = initInLineClass(temInLineFirstSet, patchMethodSignureSet);

        temInLineSecondSet.addAll(temInLineFirstSet);

        //第一次temInLineFirstSet要和temInLineSecondSet第二次获取的内联类数量相同，则表明找出了所有的内联类
        while ((temInLineFirstSet.size() < temInLineSecondSet.size())) {
            temInLineFirstSet.addAll(initInLineClass(temInLineSecondSet, patchMethodSignureSet));
            //这个循环有点饶人，initInLineClass返回的是temInLineListSecond中所有的内联类
            temInLineSecondSet.addAll(initInLineClass(temInLineFirstSet, patchMethodSignureSet));
        }

        return temInLineSecondSet;
    }

    // patchPath = app/build/outputs/robust/
    //patchPath 补丁生成路径， list 所有@Add注解在类上 的类名称集合
    public static void dealInLineClass(String patchPath, List list) {
        inlineClassFactory.dealInLineClass(patchPath);
        inlineClassFactory.dealInLineMethodInNewAddClass(patchPath, list);
    }
    /**
     *
     * @param classNameList is modified class List
     * @return all inline classes used in classNameList
     *
     * classNamesSet 有@Add注解在类上 的类名称集合
     * patchMethodSignureSet 为null
     */
    /*
     这里找内联类思路:
        遍历类中的所有方法，扫描每个方法每一行，判断每行方法调用所在的类，然后得到这个类的 混淆对应map，然后判断这个方法是否有对应的混淆方法，
        如果没有则证明该方法被删除，则为内联方法，如果混淆方法在，则不是内联方法
     */
    def HashSet initInLineClass(Set classNamesSet, Set patchMethodSignureSet) {
        //包含内联方法的类集合
        HashSet inLineClassNameSet = new HashSet<String>();
        CtClass modifiedCtclass;
        Set <String>allPatchMethodSignureSet = new HashSet();
        boolean isNewClass=false;
        for (String fullClassName : classNamesSet) {
            if(patchMethodSignureSet!=null) {
                allPatchMethodSignureSet.addAll(patchMethodSignureSet);
            } else{
                isNewClass=true;
            }
            modifiedCtclass = Config.classPool.get(fullClassName)
            //getDeclaredMethods 得到所有方法，不包含继承的方法
            modifiedCtclass.declaredMethods.each {
                method ->
                    //找出modifiedclass中所有内联的类
                    allPatchMethodSignureSet.addAll(classInLineMethodsMap.getOrDefault(fullClassName, new ArrayList())) // getOrDefault() 返回key对应的值，没有返回default值
                    if (isNewClass||allPatchMethodSignureSet.contains(method.longName)) {
//                        isNewClass=false;
                        method.instrument(new ExprEditor() {
                            @Override
                            void edit(MethodCall m) throws CannotCompileException {
                                //1):遍历method每一行的方法调用，m.method 就是每一行的方法CtMethod，m.method.declaringClass就是每一行的方法CtMethod所在的类CtClass，
                                //m.method.delcaringClass.name 就是每一行的方法CtMethod所在类的名称
                                //2):classInLineMethodsMap 以类class为key，类中的所有内联方法集合为value
                                List inLineMethodList = classInLineMethodsMap.getOrDefault(m.method.declaringClass.name, new ArrayList());
                                //得到 mapping 的 映射关系
                                ClassMapping classMapping = ReadMapping.getInstance().getClassMapping(m.method.declaringClass.name);

                                //查看混淆映射文件中(mapping.txt)，查看mapping.txt中没有混淆的方法，是否有对应的混淆方法，如果没有，说明方法已经被内联删除了
                                if (null != classMapping && classMapping.memberMapping.get(ReflectUtils.getJavaMethodSignureWithReturnType(m.method)) == null) {
                                    inLineClassNameSet.add(m.method.declaringClass.name);
                                    if (!inLineMethodList.contains(m.method.longName)) {
                                        //内联方法集合
                                        inLineMethodList.add(m.method.longName);
                                        //内联类为key，key中的内联方法为集合inLineMethodList
                                        classInLineMethodsMap.put(m.method.declaringClass.name, inLineMethodList)
                                    }
                                }
                            }
                        }
                        )
                    }
            }
        }
        return inLineClassNameSet;
    }


    def repalceInlineMethod(MethodCall m, CtMethod method, boolean isNewClass) {
        ClassMapping classMapping = ReadMapping.getInstance().getClassMapping(m.method.declaringClass.name);
        //内联方法调用
        if (null != classMapping && classMapping.memberMapping.get(ReflectUtils.getJavaMethodSignureWithReturnType(m.method)) == null) {
            //方法调用 代替为内联方法调用
            m.replace(ReflectUtils.getInLineMemberString(m.method, ReflectUtils.isStatic(method.modifiers), isNewClass));
            return true;
        }
        return false;
    }


}
