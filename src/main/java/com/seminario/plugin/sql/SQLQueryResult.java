package com.seminario.plugin.sql;

import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Enhanced result of SQL query validation that includes the raw ResultSet
 */
public class SQLQueryResult {
    private final boolean correct;
    private final String feedback;
    private final List<String> actualResults;
    private final List<String> expectedResults;
    private final String error;
    private final ResultSet resultSet;
    private final String originalQuery;
    private final boolean hasResults;
    
    public SQLQueryResult(boolean correct, String feedback, List<String> actualResults, 
                         List<String> expectedResults, String error, ResultSet resultSet, 
                         String originalQuery, boolean hasResults) {
        this.correct = correct;
        this.feedback = feedback;
        this.actualResults = actualResults != null ? actualResults : new ArrayList<>();
        this.expectedResults = expectedResults != null ? expectedResults : new ArrayList<>();
        this.error = error;
        this.resultSet = resultSet;
        this.originalQuery = originalQuery;
        this.hasResults = hasResults;
    }
    
    // Static factory methods for different result types
    public static SQLQueryResult correct(String feedback, List<String> actualResults, 
                                       List<String> expectedResults, ResultSet resultSet, 
                                       String originalQuery, boolean hasResults) {
        return new SQLQueryResult(true, feedback, actualResults, expectedResults, null, 
                                resultSet, originalQuery, hasResults);
    }
    
    public static SQLQueryResult incorrect(String feedback, List<String> actualResults, 
                                         List<String> expectedResults, ResultSet resultSet, 
                                         String originalQuery, boolean hasResults) {
        return new SQLQueryResult(false, feedback, actualResults, expectedResults, null, 
                                resultSet, originalQuery, hasResults);
    }
    
    public static SQLQueryResult error(String error, String originalQuery) {
        return new SQLQueryResult(false, "Error en la consulta SQL", new ArrayList<>(), 
                                new ArrayList<>(), error, null, originalQuery, false);
    }
    
    public static SQLQueryResult success(String query, ResultSet resultSet) {
        return new SQLQueryResult(true, "Consulta ejecutada exitosamente", new ArrayList<>(), 
                                new ArrayList<>(), null, resultSet, query, true);
    }
    
    // Getters
    public boolean isCorrect() { return correct; }
    public String getFeedback() { return feedback; }
    public List<String> getActualResults() { return actualResults; }
    public List<String> getExpectedResults() { return expectedResults; }
    public String getError() { return error; }
    public ResultSet getResultSet() { return resultSet; }
    public String getOriginalQuery() { return originalQuery; }
    public boolean hasResults() { return hasResults; }
    public boolean hasError() { return error != null; }
}