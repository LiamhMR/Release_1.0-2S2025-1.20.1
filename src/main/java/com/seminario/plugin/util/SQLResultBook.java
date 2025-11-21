package com.seminario.plugin.util;

import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BookMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;

/**
 * Utility class to generate books with SQL query results
 */
public class SQLResultBook {
    
    private static final String BOOK_TITLE = "Resultados SQL";
    private static final String BOOK_AUTHOR = "SQL Dungeon";
    private static final int MAX_LINES_PER_PAGE = 12;
    private static final int MAX_CHARS_PER_LINE = 30;
    
    /**
     * Create a book with SQL query results
     * @param player The player who executed the query
     * @param query The SQL query that was executed
     * @param resultSet The result set from the query
     * @param isCorrect Whether the query was correct
     * @return ItemStack book with results
     */
    public static ItemStack createResultBook(Player player, String query, ResultSet resultSet, boolean isCorrect) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();
        
        if (bookMeta == null) {
            return book;
        }
        
        // Set book metadata
        bookMeta.setTitle(BOOK_TITLE);
        bookMeta.setAuthor(BOOK_AUTHOR);
        
        try {
            List<Component> pages = generatePages(query, resultSet, isCorrect);
            bookMeta.pages(pages);
        } catch (SQLException e) {
            // If there's an error reading results, create error page
            List<Component> errorPages = createErrorPage(query, e.getMessage());
            bookMeta.pages(errorPages);
        }
        
