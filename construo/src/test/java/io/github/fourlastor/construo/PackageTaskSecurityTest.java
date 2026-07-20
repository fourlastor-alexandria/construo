package io.github.fourlastor.construo;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.github.fourlastor.construo.task.PackageTask;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Collections;
import java.util.zip.ZipFile;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class PackageTaskSecurityTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void rejectsPackageFileDestinationsOutsideTheDistribution() throws Exception {
        for (String destination :
                Arrays.asList("../escape.txt", "safe/../../escape.txt", "/absolute.txt", "C:\\absolute.txt")) {
            Project project =
                    ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
            File input = new File(project.getProjectDir(), "input");
            assertTrue(input.mkdirs());
            File launcher = new File(input, "launcher");
            Files.write(launcher.toPath(), "launcher".getBytes(StandardCharsets.UTF_8));
            File extra = new File(project.getProjectDir(), "extra.txt");
            Files.write(extra.toPath(), "extra".getBytes(StandardCharsets.UTF_8));
            File archive = new File(project.getProjectDir(), "output/package.zip");

            PackageTask task = project.getTasks().create("packageApp", PackageTask.class);
            task.getFrom().set(input);
            task.getExecutable().set(launcher);
            task.getArchiveFile().set(archive);
            task.getPackageFiles()
                    .set(Collections.singletonMap(destination, project.getLayout().getProjectDirectory().file("extra.txt")));

            try {
                task.run();
                fail("Expected destination to be rejected: " + destination);
            } catch (IllegalArgumentException expected) {
                assertTrue(expected.getMessage().contains(destination));
            }
            assertFalse("Invalid mappings must not leave an archive", archive.exists());
        }
    }

    @Test
    public void rejectsArchiveRootOutsideTheDistribution() throws Exception {
        Project project =
                ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
        File input = new File(project.getProjectDir(), "input");
        assertTrue(input.mkdirs());
        File launcher = new File(input, "launcher");
        Files.write(launcher.toPath(), "launcher".getBytes(StandardCharsets.UTF_8));
        File archive = new File(project.getProjectDir(), "output/package.zip");

        PackageTask task = project.getTasks().create("packageApp", PackageTask.class);
        task.getFrom().set(input);
        task.getExecutable().set(launcher);
        task.getArchiveFile().set(archive);
        task.getPackageFiles().set(Collections.emptyMap());
        task.getInto().set("../escape");

        try {
            task.run();
            fail("Expected archive root to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("../escape"));
        }
        assertFalse("Invalid archive roots must not leave an archive", archive.exists());
    }

    @Test
    public void normalizesWindowsSeparatorsInArchiveEntries() throws Exception {
        Project project =
                ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
        File input = new File(project.getProjectDir(), "input");
        assertTrue(input.mkdirs());
        File launcher = new File(input, "launcher");
        Files.write(launcher.toPath(), "launcher".getBytes(StandardCharsets.UTF_8));
        File extra = new File(project.getProjectDir(), "extra.txt");
        Files.write(extra.toPath(), "extra".getBytes(StandardCharsets.UTF_8));
        File archive = new File(project.getProjectDir(), "output/package.zip");

        PackageTask task = project.getTasks().create("packageApp", PackageTask.class);
        task.getFrom().set(input);
        task.getExecutable().set(launcher);
        task.getArchiveFile().set(archive);
        task.getPackageFiles()
                .set(Collections.singletonMap(
                        "runtime\\extra.txt",
                        project.getLayout().getProjectDirectory().file("extra.txt")));
        task.run();

        try (ZipFile zip = new ZipFile(archive)) {
            assertNotNull(zip.getEntry("runtime/extra.txt"));
        }
    }

    @Test
    public void rejectsDuplicateLogicalDestinationsWithMixedSeparators() throws Exception {
        Project project =
                ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
        File input = new File(project.getProjectDir(), "input");
        assertTrue(input.mkdirs());
        File launcher = new File(input, "launcher");
        Files.write(launcher.toPath(), "launcher".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(project.getProjectDir(), "one.txt").toPath(), "one".getBytes(StandardCharsets.UTF_8));
        Files.write(new File(project.getProjectDir(), "two.txt").toPath(), "two".getBytes(StandardCharsets.UTF_8));
        File archive = new File(project.getProjectDir(), "output/package.zip");

        PackageTask task = project.getTasks().create("packageApp", PackageTask.class);
        task.getFrom().set(input);
        task.getExecutable().set(launcher);
        task.getArchiveFile().set(archive);
        Map<String, org.gradle.api.file.RegularFile> mappings = new LinkedHashMap<>();
        mappings.put("runtime/extra.txt", project.getLayout().getProjectDirectory().file("one.txt"));
        mappings.put("runtime\\extra.txt", project.getLayout().getProjectDirectory().file("two.txt"));
        task.getPackageFiles().set(mappings);

        try {
            task.run();
            fail("Expected duplicate logical destinations to be rejected");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Duplicate package entry"));
        }
    }

    @Test
    public void rejectsArchiveInsideInputTree() throws Exception {
        Project project =
                ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
        File input = new File(project.getProjectDir(), "input");
        assertTrue(input.mkdirs());
        File launcher = new File(input, "launcher");
        Files.write(launcher.toPath(), "launcher".getBytes(StandardCharsets.UTF_8));
        File archive = new File(input, "package.zip");

        PackageTask task = project.getTasks().create("packageApp", PackageTask.class);
        task.getFrom().set(input);
        task.getExecutable().set(launcher);
        task.getArchiveFile().set(archive);
        task.getPackageFiles().set(Collections.emptyMap());

        try {
            task.run();
            fail("Expected archive inside input tree to be rejected");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must not be inside package input"));
        }
        assertFalse(archive.exists());
    }
}
