package io.github.fourlastor.construo.task;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskExecutionException;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class DownloadTaskTest {

    @Rule public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void interruptedDownloadDoesNotLeaveDeclaredOutput() throws Exception {
        byte[] partialBody = "partial".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/archive.zip",
                exchange -> {
                    exchange.sendResponseHeaders(200, partialBody.length + 100L);
                    exchange.getResponseBody().write(partialBody);
                    exchange.close();
                });
        server.start();
        try {
            Project project =
                    ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
            DownloadTask task = project.getTasks().create("download", DownloadTask.class);
            File destination = new File(project.getBuildDir(), "downloads/archive.zip");
            task.getSrc()
                    .set("http://127.0.0.1:" + server.getAddress().getPort() + "/archive.zip");
            task.getDest().set(destination);

            assertThrows(TaskExecutionException.class, task::run);
            assertFalse(destination.exists());
            assertFalse(new File(destination.getPath() + ".part").exists());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void successfulDownloadReplacesPartFile() throws Exception {
        byte[] body = "complete archive".getBytes(StandardCharsets.UTF_8);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/archive.zip",
                exchange -> {
                    exchange.sendResponseHeaders(200, body.length);
                    exchange.getResponseBody().write(body);
                    exchange.close();
                });
        server.start();
        try {
            Project project =
                    ProjectBuilder.builder().withProjectDir(temporaryFolder.newFolder()).build();
            DownloadTask task = project.getTasks().create("download", DownloadTask.class);
            File destination = new File(project.getBuildDir(), "downloads/archive.zip");
            task.getSrc()
                    .set("http://127.0.0.1:" + server.getAddress().getPort() + "/archive.zip");
            task.getDest().set(destination);

            task.run();

            assertArrayEquals(body, java.nio.file.Files.readAllBytes(destination.toPath()));
            assertFalse(new File(destination.getPath() + ".part").exists());
        } finally {
            server.stop(0);
        }
    }
}
