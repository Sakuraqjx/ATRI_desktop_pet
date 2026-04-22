package com.example.desktoppet.live2d;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

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

        Path assetRoot = manifestPath.getParent();
        List<Live2dModel> models = catalogFile.models().stream()
                .map(model -> normalizeModel(assetRoot, model))
                .toList();

        return new Live2dCatalog(activeModelId, models);
    }

    private Live2dModel normalizeModel(Path assetRoot, Live2dModel model) {
        List<Live2dOutfit> outfits = normalizeOutfits(model.outfits());
        List<Live2dMenuExpression> menuExpressions = normalizeMenuExpressions(assetRoot, model, outfits);

        return new Live2dModel(
                model.id(),
                model.name(),
                model.entry(),
                model.preview(),
                model.defaultExpression(),
                model.expressionAliases() == null ? Map.of() : Map.copyOf(model.expressionAliases()),
                model.motionBindings() == null ? Map.of() : Map.copyOf(model.motionBindings()),
                menuExpressions,
                outfits
        );
    }

    private List<Live2dMenuExpression> normalizeMenuExpressions(
            Path assetRoot,
            Live2dModel model,
            List<Live2dOutfit> outfits
    ) {
        Map<String, String> parameterNames = readDisplayParameterNames(assetRoot, model);
        Map<String, List<ExpressionParameter>> expressionParameters = new LinkedHashMap<>();
        Set<String> outfitControlledIds = collectOutfitParameterIds(outfits);
        Map<String, Live2dMenuExpression> merged = new LinkedHashMap<>();

        if (model.menuExpressions() != null) {
            for (Live2dMenuExpression expression : model.menuExpressions()) {
                String category = expression.category() == null ? inferCategory(expression.label()) : expression.category();
                merged.put(expression.expression(), new Live2dMenuExpression(
                        expression.id(),
                        fallbackLabel(expression.label(), expression.expression()),
                        expression.expression(),
                        fallbackMessage(expression.message(), expression.expression()),
                        category,
                        expression.toggleable() || isToggleCategory(category)
                ));
            }
        }

        for (String expressionName : discoverExpressions(assetRoot, model, outfits)) {
            List<ExpressionParameter> parameters = readExpressionParameters(assetRoot, model, expressionName);
            expressionParameters.put(expressionName, parameters);
            if (isOutfitControlled(parameters, outfitControlledIds)) {
                continue;
            }
            merged.putIfAbsent(expressionName, createAutoExpression(expressionName, parameters, parameterNames));
        }

        return List.copyOf(merged.values());
    }

    private List<String> discoverExpressions(Path assetRoot, Live2dModel model, List<Live2dOutfit> outfits) {
        Path entryPath = assetRoot.resolve(model.entry()).normalize();
        LinkedHashSet<String> discovered = new LinkedHashSet<>();

        discovered.addAll(readExpressionsFromEntry(entryPath));
        discovered.addAll(readExpressionsFromDirectory(entryPath.getParent()));

        Set<String> outfitExpressions = outfits.stream()
                .map(Live2dOutfit::expression)
                .filter(expression -> expression != null && !expression.isBlank())
                .collect(LinkedHashSet::new, Set::add, Set::addAll);

        return discovered.stream()
                .filter(expression -> !expression.isBlank())
                .filter(expression -> !outfitExpressions.contains(expression))
                .filter(expression -> !expression.startsWith("outfit_"))
                .toList();
    }

    private List<String> readExpressionsFromEntry(Path entryPath) {
        if (entryPath == null || !Files.isRegularFile(entryPath)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(entryPath.toFile());
            JsonNode expressionsNode = root.path("FileReferences").path("Expressions");
            if (!expressionsNode.isArray()) {
                return List.of();
            }

            List<String> names = new ArrayList<>();
            for (JsonNode expressionNode : expressionsNode) {
                String name = expressionNode.path("Name").asText("");
                if (!name.isBlank()) {
                    names.add(name);
                }
            }
            return names;
        } catch (IOException exception) {
            return List.of();
        }
    }

    private List<String> readExpressionsFromDirectory(Path modelDirectory) {
        if (modelDirectory == null || !Files.isDirectory(modelDirectory)) {
            return List.of();
        }

        try (Stream<Path> stream = Files.list(modelDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".exp3.json"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString().toLowerCase()))
                    .map(path -> stripSuffix(path.getFileName().toString(), ".exp3.json"))
                    .toList();
        } catch (IOException exception) {
            return List.of();
        }
    }

    private Map<String, String> readDisplayParameterNames(Path assetRoot, Live2dModel model) {
        Path entryPath = assetRoot.resolve(model.entry()).normalize();
        if (!Files.isRegularFile(entryPath)) {
            return Map.of();
        }

        try {
            JsonNode root = objectMapper.readTree(entryPath.toFile());
            String displayInfo = root.path("FileReferences").path("DisplayInfo").asText("");
            Path displayInfoPath = resolveDisplayInfoPath(entryPath, displayInfo);
            if (!Files.isRegularFile(displayInfoPath)) {
                return Map.of();
            }

            JsonNode displayRoot = objectMapper.readTree(displayInfoPath.toFile());
            JsonNode parametersNode = displayRoot.path("Parameters");
            if (!parametersNode.isArray()) {
                return Map.of();
            }

            Map<String, String> names = new LinkedHashMap<>();
            for (JsonNode parameterNode : parametersNode) {
                String id = parameterNode.path("Id").asText("");
                String name = parameterNode.path("Name").asText("");
                if (!id.isBlank() && !name.isBlank()) {
                    names.put(id, name.trim());
                }
            }
            return names;
        } catch (IOException exception) {
            return Map.of();
        }
    }

    private Path resolveDisplayInfoPath(Path entryPath, String displayInfo) {
        Path modelDirectory = entryPath.getParent();
        if (modelDirectory == null) {
            return entryPath;
        }
        if (displayInfo != null && !displayInfo.isBlank()) {
            return modelDirectory.resolve(displayInfo).normalize();
        }

        String fileName = entryPath.getFileName().toString();
        String cdiCandidateName = fileName
                .replace(".model3.json", ".cdi3.json")
                .replace(".runtime.cdi3.json", ".cdi3.json");
        Path directCandidate = modelDirectory.resolve(cdiCandidateName).normalize();
        if (Files.isRegularFile(directCandidate)) {
            return directCandidate;
        }

        try (Stream<Path> stream = Files.list(modelDirectory)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".cdi3.json"))
                    .findFirst()
                    .orElse(directCandidate);
        } catch (IOException exception) {
            return directCandidate;
        }
    }

    private List<ExpressionParameter> readExpressionParameters(Path assetRoot, Live2dModel model, String expressionName) {
        Path entryPath = assetRoot.resolve(model.entry()).normalize();
        Path modelDirectory = entryPath.getParent();
        if (modelDirectory == null) {
            return List.of();
        }

        Path expressionPath = resolveExpressionPath(entryPath, modelDirectory, expressionName);
        if (expressionPath == null || !Files.isRegularFile(expressionPath)) {
            return List.of();
        }

        try {
            JsonNode root = objectMapper.readTree(expressionPath.toFile());
            JsonNode parametersNode = root.path("Parameters");
            if (!parametersNode.isArray()) {
                return List.of();
            }

            List<ExpressionParameter> parameters = new ArrayList<>();
            for (JsonNode parameterNode : parametersNode) {
                String id = parameterNode.path("Id").asText("");
                if (!id.isBlank()) {
                    double value = parameterNode.path("Value").asDouble(0);
                    String blend = parameterNode.path("Blend").asText("Overwrite");
                    parameters.add(new ExpressionParameter(id, value, blend));
                }
            }
            return parameters;
        } catch (IOException exception) {
            return List.of();
        }
    }

    private Path resolveExpressionPath(Path entryPath, Path modelDirectory, String expressionName) {
        try {
            JsonNode root = objectMapper.readTree(entryPath.toFile());
            JsonNode expressionsNode = root.path("FileReferences").path("Expressions");
            if (expressionsNode.isArray()) {
                for (JsonNode expressionNode : expressionsNode) {
                    if (expressionName.equals(expressionNode.path("Name").asText(""))) {
                        String file = expressionNode.path("File").asText("");
                        if (!file.isBlank()) {
                            return modelDirectory.resolve(file).normalize();
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            // Fall back to conventional file name resolution below.
        }

        return modelDirectory.resolve(expressionName + ".exp3.json").normalize();
    }

    private Set<String> collectOutfitParameterIds(List<Live2dOutfit> outfits) {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        for (Live2dOutfit outfit : outfits) {
            if (outfit.parameterValues() == null) {
                continue;
            }
            for (Live2dParameterValue parameterValue : outfit.parameterValues()) {
                if (parameterValue.id() != null && !parameterValue.id().isBlank()) {
                    ids.add(parameterValue.id());
                }
            }
        }
        return ids;
    }

    private boolean isOutfitControlled(List<ExpressionParameter> parameters, Set<String> outfitControlledIds) {
        if (parameters.isEmpty()) {
            return false;
        }
        return parameters.stream().allMatch(parameter -> outfitControlledIds.contains(parameter.id()));
    }

    private List<Live2dOutfit> normalizeOutfits(List<Live2dOutfit> outfits) {
        if (outfits == null || outfits.isEmpty()) {
            return List.of();
        }

        return outfits.stream()
                .map(outfit -> new Live2dOutfit(
                        outfit.id(),
                        fallbackLabel(outfit.label(), outfit.id()),
                        outfit.expression(),
                        fallbackMessage(outfit.message(), outfit.id()),
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

    private Live2dMenuExpression createAutoExpression(
            String expressionName,
            List<ExpressionParameter> parameters,
            Map<String, String> parameterNames
    ) {
        String pathLabel = buildExpressionLabel(expressionName, parameters, parameterNames);
        String category = inferCategory(pathLabel);
        String leafLabel = pathLabel.contains("/") ? pathLabel.substring(pathLabel.lastIndexOf('/') + 1) : pathLabel;

        return new Live2dMenuExpression(
                expressionName,
                pathLabel,
                expressionName,
                "切换到 " + leafLabel + "。",
                category,
                isToggleCategory(category)
        );
    }

    private String buildExpressionLabel(
            String expressionName,
            List<ExpressionParameter> parameters,
            Map<String, String> parameterNames
    ) {
        if (parameters.isEmpty()) {
            return "基础/" + prettifyBaseExpressionName(expressionName);
        }

        List<String> labels = parameters.stream()
                .map(parameter -> prettifyParameterLabel(parameter, parameterNames.getOrDefault(parameter.id(), parameter.id())))
                .distinct()
                .toList();

        String category = classifyCategory(labels);
        return category + "/" + String.join(" + ", labels);
    }

    private String classifyCategory(List<String> labels) {
        String joined = String.join(" ", labels);
        if (joined.contains("鸟") || joined.contains("kani") || joined.contains("枕头")) {
            return "道具";
        }
        if (joined.contains("屏幕")) {
            return "特效";
        }
        if (joined.contains("脸") || joined.contains("眼") || joined.contains("眉") || joined.contains("嘴")) {
            return "附加";
        }
        return "附加";
    }

    private String prettifyBaseExpressionName(String expressionName) {
        return switch (expressionName.toLowerCase()) {
            case "neutral" -> "中性";
            case "curious" -> "好奇";
            case "happy" -> "开心";
            case "sleepy" -> "困倦";
            case "excited" -> "兴奋";
            default -> prettifyExpressionName(expressionName);
        };
    }

    private String prettifyParameterLabel(ExpressionParameter parameter, String baseLabel) {
        if (baseLabel.contains("枕头")) {
            return baseLabel + (parameter.value() >= 0 ? " YES" : " NO");
        }
        return baseLabel;
    }

    private String inferCategory(String label) {
        if (label == null || label.isBlank()) {
            return "基础";
        }
        int slash = label.indexOf('/');
        return slash >= 0 ? label.substring(0, slash) : "基础";
    }

    private boolean isToggleCategory(String category) {
        return "附加".equals(category) || "特效".equals(category) || "道具".equals(category);
    }

    private String prettifyExpressionName(String expressionName) {
        String normalized = expressionName.replace('_', ' ').replace('-', ' ').trim();
        if (normalized.isBlank()) {
            return expressionName;
        }
        if (normalized.matches("(?i)^expression\\s*\\d+$")) {
            return normalized.replaceAll("(?i)^expression\\s*", "expression ");
        }
        return normalized;
    }

    private String fallbackLabel(String label, String fallback) {
        return (label == null || label.isBlank()) ? fallback : label;
    }

    private String fallbackMessage(String message, String fallback) {
        return (message == null || message.isBlank()) ? ("切换到 " + fallback + "。") : message;
    }

    private String stripSuffix(String value, String suffix) {
        return value.endsWith(suffix) ? value.substring(0, value.length() - suffix.length()) : value;
    }

    private record ExpressionParameter(String id, double value, String blend) {
    }
}
