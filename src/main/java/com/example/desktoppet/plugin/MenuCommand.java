package com.example.desktoppet.plugin;

public record MenuCommand(String id, String label, Runnable handler) {
}
