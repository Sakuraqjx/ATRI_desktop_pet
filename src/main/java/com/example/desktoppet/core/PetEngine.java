package com.example.desktoppet.core;

import com.example.desktoppet.behavior.actions.GreetAction;
import com.example.desktoppet.behavior.actions.NapAction;
import com.example.desktoppet.behavior.actions.PlayAction;
import com.example.desktoppet.behavior.actions.ThinkAction;
import com.example.desktoppet.interaction.PetActionEvent;
import com.example.desktoppet.plugin.MenuCommand;
import com.example.desktoppet.plugin.PetPlugin;

import java.util.ServiceLoader;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class PetEngine {
    private final PetContext context;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "pet-idle-loop");
        thread.setDaemon(true);
        return thread;
    });

    public PetEngine(PetContext context) {
        this.context = context;
    }

    public void start() {
        registerBuiltInActions();
        registerBuiltInMenu();
        loadPlugins();
        startIdleLoop();
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
        context.addMenuCommand(new MenuCommand("action.nap", "互动/先休息", () -> context.runAction("nap")));
        context.addMenuCommand(new MenuCommand("action.think", "互动/发会呆", () -> context.runAction("think")));
        context.addMenuCommand(new MenuCommand("expression.neutral", "表情/中性", () -> switchExpression("neutral", "恢复成平静表情。")));
        context.addMenuCommand(new MenuCommand("expression.curious", "表情/好奇", () -> switchExpression("curious", "我在认真看着你。")));
        context.addMenuCommand(new MenuCommand("expression.happy", "表情/开心", () -> switchExpression("happy", "见到你就会开心。")));
        context.addMenuCommand(new MenuCommand("expression.sleepy", "表情/困倦", () -> switchExpression("sleepy", "让我先眯一会。")));
        context.addMenuCommand(new MenuCommand("expression.excited", "表情/兴奋", () -> switchExpression("excited", "好耶，来点有趣的。")));
    }

    private void switchExpression(String expressionName, String message) {
        context.getPetView().applyNamedExpression(expressionName);
        context.getPetView().showMessage(message);
    }

    private void loadPlugins() {
        ServiceLoader<PetPlugin> loader = ServiceLoader.load(PetPlugin.class);
        for (PetPlugin plugin : loader) {
            plugin.activate(context);
        }
    }

    private void startIdleLoop() {
        scheduler.scheduleWithFixedDelay(this::triggerIdleBehavior, 6, 6, TimeUnit.SECONDS);
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
}
