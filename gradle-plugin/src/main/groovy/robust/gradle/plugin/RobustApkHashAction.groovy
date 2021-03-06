package robust.gradle.plugin

import com.meituan.robust.Constants
import org.gradle.api.Action
import org.gradle.api.Project

import java.security.MessageDigest

/**
 * Created by hedex on 17/2/14.
 *
 * calculate unique string for each apk,you can get the string in file located in build/outputs/robust/robust.apkhash
 *
 * 创建 robust.apkhash 文件
 */
class RobustApkHashAction implements Action<Project> {
    @Override
    void execute(Project project) {
        project.android.applicationVariants.each { variant ->
            // variant 的值 只有 debug  和 release
            //variant.name = debug
            //variant.name = release

            // packageTask 是 packageRelease 或者 packageDebug
            def packageTask = project.tasks.findByName("package${variant.name.capitalize()}")

            if (packageTask == null) {
                return
            }

            packageTask.doFirst {
//                project.logger.quiet("===start compute robust apk hash===")
//                def startTime = System.currentTimeMillis()

                // partFiles存储  resourceFile ，dexFolders ，javaResourceFiles， jniFolders， assets 的目录File 对象
                List<File> partFiles = new ArrayList<>()

                // E:\github\Robust-master\app\build\intermediates\res\resources-release.ap_  或者 resources-debug.ap_
                File resourceFile = packageTask.resourceFile
                if (null == resourceFile) {
                    return
                }
                partFiles.add(resourceFile)

                Collection<File> dexFolders = null
                try {
                    // E:\github\Robust-master\app\build\intermediates\transforms\dex\release\folders\1000\1f\main
                    dexFolders = packageTask.dexFolders
                } catch (MissingPropertyException e) {
                    // api is not public
                }
                if (null != dexFolders) {
                    partFiles.addAll(dexFolders)
                }

                Collection<File> javaResourceFiles = null
                try {
                    javaResourceFiles = packageTask.javaResourceFiles
                } catch (MissingPropertyException e) {
                    // api is not public
                }
                if (null != javaResourceFiles) {
                    partFiles.addAll(javaResourceFiles)
                }


                Collection<File> jniFolders = null
                try {
                    jniFolders = packageTask.jniFolders
                } catch (MissingPropertyException e) {
                    // api is not public
                }
                if (null != jniFolders) {
                    partFiles.addAll(jniFolders)
                }


                try {
                } catch (MissingPropertyException e) {
                    // api is not public
                }

                File assets = null;
                try {
                    // E:\github\Robust-master\app\build\intermediates\assets\release
                    assets = packageTask.assets
                } catch (MissingPropertyException e) {
                    // Android Gradle Plugin version < 2.2.0-beta1
                }

                if (null != assets) {
                    partFiles.add(assets)
                }

                String robustHash = computeRobustHash(partFiles)

                if (null != assets) {
                    // Android Gradle Plugin is 2.2.0-beta1 + , assets is able to access
                    createHashFile(assets.absolutePath, Constants.ROBUST_APK_HASH_FILE_NAME, robustHash)
                } else {
                    // add robustHashFile to resourceFile
                    File robustHashFile = createHashFile(resourceFile.parentFile.absolutePath, Constants.ROBUST_APK_HASH_FILE_NAME, robustHash)
                    RobustApkHashZipUtils.addApkHashFile2ApFile(resourceFile, robustHashFile);
                }

                String buildRubustDir = "${project.buildDir}" + File.separator + "$Constants.ROBUST_GENERATE_DIRECTORY" + File.separator
                createHashFile(buildRubustDir, Constants.ROBUST_APK_HASH_FILE_NAME, robustHash)

//                def cost = (System.currentTimeMillis() - startTime) / 1000
//                logger.quiet "robust apk hash is $robustHash"
//                logger.quiet "compute robust apk hash cost $cost second"
//                project.logger.quiet("===compute robust apk hash end===")
            }
        }
    }


    def String computeRobustHash(ArrayList<File> partFiles) {
        File sumFile = new File("temp_robust_sum.zip")
        RobustApkHashZipUtils.packZip(sumFile, partFiles)
        String apkHashValue = fileMd5(sumFile)
        if (sumFile.exists()) {
            sumFile.delete()
        }
        return apkHashValue
    }

    def String fileMd5(File file) {
        if (!file.isFile()) {
            return "";
        }
        MessageDigest digest;
        byte[] buffer = new byte[4096];
        int len;
        try {
            digest = MessageDigest.getInstance("MD5");
            FileInputStream inputStream = new FileInputStream(file);
            while ((len = inputStream.read(buffer, 0, 1024)) != -1) {
                digest.update(buffer, 0, len);
            }
            inputStream.close();
        } catch (Exception e) {
            return "";
        }

        BigInteger bigInt = new BigInteger(1, digest.digest());
        return bigInt.toString(16);
    }

    def static File createHashFile(String dir, String hashFileName, String hashValue) {
        File hashFile = new File(dir, hashFileName)
        if (hashFile.exists()) {
            hashFile.delete()
        }

        FileWriter fileWriter = new FileWriter(hashFile)
        fileWriter.write(hashValue)
        fileWriter.close()
        return hashFile
    }
}