package io.github.fourlastor.construo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import io.github.fourlastor.construo.task.PackageTask;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.commons.compress.archivers.zip.UnixStat;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Assume;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PackageTaskTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void linksStayLinks() throws Exception {
        Assume.assumeFalse(System.getProperty("os.name").startsWith("Windows"));
        Project project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
        File app = new File(project.getProjectDir(), "Game.app");
        File macOs = new File(app, "Contents/MacOS");
        File runtime = new File(app, "Contents/Resources/runtime");
        assertTrue(macOs.mkdirs());
        assertTrue(runtime.mkdirs());
        File launcher = new File(macOs, "game");
        Files.write(launcher.toPath(), "launcher".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(runtime, "release").toPath(), "JAVA_VERSION=17".getBytes(StandardCharsets.UTF_8));
        Files.createSymbolicLink(new File(macOs, "runtime").toPath(), Paths.get("..", "Resources", "runtime"));
        File archive = new File(project.getProjectDir(), "dist/game.zip");

        PackageTask task = project.getTasks().create("packageApp", PackageTask.class);
        task.getFrom().set(app);
        task.getInto().set("Game.app");
        task.getExecutable().set(launcher);
        task.getArchiveFile().set(archive);
        task.getPackageFiles().set(Collections.emptyMap());
        task.run();

        try (ZipFile zip = ZipFile.builder().setFile(archive).get()) {
            ZipArchiveEntry link = zip.getEntry("Game.app/Contents/MacOS/runtime");
            assertNotNull(link);
            assertTrue(link.isUnixSymlink());
            try (InputStream content = zip.getInputStream(link)) {
                assertEquals("../Resources/runtime", new String(content.readAllBytes(), StandardCharsets.UTF_8));
            }
            assertEquals(UnixStat.FILE_FLAG | 0755, zip.getEntry("Game.app/Contents/MacOS/game").getUnixMode());
            assertNotNull(zip.getEntry("Game.app/Contents/Resources/runtime/release"));
            List<String> throughLink = new ArrayList<>();
            Collections.list(zip.getEntries()).forEach(entry -> {
                if (entry.getName().startsWith("Game.app/Contents/MacOS/runtime/")) {
                    throughLink.add(entry.getName());
                }
            });
            assertEquals("A link's target must not be packaged twice", Collections.emptyList(), throughLink);
        }
    }
}
