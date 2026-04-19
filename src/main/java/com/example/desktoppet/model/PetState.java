package com.example.desktoppet.model;

public final class PetState {
    private PetMood mood = PetMood.CURIOUS;
    private PetActivity activity = PetActivity.IDLE;
    private int energy = 80;
    private int affinity = 0;

    public PetMood getMood() {
        return mood;
    }

    public void setMood(PetMood mood) {
        this.mood = mood;
    }

    public PetActivity getActivity() {
        return activity;
    }

    public void setActivity(PetActivity activity) {
        this.activity = activity;
    }

    public int getEnergy() {
        return energy;
    }

    public void changeEnergy(int delta) {
        energy = Math.max(0, Math.min(100, energy + delta));
    }

    public int getAffinity() {
        return affinity;
    }

    public void changeAffinity(int delta) {
        affinity = Math.max(0, affinity + delta);
    }

    public String summary() {
        return "心情: " + moodLabel() + " | 活动: " + activityLabel() + " | 精力: " + energy + " | 亲密度: " + affinity;
    }

    private String moodLabel() {
        return switch (mood) {
            case CALM -> "平静";
            case CURIOUS -> "好奇";
            case HAPPY -> "开心";
            case SLEEPY -> "困倦";
            case EXCITED -> "兴奋";
        };
    }

    private String activityLabel() {
        return switch (activity) {
            case IDLE -> "发呆";
            case PLAYING -> "玩耍";
            case RESTING -> "休息";
            case INTERACTING -> "互动";
        };
    }
}
