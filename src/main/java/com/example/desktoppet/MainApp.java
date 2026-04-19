package com.example.desktoppet;

import com.example.desktoppet.core.PetContext;
import com.example.desktoppet.core.PetEngine;
import com.example.desktoppet.ui.ElectronShellLauncher;
import com.example.desktoppet.ui.PetBackendServer;
import com.example.desktoppet.ui.PetView;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainApp {
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private final CountDownLatch shutdownLatch = new CountDownLatch(1);

    private PetEngine petEngine;
    private PetBackendServer backendServer;
    private ElectronShellLauncher shellLauncher;

    public static void main(String[] args) {
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println("[UNCAUGHT] " + thread.getName());
            throwable.printStackTrace(System.err);
        });
        new MainApp().start();
    }

    private void start() {
        try {
            PetContext context = new PetContext();
            PetView petView = new PetView();
            petView.setCloseHandler(this::shutdown);
            context.setPetView(petView);

            petEngine = new PetEngine(context);
            petEngine.start();

            backendServer = new PetBackendServer(
                    Path.of("src", "main", "resources", "live2d").toAbsolutePath(),
                    context,
                    petView,
                    this::shutdown
            );
            backendServer.start();

            shellLauncher = new ElectronShellLauncher(Path.of("desktop-shell").toAbsolutePath());
            shellLauncher.launch(backendServer.getBaseUrl(), this::shutdown);

            petView.showMessage("你好，我已经准备好陪你了。");
            Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "desktop-pet-shutdown"));
            shutdownLatch.await();
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            shutdown();
        } catch (Exception exception) {
            shutdown();
            throw new IllegalStateException("Failed to start desktop pet.", exception);
        }
    }

    private void shutdown() {
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }

        if (shellLauncher != null) {
            shellLauncher.stop();
        }
        if (backendServer != null) {
            backendServer.stop();
        }
        if (petEngine != null) {
            petEngine.stop();
        }
        shutdownLatch.countDown();
    }
}
