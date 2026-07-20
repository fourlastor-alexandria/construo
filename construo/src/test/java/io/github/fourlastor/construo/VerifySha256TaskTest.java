package io.github.fourlastor.construo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class VerifySha256TaskTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void acceptsTheExpectedDigestAndHasNoOutputs() throws Exception {
        File archive = temporaryFolder.newFile("archive.zip");
        Files.write(archive.toPath(), "verified bytes".getBytes(StandardCharsets.UTF_8));
        Task task = verifyTask(archive, sha256(archive));

        invokeVerify(task);

        assertFalse("Verification must run for warm downloads", task.getOutputs().getHasOutput());
        Method archiveGetter = taskType().getMethod("getArchive");
        Method digestGetter = taskType().getMethod("getExpectedSha256");
        assertNotNull(archiveGetter.getAnnotation(InputFile.class));
        assertNotNull(digestGetter.getAnnotation(Input.class));
    }

    @Test
    public void rejectsAMismatchedDigest() throws Exception {
        File archive = temporaryFolder.newFile("archive.zip");
        Files.write(archive.toPath(), "untrusted bytes".getBytes(StandardCharsets.UTF_8));
        Task task = verifyTask(archive, repeat("0", 64));

        Throwable failure = expectFailure(task);

        assertTrue(failure.getMessage().contains("SHA-256 mismatch"));
        assertTrue(failure.getMessage().contains(archive.getName()));
        assertFalse("A mismatched cached archive must be downloaded again", archive.exists());
    }

    @Test
    public void rejectsMalformedDigestBeforeReadingTheArchive() throws Exception {
        File missingArchive = new File(temporaryFolder.getRoot(), "missing.zip");
        Task task = verifyTask(missingArchive, "not-a-sha256");

        Throwable failure = expectFailure(task);

        assertTrue(failure.getMessage().contains("64 hexadecimal"));
    }

    private Task verifyTask(File archive, String expectedSha256) throws Exception {
        Project project =
                ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
        Class<? extends Task> taskType = taskType();
        Task task = project.getTasks().create("verifyArchive", taskType);
        property(task, "getArchive", RegularFileProperty.class).set(archive);
        property(task, "getExpectedSha256", Property.class).set(expectedSha256);
        return task;
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends Task> taskType() throws ClassNotFoundException {
        return (Class<? extends Task>)
                Class.forName("io.github.fourlastor.construo.task.VerifySha256Task");
    }

    private static void invokeVerify(Task task) throws Exception {
        task.getClass().getMethod("run").invoke(task);
    }

    private static Throwable expectFailure(Task task) throws Exception {
        try {
            invokeVerify(task);
            fail("Expected SHA-256 verification to fail");
            throw new AssertionError("unreachable");
        } catch (InvocationTargetException error) {
            return error.getCause();
        }
    }

    @SuppressWarnings("unchecked")
    private static <T> T property(Object owner, String getter, Class<T> type) throws Exception {
        return (T) type.cast(owner.getClass().getMethod(getter).invoke(owner));
    }

    private static String sha256(File file) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = Files.readAllBytes(file.toPath());
        byte[] result = digest.digest(bytes);
        StringBuilder hex = new StringBuilder(result.length * 2);
        for (byte value : result) {
            hex.append(String.format("%02x", value));
        }
        return hex.toString();
    }

    private static String repeat(String value, int count) {
        StringBuilder result = new StringBuilder(value.length() * count);
        for (int index = 0; index < count; index++) {
            result.append(value);
        }
        return result.toString();
    }
}
