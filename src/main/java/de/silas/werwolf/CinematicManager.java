package de.silas.werwolf;

import net.citizensnpcs.api.npc.NPC;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Bed;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Kino-Nacht: Kamera-Lock, Citizens-NPCs laufen zu den Häusern, Türen, Betten.
 */
public class CinematicManager {

    private final WerwolfPlugin plugin;
    private final ConfigStore config;
    private final CitizensHook citizens;

    private final Map<UUID, Location> lockedBeds = new HashMap<>();
    private final Map<UUID, UUID> bedMarkers = new HashMap<>();   // Marker-Entity -> Spieler
    private final Map<UUID, UUID> playerMarkers = new HashMap<>(); // Spieler -> Marker-Entity
    private final List<NPC> activeNpcs = new ArrayList<>();
    private final Map<UUID, Integer> houseAssignments = new LinkedHashMap<>();

    private final List<BukkitTask> pendingTasks = new ArrayList<>();
    private final Map<NPC, Integer> walkerHouses = new LinkedHashMap<>();
    private BukkitTask arrivalTask;
    private int arrivalTicks;
    private boolean anyWalker;
    private BukkitTask cameraTask;
    private BukkitTask cameraEnforceTask;
    private Location cameraLocation;
    private Collection<Player> cameraPlayers = List.of();

    private final Map<NPC, Location> morningTargets = new LinkedHashMap<>();
    private final Map<UUID, Location> morningSeats = new HashMap<>();
    private BukkitTask morningTask;
    private int morningTicks;

    public CinematicManager(WerwolfPlugin plugin, ConfigStore config, CitizensHook citizens) {
        this.plugin = plugin;
        this.config = config;
        this.citizens = citizens;
    }

    /* ---------------------------------------------------------------- Kino */

    public void play(Collection<Player> players) {
        assignHouses(players);
        startInternal(players);
    }

    public void play(Collection<Player> players, Map<UUID, Integer> houses) {
        houseAssignments.clear();
        houseAssignments.putAll(houses);
        startInternal(players);
    }

    private void startInternal(Collection<Player> players) {
        if (config.center() == null) {
            players.forEach(p -> p.sendMessage(Component.text("Kein Dorfmittelpunkt gesetzt (/ww setcenter).", NamedTextColor.RED)));
            return;
        }

        World world = config.center().getWorld();
        if (world != null) {
            setNight(world);
        }

        Location cam = cameraLocation();
        this.cameraLocation = cam;
        this.cameraPlayers = players;

        Title title = Title.title(
                Component.text("Die Nacht bricht an", NamedTextColor.DARK_RED),
                Component.text("Alle gehen nach Hause …", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(400)));

        for (Player player : players) {
            // Sitz-Stuhl lösen, sonst bleibt der Spieler beim Teleport sitzen.
            plugin.seats().standUp(player);
            if (player.isSleeping()) {
                player.wakeup(true);
            }
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setInvulnerable(true);
            player.setFallDistance(0);
            player.showTitle(title);
        }

        hideParticipants(players);
        startCameraLock();

        pendingTasks.add(Bukkit.getScheduler().runTaskLater(plugin, () -> spawnWalkers(players), 4L));
        // Kamera läuft, bis alle NPCs angekommen sind (max. 20 s).
        pendingTasks.add(Bukkit.getScheduler().runTaskLater(plugin, this::startArrivalWatch, 20L));
    }

    /** Nacht fixieren, bis das Spiel sie beendet. */
    public void setNight(World world) {
        world.setTime(14000L);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setStorm(false);
    }

