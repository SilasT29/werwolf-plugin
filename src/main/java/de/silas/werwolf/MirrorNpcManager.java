package de.silas.werwolf;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Menü-NPC in der Lobby: jeder Spieler sieht eine Mannequin-Kopie mit seinem eigenen Skin,
 * die ihn dauerhaft anschaut. Rechtsklick öffnet das Menü (nur OPs).
 */
public class MirrorNpcManager implements Listener {

    private static final double X = -34.46;
    private static final double Y = 87.0;
    private static final double Z = -98.70;

    private final WerwolfPlugin plugin;
    private final Map<UUID, Mannequin> mannequins = new HashMap<>();
    private BukkitTask lookTask;

    public MirrorNpcManager(WerwolfPlugin plugin) {
        this.plugin = plugin;
    }

    /** Entfernt übrig gebliebene Mannequins am Lobby-Punkt. */
    private void removeStrays() {
        World world = world();
        if (world == null) {
            return;
        }
        Location location = new Location(world, X, Y, Z);
        for (Entity entity : world.getNearbyEntities(location, 6, 6, 6)) {
            if (entity instanceof Mannequin mannequin && !mannequins.containsValue(mannequin)) {
                mannequin.remove();
                plugin.getLogger().info("Stray-Mannequin entfernt.");
            }
        }
    }

    public void start() {
        removeStrays();
        for (Player player : Bukkit.getOnlinePlayers()) {
            createFor(player);
        }
        lookTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickLook, 20L, 2L);
    }

    public void stop() {
        if (lookTask != null) {
            lookTask.cancel();
            lookTask = null;
        }
        for (Mannequin mannequin : mannequins.values()) {
            mannequin.remove();
        }
        mannequins.clear();
    }

    private World world() {
        return Bukkit.getWorld("world");
    }

    private void createFor(Player owner) {
        if (mannequins.containsKey(owner.getUniqueId())) {
            return;
        }
        World world = world();
        if (world == null) {
            return;
        }
        removeStrays();

        Mannequin mannequin = world.spawn(new Location(world, X, Y, Z), Mannequin.class, entity -> {
            entity.setProfile(ResolvableProfile.resolvableProfile(owner.getPlayerProfile()));
            entity.setImmovable(true);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.setSilent(true);
            entity.setAI(false);
            entity.setCollidable(false);
            entity.setCustomNameVisible(false);
            entity.setDescription(Component.empty());
            entity.setPose(Pose.STANDING);
        });

        mannequins.put(owner.getUniqueId(), mannequin);

        // Für alle anderen explizit verstecken (auch wenn sie schon getrackt wurden).
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (!other.getUniqueId().equals(owner.getUniqueId())) {
                other.hideEntity(plugin, mannequin);
            }
        }
        owner.showEntity(plugin, mannequin);
        rotateTo(mannequin, owner);
        plugin.getLogger().info("Menü-NPC für " + owner.getName() + " erstellt (" + mannequin.getUniqueId() + ").");
    }

    private void removeFor(Player owner) {
        Mannequin mannequin = mannequins.remove(owner.getUniqueId());
        if (mannequin != null) {
            mannequin.remove();
        }
    }

    private void tickLook() {
        World world = world();
        if (world == null) {
            return;
        }
        Location npcLocation = new Location(world, X, Y, Z);

        for (Player owner : Bukkit.getOnlinePlayers()) {
            Mannequin mannequin = mannequins.get(owner.getUniqueId());
            boolean near = owner.getWorld().equals(world)
                    && owner.getLocation().distanceSquared(npcLocation) < 64 * 64;

            if (mannequin == null || !mannequin.isValid()) {
                mannequins.remove(owner.getUniqueId());
                if (near) {
                    createFor(owner);
                }
                continue;
            }
            if (!owner.canSee(mannequin)) {
                owner.showEntity(plugin, mannequin);
            }
            // Dauerhaft für alle anderen unsichtbar halten.
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (!other.getUniqueId().equals(owner.getUniqueId()) && other.canSee(mannequin)) {
                    other.hideEntity(plugin, mannequin);
                }
            }
            rotateTo(mannequin, owner);
        }
    }

    private void rotateTo(Mannequin mannequin, Player owner) {
        if (mannequin == null || !mannequin.isValid()) {
            return;
        }
        Location base = mannequin.getLocation();
        Vector direction = owner.getEyeLocation().toVector()
                .subtract(base.clone().add(0, 1.6, 0).toVector());
        if (direction.lengthSquared() < 0.0001) {
            return;
        }
        Location look = base.clone().setDirection(direction);
        mannequin.setRotation(look.getYaw(), look.getPitch());
    }

    /** Diagnose: Mannequin direkt vor dem Spieler spawnen. */
    public Mannequin debugSpawn(Player player) {
        org.bukkit.util.Vector direction = player.getLocation().getDirection().setY(0).normalize().multiply(2);
        Location location = player.getLocation().add(direction);
        Mannequin mannequin = player.getWorld().spawn(location, Mannequin.class, entity -> {
            entity.setProfile(ResolvableProfile.resolvableProfile(player.getPlayerProfile()));
            entity.setImmovable(true);
            entity.setInvulnerable(true);
            entity.setPersistent(false);
            entity.setAI(false);
        });
        plugin.getLogger().info("Debug-Mannequin für " + player.getName() + " bei " + location
                + " canSee=" + player.canSee(mannequin));
        return mannequin;
    }

    /** Diagnose: alle Mannequins in der Nähe melden. */
    public int debugInfo(Player player) {
        int count = 0;
        for (Entity entity : player.getWorld().getNearbyEntities(player.getLocation(), 12, 12, 12)) {
            if (entity instanceof Mannequin mannequin) {
                count++;
                player.sendMessage(net.kyori.adventure.text.Component.text(
                        "Mannequin " + mannequin.getUniqueId().toString().substring(0, 8)
                                + " @ " + mannequin.getLocation().getBlockX() + "/" + mannequin.getLocation().getBlockY()
                                + "/" + mannequin.getLocation().getBlockZ()
                                + " canSee=" + player.canSee(mannequin)
                                + " valid=" + mannequin.isValid(),
                        net.kyori.adventure.text.format.NamedTextColor.GRAY));
            }
        }
        return count;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Mannequin own = mannequins.get(player.getUniqueId());
            for (Mannequin mannequin : mannequins.values()) {
                if (mannequin != own) {
                    player.hideEntity(plugin, mannequin);
                }
            }
            if (own != null) {
                player.showEntity(plugin, own);
            }
            createFor(player);
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeFor(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (!(event.getRightClicked() instanceof Mannequin mannequin)) {
            return;
        }
        UUID ownerId = null;
        for (Map.Entry<UUID, Mannequin> entry : mannequins.entrySet()) {
            if (entry.getValue().equals(mannequin)) {
                ownerId = entry.getKey();
                break;
            }
        }
        if (ownerId == null) {
            return;
        }
        // Sofort canceln, damit WorldGuard & Co. gar nicht erst meckern.
        event.setCancelled(true);

        Player player = event.getPlayer();
        if (!player.getUniqueId().equals(ownerId)) {
            return;
        }
        if (!player.hasPermission("werwolf.admin")) {
            player.sendMessage(Component.text("[Werwolf] ", NamedTextColor.DARK_GRAY)
                    .append(Component.text("Dieses Menü ist nur für das Team.", NamedTextColor.RED)));
            return;
        }
        plugin.menus().open(player);
    }
}
