package io.github.fourlastor.construo;

import static org.junit.Assert.assertEquals;

import io.github.fourlastor.construo.task.jvm.RoastTask;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.FileTime;
import java.util.Collections;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class RoastTaskTest {

    private static final long NORMALIZED_JAR_MTIME_MILLIS = 1_700_000_000_000L;

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void preservesTheApplicationJarTimestamp() throws Exception {
        File projectDirectory = temporaryFolder.newFolder("project");
        Project project = ProjectBuilder.builder().withProjectDir(projectDirectory).build();
        File runtime = temporaryFolder.newFolder("runtime");
        Files.write(new File(runtime, "release").toPath(), new byte[] {0});
        File jar = temporaryFolder.newFile("indexino-cli.jar");
        Files.write(jar.toPath(), "application".getBytes(StandardCharsets.UTF_8));
        Files.setLastModifiedTime(jar.toPath(), FileTime.fromMillis(NORMALIZED_JAR_MTIME_MILLIS));
        File launcher = temporaryFolder.newFile("roast-macos-aarch64");
        Files.write(launcher.toPath(), "launcher".getBytes(StandardCharsets.UTF_8));
        File output = new File(projectDirectory, "build/roast");

        RoastTask task = project.getTasks().create("roast", RoastTask.class);
        task.getJdkRoot().set(runtime);
        task.getAppName().set("indexino");
        task.getMainClassName().set("example.Main");
        task.getRoastExe().set(launcher);
        task.getRoastExeName().set("indexino");
        task.getJarFile().set(jar);
        task.getRunOnFirstThread().set(true);
        task.getVmArgs().set(Collections.emptyList());
        task.getUseZgc().set(false);
        task.getUseMainAsContextClassLoader().set(false);
        task.getOutput().set(output);

        task.run();

        assertEquals(
                NORMALIZED_JAR_MTIME_MILLIS,
                Files.getLastModifiedTime(new File(output, jar.getName()).toPath()).toMillis());
    }
}
