package com.seminario.plugin.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.serialization.ConfigurationSerializable;

/**
 * Represents a survey with questions and player responses
 */
public class Survey implements ConfigurationSerializable {
    
    private String name;
    private List<String> questions;
    private Map<String, List<Integer>> playerResponses; // playerName -> list of responses (1-5)
    
    public Survey() {
        this.questions = new ArrayList<>();
        this.playerResponses = new HashMap<>();
    }
    
    public Survey(String name) {
        this.name = name;
        this.questions = new ArrayList<>();
        this.playerResponses = new HashMap<>();
    }
    
    public Survey(Map<String, Object> map) {
        this.name = (String) map.get("name");
        this.questions = (List<String>) map.getOrDefault("questions", new ArrayList<>());
        this.playerResponses = (Map<String, List<Integer>>) map.getOrDefault("playerResponses", new HashMap<>());
    }
    
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("name", name);
        map.put("questions", questions);
        map.put("playerResponses", playerResponses);
        return map;
    }
    
    // Getters and setters
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public List<String> getQuestions() {
        return questions;
    }
    
    public void addQuestion(String question) {
        this.questions.add(question);
    }
    
    public void editQuestion(int index, String newQuestion) {
        if (index >= 0 && index < questions.size()) {
            questions.set(index, newQuestion);
        }
    }
    
    public void removeQuestion(int index) {
        if (index >= 0 && index < questions.size()) {
            questions.remove(index);
        }
    }
    
    public String getQuestion(int index) {
        if (index >= 0 && index < questions.size()) {
            return questions.get(index);
        }
        return null;
    }
    
    public int getQuestionCount() {
        return questions.size();
    }
    
    public Map<String, List<Integer>> getPlayerResponses() {
        return playerResponses;
    }
    
    public void setPlayerResponse(String playerName, List<Integer> responses) {
        playerResponses.put(playerName, new ArrayList<>(responses));
    }
    
    public List<Integer> getPlayerResponse(String playerName) {
        return playerResponses.get(playerName);
    }
    
    public boolean hasPlayerResponded(String playerName) {
        return playerResponses.containsKey(playerName);
    }
    
    public int getTotalResponses() {
        return playerResponses.size();
    }
    
    /**
     * Get average response for a specific question (1-5 scale)
     */
    public double getQuestionAverage(int questionIndex) {
        if (questionIndex < 0 || questionIndex >= questions.size()) {
            return 0.0;
        }
        
        double sum = 0.0;
        int count = 0;
        
        for (List<Integer> responses : playerResponses.values()) {
            if (responses.size() > questionIndex) {
                sum += responses.get(questionIndex);
                count++;
            }
        }
        
        return count > 0 ? sum / count : 0.0;
    }
    
    /**
     * Get response count for each rating (1-5) for a specific question
     */
    public Map<Integer, Integer> getQuestionDistribution(int questionIndex) {
        Map<Integer, Integer> distribution = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            distribution.put(i, 0);
        }
        
        if (questionIndex < 0 || questionIndex >= questions.size()) {
            return distribution;
        }
        
        for (List<Integer> responses : playerResponses.values()) {
            if (responses.size() > questionIndex) {
                int rating = responses.get(questionIndex);
                distribution.put(rating, distribution.get(rating) + 1);
            }
        }
        
        return distribution;
    }
    
    /**
     * Get overall survey average across all questions
     */
    public double getOverallAverage() {
        if (questions.isEmpty() || playerResponses.isEmpty()) {
            return 0.0;
        }
        
        double totalSum = 0.0;
        int totalCount = 0;
        
        for (int i = 0; i < questions.size(); i++) {
            for (List<Integer> responses : playerResponses.values()) {
                if (responses.size() > i) {
                    totalSum += responses.get(i);
                    totalCount++;
                }
            }
        }
        
        return totalCount > 0 ? totalSum / totalCount : 0.0;
    }
    
    @Override
    public String toString() {
        return "Survey{name='" + name + "', questions=" + questions.size() + ", responses=" + playerResponses.size() + "}";
    }
}