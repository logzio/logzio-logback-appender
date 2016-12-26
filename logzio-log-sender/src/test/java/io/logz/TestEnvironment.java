package io.logz;

import com.google.common.base.Splitter;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.GROUP_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.GROUP_READ;
import static java.nio.file.attribute.PosixFilePermission.GROUP_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_READ;
import static java.nio.file.attribute.PosixFilePermission.OTHERS_WRITE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_EXECUTE;
import static java.nio.file.attribute.PosixFilePermission.OWNER_READ;
import static java.nio.file.attribute.PosixFilePermission.OWNER_WRITE;

public class TestEnvironment {

    /**
     * Creates a temp directory in the Build temp directory (Gradle 'build')
     */
    public static File createTempDirectory() {
        final String mavenBuildDirectory = "target";
        final String buildDirSubString = File.separator + mavenBuildDirectory + File.separator;
        String classPath = System.getProperty("java.class.path");
        String separator = System.getProperty("path.separator");
        List<String> paths = Splitter.on(separator).trimResults().splitToList(classPath);
        File buildDir = paths.stream().filter(path -> path.contains(buildDirSubString)).findFirst().map(path -> {
            String upToBuildDir = path.substring(0, path.indexOf(buildDirSubString) + buildDirSubString.length());
            return new File(upToBuildDir);
        }).orElseThrow(() -> new RuntimeException("Failed finding classpath entry containing the build directory ("
                +buildDirSubString+" in the following classpath: "+classPath));

        try {
            FileAttribute<Set<PosixFilePermission>> fileAttributes = PosixFilePermissions.asFileAttribute(EnumSet
                    .of(OWNER_READ, OWNER_WRITE, OWNER_EXECUTE,
                            GROUP_READ, GROUP_EXECUTE, GROUP_WRITE,
                            OTHERS_EXECUTE, OTHERS_READ, OTHERS_WRITE));
            return Files.createTempDirectory(buildDir.toPath(), "", fileAttributes).toFile();
        } catch (IOException e) {
            throw new RuntimeException("Failed creating temp directory in "+buildDir.getAbsolutePath(), e);
        }
    }

}
