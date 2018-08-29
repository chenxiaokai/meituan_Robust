package com.meituan.robust.autopatch

import com.meituan.robust.Constants
import com.meituan.robust.utils.JavaUtils

class ReadXML {
    private static robust;

    public static void readXMl(String path) {
        //解析 E:\github\Robust-master\app\robust.xml
        robust = new XmlSlurper().parse(new File("${path}${File.separator}${Constants.ROBUST_XML}"))

        //读取配置的补丁包名
        if (robust.patchPackname.name.text() != null && "" != robust.patchPackname.name.text())
            Config.patchPackageName = robust.patchPackname.name.text()

        Config.isManual = robust.switch.manual != null && "true" == String.valueOf(robust.switch.manual.text())
        //是否支持混淆开关
        if (robust.switch.proguard.text() != null && "" != robust.switch.proguard.text())
            Config.supportProGuard = Boolean.valueOf(robust.switch.proguard.text()).booleanValue();
        //读取mapping文件
        if (robust.mappingFile.name.text() != null && "" != robust.mappingFile.name.text()) {
            Config.mappingFilePath = robust.mappingFile.name.text()
        } else {
            Config.mappingFilePath = "${path}${Constants.DEFAULT_MAPPING_FILE}"
        }

        if (Config.supportProGuard&&(Config.mappingFilePath == null || "" == Config.mappingFilePath || !(new File(Config.mappingFilePath)).exists())) {
            throw new RuntimeException("Not found ${Config.mappingFilePath}, please put it on your project's robust dir or change your robust.xml !");
        }

        for (name in robust.patchPackClass.name) {
            Config.modifiedClassNameList.add(String.valueOf(name.text()).trim());
        }

        for (name in robust.patchMethodSignure.name) {
            if (!JavaUtils.isMethodSignureContainPatchClassName(String.valueOf(name.text()), Config.modifiedClassNameList))
                throw new RuntimeException("input patchMethodSignure in robust.xml error,there are more than one patch classes,you need to config full class name and java method sigure");
            Config.patchMethodSignatureSet.add(String.valueOf(name.text()).trim());
        }
        //热修复包 列表
        for (name in robust.packname.name) {
            Config.hotfixPackageList.add(name.text());
        }
        for (name in robust.newlyAddClass.name) {
            Config.newlyAddedClassNameList.add(name.text());
        }

        //是否捕获补丁中所有异常开关
        if (robust.switch.catchReflectException.text() != null && "" != robust.switch.catchReflectException.text())
            Config.catchReflectException = Boolean.valueOf(robust.switch.catchReflectException.text()).booleanValue();

        //是否在补丁加上log
        if (robust.switch.patchLog.text() != null && "" != robust.switch.patchLog.text())
            Constants.isLogging = Boolean.valueOf(robust.switch.patchLog.text()).booleanValue();

        //自动化补丁中，不需要反射处理的类
        for (name in robust.noNeedReflectClass.name) {
            Config.noNeedReflectClassSet.add(name.text());
        }



    }
}
