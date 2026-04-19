package com.example.desktoppet.ui;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ElectronShellLauncher {
    private final Path shellDirectory;

    private Process shellProcess;

    public ElectronShellLauncher(Path shellDirectory) {
        this.shellDirectory = shellDirectory;
    }

    public void launch(String backendUrl, Runnable onExit) throws IOException, InterruptedException {
        ensureDependencies();

        List<String> command = new ArrayList<>();
        command.add(electronExecutable().toString());
        command.add(".");
        command.add("--backend=" + backendUrl);

        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(shellDirectory.toFile());
        builder.environment().remove("ELECTRON_RUN_AS_NODE");
        builder.redirectOutput(shellDirectory.resolve("electron-shell.out.log").toFile());
        builder.redirectError(shellDirectory.resolve("electron-shell.err.log").toFile());

        shellProcess = builder.start();

        Thread watcher = new Thread(() -> {
            try {
                shellProcess.waitFor();
            } catch (InterruptedException interruptedException) {
                Thread.currentThread().interrupt();
            }
            onExit.run();
        }, "electron-shell-watcher");
        watcher.setDaemon(true);
        watcher.start();
    }

    public void stop() {
        if (shellProcess != null && shellProcess.isAlive()) {
            shellProcess.destroy();
        }
    }

    private void ensureDependencies() throws IOException, InterruptedException {
        if (Files.exists(electronExecutable())) {
            return;
        }

        ProcessBuilder builder = new ProcessBuilder(npmExecutable(), "install");
        builder.directory(shellDirectory.toFile());
        builder.environment().remove("ELECTRON_RUN_AS_NODE");
        builder.redirectOutput(shellDirectory.resolve("electron-bootstrap.out.log").toFile());
        builder.redirectError(shellDirectory.resolve("electron-bootstrap.err.log").toFile());

        Process bootstrap = builder.start();
        int exitCode = bootstrap.waitFor();
        if (exitCode != 0) {
            throw new IllegalStateException("Failed to install Electron shell dependencies. See desktop-shell/electron-bootstrap.err.log.");
        }
    }

    private String npmExecutable() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return osName.contains("win") ? "npm.cmd" : "npm";
    }

    private Path electronExecutable() {
        String osName = System.getProperty("os.name", "").toLowerCase();
        return osName.contains("win")
                ? shellDirectory.resolve("node_modules").resolve(".bin").resolve("electron.cmd")
                : shellDirectory.resolve("node_modules").resolve(".bin").resolve("electron");
    }
}
