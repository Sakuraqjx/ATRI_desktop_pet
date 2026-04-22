package com.example.desktoppet;

import com.example.desktoppet.core.PetContext;
import com.example.desktoppet.core.PetEngine;
import com.example.desktoppet.live2d.Live2dCatalog;
import com.example.desktoppet.live2d.Live2dCatalogLoader;
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
            Live2dCatalog live2dCatalog = new Live2dCatalogLoader()
                    .load(Path.of("src", "main", "resources", "live2d", "models.json").toAbsolutePath());
            live2dCatalog = overrideActiveModel(live2dCatalog);

            PetContext context = new PetContext();
            context.setLive2dCatalog(live2dCatalog);

            PetView petView = new PetView(live2dCatalog.activeModel());
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

            petView.showMessage("你好，" + live2dCatalog.activeModel().name() + " 已经准备好陪你了。");
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

    private Live2dCatalog overrideActiveModel(Live2dCatalog live2dCatalog) {
        String selectedModelId = System.getProperty("desktop.pet.model");
        if (selectedModelId == null || selectedModelId.isBlank()) {
            selectedModelId = System.getenv("DESKTOP_PET_MODEL");
        }
        if (selectedModelId == null || selectedModelId.isBlank()) {
            return live2dCatalog;
        }

        String normalizedId = selectedModelId.trim();
        boolean exists = live2dCatalog.models().stream()
                .anyMatch(model -> model.id().equals(normalizedId));
        if (!exists) {
            throw new IllegalStateException("Unknown Live2D model id: " + normalizedId);
        }

        return new Live2dCatalog(normalizedId, live2dCatalog.models());
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
