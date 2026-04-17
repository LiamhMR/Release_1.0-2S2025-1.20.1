package com.seminario.plugin.listener;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import com.seminario.plugin.manager.SQLBattleManager;

public class SQLBattleWaveListener implements Listener {

    private static final double MIN_VALID_Y = 70.0D;
    private static final double ICE_SPELL_DIRECT_DAMAGE = 8.0D;
    private static final double ICE_SPELL_AOE_DAMAGE = 5.0D;
    private static final double ICE_SPELL_RADIUS = 4.0D;
    private static final int ICE_SPELL_SLOW_DURATION_TICKS = 100;
    private static final int ICE_SPELL_SLOW_AMPLIFIER = 3;
    private static final double FIRE_SPELL_DIRECT_DAMAGE = 10.0D;
    private static final double FIRE_SPELL_AOE_DAMAGE = 6.0D;
    private static final double FIRE_SPELL_RADIUS = 3.25D;
    private static final float FIRE_SPELL_EXPLOSION_POWER = 2.2F;
    private static final int FIRE_SPELL_BURN_TICKS = 100;
    private static final double ARROW_RAIN_RADIUS = 16.0D;
    private static final int ARROW_RAIN_MAX_TARGETS = 6;
    private static final int ARROW_RAIN_ARROWS_PER_TARGET = 3;
    private static final int TNT_SPELL_FUSE_TICKS = 100;

    private final SQLBattleManager sqlBattleManager;
    private final JavaPlugin plugin;
    private final Map<UUID, SpellType> spellProjectiles;

    private enum SpellType {
        ICE,
        FIRE
    }

