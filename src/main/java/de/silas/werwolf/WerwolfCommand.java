package de.silas.werwolf;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.block.Block;
import org.bukkit.block.data.type.Door;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class WerwolfCommand implements CommandExecutor, TabCompleter {

    private static final java.util.Set<String> MUTATING = java.util.Set.of(
            "sethouse", "setbed", "setdoor", "setseat", "delseat", "delhouse", "delbed",
            "deldoor", "delspawn", "delpath", "path", "setlobby", "setcenter", "setcamera");

    private final WerwolfPlugin plugin;

    public WerwolfCommand(WerwolfPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur ingame verfügbar.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("werwolf.admin")) {
            player.sendMessage(Component.text("Keine Berechtigung.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("menu")) {
            plugin.menus().open(player);
            return true;
        }

        ConfigStore config = plugin.config();
        switch (args[0].toLowerCase()) {
            case "setlobby" -> {
                config.lobby(player.getLocation());
                config.save();
                reply(player, "Lobby-Spawn gesetzt.");
            }
            case "setcenter" -> {
                config.center(player.getLocation());
                config.save();
                reply(player, "Dorfmittelpunkt gesetzt.");
            }
            case "setcamera" -> {
                config.camera(player.getLocation());
                config.save();
                reply(player, "Kamera-Position gesetzt.");
            }
            case "sethouse" -> {
                Integer id = id(player, args, 1);
                if (id == null) {
                    return true;
                }
                config.house(id).spawn = player.getLocation();
                config.save();
                reply(player, "Haus " + id + ": Spawn gesetzt.");
            }
            case "setbed" -> {
                Integer id = id(player, args, 1);
                if (id == null) {
                    return true;
                }
                Block block = player.getTargetBlockExact(6);
                if (block == null || !block.getType().name().endsWith("_BED")) {
                    reply(player, "Schau auf ein Bett (max. 6 Blöcke).", NamedTextColor.RED);
                    return true;
                }
                config.house(id).bed = plugin.cinematic().normalizeBed(block.getLocation());
                config.save();
                reply(player, "Haus " + id + ": Bett gesetzt.");
            }
            case "setdoor" -> {
                Integer id = id(player, args, 1);
                if (id == null) {
                    return true;
                }
                Block block = player.getTargetBlockExact(6);
                if (block == null || !(block.getBlockData() instanceof Door)) {
                    reply(player, "Schau auf eine Tür (max. 6 Blöcke).", NamedTextColor.RED);
                    return true;
                }
                config.house(id).door = block.getLocation();
                config.save();
                reply(player, "Haus " + id + ": Tür gesetzt.");
            }
            case "setseat" -> {
                Integer id = id(player, args, 1);
                if (id == null) {
                    return true;
                }
                config.seat(id, player.getLocation());
                config.save();
                reply(player, "Sitz " + id + " gesetzt.");
            }
            case "path" -> {
                if (args.length < 3 || !args[1].equalsIgnoreCase("add")) {
                    reply(player, "Nutzung: /ww path add <1-20>", NamedTextColor.RED);
                    return true;
                }
                Integer id = id(player, args, 2);
                if (id == null) {
                    return true;
                }
                config.house(id).path.add(player.getLocation());
                config.save();
                reply(player, "Haus " + id + ": Wegpunkt " + config.house(id).path.size() + " gesetzt.");
            }
            case "delseat" -> {
                Integer id = id(player, args, 1);
                if (id == null) {
                    return true;
                }
                boolean removed = config.removeSeat(id);
                config.save();
                reply(player, removed ? "Sitz " + id + " gelöscht." : "Sitz " + id + " war nicht gesetzt.", removed ? NamedTextColor.YELLOW : NamedTextColor.RED);
            }
            case "delhouse" -> {
                Integer id = id(player, args, 1);
                if (id == null) {
                    return true;
                }
                boolean removed = config.removeHouse(id);
                config.save();
                reply(player, removed ? "Haus " + id + " komplett gelöscht." : "Haus " + id + " existiert nicht.", removed ? NamedTextColor.YELLOW : NamedTextColor.RED);
            }
            case "delbed" -> {
                Integer id = id(player, args, 1);
                if (id == null) {
                    return true;
                }
                boolean removed = config.removeBed(id);
                config.save();
                reply(player, removed ? "Bett von Haus " + id + " gelöscht." : "Kein Bett bei Haus " + id + ".", removed ? NamedTextColor.YELLOW : NamedTextColor.RED);
            }
            case "deldoor" -> {
                Integer id = id(player, args, 1);
                if (id == null) {
                    return true;
                }
                boolean removed = config.removeDoor(id);
                config.save();
                reply(player, removed ? "Tür von Haus " + id + " gelöscht." : "Keine Tür bei Haus " + id + ".", removed ? NamedTextColor.YELLOW : NamedTextColor.RED);
            }
            case "delspawn" -> {
                Integer id = id(player, args, 1);
                if (id == null) {
                    return true;
                }
                boolean removed = config.removeSpawn(id);
                config.save();
                reply(player, removed ? "Spawn von Haus " + id + " gelöscht." : "Kein Spawn bei Haus " + id + ".", removed ? NamedTextColor.YELLOW : NamedTextColor.RED);
            }
            case "delpath" -> {
                Integer id = id(player, args, 1);
                if (id == null) {
                    return true;
                }
                Integer index = null;
                if (args.length >= 3) {
                    try {
                        index = Integer.parseInt(args[2]);
                    } catch (NumberFormatException e) {
                        reply(player, "Ungültige Wegpunkt-Nummer.", NamedTextColor.RED);
                        return true;
                    }
                }
                int removed = config.removePath(id, index);
                config.save();
                reply(player, removed > 0 ? removed + " Wegpunkt(e) von Haus " + id + " gelöscht." : "Keine Wegpunkte gefunden.", removed > 0 ? NamedTextColor.YELLOW : NamedTextColor.RED);
            }
            case "cleanup" -> {
                boolean all = args.length >= 2 && args[1].equalsIgnoreCase("all");
                int removed = all ? plugin.citizens().cleanupAll() : plugin.citizens().cleanupMarked();
                reply(player, removed + " NPC(s) entfernt" + (all ? " (alle Citizens-NPCs)." : " (Werwolf-NPCs)."),
                        NamedTextColor.YELLOW);
            }
            case "overlay" -> {
                boolean on = plugin.overlay().toggle();
                reply(player, on ? "Overlay an (" + plugin.overlayCount() + " Hologramme)." : "Overlay aus.");
            }
            case "seatoffset" -> {
                if (args.length < 2) {
                    reply(player, "Sitzhöhe aktuell: " + config.seatOffset() + " (nutze /ww seatoffset 1.0)", NamedTextColor.GRAY);
                    return true;
                }
                try {
                    double value = Double.parseDouble(args[1]);
                    if (value < 0.2 || value > 2.5) {
                        reply(player, "Wert zwischen 0.2 und 2.5.", NamedTextColor.RED);
                        return true;
                    }
                    config.seatOffset(value);
                    config.save();
                    reply(player, "Sitzhöhe auf " + value + " gesetzt.");
                } catch (NumberFormatException e) {
                    reply(player, "Ungültiger Wert.", NamedTextColor.RED);
                }
            }
            case "house" -> {
                Integer id = id(player, args, 1);
                if (id == null) {
                    return true;
                }
                ConfigStore.House house = config.house(id);
                Location target = house.spawn != null ? house.spawn : house.bed;
                if (target == null) {
                    reply(player, "Haus " + id + " hat keinen Spawn/Bett.", NamedTextColor.RED);
                    return true;
                }
                player.teleport(target);
                reply(player, "Teleportiert zu Haus " + id + ".");
            }
            case "seats" -> {
                if (args.length < 2 || !args[1].equalsIgnoreCase("show")) {
                    reply(player, "Nutzung: /ww seats show", NamedTextColor.RED);
                    return true;
                }
                showSeats(player);
            }
            case "cinematic" -> {
                plugin.cinematic().play(new ArrayList<>(Bukkit.getOnlinePlayers()));
                reply(player, "Kino gestartet (" + Bukkit.getOnlinePlayers().size() + " Spieler).");
            }
            case "vote" -> {
                plugin.vote().start();
                reply(player, "Abstimmung gestartet.");
            }
            case "narrator" -> {
                if (args.length < 2 || args[1].equalsIgnoreCase("off")) {
                    plugin.gameManager().clearNarrator();
                    reply(player, "Erzähler entfernt.");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    reply(player, "Spieler nicht gefunden.", NamedTextColor.RED);
                    return true;
                }
                plugin.gameManager().setNarrator(target);
                reply(player, target.getName() + " ist jetzt Erzähler (Sitz 20).");
            }
            case "night" -> {
                if (plugin.gameManager().state() != GameManager.State.DAY) {
                    reply(player, "Nacht kann nur am Tag gestartet werden (aktuell: " + plugin.gameManager().state() + ").", NamedTextColor.RED);
                    return true;
                }
                plugin.gameManager().startNight();
                reply(player, "Nacht gestartet.");
            }
            case "day" -> {
                plugin.gameManager().startDay();
                reply(player, "Tag gestartet.");
            }
            case "status" -> showStatus(player, config);
            case "stop" -> {
                plugin.gameManager().stop();
                reply(player, "Runde beendet: Kino, Kamera, Schlaf-Zwang, NPCs, Sitze zurückgesetzt.");
            }
            case "test" -> handleTest(player, args);
            default -> reply(player, "Unbekannt. /ww menu | setcenter | sethouse | setbed | setdoor | setseat | path add | house | seats show | cinematic | status | test", NamedTextColor.GRAY);
        }

        if (plugin.overlay().isActive() && MUTATING.contains(args[0].toLowerCase())) {
            plugin.overlay().show();
        }
        return true;
    }

    private void handleTest(Player player, String[] args) {
        if (args.length < 2) {
            reply(player, "Tests: npc <n>, door <n>, seat <n>, sleep <n>, wake, camera, cinematic [spieler]", NamedTextColor.GRAY);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "npc" -> {
                Integer id = id(player, args, 2);
                if (id == null) {
                    return;
                }
                if (!plugin.citizensAvailable()) {
                    reply(player, "Citizens ist nicht installiert.", NamedTextColor.RED);
                    return;
                }
                var npc = plugin.cinematic().testWalker(player, id);
                reply(player, npc == null ? "Haus " + id + " hat keine Tür/Spawn." : "NPC läuft zu Haus " + id + ".");
            }
            case "door" -> {
                Integer id = id(player, args, 2);
                if (id == null) {
                    return;
                }
                plugin.cinematic().openDoor(id);
                Bukkit.getScheduler().runTaskLater(plugin, () -> plugin.cinematic().closeDoor(id), 60L);
                reply(player, "Tür von Haus " + id + " öffnet (3 s).");
            }
            case "seat" -> {
                Integer id = id(player, args, 2);
                if (id == null) {
                    return;
                }
                Location seat = plugin.config().seat(id);
                if (seat == null) {
                    reply(player, "Sitz " + id + " ist nicht gesetzt.", NamedTextColor.RED);
                    return;
                }
                plugin.seats().sit(player, seat, plugin.config().seatOffset());
                reply(player, "Du sitzt auf Sitz " + id + ".");
            }
            case "sleep" -> {
                Integer id = id(player, args, 2);
                if (id == null) {
                    return;
                }
                boolean ok = plugin.cinematic().sleepIn(player, id);
                reply(player, ok ? "Du liegst in Bett von Haus " + id + " (gesperrt)." : "Schlafen fehlgeschlagen.");
            }
            case "stop" -> {
                plugin.gameManager().stop();
                reply(player, "Test abgebrochen, Runde zurückgesetzt.");
            }
            case "wake" -> {
                plugin.cinematic().wakeAll();
                plugin.seats().standUp(player);
                reply(player, "Alle geweckt / Sitze freigegeben.");
            }
            case "mannequin" -> {
                var mannequin = plugin.mirrorDebugSpawn(player);
                reply(player, "Mannequin gespawnt: " + mannequin.getUniqueId());
            }
            case "npcinfo" -> {
                int count = plugin.mirrorDebugInfo(player);
                reply(player, "Mannequins in der Nähe: " + count);
            }
            case "camera" -> {
                plugin.cinematic().testCamera(player, 100);
                reply(player, "Kamera-Lock für 5 s.");
            }
            case "night" -> {
                plugin.cinematic().setNight(player.getWorld());
                reply(player, "Nacht fixiert (Zeit läuft nicht weiter).");
            }
            case "day" -> {
                plugin.cinematic().setDay(player.getWorld());
                reply(player, "Tag gesetzt (Zeit läuft wieder).");
            }
            case "cinematic" -> {
                if (args.length >= 3) {
                    Player target = Bukkit.getPlayerExact(args[2]);
                    if (target == null) {
                        reply(player, "Spieler nicht gefunden.", NamedTextColor.RED);
                        return;
                    }
                    plugin.cinematic().play(List.of(target));
                    reply(player, "Kino für " + target.getName() + " gestartet.");
                } else {
                    plugin.cinematic().play(new ArrayList<>(Bukkit.getOnlinePlayers()));
                    reply(player, "Kino für alle gestartet.");
                }
            }
            default -> reply(player, "Unbekannter Test.", NamedTextColor.RED);
        }
    }

    private void showSeats(Player player) {
        Map<Integer, Location> seats = plugin.config().seats();
        if (seats.isEmpty()) {
            reply(player, "Keine Sitze gesetzt.", NamedTextColor.RED);
            return;
        }
        var task = Bukkit.getScheduler().runTaskTimer(plugin, () ->
                seats.values().forEach(loc -> loc.getWorld().spawnParticle(Particle.END_ROD, loc.clone().add(0, 1, 0), 3, 0.1, 0.1, 0.1, 0)), 0L, 10L);
        Bukkit.getScheduler().runTaskLater(plugin, task::cancel, 200L);
        reply(player, "Zeige " + seats.size() + " Sitze (10 s).");
    }

    private void showStatus(Player player, ConfigStore config) {
        int beds = 0;
        int doors = 0;
        for (ConfigStore.House house : config.houses().values()) {
            if (house.bed != null) {
                beds++;
            }
            if (house.door != null) {
                doors++;
            }
        }
        reply(player, "Mittelpunkt: " + (config.center() != null ? "ja" : "nein")
                + " | Kamera: " + (config.camera() != null ? "ja" : "eigen")
                + " | Häuser: " + config.houses().size()
                + " (Betten " + beds + ", Türen " + doors + ")"
                + " | Sitze: " + config.seats().size()
                + " | Citizens: " + (plugin.citizensAvailable() ? "ja" : "nein"), NamedTextColor.GRAY);
    }

    private Integer id(Player player, String[] args, int index) {
        if (args.length <= index) {
            reply(player, "Nummer fehlt (1-20).", NamedTextColor.RED);
            return null;
        }
        try {
            int id = Integer.parseInt(args[index]);
            if (id < 1 || id > 20) {
                reply(player, "Nummer muss 1-20 sein.", NamedTextColor.RED);
                return null;
            }
            return id;
        } catch (NumberFormatException e) {
            reply(player, "Ungültige Nummer.", NamedTextColor.RED);
            return null;
        }
    }

    private void reply(Player player, String message) {
        reply(player, message, NamedTextColor.YELLOW);
    }

    private void reply(Player player, String message, NamedTextColor color) {
        player.sendMessage(Component.text(message, color));
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("menu", "setlobby", "setcenter", "setcamera", "sethouse", "setbed", "setdoor",
                    "setseat", "delseat", "delhouse", "delbed", "deldoor", "delspawn", "delpath", "seatoffset", "path", "overlay", "house", "seats", "cinematic", "narrator", "vote", "night", "day", "status", "stop", "test"), args[0]);
        }
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("test")) {
                return filter(Arrays.asList("npc", "door", "seat", "sleep", "wake", "camera", "mannequin", "npcinfo", "cinematic", "night", "day", "stop"), args[1]);
            }
            if (args[0].equalsIgnoreCase("path")) {
                return filter(List.of("add"), args[1]);
            }
            if (args[0].equalsIgnoreCase("seats")) {
                return filter(List.of("show"), args[1]);
            }
            return numbers(args[1]);
        }
        if (args.length == 3) {
            if (args[0].equalsIgnoreCase("test") && args[1].equalsIgnoreCase("cinematic")) {
                return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[2]);
            }
            return numbers(args[2]);
        }
        return List.of();
    }

    private List<String> numbers(String prefix) {
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            String s = String.valueOf(i);
            if (s.startsWith(prefix)) {
                out.add(s);
            }
        }
        return out;
    }

    private List<String> filter(List<String> options, String prefix) {
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(prefix.toLowerCase())) {
                out.add(option);
            }
        }
        return out;
    }
}
