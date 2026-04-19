package com.example.desktoppet.behavior;

import com.example.desktoppet.core.PetContext;

public interface PetAction {
    String id();

    void execute(PetContext context);
}
