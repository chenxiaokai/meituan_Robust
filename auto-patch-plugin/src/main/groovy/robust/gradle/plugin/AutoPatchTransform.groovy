package robust.gradle.plugin

import com.android.build.api.transform.*
import com.android.build.gradle.internal.pipeline.TransformManager
import com.meituan.robust.Constants
import com.meituan.robust.autopatch.*
import com.meituan.robust.utils.JavaUtils
import com.meituan.robust.utils.SmaliTool
import javassist.CannotCompileException
import javassist.CtClass
import javassist.CtMethod
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.logging.Logger

import java.util.zip.Deflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
/**
 * Created by mivanzhang on 16/7/21.
 *
 * AutoPatchTransform generate patch dex
 */
class AutoPatchTransform extends Transform implements Plugin<Project> {
    private
    static String dex2SmaliCommand;
    private
    static String smali2DexCommand;
    private
    static String jar2DexCommand;
    public static String ROBUST_DIR;
    Project project
    static Logger logger

    @Override
    void apply(Project target) {
        this.project = target
        logger = project.logger
        initConfig();
        project.android.registerTransform(this)
    }

    def initConfig() {
        //clear
        NameManger.init();
        InlineClassFactory.init();
        ReadMapping.init();
        Config.init();
        //E:\github\Robust-master\app\robust\
        ROBUST_DIR = "${project.projectDir}${File.separator}robust${File.separator}"

        //https://bitbucket.org/JesusFreke/smali/downloads/?tab=downloads 工具下载地址
        def baksmaliFilePath = "${ROBUST_DIR}${Constants.LIB_NAME_ARRAY[0]}"  //baksmali-2.1.2.jar
        def smaliFilePath = "${ROBUST_DIR}${Constants.LIB_NAME_ARRAY[1]}" //smali-2.1.2.jar
        def dxFilePath = "${ROBUST_DIR}${Constants.LIB_NAME_ARRAY[2]}" //dx.jar

        // E:\github\Robust-master\app\build\output\robust\
        Config.robustGenerateDirectory = "${project.buildDir}" + File.separator + "$Constants.ROBUST_GENERATE_DIRECTORY" + File.separator;

        //java -jar baksmali.jar -o classout/ classes.dex  classes.dex反编译的smali文件存在./classout之中
        dex2SmaliCommand = "  java -jar ${baksmaliFilePath} -o classout" + File.separator + "  $Constants.CLASSES_DEX_NAME";

        //java -jar smali.jar classout/ -o classes.dex  使用smali.jar将classouti目录smali文件重新编译为classes.dex
        smali2DexCommand = "   java -jar ${smaliFilePath} classout" + File.separator + " -o "+Constants.PATACH_DEX_NAME;

        //java -jar dx.jar --dex --output=classes.dex meituan.jar   dx 是android 把jar转成dex的工具
        jar2DexCommand = "   java -jar ${dxFilePath} --dex --output=$Constants.CLASSES_DEX_NAME  " + Constants.ZIP_FILE_NAME;

        //解析 E:\github\Robust-master\app\robust.xml 到 Config 类中去
        ReadXML.readXMl(project.projectDir.path);
        //读取methodsMap.robust内容，里面内容是上个打桩插件写入的方法唯一标识的map对象   //  app/robust/methodsMap.robust 文件
        Config.methodMap = JavaUtils.getMapFromZippedFile(project.projectDir.path + Constants.METHOD_MAP_PATH)
    }

