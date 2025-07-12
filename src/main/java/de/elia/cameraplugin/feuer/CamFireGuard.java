package de.elia.cameraplugin.feuer;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

/**
 * Simplified fire protection used for the camera mode. It hides nearby fire
 * blocks from the player and prevents burning while camera mode is active.
 */
public class CamFireGuard {
    private final JavaPlugin plugin;

    // configuration values
    private boolean enabled;
    private boolean hideFire;
    private long tickInterval;
    private double radiusH;
    private int radiusUp;
    private int radiusDown;
    private double radiusH2;
    private int rangeXZ;

    private final Map<UUID, BukkitRunnable> tasks = new HashMap<>();
    private final Map<UUID, Set<Block>> hiddenMap = new HashMap<>();

    public CamFireGuard(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfig(FileConfiguration config) {
        enabled = config.getBoolean("fireguard.enabled", true);
        hideFire = config.getBoolean("fireguard.hide-fire", true);
        tickInterval = config.getLong("fireguard.tick-interval", 10L);
        radiusH = config.getDouble("fireguard.radius-horizontal", 1.5);
        radiusUp = config.getInt("fireguard.radius-up", 2);
        radiusDown = config.getInt("fireguard.radius-down", 1);
        radiusH2 = radiusH * radiusH;
        rangeXZ = (int) Math.ceil(radiusH);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void startFor(Player player) {
        if (!enabled || tasks.containsKey(player.getUniqueId())) return;

        hiddenMap.put(player.getUniqueId(), new HashSet<>());
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!player.isOnline()) {
                    stopFor(player);
                    return;
                }
                if (hideFire) {
                    updateHiddenFire(player);
                }
                if (player.getFireTicks() > 0) {
                    player.setFireTicks(0);
                }
            }
        };
        task.runTaskTimer(plugin, 0L, tickInterval);
        tasks.put(player.getUniqueId(), task);
    }

    /**
     * Stops the protection for the given player. Returns true if the player is
     * currently standing in fire when the guard is removed.
     */
    public boolean stopFor(Player player) {
        UUID id = player.getUniqueId();
        BukkitRunnable task = tasks.remove(id);
        if (task != null) task.cancel();
        restoreAllHidden(player);
        hiddenMap.remove(id);
        return isPlayerInFire(player);
    }

    public void onDisable() {
        for (UUID id : new HashSet<>(tasks.keySet())) {
            Player p = plugin.getServer().getPlayer(id);
            if (p != null) {
                stopFor(p);
            }
        }
    }

    private void updateHiddenFire(Player p) {
        Location loc = p.getLocation();
        World w = loc.getWorld();
        if (w == null) return;

        Set<Block> current = new HashSet<>();
        for (int dx = -rangeXZ; dx <= rangeXZ; dx++)
            for (int dz = -rangeXZ; dz <= rangeXZ; dz++) {
                if (dx * dx + dz * dz > radiusH2) continue;
                for (int dy = -radiusDown; dy <= radiusUp; dy++) {
                    Block b = w.getBlockAt(
                            loc.getBlockX() + dx,
                            loc.getBlockY() + dy,
                            loc.getBlockZ() + dz);
                    Material t = b.getType();
                    if (t == Material.FIRE || t == Material.SOUL_FIRE) current.add(b);
                }
            }

        Set<Block> previous = hiddenMap.getOrDefault(p.getUniqueId(), Collections.emptySet());

        for (Block b : previous) if (!current.contains(b))
            p.sendBlockChange(b.getLocation(), b.getBlockData());

        for (Block b : current) if (!previous.contains(b))
            p.sendBlockChange(b.getLocation(), Material.AIR.createBlockData());

        hiddenMap.put(p.getUniqueId(), current);
    }

    private void restoreAllHidden(Player p) {
        Set<Block> hidden = hiddenMap.get(p.getUniqueId());
        if (hidden == null) return;
        for (Block b : hidden) {
            p.sendBlockChange(b.getLocation(), b.getBlockData());
        }
    }

    private boolean isPlayerInFire(Player p) {
        Location loc = p.getLocation();
        World w = loc.getWorld();
        if (w == null) return false;
        Block feet = loc.getBlock();
        if (feet.getType() == Material.FIRE || feet.getType() == Material.SOUL_FIRE) return true;
        Block head = w.getBlockAt(loc.getBlockX(), loc.getBlockY() + 1, loc.getBlockZ());
        return head.getType() == Material.FIRE || head.getType() == Material.SOUL_FIRE;
    }
}
