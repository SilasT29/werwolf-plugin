package de.silas.werwolf;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Color;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Entwickler-Overlay: Hologramme an allen konfigurierten Punkten (Häuser, Betten, Türen, Pfade, Sitze).
 */
public class OverlayManager {

    private final WerwolfPlugin plugin;
    private final ConfigStore config;
    private final List<TextDisplay> displays = new ArrayList<>();
    private boolean active;

    public OverlayManager(WerwolfPlugin plugin, ConfigStore config) {
        this.plugin = plugin;
        this.config = config;
    }

    public boolean isActive() {
        return active;
    }

    /** Schaltet das Overlay an/aus; Rückgabe = neuer Zustand. */
    public boolean toggle() {
        if (active) {
            hide();
        } else {
            show();
        }
        return active;
    }

    public void show() {
        hide();
        add(config.center(), "Zentrum", NamedTextColor.YELLOW, 1.4);
        add(config.camera(), "Kamera", NamedTextColor.AQUA, 0.8);

        for (Map.Entry<Integer, ConfigStore.House> entry : config.houses().entrySet()) {
            int id = entry.getKey();
            ConfigStore.House house = entry.getValue();
            add(house.spawn, "Haus " + id + " – Spawn", NamedTextColor.GREEN, 1.4);
            add(house.bed, "Haus " + id + " – Bett", NamedTextColor.LIGHT_PURPLE, 1.2);
            add(house.door, "Haus " + id + " – Tür", NamedTextColor.GOLD, 1.2);
            for (int i = 0; i < house.path.size(); i++) {
                add(house.path.get(i), "Haus " + id + " – Wegpunkt " + (i + 1), NamedTextColor.GRAY, 1.0);
            }
        }

        for (Map.Entry<Integer, Location> entry : config.seats().entrySet()) {
            add(entry.getValue(), "Sitz " + entry.getKey(), NamedTextColor.WHITE, 1.0);
        }

        active = true;
        for (Player player : Bukkit.getOnlinePlayers()) {
            showTo(player);
        }
    }

    /** Overlay nur für Teammitglieder sichtbar. */
    public void showTo(Player player) {
        if (!active || !player.hasPermission("werwolf.admin")) {
            return;
        }
        for (TextDisplay display : displays) {
            player.showEntity(plugin, display);
        }
    }

    private void add(Location location, String text, NamedTextColor color, double offsetY) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        Location spawn = location.clone().add(0, offsetY, 0);
        TextDisplay display = spawn.getWorld().spawn(spawn, TextDisplay.class, entity -> {
            entity.text(Component.text(text, color));
            entity.setBillboard(Display.Billboard.CENTER);
            entity.setSeeThrough(true);
            entity.setShadowed(true);
            entity.setDefaultBackground(false);
            entity.setBackgroundColor(Color.fromARGB(140, 0, 0, 0));
            entity.setPersistent(false);
            entity.setVisibleByDefault(false);
        });
        displays.add(display);
    }

    public int count() {
        return displays.size();
    }

    public void hide() {
        for (TextDisplay display : displays) {
            display.remove();
        }
        displays.clear();
        active = false;
    }
}
