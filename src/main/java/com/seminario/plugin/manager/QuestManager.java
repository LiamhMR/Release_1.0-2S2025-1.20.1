package com.seminario.plugin.manager;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.model.QuestDefinition;
import com.seminario.plugin.model.QuestQuestion;
import com.seminario.plugin.model.QuestSession;

public class QuestManager {

    private static final Logger logger = Logger.getLogger(QuestManager.class.getName());
    // Layout: 2 rows (18 slots)
    // Row 0 (slots 0-8):  filler | filler | filler | filler | QUESTION | filler | filler | filler | filler
    // Row 1 (slots 9-17): EXIT   | filler | A      | B      | C        | D      | E      | filler | NEXT
    private static final int INVENTORY_SIZE = 18;
    private static final int QUESTION_SLOT = 4;
    private static final int EXIT_SLOT = 9;
    private static final int[] OPTION_SLOTS = {11, 12, 13, 14, 15};
    private static final int NEXT_SLOT = 17;
    private static final int CONFIRM_EXIT_SLOT = 15;
    private static final int CANCEL_EXIT_SLOT = 11;
    private static final List<String> OPTION_KEYS = Arrays.asList("A", "B", "C", "D", "E");
    private static final org.bukkit.Sound QUEST_MUSIC = org.bukkit.Sound.MUSIC_DISC_MELLOHI;
    private static final String QUEST_SELECTOR_TITLE = ChatColor.DARK_AQUA + "Selector de Quests";

    private final JavaPlugin plugin;
    private final File questDirectory;
    private final File questRequirementsFile;
    private final Map<UUID, QuestSession> activeSessions;
    private final Map<String, String> questRequirements;
    private final Map<UUID, Map<Integer, String>> questSelectorSlots;

    public QuestManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.questDirectory = new File(plugin.getDataFolder(), "quest");
        this.questRequirementsFile = new File(plugin.getDataFolder(), "quest_requirements.yml");
        this.activeSessions = new LinkedHashMap<>();
        this.questRequirements = new HashMap<>();
        this.questSelectorSlots = new HashMap<>();

        if (!questDirectory.exists()) {
            questDirectory.mkdirs();
        }

