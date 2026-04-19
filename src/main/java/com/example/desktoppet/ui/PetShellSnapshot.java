package com.example.desktoppet.ui;

import com.example.desktoppet.live2d.Live2dParameterValue;

import java.util.List;

public record PetShellSnapshot(
        String message,
        long messageRevision,
        String expressionKind,
        String expressionValue,
        long expressionRevision,
        String outfitExpression,
        List<Live2dParameterValue> outfitParameters,
        long outfitRevision,
        String motionGroup,
        String motionName,
        long motionRevision,
        String stateSummary
) {
}
