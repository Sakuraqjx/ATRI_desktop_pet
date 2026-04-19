package com.example.desktoppet.ui;

public record PetShellSnapshot(
        String message,
        long messageRevision,
        String expressionKind,
        String expressionValue,
        long expressionRevision,
        String motionGroup,
        String motionName,
        long motionRevision,
        String stateSummary
) {
}
