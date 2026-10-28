package io.github.fourlastor.construo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.github.fourlastor.construo.task.DownloadTask;
import io.github.fourlastor.construo.task.PackageTask;
import io.github.fourlastor.construo.task.VerifySha256Task;
import io.github.fourlastor.construo.task.jvm.CreateRuntimeImageTask;
import io.github.fourlastor.construo.task.macos.BuildMacAppBundle;
import io.github.fourlastor.construo.task.macos.Rcodesign;
import io.github.fourlastor.construo.task.macos.SignMacAppBundle;
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
        String rawPackagePath = new File("macosArm64", "roast").getPath();
        assertTrue(packageTask.getFrom().get().getAsFile().getPath().endsWith(rawPackagePath));
        assertFalse(packageTask.getInto().isPresent());
        assertTrue(
                packageTask
                        .getExecutable()
                        .get()
                        .getAsFile()
                        .getPath()
                        .endsWith(new File(rawPackagePath, "game").getPath()));
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
    public void exposesTheMacSigningContract() throws Exception {
        assertGetter(Target.MacOs.class, "getSigning");
        assertGetter(Target.MacOs.class, "getNotarization");
        assertGetter(ConstruoPluginExtension.class, "getRcodesign");
        Target.MacOs.class.getMethod("signing", org.gradle.api.Action.class);
        Target.MacOs.class.getMethod("notarization", org.gradle.api.Action.class);
        ConstruoPluginExtension.class.getMethod("rcodesign", org.gradle.api.Action.class);
    }

    @Test
    public void unsignedMacBundleIsPackagedAsBuilt() throws Exception {
        Project project = newProject();
        Target.MacOs target = createMacTarget(extension(project), "macM1");

        PackageTask packageTask = (PackageTask) project.getTasks().getByName("packageMacM1");

        assertFalse(target.getSigning().getEnabled().get());
        assertFalse(target.getNotarization().getEnabled().get());
        Set<String> dependencies = taskDependencyNames(packageTask);
        assertTrue(dependencies.contains("buildMacAppBundleMacM1"));
        assertFalse(dependencies.contains("signMacAppBundleMacM1"));
        assertEquals(new File(project.getBuildDir(), "construo/macM1/Game.app"), packageTask.getFrom().get().getAsFile());
        assertEquals(
                new File(project.getBuildDir(), "construo/macM1/Game.app/Contents/MacOS/game"),
                packageTask.getExecutable().get().getAsFile());
    }

    @Test
    public void signedMacBundleIsPackagedFromTheSignedApp() throws Exception {
        Project project = newProject();
        ConstruoPluginExtension extension = extension(project);
        Target.MacOs target = createMacTarget(extension, "macM1");
        File extra = new File(project.getProjectDir(), "extra.txt");
        target.getPackageFiles()
                .set(Collections.singletonMap("extra.txt", project.getLayout().getProjectDirectory().file("extra.txt")));
        target.getEntitlementsFile().set(new File(project.getProjectDir(), "app.entitlements"));
        target.signing(signing -> signing.getEnabled().set(true));

        PackageTask packageTask = (PackageTask) project.getTasks().getByName("packageMacM1");
        SignMacAppBundle sign = (SignMacAppBundle) project.getTasks().getByName("signMacAppBundleMacM1");
        BuildMacAppBundle build = (BuildMacAppBundle) project.getTasks().getByName("buildMacAppBundleMacM1");

        assertTrue(taskDependencyNames(packageTask).contains("signMacAppBundleMacM1"));
        assertTrue(taskDependencyNames(sign).contains("extractRcodesign"));
        File signedApp = new File(project.getBuildDir(), "construo/macM1/signed/Game.app");
        assertEquals(signedApp, packageTask.getFrom().get().getAsFile());
        assertEquals(signedApp, sign.getOutputDirectory().get().getAsFile());
        assertEquals(new File(signedApp, "Contents/MacOS/game"), packageTask.getExecutable().get().getAsFile());
        // the signed app has them inside, in its seal
        assertTrue(packageTask.getPackageFiles().get().isEmpty());
        assertEquals(extra, sign.getPackageFiles().get().get("extra.txt").getAsFile());
        // the signature carries the entitlements
        assertFalse(build.getEntitlementsFileSource().isPresent());
        assertEquals(new File(project.getProjectDir(), "app.entitlements"), sign.getEntitlementsFile().get().getAsFile());
        assertFalse(sign.getHardenedRuntime().get());
        assertFalse(sign.getCertificate().isPresent());
    }

    @Test
    public void certificateSignatureHasTheHardenedRuntime() throws Exception {
        Project project = newProject();
        Target.MacOs target = createMacTarget(extension(project), "macM1");
        target.getSigning().getEnabled().set(true);
        target.getSigning().getCertificate().set(new File(project.getProjectDir(), "developer-id.p12"));

        SignMacAppBundle sign = (SignMacAppBundle) project.getTasks().getByName("signMacAppBundleMacM1");

        assertTrue(sign.getHardenedRuntime().get());
        assertFalse(sign.getForNotarization().get());
        target.getSigning().getHardenedRuntime().set(false);
        assertFalse(sign.getHardenedRuntime().get());
    }

    @Test
    public void notarizedMacBundleIsSignedAndPackagedFromTheNotarizedApp() throws Exception {
        Project project = newProject();
        Target.MacOs target = createMacTarget(extension(project), "macM1");
        target.notarization(notarization -> notarization.getEnabled().set(true));

        PackageTask packageTask = (PackageTask) project.getTasks().getByName("packageMacM1");
        SignMacAppBundle sign = (SignMacAppBundle) project.getTasks().getByName("signMacAppBundleMacM1");
        Task notarize = project.getTasks().getByName("notarizeMacAppBundleMacM1");

        assertTrue(target.getSigning().getEnabled().get());
        assertTrue(sign.getForNotarization().get());
        assertTrue(taskDependencyNames(packageTask).contains("notarizeMacAppBundleMacM1"));
        assertTrue(taskDependencyNames(notarize).contains("signMacAppBundleMacM1"));
        assertEquals(
                new File(project.getBuildDir(), "construo/macM1/notarized/Game.app"),
                packageTask.getFrom().get().getAsFile());
    }

    @Test
    public void notarizationNeedsSigning() throws Exception {
        Project project = newProject();
        Target.MacOs target = createMacTarget(extension(project), "macM1");
        target.getNotarization().getEnabled().set(true);
        target.getSigning().getEnabled().set(false);

        assertResolutionFails(project.getTasks().getByName("packageMacM1"), "signing.enabled = true");
    }

    @Test
    public void signingNeedsAnAppBundle() throws Exception {
        Project project = newProject();
        Target.MacOs target = createMacTarget(extension(project), "macM1");
        target.getAppBundle().set(false);
        target.getSigning().getEnabled().set(true);

        assertResolutionFails(project.getTasks().getByName("packageMacM1"), "appBundle = true");
    }

    @Test
    public void rcodesignIsDownloadedForTheHostAndVerified() throws Exception {
        Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        Project project = newProject();
        ConstruoPluginExtension extension = extension(project);
        String host = Rcodesign.INSTANCE.hostTarget(System.getProperty("os.name"), System.getProperty("os.arch"));

        DownloadTask download = (DownloadTask) project.getTasks().getByName("downloadRcodesign");
        VerifySha256Task verify = (VerifySha256Task) project.getTasks().getByName("verifyRcodesign");
        Task extract = project.getTasks().getByName("extractRcodesign");

        assertEquals(
                "https://github.com/indygreg/apple-platform-rs/releases/download/apple-codesign%2F0.29.0/apple-codesign-0.29.0-"
                        + host + ".tar.gz",
                download.getSrc().get());
        assertTrue(verify.getExpectedSha256().get().matches("[0-9a-f]{64}"));
        assertTrue(taskDependencyNames(extract).contains("verifyRcodesign"));

        // a version of its own has no known digest
        extension.getRcodesign().getVersion().set("0.30.0");
        assertTrue(download.getSrc().get().endsWith("/apple-codesign%2F0.30.0/apple-codesign-0.30.0-" + host + ".tar.gz"));
        assertFalse(verify.getExpectedSha256().isPresent());
        assertFalse(taskDependencyNames(extract).contains("verifyRcodesign"));
        extension.getRcodesign().getSha256().set(repeat("c", 64));
        assertTrue(taskDependencyNames(extract).contains("verifyRcodesign"));
    }

    @Test
    public void localRcodesignSkipsTheDownload() throws Exception {
        Project project = newProject();
        ConstruoPluginExtension extension = extension(project);
        Target.MacOs target = createMacTarget(extension, "macM1");
        target.getSigning().getEnabled().set(true);
        File rcodesign = new File(project.getProjectDir(), "tools/rcodesign");
        extension.rcodesign(options -> options.getExecutable().set(rcodesign));

        SignMacAppBundle sign = (SignMacAppBundle) project.getTasks().getByName("signMacAppBundleMacM1");

        assertEquals(rcodesign, sign.getRcodesign().get().getAsFile());
        assertFalse(taskDependencyNames(sign).contains("extractRcodesign"));
    }

    @Test
    public void groovyDslConfiguresSigningAndNotarization() throws Exception {
        File project = temporaryFolder.newFolder();
        Files.write(new File(project, "settings.gradle").toPath(), "rootProject.name = 'probe'\n".getBytes(StandardCharsets.UTF_8));
        String buildScript =
                "import io.github.fourlastor.construo.Target\n"
                        + "plugins { id 'java'; id 'io.github.fourlastor.construo' }\n"
                        + "construo {\n"
                        + "  name = 'probe'\n"
                        + "  humanName = 'Probe'\n"
                        + "  mainClass = 'example.Main'\n"
                        + "  rcodesign { executable = file('tools/rcodesign') }\n"
                        + "  targets.create('macM1', Target.MacOs) {\n"
                        + "    architecture = Target.Architecture.AARCH64\n"
                        + "    jdkUrl = 'https://not-used.example.test/jdk.tar.gz'\n"
                        + "    identifier = 'example.Probe'\n"
                        + "    signing {\n"
                        + "      certificate = file('developer-id.p12')\n"
                        + "    }\n"
                        + "    notarization {\n"
                        + "      enabled = true\n"
                        + "      keyId = 'ABC123'\n"
                        + "    }\n"
                        + "  }\n"
                        + "}\n"
                        + "tasks.register('printSigning') {\n"
                        + "  def sign = tasks.named('signMacAppBundleMacM1')\n"
                        + "  def notarize = tasks.named('notarizeMacAppBundleMacM1')\n"
                        + "  doLast {\n"
                        + "    println \"signing ${sign.get().certificate.get().asFile.name} ${sign.get().hardenedRuntime.get()} "
                        + "${sign.get().forNotarization.get()} ${sign.get().rcodesign.get().asFile.name} ${notarize.get().keyId.get()}\"\n"
                        + "  }\n"
                        + "}\n";
        Files.write(new File(project, "build.gradle").toPath(), buildScript.getBytes(StandardCharsets.UTF_8));

        String output = GradleRunner.create()
                .withProjectDir(project)
                .withPluginClasspath()
                .withArguments("printSigning", "--stacktrace")
                .build()
                .getOutput();

        assertTrue(output, output.contains("signing developer-id.p12 true true rcodesign ABC123"));
    }

    @Test
    public void rcodesignBuildsMatchTheirHosts() {
        assertEquals("x86_64-unknown-linux-musl", Rcodesign.INSTANCE.hostTarget("Linux", "amd64"));
        assertEquals("aarch64-unknown-linux-musl", Rcodesign.INSTANCE.hostTarget("Linux", "aarch64"));
        assertEquals("aarch64-apple-darwin", Rcodesign.INSTANCE.hostTarget("Mac OS X", "aarch64"));
        assertEquals("x86_64-apple-darwin", Rcodesign.INSTANCE.hostTarget("Mac OS X", "x86_64"));
        try {
            Rcodesign.INSTANCE.hostTarget("Windows 11", "amd64");
            fail("Expected Windows hosts to be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Linux or macOS"));
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
    public void downloadsWithoutDigestsSkipVerification() throws Exception {
        Project project = newProject();
        ConstruoPluginExtension extension = extension(project);
        createLinuxTarget(extension, "linuxX64");

        Task unzipJdk = project.getTasks().getByName("unzipJdkLinuxX64");
        Task unzipRoast = project.getTasks().getByName("unzipRoastLinuxX64");

        assertFalse(taskDependencyNames(unzipJdk).contains("verifyJdkLinuxX64"));
        assertFalse(taskDependencyNames(unzipRoast).contains("verifyRoastLinuxX64"));
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

    private static Target.MacOs createMacTarget(ConstruoPluginExtension extension, String name) {
        extension.getHumanName().set("Game");
        Target.MacOs target = extension.getTargets().create(name, Target.MacOs.class);
        target.getArchitecture().set(Target.Architecture.AARCH64);
        target.getJdkUrl().set("https://example.test/jdk.tar.gz");
        target.getIdentifier().set("io.github.fourlastor.Game");
        return target;
    }

    private static void assertResolutionFails(Task task, String message) {
        try {
            taskDependencyNames(task);
            fail("Expected the dependencies of " + task.getName() + " not to resolve");
        } catch (RuntimeException expected) {
            Throwable cause = expected;
            while (cause != null && (cause.getMessage() == null || !cause.getMessage().contains(message))) {
                cause = cause.getCause();
            }
            assertNotNull("Expected a failure mentioning '" + message + "', got " + expected, cause);
        }
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
