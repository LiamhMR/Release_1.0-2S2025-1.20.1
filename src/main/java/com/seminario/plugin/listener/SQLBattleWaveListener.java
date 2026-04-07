package com.seminario.plugin.listener;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import com.seminario.plugin.manager.SQLBattleManager;

public class SQLBattleWaveListener implements Listener {

    private final SQLBattleManager sqlBattleManager;

    public SQLBattleWaveListener(SQLBattleManager sqlBattleManager) {
        this.sqlBattleManager = sqlBattleManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDeath(EntityDeathEvent event) {
        sqlBattleManager.handleBattleEntityDeath(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity)) {
            return;
        }
        sqlBattleManager.handleBattleEntityDamage((LivingEntity) event.getEntity(), event.getFinalDamage());
    }
}
