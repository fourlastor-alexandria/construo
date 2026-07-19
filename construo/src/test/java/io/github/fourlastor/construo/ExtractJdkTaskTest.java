package io.github.fourlastor.construo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import io.github.fourlastor.construo.task.ExtractJdkTask;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ExtractJdkTaskTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void replacesStaleOutputWithOnlyVerifiedArchiveContents() throws Exception {
        Project project =
                ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
        File archive = new File(project.getProjectDir(), "jdk.zip");
        writeZip(archive, "jdk/bin/java", "java", "jdk/release", "JAVA_VERSION=17");
        File output = new File(project.getBuildDir(), "jdk");
        assertTrue(output.mkdirs());
        Files.write(new File(output, "obsolete.txt").toPath(), "stale".getBytes(StandardCharsets.UTF_8));

        ExtractJdkTask task = project.getTasks().create("extractJdk", ExtractJdkTask.class);
        task.getArchive().set(archive);
        task.getOutputDirectory().set(output);
        task.run();

        assertFalse(new File(output, "obsolete.txt").exists());
        assertTrue(new File(output, "bin/java").isFile());
        assertTrue(new File(output, "release").isFile());
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
}
