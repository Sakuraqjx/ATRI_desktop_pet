package com.example.desktoppet.behavior.actions;

import com.example.desktoppet.behavior.PetAction;
import com.example.desktoppet.core.PetContext;
import com.example.desktoppet.interaction.PetActionEvent;
import com.example.desktoppet.model.PetActivity;
import com.example.desktoppet.model.PetMood;

public final class PlayAction implements PetAction {
    @Override
    public String id() {
        return "play";
    }

    @Override
    public void execute(PetContext context) {
        context.getPetState().setMood(PetMood.EXCITED);
        context.getPetState().setActivity(PetActivity.PLAYING);
        context.getPetState().changeAffinity(2);
        context.getPetState().changeEnergy(-10);

        context.getPetView().setExpression("owo");
        context.getPetView().jump();
        context.getPetView().showMessage("来玩吧。");
        context.getEventBus().publish(new PetActionEvent(id()));
    }
}
