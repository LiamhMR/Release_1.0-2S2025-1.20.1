package com.seminario.plugin.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class QuestDefinition {

    private final String name;
    private final List<QuestQuestion> questions;

    public QuestDefinition(String name, List<QuestQuestion> questions) {
        this.name = name;
        this.questions = new ArrayList<>(questions);
    }

    public String getName() {
        return name;
    }

    public List<QuestQuestion> getQuestions() {
        return Collections.unmodifiableList(questions);
    }

    public int getQuestionCount() {
        return questions.size();
    }
}