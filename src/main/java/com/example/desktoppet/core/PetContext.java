package com.example.desktoppet.core;

import com.example.desktoppet.live2d.Live2dCatalog;
import com.example.desktoppet.plugin.MenuCommand;
import com.example.desktoppet.ui.PetView;

import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

import com.example.desktoppet.model.PetState;

public final class PetContext {
    private final EventBus eventBus = new EventBus();
    private final ActionRegistry actionRegistry = new ActionRegistry();
    private final PetState petState = new PetState();
    private final List<MenuCommand> menuCommands = new CopyOnWriteArrayList<>();
    private final Random random = new Random();

    private PetView petView;
    private Live2dCatalog live2dCatalog;

    public EventBus getEventBus() {
        return eventBus;
    }

    public ActionRegistry getActionRegistry() {
        return actionRegistry;
    }

    public PetState getPetState() {
        return petState;
    }

    public Random getRandom() {
        return random;
    }

    public PetView getPetView() {
        return petView;
    }

    public void setPetView(PetView petView) {
        this.petView = petView;
    }

    public Live2dCatalog getLive2dCatalog() {
        return live2dCatalog;
    }

    public void setLive2dCatalog(Live2dCatalog live2dCatalog) {
        this.live2dCatalog = live2dCatalog;
    }

    public void runAction(String actionId) {
        actionRegistry.find(actionId).ifPresent(action -> action.execute(this));
    }

    public void addMenuCommand(MenuCommand command) {
        menuCommands.add(command);
    }

    public List<MenuCommand> getMenuCommands() {
        return List.copyOf(menuCommands);
    }

    public void invokeMenuCommand(String commandId) {
        menuCommands.stream()
                .filter(command -> command.id().equals(commandId))
                .findFirst()
                .ifPresent(command -> command.handler().run());
    }
}
