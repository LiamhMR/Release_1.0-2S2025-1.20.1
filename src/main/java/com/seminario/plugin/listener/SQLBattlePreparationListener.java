package com.seminario.plugin.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.manager.SQLBattleManager;

public class SQLBattlePreparationListener implements Listener {

    private final SQLBattleManager sqlBattleManager;
    private final JavaPlugin plugin;

    public SQLBattlePreparationListener(SQLBattleManager sqlBattleManager, JavaPlugin plugin) {
        this.sqlBattleManager = sqlBattleManager;
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!sqlBattleManager.shouldCapturePreparationChat(player)) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage();
        plugin.getServer().getScheduler().runTask(plugin, () -> sqlBattleManager.handlePreparationChat(player, message));
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        sqlBattleManager.cleanupPlayerSession(event.getPlayer());
    }
}