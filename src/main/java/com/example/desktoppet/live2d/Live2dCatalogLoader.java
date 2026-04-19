package com.example.desktoppet.live2d;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public final class Live2dCatalogLoader {
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public Live2dCatalog load(Path manifestPath) throws IOException {
        Live2dCatalogFile catalogFile = objectMapper.readValue(manifestPath.toFile(), Live2dCatalogFile.class);
        if (catalogFile.models() == null || catalogFile.models().isEmpty()) {
            throw new IllegalStateException("No Live2D models were defined in " + manifestPath);
        }

        String activeModelId = catalogFile.activeModelId();
        if (activeModelId == null || activeModelId.isBlank()) {
            activeModelId = catalogFile.models().get(0).id();
        }

        List<Live2dModel> models = catalogFile.models().stream()
                .map(this::normalizeModel)
                .toList();

        return new Live2dCatalog(activeModelId, models);
    }

    private Live2dModel normalizeModel(Live2dModel model) {
        return new Live2dModel(
                model.id(),
                model.name(),
                model.entry(),
                model.preview(),
                model.defaultExpression(),
                model.expressionAliases() == null ? Map.of() : Map.copyOf(model.expressionAliases()),
                model.motionBindings() == null ? Map.of() : Map.copyOf(model.motionBindings()),
                model.menuExpressions() == null ? List.of() : List.copyOf(model.menuExpressions()),
                normalizeOutfits(model.outfits())
        );
    }

    private List<Live2dOutfit> normalizeOutfits(List<Live2dOutfit> outfits) {
        if (outfits == null || outfits.isEmpty()) {
            return List.of();
        }

        return outfits.stream()
                .map(outfit -> new Live2dOutfit(
                        outfit.id(),
                        outfit.label(),
                        outfit.expression(),
                        outfit.message(),
                        normalizeParameters(outfit.parameterValues())
                ))
                .toList();
    }

    private List<Live2dParameterValue> normalizeParameters(List<Live2dParameterValue> parameters) {
        if (parameters == null || parameters.isEmpty()) {
            return List.of();
        }

        return parameters.stream()
                .map(parameter -> new Live2dParameterValue(
                        parameter.id(),
                        parameter.value(),
                        parameter.blend() == null || parameter.blend().isBlank() ? "Overwrite" : parameter.blend()
                ))
                .toList();
    }
}
