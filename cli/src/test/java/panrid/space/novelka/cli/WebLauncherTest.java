package panrid.space.novelka.cli;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class WebLauncherTest {
    @TempDir Path temporary;
    private Path project;
    private Path caller;

    @BeforeEach
    void prepare() throws Exception {
        project = Files.createDirectory(temporary.resolve("project with spaces"));
        caller = Files.createDirectory(temporary.resolve("caller"));
        Files.copy(Path.of("../novelka-web"), project.resolve("novelka-web"));
        Path bin = project.resolve("test-bin/java");
        Files.createDirectories(bin.getParent());
        executable(bin, """
                #!/bin/sh
                printf 'cwd=%s\n' "$PWD"
                printf 'env=%s\n' "${NOVELKA_TEST_VALUE:-missing}"
                printf 'arg=%s\n' "$@"
                """);
        executable(project.resolve("gradlew"), """
                #!/bin/sh
                [ "$1" = "-p" ] && [ "$2" = "$(dirname "$0")" ] || exit 9
                printf 'build-log\n'
                exit "${NOVELKA_TEST_BUILD_EXIT:-0}"
                """);
    }

    @Test
    void buildsFromProjectButPreservesCallerDirectoryAndArguments() throws Exception {
        Files.writeString(project.resolve(".env.local"), "NOVELKA_TEST_VALUE=local\n");
        Process process = launcher("--server.port=8081", "--test=argument with spaces").start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        String output = new String(process.getInputStream().readAllBytes());
        String workingDirectory = output.lines().filter(line -> line.startsWith("cwd="))
                .findFirst().orElseThrow().substring(4);
        assertEquals(caller.toRealPath(), Path.of(workingDirectory).toRealPath());
        assertTrue(output.contains("env=local"));
        assertTrue(output.contains("arg=-jar"));
        assertTrue(output.contains("arg=" + project.resolve("server/build/libs/novelka-server.jar")));
        assertTrue(output.contains("arg=--test=argument with spaces"));
        assertFalse(output.contains("build-log"));
        assertTrue(new String(process.getErrorStream().readAllBytes()).contains("build-log"));
    }

    @Test
    void loadsExplicitRelativeEnvFileFromCallerDirectory() throws Exception {
        Files.writeString(caller.resolve("custom.env"), "NOVELKA_TEST_VALUE=custom\n");
        var builder = launcher("--version");
        builder.environment().put("NOVELKA_ENV_FILE", "custom.env");
        Process process = builder.start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(0, process.exitValue());
        assertTrue(new String(process.getInputStream().readAllBytes()).contains("env=custom"));
    }

    @Test
    void missingExplicitEnvFileDoesNotLaunchWithFallbackCredentials() throws Exception {
        var builder = launcher("--server.port=8081");
        builder.environment().put("NOVELKA_ENV_FILE", "missing.env");
        Process process = builder.start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(1, process.exitValue());
        assertEquals("", new String(process.getInputStream().readAllBytes()));
        assertTrue(new String(process.getErrorStream().readAllBytes()).contains("Environment file not found"));
    }

    @Test
    void failedBuildNeverRunsExistingDistribution() throws Exception {
        var builder = launcher("--version");
        builder.environment().put("NOVELKA_TEST_BUILD_EXIT", "7");
        Process process = builder.start();
        assertTrue(process.waitFor(10, TimeUnit.SECONDS));
        assertEquals(7, process.exitValue());
        assertEquals("", new String(process.getInputStream().readAllBytes()));
    }

    private ProcessBuilder launcher(String... arguments) {
        var command = new java.util.ArrayList<String>();
        command.add("/bin/sh");
        command.add(project.resolve("novelka-web").toString());
        command.addAll(java.util.List.of(arguments));
        var builder = new ProcessBuilder(command).directory(caller.toFile());
        builder.environment().put("PATH", project.resolve("test-bin") + ":" + builder.environment().get("PATH"));
        builder.environment().remove("NOVELKA_ENV_FILE");
        builder.environment().remove("NOVELKA_TEST_VALUE");
        builder.environment().remove("NOVELKA_TEST_BUILD_EXIT");
        return builder;
    }

    private void executable(Path file, String text) throws Exception {
        Files.writeString(file, text);
        assertTrue(file.toFile().setExecutable(true));
    }
}
