package com.example.desktoppet.core;

import com.example.desktoppet.behavior.PetAction;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class ActionRegistry {
    private final Map<String, PetAction> actions = new LinkedHashMap<>();

    public void register(PetAction action) {
        actions.put(action.id(), action);
    }

    public Optional<PetAction> find(String id) {
        return Optional.ofNullable(actions.get(id));
    }

    public Collection<PetAction> all() {
        return actions.values();
    }
}
