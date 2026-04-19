package com.example.desktoppet.live2d;

import java.util.List;

public record Live2dOutfit(
        String id,
        String label,
        String expression,
        String message,
        List<Live2dParameterValue> parameterValues
) {
}
