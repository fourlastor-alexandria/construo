package io.github.fourlastor.construo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.github.fourlastor.construo.task.DownloadTask;
import io.github.fourlastor.construo.task.PackageTask;
import io.github.fourlastor.construo.task.jvm.CreateRuntimeImageTask;
import java.io.File;
import java.io.FileOutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.testkit.runner.GradleRunner;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.Assume;
import org.junit.rules.TemporaryFolder;

public class ConstruoPluginContractTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void exposesTheDistributionContract() throws Exception {
        assertGetter(Target.class, "getJdkSha256");
        assertGetter(Target.class, "getRoastUrl");
        assertGetter(Target.class, "getRoastSha256");
        assertGetter(Target.class, "getArchiveFile");
        assertGetter(Target.class, "getPackagingToolJdk");
        assertGetter(Target.MacOs.class, "getAppBundle");
        assertGetter(RoastOptions.class, "getVersion");
        assertGetter(RoastOptions.class, "getBaseUrl");

        Method archiveFile = assertGetter(PackageTask.class, "getArchiveFile");
        assertNotNull("PackageTask.archiveFile must be @OutputFile", archiveFile.getAnnotation(OutputFile.class));
        assertEquals(
                "The shared destination directory must not be a task output",
                null,
                PackageTask.class.getMethod("getDestinationDirectory").getAnnotation(OutputDirectory.class));
    }

    @Test
    public void roastCoordinatesAreLazyAndTargetOverridable() throws Exception {
        Project project = newProject();
        ConstruoPluginExtension extension = extension(project);
        property(extension.getRoast(), "getVersion", Property.class).set("v9.8.7");
        property(extension.getRoast(), "getBaseUrl", Property.class).set("https://downloads.example.test/roast");

        Target.Linux target = createLinuxTarget(extension, "linuxX64");
        DownloadTask download = (DownloadTask) project.getTasks().getByName("downloadRoastLinuxX64");
        assertEquals(
                "https://downloads.example.test/roast/v9.8.7/roast-linux-x86_64.zip",
                download.getSrc().get());

        property(target, "getRoastUrl", Property.class).set("https://mirror.example.test/pinned.zip");
        assertEquals("https://mirror.example.test/pinned.zip", download.getSrc().get());
    }

    @Test
    public void targetJdkCanProvideAllPackagingTools() throws Exception {
        Project project = newProject();
        ConstruoPluginExtension extension = extension(project);
        Target.Linux target = createLinuxTarget(extension, "linuxX64");
        setEnumProperty(target, "getPackagingToolJdk", "TARGET_JDK");

        File targetJdk = new File(project.getBuildDir(), "construo/jdk/linuxX64");
        assertTrue(new File(targetJdk, "bin").mkdirs());
        assertTrue(new File(targetJdk, "jmods").mkdirs());
        assertTrue(new File(targetJdk, executable("bin/java")).createNewFile());

        CreateRuntimeImageTask runtime =
                (CreateRuntimeImageTask) project.getTasks().getByName("createRuntimeImageLinuxX64");
        assertEquals(targetJdk.getCanonicalFile(), runtime.getJdkRoot().get().getAsFile().getCanonicalFile());
        assertEquals(
                targetJdk.getCanonicalFile(),
                runtime.getTargetJdkRoot().get().getAsFile().getCanonicalFile());
    }

    @Test
    public void targetJdkExecutesJavapJdepsAndJlinkFromItsOwnBinDirectory() throws Exception {
        Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        Project project = newProject();
        File targetJdk = new File(project.getProjectDir(), "target-jdk");
        File bin = new File(targetJdk, "bin");
        assertTrue(bin.mkdirs());
        assertTrue(new File(targetJdk, "jmods").mkdirs());
        File log = new File(project.getProjectDir(), "tools.log");
        writeScript(new File(bin, "java"), "exit 0");
        writeScript(
                new File(bin, "javap"),
                "printf 'javap:%s\\n' \"$0\" >> '" + log.getAbsolutePath() + "'\nprintf '17.0.8\\n'");
        writeScript(
                new File(bin, "jdeps"),
                "printf 'jdeps:%s %s\\n' \"$0\" \"$*\" >> '"
                        + log.getAbsolutePath()
                        + "'\nprintf 'java.base\\n'");
        writeScript(
                new File(bin, "jlink"),
                "printf 'jlink:%s %s\\n' \"$0\" \"$*\" >> '" + log.getAbsolutePath() + "'");
        File jar = new File(project.getProjectDir(), "app.jar");
        Files.write(jar.toPath(), new byte[0]);

        CreateRuntimeImageTask task =
                project.getTasks().create("probeRuntime", CreateRuntimeImageTask.class);
        task.getJdkRoot().set(targetJdk);
        task.getTargetJdkRoot().set(targetJdk);
        task.getModules().set(Collections.emptyList());
        task.getGuessModulesFromJar().set(true);
        task.getIncludeDefaultCryptoModules().set(false);
        task.getStripNativeCommands().set(false);
        task.getJarFile().set(jar);
        task.getOutput().set(new File(project.getBuildDir(), "runtime"));

        task.run();

        String invocations = new String(Files.readAllBytes(log.toPath()), StandardCharsets.UTF_8);
        assertTrue(invocations.contains("javap:" + new File(bin, "javap").getCanonicalPath()));
        assertTrue(invocations.contains("jdeps:" + new File(bin, "jdeps").getCanonicalPath()));
        assertTrue(invocations.contains("--multi-release 17"));
        assertTrue(invocations.contains("jlink:" + new File(bin, "jlink").getCanonicalPath()));
        assertTrue(invocations.contains("--module-path " + new File(targetJdk, "jmods").getAbsolutePath()));
    }

    @Test
    public void packageTasksOwnDistinctArchiveFiles() throws Exception {
        Project project = newProject();
        ConstruoPluginExtension extension = extension(project);
        extension.getOutputDir().set(project.getLayout().getProjectDirectory().dir("dist"));
        createLinuxTarget(extension, "linuxX64");
        createLinuxTarget(extension, "linuxArm64").getArchitecture().set(Target.Architecture.AARCH64);

        PackageTask x64 = (PackageTask) project.getTasks().getByName("packageLinuxX64");
        PackageTask arm64 = (PackageTask) project.getTasks().getByName("packageLinuxArm64");
        File x64Archive = property(x64, "getArchiveFile", RegularFileProperty.class).get().getAsFile();
        File arm64Archive = property(arm64, "getArchiveFile", RegularFileProperty.class).get().getAsFile();

        assertEquals(new File(project.getProjectDir(), "dist/game-linuxX64.zip"), x64Archive);
        assertEquals(new File(project.getProjectDir(), "dist/game-linuxArm64.zip"), arm64Archive);
        assertNotEquals(x64Archive, arm64Archive);
    }

    @Test
    public void legacyPackageTaskOutputPropertiesStillControlArchive() throws Exception {
        Project project = newProject();
        ConstruoPluginExtension extension = extension(project);
        createLinuxTarget(extension, "linuxX64");

        PackageTask packageTask =
                (PackageTask) project.getTasks().getByName("packageLinuxX64");
        packageTask
                .getDestinationDirectory()
                .set(project.getLayout().getProjectDirectory().dir("custom-dist"));
        packageTask.getArchiveFileName().set("custom.zip");

        assertEquals(
                new File(project.getProjectDir(), "custom-dist/custom.zip"),
                packageTask.getArchiveFile().get().getAsFile());
    }

    @Test
    public void rawMacPackageUsesTheFlatRoastLayout() throws Exception {
        Project project = newProject();
        ConstruoPluginExtension extension = extension(project);
        Target.MacOs target = extension.getTargets().create("macosArm64", Target.MacOs.class);
        target.getArchitecture().set(Target.Architecture.AARCH64);
        target.getJdkUrl().set("https://example.test/jdk.tar.gz");
        property(target, "getAppBundle", Property.class).set(false);

        PackageTask packageTask = (PackageTask) project.getTasks().getByName("packageMacosArm64");
        Set<String> dependencies =
                packageTask.getTaskDependencies().getDependencies(packageTask).stream()
                        .map(Task::getName)
                        .collect(Collectors.toSet());

        assertTrue(dependencies.contains("roastMacosArm64"));
        assertFalse(dependencies.contains("buildMacAppBundleMacosArm64"));
        assertFalse(dependencies.contains("generatePListMacosArm64"));
        assertTrue(packageTask.getFrom().get().getAsFile().getPath().endsWith("macosArm64/roast"));
        assertFalse(packageTask.getInto().isPresent());
        assertTrue(packageTask.getExecutable().get().getAsFile().getPath().endsWith("macosArm64/roast/game"));
    }

    @Test
    public void rawMacPackageArchiveHasNoAppBundle() throws Exception {
        Project project = newProject();
        ConstruoPluginExtension extension = extension(project);
        Target.MacOs target = extension.getTargets().create("macosArm64", Target.MacOs.class);
        target.getArchitecture().set(Target.Architecture.AARCH64);
        target.getJdkUrl().set("https://example.test/jdk.tar.gz");
        property(target, "getAppBundle", Property.class).set(false);

        PackageTask packageTask = (PackageTask) project.getTasks().getByName("packageMacosArm64");
        File packageRoot = packageTask.getFrom().get().getAsFile();
        assertTrue(new File(packageRoot, "app").mkdirs());
        assertTrue(new File(packageRoot, "runtime").mkdirs());
        Files.write(new File(packageRoot, "game").toPath(), "launcher".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(packageRoot, "app/game.json").toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(packageRoot, "runtime/release").toPath(), "JAVA_VERSION=17".getBytes(StandardCharsets.UTF_8));
        File archive = new File(project.getProjectDir(), "raw-mac.zip");
        packageTask.getArchiveFile().set(archive);
        packageTask.run();

        try (ZipFile zip = new ZipFile(archive)) {
            assertNotNull(zip.getEntry("game"));
            assertNotNull(zip.getEntry("app/game.json"));
            assertNotNull(zip.getEntry("runtime/release"));
            assertFalse(zip.stream().anyMatch(entry -> entry.getName().contains(".app/Contents")));
        }
    }

    @Test
    public void verifiedDownloadsGateBothExtractions() throws Exception {
        Project project = newProject();
        ConstruoPluginExtension extension = extension(project);
        Target.Linux target = createLinuxTarget(extension, "linuxX64");
        property(target, "getJdkSha256", Property.class).set(repeat("a", 64));
        property(target, "getRoastSha256", Property.class).set(repeat("b", 64));

        Task verifyJdk = project.getTasks().getByName("verifyJdkLinuxX64");
        Task verifyRoast = project.getTasks().getByName("verifyRoastLinuxX64");
        Task unzipJdk = project.getTasks().getByName("unzipJdkLinuxX64");
        Task unzipRoast = project.getTasks().getByName("unzipRoastLinuxX64");

        assertTrue(taskDependencyNames(unzipJdk).contains(verifyJdk.getName()));
        assertTrue(taskDependencyNames(unzipRoast).contains(verifyRoast.getName()));
    }

    @Test
    public void targetJdkExtractionRetainsLegalFiles() throws Exception {
        File project = temporaryFolder.newFolder();
        File archive = new File(project, "build/construo/jdk/linuxX64.zip");
        assertTrue(archive.getParentFile().mkdirs());
        writeZip(archive, "fake-jdk/bin/java", "java", "fake-jdk/legal/java.base/LICENSE", "license");
        Files.write(new File(project, "settings.gradle").toPath(), "rootProject.name = 'probe'\n".getBytes(StandardCharsets.UTF_8));
        String buildScript =
                "import io.github.fourlastor.construo.Target\n"
                        + "plugins { id 'java'; id 'io.github.fourlastor.construo' }\n"
                        + "construo {\n"
                        + "  name = 'probe'\n"
                        + "  mainClass = 'example.Main'\n"
                        + "  targets.create('linuxX64', Target.Linux) {\n"
                        + "    architecture = Target.Architecture.X86_64\n"
                        + "    jdkUrl = 'https://not-used.example.test/jdk.zip'\n"
                        + "    jdkSha256 = '" + sha256(archive) + "'\n"
                        + "  }\n"
                        + "}\n"
                        + "tasks.named('downloadJdkLinuxX64') { onlyIf { false } }\n";
        Files.write(new File(project, "build.gradle").toPath(), buildScript.getBytes(StandardCharsets.UTF_8));

        GradleRunner.create()
                .withProjectDir(project)
                .withPluginClasspath()
                .withArguments("unzipJdkLinuxX64", "--stacktrace")
                .build();

        assertTrue(new File(project, "build/construo/jdk/linuxX64/legal/java.base/LICENSE").isFile());
    }

    private Project newProject() throws Exception {
        Project project =
                ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
        project.getPluginManager().apply(JavaPlugin.class);
        project.getPluginManager().apply(ConstruoPlugin.class);
        ConstruoPluginExtension extension = extension(project);
        extension.getName().set("game");
        extension.getMainClass().set("example.Main");
        return project;
    }

    private static ConstruoPluginExtension extension(Project project) {
        return project.getExtensions().getByType(ConstruoPluginExtension.class);
    }

    private static Target.Linux createLinuxTarget(ConstruoPluginExtension extension, String name) {
        Target.Linux target = extension.getTargets().create(name, Target.Linux.class);
        target.getArchitecture().set(Target.Architecture.X86_64);
        target.getJdkUrl().set("https://example.test/jdk.tar.gz");
        return target;
    }

    private static Method assertGetter(Class<?> type, String name) {
        try {
            return type.getMethod(name);
        } catch (NoSuchMethodException error) {
            fail(type.getSimpleName() + " is missing " + name + "(); found "
                    + Arrays.stream(type.getMethods()).map(Method::getName).sorted().collect(Collectors.toList()));
            throw new AssertionError(error);
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T property(Object owner, String getter, Class<T> type) {
        try {
            Object value = assertGetter(owner.getClass(), getter).invoke(owner);
            return (T) type.cast(value);
        } catch (IllegalAccessException | InvocationTargetException error) {
            throw new AssertionError("Could not read " + getter + " from " + owner, error);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void setEnumProperty(Object owner, String getter, String value) throws Exception {
        Property property = property(owner, getter, Property.class);
        Class<? extends Enum> enumType =
                (Class<? extends Enum>) Class.forName(Target.class.getName() + "$PackagingToolJdk");
        property.set(Enum.valueOf(enumType, value));
    }

    private static String executable(String path) {
        return System.getProperty("os.name").startsWith("Windows") ? path + ".exe" : path;
    }

    private static Set<String> taskDependencyNames(Task task) {
        return task.getTaskDependencies().getDependencies(task).stream()
                .map(Task::getName)
                .collect(Collectors.toSet());
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }

    private static void writeZip(File archive, String... pathsAndContents) throws Exception {
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(archive))) {
            for (int index = 0; index < pathsAndContents.length; index += 2) {
                output.putNextEntry(new ZipEntry(pathsAndContents[index]));
                output.write(pathsAndContents[index + 1].getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            }
        }
    }

    private static void writeScript(File script, String body) throws Exception {
        Files.write(script.toPath(), ("#!/bin/sh\nset -eu\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
        assertTrue(script.setExecutable(true));
    }

    private static String sha256(File file) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file.toPath()));
        StringBuilder result = new StringBuilder(digest.length * 2);
        for (byte value : digest) {
            result.append(String.format("%02x", value));
        }
        return result.toString();
    }
}
