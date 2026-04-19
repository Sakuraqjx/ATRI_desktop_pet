package com.example.desktoppet.core;

import com.example.desktoppet.behavior.actions.GreetAction;
import com.example.desktoppet.behavior.actions.NapAction;
import com.example.desktoppet.behavior.actions.PlayAction;
import com.example.desktoppet.behavior.actions.ThinkAction;
import com.example.desktoppet.interaction.PetActionEvent;
import com.example.desktoppet.live2d.Live2dMenuExpression;
import com.example.desktoppet.live2d.Live2dModel;
import com.example.desktoppet.live2d.Live2dOutfit;
import com.example.desktoppet.plugin.MenuCommand;
import com.example.desktoppet.plugin.PetPlugin;

import java.util.LinkedHashSet;
import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class PetEngine {
    private static final int MIN_IDLE_DELAY_SECONDS = 18;
    private static final int MAX_IDLE_DELAY_SECONDS = 32;

    private final PetContext context;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pet-idle-loop");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean idleEnabled = new AtomicBoolean(true);

    public PetEngine(PetContext context) {
        this.context = context;
    }

    public void start() {
        registerBuiltInActions();
        registerBuiltInMenu();
        loadPlugins();
        scheduleNextIdleTick();
    }

    public void stop() {
        scheduler.shutdownNow();
    }

    private void registerBuiltInActions() {
        context.getActionRegistry().register(new GreetAction());
        context.getActionRegistry().register(new PlayAction());
        context.getActionRegistry().register(new NapAction());
        context.getActionRegistry().register(new ThinkAction());
    }

    private void registerBuiltInMenu() {
        context.addMenuCommand(new MenuCommand("action.greet", "互动/打个招呼", () -> context.runAction("greet")));
        context.addMenuCommand(new MenuCommand("action.play", "互动/一起玩", () -> context.runAction("play")));
        context.addMenuCommand(new MenuCommand("action.nap", "互动/先休息一下", () -> context.runAction("nap")));
        context.addMenuCommand(new MenuCommand("action.think", "互动/发会呆", () -> context.runAction("think")));
        context.addMenuCommand(new MenuCommand("idle.enable", "待机/开启自动互动", this::enableIdleLoop));
        context.addMenuCommand(new MenuCommand("idle.disable", "待机/暂停自动互动", this::disableIdleLoop));

        Live2dModel activeModel = context.getLive2dCatalog().activeModel();
        registerExpressionMenu(activeModel);
        registerOutfitMenu(activeModel);
    }

    private void registerExpressionMenu(Live2dModel activeModel) {
        LinkedHashSet<String> toggleCategories = new LinkedHashSet<>();
        for (Live2dMenuExpression expression : activeModel.menuExpressions()) {
            if (expression.toggleable()) {
                toggleCategories.add(expression.category());
            }
            context.addMenuCommand(new MenuCommand(
                    "expression." + expression.id(),
                    "表情/" + expression.label(),
                    () -> {
                        if (expression.toggleable()) {
                            toggleExpression(expression);
                        } else {
                            switchExpression(expression.expression(), expression.message());
                        }
                    }
            ));
        }

        for (String category : toggleCategories) {
            if (category == null || category.isBlank()) {
                continue;
            }
            context.addMenuCommand(new MenuCommand(
                    "expression.clear." + category,
                    "表情/" + category + "/关闭当前",
                    () -> clearExpressionCategory(category)
            ));
        }
    }

    private void registerOutfitMenu(Live2dModel activeModel) {
        for (Live2dOutfit outfit : activeModel.outfits()) {
            context.addMenuCommand(new MenuCommand(
                    "outfit." + outfit.id(),
                    "换装/" + outfit.label(),
                    () -> switchOutfit(outfit)
            ));
        }
    }

    private void switchExpression(String expressionName, String message) {
        context.getPetView().applyNamedExpression(expressionName);
        context.getPetView().showMessage(message);
    }

    private void toggleExpression(Live2dMenuExpression expression) {
        boolean enabled = context.getPetView().toggleOverlayExpression(expression.category(), expression.expression());
        String leafLabel = leafLabel(expression.label());
        context.getPetView().showMessage(enabled
                ? "已开启 " + leafLabel + "。"
                : "已关闭 " + leafLabel + "。");
    }

    private void clearExpressionCategory(String category) {
        boolean changed = context.getPetView().clearOverlayCategory(category);
        context.getPetView().showMessage(changed
                ? "已关闭 " + category + "。"
                : category + " 当前没有开启项。");
    }

    private void switchOutfit(Live2dOutfit outfit) {
        context.getPetView().applyOutfit(outfit);
        context.getPetView().showMessage(outfit.message());
    }

    private void loadPlugins() {
        ServiceLoader<PetPlugin> loader = ServiceLoader.load(PetPlugin.class);
        for (PetPlugin plugin : loader) {
            plugin.activate(context);
        }
    }

    private void enableIdleLoop() {
        boolean changed = idleEnabled.compareAndSet(false, true);
        context.getPetView().showMessage(changed ? "自动互动已开启，频率已经放慢。" : "自动互动本来就是开启的。");
    }

    private void disableIdleLoop() {
        boolean changed = idleEnabled.compareAndSet(true, false);
        context.getPetView().showMessage(changed ? "自动互动已暂停。" : "自动互动已经是暂停状态。");
    }

    private void scheduleNextIdleTick() {
        long delaySeconds = MIN_IDLE_DELAY_SECONDS
                + context.getRandom().nextInt(MAX_IDLE_DELAY_SECONDS - MIN_IDLE_DELAY_SECONDS + 1);
        scheduler.schedule(this::runIdleCycle, delaySeconds, TimeUnit.SECONDS);
    }

    private void runIdleCycle() {
        try {
            if (!idleEnabled.get()) {
                return;
            }
            triggerIdleBehavior();
        } finally {
            if (!scheduler.isShutdown()) {
                scheduleNextIdleTick();
            }
        }
    }

    private void triggerIdleBehavior() {
        int roll = context.getRandom().nextInt(100);
        if (roll < 35) {
            context.runAction("think");
        } else if (roll < 70) {
            context.runAction("greet");
        } else {
            context.runAction("nap");
        }
        context.getEventBus().publish(new PetActionEvent("idle-loop"));
    }

    private String leafLabel(String label) {
        if (label == null || label.isBlank()) {
            return "表情";
        }
        int slash = label.lastIndexOf('/');
        return slash >= 0 ? label.substring(slash + 1) : label;
    }
}