    /** Spiel-Tag: Zeit wieder laufen lassen. */
    public void setDay(World world) {
        world.setTime(1000L);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, true);
    }

    private void assignHouses(Collection<Player> players) {
        houseAssignments.clear();
        List<Integer> free = new ArrayList<>(config.houses().keySet());
        free.sort(Integer::compareTo);
        List<Player> sorted = new ArrayList<>(players);
        sorted.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        int index = 0;
        for (Player player : sorted) {
            if (free.isEmpty()) {
                break;
            }
            int houseId = free.get(index % free.size());
            houseAssignments.put(player.getUniqueId(), houseId);
            index++;
        }
    }

    /** Kamera-Spieler sehen sich gegenseitig nicht (keine Nametags, kein Ineinanderstehen). */
    private void hideParticipants(Collection<? extends Player> players) {
        for (Player viewer : players) {
            for (Player other : players) {
                if (viewer != other) {
                    viewer.hidePlayer(plugin, other);
                }
            }
        }
    }

    private void showParticipants(Collection<? extends Player> players) {
        for (Player viewer : players) {
            for (Player other : players) {
                if (viewer != other) {
                    viewer.showPlayer(plugin, other);
                }
            }
        }
    }

    public Location cameraView() {
        return cameraLocation();
    }

    private Location cameraLocation() {
        if (config.camera() != null) {
            return config.camera().clone();
        }
        Location center = config.center().clone();
        Location cam = center.clone().add(0, 22, 14);
        cam.setDirection(center.toVector().subtract(cam.toVector()));
        return cam;
    }

    private void startCameraLock() {
        cancelCameraTask();
        if (cameraLocation == null) {
            return;
        }
        cameraTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : cameraPlayers) {
                if (player.isOnline()) {
                    player.teleport(cameraLocation);
                    player.setFlying(true);
                }
            }
        }, 0L, 1L);
    }

    private void stopCameraLock() {
        cancelCameraTask();
        for (Player player : cameraPlayers) {
            if (player.isOnline()) {
                player.setAllowFlight(false);
                player.setFlying(false);
                player.setInvulnerable(false);
            }
        }
        cameraPlayers = List.of();
    }

    /* ------------------------------------------------------------- Morgen */

    /**
     * Morgen-Kino: NPCs verlassen die Häuser, laufen zurück zu den Sitzen,
     * danach werden die Spieler auf ihre Sitze gesetzt.
     */
    public void playMorning(Collection<Player> players, Runnable onDone) {
        if (players.isEmpty()) {
            onDone.run();
            return;
        }
        if (config.center() == null) {
            for (Player player : players) {
                seatPlayer(player);
            }
            onDone.run();
            return;
        }

        if (config.center().getWorld() != null) {
            setDay(config.center().getWorld());
        }
        cameraLocation = cameraLocation();
        cameraPlayers = players;

        // Wichtig: Bett-Sperre lösen, sonst legt enforceSleep alle wieder schlafen.
        wakeAll();

        for (Player player : players) {
            plugin.seats().standUp(player);
            if (player.isSleeping()) {
                player.wakeup(true);
            }
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setInvulnerable(true);
            player.setFallDistance(0);
        }
        hideParticipants(players);
        startCameraLock();

        Bukkit.getScheduler().runTaskLater(plugin, () -> spawnMorningWalkers(players), 10L);
        morningTicks = 0;
        cancelMorningTask();
        morningTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickMorning(players, onDone), 20L, 5L);
    }

    private void spawnMorningWalkers(Collection<Player> players) {
        if (!citizens.available()) {
            return;
        }
        for (Player player : players) {
            Integer houseId = houseAssignments.get(player.getUniqueId());
            if (houseId == null) {
                continue;
            }
            ConfigStore.House house = config.house(houseId);
            Location start = house.door != null ? house.door : house.spawn;
            Location seat = config.seat(houseId);
            Location target = seat != null ? seat : config.center();
            if (start == null || target == null) {
                continue;
            }

            List<Location> back = new ArrayList<>(house.path == null ? List.of() : house.path);
            java.util.Collections.reverse(back);

            NPC npc = citizens.spawnWalker(player, start, back, target, 1.8);
            activeNpcs.add(npc);
            morningTargets.put(npc, target);
            morningSeats.put(player.getUniqueId(), seat);
            openDoor(houseId);
        }
    }

    private void tickMorning(Collection<Player> players, Runnable onDone) {
        morningTicks += 5;
        for (NPC npc : new ArrayList<>(morningTargets.keySet())) {
            Location target = morningTargets.get(npc);
            boolean arrived = npc == null || !npc.isSpawned() || npc.getEntity() == null || target == null
                    || npc.getEntity().getLocation().distanceSquared(target) <= 9.0;
            if (arrived) {
                morningTargets.remove(npc);
                citizens.remove(npc);
            }
        }
        boolean done = morningTargets.isEmpty() && morningTicks >= 40;
        if (done || morningTicks >= 320) {
            cancelMorningTask();
            despawnWalkers();
            morningTargets.clear();
            showParticipants(players);
            cancelCameraTask();
            for (Player player : players) {
                if (!player.isOnline()) {
                    continue;
                }
                seatPlayer(player);
                player.setAllowFlight(false);
                player.setFlying(false);
                player.setInvulnerable(false);
            }
            cameraPlayers = List.of();
            onDone.run();
        }
    }

    private void seatPlayer(Player player) {
        if (player.isSleeping()) {
            player.wakeup(true);
        }
        Location seat = morningSeats.remove(player.getUniqueId());
        if (seat == null) {
            Integer houseId = houseAssignments.get(player.getUniqueId());
            if (houseId != null) {
                seat = config.seat(houseId);
            }
        }
        if (seat != null) {
            player.teleport(seat);
            plugin.seats().sit(player, seat, config.seatOffset());
        } else if (config.center() != null) {
            player.teleport(config.center());
        }
    }

    private void cancelMorningTask() {
        if (morningTask != null) {
            morningTask.cancel();
            morningTask = null;
        }
    }

    /* --------------------------------------------------------------- NPCs */

    private void spawnWalkers(Collection<Player> players) {
        if (!citizens.available()) {
            plugin.getLogger().warning("Citizens nicht installiert – Kino läuft ohne laufende Skins.");
            return;
        }
        Location center = config.center();
        int index = 0;
        int total = Math.max(1, players.size());

        for (Player player : players) {
            Integer houseId = houseAssignments.get(player.getUniqueId());
            if (houseId == null) {
                continue;
            }
            ConfigStore.House house = config.house(houseId);
            Location target = house.door != null ? house.door : house.spawn;
            if (target == null) {
                continue;
            }

            Location seat = config.seat(houseId);
            if (seat != null) {
                // Sitzender NPC; nach 0,5 s durch einen frischen laufenden NPC ersetzen.
                NPC sitting = citizens.spawnSitting(player, seat);
                activeNpcs.add(sitting);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    activeNpcs.remove(sitting);
                    citizens.remove(sitting);
                    NPC walker = citizens.spawnWalker(player, seat, house.path, target, 1.8);
                    activeNpcs.add(walker);
                    walkerHouses.put(walker, houseId);
                    anyWalker = true;
                }, 10L);
            } else if (center != null) {
                double angle = (Math.PI * 2 / total) * index;
                Location spawn = center.clone().add(Math.cos(angle) * 2.5, 0, Math.sin(angle) * 2.5);
                spawn.setDirection(target.toVector().subtract(spawn.toVector()));
                NPC walker = citizens.spawnWalker(player, spawn, house.path, target, 1.8);
                activeNpcs.add(walker);
                walkerHouses.put(walker, houseId);
                anyWalker = true;
            }
            index++;
        }
    }

    private void startArrivalWatch() {
        if (arrivalTask != null) {
            return;
        }
        arrivalTicks = 0;
        arrivalTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tickArrivals, 0L, 5L);
    }

    private void tickArrivals() {
        arrivalTicks += 5;
        for (NPC npc : new ArrayList<>(walkerHouses.keySet())) {
            Integer houseId = walkerHouses.get(npc);
            ConfigStore.House house = houseId != null ? config.house(houseId) : null;
            Location target = house == null ? null : (house.door != null ? house.door : house.spawn);

            boolean arrived;
            if (npc == null || !npc.isSpawned() || npc.getEntity() == null || target == null) {
                arrived = true;
            } else {
                arrived = npc.getEntity().getLocation().distanceSquared(target) <= 9.0;
            }

            if (arrived) {
                walkerHouses.remove(npc);
                if (houseId != null) {
                    openDoor(houseId);
                }
                Bukkit.getScheduler().runTaskLater(plugin, () -> citizens.remove(npc), 12L);
            }
        }

        boolean allArrived = anyWalker && walkerHouses.isEmpty() && arrivalTicks >= 60;
        boolean timeout = arrivalTicks >= 400;
        if (allArrived || timeout) {
            cancelArrivalWatch();
            finishCinematic(cameraPlayers);
        }
    }

    private void cancelArrivalWatch() {
        if (arrivalTask != null) {
            arrivalTask.cancel();
            arrivalTask = null;
        }
    }

    private void despawnWalkers() {
        for (NPC npc : activeNpcs) {
            citizens.remove(npc);
        }
        activeNpcs.clear();
    }

    /* -------------------------------------------------------------- Türen */

    public void openDoors(Collection<Player> players) {
        for (Player player : players) {
            Integer houseId = houseAssignments.get(player.getUniqueId());
            if (houseId != null) {
                openDoor(houseId);
            }
        }
    }

    public void openDoor(int houseId) {
        ConfigStore.House house = config.house(houseId);
        if (house.door == null) {
            return;
        }
        setDoor(house.door, true);
        house.door.getWorld().playSound(house.door, Sound.BLOCK_WOODEN_DOOR_OPEN, SoundCategory.BLOCKS, 1f, 1f);
    }

    public void closeDoor(int houseId) {
        ConfigStore.House house = config.house(houseId);
        if (house.door == null) {
            return;
        }
        setDoor(house.door, false);
        house.door.getWorld().playSound(house.door, Sound.BLOCK_WOODEN_DOOR_CLOSE, SoundCategory.BLOCKS, 1f, 1f);
    }

    private void setDoor(Location location, boolean open) {
        Block block = location.getBlock();
        if (!(block.getBlockData() instanceof Door door)) {
            return;
        }
        setDoorHalf(block, open);
        Block other = door.getHalf() == Door.Half.TOP
                ? block.getRelative(BlockFace.DOWN)
                : block.getRelative(BlockFace.UP);
        setDoorHalf(other, open);
    }

    private void setDoorHalf(Block block, boolean open) {
        BlockData data = block.getBlockData();
        if (data instanceof Door door) {
            door.setOpen(open);
            block.setBlockData(door, false);
        }
    }

    /* -------------------------------------------------------------- Betten */

    /**
     * Kamera aus, Spieler ins Bett (oder Haus-Spawn), danach Flug deaktivieren.
     * Wichtig: erst teleportieren/schlafen, dann Flug aus – sonst fallen Spieler aus der Luft.
     */
    private void finishCinematic(Collection<Player> players) {
        showParticipants(players);
        cancelArrivalWatch();
        despawnWalkers();
        walkerHouses.clear();
        anyWalker = false;
        cancelCameraTask();

        for (Player player : players) {
            if (!player.isOnline()) {
                continue;
            }
            Integer houseId = houseAssignments.get(player.getUniqueId());
            if (houseId != null) {
                ConfigStore.House house = config.house(houseId);
                boolean slept = sleepIn(player, houseId);
                // Tür schließen, sobald der Spieler im Bett liegt.
                Bukkit.getScheduler().runTaskLater(plugin, () -> closeDoor(houseId), 15L);
                if (!slept) {
                    if (house.spawn != null) {
                        player.teleport(house.spawn);
                    } else if (config.center() != null) {
                        player.teleport(config.center());
                    }
                }
            } else if (config.center() != null) {
                player.teleport(config.center());
            }
        }

        for (Player player : players) {
            if (player.isOnline()) {
                player.setAllowFlight(false);
                player.setFlying(false);
                player.setInvulnerable(false);
            }
        }
        cameraPlayers = List.of();
        plugin.gameManager().onCinematicFinished();
    }

    private void cancelCameraTask() {
        if (cameraTask != null) {
            cameraTask.cancel();
            cameraTask = null;
        }
    }

    /** Bett immer auf das Kopf-Teil + Blockmitte normalisieren. */
    public Location normalizeBed(Location location) {
        Block block = location.getBlock();
        if (!(block.getBlockData() instanceof Bed bed)) {
            return location;
        }
        if (bed.getPart() == Bed.Part.FOOT) {
            block = block.getRelative(bed.getFacing());
        }
        return block.getLocation().add(0.5, 0, 0.5);
    }

    public boolean sleepIn(Player player, int houseId) {
        ConfigStore.House house = config.house(houseId);
        if (house.bed == null) {
            if (house.spawn != null) {
                player.teleport(house.spawn);
            }
            player.sendMessage(Component.text("Haus " + houseId + " hat kein Bett (/ww setbed).", NamedTextColor.RED));
            return false;
        }

        Location bedLocation = normalizeBed(house.bed);
        boolean slept = player.sleep(bedLocation, true);
        if (slept) {
            lockedBeds.put(player.getUniqueId(), house.bed.clone());
            spawnBedMarker(player, bedLocation);
        } else {
            if (house.spawn != null) {
                player.teleport(house.spawn);
            }
            player.sendMessage(Component.text("Schlafen in Haus " + houseId + " fehlgeschlagen (Bett/Tageszeit).", NamedTextColor.RED));
        }
        return slept;
    }

    /** Große, unsichtbare Trefferfläche über dem Bett (Slime-Marker). */
    private void spawnBedMarker(Player player, Location bedLocation) {
        removeBedMarker(player);
        Location at = bedLocation.clone().add(0, 0.6, 0);
        org.bukkit.entity.Slime marker = at.getWorld().spawn(at, org.bukkit.entity.Slime.class, slime -> {
            slime.setSize(4);
            slime.setInvisible(true);
            slime.setSilent(true);
            slime.setAI(false);
            slime.setGravity(false);
            slime.setCollidable(false);
            slime.setPersistent(false);
            slime.setRemoveWhenFarAway(false);
            slime.setCustomName("ww_bed_marker");
            slime.setCustomNameVisible(false);
        });
        bedMarkers.put(marker.getUniqueId(), player.getUniqueId());
        playerMarkers.put(player.getUniqueId(), marker.getUniqueId());
    }

    /** Spieler hinter einem Bett-Marker (oder null). */
    public Player markerVictim(org.bukkit.entity.Entity entity) {
        UUID victimId = bedMarkers.get(entity.getUniqueId());
        if (victimId == null) {
            return null;
        }
        Player player = Bukkit.getPlayer(victimId);
        return player != null && player.isOnline() ? player : null;
    }

    public void removeBedMarker(Player player) {
        UUID markerId = playerMarkers.remove(player.getUniqueId());
        if (markerId != null) {
            bedMarkers.remove(markerId);
            org.bukkit.entity.Entity entity = Bukkit.getEntity(markerId);
            if (entity != null) {
                entity.remove();
            }
        }
    }

    public void removeAllBedMarkers() {
        for (UUID markerId : new ArrayList<>(bedMarkers.keySet())) {
            org.bukkit.entity.Entity entity = Bukkit.getEntity(markerId);
            if (entity != null) {
                entity.remove();
            }
        }
        bedMarkers.clear();
        playerMarkers.clear();
    }

    public boolean isBedLocked(Player player) {
        return lockedBeds.containsKey(player.getUniqueId());
    }

    public void wakeAll() {
        for (UUID uuid : new ArrayList<>(lockedBeds.keySet())) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                if (player.isSleeping()) {
                    player.wakeup(true);
                }
                player.setGameMode(GameMode.ADVENTURE);
            }
        }
        lockedBeds.clear();
        removeAllBedMarkers();

        // Sicherheitsnetz: auch alle anderen Schlafenden wecken.
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isSleeping()) {
                player.wakeup(true);
            }
        }
    }

    public void wake(Player player) {
        lockedBeds.remove(player.getUniqueId());
        removeBedMarker(player);
        if (player.isSleeping()) {
            player.wakeup(true);
        }
    }

    /** Wird periodisch aufgerufen: hält schlafende Spieler im Bett. */
    public void enforceSleep() {
        for (Map.Entry<UUID, Location> entry : new HashMap<>(lockedBeds).entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (!player.isSleeping()) {
                player.teleport(entry.getValue());
                player.sleep(entry.getValue(), true);
            }
        }
    }

    /* --------------------------------------------------------------- Tests */

    public NPC testWalker(Player skinOwner, int houseId) {
        ConfigStore.House house = config.house(houseId);
        Location center = config.center();
        Location target = house.door != null ? house.door : house.spawn;
        if (center == null || target == null) {
            return null;
        }
        Location spawn = center.clone().add(0, 0, 0);
        spawn.setDirection(target.toVector().subtract(spawn.toVector()));
        NPC npc = citizens.spawnWalker(skinOwner, spawn, house.path, target, 2.0);
        activeNpcs.add(npc);
        return npc;
    }

    public void testCamera(Player player, int ticks) {
        Location cam = cameraLocation();
        if (cam == null) {
            player.sendMessage(Component.text("Kein Mittelpunkt/Kamera gesetzt.", NamedTextColor.RED));
            return;
        }
        Location returnTo = player.getLocation().clone();
        player.setGameMode(GameMode.ADVENTURE);
        player.setAllowFlight(true);
        player.setFlying(true);
        cameraEnforceTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (player.isOnline()) {
                player.teleport(cam);
            }
        }, 0L, 1L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (cameraEnforceTask != null) {
                cameraEnforceTask.cancel();
                cameraEnforceTask = null;
            }
            if (player.isOnline()) {
                player.teleport(returnTo);
                player.setAllowFlight(false);
                player.setFlying(false);
            }
        }, ticks);
    }

    public Map<UUID, Integer> assignments() {
        return houseAssignments;
    }

    /** Bricht Kino, Kamera, Schlaf-Zwang und NPCs sofort ab. */
    public void stopAll() {
        for (BukkitTask task : new ArrayList<>(pendingTasks)) {
            task.cancel();
        }
        pendingTasks.clear();
        if (cameraEnforceTask != null) {
            cameraEnforceTask.cancel();
            cameraEnforceTask = null;
        }
        cancelMorningTask();
        morningTargets.clear();
        morningSeats.clear();
        removeAllBedMarkers();
        despawnWalkers();
        stopCameraLock();
        showParticipants(Bukkit.getOnlinePlayers());
        wakeAll();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isFlying() || player.getAllowFlight()) {
                Location safe = config.center() != null ? config.center() : player.getWorld().getSpawnLocation();
                player.teleport(safe);
            }
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setInvulnerable(false);
        }
        for (Integer houseId : config.houses().keySet()) {
            closeDoor(houseId);
        }
        houseAssignments.clear();
        if (config.center() != null && config.center().getWorld() != null) {
            setDay(config.center().getWorld());
        }
    }
}
