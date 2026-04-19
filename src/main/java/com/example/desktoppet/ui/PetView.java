package com.example.desktoppet.ui;

import com.example.desktoppet.model.PetState;

import java.util.concurrent.atomic.AtomicLong;

public final class PetView {
    private final AtomicLong messageRevision = new AtomicLong();
    private final AtomicLong expressionRevision = new AtomicLong();
    private final AtomicLong motionRevision = new AtomicLong();

    private volatile String message = "";
    private volatile String expressionKind = "mapped";
    private volatile String expressionValue = "o_o";
    private volatile String motionGroup = "idle";
    private volatile String motionName = "rest";
    private volatile Runnable closeHandler = () -> {
    };

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
        expressionKind = "mapped";
        expressionValue = expression;
        expressionRevision.incrementAndGet();
    }

    public void applyNamedExpression(String expressionName) {
        expressionKind = "named";
        expressionValue = expressionName;
        expressionRevision.incrementAndGet();
    }

    public void bounce() {
        setMotion("react", "bounce");
    }

    public void jump() {
        setMotion("react", "jump");
    }

    public void rest() {
        setMotion("idle", "rest");
    }

    public void tilt() {
        setMotion("react", "tilt");
    }

    public PetShellSnapshot snapshot(PetState petState) {
        return new PetShellSnapshot(
                message,
                messageRevision.get(),
                expressionKind,
                expressionValue,
                expressionRevision.get(),
                motionGroup,
                motionName,
                motionRevision.get(),
                petState.summary()
        );
    }

    public void requestClose() {
        closeHandler.run();
    }

    private void setMotion(String group, String name) {
        motionGroup = group;
        motionName = name;
        motionRevision.incrementAndGet();
    }
}
