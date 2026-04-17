package com.seminario.plugin.model;

import java.util.LinkedHashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

public class QuestSession {

    private final QuestDefinition quest;
    private final Location lockedLocation;
    private final Map<Integer, String> answers;
    private int questionIndex;
    private String selectedOptionKey;
    private boolean confirmingExit;
    private boolean ignoreNextClose;
    private BukkitTask actionBarTask;

    public QuestSession(QuestDefinition quest, Location lockedLocation) {
        this.quest = quest;
        this.lockedLocation = lockedLocation.clone();
        this.answers = new LinkedHashMap<>();
        this.questionIndex = 0;
        this.selectedOptionKey = null;
        this.confirmingExit = false;
        this.ignoreNextClose = false;
    }

    public QuestDefinition getQuest() {
        return quest;
    }

    public Location getLockedLocation() {
        return lockedLocation.clone();
    }

    public int getQuestionIndex() {
        return questionIndex;
    }

    public void setQuestionIndex(int questionIndex) {
        this.questionIndex = questionIndex;
    }

    public String getSelectedOptionKey() {
        return selectedOptionKey;
    }

    public void setSelectedOptionKey(String selectedOptionKey) {
        this.selectedOptionKey = selectedOptionKey;
    }

    public boolean isConfirmingExit() {
        return confirmingExit;
    }

    public void setConfirmingExit(boolean confirmingExit) {
        this.confirmingExit = confirmingExit;
    }

    public boolean shouldIgnoreNextClose() {
        return ignoreNextClose;
    }

    public void setIgnoreNextClose(boolean ignoreNextClose) {
        this.ignoreNextClose = ignoreNextClose;
    }

    public Map<Integer, String> getAnswers() {
        return answers;
    }

    public BukkitTask getActionBarTask() {
        return actionBarTask;
    }

    public void setActionBarTask(BukkitTask actionBarTask) {
        this.actionBarTask = actionBarTask;
    }
}