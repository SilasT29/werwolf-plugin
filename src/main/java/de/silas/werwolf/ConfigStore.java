package de.silas.werwolf;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persistent: Dorfmittelpunkt, Kamera, 20 Häuser (Bett/Tür/Spawn/Path) und 20 Sitze.
 */
public class ConfigStore {

    public static class House {
        public Location spawn;
        public Location bed;
        public Location door;
        public final List<Location> path = new ArrayList<>();
    }

    private final WerwolfPlugin plugin;
    private final File file;
    private final YamlConfiguration cfg = new YamlConfiguration();

    private Location center;
    private Location camera;
    private Location lobby;
    private double seatOffset = 1.0;
    private final Map<Integer, House> houses = new LinkedHashMap<>();
    private final Map<Integer, Location> seats = new LinkedHashMap<>();

    public ConfigStore(WerwolfPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "world.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            return;
        }
        try {
            cfg.load(file);
        } catch (Exception e) {
            plugin.getLogger().warning("world.yml konnte nicht geladen werden: " + e.getMessage());
        }

        center = readLoc(cfg.getConfigurationSection("center"));
        camera = readLoc(cfg.getConfigurationSection("camera"));
        lobby = readLoc(cfg.getConfigurationSection("lobby"));
        seatOffset = cfg.getDouble("seat-offset", 1.0);

        houses.clear();
        seats.clear();

        ConfigurationSection hs = cfg.getConfigurationSection("houses");
        if (hs != null) {
            for (String key : hs.getKeys(false)) {
                ConfigurationSection s = hs.getConfigurationSection(key);
                if (s == null) {
                    continue;
                }
                House house = new House();
                house.spawn = readLoc(s.getConfigurationSection("spawn"));
                house.bed = readLoc(s.getConfigurationSection("bed"));
                house.door = readLoc(s.getConfigurationSection("door"));
                for (String raw : s.getStringList("path")) {
                    Location loc = parseLoc(raw);
                    if (loc != null) {
                        house.path.add(loc);
                    }
                }
                houses.put(Integer.parseInt(key), house);
            }
        }

        ConfigurationSection ss = cfg.getConfigurationSection("seats");
        if (ss != null) {
            for (String key : ss.getKeys(false)) {
                Location loc = readLoc(ss.getConfigurationSection(key));
                if (loc != null) {
                    seats.put(Integer.parseInt(key), loc);
                }
            }
        }
    }

    public void save() {
        cfg.set("center", null);
        cfg.set("camera", null);
        cfg.set("houses", null);
        cfg.set("seats", null);

        writeLoc("center", center);
        writeLoc("camera", camera);
        writeLoc("lobby", lobby);
        cfg.set("seat-offset", seatOffset);

        for (Map.Entry<Integer, House> e : houses.entrySet()) {
            String base = "houses." + e.getKey();
            House h = e.getValue();
            writeLoc(base + ".spawn", h.spawn);
            writeLoc(base + ".bed", h.bed);
            writeLoc(base + ".door", h.door);
            List<String> path = new ArrayList<>();
            for (Location loc : h.path) {
                path.add(formatLoc(loc));
            }
            cfg.set(base + ".path", path);
        }

        for (Map.Entry<Integer, Location> e : seats.entrySet()) {
            writeLoc("seats." + e.getKey(), e.getValue());
        }

        try {
            if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
                plugin.getLogger().warning("Datenordner konnte nicht erstellt werden.");
            }
            cfg.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("world.yml konnte nicht gespeichert werden: " + e.getMessage());
        }
    }

    private void writeLoc(String path, Location loc) {
        if (loc == null) {
            return;
        }
        cfg.set(path + ".world", loc.getWorld() != null ? loc.getWorld().getName() : "world");
        cfg.set(path + ".x", loc.getX());
        cfg.set(path + ".y", loc.getY());
        cfg.set(path + ".z", loc.getZ());
        cfg.set(path + ".yaw", loc.getYaw());
        cfg.set(path + ".pitch", loc.getPitch());
    }

    private Location readLoc(ConfigurationSection s) {
        if (s == null) {
            return null;
        }
        World world = Bukkit.getWorld(s.getString("world", "world"));
        if (world == null) {
            return null;
        }
        return new Location(world, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                (float) s.getDouble("yaw"), (float) s.getDouble("pitch"));
    }

    private String formatLoc(Location loc) {
        String world = loc.getWorld() != null ? loc.getWorld().getName() : "world";
        return world + "," + loc.getX() + "," + loc.getY() + "," + loc.getZ();
    }

    private Location parseLoc(String raw) {
        String[] parts = raw.split(",");
        if (parts.length < 4) {
            return null;
        }
        World world = Bukkit.getWorld(parts[0]);
        if (world == null) {
            return null;
        }
        try {
            return new Location(world, Double.parseDouble(parts[1]), Double.parseDouble(parts[2]), Double.parseDouble(parts[3]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public double seatOffset() {
        return seatOffset;
    }

    public void seatOffset(double seatOffset) {
        this.seatOffset = seatOffset;
    }

    public Location lobby() {
        return lobby;
    }

    public void lobby(Location location) {
        this.lobby = location;
    }

    /** Lobby-Position; Fallback ist der feste Spawn bei -34,5 / 87 / -80,5. */
    public Location lobbyOrFallback() {
        if (lobby != null) {
            return lobby.clone();
        }
        World world = Bukkit.getWorld("world");
        return world != null ? new Location(world, -34.5, 87.0, -80.5) : null;
    }

    public Location center() {
        return center;
    }

    public void center(Location loc) {
        this.center = loc;
    }

    public Location camera() {
        return camera;
    }

    public void camera(Location loc) {
        this.camera = loc;
    }

    public Map<Integer, House> houses() {
        return houses;
    }

    public House house(int id) {
        return houses.computeIfAbsent(id, k -> new House());
    }

    public Map<Integer, Location> seats() {
        return seats;
    }

    public Location seat(int id) {
        return seats.get(id);
    }

    public void seat(int id, Location loc) {
        seats.put(id, loc);
    }

    public boolean removeSeat(int id) {
        return seats.remove(id) != null;
    }

    public boolean removeHouse(int id) {
        return houses.remove(id) != null;
    }

    public boolean removeBed(int id) {
        House house = houses.get(id);
        if (house == null || house.bed == null) {
            return false;
        }
        house.bed = null;
        return true;
    }

    public boolean removeDoor(int id) {
        House house = houses.get(id);
        if (house == null || house.door == null) {
            return false;
        }
        house.door = null;
        return true;
    }

    public boolean removeSpawn(int id) {
        House house = houses.get(id);
        if (house == null || house.spawn == null) {
            return false;
        }
        house.spawn = null;
        return true;
    }

    /** index == null: alle Wegpunkte löschen; sonst 1-basiert den einzelnen. */
    public int removePath(int id, Integer index) {
        House house = houses.get(id);
        if (house == null) {
            return 0;
        }
        if (index == null) {
            int count = house.path.size();
            house.path.clear();
            return count;
        }
        if (index >= 1 && index <= house.path.size()) {
            house.path.remove(index - 1);
            return 1;
        }
        return 0;
    }
}
