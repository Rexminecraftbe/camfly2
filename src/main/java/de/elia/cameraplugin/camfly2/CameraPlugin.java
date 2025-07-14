package de.elia.cameraplugin.camfly2;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityPotionEffectEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.SkullMeta;
import de.elia.cameraplugin.mutplayer.ProtocolLibHook;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import de.elia.cameraplugin.camfly2.CamCommand;
import de.elia.cameraplugin.camfly2.CamTabCompleter;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.block.Block;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.scoreboard.Team;
import org.bukkit.NamespacedKey;
import org.bukkit.persistence.PersistentDataType;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.Collections;
import java.util.Collection;
import java.util.ArrayList;
import de.elia.cameraplugin.feuer.CamFireGuard;

import static org.bukkit.Sound.ENTITY_ITEM_BREAK;

@SuppressWarnings("removal")
public final class CameraPlugin extends JavaPlugin implements Listener {

    private final Map<UUID, CameraData> cameraPlayers = new HashMap<>();
    private final Map<UUID, Long> distanceMessageCooldown = new HashMap<>();
    private final Set<UUID> damageImmunityBypass = new HashSet<>();
    private final Map<UUID, UUID> armorStandOwners = new HashMap<>();
    private final Map<UUID, UUID> hitboxEntities = new HashMap<>();
    private final Set<UUID> pendingDamage = new HashSet<>();
    private final Set<UUID> mutedPlayers = new HashSet<>();
    private boolean protocolLibAvailable = false;
    private boolean muteAttack;
    private boolean muteFootsteps;
    private boolean hideSprintParticles;
    private CamFireGuard camFireGuard;
    private double particleHeight;
    private int particlesPerTick;
    private boolean showOwnParticles;
    private ChatColor protocolFoundLogColor;
    private ChatColor protocolNotFoundLogColor;
    private final Map<UUID, BukkitRunnable> particleTasks = new HashMap<>();
    private final Map<UUID, BukkitRunnable> actionBarTasks = new HashMap<>();
    private final Map<UUID, BukkitRunnable> offMessageTasks = new HashMap<>();
    private final Map<UUID, BukkitRunnable> timeLimitTasks = new HashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();
    private final Map<UUID, Long> camCooldowns = new HashMap<>();
    private final Map<UUID, BukkitRunnable> cooldownTasks = new HashMap<>();
    private final Map<UUID, Long> lastDamageTimes = new HashMap<>();
    private boolean shuttingDown = false;
    private NamespacedKey bodyKey;
    private NamespacedKey hitboxKey;
    private static final String CAM_OBJECTIVE = "cam_mode";
    private org.bukkit.scoreboard.Objective camModeObjective;

    private boolean actionBarEnabled;
    private String actionBarOnMessage;
    private String actionBarOffMessage;
    private int actionBarOffDuration;

    private static final String NO_COLLISION_TEAM = "cam_no_push";

    // Configurable values
    private boolean maxDistanceEnabled;
    private double maxDistance;
    private int distanceWarningCooldown;
    private double drowningDamage;
    private boolean armorStandNameVisible;
    private boolean armorStandVisible;
    private boolean armorStandGravity;
    private VisibilityMode playerVisibilityMode;
    private boolean allowInvisibilityPotion;
    private boolean allowLavaFlight;
    private Object Sound;

    // Time limit and cooldown settings
    private boolean timeLimitEnabled;
    private boolean cooldownsEnabled;
    private int durationSeconds;
    private int cooldownSeconds;
    private boolean showBossbar;
    private BarColor bossbarColor;
    private String bossbarText;
    private String cooldownText;
    private String cooldownAvailableText;

    // Camera safety settings
    private boolean camSafetyEnabled;
    private int camSafetyDelay;
    private String camSafetyMessage;

    private enum VisibilityMode { CAM, ALL, NONE }

