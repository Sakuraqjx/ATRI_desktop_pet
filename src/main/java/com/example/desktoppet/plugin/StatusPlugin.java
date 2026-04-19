package com.example.desktoppet.plugin;

import com.example.desktoppet.core.PetContext;
import com.example.desktoppet.interaction.PetActionEvent;

public final class StatusPlugin implements PetPlugin {
    @Override
    public String pluginId() {
        return "status-plugin";
    }

    @Override
    public void activate(PetContext context) {
        context.addMenuCommand(new MenuCommand(
                "status.overview",
                "查看状态",
                () -> context.getPetView().showMessage(context.getPetState().summary())
        ));

        context.getEventBus().subscribe(PetActionEvent.class, event -> {
            if ("play".equals(event.actionId()) && context.getPetState().getEnergy() < 30) {
                context.getPetView().showMessage("我有点累了，陪我休息一下吧。");
            }
        });
    }
}
