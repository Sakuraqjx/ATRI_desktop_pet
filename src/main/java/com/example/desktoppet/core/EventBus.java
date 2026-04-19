package com.example.desktoppet.core;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class EventBus {
    private final Map<Class<?>, List<Consumer<?>>> listeners = new ConcurrentHashMap<>();

    public <T> void subscribe(Class<T> eventType, Consumer<T> consumer) {
        listeners.computeIfAbsent(eventType, key -> new CopyOnWriteArrayList<>()).add(consumer);
    }

    @SuppressWarnings("unchecked")
    public <T> void publish(T event) {
        List<Consumer<?>> consumers = listeners.get(event.getClass());
        if (consumers == null) {
            return;
        }

        for (Consumer<?> consumer : consumers) {
            ((Consumer<T>) consumer).accept(event);
        }
    }
}
