package com.seminario.plugin.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class QuestQuestion {

    private final String prompt;
    private final LinkedHashMap<String, String> options;

    public QuestQuestion(String prompt, Map<String, String> options) {
        this.prompt = prompt;
        this.options = new LinkedHashMap<>();
        if (options != null) {
            for (Map.Entry<String, String> entry : options.entrySet()) {
                this.options.put(entry.getKey(), entry.getValue());
            }
        }
    }

    public String getPrompt() {
        return prompt;
    }

    public LinkedHashMap<String, String> getOptions() {
        return new LinkedHashMap<>(options);
    }
}