package io.github.fourlastor.construo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.github.fourlastor.construo.task.macos.SignMacAppBundle;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class SignMacAppBundleTest {

    // a 64 bit Mach-O as it's written on disk, and a universal one
    private static final byte[] MACH_O = {(byte) 0xcf, (byte) 0xfa, (byte) 0xed, (byte) 0xfe, 12, 0, 0, 1, 0, 0};
    private static final byte[] FAT_MACH_O = {(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe, 0, 0, 0, 2, 0, 0};
    // a class file has the same magic, then its version
    private static final byte[] CLASS_FILE = {(byte) 0xca, (byte) 0xfe, (byte) 0xba, (byte) 0xbe, 0, 0, 0, 61, 0, 0};

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private Project project;
    private File log;
    private SignMacAppBundle task;
    private File signedApp;

    @Before
    public void setUp() throws Exception {
        Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder("project")).build();
        File app = new File(project.getProjectDir(), "build/Game.app");
        write(new File(app, "Contents/Info.plist"), "<plist/>".getBytes(StandardCharsets.UTF_8));
        write(new File(app, "Contents/MacOS/game"), MACH_O);
        write(new File(app, "Contents/MacOS/app/game.json"), "{}".getBytes(StandardCharsets.UTF_8));
        write(new File(app, "Contents/MacOS/runtime/lib/server/libjvm.dylib"), MACH_O);
        write(new File(app, "Contents/MacOS/runtime/lib/modules"), "modules".getBytes(StandardCharsets.UTF_8));
        writeJar(
                new File(app, "Contents/MacOS/game.jar"),
                "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n".getBytes(StandardCharsets.UTF_8),
                "io/github/Main.class", CLASS_FILE,
                "natives/libgdx64.dylib", MACH_O,
                "natives/libfat.dylib", FAT_MACH_O,
                "natives/disguised.bin", CLASS_FILE,
                "assets/text.txt", "text".getBytes(StandardCharsets.UTF_8));
        File extra = new File(project.getProjectDir(), "extra.txt");
        write(extra, "extra".getBytes(StandardCharsets.UTF_8));

        log = new File(project.getProjectDir(), "rcodesign.log");
        File rcodesign = new File(project.getProjectDir(), "rcodesign");
        writeScript(
                rcodesign,
                "printf '%s\\n' \"$*\" >> '" + log.getAbsolutePath() + "'\n"
                        + "previous=''\n"
                        + "for argument in \"$@\"; do\n"
                        + "  case \"$previous\" in\n"
                        + "    --p12-password-file) printf 'password:%s\\n' \"$(cat \"$argument\")\" >> '" + log.getAbsolutePath() + "' ;;\n"
                        + "    --entitlements-xml-file) grep -o 'com.apple.security.cs.[a-z-]*' \"$argument\" | sed 's/^/entitlement:/' >> '" + log.getAbsolutePath() + "' ;;\n"
                        + "  esac\n"
                        + "  previous=\"$argument\"\n"
                        + "  target=\"$argument\"\n"
                        + "done\n"
                        + "if [ -d \"$target\" ]; then mkdir -p \"$target/Contents/_CodeSignature\"; echo sealed > \"$target/Contents/_CodeSignature/CodeResources\"; else printf 'signed' >> \"$target\"; fi\n");

        task = project.getTasks().create("signMacAppBundle", SignMacAppBundle.class);
        signedApp = new File(project.getProjectDir(), "build/signed/Game.app");
        task.getAppDirectory().set(app);
        task.getExecutableName().set("game");
        task.getRcodesign().set(rcodesign);
        task.getHardenedRuntime().set(false);
        task.getForNotarization().set(false);
        task.getPackageFiles()
                .set(Collections.singletonMap(
                        "runtime/extra.txt", project.getLayout().getProjectDirectory().file("extra.txt")));
        task.getOutputDirectory().set(signedApp);
    }

    @Test
    public void adHocSignatureSealsTheWholeBundle() throws Exception {
        task.run();

        assertEquals(Collections.singletonList("sign " + signedApp.getAbsolutePath()), invocations());
        File macOs = new File(signedApp, "Contents/MacOS");
        assertEquals(
                Arrays.asList("app", "game", "game.jar", "runtime"),
                sorted(macOs.list()));
        assertTrue(Files.isRegularFile(new File(macOs, "game").toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS));
        for (String linked : Arrays.asList("app", "game.jar", "runtime")) {
            assertEquals(
                    Paths.get("..", "Resources", linked),
                    Files.readSymbolicLink(new File(macOs, linked).toPath()));
            assertTrue(new File(signedApp, "Contents/Resources/" + linked).exists());
        }
        assertEquals("extra", read(new File(signedApp, "Contents/Resources/runtime/extra.txt")));
        assertTrue(new File(signedApp, "Contents/_CodeSignature/CodeResources").isFile());
        assertArrayEquals(MACH_O, entry(new File(signedApp, "Contents/Resources/game.jar"), "natives/libgdx64.dylib"));
    }

    @Test
    public void certificateSignsTheJarNativesAndHardensEveryBinary() throws Exception {
        File certificate = temporaryFolder.newFile("developer-id.p12");
        task.getCertificate().set(certificate);
        task.getCertificatePassword().set("secret");
        task.getHardenedRuntime().set(true);

        task.run();

        String identity = "--p12-file " + certificate.getAbsolutePath() + " --p12-password-file ";
        List<String> invocations = invocations();
        List<String> natives = new ArrayList<>();
        for (String invocation : invocations) {
            if (invocation.startsWith("sign ") && invocation.endsWith(".dylib")) {
                assertTrue(invocation, invocation.startsWith("sign " + identity));
                assertTrue(invocation, invocation.contains(" --code-signature-flags runtime "));
                natives.add(invocation.substring(invocation.lastIndexOf('/') + 1));
            }
        }
        assertEquals(Arrays.asList("libgdx64.dylib", "libfat.dylib"), natives);
        String bundle = invocations.stream()
                .filter(it -> it.endsWith(" " + signedApp.getAbsolutePath()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertTrue(bundle, bundle.startsWith("sign " + identity));
        assertTrue(bundle, bundle.contains(" --code-signature-flags runtime "));
        assertTrue(bundle, bundle.contains(" --code-signature-flags Contents/Resources/runtime/lib/server/libjvm.dylib:runtime "));
        assertFalse(bundle, bundle.contains("Contents/MacOS/game:runtime"));
        assertTrue(invocations.contains("entitlement:com.apple.security.cs.allow-jit"));
        assertTrue(invocations.contains("entitlement:com.apple.security.cs.allow-unsigned-executable-memory"));
        assertTrue(invocations.contains("entitlement:com.apple.security.cs.disable-library-validation"));
        assertTrue(invocations.stream().filter(it -> it.startsWith("password:")).allMatch("password:secret"::equals));
        assertFalse("The password must not outlive the task", new File(task.getTemporaryDir(), "certificate-password").exists());

        File jar = new File(signedApp, "Contents/Resources/game.jar");
        assertEquals(
                Arrays.asList(
                        "META-INF/MANIFEST.MF",
                        "io/github/Main.class",
                        "natives/libgdx64.dylib",
                        "natives/libfat.dylib",
                        "natives/disguised.bin",
                        "assets/text.txt"),
                entryNames(jar));
        assertEquals("signed", tail(entry(jar, "natives/libgdx64.dylib"), MACH_O.length));
        assertEquals("signed", tail(entry(jar, "natives/libfat.dylib"), FAT_MACH_O.length));
        assertArrayEquals(CLASS_FILE, entry(jar, "io/github/Main.class"));
        assertArrayEquals(CLASS_FILE, entry(jar, "natives/disguised.bin"));
        assertEquals("text", new String(entry(jar, "assets/text.txt"), StandardCharsets.UTF_8));
    }

    @Test
    public void customEntitlementsReplaceTheJvmOnes() throws Exception {
        File entitlements = new File(project.getProjectDir(), "custom.entitlements");
        write(entitlements, "<key>com.apple.security.cs.allow-jit</key>".getBytes(StandardCharsets.UTF_8));
        task.getEntitlementsFile().set(entitlements);

        task.run();

        String bundle = invocations().get(0);
        assertTrue(bundle, bundle.contains("--entitlements-xml-file " + entitlements.getAbsolutePath()));
        assertEquals(Collections.singletonList("entitlement:com.apple.security.cs.allow-jit"), entitlementLines());
    }

    @Test
    public void notarizationNeedsACertificate() {
        task.getForNotarization().set(true);

        try {
            task.run();
            fail("Expected notarization without a certificate to be refused");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("signing.certificate"));
        }
    }

    @Test
    public void notarizationChecksTheSignatureAndHardensIt() throws Exception {
        task.getCertificate().set(temporaryFolder.newFile("developer-id.p12"));
        task.getForNotarization().set(true);

        task.run();

        String bundle = invocations().stream()
                .filter(it -> it.endsWith(" " + signedApp.getAbsolutePath()))
                .findFirst()
                .orElseThrow(AssertionError::new);
        assertTrue(bundle, bundle.contains(" --for-notarization "));
        assertTrue(bundle, bundle.contains(" --code-signature-flags runtime "));
        assertTrue(entitlementLines().contains("entitlement:com.apple.security.cs.allow-jit"));
    }

    @Test
    public void unreadableCertificateFailureExplainsTheLegacyEncryption() throws Exception {
        // the error comes after the other stream ended: both go to the same log
        writeScript(
                task.getRcodesign().get().getAsFile(),
                "exec 2>&-\nsleep 0.5\necho 'Error: incorrect password given when decrypting PFX data'\nexit 1");
        task.getCertificate().set(temporaryFolder.newFile("modern.p12"));

        try {
            task.run();
            fail("Expected the failure to be reported");
        } catch (org.gradle.api.GradleException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("incorrect password"));
            assertTrue(expected.getMessage(), expected.getMessage().contains("-legacy"));
        }
    }

    private List<String> invocations() throws Exception {
        return Files.readAllLines(log.toPath(), StandardCharsets.UTF_8);
    }

    private List<String> entitlementLines() throws Exception {
        List<String> lines = new ArrayList<>();
        for (String line : invocations()) {
            if (line.startsWith("entitlement:")) {
                lines.add(line);
            }
        }
        return lines;
    }

    private static List<String> sorted(String[] names) {
        List<String> list = new ArrayList<>(Arrays.asList(names));
        Collections.sort(list);
        return list;
    }

    private static String read(File file) throws Exception {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }

    private static String tail(byte[] content, int from) {
        return new String(content, from, content.length - from, StandardCharsets.UTF_8);
    }

    private static List<String> entryNames(File jar) throws Exception {
        List<String> names = new ArrayList<>();
        try (ZipFile zip = new ZipFile(jar)) {
            zip.stream().forEach(entry -> names.add(entry.getName()));
        }
        return names;
    }

    private static byte[] entry(File jar, String name) throws Exception {
        try (ZipFile zip = new ZipFile(jar);
                InputStream input = zip.getInputStream(zip.getEntry(name))) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
        }
    }

    private static void write(File file, byte[] content) throws Exception {
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        Files.write(file.toPath(), content);
    }

    private static void writeJar(File jar, Object... namesAndContents) throws Exception {
        assertTrue(jar.getParentFile().isDirectory() || jar.getParentFile().mkdirs());
        try (ZipOutputStream output = new ZipOutputStream(new FileOutputStream(jar))) {
            for (int index = 0; index < namesAndContents.length; index += 2) {
                output.putNextEntry(new ZipEntry((String) namesAndContents[index]));
                output.write((byte[]) namesAndContents[index + 1]);
                output.closeEntry();
            }
        }
    }

    private static void writeScript(File script, String body) throws Exception {
        Files.write(script.toPath(), ("#!/bin/sh\nset -eu\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
        assertTrue(script.setExecutable(true));
    }
}
