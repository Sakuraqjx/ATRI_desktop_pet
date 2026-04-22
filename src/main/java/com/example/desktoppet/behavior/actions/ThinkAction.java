package com.example.desktoppet.behavior.actions;

import com.example.desktoppet.behavior.PetAction;
import com.example.desktoppet.core.PetContext;
import com.example.desktoppet.interaction.PetActionEvent;
import com.example.desktoppet.model.PetActivity;
import com.example.desktoppet.model.PetMood;

public final class ThinkAction implements PetAction {
    private static final String[] THOUGHTS = {
            "今天也想陪着你。",
            "要是有零食就更好了。",
            "我在想新的冒险。",
            "你忙的时候，我会安静陪着你。"
    };

    @Override
    public String id() {
        return "think";
    }

    @Override
    public void execute(PetContext context) {
        context.getPetState().setMood(PetMood.CURIOUS);
        context.getPetState().setActivity(PetActivity.IDLE);
        context.getPetState().changeEnergy(-1);

        int index = context.getRandom().nextInt(THOUGHTS.length);
        context.getPetView().tilt();
        context.getPetView().previewExpression("o_o");
        context.getPetView().showMessage(THOUGHTS[index]);
        context.getEventBus().publish(new PetActionEvent(id()));
    }
}
