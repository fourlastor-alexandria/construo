package io.github.fourlastor.construo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.github.fourlastor.construo.task.macos.NotarizeMacAppBundle;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class NotarizeMacAppBundleTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    private File log;
    private File signedApp;
    private File notarizedApp;
    private File privateKey;
    private NotarizeMacAppBundle task;

    @Before
    public void setUp() throws Exception {
        Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        Project project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder("project")).build();
        signedApp = new File(project.getProjectDir(), "signed/Game.app");
        File runtime = new File(signedApp, "Contents/Resources/runtime");
        assertTrue(runtime.mkdirs());
        assertTrue(new File(signedApp, "Contents/MacOS").mkdirs());
        File launcher = new File(signedApp, "Contents/MacOS/game");
        Files.write(launcher.toPath(), "launcher".getBytes(StandardCharsets.UTF_8));
        assertTrue(launcher.setExecutable(true));
        Files.write(new File(runtime, "release").toPath(), "JAVA_VERSION=17".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(
                new File(signedApp, "Contents/MacOS/runtime").toPath(), Paths.get("..", "Resources", "runtime"));
        privateKey = new File(project.getProjectDir(), "AuthKey_ABC123.p8");
        Files.write(privateKey.toPath(), "key".getBytes(StandardCharsets.UTF_8));

        log = new File(project.getProjectDir(), "rcodesign.log");
        File rcodesign = new File(project.getProjectDir(), "rcodesign");
        Files.write(
                rcodesign.toPath(),
                ("#!/bin/sh\nset -eu\n"
                                + "printf '%s\\n' \"$*\" >> '" + log.getAbsolutePath() + "'\n"
                                + "case \"$1\" in\n"
                                + "  encode-app-store-connect-api-key) echo '{}' > \"$3\" ;;\n"
                                + "  notary-submit) [ -f \"$3\" ] && echo 'key present' >> '" + log.getAbsolutePath() + "'; "
                                + "echo ticket > \"$5/Contents/CodeResources\" ;;\n"
                                + "esac\n")
                        .getBytes(StandardCharsets.UTF_8));
        assertTrue(rcodesign.setExecutable(true));

        task = project.getTasks().create("notarizeMacAppBundle", NotarizeMacAppBundle.class);
        notarizedApp = new File(project.getProjectDir(), "notarized/Game.app");
        task.getAppDirectory().set(signedApp);
        task.getRcodesign().set(rcodesign);
        task.getIssuerId().set("issuer");
        task.getKeyId().set("ABC123");
        task.getPrivateKeyFile().set(privateKey);
        task.getOutputDirectory().set(notarizedApp);
    }

    @Test
    public void submitsACopyAndStaplesIt() throws Exception {
        task.run();

        File apiKey = new File(task.getTemporaryDir(), "api-key.json");
        List<String> invocations = Files.readAllLines(log.toPath(), StandardCharsets.UTF_8);
        assertEquals(
                Arrays.asList(
                        "encode-app-store-connect-api-key --output-path " + apiKey.getAbsolutePath()
                                + " issuer ABC123 " + privateKey.getAbsolutePath(),
                        "notary-submit --api-key-file " + apiKey.getAbsolutePath() + " --staple "
                                + notarizedApp.getAbsolutePath(),
                        "key present"),
                invocations);
        assertFalse("The API key must not outlive the task", apiKey.exists());
        assertTrue(new File(notarizedApp, "Contents/CodeResources").isFile());
        assertFalse("The signed app stays as it is", new File(signedApp, "Contents/CodeResources").exists());
        assertEquals(
                Paths.get("..", "Resources", "runtime"),
                Files.readSymbolicLink(new File(notarizedApp, "Contents/MacOS/runtime").toPath()));
        assertTrue(new File(notarizedApp, "Contents/MacOS/game").canExecute());
    }

    @Test
    public void needsTheWholeApiKey() {
        task.getKeyId().set((String) null);

        try {
            task.run();
            fail("Expected a missing key ID to be refused");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("notarization.keyId"));
        }
    }
}
