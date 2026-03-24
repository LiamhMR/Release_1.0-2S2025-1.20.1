package com.seminario.plugin.sql.context.challenge;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

/**
 * In-memory challenge repository grouped by difficulty key.
 */
public class InMemorySQLChallengeRepository implements SQLChallengeRepository {

    private final Map<String, List<SQLChallengeDefinition>> challengesByDifficulty;
    private final Random random;

    public InMemorySQLChallengeRepository(Map<String, List<SQLChallengeDefinition>> challengesByDifficulty) {
        this(challengesByDifficulty, new Random());
    }

    public InMemorySQLChallengeRepository(Map<String, List<SQLChallengeDefinition>> challengesByDifficulty, Random random) {
        this.challengesByDifficulty = new HashMap<>();
        if (challengesByDifficulty != null) {
            for (Map.Entry<String, List<SQLChallengeDefinition>> entry : challengesByDifficulty.entrySet()) {
                this.challengesByDifficulty.put(
                    entry.getKey(),
                    new ArrayList<>(entry.getValue())
                );
            }
        }
        this.random = random;
    }

    @Override
    public Optional<SQLChallengeDefinition> getRandomChallenge(String difficultyKey) {
        List<SQLChallengeDefinition> challenges = challengesByDifficulty.get(difficultyKey);
        if (challenges == null || challenges.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(challenges.get(random.nextInt(challenges.size())));
    }

    @Override
    public List<SQLChallengeDefinition> getAllChallenges(String difficultyKey) {
        List<SQLChallengeDefinition> challenges = challengesByDifficulty.get(difficultyKey);
        if (challenges == null) {
            return Collections.emptyList();
        }
        return Collections.unmodifiableList(challenges);
    }

    @Override
    public int getChallengeCount(String difficultyKey) {
        return getAllChallenges(difficultyKey).size();
    }

    @Override
    public int getTotalChallenges() {
        return challengesByDifficulty.values().stream().mapToInt(List::size).sum();
    }
}