    @Override
    String getName() {
        return "AutoPatchTransform"
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(Context context, Collection<TransformInput> inputs, Collection<TransformInput> referencedInputs, TransformOutputProvider outputProvider, boolean isIncremental) throws IOException, TransformException, InterruptedException {
        def startTime = System.currentTimeMillis()
        logger.quiet '================autoPatch start================'
        //拷贝工具在 auto-patch-plugin 中的 resources/libs/下 baksmali-2.1.2.jar smali-2.1.2.jar dx.jar 到  app/robust 目录下
        copyJarToRobust()

        outputProvider.deleteAll()
        def outDir = outputProvider.getContentLocation("main", outputTypes, scopes, Format.DIRECTORY)
        project.android.bootClasspath.each {
            Config.classPool.appendClassPath((String) it.absolutePath)
        }
        def box = ReflectUtils.toCtClasses(inputs, Config.classPool)
        def cost = (System.currentTimeMillis() - startTime) / 1000
        logger.quiet "check all class cost $cost second, class count: ${box.size()}"
        autoPatch(box)
//        JavaUtils.removeJarFromLibs()
        logger.quiet '================method singure to methodid is printed below================'
        JavaUtils.printMap(Config.methodMap)
        cost = (System.currentTimeMillis() - startTime) / 1000
        logger.quiet "autoPatch cost $cost second"
        throw new RuntimeException("auto patch end successfully")
    }

    static def copyJarToRobust() {
        File targetDir = new File(ROBUST_DIR);
        if (!targetDir.exists()) {
            targetDir.mkdirs();
        }
        for (String libName : Constants.LIB_NAME_ARRAY) {
            InputStream inputStream = JavaUtils.class.getResourceAsStream("/libs/" + libName);
            if (inputStream == null) {
                System.out.println("Warning!!!  Did not find " + libName + " ，you must add it to your project's libs ");
                continue;
            }
            File inputFile = new File(ROBUST_DIR + libName);
            try {
                OutputStream inputFileOut = new FileOutputStream(inputFile);
                JavaUtils.copy(inputStream, inputFileOut);
            } catch (Exception e) {
                e.printStackTrace();
                System.out.println("Warning!!! " + libName + " copy error " + e.getMessage());

            }
        }
    }

    def autoPatch(List<CtClass> box) {
        File buildDir = project.getBuildDir();
        // patchPath = app/build/outputs/robust/
        String patchPath = buildDir.getAbsolutePath() + File.separator + Constants.ROBUST_GENERATE_DIRECTORY + File.separator;
        //删除 app/build/outputs/robust 文件夹
        clearPatchPath(patchPath);

        ReadAnnotation.readAnnotation(box, logger);
        if(Config.supportProGuard) {
            ReadMapping.getInstance().initMappingInfo();
        }

        generatPatch(box,patchPath);

        //补丁的class zip压缩到 E:\github\Robust-master\app\build\output\robust\meituan.jar   meituan.jar中
        zipPatchClassesFile()
        //meituan.jar 转变成 dex 文件
        executeCommand(jar2DexCommand)
        //dex 文件 转为 smali文件 到 classout 目录下
        executeCommand(dex2SmaliCommand)
        SmaliTool.getInstance().dealObscureInSmali();
        //smali文件转变成 dex文件
        executeCommand(smali2DexCommand)
        //package patch.dex to patch.jar
        packagePatchDex2Jar()
        deleteTmpFiles()
    }
    def  zipPatchClassesFile(){
        //zipOut   E:\github\Robust-master\app\build\output\robust\meituan.jar
        ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(Config.robustGenerateDirectory+ Constants.ZIP_FILE_NAME));
        // 参数 1)E:\github\Robust-master\app\build\output\robust\com  2)""  3)zipOut
        zipAllPatchClasses(Config.robustGenerateDirectory+Config.patchPackageName.substring(0,Config.patchPackageName.indexOf(".")),"",zipOut);
        zipOut.close();

    }

    //参数 1)E:\github\Robust-master\app\build\output\robust\com  2)""  3)zipOut
    def zipAllPatchClasses(String path, String fullClassName, ZipOutputStream zipOut) {
        File file = new File(path);
        if (file.exists()) {
            fullClassName=fullClassName+file.name;
            if (file.isDirectory()) {
                //目录继续递归
                fullClassName+=File.separator;
                File[] files = file.listFiles();
                if (files.length == 0) {
                    return;
                } else {
                    for (File file2 : files) {
                        zipAllPatchClasses(file2.getAbsolutePath(),fullClassName,zipOut);
                    }
                }
            } else {
                //文件  压缩文件
                zipFile(file,zipOut, fullClassName);
            }
        } else {
            logger.debug("文件不存在!");
        }
    }

    def  generatPatch(List<CtClass> box,String patchPath){
        if (!Config.isManual) {
            if (Config.patchMethodSignatureSet.size() < 1) {
                throw new RuntimeException(" patch method is empty ,please check your Modify annotation or use RobustModify.modify() to mark modified methods")
            }
            //有@Modify注解方法 和 RobustModify.modify() 调用方法  的 方法签名集合
            Config.methodNeedPatchSet.addAll(Config.patchMethodSignatureSet)

            InlineClassFactory.dealInLineClass(patchPath, Config.newlyAddedClassNameList)

            //有@Modify注解在方法上的类名称的集合
            initSuperMethodInClass(Config.modifiedClassNameList);
            //auto generate all class
            for (String fullClassName : Config.modifiedClassNameList) {
                CtClass ctClass = Config.classPool.get(fullClassName)
                CtClass patchClass = PatchesFactory.createPatch(patchPath, ctClass, false, NameManger.getInstance().getPatchName(ctClass.name), Config.patchMethodSignatureSet)
                patchClass.writeFile(patchPath)
                patchClass.defrost();
                createControlClass(patchPath, ctClass)
            }
            //构造PatchesInfoImpl类
            createPatchesInfoClass(patchPath);
            if (Config.methodNeedPatchSet.size() > 0) {
                throw new RuntimeException(" some methods haven't patched,see unpatched method list : " + Config.methodNeedPatchSet.toListString())
            }
        } else {
            autoPatchManually(box, patchPath);
        }

    }
    def deleteTmpFiles(){
        // E:\github\Robust-master\app\build\output\robust\
        File diretcory=new File(Config.robustGenerateDirectory);
        if(!diretcory.isDirectory()){
            throw new RuntimeException("patch directry "+Config.robustGenerateDirectory+" dones not exist");
        }else{
            diretcory.listFiles(new FilenameFilter() {
                @Override
                boolean accept(File file, String s) {
                    return !(Constants.PATACH_JAR_NAME.equals(s))
                }
            }).each {
                if(it.isDirectory()){
                    it.deleteDir()
                }else {
                    it.delete()
                }
            }
        }
    }

