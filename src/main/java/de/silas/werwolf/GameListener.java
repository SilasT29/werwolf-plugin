package de.silas.werwolf;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class GameListener implements Listener {

    private final java.util.Map<java.util.UUID, java.util.Map<Material, Long>> narratorCooldowns =
            new java.util.HashMap<>();

    private final WerwolfPlugin plugin;
    private final CinematicManager cinematic;
    private final SeatManager seats;

    public GameListener(WerwolfPlugin plugin, CinematicManager cinematic, SeatManager seats) {
        this.plugin = plugin;
        this.cinematic = cinematic;
        this.seats = seats;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                return;
            }
            Location lobby = plugin.config().lobbyOrFallback();
            if (lobby != null) {
                player.teleport(lobby);
            }
            plugin.overlay().showTo(player);
            plugin.gameManager().handleJoin(player);
            plugin.gameManager().applyVoicePermissions();
            plugin.hud().update(player);
            if (plugin.gameManager().state() == GameManager.State.LOBBY) {
                player.getInventory().clear();
                player.getInventory().setArmorContents(null);
            }
        }, 10L);
    }

    /** Treffer auf die vergrößerte Bett-Hitbox zählt als Treffer auf den Schlafenden. */
    @EventHandler
    public void onBedMarkerHit(EntityDamageByEntityEvent event) {
        Player victim = cinematic.markerVictim(event.getEntity());
        if (victim == null) {
            return;
        }
        event.setCancelled(true);
        if (event.getDamager() instanceof Player attacker) {
            plugin.gameManager().wolfHit(attacker, victim);
        }
    }

    /** Am Tag (alle sitzen in der Mitte) nimmt niemand Schaden. */
    @EventHandler
    public void onSeatedDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        if (plugin.gameManager().state() == GameManager.State.DAY && seats.isSeated(player)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.gameManager().forgetVoice(event.getPlayer());
        plugin.gameManager().handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onAmorClose(InventoryCloseEvent event) {
        GameManager game = plugin.gameManager();
        if (!game.amorSelecting() || !(event.getInventory().getHolder() instanceof AmorMenu)) {
            return;
        }
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (game.amorSelecting() && game.state() == GameManager.State.NIGHT && player.isOnline()) {
                game.reopenAmor(player);
            }
        }, 2L);
    }

    @EventHandler
    public void onBedLeave(PlayerBedLeaveEvent event) {
        if (cinematic.isBedLocked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!seats.isSeated(player)) {
            return;
        }
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) {
            return;
        }
        if (from.getX() != to.getX() || from.getY() != to.getY() || from.getZ() != to.getZ()) {
            Location fixed = from.clone();
            fixed.setYaw(to.getYaw());
            fixed.setPitch(to.getPitch());
            event.setTo(fixed);
        }
    }

    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (event.getEntity() instanceof Player player && event.getDismounted() instanceof ArmorStand) {
            if (seats.isSeated(player)) {
                // Aufstehen nur über das Plugin (Phasenwechsel, /ww test stop), nicht per Sneak.
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onNarratorItem(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player player = event.getPlayer();
        if (!plugin.gameManager().isNarrator(player.getUniqueId())) {
            return;
        }
        ItemStack item = event.getItem();
        if (item == null) {
            return;
        }
        Material type = item.getType();
        if (type != Material.CLOCK && type != Material.PAPER && type != Material.BARRIER) {
            return;
        }
        event.setCancelled(true);

        long now = System.currentTimeMillis();
        java.util.Map<Material, Long> cooldowns = narratorCooldowns.computeIfAbsent(
                player.getUniqueId(), key -> new java.util.EnumMap<>(Material.class));
        Long until = cooldowns.get(type);
        if (until != null && until > now) {
            long left = (until - now + 999) / 1000;
            player.sendActionBar(net.kyori.adventure.text.Component.text(
                    "Noch " + left + " s warten", net.kyori.adventure.text.format.NamedTextColor.RED));
            return;
        }
        cooldowns.put(type, now + 3000L);

        GameManager game = plugin.gameManager();
        switch (type) {
            case CLOCK -> {
                if (game.state() == GameManager.State.DAY) {
                    game.startNight();
                    player.sendMessage(net.kyori.adventure.text.Component.text("Nacht wird eingeleitet.",
                            net.kyori.adventure.text.format.NamedTextColor.GOLD));
                } else {
                    player.sendMessage(net.kyori.adventure.text.Component.text("Nur am Tag möglich (aktuell: " + game.state() + ").",
                            net.kyori.adventure.text.format.NamedTextColor.RED));
                }
            }
            case PAPER -> plugin.vote().start();
            case BARRIER -> game.stop();
            default -> {
            }
        }
    }

    @EventHandler
    public void onDropNarratorItem(PlayerDropItemEvent event) {
        if (!plugin.gameManager().isNarrator(event.getPlayer().getUniqueId())) {
            return;
        }
        Material type = event.getItemDrop().getItemStack().getType();
        if (type == Material.CLOCK || type == Material.PAPER || type == Material.BARRIER) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onWolfHit(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim) || !(event.getDamager() instanceof Player damager)) {
            return;
        }
        GameManager game = plugin.gameManager();
        if (game.state() != GameManager.State.NIGHT) {
            return;
        }
        if (game.roleOf(damager) != Role.WERWOLF) {
            return;
        }
        if (damager.getInventory().getItemInMainHand().getType() != Material.IRON_SWORD) {
            return;
        }
        if (game.roleOf(victim) == Role.WERWOLF) {
            event.setCancelled(true);
            return;
        }
        if (!victim.isSleeping()) {
            // Blinzelmädchen ist wach – ein Wolf, der sie erwischt, tötet sie sofort.
            if (game.roleOf(victim) == Role.EINZELGAENGER) {
                event.setCancelled(true);
                game.killCaught(victim);
            } else {
                event.setCancelled(true);
            }
            return;
        }
        event.setCancelled(true);
        game.wolfHit(damager, victim);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onDeadChat(AsyncChatEvent event) {
        GameManager game = plugin.gameManager();
        if (game.state() == GameManager.State.LOBBY) {
            return;
        }
        Player sender = event.getPlayer();
        if (!game.roles().containsKey(sender.getUniqueId()) || game.alive().contains(sender.getUniqueId())) {
            return;
        }

        event.setCancelled(true);
        Component message = Component.text("[Tot] ", NamedTextColor.DARK_GRAY)
                .append(Component.text(sender.getName() + ": ", NamedTextColor.GRAY))
                .append(event.message().colorIfAbsent(NamedTextColor.GRAY));

        for (Player online : Bukkit.getOnlinePlayers()) {
            if (game.roles().containsKey(online.getUniqueId())
                    && !game.alive().contains(online.getUniqueId())) {
                online.sendMessage(message);
            }
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        GameManager game = plugin.gameManager();
        if (game.state() == GameManager.State.LOBBY) {
            return;
        }
        Player player = event.getEntity();
        game.alive().remove(player.getUniqueId());
        plugin.hud().update(player);

        if (player.getUniqueId().equals(game.mayorId())) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> game.startMayorSuccessor(player), 10L);
        }
        if (game.roleOf(player) == Role.JAEGER) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> game.startHunterMenu(player), 10L);
        }

        java.util.UUID partnerId = game.lovePartnerOf(player.getUniqueId());
        if (partnerId != null && game.alive().contains(partnerId)) {
            Player partner = Bukkit.getPlayer(partnerId);
            if (partner != null) {
                game.alive().remove(partnerId);
                Bukkit.broadcast(Component.text(partner.getName() + " stirbt aus Liebeskummer.",
                        NamedTextColor.LIGHT_PURPLE));
                partner.setHealth(0);
            }
        }
        event.getDrops().clear();
        event.setDroppedExp(0);
        event.setKeepInventory(true);
        event.setKeepLevel(true);
        plugin.cinematic().wake(player);
        plugin.seats().standUp(player);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        GameManager game = plugin.gameManager();
        if (game.state() == GameManager.State.LOBBY) {
            return;
        }
        Player player = event.getPlayer();
        player.setGameMode(GameMode.SPECTATOR);
        Location center = plugin.config().center();
        if (center != null) {
            event.setRespawnLocation(center.clone().add(0, 3, 0));
        }
    }

    @EventHandler
    public void onQuitCleanup(PlayerQuitEvent event) {
        seats.standUp(event.getPlayer());
    }
}
