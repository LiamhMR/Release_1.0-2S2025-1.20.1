package com.seminario.plugin.sql.context.challenge;

import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction for challenge banks per SQL context.
 */
public interface SQLChallengeRepository {

    Optional<SQLChallengeDefinition> getRandomChallenge(String difficultyKey);

    List<SQLChallengeDefinition> getAllChallenges(String difficultyKey);

    int getChallengeCount(String difficultyKey);

    int getTotalChallenges();
}