    def autoPatchManually(List<CtClass> box, String patchPath) {
        box.forEach { ctClass ->
            if (Config.isManual && ctClass.name.startsWith(Config.patchPackageName)) {
                Config.modifiedClassNameList.add(ctClass.name);
                ctClass.writeFile(patchPath);
            }
        }
    }


    def executeCommand(String commond) {
        Process output = commond.execute(null, new File(Config.robustGenerateDirectory))
        output.inputStream.eachLine { println commond + " inputStream output   " + it }
        output.errorStream.eachLine {
            println commond + " errorStream output   " + it;
            throw new RuntimeException("execute command " + commond + " error");
        }
    }


    //有@Modify注解在方法上的类名称的集合
    def initSuperMethodInClass(List originClassList) {
        CtClass modifiedCtClass;
        for (String modifiedFullClassName : originClassList) {
            List<CtMethod> invokeSuperMethodList = Config.invokeSuperMethodMap.getOrDefault(modifiedFullClassName, new ArrayList());
            //检查当前修改类中使用到类，并加入mapping信息
            modifiedCtClass = Config.classPool.get(modifiedFullClassName);
            modifiedCtClass.defrost();
            modifiedCtClass.declaredMethods.findAll {
                return Config.patchMethodSignatureSet.contains(it.longName)||InlineClassFactory.allInLineMethodLongname.contains(it.longName);
            }.each { behavior ->
                behavior.instrument(new ExprEditor() {
                    @Override
                    void edit(MethodCall m) throws CannotCompileException {
                        //有@Modify注解方法 和 RobustModify.modify() 调用方法 或者 是内联方法 中有调用父类的方法
                        if (m.isSuper()) {
                            if (!invokeSuperMethodList.contains(m.method)) {
                                invokeSuperMethodList.add(m.method);
                            }
                        }
                    }
                });
            }
            //@Modify注解在方法上的类名为key， value是 有@Modify注解方法 和 RobustModify.modify() 调用方法 或者 内联方法调用父类方法的集合
            Config.invokeSuperMethodMap.put(modifiedFullClassName, invokeSuperMethodList);
        }
    }


    def createControlClass(String patchPath, CtClass modifiedClass) {
        CtClass controlClass = PatchesControlFactory.createPatchesControl(modifiedClass);
        controlClass.writeFile(patchPath);
        return controlClass;
    }


    def createPatchesInfoClass(String patchPath) {
        PatchesInfoFactory.createPatchesInfo().writeFile(patchPath);
    }

    def  clearPatchPath(String patchPath) {
        new File(patchPath).deleteDir();
    }

    def  packagePatchDex2Jar() throws IOException {
        //E:\github\Robust-master\app\build\output\robust\patch.dex
        File inputFile=new File(Config.robustGenerateDirectory, Constants.PATACH_DEX_NAME);
        if (!inputFile.exists() || !inputFile.canRead()) {
            throw new RuntimeException("patch.dex is not exists or readable")
        }
        // E:\github\Robust-master\app\build\output\robust\patch.jar
        ZipOutputStream zipOut = new ZipOutputStream(new FileOutputStream(new File(Config.robustGenerateDirectory, Constants.PATACH_JAR_NAME)))
        zipOut.setLevel(Deflater.NO_COMPRESSION)
        FileInputStream fis = new FileInputStream(inputFile)
        zipFile(inputFile,zipOut,Constants.CLASSES_DEX_NAME);
        zipOut.close()
    }

    def zipFile(File inputFile, ZipOutputStream zos, String entryName){
        ZipEntry entry = new ZipEntry(entryName);
        zos.putNextEntry(entry);
        FileInputStream fis = new FileInputStream(inputFile)
        byte[] buffer = new byte[4092];
        int byteCount = 0;
        while ((byteCount = fis.read(buffer)) != -1) {
            zos.write(buffer, 0, byteCount);
        }
        fis.close();
        zos.closeEntry();
        zos.flush();
    }


}