    @Override
    public void onEnable() {
        shuttingDown = false;
        saveDefaultConfig();
        loadConfigValues();
        bodyKey = new NamespacedKey(this, "cam_body");
        hitboxKey = new NamespacedKey(this, "cam_hitbox");
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        camModeObjective = scoreboard.getObjective(CAM_OBJECTIVE);
        if (camModeObjective == null) {
            camModeObjective = scoreboard.registerNewObjective(CAM_OBJECTIVE, "dummy", "Cam Mode");
        }
        for (String entry : scoreboard.getEntries()) {
            camModeObjective.getScore(entry).setScore(0);
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            camModeObjective.getScore(p.getName()).setScore(0);
        }
        removeLeftoverEntities();
        camFireGuard = new CamFireGuard(this);
        camFireGuard.loadConfig(getConfig());
        if (muteAttack || muteFootsteps || hideSprintParticles) {
            if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
                protocolLibAvailable = true;
                new ProtocolLibHook(this, mutedPlayers, muteAttack, muteFootsteps, hideSprintParticles);
                String pfMessage = getMessage("protocol-found");
                if (protocolFoundLogColor != null) {
                    pfMessage = protocolFoundLogColor + pfMessage + ChatColor.RESET;
                }
                getLogger().info(ChatColor.stripColor(pfMessage));
            }
        }
        setupNoCollisionTeam();
        this.getCommand("cam").setExecutor(new CamCommand(this));
        this.getCommand("cam").setTabCompleter(new CamTabCompleter());
        this.getServer().getPluginManager().registerEvents(this, this);
        for (Player online : Bukkit.getOnlinePlayers()) {
            updateViewerTeam(online);
        }
        getLogger().info("CameraPlugin wurde aktiviert!");
    }

    @Override
    public void onDisable() {
        shuttingDown = true;
        // Erstellt eine Kopie der Keys, um ConcurrentModificationException zu vermeiden
        for (UUID playerId : new HashSet<>(cameraPlayers.keySet())) {
            Player player = Bukkit.getPlayer(playerId);
            if (player != null) {
                exitCameraMode(player);
            }
        }
        if (camFireGuard != null) {
            camFireGuard.onDisable();
        }
        for (BukkitRunnable task : particleTasks.values()) {
            task.cancel();
        }
        particleTasks.clear();
        for (BukkitRunnable task : actionBarTasks.values()) {
            task.cancel();
        }
        actionBarTasks.clear();
        for (BukkitRunnable task : offMessageTasks.values()) {
            task.cancel();
        }
        offMessageTasks.clear();
        for (BukkitRunnable task : timeLimitTasks.values()) {
            task.cancel();
        }
        timeLimitTasks.clear();
        for (BukkitRunnable task : cooldownTasks.values()) {
            task.cancel();
        }
        cooldownTasks.clear();
        for (BossBar bar : bossBars.values()) {
            bar.removeAll();
        }
        mutedPlayers.clear();
        removeLeftoverEntities();
        getLogger().info("CameraPlugin wurde deaktiviert!");
    }


    public void enterCameraMode(Player player) {
        // *** Inventar und Rüstung speichern ***
        PlayerInventory playerInventory = player.getInventory();
        ItemStack[] originalInventory = playerInventory.getContents();
        ItemStack[] originalArmor = playerInventory.getArmorContents();
        Collection<PotionEffect> pausedEffects = new ArrayList<>();
        for (PotionEffect effect : player.getActivePotionEffects()) {
            pausedEffects.add(effect);
            player.removePotionEffect(effect.getType());
        }
        // Create clones for the armor stand so the player's original items
        // can be restored later while durability changes are preserved.
        ItemStack[] armorStandArmor = new ItemStack[originalArmor.length];
        for (int i = 0; i < originalArmor.length; i++) {
            if (originalArmor[i] != null) {
                armorStandArmor[i] = originalArmor[i].clone();
            }
        }
        boolean originalSilent = player.isSilent();
        int originalRemainingAir = player.getRemainingAir();

        // *** Inventar und Rüstung leeren ***
        playerInventory.clear();
        playerInventory.setArmorContents(new ItemStack[4]);// Leeres Array für Rüstungsslots
        player.updateInventory();

        Location playerLocation = player.getLocation();

        ArmorStand armorStand = (ArmorStand) player.getWorld().spawnEntity(playerLocation, EntityType.ARMOR_STAND);
        armorStand.setRemainingAir(originalRemainingAir);
        armorStand.getPersistentDataContainer().set(bodyKey, PersistentDataType.INTEGER, 1);
        armorStand.setVisible(armorStandVisible);
        armorStand.setGravity(armorStandGravity);
        armorStand.setCanPickupItems(false);
        armorStand.setCustomName(getMessage("armorstand.name-format").replace("{player}", player.getName()));
        armorStand.setCustomNameVisible(armorStandNameVisible);
        armorStand.setInvulnerable(false);
        armorStand.setMarker(false);
        armorStand.setMaxHealth(20.0);
        armorStand.setHealth(20.0);
        armorStand.addEquipmentLock(EquipmentSlot.HEAD, ArmorStand.LockType.REMOVING_OR_CHANGING);
        armorStand.addEquipmentLock(EquipmentSlot.CHEST, ArmorStand.LockType.REMOVING_OR_CHANGING);
        armorStand.addEquipmentLock(EquipmentSlot.LEGS, ArmorStand.LockType.REMOVING_OR_CHANGING);
        armorStand.addEquipmentLock(EquipmentSlot.FEET, ArmorStand.LockType.REMOVING_OR_CHANGING);
        armorStand.addEquipmentLock(EquipmentSlot.HAND, ArmorStand.LockType.REMOVING_OR_CHANGING);
        armorStand.addEquipmentLock(EquipmentSlot.OFF_HAND, ArmorStand.LockType.REMOVING_OR_CHANGING);

        ItemStack playerHead = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta skullMeta = (SkullMeta) playerHead.getItemMeta();
        if (skullMeta != null) {
            skullMeta.setOwningPlayer(player);
            playerHead.setItemMeta(skullMeta);
        }
        armorStand.getEquipment().setHelmet(playerHead);

        // Equip the armor stand with the player's armor pieces so durability and
        // enchantments are retained while the player is in camera mode. The
        // original armor items are stored in CameraData and will be returned to
        // the player on exit. Using the same ItemStack objects ensures that any
        // durability loss while the armor stand is damaged is preserved.
        armorStand.getEquipment().setArmorContents(originalArmor);

        Villager hitbox = (Villager) player.getWorld().spawnEntity(playerLocation, EntityType.VILLAGER);
        hitbox.getPersistentDataContainer().set(hitboxKey, PersistentDataType.INTEGER, 1);
        hitbox.setInvisible(true);
        hitbox.setSilent(true);
        hitbox.setAI(false);
        hitbox.setInvulnerable(false);
        hitbox.setCustomName(getMessage("hitbox.name-format").replace("{player}", player.getName()));
        hitbox.setCustomNameVisible(false);
        hitbox.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        hitbox.setProfession(Villager.Profession.NONE);
        hitbox.setVillagerType(Villager.Type.PLAINS);
        hitbox.setVillagerLevel(1);
        hitbox.setCanPickupItems(false);
        hitbox.teleport(armorStand.getLocation().add(0, 0.1, 0));

        GameMode originalGameMode = player.getGameMode();
        boolean originalAllowFlight = player.getAllowFlight();
        boolean originalFlying = player.isFlying();

        player.setGameMode(GameMode.CREATIVE);
        player.setAllowFlight(true);
        player.setFlying(true);
        if (!protocolLibAvailable && (muteAttack || muteFootsteps)) {
            player.setSilent(true);
        }
        if (!protocolLibAvailable && (muteAttack || muteFootsteps)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
        }


        new BukkitRunnable() {
            @Override
            public void run() {
                player.setGameMode(GameMode.ADVENTURE);
                player.setAllowFlight(true); // ensure flight remains enabled
                player.setFlying(true);       // keep player flying
                if (allowInvisibilityPotion && !player.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, Integer.MAX_VALUE, 0, false, false));
                }
            }
        }.runTaskLater(this, 1L);

        double reaggroRadius = 64.0;
        for (Entity entity : player.getNearbyEntities(reaggroRadius, reaggroRadius, reaggroRadius)) {
            if (entity instanceof Mob) {
                Mob mob = (Mob) entity;
                if (player.equals(mob.getTarget())) {
                    mob.setTarget(hitbox); // redirect aggro to hitbox
                }
            }
        }

        // *** Gespeichertes Inventar an CameraData übergeben ***
        cameraPlayers.put(player.getUniqueId(), new CameraData(armorStand, hitbox, originalGameMode, originalAllowFlight, originalFlying, originalSilent, originalInventory, originalArmor, pausedEffects, originalRemainingAir));
        armorStandOwners.put(armorStand.getUniqueId(), player.getUniqueId());
        hitboxEntities.put(hitbox.getUniqueId(), player.getUniqueId());
        if (protocolLibAvailable) {
            mutedPlayers.add(player.getUniqueId());
        }

        startHitboxSync(armorStand, hitbox);
        startCameraParticles(player);
        startActionBar(player);
        camFireGuard.startFor(player);
        startArmorStandHealthCheck(player, armorStand);
        addPlayerToNoCollisionTeam(player);
        updateViewerTeam(player);
        updateVisibilityForAll();
        if (camModeObjective != null) {
            camModeObjective.getScore(player.getName()).setScore(1);
        }
        startTimeLimit(player);

        new BukkitRunnable() {
            @Override
            public void run() {
                String command = "item replace entity " +player.getName()+" armor.head with minecraft:player_head[profile={id:[I;-533456765,-1383640296,-2045139879,-1815718960],properties:[{name:\"textures\",value:\"e3RleHR1cmVzOntTS0lOOnt1cmw6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYmZlZjUxNDFkMGQyOTE1NGVmYTQ5NjE0NGUxMTdkMThjMjU3YjQ3MDVhZDEwZDI5YmEwN2VjN2Y0NWZjYWJjMyJ9fX0=\"}]},minecraft:lore=['{\"text\":\"https://namemc.com/skin/437c1be7c3e403c1\"}']]";
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
            }
        }.runTaskLater(this, 1L);
    }

    public void exitCameraMode(Player player) {
        CameraData cameraData = cameraPlayers.get(player.getUniqueId());
        if (cameraData == null) {
            // Ensure players are removed from the no-collision team even if the
            // CameraData has already been cleaned up by another call.
            removePlayerFromNoCollisionTeam(player);
            mutedPlayers.remove(player.getUniqueId());
            updateViewerTeam(player);
            if (camModeObjective != null) {
                camModeObjective.getScore(player.getName()).setScore(0);
            }
            return;
        }
        cancelTimeLimit(player);
        ArmorStand armorStand = cameraData.getArmorStand();
        Villager hitbox = cameraData.getHitbox();

        double reaggroRadius = 64.0;
        for (Entity entity : armorStand.getNearbyEntities(reaggroRadius, reaggroRadius, reaggroRadius)) {
            if (entity instanceof Mob) {
                Mob mob = (Mob) entity;
                if (armorStand.equals(mob.getTarget()) || hitbox.equals(mob.getTarget())) {
                    mob.setTarget(player);
                }
            }
        }

        // Zuerst zum Körper teleportieren
        player.teleport(armorStand.getLocation());
        stopCameraParticles(player);
        stopActionBar(player);
        if (!shuttingDown) {
            showActionBarOffMessage(player);
        }
        boolean standingInFire = camFireGuard.stopFor(player);

        // *** Inventar und Rüstung wiederherstellen ***
        PlayerInventory playerInventory = player.getInventory();
        playerInventory.clear(); // Sicherheitshalber leeren, falls Items hinzugefügt wurden
        playerInventory.setContents(cameraData.getOriginalInventoryContents());
        playerInventory.setArmorContents(cameraData.getOriginalArmorContents());
        player.updateInventory();

        player.removePotionEffect(PotionEffectType.INVISIBILITY);

        if (standingInFire) {
            player.setFireTicks(160);
        } else {
            player.setFireTicks(0);
        }
        for (PotionEffect effect : cameraData.getPausedEffects()) {
            player.addPotionEffect(effect);
        }
        player.setGameMode(cameraData.getOriginalGameMode());
        player.setAllowFlight(cameraData.getOriginalAllowFlight());
        player.setFlying(cameraData.getOriginalFlying());
        player.setSilent(cameraData.getOriginalSilent());
        player.setRemainingAir(cameraData.getOriginalRemainingAir());

        removePlayerFromNoCollisionTeam(player);

        cameraPlayers.remove(player.getUniqueId());
        mutedPlayers.remove(player.getUniqueId());
        updateViewerTeam(player);
        if (camModeObjective != null) {
            camModeObjective.getScore(player.getName()).setScore(0);
        }

        // Safety check to ensure the player really left the no-collision team
        removePlayerFromNoCollisionTeam(player);

        // Aufräumen
        armorStandOwners.remove(armorStand.getUniqueId());
        hitboxEntities.remove(hitbox.getUniqueId());

        // Clear equipment before removing to avoid item drops or duplication
        armorStand.getEquipment().setArmorContents(new ItemStack[4]);
        armorStand.getEquipment().setHelmet(new ItemStack(Material.AIR));
        armorStand.remove();
        hitbox.remove();

        for (Player other : Bukkit.getOnlinePlayers()) {
            other.showPlayer(this, player);
        }
        updateVisibilityForAll();
        if (!shuttingDown) {
            startCooldown(player);
        }
    }

    public boolean isInCameraMode(Player player) {
        return cameraPlayers.containsKey(player.getUniqueId());
    }

    private void startArmorStandHealthCheck(Player player, ArmorStand armorStand) {
        final Location initialLocation = armorStand.getLocation().clone();
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!cameraPlayers.containsKey(player.getUniqueId()) || !player.isOnline() || armorStand.isDead()) {
                    this.cancel();
                    return;
                }
                if (!armorStand.getLocation().getWorld().equals(initialLocation.getWorld()) ||
                        armorStand.getLocation().distanceSquared(initialLocation) > 0.01) {
                    sendConfiguredMessage(player, "body-moved");
                    exitCameraMode(player);
                    this.cancel();
                    return;
                }
                if (armorStand.getEyeLocation().getBlock().getType().isSolid()) {
                    sendConfiguredMessage(player, "body-suffocating");
                    exitCameraMode(player);
                    this.cancel();
                }
                if (armorStand.getRemainingAir() < armorStand.getMaximumAir() && armorStand.getRemainingAir() <= 0) {
                    if (armorStand.getTicksLived() % 20 == 0) {
                        sendConfiguredMessage(player, "body-drowning");
                        exitCameraMode(player);
                    }
                }
            }
        }.runTaskTimer(this, 20L, 1L);
    }

    private void startHitboxSync(ArmorStand armorStand, Villager hitbox) {
        new BukkitRunnable() {
            @Override
            public void run() {
                if (armorStand.isDead() || hitbox.isDead()) {
                    this.cancel();
                    return;
                }
                hitbox.teleport(armorStand.getLocation().add(0, 0.1, 0));
            }
        }.runTaskTimer(this, 1L, 1L);
    }

    private void startCameraParticles(Player player) {
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!cameraPlayers.containsKey(player.getUniqueId()) || !player.isOnline()) {
                    this.cancel();
                    return;
                }
                Location particleLoc = player.getLocation().add(0, particleHeight, 0);
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (!showOwnParticles && viewer.equals(player)) continue;
                    if (shouldShowParticlesTo(viewer)) {
                        viewer.spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc,
                                particlesPerTick, 0.1, 0.1, 0.1, 0);
                    }
                }
            }
        };
        task.runTaskTimer(this, 0L, 1L);
        particleTasks.put(player.getUniqueId(), task);
    }

    private boolean shouldShowParticlesTo(Player viewer) {
        return switch (playerVisibilityMode) {
            case ALL -> true;
            case CAM -> cameraPlayers.containsKey(viewer.getUniqueId());
            case NONE -> false;
        };
    }

    private void stopCameraParticles(Player player) {
        BukkitRunnable task = particleTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void startActionBar(Player player) {
        if (!actionBarEnabled) return;
        stopActionBar(player);
        BukkitRunnable off = offMessageTasks.remove(player.getUniqueId());
        if (off != null) off.cancel();
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                if (!cameraPlayers.containsKey(player.getUniqueId()) || !player.isOnline()) {
                    this.cancel();
                    return;
                }
                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarOnMessage));
            }
        };
        task.runTaskTimer(this, 0L, 40L);
        actionBarTasks.put(player.getUniqueId(), task);
    }

    private void stopActionBar(Player player) {
        BukkitRunnable task = actionBarTasks.remove(player.getUniqueId());
        if (task != null) {
            task.cancel();
        }
    }

    private void showActionBarOffMessage(Player player) {
        if (!actionBarEnabled || shuttingDown) return;
        stopActionBar(player);
        BukkitRunnable existing = offMessageTasks.remove(player.getUniqueId());
        if (existing != null) existing.cancel();

        BukkitRunnable task = new BukkitRunnable() {
            private int ticks = 0;


            @Override
            public void run() {
                if (!player.isOnline()) {
                    this.cancel();
                    offMessageTasks.remove(player.getUniqueId());
                    return;
                }

                if (ticks >= actionBarOffDuration) {
                    this.cancel();
                    offMessageTasks.remove(player.getUniqueId());
                    return;
                }

                player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(actionBarOffMessage));
                ticks++;
            }
        };

        task.runTaskTimer(this, 0L, 1L);
        offMessageTasks.put(player.getUniqueId(), task);
    }



    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onArmorStandDamage(EntityDamageEvent event) {
        Entity damagedEntity = event.getEntity();
        UUID ownerUUID = null;

        // Prüfe, ob es sich um unseren ArmorStand oder die zugehörige Hitbox handelt
        if (damagedEntity instanceof ArmorStand) {
            ownerUUID = armorStandOwners.get(damagedEntity.getUniqueId());
        } else if (damagedEntity instanceof Villager) {
            ownerUUID = hitboxEntities.get(damagedEntity.getUniqueId());
        }

        if (ownerUUID == null) return; // Nicht von uns verwaltet

        Player owner = Bukkit.getPlayer(ownerUUID);
        if (owner == null || !owner.isOnline()) {
            // Spieler offline -> Aufräumen
            if (damagedEntity instanceof ArmorStand) {
                armorStandOwners.remove(damagedEntity.getUniqueId());
            } else {
                hitboxEntities.remove(damagedEntity.getUniqueId());
            }
            cameraPlayers.remove(ownerUUID);
            damagedEntity.remove();
            return;
        }

        if (!pendingDamage.add(ownerUUID)) {
            // already scheduled damage for this hit
            return;
        }

        if (owner.isDead()) {
            event.setCancelled(true);
            exitCameraMode(owner);
            pendingDamage.remove(ownerUUID);
            return;
        }

        // ArmorStand soll keinen Schaden nehmen, Haltbarkeit manuell berechnen
        event.setCancelled(true);

        if (event instanceof EntityDamageByEntityEvent selfHit &&
                selfHit.getDamager().getUniqueId().equals(owner.getUniqueId())) {
            sendConfiguredMessage(owner, "camera-off");
            exitCameraMode(owner);
            pendingDamage.remove(ownerUUID);
            return;
        }

        String damagerName = "Umgebung";
        if (event instanceof EntityDamageByEntityEvent entityEvent) {
            Entity damager = entityEvent.getDamager();
            damagerName = damager instanceof Player ? damager.getName() : damager.getType().toString();
        }

        exitCameraMode(owner);

        String messageKey = event instanceof EntityDamageByEntityEvent ?
                "body-attacked" : "body-env-damage";
        if (isMessageEnabled(messageKey)) {
            owner.sendMessage(
                    getMessage(messageKey)
                            .replace("{damager}", damagerName)
                            .replace("{cause}", event.getCause().toString())
            );
        }
        pendingDamage.remove(ownerUUID);
    }



    @EventHandler(priority = EventPriority.HIGH)
    public void onVillagerInteract(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        if (entity instanceof Villager) {
            UUID ownerUUID = hitboxEntities.get(entity.getUniqueId());
            if (ownerUUID != null) {
                event.setCancelled(true);
                if (player.getUniqueId().equals(ownerUUID)) {
                    sendConfiguredMessage(player, "camera-off");
                    Player owner = Bukkit.getPlayer(ownerUUID);
                    if (owner != null) {
                        exitCameraMode(owner);
                    }
                } else {
                    sendConfiguredMessage(player, "cant-interact-other");
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onHitboxTransform(EntityTransformEvent event) {
        if (event.getTransformReason() == EntityTransformEvent.TransformReason.LIGHTNING
                && event.getEntity() instanceof Villager villager
                && hitboxEntities.containsKey(villager.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMobTarget(EntityTargetEvent event) {
        if (!(event.getTarget() instanceof Player)) return;
        Player player = (Player) event.getTarget();
        if (cameraPlayers.containsKey(player.getUniqueId())) {
            CameraData data = cameraPlayers.get(player.getUniqueId());
            if (data != null && data.getHitbox() != null && !data.getHitbox().isDead()) {
                event.setTarget(data.getHitbox());
            }
        }
    }

    @EventHandler
    public void onWardenTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getEntity() instanceof Warden)) return;
        LivingEntity target = event.getTarget();
        if (target instanceof Player player) {
            if (cameraPlayers.containsKey(player.getUniqueId())) {
                event.setCancelled(true);
                event.setTarget(null);
            }
        } else if (target instanceof ArmorStand stand) {
            UUID owner = armorStandOwners.get(stand.getUniqueId());
            if (owner != null && cameraPlayers.containsKey(owner)) {
                event.setCancelled(true);
                event.setTarget(null);
            }
        } else if (target instanceof Villager villager) {
            UUID owner = hitboxEntities.get(villager.getUniqueId());
            if (owner != null && cameraPlayers.containsKey(owner)) {
                event.setCancelled(true);
                event.setTarget(null);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockReceiveGame(BlockReceiveGameEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof Player player && cameraPlayers.containsKey(player.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity().getShooter() instanceof Player)) {
            return;
        }

        Player shooter = (Player) event.getEntity().getShooter();

        if (cameraPlayers.containsKey(shooter.getUniqueId())) {
            event.setCancelled(true); // Generell Projektile verhindern
            sendConfiguredMessage(shooter, "no-projectiles");
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.ADVENTURE) {
            player.stopSound(SoundCategory.PLAYERS);
        }
        if (!cameraPlayers.containsKey(player.getUniqueId())) {
            return;
        }

        Action action = event.getAction();

        // Verhindert jegliche Interaktionen im Kamera-Modus
        if (action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK ||
                action == Action.LEFT_CLICK_AIR || action == Action.LEFT_CLICK_BLOCK ||
                action == Action.PHYSICAL) {
            event.setCancelled(true);
            player.stopSound(SoundCategory.PLAYERS);
        }
    }

    @EventHandler
    public void onPlayerDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player &&
                cameraPlayers.containsKey(event.getEntity().getUniqueId()) &&
                !damageImmunityBypass.contains(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void recordLastDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastDamageTimes.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (cameraPlayers.containsKey(event.getPlayer().getUniqueId())) {
            exitCameraMode(event.getPlayer());
        }
        distanceMessageCooldown.remove(event.getPlayer().getUniqueId());
        removePlayerFromNoCollisionTeam(event.getPlayer());
        mutedPlayers.remove(event.getPlayer().getUniqueId());
        lastDamageTimes.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        new BukkitRunnable() {
            @Override
            public void run() {
                updateViewerTeam(event.getPlayer());
                updateVisibilityForAll();
                if (camModeObjective != null) {
                    camModeObjective.getScore(event.getPlayer().getName()).setScore(0);
                }
            }
        }.runTaskLater(this, 1L);
    }

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!cameraPlayers.containsKey(player.getUniqueId())) return;
        Location to = event.getTo();
        if (to == null) return;

        Block blockAt = to.getBlock();
        if (!allowLavaFlight && blockAt.getType() == Material.LAVA) {
            sendConfiguredMessage(player, "cant-fly-in-lava");
            exitCameraMode(player);
            return;
        }

        Location standLoc = cameraPlayers.get(player.getUniqueId()).getArmorStand().getLocation();
        // Always prevent players from switching worlds, optionally limit distance
        if (!to.getWorld().equals(standLoc.getWorld()) ||
                (maxDistanceEnabled && to.distanceSquared(standLoc) > maxDistance * maxDistance)) {
            event.setCancelled(true);
            long now = System.currentTimeMillis();
            if (distanceMessageCooldown.getOrDefault(player.getUniqueId(), 0L) < now) {
                if (isMessageEnabled("distance-limit")) {
                    player.sendMessage(getMessage("distance-limit").replace("{distance}", String.valueOf(maxDistance)));
                }
                distanceMessageCooldown.put(player.getUniqueId(), now + TimeUnit.SECONDS.toMillis(distanceWarningCooldown));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void suppressAdventureMoveSound(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.ADVENTURE) {
            player.stopSound(SoundCategory.PLAYERS);
            player.stopSound(SoundCategory.BLOCKS);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        // Der Spieler soll sterben, aber vorher den Kamera-Modus korrekt beenden.
        // Die Drops und XP werden vom Tod selbst gehandhabt.
        if (cameraPlayers.containsKey(event.getEntity().getUniqueId())) {
            // Wichtig: Die Items sind im CameraData gespeichert.
            // Wir müssen die Drops des Todes-Events leeren und unsere eigenen Items fallen lassen.
            Player player = event.getEntity();
            CameraData data = cameraPlayers.get(player.getUniqueId());

            event.getDrops().clear(); // Leert die Standard-Drops (leeres Inventar)

            // Füge die gespeicherten Items zu den Drops hinzu
            for (ItemStack item : data.getOriginalInventoryContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    event.getDrops().add(item);
                }
            }
            for (ItemStack item : data.getOriginalArmorContents()) {
                if (item != null && item.getType() != Material.AIR) {
                    event.getDrops().add(item);
                }
            }

            exitCameraMode(player);
        }
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player &&
                cameraPlayers.containsKey(event.getEntered().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player &&
                cameraPlayers.containsKey(event.getEntity().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (cameraPlayers.containsKey(event.getPlayer().getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBodyPotionEffect(EntityPotionEffectEvent event) {
        Entity entity = event.getEntity();
        UUID ownerUUID = null;

        if (entity instanceof ArmorStand) {
            ownerUUID = armorStandOwners.get(entity.getUniqueId());
        } else if (entity instanceof Villager) {
            ownerUUID = hitboxEntities.get(entity.getUniqueId());
        }

        if (ownerUUID == null) return;

        Player owner = Bukkit.getPlayer(ownerUUID);
        if (owner != null) {
            PotionEffect newEffect = event.getNewEffect();
            if (newEffect != null) {
                owner.addPotionEffect(newEffect);
            }
            exitCameraMode(owner);
        }

        event.setCancelled(true);
    }

    @EventHandler
    public void onPotionEffectChange(EntityPotionEffectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!allowInvisibilityPotion) return;
        if (playerVisibilityMode == VisibilityMode.NONE) return;
        if (cameraPlayers.containsKey(player.getUniqueId())) return;
        if (!PotionEffectType.INVISIBILITY.equals(event.getModifiedType())) return;

        Bukkit.getScheduler().runTask(this, () -> updateViewerTeam(player));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void filterCommandSuggestions(PlayerCommandSendEvent event) {
        event.getCommands().removeIf(cmd -> cmd.equalsIgnoreCase("camplugin:cam"));
        // Remove the namespaced variant of our command from the suggestion list
        // so only "/cam" is shown when tab completing. Using the plugin name
        // ensures this works even if the plugin is renamed.
        String namespaced = getName().toLowerCase() + ":cam";
        event.getCommands().removeIf(cmd -> cmd.equalsIgnoreCase(namespaced));
    }

    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!cameraPlayers.containsKey(attacker.getUniqueId())) {
            return;
        }

        Entity target = event.getEntity();
        UUID ownerUUID = null;

        if (target instanceof ArmorStand) {
            ownerUUID = armorStandOwners.get(target.getUniqueId());
        } else if (target instanceof Villager) {
            ownerUUID = hitboxEntities.get(target.getUniqueId());
        }

        // Cancel attacks on anything except the player's own armor stand or hitbox
        if (ownerUUID == null || !ownerUUID.equals(attacker.getUniqueId())) {
            event.setCancelled(true);
            if (ownerUUID != null && !ownerUUID.equals(attacker.getUniqueId())) {
                sendConfiguredMessage(attacker, "cant-attack-other-body");
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void suppressAdventureHitSound(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker &&
                attacker.getGameMode() == GameMode.ADVENTURE) {
            attacker.stopSound(SoundCategory.PLAYERS);
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player &&
                cameraPlayers.containsKey(event.getWhoClicked().getUniqueId())) {
            event.setCancelled(true);
        }
    }



    public String getMessage(String path) {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages." + path, ""));
    }

    public boolean isMessageEnabled(String path) {
        if (!getConfig().getBoolean("message-settings.enabled", true)) {
            return false;
        }
        return getConfig().getBoolean("message-settings." + path, true);
    }

    public void sendConfiguredMessage(CommandSender sender, String path) {
        if (!(sender instanceof Player) || isMessageEnabled(path)) {
            sender.sendMessage(getMessage(path));
        }
    }

    private void loadConfigValues() {
        maxDistanceEnabled = getConfig().getBoolean("camera-mode.max-distance-enabled", true);
        maxDistance = getConfig().getDouble("camera-mode.max-distance", 100.0);
        distanceWarningCooldown = getConfig().getInt("camera-mode.distance-warning-cooldown", 3);
        drowningDamage = getConfig().getDouble("camera-mode.drowning-damage", 2.0);
        String visibility = getConfig().getString("camera-mode.player_visibility_mode", "cam").toLowerCase();
        playerVisibilityMode = switch (visibility) {
            case "true" -> VisibilityMode.ALL;
            case "false" -> VisibilityMode.NONE;
            default -> VisibilityMode.CAM;
        };
        allowInvisibilityPotion = getConfig().getBoolean("camera-mode.allow_invisibility_potion", true);
        allowLavaFlight = getConfig().getBoolean("camera-mode.allow_lava_flight", false);
        armorStandNameVisible = getConfig().getBoolean("armorstand.name-visible", true);
        armorStandVisible = getConfig().getBoolean("armorstand.visible", true);
        armorStandGravity = getConfig().getBoolean("armorstand.gravity", true);
        muteAttack = getConfig().getBoolean("mute.attack", false);
        muteFootsteps = getConfig().getBoolean("mute.footsteps", false);
        hideSprintParticles = getConfig().getBoolean("mute.hide-sprint-particles", true);
        particleHeight = getConfig().getDouble("camera-particles.height", 1.0);
        particlesPerTick = getConfig().getInt("camera-particles.particles-per-tick", 5);
        showOwnParticles = getConfig().getBoolean("camera-particles.show-own-particles", false);
        protocolFoundLogColor = parseColor(getConfig().getString("log-colors.protocol-found", ""));
        actionBarEnabled = getConfig().getBoolean("action-bar.enabled", true);
        actionBarOffDuration = getConfig().getInt("action-bar.off-duration", 10);
        actionBarOnMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.actionbar-on", "&aCam-Modus aktiviert"));
        actionBarOffMessage = ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.actionbar-off", "&cCam-Modus beendet"));
        timeLimitEnabled = getConfig().getBoolean("time-limit.enabled", false);
        cooldownsEnabled = getConfig().getBoolean("time-limit.cooldowns-enabled", false);
        durationSeconds = getConfig().getInt("time-limit.duration-seconds", 300);
        cooldownSeconds = getConfig().getInt("time-limit.cooldown-seconds", 120);
        showBossbar = getConfig().getBoolean("time-limit.show-bossbar", true);
        String colorName = getConfig().getString("time-limit.bossbar-color", "BLUE");
        try {
            bossbarColor = BarColor.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            bossbarColor = BarColor.BLUE;
        }
        bossbarText = ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.bossbar-text", "Cam-Modus endet in: %time%"));
        cooldownText = ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.cooldown-text", "Du kannst den Cam-Modus erst in %time% erneut starten."));
        cooldownAvailableText = ChatColor.translateAlternateColorCodes('&', getConfig().getString("messages.cooldown-available", "&aCam-Modus wieder verf\u00fcgbar"));
        camSafetyEnabled = getConfig().getBoolean("cam-safety.enabled", true);
        camSafetyDelay = getConfig().getInt("cam-safety.delay", 5);
        camSafetyMessage = getConfig().getString("messages.cam-safety",
                "§cDu kannst den Cam-Modus nicht starten! Du musst noch %seconds% Sekunden in Sicherheit bleiben.");
        if (camFireGuard != null) {
            camFireGuard.loadConfig(getConfig());
        }
    }

    private ChatColor parseColor(String colorName) {
        if (colorName == null || colorName.isEmpty()) {
            return null;
        }
        try {
            return ChatColor.valueOf(colorName.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private void setupNoCollisionTeam() {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(NO_COLLISION_TEAM);
        if (team == null) {
            team = scoreboard.registerNewTeam(NO_COLLISION_TEAM);
        }
        team.setOption(Team.Option.COLLISION_RULE, Team.OptionStatus.NEVER);
        team.setCanSeeFriendlyInvisibles(true);
    }

    private void addPlayerToNoCollisionTeam(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(NO_COLLISION_TEAM);
        if (team != null) {
            team.addEntry(player.getName());
        }
    }

    private void removePlayerFromNoCollisionTeam(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(NO_COLLISION_TEAM);
        if (team != null) {
            team.removeEntry(player.getName());
        }
    }

    private void updateViewerTeam(Player player) {
        Scoreboard scoreboard = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = scoreboard.getTeam(NO_COLLISION_TEAM);
        if (team == null) return;

        boolean inCam = cameraPlayers.containsKey(player.getUniqueId());
        boolean shouldBeMember;

        if (inCam) {
            shouldBeMember = true;
        } else if (allowInvisibilityPotion && playerVisibilityMode == VisibilityMode.ALL) {
            shouldBeMember = !player.hasPotionEffect(PotionEffectType.INVISIBILITY);
        } else {
            shouldBeMember = false;
        }

        if (shouldBeMember) {
            if (!team.hasEntry(player.getName())) {
                team.addEntry(player.getName());
            }
        } else {
            if (team.hasEntry(player.getName())) {
                team.removeEntry(player.getName());
            }
        }
    }

    private void applyVisibility(Player camPlayer, Player viewer) {
        if (camPlayer.equals(viewer)) return;
        switch (playerVisibilityMode) {
            case CAM -> {
                if (cameraPlayers.containsKey(viewer.getUniqueId())) {
                    viewer.showPlayer(this, camPlayer);
                } else {
                    viewer.hidePlayer(this, camPlayer);
                }
            }
            case ALL -> viewer.showPlayer(this, camPlayer);
            case NONE -> viewer.hidePlayer(this, camPlayer);
        }
    }

    private void updateVisibilityForAll() {
        for (UUID camId : cameraPlayers.keySet()) {
            Player cam = Bukkit.getPlayer(camId);
            if (cam == null) continue;
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                applyVisibility(cam, viewer);
            }
        }
    }

    private void removeLeftoverEntities() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity.getPersistentDataContainer().has(bodyKey, PersistentDataType.INTEGER) ||
                        entity.getPersistentDataContainer().has(hitboxKey, PersistentDataType.INTEGER)) {
                    entity.remove();
                }
            }
        }
    }

    public String formatDuration(long seconds) {
        if (seconds >= 3600) {
            long h = seconds / 3600;
            long m = (seconds % 3600) / 60;
            long s = seconds % 60;
            return h + "h " + m + "m " + s + "s";
        } else if (seconds >= 60) {
            long m = seconds / 60;
            long s = seconds % 60;
            return m + "m " + s + "s";
        } else {
            return seconds + " seconds";
        }
    }

    private void startTimeLimit(Player player) {
        if (!timeLimitEnabled) return;
        cancelTimeLimit(player);
        BossBar bar = null;
        if (showBossbar) {
            bar = Bukkit.createBossBar("", bossbarColor, BarStyle.SOLID);
            bar.addPlayer(player);
            bossBars.put(player.getUniqueId(), bar);
        }
        long total = durationSeconds;
        BossBar finalBar = bar;
        BukkitRunnable task = new BukkitRunnable() {
            long remaining = total;
            @Override
            public void run() {
                if (!cameraPlayers.containsKey(player.getUniqueId()) || !player.isOnline()) {
                    cancel();
                    if (finalBar != null) finalBar.removeAll();
                    return;
                }
                remaining--;
                if (finalBar != null) {
                    finalBar.setProgress(Math.max(0.0, remaining / (double) total));
                    if (isMessageEnabled("bossbar-text")) {
                        finalBar.setTitle(bossbarText.replace("%time%", formatDuration(remaining)));
                    }
                }
                if (remaining <= 0) {
                    cancel();
                    if (finalBar != null) finalBar.removeAll();
                    exitCameraMode(player);
                    sendConfiguredMessage(player, "time-limit-expired");
                }
            }
        };
        task.runTaskTimer(this, 20L, 20L);
        timeLimitTasks.put(player.getUniqueId(), task);
    }

    private void cancelTimeLimit(Player player) {
        BukkitRunnable t = timeLimitTasks.remove(player.getUniqueId());
        if (t != null) t.cancel();
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) bar.removeAll();
    }

    private void startCooldown(Player player) {
        if (!cooldownsEnabled) return;
        long end = System.currentTimeMillis() + cooldownSeconds * 1000L;
        camCooldowns.put(player.getUniqueId(), end);
        BukkitRunnable task = new BukkitRunnable() {
            @Override
            public void run() {
                long remaining = end - System.currentTimeMillis();
                if (remaining <= 0) {
                    Player p = Bukkit.getPlayer(player.getUniqueId());
                    if (p != null && isMessageEnabled("cooldown-available")) {
                        p.sendMessage(cooldownAvailableText);
                    }
                    camCooldowns.remove(player.getUniqueId());
                    cooldownTasks.remove(player.getUniqueId());
                    cancel();
                }
            }
        };
        task.runTaskTimer(this, 20L, 20L);
        cooldownTasks.put(player.getUniqueId(), task);
    }

    public boolean isCooldownActive(Player player) {
        if (!cooldownsEnabled) return false;
        Long end = camCooldowns.get(player.getUniqueId());
        if (end == null) return false;
        if (System.currentTimeMillis() >= end) {
            camCooldowns.remove(player.getUniqueId());
            BukkitRunnable task = cooldownTasks.remove(player.getUniqueId());
            if (task != null) task.cancel();
            return false;
        }
        return true;
    }

    public long getCooldownRemaining(Player player) {
        Long end = camCooldowns.get(player.getUniqueId());
        if (end == null) return 0;
        long remaining = end - System.currentTimeMillis();
        if (remaining < 0) return 0;
        return (remaining + 999) / 1000;
    }

    public boolean checkCamSafety(Player player) {
        if (!camSafetyEnabled) return true;
        Long last = lastDamageTimes.get(player.getUniqueId());
        if (last == null) return true;
        long elapsed = System.currentTimeMillis() - last;
        long delayMillis = camSafetyDelay * 1000L;
        if (elapsed >= delayMillis) return true;
        long remaining = (delayMillis - elapsed + 999) / 1000;
        String msg = camSafetyMessage.replace("%seconds%", String.valueOf(remaining));
        if (isMessageEnabled("cam-safety")) {
            player.sendMessage(ChatColor.RED + ChatColor.translateAlternateColorCodes('&', msg));
        }
        return false;
    }

    public void reloadPlugin(Player initiator) {
        for (UUID uuid : new HashSet<>(cameraPlayers.keySet())) {
            Player camPlayer = Bukkit.getPlayer(uuid);
            if (camPlayer != null) {
                sendConfiguredMessage(camPlayer, "reload-exit");
                exitCameraMode(camPlayer);
            }
        }
        reloadConfig();
        loadConfigValues();
        protocolLibAvailable = false;
        mutedPlayers.clear();
        if (muteAttack || muteFootsteps || hideSprintParticles) {
            if (getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
                protocolLibAvailable = true;
                new ProtocolLibHook(this, mutedPlayers, muteAttack, muteFootsteps, hideSprintParticles);
                String pfMessage = getMessage("protocol-found");
                if (protocolFoundLogColor != null) {
                    pfMessage = protocolFoundLogColor + pfMessage + ChatColor.RESET;
                }
                getLogger().info(ChatColor.stripColor(pfMessage));
            }
        }
        setupNoCollisionTeam();
        for (Player online : Bukkit.getOnlinePlayers()) {
            updateViewerTeam(online);
        }
        for (BukkitRunnable task : cooldownTasks.values()) {
            task.cancel();
        }
        cooldownTasks.clear();
        camCooldowns.clear();
    }

    // *** CameraData Klasse erweitert ***
    private static class CameraData {
        private final ArmorStand armorStand;
        private final Villager hitbox;
        private final GameMode originalGameMode;
        private final boolean originalAllowFlight;
        private final boolean originalFlying;
        private final boolean originalSilent;
        private final int originalRemainingAir;
        private final ItemStack[] originalInventoryContents; // Für Inventar
        private final ItemStack[] originalArmorContents;     // Für Rüstung
        private final Collection<PotionEffect> pausedEffects;

        public CameraData(ArmorStand armorStand, Villager hitbox, GameMode originalGameMode, boolean originalAllowFlight, boolean originalFlying, boolean originalSilent, ItemStack[] originalInventoryContents, ItemStack[] originalArmorContents, Collection<PotionEffect> pausedEffects, int originalRemainingAir) {
            this.armorStand = armorStand;
            this.hitbox = hitbox;
            this.originalGameMode = originalGameMode;
            this.originalAllowFlight = originalAllowFlight;
            this.originalFlying = originalFlying;
            this.originalSilent = originalSilent;
            this.originalInventoryContents = originalInventoryContents;
            this.originalArmorContents = originalArmorContents;
            this.pausedEffects = pausedEffects;
            this.originalRemainingAir = originalRemainingAir;
        }

        public ArmorStand getArmorStand() { return armorStand; }
        public Villager getHitbox() { return hitbox; }
        public GameMode getOriginalGameMode() { return originalGameMode; }
        public boolean getOriginalAllowFlight() { return originalAllowFlight; }
        public boolean getOriginalFlying() { return originalFlying; }
        public boolean getOriginalSilent() { return originalSilent; }
        public ItemStack[] getOriginalInventoryContents() { return originalInventoryContents; }
        public ItemStack[] getOriginalArmorContents() { return originalArmorContents; }
        public Collection<PotionEffect> getPausedEffects() { return pausedEffects; }
        public int getOriginalRemainingAir() { return originalRemainingAir; }
    }
}