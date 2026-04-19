package com.example.desktoppet.plugin;

import com.example.desktoppet.core.PetContext;

public interface PetPlugin {
    String pluginId();

    void activate(PetContext context);
}
