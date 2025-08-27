package org.stefanapetri.licenta.controller;

import java.util.Optional;

public enum WhisperModel {
    TINY("tiny"),
    BASE("base"),
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"); // Refers to large-v2 or large-v3

    private final String modelName;

    WhisperModel(String modelName) {
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    public String toString() {
        return modelName.substring(0, 1).toUpperCase() + modelName.substring(1); // Capitalize first letter
    }

    public static Optional<WhisperModel> fromName(String name) {
        for (WhisperModel model : values()) {
            if (model.modelName.equalsIgnoreCase(name)) {
                return Optional.of(model);
            }
        }
        return Optional.empty();
    }
}