package com.seminario.plugin.manager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Rotation;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Tutorial SQL slideshow rendered in an item frame in front of the player.
 */
public class TutorialSQLPresentationManager {

    private static final String TUTORIAL_NAME = "§9§lTutorial SQL";
    private static final int FIRST_SLIDE_MODEL_DATA = 3001;
    private static final int TOTAL_SLIDES = 16;
    private static final double MAX_DISTANCE_FROM_PRESENTATION = 3.0;
    private static final double[] FRAME_DISTANCE_CANDIDATES = { 0.0, 0.15, 0.35, 0.6, 1.0, 1.5, 2.0 };

    private final Map<UUID, PresentationSession> activeSessions = new HashMap<>();

    private static final class PresentationSession {
        private final int slot;
        private final ItemStack originalItem;
        private final ItemFrame frame;
        private final Location lockedPose;
        private final Location supportBlockLocation;
        private final boolean placedSupportBlock;
        private int currentSlide;

        private PresentationSession(int slot, ItemStack originalItem, ItemFrame frame, Location lockedPose,
                Location supportBlockLocation, boolean placedSupportBlock, int currentSlide) {
            this.slot = slot;
            this.originalItem = originalItem;
            this.frame = frame;
            this.lockedPose = lockedPose;
            this.supportBlockLocation = supportBlockLocation;
            this.placedSupportBlock = placedSupportBlock;
            this.currentSlide = currentSlide;
        }
    }

    private static final class Placement {
        private final Location frameLocation;
        private final Location supportBlockLocation;
        private final BlockFace frameFacing;
        private final boolean placedSupportBlock;

        private Placement(Location frameLocation, Location supportBlockLocation, BlockFace frameFacing,
                boolean placedSupportBlock) {
            this.frameLocation = frameLocation;
            this.supportBlockLocation = supportBlockLocation;
            this.frameFacing = frameFacing;
            this.placedSupportBlock = placedSupportBlock;
        }
    }

    public TutorialSQLPresentationManager(JavaPlugin plugin) {
    }

    public void openPresentation(Player player) {
        closePresentation(player);

        if (!player.isOnGround()) {
            player.sendMessage("§cNo puedes iniciar la presentación mientras estás en el aire o cayendo.");
            return;
        }

        int heldSlot = player.getInventory().getHeldItemSlot();
        ItemStack currentItem = player.getInventory().getItem(heldSlot);
        if (currentItem == null || currentItem.getType() != Material.PAPER) {
            return;
        }

        Placement placement = createPlacement(player);
        if (placement == null) {
            player.sendMessage("§cNo encontré un espacio libre frente a ti para mostrar la presentación.");
            return;
        }

        ItemFrame frame = spawnPresentationFrame(placement);
        frame.setItem(createFrameSlideItem(1));

        Location lockedPose = player.getLocation().clone();

        activeSessions.put(player.getUniqueId(), new PresentationSession(
            heldSlot,
            currentItem.clone(),
            frame,
            lockedPose,
            placement.supportBlockLocation,
            placement.placedSupportBlock,
            1
        ));

        player.getInventory().setHeldItemSlot(heldSlot);
        player.sendMessage("§aPresentación iniciada.");
        player.sendMessage("§7Click derecho: siguiente diapositiva");
        player.sendMessage("§7Click izquierdo: cerrar presentación");
        player.sendMessage("§7Cambiar de item, alejarte más de 3 bloques o llegar al final también cierra la presentación");
    }

    public boolean hasActivePresentation(Player player) {
        return activeSessions.containsKey(player.getUniqueId());
    }

    public boolean isControllerItem(ItemStack item) {
        if (item == null || item.getType() != Material.PAPER || !item.hasItemMeta()) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        return meta.hasDisplayName() && meta.getDisplayName().contains("Tutorial SQL");
    }

    public boolean isPresentationFrame(Entity entity, Player player) {
        if (!(entity instanceof ItemFrame itemFrame)) {
            return false;
        }

        PresentationSession session = activeSessions.get(player.getUniqueId());
        return session != null && session.frame != null && session.frame.isValid() && session.frame.getUniqueId().equals(itemFrame.getUniqueId());
    }

    public void advanceSlide(Player player) {
        PresentationSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (session.currentSlide >= TOTAL_SLIDES) {
            closePresentation(player);
            return;
        }

        session.currentSlide++;
        updatePresentationFrame(session);
    }

    public void closePresentation(Player player) {
        PresentationSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        clearSession(player);
        player.sendMessage("§7Presentación cerrada.");
    }