        loadQuestRequirements();
    }

    public void openQuestSelectorMenu(Player player) {
        if (isQuestActive(player)) {
            player.sendMessage(ChatColor.YELLOW + "Ya estás respondiendo un quest.");
            return;
        }

        List<String> questNames = getQuestNames();
        Collections.sort(questNames);

        int questCount = Math.min(questNames.size(), 54);
        int size = Math.max(9, ((Math.max(questCount, 1) - 1) / 9 + 1) * 9);
        Inventory inventory = Bukkit.createInventory(null, size, QUEST_SELECTOR_TITLE);

        Map<Integer, String> slotMap = new HashMap<>();
        if (questNames.isEmpty()) {
            inventory.setItem(4, createItem(Material.BARRIER, ChatColor.RED + "No hay quests disponibles",
                    List.of(ChatColor.GRAY + "Crea uno con /sm create quest <nombre>"), false));
        } else {
            int slot = 0;
            for (String questName : questNames) {
                if (slot >= size) {
                    break;
                }

                String requirement = getQuestRequirement(questName);
                boolean requirementMet = requirement == null || playerHasQuestAttempt(player.getUniqueId(), requirement);
                boolean alreadyCompleted = playerHasQuestAttempt(player.getUniqueId(), questName);

                Material material;
                String name;
                List<String> lore = new ArrayList<>();

                if (!requirementMet) {
                    material = Material.BARRIER;
                    name = ChatColor.RED + "🔒 " + questName;
                    lore.add(ChatColor.GRAY + "Requisito pendiente:");
                    lore.add(ChatColor.YELLOW + "- " + requirement);
                    lore.add(ChatColor.DARK_GRAY + "Completa ese quest primero");
                } else {
                    material = alreadyCompleted ? Material.ENCHANTED_BOOK : Material.BOOK;
                    name = (alreadyCompleted ? ChatColor.GREEN + "✔ " : ChatColor.AQUA + "▶ ") + questName;
                    lore.add(ChatColor.GRAY + "Click para responder este quest");
                    if (alreadyCompleted) {
                        lore.add(ChatColor.GREEN + "Ya tienes al menos 1 intento guardado");
                    }
                    if (requirement != null) {
                        lore.add(ChatColor.GRAY + "Requiere: " + ChatColor.WHITE + requirement);
                    }
                }

                inventory.setItem(slot, createItem(material, name, lore, alreadyCompleted && requirementMet));
                slotMap.put(slot, questName);
                slot++;
            }
        }

        questSelectorSlots.put(player.getUniqueId(), slotMap);
        player.openInventory(inventory);
    }

    public boolean isQuestSelectorView(InventoryView view) {
        return view != null && QUEST_SELECTOR_TITLE.equals(view.getTitle());
    }

    public void handleQuestSelectorClick(Player player, int rawSlot) {
        Map<Integer, String> slotMap = questSelectorSlots.get(player.getUniqueId());
        if (slotMap == null) {
            return;
        }

        String questName = slotMap.get(rawSlot);
        if (questName == null) {
            return;
        }

        if (!canStartQuest(player, questName)) {
            String requirement = getQuestRequirement(questName);
            player.sendMessage(ChatColor.RED + "No puedes responder '" + questName + "' todavía.");
            if (requirement != null) {
                player.sendMessage(ChatColor.GRAY + "Debes completar primero: " + ChatColor.YELLOW + requirement);
            }
            return;
        }

        questSelectorSlots.remove(player.getUniqueId());
        player.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> startQuest(player, questName));
    }

    public void handleQuestSelectorClose(Player player) {
        questSelectorSlots.remove(player.getUniqueId());
    }

    public boolean setQuestRequirement(String questName, String requiredQuestName) {
        String key = normalizeQuestFileName(questName);
        String value = normalizeQuestFileName(requiredQuestName);
        if (key.equals(value)) {
            return false;
        }

        questRequirements.put(key, value);
        saveQuestRequirements();
        return true;
    }

    public String getQuestRequirement(String questName) {
        String raw = questRequirements.get(normalizeQuestFileName(questName));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return raw;
    }

    public boolean canStartQuest(Player player, String questName) {
        String requirement = getQuestRequirement(questName);
        return requirement == null || playerHasQuestAttempt(player.getUniqueId(), requirement);
    }

    public boolean createQuest(String name) {
        File questFile = getQuestFile(name);
        if (questFile.exists()) {
            return false;
        }

        String content = "name: " + name + "\n"
                + "questions:\n"
                + "  - prompt: \"¿Es esta una pregunta place holder?\"\n"
                + "    options:\n"
                + "      A: \"Opción place holder A\"\n"
                + "      B: \"Opción place holder B\"\n"
                + "      C: \"Opción place holder C\"\n"
                + "      D: \"Opción place holder D\"\n"
                + "      E: \"Opción place holder E\"\n";

        try (java.io.FileWriter writer = new java.io.FileWriter(questFile, java.nio.charset.StandardCharsets.UTF_8)) {
            writer.write(content);
            return true;
        } catch (IOException e) {
            logger.warning("Could not save quest '" + name + "': " + e.getMessage());
            return false;
        }
    }

    public boolean questExists(String name) {
        return getQuestFile(name).exists();
    }

    public List<String> getQuestNames() {
        List<String> names = new ArrayList<>();
        File[] files = questDirectory.listFiles((dir, fileName) -> fileName.toLowerCase(Locale.ROOT).endsWith(".yml"));
        if (files == null) {
            return names;
        }

        for (File file : files) {
            String fileName = file.getName();
            names.add(fileName.substring(0, fileName.length() - 4));
        }
        return names;
    }

    public boolean startQuest(Player player, String questName) {
        if (!canStartQuest(player, questName)) {
            String requirement = getQuestRequirement(questName);
            player.sendMessage(ChatColor.RED + "No puedes iniciar '" + questName + "' todavía.");
            if (requirement != null) {
                player.sendMessage(ChatColor.GRAY + "Debes completar primero: " + ChatColor.YELLOW + requirement);
            }
            return false;
        }

        QuestDefinition definition = loadQuest(questName);
        if (definition == null) {
            player.sendMessage(ChatColor.RED + "No existe el quest '" + questName + "'.");
            return false;
        }
        if (definition.getQuestionCount() <= 0) {
            player.sendMessage(ChatColor.RED + "Ese quest no tiene preguntas válidas.");
            return false;
        }

        forceEndQuest(player, false, null);

        QuestSession session = new QuestSession(definition, player.getLocation());
        activeSessions.put(player.getUniqueId(), session);

        // Start relaxing background music
        player.playSound(player.getLocation(), QUEST_MUSIC, org.bukkit.SoundCategory.MUSIC, 1.0f, 1.0f);

        renderQuestion(player, session);
        startActionBarTask(player, session);
        return true;
    }

    public boolean isQuestActive(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public void handleQuestMovement(Player player, org.bukkit.event.player.PlayerMoveEvent event) {
        QuestSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }

        boolean moved = from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ();
        boolean rotated = from.getYaw() != to.getYaw() || from.getPitch() != to.getPitch();
        if (moved || rotated) {
            event.setTo(from);
        }
    }

    public void handleInventoryClose(Player player) {
        QuestSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }
        if (session.shouldIgnoreNextClose()) {
            session.setIgnoreNextClose(false);
            return;
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            QuestSession freshSession = activeSessions.get(player.getUniqueId());
            if (freshSession == null || !player.isOnline()) {
                return;
            }
            if (freshSession.isConfirmingExit()) {
                renderExitConfirmation(player, freshSession);
            } else {
                renderQuestion(player, freshSession);
            }
        });
    }

    public void handleQuestInventoryClick(Player player, int rawSlot, ClickType clickType) {
        QuestSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (session.isConfirmingExit()) {
            handleExitConfirmationClick(player, session, rawSlot);
            return;
        }

        if (rawSlot == EXIT_SLOT) {
            renderExitConfirmation(player, session);
            return;
        }

        if (rawSlot == NEXT_SLOT) {
            handleNextQuestion(player, session);
            return;
        }

        int optionIndex = getOptionIndexBySlot(rawSlot);
        if (optionIndex < 0) {
            return;
        }

        QuestQuestion question = session.getQuest().getQuestions().get(session.getQuestionIndex());
        String optionKey = OPTION_KEYS.get(optionIndex);
        if (!question.getOptions().containsKey(optionKey)) {
            return;
        }

        session.setSelectedOptionKey(optionKey);
        renderQuestion(player, session);
        sendActionBar(player, ChatColor.GREEN + "Opción " + optionKey + " seleccionada  |  Haz click en " + ChatColor.WHITE + "Siguiente" + ChatColor.GREEN + " para continuar");
    }

    public void forceEndQuest(Player player, boolean sendMessage, String message) {
        QuestSession session = activeSessions.remove(player.getUniqueId());
        questSelectorSlots.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        // Cancel action bar repeating task
        if (session.getActionBarTask() != null) {
            session.getActionBarTask().cancel();
        }

        // Stop music
        player.stopSound(QUEST_MUSIC, org.bukkit.SoundCategory.MUSIC);

        player.closeInventory();
        if (sendMessage && message != null && !message.isBlank()) {
            player.sendMessage(message);
        }
    }

    public void shutdown() {
        for (UUID playerId : new ArrayList<>(activeSessions.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                forceEndQuest(player, false, null);
            }
        }
        activeSessions.clear();
    }

    private void handleNextQuestion(Player player, QuestSession session) {
        if (session.getSelectedOptionKey() == null) {
            sendActionBar(player, ChatColor.RED + "Selecciona una opción antes de continuar");
            return;
        }

        session.getAnswers().put(session.getQuestionIndex(), session.getSelectedOptionKey());
        if (session.getQuestionIndex() + 1 >= session.getQuest().getQuestionCount()) {
            completeQuestAndSave(player, session);
            return;
        }

        session.setQuestionIndex(session.getQuestionIndex() + 1);
        session.setSelectedOptionKey(null);
        renderQuestion(player, session);
    }

    private void completeQuestAndSave(Player player, QuestSession session) {
        String questName = session.getQuest().getName();
        activeSessions.remove(player.getUniqueId());
        player.closeInventory();

        // Save quest result to file: quest/<quest_name>/<player_name>_try_<number>.yml
        File questResultDir = new File(questDirectory, normalizeQuestFileName(questName));
        if (!questResultDir.exists()) {
            questResultDir.mkdirs();
        }

        // Find next try number
        int tryNumber = 1;
        File resultFile;
        while ((resultFile = new File(questResultDir, player.getName() + "_try_" + tryNumber + ".yml")).exists()) {
            tryNumber++;
        }

        // Write result YAML
        YamlConfiguration resultYaml = new YamlConfiguration();
        resultYaml.set("player", player.getName());
        resultYaml.set("uuid", player.getUniqueId().toString());
        resultYaml.set("quest", questName);
        resultYaml.set("try", tryNumber);
        resultYaml.set("timestamp", System.currentTimeMillis());
        resultYaml.set("timestamp_human", java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));

        // Save answers
        for (Map.Entry<Integer, String> answer : session.getAnswers().entrySet()) {
            resultYaml.set("answers.question_" + answer.getKey(), answer.getValue());
        }

        try {
            resultYaml.save(resultFile);
            player.sendMessage(ChatColor.GREEN + "Quest completado: " + ChatColor.WHITE + questName + ChatColor.GREEN + ".");
            player.sendMessage(ChatColor.GRAY + "Intento guardado: " + tryNumber + " | Respuestas: " + session.getAnswers().size() + "/" + session.getQuest().getQuestionCount());
        } catch (IOException e) {
            logger.warning("Could not save quest result for " + player.getName() + ": " + e.getMessage());
            player.sendMessage(ChatColor.YELLOW + "Quest completado, pero no pudieron guardarse los resultados.");
        }
    }

    private void handleExitConfirmationClick(Player player, QuestSession session, int rawSlot) {
        if (rawSlot == CONFIRM_EXIT_SLOT) {
            activeSessions.remove(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(ChatColor.YELLOW + "Saliste del quest sin terminar.");
            return;
        }

        if (rawSlot == CANCEL_EXIT_SLOT) {
            session.setConfirmingExit(false);
            renderQuestion(player, session);
        }
    }

    private void renderQuestion(Player player, QuestSession session) {
        QuestQuestion question = session.getQuest().getQuestions().get(session.getQuestionIndex());
        int questionNumber = session.getQuestionIndex() + 1;
        int questionTotal = session.getQuest().getQuestionCount();
        String inventoryTitle = ChatColor.DARK_AQUA + "Pregunta " + questionNumber + "/" + questionTotal
                + ChatColor.GRAY + " - " + ChatColor.WHITE + session.getQuest().getName();
        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, inventoryTitle);

        fillEmptySlots(inventory);

        // Question display item in top-center slot
        List<String> questionLore = new ArrayList<>();
        questionLore.add(ChatColor.RESET + "");
        for (String line : wrapText(question.getPrompt(), 40)) {
            questionLore.add(ChatColor.WHITE + line);
        }
        questionLore.add(ChatColor.RESET + "");
        questionLore.add(ChatColor.GRAY + "Click en una opción para seleccionarla");
        inventory.setItem(QUESTION_SLOT, createItem(Material.WRITTEN_BOOK,
                ChatColor.GOLD + "Pregunta " + questionNumber + " de " + questionTotal, questionLore, false));

        inventory.setItem(EXIT_SLOT, createItem(Material.BARRIER, ChatColor.RED + "Salir",
                List.of(ChatColor.GRAY + "Abandonar este quest"), false));
        inventory.setItem(NEXT_SLOT, createItem(Material.ARROW, ChatColor.GREEN + "Siguiente",
                List.of(ChatColor.GRAY + "Confirmar selección y avanzar"), false));

        int optionCounter = 0;
        for (String optionKey : OPTION_KEYS) {
            String optionText = question.getOptions().get(optionKey);
            if (optionText == null) {
                optionCounter++;
                continue;
            }
            boolean selected = optionKey.equalsIgnoreCase(session.getSelectedOptionKey());
            Material material = selected ? Material.LIME_DYE : Material.CYAN_DYE;
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.RESET + "");
            for (String line : wrapText(optionText, 38)) {
                lore.add(ChatColor.WHITE + line);
            }
            lore.add(ChatColor.RESET + "");
            if (selected) {
                lore.add(ChatColor.GREEN + "✔ Seleccionada");
            } else {
                lore.add(ChatColor.GRAY + "Click para seleccionar");
            }
            inventory.setItem(OPTION_SLOTS[optionCounter],
                    createItem(material, (selected ? ChatColor.GREEN : ChatColor.YELLOW) + optionKey, lore, selected));
            optionCounter++;
        }

        session.setConfirmingExit(false);
        session.setIgnoreNextClose(true);
        player.openInventory(inventory);
    }

    public boolean playerHasQuestAttempt(java.util.UUID playerId, String questName) {
        File questResultDir = new File(questDirectory, normalizeQuestFileName(questName));
        if (!questResultDir.exists()) {
            return false;
        }

        File[] files = questResultDir.listFiles((dir, name) ->
            name.toLowerCase(Locale.ROOT).contains("_try_") && name.toLowerCase(Locale.ROOT).endsWith(".yml"));

        if (files == null) {
            return false;
        }

        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String storedUuid = yaml.getString("uuid", "");
            if (storedUuid.equalsIgnoreCase(playerId.toString())) {
                return true;
            }
        }

        return false;
    }

    private void loadQuestRequirements() {
        questRequirements.clear();
        if (!questRequirementsFile.exists()) {
            return;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(questRequirementsFile);
        org.bukkit.configuration.ConfigurationSection section = yaml.getConfigurationSection("requirements");
        if (section == null) {
            return;
        }

        for (String quest : section.getKeys(false)) {
            String required = section.getString(quest, "");
            if (!required.isBlank()) {
                questRequirements.put(normalizeQuestFileName(quest), normalizeQuestFileName(required));
            }
        }
    }

    private void saveQuestRequirements() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, String> entry : questRequirements.entrySet()) {
            yaml.set("requirements." + entry.getKey(), entry.getValue());
        }

        try {
            yaml.save(questRequirementsFile);
        } catch (IOException e) {
            logger.warning("Could not save quest requirements: " + e.getMessage());
        }
    }

    private void sendActionBar(Player player, String message) {
        player.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                new net.md_5.bungee.api.chat.TextComponent(message));
    }

    private void startActionBarTask(Player player, QuestSession session) {
        // Cancel any previous task
        if (session.getActionBarTask() != null) {
            session.getActionBarTask().cancel();
        }

        // Repeat every 40 ticks (2 s) to keep action bar visible
        org.bukkit.scheduler.BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            QuestSession current = activeSessions.get(player.getUniqueId());
            if (current == null || !player.isOnline()) {
                return;
            }
            if (current.isConfirmingExit()) {
                sendActionBar(player, ChatColor.RED + "¿Confirmar salida? Elige una opción");
                return;
            }
            QuestQuestion q = current.getQuest().getQuestions().get(current.getQuestionIndex());
            String prompt = q.getPrompt();
            // Truncate if too long for action bar
            String display = prompt.length() > 80 ? prompt.substring(0, 77) + "..." : prompt;
            String selected = current.getSelectedOptionKey();
            String suffix = selected != null
                    ? ChatColor.GREEN + "  [" + selected + " seleccionada]"
                    : ChatColor.GRAY + "  [sin selección]";
            sendActionBar(player, ChatColor.GOLD + display + suffix);
        }, 0L, 40L);

        session.setActionBarTask(task);
    }

    private List<String> wrapText(String text, int maxChars) {
        List<String> lines = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return lines;
        }
        String[] words = text.split(" ");
        StringBuilder current = new StringBuilder();
        for (String word : words) {
            if (current.length() + word.length() + (current.length() > 0 ? 1 : 0) > maxChars) {
                if (current.length() > 0) {
                    lines.add(current.toString());
                    current = new StringBuilder();
                }
            }
            if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) {
            lines.add(current.toString());
        }
        return lines;
    }

    private void renderExitConfirmation(Player player, QuestSession session) {
        Inventory inventory = Bukkit.createInventory(null, INVENTORY_SIZE, ChatColor.RED + "¿Salir del cuestionario?");
        fillEmptySlots(inventory);
        inventory.setItem(CANCEL_EXIT_SLOT, createItem(Material.GREEN_CONCRETE, ChatColor.GREEN + "Volver al cuestionario",
                List.of(ChatColor.GRAY + "Continuar respondiendo"), false));
        inventory.setItem(CONFIRM_EXIT_SLOT, createItem(Material.RED_CONCRETE, ChatColor.RED + "Salir sin terminar",
                List.of(ChatColor.GRAY + "El progreso actual se perderá"), false));

        session.setConfirmingExit(true);
        session.setIgnoreNextClose(true);
        player.openInventory(inventory);
    }

    private void fillEmptySlots(Inventory inventory) {
        ItemStack filler = createItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }

    private ItemStack createItem(Material material, String name, List<String> lore, boolean selected) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            if (selected) {
                meta.addEnchant(Enchantment.DURABILITY, 1, true);
                meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private int getOptionIndexBySlot(int rawSlot) {
        for (int index = 0; index < OPTION_SLOTS.length; index++) {
            if (OPTION_SLOTS[index] == rawSlot) {
                return index;
            }
        }
        return -1;
    }

    private QuestDefinition loadQuest(String questName) {
        File file = getQuestFile(questName);
        if (!file.exists()) {
            return null;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String name = yaml.getString("name", questName);
        List<QuestQuestion> questions = new ArrayList<>();

        // Support both YAML list format (- prompt: ...) and map-with-numeric-keys format ('0': prompt: ...)
        List<Map<?, ?>> questionList = yaml.getMapList("questions");
        if (!questionList.isEmpty()) {
            // List format
            for (Map<?, ?> rawQuestion : questionList) {
                Object promptValue = rawQuestion.get("prompt");
                String prompt = promptValue != null ? String.valueOf(promptValue) : "Pregunta sin texto";
                LinkedHashMap<String, String> options = new LinkedHashMap<>();
                Object rawOptions = rawQuestion.get("options");
                if (rawOptions instanceof Map<?, ?> optionMap) {
                    for (String optionKey : OPTION_KEYS) {
                        Object val = optionMap.get(optionKey);
                        if (val != null) {
                            options.put(optionKey, String.valueOf(val));
                        }
                    }
                }
                questions.add(new QuestQuestion(prompt, options));
            }
        } else {
            // Map-with-string-numeric-keys format: questions: '0': prompt: ...
            org.bukkit.configuration.ConfigurationSection questionsSection = yaml.getConfigurationSection("questions");
            if (questionsSection != null) {
                List<String> sortedKeys = new ArrayList<>(questionsSection.getKeys(false));
                sortedKeys.sort((a, b) -> {
                    try {
                        return Integer.compare(Integer.parseInt(a), Integer.parseInt(b));
                    } catch (NumberFormatException e) {
                        return a.compareTo(b);
                    }
                });

                for (String key : sortedKeys) {
                    org.bukkit.configuration.ConfigurationSection questionSection = questionsSection.getConfigurationSection(key);
                    if (questionSection == null) {
                        continue;
                    }
                    String prompt = questionSection.getString("prompt", "Pregunta sin texto");
                    LinkedHashMap<String, String> options = new LinkedHashMap<>();
                    org.bukkit.configuration.ConfigurationSection optionsSection = questionSection.getConfigurationSection("options");
                    if (optionsSection != null) {
                        for (String optionKey : OPTION_KEYS) {
                            String optionValue = optionsSection.getString(optionKey);
                            if (optionValue != null) {
                                options.put(optionKey, optionValue);
                            }
                        }
                    }
                    questions.add(new QuestQuestion(prompt, options));
                }
            }
        }

        return new QuestDefinition(name, questions);
    }

    private File getQuestFile(String questName) {
        return new File(questDirectory, normalizeQuestFileName(questName) + ".yml");
    }

    private String normalizeQuestFileName(String questName) {
        return questName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }
}