        book.setItemMeta(bookMeta);
        return book;
    }
    
    /**
     * Create a book for queries that produced no results
     * @param player The player who executed the query 
     * @param query The SQL query that was executed
     * @param isCorrect Whether the query was correct
     * @return ItemStack book with no results message
     */
    public static ItemStack createNoResultsBook(Player player, String query, boolean isCorrect) {
        ItemStack book = new ItemStack(Material.WRITTEN_BOOK);
        BookMeta bookMeta = (BookMeta) book.getItemMeta();
        
        if (bookMeta == null) {
            return book;
        }
        
        bookMeta.setTitle(BOOK_TITLE);
        bookMeta.setAuthor(BOOK_AUTHOR);
        
        List<Component> pages = createNoResultsPage(query, isCorrect);
        bookMeta.pages(pages);
        
        book.setItemMeta(bookMeta);
        return book;
    }
    
    /**
     * Generate pages with query results
     */
    private static List<Component> generatePages(String query, ResultSet resultSet, boolean isCorrect) throws SQLException {
        List<Component> pages = new ArrayList<>();
        
        // First page: Query info and status
        Component firstPage = createQueryInfoPage(query, isCorrect);
        pages.add(firstPage);
        
        // Check if there are results
        if (!resultSet.next()) {
            // No results - create separate page for this info
            Component noResultsPage = Component.text("📋 RESULTADOS", NamedTextColor.BLUE, TextDecoration.BOLD)
                .append(Component.text("\n\n"))
                .append(Component.text("Sin resultados", NamedTextColor.DARK_GRAY))
                .append(Component.text("\n\n"))
                .append(Component.text("La consulta se ejecutó", NamedTextColor.BLACK))
                .append(Component.text("\ncorrectamente pero no", NamedTextColor.BLACK))
                .append(Component.text("\ndevolvió ningún dato.", NamedTextColor.BLACK));
            pages.add(noResultsPage);
            return pages;
        }
        
        // Get column information
        ResultSetMetaData metaData = resultSet.getMetaData();
        int columnCount = metaData.getColumnCount();
        
        List<String> columnNames = new ArrayList<>();
        for (int i = 1; i <= columnCount; i++) {
            columnNames.add(metaData.getColumnName(i));
        }
        
        // Collect all rows
        List<List<String>> rows = new ArrayList<>();
        
        // Reset to beginning and collect all rows
        resultSet.beforeFirst();
        while (resultSet.next()) {
            List<String> row = new ArrayList<>();
            for (int i = 1; i <= columnCount; i++) {
                String value = resultSet.getString(i);
                row.add(value != null ? value : "NULL");
            }
            rows.add(row);
        }
        
        // Generate result pages
        pages.addAll(createResultPages(columnNames, rows));
        
        return pages;
    }
    
    /**
     * Create the first page with query information
     */
    private static Component createQueryInfoPage(String query, boolean isCorrect) {
        Component page = Component.text("🏰 SQL DUNGEON", NamedTextColor.GOLD, TextDecoration.BOLD)
            .append(Component.text("\n"))
            .append(Component.text("════════════════", NamedTextColor.DARK_GRAY))
            .append(Component.text("\n\n"));
        
        // Status indicator
        if (isCorrect) {
            page = page.append(Component.text("✅ CORRECTA", NamedTextColor.GREEN, TextDecoration.BOLD));
        } else {
            page = page.append(Component.text("❌ INCORRECTA", NamedTextColor.RED, TextDecoration.BOLD));
        }
        
        page = page.append(Component.text("\n\n"))
            .append(Component.text("📝 CONSULTA:", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text("\n"))
            .append(Component.text(formatQuery(query), NamedTextColor.BLACK));
        
        return page;
    }
    
    /**
     * Create pages with the actual results
     */
    private static List<Component> createResultPages(List<String> columnNames, List<List<String>> rows) {
        List<Component> pages = new ArrayList<>();
        
        // Results header page
        Component headerPage = Component.text("📋 RESULTADOS", NamedTextColor.BLUE, TextDecoration.BOLD)
            .append(Component.text("\n\n"))
            .append(Component.text("Filas: " + rows.size(), NamedTextColor.DARK_GRAY))
            .append(Component.text("\n"))
            .append(Component.text("Columnas: " + columnNames.size(), NamedTextColor.DARK_GRAY))
            .append(Component.text("\n\n"))
            .append(Component.text("📊 COLUMNAS:", NamedTextColor.AQUA, TextDecoration.BOLD));
        
        // Add column names
        for (int i = 0; i < columnNames.size() && i < 4; i++) {
            headerPage = headerPage.append(Component.text("\n" + (i + 1) + ". " + columnNames.get(i), NamedTextColor.BLACK));
        }
        
        if (columnNames.size() > 4) {
            headerPage = headerPage.append(Component.text("\n... y " + (columnNames.size() - 4) + " más", NamedTextColor.DARK_GRAY));
        }
        
        pages.add(headerPage);
        
        // Create data pages with better pagination
        createDataPages(pages, columnNames, rows);
        
        return pages;
    }
    
    /**
     * Create data pages with proper pagination
     */
    private static void createDataPages(List<Component> pages, List<String> columnNames, List<List<String>> rows) {
        int linesPerPage = MAX_LINES_PER_PAGE;
        int currentLine = 0;
        Component currentPage = Component.text("📊 DATOS", NamedTextColor.GREEN, TextDecoration.BOLD)
            .append(Component.text("\n"));
        currentLine = 2; // Title + separator
        
        for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            
            // Check if we need a new page before adding this row
            int linesNeeded = 1 + row.size(); // Row header + data lines
            if (currentLine + linesNeeded > linesPerPage) {
                pages.add(currentPage);
                currentPage = Component.text("📊 DATOS (pág " + (pages.size()) + ")", NamedTextColor.GREEN, TextDecoration.BOLD)
                    .append(Component.text("\n"));
                currentLine = 2;
            }
            
            // Add row header
            currentPage = currentPage.append(Component.text("\nFila " + (rowIndex + 1) + ":", NamedTextColor.YELLOW, TextDecoration.BOLD));
            currentLine++;
            
            // Add row data
            for (int colIndex = 0; colIndex < row.size(); colIndex++) {
                String columnName = columnNames.get(colIndex);
                String value = row.get(colIndex);
                
                // Truncate long values
                if (value.length() > MAX_CHARS_PER_LINE - columnName.length() - 3) {
                    value = value.substring(0, MAX_CHARS_PER_LINE - columnName.length() - 6) + "...";
                }
                
                String dataLine = columnName + ": " + value;
                currentPage = currentPage.append(Component.text("\n" + dataLine, NamedTextColor.BLACK));
                currentLine++;
            }
            
            // Add separator between rows (if not last row and space allows)
            if (rowIndex < rows.size() - 1 && currentLine < linesPerPage - 1) {
                currentPage = currentPage.append(Component.text("\n" + "─────────────", NamedTextColor.DARK_GRAY));
                currentLine++;
            }
        }
        
        // Add the last page if it has meaningful content
        if (currentLine > 2) {
            pages.add(currentPage);
        }
    }
    
    /**
     * Create page for no results
     */
    private static List<Component> createNoResultsPage(String query, boolean isCorrect) {
        List<Component> pages = new ArrayList<>();
        
        // First page with query info
        Component firstPage = createQueryInfoPage(query, isCorrect);
        pages.add(firstPage);
        
        // Second page with no results explanation
        Component secondPage = Component.text("📋 RESULTADOS", NamedTextColor.BLUE, TextDecoration.BOLD)
            .append(Component.text("\n\n"))
            .append(Component.text("Sin resultados", NamedTextColor.DARK_GRAY))
            .append(Component.text("\n\n"))
            .append(Component.text("💡 Posibles causas:", NamedTextColor.YELLOW))
            .append(Component.text("\n• Filtros muy restrictivos", NamedTextColor.BLACK))
            .append(Component.text("\n• No hay datos coincidentes", NamedTextColor.BLACK))
            .append(Component.text("\n• Consulta válida pero vacía", NamedTextColor.BLACK))
            .append(Component.text("\n\n"))
            .append(Component.text("Esto es normal en SQL!", NamedTextColor.GREEN));
        
        pages.add(secondPage);
        return pages;
    }
    
    /**
     * Create error page
     */
    private static List<Component> createErrorPage(String query, String errorMessage) {
        List<Component> pages = new ArrayList<>();
        
        // First page with query info
        Component firstPage = Component.text("🏰 SQL DUNGEON", NamedTextColor.GOLD, TextDecoration.BOLD)
            .append(Component.text("\n"))
            .append(Component.text("════════════════", NamedTextColor.DARK_GRAY))
            .append(Component.text("\n\n"))
            .append(Component.text("❌ ERROR", NamedTextColor.RED, TextDecoration.BOLD))
            .append(Component.text("\n\n"))
            .append(Component.text("📝 CONSULTA:", NamedTextColor.YELLOW, TextDecoration.BOLD))
            .append(Component.text("\n"))
            .append(Component.text(formatQuery(query), NamedTextColor.BLACK));
        
        pages.add(firstPage);
        
        // Second page with error details
        Component secondPage = Component.text("⚠️ DETALLES DEL ERROR", NamedTextColor.RED, TextDecoration.BOLD)
            .append(Component.text("\n\n"))
            .append(Component.text(formatErrorMessage(errorMessage), NamedTextColor.BLACK))
            .append(Component.text("\n\n"))
            .append(Component.text("💡 CONSEJOS:", NamedTextColor.YELLOW))
            .append(Component.text("\n• Revisa la sintaxis", NamedTextColor.BLACK))
            .append(Component.text("\n• Verifica nombres de tablas", NamedTextColor.BLACK))
            .append(Component.text("\n• Usa comillas correctas", NamedTextColor.BLACK));
        
        pages.add(secondPage);
        return pages;
    }
    
    /**
     * Format error message for better readability
     */
    private static String formatErrorMessage(String errorMessage) {
        if (errorMessage.length() > MAX_CHARS_PER_LINE * 3) {
            return errorMessage.substring(0, MAX_CHARS_PER_LINE * 3 - 3) + "...";
        }
        return errorMessage;
    }
    
    /**
     * Format query for display (break long lines)
     */
    private static String formatQuery(String query) {
        if (query.length() <= MAX_CHARS_PER_LINE) {
            return query;
        }
        
        StringBuilder formatted = new StringBuilder();
        String[] words = query.split(" ");
        int currentLineLength = 0;
        
        for (String word : words) {
            if (currentLineLength + word.length() + 1 > MAX_CHARS_PER_LINE) {
                formatted.append("\n");
                currentLineLength = 0;
            }
            
            if (currentLineLength > 0) {
                formatted.append(" ");
                currentLineLength++;
            }
            
            formatted.append(word);
            currentLineLength += word.length();
        }
        
        return formatted.toString();
    }
    
    /**
     * Remove existing SQL result books from player inventory
     * @param player The player
     */
    public static void removeExistingResultBooks(Player player) {
        for (int i = 0; i < player.getInventory().getSize(); i++) {
            ItemStack item = player.getInventory().getItem(i);
            if (item != null && item.getType() == Material.WRITTEN_BOOK) {
                BookMeta meta = (BookMeta) item.getItemMeta();
                if (meta != null && BOOK_TITLE.equals(meta.getTitle()) && BOOK_AUTHOR.equals(meta.getAuthor())) {
                    player.getInventory().setItem(i, null);
                }
            }
        }
    }
}