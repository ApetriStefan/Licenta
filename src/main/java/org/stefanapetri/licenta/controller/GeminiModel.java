// src\main\java\org\stefanapetri\licenta\controller\GeminiModel.java
package org.stefanapetri.licenta.controller;

import java.util.Optional;

public enum GeminiModel {
    GEMINI_2_5_PRO("gemini-2.5-pro"),
    GEMINI_2_5_FLASH("gemini-2.5-flash");

    private final String modelName;

    GeminiModel(String modelName) {
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }

    @Override
    public String toString() {
        return modelName;
    }

    public static Optional<GeminiModel> fromName(String name) {
        for (GeminiModel model : values()) {
            if (model.modelName.equalsIgnoreCase(name)) {
                return Optional.of(model);
            }
        }
        return Optional.empty();
    }
}