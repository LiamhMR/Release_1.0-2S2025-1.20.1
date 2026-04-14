package com.seminario.plugin.listener;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.plugin.java.JavaPlugin;

import com.seminario.plugin.manager.SQLBattleManager;

public class SQLBattleWaveListener implements Listener {

    private static final double MIN_VALID_Y = 70.0D;

    private final SQLBattleManager sqlBattleManager;
    private final JavaPlugin plugin;

    public SQLBattleWaveListener(SQLBattleManager sqlBattleManager, JavaPlugin plugin) {
        this.sqlBattleManager = sqlBattleManager;
        this.plugin = plugin;

        // Hard kill guard: anything below Y<70 in SQL Battle worlds dies.
        Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            enforceMinimumYRule();
            enforceSpectatorModeRule();
            sqlBattleManager.tickCastleSystems(0.5D);
        }, 20L, 10L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        World world = event.getLocation().getWorld();
        if (world == null || !sqlBattleManager.isSQLBattle(world.getName())) {
            return;
        }

        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.CUSTOM) {
            return;
        }

        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        if (sqlBattleManager.isBattleEnemyEntity(event.getEntity())) {
            event.getDrops().clear();
            event.setDroppedExp(0);
        }
        sqlBattleManager.handleBattleEntityDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        sqlBattleManager.handleBattleEntityDamage((LivingEntity) event.getEntity(), event.getFinalDamage());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        Player attacker = null;
        if (event.getDamager() instanceof Player playerDamager) {
            attacker = playerDamager;
        } else if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player projectileShooter) {
            attacker = projectileShooter;
        }

        if (attacker == null) {
            return;
        }

        if (!sqlBattleManager.isPlayerInBattleArena(attacker) || !sqlBattleManager.isPlayerInBattleArena(victim)) {
            return;
        }

        if (sqlBattleManager.arePlayersInSameBattleArena(attacker, victim)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (sqlBattleManager.isPlayerInBattleSession(event.getEntity())) {
            event.setKeepInventory(true);
            event.getDrops().clear();
            event.setKeepLevel(true);
            event.setDroppedExp(0);
        }
        sqlBattleManager.handleBattlePlayerDeath(event.getEntity());
    }

    private void enforceMinimumYRule() {
        for (String worldName : sqlBattleManager.getAllSQLBattles().keySet()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }

            for (Player player : world.getPlayers()) {
                if (player.getLocation().getY() < MIN_VALID_Y && !player.isDead()) {
                    player.setHealth(0.0D);
                }
            }

            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity instanceof Player) {
                    continue;
                }
                if (!sqlBattleManager.isBattleManagedEntity(entity)) {
                    continue;
                }
                if (entity.getLocation().getY() < MIN_VALID_Y && !entity.isDead()) {
                    entity.setHealth(0.0D);
                }
            }
        }
    }

    private void enforceSpectatorModeRule() {
        for (String worldName : sqlBattleManager.getAllSQLBattles().keySet()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }

            for (Player player : world.getPlayers()) {
                if (!sqlBattleManager.isPlayerBattleSpectator(player)) {
                    continue;
                }
                if (player.getGameMode() != GameMode.SPECTATOR) {
                    player.setGameMode(GameMode.SPECTATOR);
                }
            }
        }
    }
}
