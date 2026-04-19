package com.example.desktoppet.behavior.actions;

import com.example.desktoppet.behavior.PetAction;
import com.example.desktoppet.core.PetContext;
import com.example.desktoppet.interaction.PetActionEvent;
import com.example.desktoppet.model.PetActivity;
import com.example.desktoppet.model.PetMood;

public final class GreetAction implements PetAction {
    @Override
    public String id() {
        return "greet";
    }

    @Override
    public void execute(PetContext context) {
        context.getPetState().setMood(PetMood.HAPPY);
        context.getPetState().setActivity(PetActivity.INTERACTING);
        context.getPetState().changeAffinity(1);
        context.getPetState().changeEnergy(-2);

        context.getPetView().previewExpression("^_^");
        context.getPetView().showMessage("见到你很开心。");
        context.getEventBus().publish(new PetActionEvent(id()));
    }
}