    public SQLBattleWaveListener(SQLBattleManager sqlBattleManager, JavaPlugin plugin) {
        this.sqlBattleManager = sqlBattleManager;
        this.plugin = plugin;
        this.spellProjectiles = new HashMap<>();

        // Hard kill guard: anything below Y<70 in SQL Battle worlds dies.
        Bukkit.getScheduler().runTaskTimer(this.plugin, () -> {
            enforceMinimumYRule();
            enforceParticipantModeRule();
            enforceSpectatorModeRule();
            sqlBattleManager.reconcileActiveWaveProgress();
            sqlBattleManager.tickCastleSystems(0.5D);
        }, 20L, 10L);

        Bukkit.getScheduler().runTaskTimer(this.plugin, this::emitSpellTrails, 2L, 2L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (!sqlBattleManager.isPlayerInBattleArena(player)) {
            return;
        }
        if (sqlBattleManager.isPlayerBattleSpectator(player)) {
            return;
        }

        sqlBattleManager.enforceBattleParticipantState(player);

        EquipmentSlot hand = event.getHand();
        if (hand == null) {
            return;
        }

        ItemStack usedItem = getItemInHand(player, hand);
        if (usedItem == null || usedItem.getType() == Material.AIR) {
            return;
        }

        Material material = usedItem.getType();

        if (material == Material.SNOWBALL) {
            event.setCancelled(true);
            if (consumeOneItem(player, hand)) {
                castIceSpell(player);
            }
            return;
        }

        if (material == Material.FIRE_CHARGE) {
            event.setCancelled(true);
            if (consumeOneItem(player, hand)) {
                castFireSpell(player);
            }
            return;
        }

        if (material == Material.SPECTRAL_ARROW) {
            event.setCancelled(true);
            if (consumeOneItem(player, hand)) {
                castArrowRainSpell(player);
            }
            return;
        }

        if (material == Material.TNT) {
            event.setCancelled(true);
            if (consumeOneItem(player, hand)) {
                castTimedTntSpell(player);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onProjectileHit(ProjectileHitEvent event) {
        Projectile projectile = event.getEntity();
        SpellType spellType = spellProjectiles.remove(projectile.getUniqueId());
        if (spellType == null) {
            return;
        }

        if (!(projectile.getShooter() instanceof Player shooter)) {
            return;
        }

        if (spellType == SpellType.ICE) {
            applyIceImpact(projectile, shooter, event.getHitEntity());
            return;
        }

        if (spellType == SpellType.FIRE) {
            applyFireImpact(projectile, shooter, event.getHitEntity());
        }
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
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!(event.getEntity() instanceof LivingEntity livingEntity)) {
            return;
        }
        if (!sqlBattleManager.isBattleEnemyEntity(livingEntity)) {
            return;
        }

        sqlBattleManager.handleBattleEntityRemoved(livingEntity);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player && sqlBattleManager.isPlayerBattleSpectator(player)) {
            event.setCancelled(true);
            return;
        }
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

        if (sqlBattleManager.isPlayerBattleSpectator(attacker) || sqlBattleManager.isPlayerBattleSpectator(victim)) {
            event.setCancelled(true);
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
        Player player = event.getEntity();
        sqlBattleManager.handleBattlePlayerDeath(player);
        if (sqlBattleManager.shouldAutoRespawnBattlePlayer(player)) {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    player.spigot().respawn();
                }
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        if (!sqlBattleManager.shouldAutoRespawnBattlePlayer(player)) {
            return;
        }

        Location respawnLocation = sqlBattleManager.getBattleRespawnLocation(player);
        if (respawnLocation != null) {
            event.setRespawnLocation(respawnLocation);
        }

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location target = sqlBattleManager.getBattleRespawnLocation(player);
            if (target != null) {
                player.teleport(target);
                player.setFallDistance(0.0F);
                player.setFireTicks(0);
            }
        });
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
                sqlBattleManager.enforceSimulatedSpectatorState(player);
            }
        }
    }

    private void enforceParticipantModeRule() {
        for (String worldName : sqlBattleManager.getAllSQLBattles().keySet()) {
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                continue;
            }

            for (Player player : world.getPlayers()) {
                sqlBattleManager.enforceBattleParticipantState(player);
            }
        }
    }

    private void castIceSpell(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        Snowball snowball = player.getWorld().spawn(player.getEyeLocation().add(direction.multiply(0.4D)), Snowball.class, spawned -> {
            spawned.setShooter(player);
            spawned.setVelocity(direction.multiply(1.8D));
            spawned.setGravity(true);
        });
        spellProjectiles.put(snowball.getUniqueId(), SpellType.ICE);
        player.getWorld().spawnParticle(Particle.SNOWFLAKE, player.getEyeLocation(), 12, 0.15D, 0.15D, 0.15D, 0.01D);
        player.playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 0.7f, 1.3f);
    }

    private void castFireSpell(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        Fireball fireball = player.getWorld().spawn(player.getEyeLocation().add(direction.multiply(1.0D)), Fireball.class, spawned -> {
            spawned.setShooter(player);
            spawned.setDirection(direction);
            spawned.setVelocity(direction.multiply(1.1D));
            spawned.setIsIncendiary(false);
            spawned.setYield(0.0F);
        });
        spellProjectiles.put(fireball.getUniqueId(), SpellType.FIRE);
        player.getWorld().spawnParticle(Particle.FLAME, player.getEyeLocation(), 10, 0.15D, 0.15D, 0.15D, 0.01D);
        player.playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 0.9f, 1.0f);
    }

    private void castArrowRainSpell(Player player) {
        List<LivingEntity> targets = new ArrayList<>();
        for (LivingEntity entity : player.getWorld().getLivingEntities()) {
            if (!sqlBattleManager.isBattleEnemyEntity(entity)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(player.getLocation()) > (ARROW_RAIN_RADIUS * ARROW_RAIN_RADIUS)) {
                continue;
            }
            targets.add(entity);
        }

        targets.sort(Comparator.comparingDouble(e -> e.getLocation().distanceSquared(player.getLocation())));
        int maxTargets = Math.min(ARROW_RAIN_MAX_TARGETS, targets.size());
        for (int i = 0; i < maxTargets; i++) {
            LivingEntity target = targets.get(i);
            for (int arrowIndex = 0; arrowIndex < ARROW_RAIN_ARROWS_PER_TARGET; arrowIndex++) {
                Arrow arrow = player.getWorld().spawn(target.getLocation().clone().add(0.0D, 9.0D + (arrowIndex * 0.45D), 0.0D), Arrow.class);
                arrow.setShooter(player);
                arrow.setDamage(3.0D);
                arrow.setCritical(true);
                arrow.setPickupStatus(Arrow.PickupStatus.DISALLOWED);
                arrow.setVelocity(new Vector(0.0D, -1.35D, 0.0D));
            }
        }

        player.getWorld().spawnParticle(Particle.CRIT, player.getLocation(), 30, 0.35D, 0.6D, 0.35D, 0.02D);
        player.playSound(player.getLocation(), Sound.ENTITY_ARROW_SHOOT, 0.9f, 0.8f);
    }

    private void castTimedTntSpell(Player player) {
        Vector direction = player.getLocation().getDirection().normalize();
        Entity entity = player.getWorld().spawnEntity(player.getEyeLocation().add(direction.multiply(1.1D)), org.bukkit.entity.EntityType.PRIMED_TNT);
        if (entity instanceof TNTPrimed tnt) {
            tnt.setFuseTicks(TNT_SPELL_FUSE_TICKS);
            tnt.setYield(3.0F);
            tnt.setVelocity(direction.multiply(0.42D).setY(0.33D));
        }
        player.playSound(player.getLocation(), Sound.ENTITY_TNT_PRIMED, 1.0f, 1.0f);
    }

    private ItemStack getItemInHand(Player player, EquipmentSlot hand) {
        if (hand == EquipmentSlot.OFF_HAND) {
            return player.getInventory().getItemInOffHand();
        }
        return player.getInventory().getItemInMainHand();
    }

    private boolean consumeOneItem(Player player, EquipmentSlot hand) {
        ItemStack stack = getItemInHand(player, hand);
        if (stack == null || stack.getType() == Material.AIR) {
            return false;
        }

        int amount = stack.getAmount();
        if (amount <= 1) {
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(null);
            } else {
                player.getInventory().setItemInMainHand(null);
            }
        } else {
            stack.setAmount(amount - 1);
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(stack);
            } else {
                player.getInventory().setItemInMainHand(stack);
            }
        }
        return true;
    }

    private void applyIceImpact(Projectile projectile, Player shooter, org.bukkit.entity.Entity directHit) {
        projectile.getWorld().spawnParticle(Particle.SNOWFLAKE, projectile.getLocation(), 60, 0.7D, 0.45D, 0.7D, 0.03D);
        projectile.getWorld().spawnParticle(Particle.BLOCK_CRACK, projectile.getLocation(), 26, 0.65D, 0.35D, 0.65D, Material.PACKED_ICE.createBlockData());
        projectile.getWorld().playSound(projectile.getLocation(), Sound.BLOCK_POWDER_SNOW_BREAK, 0.8f, 1.2f);

        if (directHit instanceof LivingEntity directTarget && sqlBattleManager.isBattleEnemyEntity(directTarget)) {
            directTarget.damage(ICE_SPELL_DIRECT_DAMAGE, shooter);
            directTarget.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ICE_SPELL_SLOW_DURATION_TICKS, ICE_SPELL_SLOW_AMPLIFIER));
        }

        for (LivingEntity entity : projectile.getWorld().getLivingEntities()) {
            if (entity.equals(shooter) || !sqlBattleManager.isBattleEnemyEntity(entity)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(projectile.getLocation()) > (ICE_SPELL_RADIUS * ICE_SPELL_RADIUS)) {
                continue;
            }
            entity.damage(ICE_SPELL_AOE_DAMAGE, shooter);
            entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOW, ICE_SPELL_SLOW_DURATION_TICKS, 2));
            entity.getWorld().spawnParticle(Particle.SNOWFLAKE, entity.getLocation().add(0.0D, 1.0D, 0.0D), 12, 0.25D, 0.45D, 0.25D, 0.01D);
        }
    }

    private void applyFireImpact(Projectile projectile, Player shooter, org.bukkit.entity.Entity directHit) {
        projectile.getWorld().spawnParticle(Particle.FLAME, projectile.getLocation(), 45, 0.6D, 0.35D, 0.6D, 0.03D);
        projectile.getWorld().spawnParticle(Particle.SMOKE_LARGE, projectile.getLocation(), 18, 0.5D, 0.25D, 0.5D, 0.02D);
        projectile.getWorld().spawnParticle(Particle.EXPLOSION_LARGE, projectile.getLocation(), 3, 0.25D, 0.15D, 0.25D, 0.0D);
        projectile.getWorld().playSound(projectile.getLocation(), Sound.ENTITY_BLAZE_HURT, 0.85f, 1.0f);
        projectile.getWorld().createExplosion(projectile.getLocation().getX(), projectile.getLocation().getY(), projectile.getLocation().getZ(), FIRE_SPELL_EXPLOSION_POWER, false, false, shooter);

        if (directHit instanceof LivingEntity directTarget && sqlBattleManager.isBattleEnemyEntity(directTarget)) {
            directTarget.damage(FIRE_SPELL_DIRECT_DAMAGE, shooter);
            directTarget.setFireTicks(Math.max(directTarget.getFireTicks(), FIRE_SPELL_BURN_TICKS));
        }

        for (LivingEntity entity : projectile.getWorld().getLivingEntities()) {
            if (entity.equals(shooter) || !sqlBattleManager.isBattleEnemyEntity(entity)) {
                continue;
            }
            if (entity.getLocation().distanceSquared(projectile.getLocation()) > (FIRE_SPELL_RADIUS * FIRE_SPELL_RADIUS)) {
                continue;
            }
            entity.damage(FIRE_SPELL_AOE_DAMAGE, shooter);
            entity.setFireTicks(Math.max(entity.getFireTicks(), FIRE_SPELL_BURN_TICKS));
            entity.getWorld().spawnParticle(Particle.FLAME, entity.getLocation().add(0.0D, 1.0D, 0.0D), 10, 0.3D, 0.4D, 0.3D, 0.02D);
        }
    }

    private void emitSpellTrails() {
        List<UUID> staleProjectiles = new ArrayList<>();
        for (Map.Entry<UUID, SpellType> entry : spellProjectiles.entrySet()) {
            Entity entity = Bukkit.getEntity(entry.getKey());
            if (!(entity instanceof Projectile projectile) || !projectile.isValid() || projectile.isDead()) {
                staleProjectiles.add(entry.getKey());
                continue;
            }

            if (entry.getValue() == SpellType.ICE) {
                projectile.getWorld().spawnParticle(Particle.SNOWFLAKE, projectile.getLocation(), 4, 0.08D, 0.08D, 0.08D, 0.0D);
                continue;
            }

            projectile.getWorld().spawnParticle(Particle.FLAME, projectile.getLocation(), 3, 0.08D, 0.08D, 0.08D, 0.0D);
            projectile.getWorld().spawnParticle(Particle.SMOKE_NORMAL, projectile.getLocation(), 1, 0.02D, 0.02D, 0.02D, 0.0D);
        }

        if (!staleProjectiles.isEmpty()) {
            for (UUID projectileId : staleProjectiles) {
                spellProjectiles.remove(projectileId);
            }
        }
    }
}
