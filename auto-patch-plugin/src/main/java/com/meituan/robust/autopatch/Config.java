package com.meituan.robust.autopatch;

import com.meituan.robust.Constants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javassist.ClassPool;
import javassist.CtMethod;

import static com.meituan.robust.Constants.DEFAULT_MAPPING_FILE;

/**
 * Created by mivanzhang on 16/12/2.
 * <p>
 * members read from robust.xml
 */

public final class Config {
    public static boolean catchReflectException = false;
    public static boolean supportProGuard = true;
    public static boolean isLogging = true;
    public static boolean isManual = false;
    public static String patchPackageName = Constants.PATCH_PACKAGENAME;

    public static String mappingFilePath;  // app/robust/mapping.txt 文件

    public static Set<String> patchMethodSignatureSet = new HashSet<>();  //有@Modify注解方法 和 RobustModify.modify() 调用方法  的 方法签名集合

    public static List<String> newlyAddedClassNameList = new ArrayList<String>();  //有@Add注解在类上 的类名称集合

    public static Set newlyAddedMethodSet = new HashSet<String>();  //有@Add注解在类方法上 方法签名集合

    public static List<String> modifiedClassNameList = new ArrayList<String>();  //有@Modify注解在方法上的类名称的集合

    public static List<String> hotfixPackageList = new ArrayList<>();

    public static HashMap<String, Integer> methodMap = new HashMap();  //打桩方法添加标识的 map对象 key是方法签名  value是 方法的数字唯一表示

    public static  String robustGenerateDirectory; // E:\github\Robust-master\app\build\output\robust\

    public static Map<String, List<CtMethod>> invokeSuperMethodMap = new HashMap<>();
    public static ClassPool classPool = new ClassPool();

    public static Set methodNeedPatchSet = new HashSet(); //有@Modify注解方法 和 RobustModify.modify() 调用方法  的 方法签名集合

    public static List<CtMethod> addedSuperMethodList = new ArrayList<>();

    public static Set<String> noNeedReflectClassSet = new HashSet<>(); //不需要反射的类，在robust.xml定义，在下面init方法中增加了两个类Bundle,BaseBundle


    public static void init() {
        catchReflectException = false;
        isLogging = true;
        isManual = false;
        patchPackageName = Constants.PATCH_PACKAGENAME;
        mappingFilePath = DEFAULT_MAPPING_FILE;
        patchMethodSignatureSet = new HashSet<>();
        newlyAddedClassNameList = new ArrayList<String>();
        modifiedClassNameList = new ArrayList<String>();
        hotfixPackageList = new ArrayList<>();
        newlyAddedMethodSet = new HashSet<>();
        invokeSuperMethodMap = new HashMap<>();
        classPool = new ClassPool();
        methodNeedPatchSet = new HashSet();
        addedSuperMethodList = new ArrayList<>();
        noNeedReflectClassSet = new HashSet<>();
        noNeedReflectClassSet.addAll(Constants.NO_NEED_REFLECT_CLASS);
        supportProGuard=true;
    }

}
