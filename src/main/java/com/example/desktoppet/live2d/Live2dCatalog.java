package com.example.desktoppet.live2d;

import java.util.List;

public record Live2dCatalog(String activeModelId, List<Live2dModel> models) {
    public Live2dModel activeModel() {
        return models.stream()
                .filter(model -> model.id().equals(activeModelId))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Active Live2D model not found: " + activeModelId));
    }
}
