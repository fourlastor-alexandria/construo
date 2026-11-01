package io.github.fourlastor.construo;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import io.github.fourlastor.construo.task.macos.ExtractRcodesign;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;
import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class ExtractRcodesignTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void extractsTheExecutableAlone() throws Exception {
        Project project = ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
        byte[] executable = "rcodesign".getBytes(StandardCharsets.UTF_8);
        File archive = new File(project.getProjectDir(), "apple-codesign-0.29.0-x86_64-unknown-linux-musl.tar.gz");
        try (TarArchiveOutputStream tar =
                new TarArchiveOutputStream(new GzipCompressorOutputStream(new FileOutputStream(archive)))) {
            for (String name : Arrays.asList("COPYING", "rcodesign")) {
                byte[] content = name.equals("rcodesign") ? executable : "license".getBytes(StandardCharsets.UTF_8);
                TarArchiveEntry entry = new TarArchiveEntry("apple-codesign-0.29.0-x86_64-unknown-linux-musl/" + name);
                entry.setSize(content.length);
                entry.setMode(0644);
                tar.putArchiveEntry(entry);
                tar.write(content);
                tar.closeArchiveEntry();
            }
        }
        File output = new File(project.getProjectDir(), "bin");

        ExtractRcodesign task = project.getTasks().create("extractRcodesign", ExtractRcodesign.class);
        task.getArchive().set(archive);
        task.getOutputDirectory().set(output);
        task.run();

        List<String> extracted = Arrays.asList(output.list());
        assertEquals(Collections.singletonList("rcodesign"), extracted);
        File rcodesign = new File(output, "rcodesign");
        assertArrayEquals(executable, Files.readAllBytes(rcodesign.toPath()));
        if (!System.getProperty("os.name").startsWith("Windows")) {
            assertTrue(rcodesign.canExecute());
        }
    }
}
