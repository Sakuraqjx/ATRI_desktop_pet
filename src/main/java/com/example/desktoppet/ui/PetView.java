package com.example.desktoppet.ui;

import com.example.desktoppet.live2d.Live2dModel;
import com.example.desktoppet.live2d.Live2dOutfit;
import com.example.desktoppet.model.PetState;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public final class PetView {
    private static final long DEFAULT_TRANSIENT_EXPRESSION_MILLIS = 3_500L;

    private final AtomicLong messageRevision = new AtomicLong();
    private final AtomicLong expressionRevision = new AtomicLong();
    private final AtomicLong outfitRevision = new AtomicLong();
    private final AtomicLong motionRevision = new AtomicLong();
    private final Object stateLock = new Object();

    private final Live2dModel live2dModel;

    private volatile String message = "";
    private volatile String motionGroup;
    private volatile String motionName;
    private volatile Runnable closeHandler = () -> {
    };

    private String baseExpressionKind;
    private String baseExpressionValue;
    private String transientExpressionKind;
    private String transientExpressionValue;
    private long transientExpressionExpiresAt;
    private Live2dOutfit currentOutfit;

    public PetView(Live2dModel live2dModel) {
        this.live2dModel = live2dModel;
        this.baseExpressionKind = "named";
        this.baseExpressionValue = defaultExpression(live2dModel);
        this.currentOutfit = defaultOutfit(live2dModel);
    }

    public Live2dModel getLive2dModel() {
        return live2dModel;
    }

    public void setCloseHandler(Runnable closeHandler) {
        this.closeHandler = closeHandler;
    }

    public void showWindow() {
        // The visible shell is hosted by Electron.
    }

    public void showMessage(String message) {
        this.message = message;
        messageRevision.incrementAndGet();
    }

    public void setExpression(String expression) {
        setPersistentExpression("mapped", expression);
    }

    public void applyNamedExpression(String expressionName) {
        setPersistentExpression("named", expressionName);
    }

    public void previewExpression(String expression) {
        previewExpression(expression, DEFAULT_TRANSIENT_EXPRESSION_MILLIS);
    }

    public void previewExpression(String expression, long durationMillis) {
        setTransientExpression("mapped", expression, durationMillis);
    }

    public void previewNamedExpression(String expressionName, long durationMillis) {
        setTransientExpression("named", expressionName, durationMillis);
    }

    public void applyOutfit(Live2dOutfit outfit) {
        if (outfit == null) {
            return;
        }

        synchronized (stateLock) {
            if (currentOutfit != null && currentOutfit.id().equals(outfit.id())) {
                return;
            }
            currentOutfit = outfit;
            outfitRevision.incrementAndGet();
        }
    }

    public void bounce() {
        // Intentionally disabled to keep the desktop pet stable.
    }

    public void jump() {
        // Intentionally disabled to keep the desktop pet stable.
    }

    public void rest() {
        // Intentionally disabled to keep the desktop pet stable.
    }

    public void tilt() {
        // Intentionally disabled to keep the desktop pet stable.
    }

    public PetShellSnapshot snapshot(PetState petState) {
        ExpressionState expressionState = resolveExpressionState();
        Live2dOutfit snapshotOutfit;
        synchronized (stateLock) {
            snapshotOutfit = currentOutfit;
        }

        return new PetShellSnapshot(
                message,
                messageRevision.get(),
                expressionState.kind(),
                expressionState.value(),
                expressionRevision.get(),
                snapshotOutfit == null ? null : snapshotOutfit.expression(),
                snapshotOutfit == null ? List.of() : snapshotOutfit.parameterValues(),
                outfitRevision.get(),
                motionGroup,
                motionName,
                motionRevision.get(),
                petState.summary()
        );
    }

    public void requestClose() {
        closeHandler.run();
    }

    private void setPersistentExpression(String kind, String value) {
        synchronized (stateLock) {
            boolean changed = !equalsNullable(baseExpressionKind, kind)
                    || !equalsNullable(baseExpressionValue, value)
                    || transientExpressionKind != null;
            baseExpressionKind = kind;
            baseExpressionValue = value;
            transientExpressionKind = null;
            transientExpressionValue = null;
            transientExpressionExpiresAt = 0L;
            if (changed) {
                expressionRevision.incrementAndGet();
            }
        }
    }

    private void setTransientExpression(String kind, String value, long durationMillis) {
        long expiresAt = System.currentTimeMillis() + Math.max(durationMillis, 0L);
        synchronized (stateLock) {
            transientExpressionKind = kind;
            transientExpressionValue = value;
            transientExpressionExpiresAt = expiresAt;
            expressionRevision.incrementAndGet();
        }
    }

    private ExpressionState resolveExpressionState() {
        synchronized (stateLock) {
            if (transientExpressionKind != null && System.currentTimeMillis() >= transientExpressionExpiresAt) {
                transientExpressionKind = null;
                transientExpressionValue = null;
                transientExpressionExpiresAt = 0L;
                expressionRevision.incrementAndGet();
            }

            if (transientExpressionKind != null) {
                return new ExpressionState(transientExpressionKind, transientExpressionValue);
            }
            return new ExpressionState(baseExpressionKind, baseExpressionValue);
        }
    }

    private static String defaultExpression(Live2dModel live2dModel) {
        if (live2dModel.defaultExpression() != null && !live2dModel.defaultExpression().isBlank()) {
            return live2dModel.defaultExpression();
        }
        return "neutral";
    }

    private static Live2dOutfit defaultOutfit(Live2dModel live2dModel) {
        if (live2dModel.outfits() != null && !live2dModel.outfits().isEmpty()) {
            return live2dModel.outfits().get(0);
        }
        return null;
    }

    private static boolean equalsNullable(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    private record ExpressionState(String kind, String value) {
    }
}
