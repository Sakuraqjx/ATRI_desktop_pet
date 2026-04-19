package com.example.desktoppet.live2d;

import java.util.List;
import java.util.Map;

public record Live2dModel(
        String id,
        String name,
        String entry,
        String preview,
        String defaultExpression,
        Map<String, String> expressionAliases,
        Map<String, Live2dMotionBinding> motionBindings,
        List<Live2dMenuExpression> menuExpressions,
        List<Live2dOutfit> outfits
) {
}
