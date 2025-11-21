package com.seminario.plugin.model;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;

/**
 * Represents an active survey session for a player
 */
public class SurveySession {
    
    private final Player player;
    private final Survey survey;
    private final Location baseLocation;
    private int currentQuestionIndex;
    private List<Integer> responses;
    private List<ItemFrame> faceFrames;
    private List<ArmorStand> hologramStands;
    private boolean isActive;
    
    public SurveySession(Player player, Survey survey, Location baseLocation) {
        this.player = player;
        this.survey = survey;
        this.baseLocation = baseLocation.clone();
        this.currentQuestionIndex = 0;
        this.responses = new ArrayList<>();
        this.faceFrames = new ArrayList<>();
        this.hologramStands = new ArrayList<>();
        this.isActive = true;
    }
    
    public Player getPlayer() {
        return player;
    }
    
    public Survey getSurvey() {
        return survey;
    }
    
    public Location getBaseLocation() {
        return baseLocation;
    }
    
    public int getCurrentQuestionIndex() {
        return currentQuestionIndex;
    }
    
    public void setCurrentQuestionIndex(int index) {
        this.currentQuestionIndex = index;
    }
    
    public String getCurrentQuestion() {
        if (currentQuestionIndex >= 0 && currentQuestionIndex < survey.getQuestionCount()) {
            return survey.getQuestion(currentQuestionIndex);
        }
        return null;
    }
    
    public boolean hasNextQuestion() {
        return currentQuestionIndex < survey.getQuestionCount() - 1;
    }
    
    public void nextQuestion() {
        if (hasNextQuestion()) {
            currentQuestionIndex++;
        }
    }
    
    public List<Integer> getResponses() {
        return new ArrayList<>(responses);
    }
    
    public void addResponse(int rating) {
        responses.add(rating);
    }
    
    public List<ItemFrame> getFaceFrames() {
        return faceFrames;
    }
    
    public void addFaceFrame(ItemFrame frame) {
        faceFrames.add(frame);
    }
    
    public List<ArmorStand> getHologramStands() {
        return hologramStands;
    }
    
    public void addHologramStand(ArmorStand stand) {
        hologramStands.add(stand);
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        this.isActive = active;
    }
    
    public boolean isComplete() {
        return responses.size() >= survey.getQuestionCount();
    }
    
    public int getProgress() {
        return responses.size();
    }
    
    public int getTotalQuestions() {
        return survey.getQuestionCount();
    }
    
    /**
     * Clean up all entities associated with this session
     */
    public void cleanup() {
        // Remove all item frames
        for (ItemFrame frame : faceFrames) {
            if (frame != null && frame.isValid()) {
                frame.remove();
            }
        }
        faceFrames.clear();
        
        // Remove all hologram armor stands
        for (ArmorStand stand : hologramStands) {
            if (stand != null && stand.isValid()) {
                stand.remove();
            }
        }
        hologramStands.clear();
        
        isActive = false;
    }
    
    @Override
    public String toString() {
        return "SurveySession{player=" + player.getName() + 
               ", survey=" + survey.getName() + 
               ", question=" + (currentQuestionIndex + 1) + "/" + survey.getQuestionCount() + 
               ", active=" + isActive + "}";
    }
}