    public void handleHeldItemChange(Player player, int newSlot) {
        PresentationSession session = activeSessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (session.slot != newSlot) {
            closePresentation(player);
        }
    }

    public void enforceLockedPose(Player player) {
        PresentationSession session = activeSessions.remove(player.getUniqueId());
        if (session != null) {
            activeSessions.put(player.getUniqueId(), session);
            player.teleport(session.lockedPose);
        }
    }

    public Location getLockedPosition(Player player) {
        PresentationSession session = activeSessions.get(player.getUniqueId());
        return session == null ? null : session.lockedPose.clone();
    }

    public void handlePlayerMovement(Player player, Location nextLocation) {
        PresentationSession session = activeSessions.get(player.getUniqueId());
        if (session == null || nextLocation == null) {
            return;
        }

        if (session.frame == null || !session.frame.isValid()) {
            closePresentation(player);
            return;
        }

        Location frameLocation = session.frame.getLocation();
        if (!frameLocation.getWorld().equals(nextLocation.getWorld())) {
            closePresentation(player);
            return;
        }

        if (frameLocation.distanceSquared(nextLocation) > MAX_DISTANCE_FROM_PRESENTATION * MAX_DISTANCE_FROM_PRESENTATION) {
            clearSession(player);
            player.sendMessage("§7Presentación cerrada por alejarte demasiado.");
        }
    }

    public void clearSession(Player player) {
        PresentationSession session = activeSessions.remove(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (session.frame != null && session.frame.isValid()) {
            session.frame.remove();
        }

        if (session.placedSupportBlock) {
            Block supportBlock = session.supportBlockLocation.getBlock();
            if (supportBlock.getType() == Material.BARRIER) {
                supportBlock.setType(Material.AIR);
            }
        }

        player.getInventory().setItem(session.slot, session.originalItem.clone());
    }

    private void updatePresentationFrame(PresentationSession session) {
        if (session.frame != null && session.frame.isValid()) {
            session.frame.setItem(createFrameSlideItem(session.currentSlide));
        }
    }

    private Placement createPlacement(Player player) {
        BlockFace playerFacing = player.getFacing();
        if (!playerFacing.isCartesian() || playerFacing == BlockFace.UP || playerFacing == BlockFace.DOWN) {
            playerFacing = BlockFace.NORTH;
        }

        BlockFace frameFacing = playerFacing;

        for (double distance : FRAME_DISTANCE_CANDIDATES) {
            Location probe = player.getEyeLocation().clone().add(
                playerFacing.getModX() * distance,
                -0.3,
                playerFacing.getModZ() * distance
            );

            Block frameBlock = probe.getBlock();
            if (!frameBlock.getType().isAir()) {
                continue;
            }

            Block supportBlock = frameBlock.getRelative(frameFacing);
            boolean placeBarrier = supportBlock.getType().isAir();
            if (!placeBarrier && !supportBlock.getType().isSolid()) {
                continue;
            }

            if (placeBarrier) {
                supportBlock.setType(Material.BARRIER);
            }

            Location frameLocation = supportBlock.getLocation().add(0.5, 0.5, 0.5).add(
                placementOffset(frameFacing, -0.46875),
                0.0,
                placementOffsetZ(frameFacing, -0.46875)
            );
            return new Placement(frameLocation, supportBlock.getLocation(), frameFacing, placeBarrier);
        }

        return null;
    }

    private ItemFrame spawnPresentationFrame(Placement placement) {
        ItemFrame frame = (ItemFrame) placement.frameLocation.getWorld().spawnEntity(placement.frameLocation, EntityType.ITEM_FRAME);
        frame.setFacingDirection(placement.frameFacing, true);
        frame.setVisible(false);
        frame.setFixed(true);
        frame.setInvulnerable(true);
        frame.setItemDropChance(0.0f);
        frame.setPersistent(false);
        frame.setRotation(Rotation.NONE);
        return frame;
    }

    private double placementOffset(BlockFace face, double distance) {
        return face.getModX() * distance;
    }

    private double placementOffsetZ(BlockFace face, double distance) {
        return face.getModZ() * distance;
    }

    private ItemStack createFrameSlideItem(int slideNumber) {
        ItemStack item = new ItemStack(Material.PAPER);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(TUTORIAL_NAME + " §7(" + slideNumber + "/" + TOTAL_SLIDES + ")");
        meta.setLore(List.of(
            "§7Click derecho: siguiente",
            "§7Click izquierdo: cerrar"
        ));
        meta.setCustomModelData(FIRST_SLIDE_MODEL_DATA + slideNumber - 1);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }
}