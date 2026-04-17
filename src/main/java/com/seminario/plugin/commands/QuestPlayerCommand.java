package com.seminario.plugin.commands;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import com.seminario.plugin.manager.QuestManager;

public class QuestPlayerCommand implements CommandExecutor, TabCompleter {

    private final QuestManager questManager;

    public QuestPlayerCommand(QuestManager questManager) {
        this.questManager = questManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Este comando solo puede ser usado por jugadores.");
            return true;
        }

        if (args.length < 3 || !args[0].equalsIgnoreCase("start") || !args[1].equalsIgnoreCase("quest")) {
            player.sendMessage(ChatColor.RED + "Uso: /sqm start quest <nombre_del_quest>");
            return true;
        }

        if (!questManager.startQuest(player, args[2])) {
            player.sendMessage(ChatColor.RED + "No se pudo iniciar el quest.");
            return true;
        }

        player.sendMessage(ChatColor.GREEN + "Quest iniciado: " + args[2]);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("start").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("start")) {
            return Arrays.asList("quest").stream()
                .filter(value -> value.startsWith(args[1].toLowerCase()))
                .collect(Collectors.toList());
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("start") && args[1].equalsIgnoreCase("quest")) {
            return questManager.getQuestNames().stream()
                .filter(name -> name.toLowerCase().startsWith(args[2].toLowerCase()))
                .collect(Collectors.toList());
        }

        return Collections.emptyList();
    }
}