package com.example.desktoppet.behavior.actions;

import com.example.desktoppet.behavior.PetAction;
import com.example.desktoppet.core.PetContext;
import com.example.desktoppet.interaction.PetActionEvent;
import com.example.desktoppet.model.PetActivity;
import com.example.desktoppet.model.PetMood;

public final class NapAction implements PetAction {
    @Override
    public String id() {
        return "nap";
    }

    @Override
    public void execute(PetContext context) {
        context.getPetState().setMood(PetMood.SLEEPY);
        context.getPetState().setActivity(PetActivity.RESTING);
        context.getPetState().changeEnergy(12);

        context.getPetView().rest();
        context.getPetView().previewExpression("-_-");
        context.getPetView().showMessage("先休息一下。");
        context.getEventBus().publish(new PetActionEvent(id()));
    }
}
