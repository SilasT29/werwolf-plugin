package de.silas.werwolf;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.PermissionAttachment;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rundenkern: Zustände, Countdown, Rollen- und Hausverteilung.
 * Nachtaktionen/Tag/Abstimmung folgen in M2/M3.
 */
public class GameManager {

    public enum State {
        LOBBY, STARTING, DAY, VOTE, CINEMATIC, NIGHT, END
    }

    public static final int MIN_PLAYERS = 4;
    public static final int MAX_PLAYERS = 20;

    private final WerwolfPlugin plugin;

    private State state = State.LOBBY;
    private final Map<UUID, Role> roles = new HashMap<>();
    private final Map<UUID, Integer> houses = new LinkedHashMap<>();
    private final Set<UUID> alive = new HashSet<>();

    private final Map<UUID, UUID> wolfTargets = new HashMap<>();
    private final Map<UUID, PermissionAttachment> voicePermissions = new HashMap<>();
    private BukkitTask autoNightTask;
    private BukkitTask nightTimer;
    private int nightSeconds;
    private BukkitTask seerTimer;
    private int seerSeconds;
    private int day = 1;

    private UUID pendingVictim;
    private boolean victimHealed;
    private UUID poisonVictim;
    private boolean healUsed;
    private boolean poisonUsed;
    private boolean poisonSelecting;
    private BukkitTask witchTimer;
    private int witchSeconds;

    private UUID loverA;
    private UUID loverB;
    private boolean amorSelecting;
    private UUID amorFirst;
    private final List<UUID> amorSelected = new ArrayList<>();
    private BukkitTask amorTimer;
    private int amorSeconds;
    private BukkitTask hunterTimer;
    private int hunterSeconds;
    private boolean hunterOpen;
    private BukkitTask voteCountdownTask;
    private int voteCountdown;
    private BukkitTask nightCountdownTask;
    private int nightCountdown;

    private UUID mayorId;
    private final Map<UUID, UUID> mayorVotes = new HashMap<>();
    private boolean mayorElectionActive;
    private BukkitTask mayorTimer;
    private int mayorSeconds;
    private boolean mayorChoosingSuccessor;

    private UUID visitTarget;
    private boolean slutSelecting;
    private BukkitTask slutTimer;
    private int slutSeconds;
    private boolean narratorEnabled;
    private UUID narratorId;
    private String narratorName;

    public GameManager(WerwolfPlugin plugin) {
        this.plugin = plugin;
    }

    public State state() {
        return state;
    }

    public boolean isRunning() {
        return state != State.LOBBY;
    }

    public Map<UUID, Role> roles() {
        return roles;
    }

    public Map<UUID, Integer> houses() {
        return houses;
    }

    public Set<UUID> alive() {
        return alive;
    }

    public Role roleOf(Player player) {
        return roles.get(player.getUniqueId());
    }

    public int houseOf(Player player) {
        return houses.getOrDefault(player.getUniqueId(), 0);
    }

    /* --------------------------------------------------------------- Start */

    public void setNarrator(Player player) {
        this.narratorId = player.getUniqueId();
        this.narratorName = player.getName();
    }

    public void clearNarrator() {
        this.narratorId = null;
        this.narratorName = null;
    }

    public UUID narratorId() {
        return narratorId;
    }

    public String narratorName() {
        return narratorName;
    }

    public int day() {
        return day;
    }

    /** Erzähler anwesend? Nur dann steuert er den Ablauf, sonst läuft alles automatisch. */
    public boolean narratorActive() {
        return narratorEnabled && narratorId != null && Bukkit.getPlayer(narratorId) != null;
    }

    public UUID mayorId() {
        return mayorId;
    }

    public boolean mayorElectionActive() {
        return mayorElectionActive;
    }

    public boolean mayorChoosingSuccessor() {
        return mayorChoosingSuccessor;
    }

    /** Plant die nächste Abstimmung und zeigt den Countdown in der Actionbar. */
    public void scheduleVote(long delayTicks) {
        cancelVoteCountdown();
        voteCountdown = (int) Math.max(1, delayTicks / 20);
        voteCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.DAY || plugin.vote().isActive()) {
                cancelVoteCountdown();
                return;
            }
            if (voteCountdown <= 0) {
                cancelVoteCountdown();
                plugin.vote().start();
                return;
            }
            Component bar = Component.text("Abstimmung in ", NamedTextColor.YELLOW)
                    .append(Component.text(voteCountdown + " s", NamedTextColor.WHITE));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (alive.contains(player.getUniqueId())) {
                    player.sendActionBar(bar);
                }
            }
            voteCountdown--;
        }, 0L, 20L);
    }

    /** Plant den Nachtbeginn (Tag 1 ohne Abstimmung). */
    public void scheduleNight(long delayTicks) {
        cancelNightCountdown();
        nightCountdown = (int) Math.max(1, delayTicks / 20);
        nightCountdownTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.DAY) {
                cancelNightCountdown();
                return;
            }
            if (nightCountdown <= 0) {
                cancelNightCountdown();
                startNight();
                return;
            }
            Component bar = Component.text("Nacht in ", NamedTextColor.DARK_RED)
                    .append(Component.text(nightCountdown + " s", NamedTextColor.WHITE));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (alive.contains(player.getUniqueId())) {
                    player.sendActionBar(bar);
                }
            }
            nightCountdown--;
        }, 0L, 20L);
    }

    public void cancelNightCountdown() {
        if (nightCountdownTask != null) {
            nightCountdownTask.cancel();
            nightCountdownTask = null;
        }
    }

    public void cancelVoteCountdown() {
        if (voteCountdownTask != null) {
            voteCountdownTask.cancel();
            voteCountdownTask = null;
        }
    }

    /** Wahl am ersten Tag: alle Lebenden wählen einen Bürgermeister. */
    public void startMayorElection() {
        if (state != State.DAY || alive.size() < 2 || mayorId != null) {
            return;
        }
        mayorElectionActive = true;
        mayorVotes.clear();
        Script.say(Script.MAYOR);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (alive.contains(player.getUniqueId())) {
                openMayorMenu(player);
            }
        }

        if (mayorTimer != null) {
            mayorTimer.cancel();
        }
        mayorSeconds = 30;
        mayorTimer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!mayorElectionActive || state == State.LOBBY || state == State.END) {
                cancelMayorTimer();
                return;
            }
            if (mayorSeconds <= 0) {
                cancelMayorTimer();
                finishMayorElection();
                return;
            }
            Component bar = Component.text("Bürgermeisterwahl: " + mayorSeconds + " s", NamedTextColor.GOLD);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (alive.contains(player.getUniqueId())) {
                    player.sendActionBar(bar);
                }
            }
            mayorSeconds--;
        }, 0L, 20L);
    }

    private void openMayorMenu(Player viewer) {
        MayorMenu menu = new MayorMenu();
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(menu, 54,
                Component.text("Bürgermeisterwahl", NamedTextColor.GOLD));
        menu.inventory(inv);

        ItemStack filler = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        if (filler.getItemMeta() != null) {
            var meta = filler.getItemMeta();
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        int index = 0;
        for (UUID uuid : alive) {
            if (index >= MayorMenu.HEAD_SLOTS.length) {
                break;
            }
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) {
                continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                skull.setOwningPlayer(target);
                skull.displayName(Component.text(target.getName(), NamedTextColor.WHITE));
                List<Component> lore = new ArrayList<>();
                lore.add(Component.text("Stimmen: " + countMayorVotes(uuid), NamedTextColor.GRAY));
                if (uuid.equals(mayorVotes.get(viewer.getUniqueId()))) {
                    lore.add(Component.text("Deine Stimme", NamedTextColor.GREEN));
                }
                skull.lore(lore);
                head.setItemMeta(skull);
            }
            inv.setItem(MayorMenu.HEAD_SLOTS[index], head);
            menu.heads().put(MayorMenu.HEAD_SLOTS[index], uuid);
            index++;
        }
        viewer.openInventory(inv);
    }

    private int countMayorVotes(UUID target) {
        int count = 0;
        for (UUID vote : mayorVotes.values()) {
            if (vote.equals(target)) {
                count++;
            }
        }
        return count;
    }

    public void mayorVote(Player voter, UUID targetId) {
        if (!mayorElectionActive || !alive.contains(voter.getUniqueId())) {
            return;
        }
        mayorVotes.put(voter.getUniqueId(), targetId);
        refreshMayorMenus();
    }

    /** Aktualisiert die offenen Wahl-Menüs, ohne sie zu schließen. */
    private void refreshMayorMenus() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            org.bukkit.inventory.Inventory top = online.getOpenInventory().getTopInventory();
            if (top.getHolder() instanceof MayorMenu menu) {
                for (Map.Entry<Integer, UUID> entry : menu.heads().entrySet()) {
                    Player target = Bukkit.getPlayer(entry.getValue());
                    if (target == null) {
                        continue;
                    }
                    ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                    if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                        skull.setOwningPlayer(target);
                        skull.displayName(Component.text(target.getName(), NamedTextColor.WHITE));
                        List<Component> lore = new ArrayList<>();
                        lore.add(Component.text("Stimmen: " + countMayorVotes(entry.getValue()), NamedTextColor.GRAY));
                        if (entry.getValue().equals(mayorVotes.get(online.getUniqueId()))) {
                            lore.add(Component.text("Deine Stimme", NamedTextColor.GREEN));
                        }
                        skull.lore(lore);
                        head.setItemMeta(skull);
                    }
                    top.setItem(entry.getKey(), head);
                }
            }
        }
    }

    private void finishMayorElection() {
        mayorElectionActive = false;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof MayorMenu) {
                online.closeInventory();
            }
        }
        if (mayorVotes.isEmpty()) {
            Bukkit.broadcast(Component.text("Niemand hat gewählt – es gibt keinen Bürgermeister.", NamedTextColor.GRAY));
            return;
        }
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        for (UUID target : mayorVotes.values()) {
            counts.merge(target, 1, Integer::sum);
        }
        int max = java.util.Collections.max(counts.values());
        List<UUID> top = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == max) {
                top.add(entry.getKey());
            }
        }
        UUID winnerId = top.size() == 1 ? top.get(0) : top.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(top.size()));
        mayorId = winnerId;

        Player winner = Bukkit.getPlayer(winnerId);
        String name = winner != null ? winner.getName() : "?";
        Bukkit.broadcast(Component.text(name + " ist der neue Bürgermeister! (Doppelte Stimme)",
                NamedTextColor.GOLD));
        plugin.hud().updateAll();
    }

    /** Stirbt der Bürgermeister, wählt er einen Nachfolger. */
    public void startMayorSuccessor(Player mayor) {
        if (state == State.LOBBY || state == State.END || alive.isEmpty()) {
            return;
        }
        mayorChoosingSuccessor = true;
        MayorMenu menu = new MayorMenu();
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(menu, 54,
                Component.text("Wähle deinen Nachfolger", NamedTextColor.GOLD));
        menu.inventory(inv);
        ItemStack filler = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        if (filler.getItemMeta() != null) {
            var meta = filler.getItemMeta();
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }
        int index = 0;
        for (UUID uuid : alive) {
            if (index >= MayorMenu.HEAD_SLOTS.length) {
                break;
            }
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) {
                continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                skull.setOwningPlayer(target);
                skull.displayName(Component.text(target.getName(), NamedTextColor.WHITE));
                head.setItemMeta(skull);
            }
            inv.setItem(MayorMenu.HEAD_SLOTS[index], head);
            menu.heads().put(MayorMenu.HEAD_SLOTS[index], uuid);
            index++;
        }
        mayor.openInventory(inv);
    }

    public void mayorSuccessorPick(Player mayor, UUID targetId) {
        if (!mayorChoosingSuccessor) {
            return;
        }
        mayorChoosingSuccessor = false;
        mayor.closeInventory();
        mayorId = targetId;
        Player target = Bukkit.getPlayer(targetId);
        Bukkit.broadcast(Component.text((target != null ? target.getName() : "?")
                + " ist der neue Bürgermeister!", NamedTextColor.GOLD));
        plugin.hud().updateAll();
    }

    private void cancelMayorTimer() {
        if (mayorTimer != null) {
            mayorTimer.cancel();
            mayorTimer = null;
        }
    }

    public boolean isNarrator(UUID uuid) {
        return narratorId != null && narratorId.equals(uuid);
    }

    /** Voice-Regel aktiv: Runde läuft, Erzähler an und gesetzt. */
    public boolean isNarratorVoiceActive() {
        return narratorEnabled && narratorId != null && state != State.LOBBY;
    }

    public boolean narratorEnabled() {
        return narratorEnabled;
    }

    public void requestStart(Player admin, Map<Role, Integer> counts, boolean narratorEnabled, UUID narratorId) {
        this.narratorEnabled = narratorEnabled;
        this.narratorId = narratorId;
        if (narratorId != null) {
            Player narrator = Bukkit.getPlayer(narratorId);
            this.narratorName = narrator != null ? narrator.getName() : null;
        }
        if (state != State.LOBBY) {
            admin.sendMessage(Component.text("Es läuft bereits eine Runde.", NamedTextColor.RED));
            return;
        }
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        int assigned = counts.values().stream().mapToInt(Integer::intValue).sum();

        if (players.size() < MIN_PLAYERS) {
            admin.sendMessage(Component.text("Mindestens " + MIN_PLAYERS + " Spieler nötig (aktuell " + players.size() + ").",
                    NamedTextColor.RED));
            return;
        }
        if (players.size() > MAX_PLAYERS) {
            admin.sendMessage(Component.text("Maximal " + MAX_PLAYERS + " Spieler erlaubt (aktuell " + players.size() + ").",
                    NamedTextColor.RED));
            return;
        }
        if (narratorEnabled && narratorId == null) {
            admin.sendMessage(Component.text("Kein Erzähler gewählt – /ww narrator <Name>.", NamedTextColor.RED));
            return;
        }
        int participants = players.size() - (narratorEnabled ? 1 : 0);
        if (assigned != participants) {
            admin.sendMessage(Component.text("Rollen (" + assigned + ") ≠ Spieler ohne Erzähler (" + participants + ").",
                    NamedTextColor.RED));
            return;
        }
        if (counts.getOrDefault(Role.WERWOLF, 0) < 1) {
            admin.sendMessage(Component.text("Mindestens ein Werwolf nötig.", NamedTextColor.RED));
            return;
        }

        Map<Role, Integer> snapshot = new java.util.EnumMap<>(counts);
        state = State.STARTING;
        beginRound(admin, snapshot);
    }

    private void beginRound(Player admin, Map<Role, Integer> counts) {
        // Beim Start ist immer Tag.
        if (plugin.config().center() != null && plugin.config().center().getWorld() != null) {
            plugin.cinematic().setDay(plugin.config().center().getWorld());
        }
        roles.clear();
        houses.clear();
        alive.clear();

        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        Player narrator = narratorEnabled && narratorId != null ? Bukkit.getPlayer(narratorId) : null;
        if (narrator != null) {
            players.remove(narrator);
        }
        for (Player player : players) {
            player.closeInventory();
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
        }

        // Rollenpool mischen und verteilen
        List<Role> pool = new ArrayList<>();
        for (Map.Entry<Role, Integer> entry : counts.entrySet()) {
            for (int i = 0; i < entry.getValue(); i++) {
                pool.add(entry.getKey());
            }
        }
        Collections.shuffle(pool);
        Collections.shuffle(players);

        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            roles.put(player.getUniqueId(), pool.get(i));
            alive.add(player.getUniqueId());
            plugin.getLogger().info("Rolle: " + player.getName() + " -> " + pool.get(i).name());
        }

        // Häuser 1..n auf die Spieler verteilen
        List<Integer> houseIds = new ArrayList<>(plugin.config().houses().keySet());
        houseIds.sort(Integer::compareTo);
        if (!houseIds.isEmpty()) {
            for (int i = 0; i < players.size(); i++) {
                houses.put(players.get(i).getUniqueId(), houseIds.get(i % houseIds.size()));
            }
            if (narrator != null) {
                houses.put(narrator.getUniqueId(), houseIds.get(houseIds.size() - 1));
            }
        }

        // Rolle privat mitteilen
        for (Player player : players) {
            Role role = roles.get(player.getUniqueId());
            int houseId = houses.getOrDefault(player.getUniqueId(), 0);
            player.sendMessage(Component.text("──────────────────", NamedTextColor.DARK_GRAY));
            player.sendMessage(Component.text("Deine Rolle: ", NamedTextColor.GRAY)
                    .append(Component.text(role.displayName(), role.color(), TextDecoration.BOLD)));
            player.sendMessage(Component.text(role.description(), NamedTextColor.GRAY));
            for (String line : role.guide()) {
                player.sendMessage(Component.text("▸ ", NamedTextColor.DARK_GRAY)
                        .append(Component.text(line, NamedTextColor.WHITE)));
            }
            player.sendMessage(Component.text("Dein Haus: ", NamedTextColor.GRAY)
                    .append(Component.text("#" + houseId, NamedTextColor.WHITE)));
            player.sendMessage(Component.text("──────────────────", NamedTextColor.DARK_GRAY));
        }

        state = State.STARTING;
        applyVoicePermissions();
        day = 1;
        healUsed = false;
        poisonUsed = false;
        mayorId = null;
        mayorElectionActive = false;
        mayorChoosingSuccessor = false;
        mayorVotes.clear();
        loverA = null;
        loverB = null;
        amorSelecting = false;
        amorFirst = null;
        poisonSelecting = false;
        pendingVictim = null;
        poisonVictim = null;
        victimHealed = false;
        plugin.getLogger().info("Runde gestartet von " + admin.getName() + ": " + counts);

        applyVoicePermissions();

        // 1) Alle auf ihre Sitze, Darkness + Startscreen
        for (Player player : players) {
            // Erst Darkness, damit der Teleport nicht auffällt.
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 20 * 8, 0, false, false, false));
            int houseId = houses.getOrDefault(player.getUniqueId(), 0);
            Location seat = plugin.config().seat(houseId);
            if (seat != null) {
                plugin.seats().sit(player, seat, plugin.config().seatOffset());
            } else {
                Location spawn = plugin.config().house(houseId).spawn;
                if (spawn != null) {
                    player.teleport(spawn);
                }
            }
            player.playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.2f);
            player.showTitle(Title.title(
                    Component.text("Das Spiel beginnt", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                    Component.text("Deine Rolle wird verteilt …", NamedTextColor.GRAY),
                    Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(2), Duration.ofMillis(600))));
        }

        // Erzähler auf Sitz 20
        if (narrator != null) {
            Location narratorSeat = plugin.config().seat(20);
            if (narratorSeat == null && !plugin.config().seats().isEmpty()) {
                narratorSeat = plugin.config().seat(Collections.max(plugin.config().seats().keySet()));
            }
            if (narratorSeat != null) {
                plugin.seats().sit(narrator, narratorSeat, plugin.config().seatOffset());
            }
            giveNarratorItems(narrator);
            narrator.sendMessage(Component.text("Du bist der Erzähler. Du sitzt auf Sitz 20, darfst als Einziger sprechen und siehst nachts alles aus der Kamera.",
                    NamedTextColor.GOLD));
        }

        // 2) Rolle als Titel
        plugin.hud().updateAll();

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : players) {
                if (!player.isOnline()) {
                    continue;
                }
                Role role = roles.get(player.getUniqueId());
                if (role == null) {
                    continue;
                }
                player.showTitle(Title.title(
                        Component.text(role.displayName(), role.color(), TextDecoration.BOLD),
                        Component.text(role.description(), NamedTextColor.GRAY),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofMillis(600))));
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            }
        }, 60L);

        // Ohne Erzähler: Nacht automatisch nach 5 Minuten.
        if (!narratorEnabled) {
            autoNightTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (state == State.DAY) {
                    startNight();
                }
            }, 20L * 60 * 5);
        }

        // 3) Tag: Darkness weg, Zeit auf Tag stellen
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : players) {
                if (player.isOnline()) {
                    player.removePotionEffect(PotionEffectType.DARKNESS);
                }
            }
            if (plugin.config().center() != null && plugin.config().center().getWorld() != null) {
                plugin.cinematic().setDay(plugin.config().center().getWorld());
            }
            state = State.DAY;
            Bukkit.broadcast(Component.text("Der Tag beginnt. Diskutiert und findet die Werwölfe!", NamedTextColor.YELLOW));
            if (!narratorActive()) {
                Script.story(plugin, Script.INTRO);
            }
            if (!narratorActive()) {
                scheduleNight(900L);
            }
        }, 140L);
    }

    /* ------------------------------------------------------------ Übergänge */

    /** Wird vom Kino aufgerufen, wenn alle Spieler im Bett liegen. */
    public void onCinematicFinished() {
        if (state != State.CINEMATIC) {
            return;
        }
        state = State.NIGHT;
        wolfTargets.clear();
        Script.say(Script.SLUT);
        if (narratorEnabled && narratorId != null) {
            Player narrator = Bukkit.getPlayer(narratorId);
            if (narrator != null) {
                plugin.cinematic().wake(narrator);
                Location cam = plugin.cinematic().cameraView();
                if (cam != null) {
                    narrator.teleport(cam);
                    narrator.setAllowFlight(true);
                    narrator.setFlying(true);
                }
                giveNarratorItems(narrator);
                narrator.sendMessage(Component.text("Du siehst die Nacht aus der Kamera. Du darfst sprechen.", NamedTextColor.GOLD));
            }
        }
        startSlutPhase();
    }

    /** Wölfe aufwecken (nach der Dorfschlampe). */
    private void beginWolfPhase() {
        if (state != State.NIGHT) {
            return;
        }
        Script.say(Script.WOLVES);
        startWolfTimer();
        for (Map.Entry<UUID, Role> entry : new ArrayList<>(roles.entrySet())) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            if (entry.getValue() == Role.WERWOLF) {
                wakeWolf(player);
            } else if (entry.getValue() == Role.EINZELGAENGER) {
                wakeSingle(player);
            }
        }
    }

    /** Werwolf aufwecken, Schwert geben, aus dem Bett holen. */
    private void wakeWolf(Player wolf) {
        plugin.cinematic().wake(wolf);
        int houseId = houseOf(wolf);
        Location spawn = plugin.config().house(houseId).spawn;
        if (spawn != null) {
            wolf.teleport(spawn);
        }
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemMeta meta = sword.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("Werwolfsklaue", NamedTextColor.DARK_RED));
            meta.setUnbreakable(true);
            sword.setItemMeta(meta);
        }
        wolf.getInventory().setItem(0, sword);
        wolf.sendMessage(Component.text("Du bist erwacht. Schleiche zu deinem Opfer und schlage zu – alle Wölfe müssen dasselbe Opfer treffen.",
                NamedTextColor.DARK_RED));
    }

    /** Blinzelmädchen: darf nachts frei herumlaufen, aber ohne Schwert und ohne Unsichtbarkeit. */
    private void wakeSingle(Player player) {
        plugin.cinematic().wake(player);
        int houseId = houseOf(player);
        Location spawn = plugin.config().house(houseId).spawn;
        if (spawn != null) {
            player.teleport(spawn);
        }
        player.sendMessage(Component.text("Du kannst dich frei bewegen – aber wenn dich ein Werwolf sieht, bist du entdeckt.",
                NamedTextColor.AQUA));
    }

    /** Ein Werwolf hat einen schlafenden Spieler getroffen. */
    public void wolfHit(Player wolf, Player victim) {
        if (state != State.NIGHT || roles.get(wolf.getUniqueId()) != Role.WERWOLF) {
            return;
        }
        wolfTargets.put(wolf.getUniqueId(), victim.getUniqueId());

        List<UUID> wolves = aliveWolves();
        int hits = wolfTargets.size();
        if (hits < wolves.size()) {
            wolf.sendActionBar(Component.text("Opfer: " + victim.getName() + " (" + hits + "/" + wolves.size() + " Wölfe)",
                    NamedTextColor.RED));
            return;
        }

        Set<UUID> targets = new HashSet<>(wolfTargets.values());
        if (targets.size() != 1) {
            for (UUID id : wolves) {
                Player w = Bukkit.getPlayer(id);
                if (w != null) {
                    w.sendActionBar(Component.text("Ihr müsst euch auf dasselbe Opfer einigen!", NamedTextColor.RED));
                }
            }
            wolfTargets.clear();
            return;
        }

        Player target = Bukkit.getPlayer(targets.iterator().next());
        wolfTargets.clear();
        if (target != null) {
            pendingVictim = target.getUniqueId();
            cancelNightTimer();
            Bukkit.broadcast(Component.text("Die Werwölfe haben ihr Opfer gewählt.", NamedTextColor.DARK_RED));
            Bukkit.getScheduler().runTaskLater(plugin, this::sendWolvesToBed, 40L);
            Bukkit.getScheduler().runTaskLater(plugin, this::startPostWolfPhase, 80L);
        }
    }

    /** Blinzelmädchen wurde wach von einem Wolf erwischt. */
    public void killCaught(Player victim) {
        if (state != State.NIGHT) {
            return;
        }
        cancelNightTimer();
        plugin.cinematic().wake(victim);
        victim.setHealth(0);
        Bukkit.broadcast(Component.text(victim.getName() + " wurde von einem Werwolf erwischt.", NamedTextColor.DARK_RED));
        Bukkit.getScheduler().runTaskLater(plugin, this::sendWolvesToBed, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, this::startSeerPhase, 80L);
        Bukkit.getScheduler().runTaskLater(plugin, this::checkWin, 5L);
    }

    /** 60-Sekunden-Timer für die Wölfe, Anzeige in der Actionbar. */
    private void startWolfTimer() {
        cancelNightTimer();
        nightSeconds = 60;
        nightTimer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.NIGHT) {
                cancelNightTimer();
                return;
            }
            if (nightSeconds <= 0) {
                cancelNightTimer();
                Bukkit.broadcast(Component.text("Die Werwölfe konnten sich nicht einigen – niemand stirbt.",
                        NamedTextColor.GRAY));
                cancelNightTimer();
                Bukkit.getScheduler().runTaskLater(plugin, this::startPostWolfPhase, 40L);
                return;
            }
            Component bar = Component.text("Werwölfe: ", NamedTextColor.DARK_RED)
                    .append(Component.text(nightSeconds + " s", NamedTextColor.YELLOW));
            for (UUID id : aliveWolves()) {
                Player wolf = Bukkit.getPlayer(id);
                if (wolf != null) {
                    wolf.sendActionBar(bar);
                }
            }
            nightSeconds--;
        }, 0L, 20L);
    }

    private void cancelNightTimer() {
        if (nightTimer != null) {
            nightTimer.cancel();
            nightTimer = null;
        }
    }

    /** Wölfe zurück in ihre Betten, Schwert weg. */
    private void sendWolvesToBed() {
        for (UUID id : aliveWolves()) {
            Player wolf = Bukkit.getPlayer(id);
            if (wolf == null || !wolf.isOnline()) {
                continue;
            }
            wolf.getInventory().remove(Material.IRON_SWORD);
            int houseId = houseOf(wolf);
            ConfigStore.House house = plugin.config().house(houseId);
            if (house.bed != null) {
                plugin.cinematic().sleepIn(wolf, houseId);
            }
        }
    }

    private void killByWolves(Player victim) {
        cancelNightTimer();
        plugin.cinematic().wake(victim);
        victim.setHealth(0);
        Bukkit.broadcast(Component.text(victim.getName() + " wurde in der Nacht von den Werwölfen getötet.",
                NamedTextColor.DARK_RED));
        Bukkit.getScheduler().runTaskLater(plugin, this::sendWolvesToBed, 40L);
        Bukkit.getScheduler().runTaskLater(plugin, this::startSeerPhase, 80L);
        Bukkit.getScheduler().runTaskLater(plugin, this::checkWin, 5L);
    }

    /** Nach den Wölfen: in Nacht 1 Amor, sonst direkt Seherin. */
    private void startPostWolfPhase() {
        if (state != State.NIGHT) {
            return;
        }
        if (day == 1 && alivePlayerWithRole(Role.AMOR) != null) {
            startAmorPhase();
        } else {
            startSeerPhase();
        }
    }

    public boolean amorSelecting() {
        return amorSelecting;
    }

    public UUID lovePartnerOf(UUID uuid) {
        if (uuid.equals(loverA)) {
            return loverB;
        }
        if (uuid.equals(loverB)) {
            return loverA;
        }
        return null;
    }

    public void startAmorPhase() {
        Player amor = alivePlayerWithRole(Role.AMOR);
        if (amor == null) {
            startSeerPhase();
            return;
        }
        plugin.cinematic().wake(amor);
        int houseId = houseOf(amor);
        if (plugin.config().house(houseId).spawn != null) {
            amor.teleport(plugin.config().house(houseId).spawn);
        }
        amor.sendMessage(Component.text("Du bist Amor. Verkupple zwei Spieler – wähle nacheinander zwei Köpfe.",
                NamedTextColor.LIGHT_PURPLE));
        amorSelecting = true;
        amorFirst = null;
        amorSelected.clear();
        openAmorMenu(amor);

        cancelAmorTimer();
        amorSeconds = 45;
        amorTimer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.NIGHT) {
                cancelAmorTimer();
                return;
            }
            if (amorSeconds <= 0) {
                cancelAmorTimer();
                amor.closeInventory();
                amorSelecting = false;
                amorFirst = null;
                amorSelected.clear();
                amor.sendMessage(Component.text("Du hast niemanden verkuppelt.", NamedTextColor.GRAY));
                Bukkit.getScheduler().runTaskLater(plugin, this::startSeerPhase, 60L);
                return;
            }
            amor.sendActionBar(Component.text("Amor: ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(amorSeconds + " s", NamedTextColor.YELLOW)));
            amorSeconds--;
        }, 0L, 20L);
    }

    private void openAmorMenu(Player amor) {
        AmorMenu menu = new AmorMenu();
        String title = "Amor – " + amorSelected.size() + "/2 gewählt";
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(menu, 54,
                Component.text(title, NamedTextColor.LIGHT_PURPLE));
        menu.inventory(inv);

        ItemStack filler = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        if (filler.getItemMeta() != null) {
            var meta = filler.getItemMeta();
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        int index = 0;
        for (UUID uuid : alive) {
            if (index >= AmorMenu.HEAD_SLOTS.length) {
                break;
            }
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) {
                continue;
            }
            boolean isSelected = amorSelected.contains(uuid);
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                skull.setOwningPlayer(target);
                skull.displayName(Component.text(target.getName(),
                        isSelected ? NamedTextColor.GREEN : NamedTextColor.WHITE,
                        isSelected ? net.kyori.adventure.text.format.TextDecoration.BOLD
                                : net.kyori.adventure.text.format.TextDecoration.ITALIC));
                java.util.List<Component> lore = new ArrayList<>();
                lore.add(Component.text(isSelected ? "✔ Ausgewählt" : "Klicken zum Auswählen",
                        isSelected ? NamedTextColor.GREEN : NamedTextColor.GRAY));
                skull.lore(lore);
                if (isSelected) {
                    skull.addEnchant(org.bukkit.enchantments.Enchantment.LUCK_OF_THE_SEA, 1, true);
                    skull.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
                }
                head.setItemMeta(skull);
            }
            inv.setItem(AmorMenu.HEAD_SLOTS[index], head);
            menu.heads().put(AmorMenu.HEAD_SLOTS[index], uuid);
            index++;
        }

        ItemStack confirm = new ItemStack(amorSelected.size() == 2 ? Material.LIME_CONCRETE : Material.GRAY_CONCRETE);
        if (confirm.getItemMeta() != null) {
            var meta = confirm.getItemMeta();
            meta.displayName(Component.text(amorSelected.size() == 2 ? "Bestätigen" : "Wähle zwei Personen",
                    amorSelected.size() == 2 ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                    net.kyori.adventure.text.format.TextDecoration.BOLD));
            confirm.setItemMeta(meta);
        }
        inv.setItem(AmorMenu.CONFIRM_SLOT, confirm);

        amor.openInventory(inv);
    }

    /** Menü erneut öffnen (z. B. nachdem es geschlossen wurde). */
    public void reopenAmor(Player amor) {
        if (amorSelecting) {
            openAmorMenu(amor);
        }
    }

    public void amorToggle(Player amor, UUID targetId) {
        if (!amorSelecting || roles.get(amor.getUniqueId()) != Role.AMOR) {
            return;
        }
        if (amorSelected.contains(targetId)) {
            amorSelected.remove(targetId);
        } else if (amorSelected.size() < 2) {
            amorSelected.add(targetId);
        } else {
            amor.sendMessage(Component.text("Es sind bereits zwei Personen ausgewählt.", NamedTextColor.RED));
            return;
        }
        openAmorMenu(amor);
    }

    public void amorConfirm(Player amor) {
        if (!amorSelecting || roles.get(amor.getUniqueId()) != Role.AMOR) {
            return;
        }
        if (amorSelected.size() != 2) {
            return;
        }

        cancelAmorTimer();
        amorSelecting = false;
        loverA = amorSelected.get(0);
        loverB = amorSelected.get(1);
        amorSelected.clear();
        amor.closeInventory();

        Player a = Bukkit.getPlayer(loverA);
        Player b = Bukkit.getPlayer(loverB);
        if (a != null && b != null) {
            a.sendMessage(Component.text("Du bist unsterblich verliebt in " + b.getName() + ".",
                    NamedTextColor.LIGHT_PURPLE));
            b.sendMessage(Component.text("Du bist unsterblich verliebt in " + a.getName() + ".",
                    NamedTextColor.LIGHT_PURPLE));
        }
        amor.sendMessage(Component.text("Die beiden sind jetzt ein Liebespaar.", NamedTextColor.LIGHT_PURPLE));
        Bukkit.getScheduler().runTaskLater(plugin, this::startSeerPhase, 60L);
    }

    private void cancelAmorTimer() {
        if (amorTimer != null) {
            amorTimer.cancel();
            amorTimer = null;
        }
    }

    /** Seherinnen-Phase: 45 s, Kopf anklicken, Rolle erfahren. */
    public void startSeerPhase() {
        if (state != State.NIGHT) {
            return;
        }
        Script.say(Script.SEER);
        Player seer = alivePlayerWithRole(Role.SEHER);
        if (seer == null) {
            startWitchPhase();
            return;
        }
        plugin.cinematic().wake(seer);
        int houseId = houseOf(seer);
        if (plugin.config().house(houseId).spawn != null) {
            seer.teleport(plugin.config().house(houseId).spawn);
        }
        seer.sendMessage(Component.text("Du bist die Seherin. Wähle einen Spieler, dessen Rolle du sehen willst.",
                NamedTextColor.AQUA));
        openSeerMenu(seer);

        cancelSeerTimer();
        seerSeconds = 45;
        seerTimer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.NIGHT) {
                cancelSeerTimer();
                return;
            }
            if (seerSeconds <= 0) {
                cancelSeerTimer();
                seer.closeInventory();
                seer.sendMessage(Component.text("Du hast niemanden geprüft.", NamedTextColor.GRAY));
                Bukkit.getScheduler().runTaskLater(plugin, this::startWitchPhase, 60L);
                return;
            }
            seer.sendActionBar(Component.text("Seherin: ", NamedTextColor.AQUA)
                    .append(Component.text(seerSeconds + " s", NamedTextColor.YELLOW)));
            seerSeconds--;
        }, 0L, 20L);
    }

    private void openSeerMenu(Player seer) {
        SeerMenu menu = new SeerMenu();
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(menu, 27,
                Component.text("Wen möchtest du prüfen?", NamedTextColor.DARK_GRAY));
        menu.inventory(inv);

        int slot = 0;
        for (UUID uuid : alive) {
            Player target = Bukkit.getPlayer(uuid);
            if (target == null || slot >= 27) {
                continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                skull.setOwningPlayer(target);
                skull.displayName(Component.text(target.getName(), NamedTextColor.WHITE));
                head.setItemMeta(skull);
            }
            inv.setItem(slot, head);
            menu.heads().put(slot, uuid);
            slot++;
        }
        seer.openInventory(inv);
    }

    /** Seherin hat einen Spieler gewählt. */
    public void seerPick(Player seer, UUID targetId) {
        if (roles.get(seer.getUniqueId()) != Role.SEHER) {
            return;
        }
        cancelSeerTimer();
        seer.closeInventory();

        Role role = roles.get(targetId);
        Player target = Bukkit.getPlayer(targetId);
        String name = target != null ? target.getName() : "?";
        if (role == null) {
            seer.sendMessage(Component.text(name + " hat keine Rolle.", NamedTextColor.GRAY));
        } else {
            seer.showTitle(net.kyori.adventure.title.Title.title(
                    Component.text(role.displayName(), role.color(), net.kyori.adventure.text.format.TextDecoration.BOLD),
                    Component.text(name, NamedTextColor.GRAY),
                    net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(200),
                            java.time.Duration.ofSeconds(2), java.time.Duration.ofMillis(400))));
            seer.sendMessage(Component.text(name + " ist ", NamedTextColor.GRAY)
                    .append(Component.text(role.displayName(), role.color())));
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::startWitchPhase, 60L);
    }

    private void cancelSeerTimer() {
        if (seerTimer != null) {
            seerTimer.cancel();
            seerTimer = null;
        }
    }

    private Player alivePlayerWithRole(Role role) {
        for (UUID id : alive) {
            if (roles.get(id) == role) {
                Player player = Bukkit.getPlayer(id);
                if (player != null && player.isOnline()) {
                    return player;
                }
            }
        }
        return null;
    }

    /* --------------------------------------------------------------- Hexe */

    public boolean healUsed() {
        return healUsed;
    }

    public boolean poisonUsed() {
        return poisonUsed;
    }

    public UUID pendingVictim() {
        return pendingVictim;
    }

    public boolean poisonSelecting() {
        return poisonSelecting;
    }

    public void startWitchPhase() {
        if (state != State.NIGHT) {
            return;
        }
        Script.say(Script.WITCH);
        Player witch = alivePlayerWithRole(Role.HEXE);
        if (witch == null) {
            Bukkit.getScheduler().runTaskLater(plugin, this::startDay, 40L);
            return;
        }
        plugin.cinematic().wake(witch);
        int houseId = houseOf(witch);
        if (plugin.config().house(houseId).spawn != null) {
            witch.teleport(plugin.config().house(houseId).spawn);
        }
        witch.sendMessage(Component.text("Du bist die Hexe. Heile das Opfer oder vergifte jemanden – je einmal pro Spiel.",
                NamedTextColor.LIGHT_PURPLE));
        openWitchMenu(witch);

        cancelWitchTimer();
        witchSeconds = 45;
        witchTimer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.NIGHT) {
                cancelWitchTimer();
                return;
            }
            if (witchSeconds <= 0) {
                cancelWitchTimer();
                witch.closeInventory();
                Bukkit.getScheduler().runTaskLater(plugin, this::startDay, 60L);
                return;
            }
            witch.sendActionBar(Component.text("Hexe: ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(witchSeconds + " s", NamedTextColor.YELLOW)));
            witchSeconds--;
        }, 0L, 20L);
    }

    private void openWitchMenu(Player witch) {
        WitchMenu menu = new WitchMenu();
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(menu, 27,
                Component.text("Hexentränke", NamedTextColor.DARK_GRAY));
        menu.inventory(inv);

        ItemStack heal = new ItemStack(Material.POTION);
        if (heal.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potion) {
            potion.setBasePotionType(org.bukkit.potion.PotionType.HEALING);
            potion.displayName(Component.text("Heiltrank", NamedTextColor.GREEN));
            List<Component> lore = new ArrayList<>();
            lore.add(Component.text(healUsed ? "Bereits verbraucht" : "Rettet das Wolfsopfer", NamedTextColor.GRAY));
            if (pendingVictim != null) {
                Player victim = Bukkit.getPlayer(pendingVictim);
                lore.add(Component.text("Opfer: " + (victim != null ? victim.getName() : "?"), NamedTextColor.RED));
            }
            potion.lore(lore);
            heal.setItemMeta(potion);
        }
        inv.setItem(WitchMenu.HEAL_SLOT, heal);

        ItemStack poison = new ItemStack(Material.POTION);
        if (poison.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potion) {
            potion.setBasePotionType(org.bukkit.potion.PotionType.HARMING);
            potion.displayName(Component.text("Gifttrank", NamedTextColor.DARK_GREEN));
            potion.lore(List.of(Component.text(poisonUsed ? "Bereits verbraucht" : "Tötet einen Spieler", NamedTextColor.GRAY)));
            poison.setItemMeta(potion);
        }
        inv.setItem(WitchMenu.POISON_SLOT, poison);

        ItemStack nothing = new ItemStack(Material.BARRIER);
        if (nothing.getItemMeta() != null) {
            var meta = nothing.getItemMeta();
            meta.displayName(Component.text("Nichts tun", NamedTextColor.GRAY));
            nothing.setItemMeta(meta);
        }
        inv.setItem(WitchMenu.NOTHING_SLOT, nothing);

        witch.openInventory(inv);
    }

    public void witchHeal(Player witch) {
        if (roles.get(witch.getUniqueId()) != Role.HEXE || healUsed || pendingVictim == null) {
            return;
        }
        healUsed = true;
        victimHealed = true;
        cancelWitchTimer();
        witch.closeInventory();
        witch.sendMessage(Component.text("Du hast das Opfer geheilt.", NamedTextColor.GREEN));
        Bukkit.getScheduler().runTaskLater(plugin, this::startDay, 60L);
    }

    public void witchPoisonChoose(Player witch) {
        if (roles.get(witch.getUniqueId()) != Role.HEXE || poisonUsed) {
            return;
        }
        poisonSelecting = true;
        openPoisonMenu(witch);
    }

    private void openPoisonMenu(Player witch) {
        SeerMenu menu = new SeerMenu();
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(menu, 27,
                Component.text("Wen vergiften?", NamedTextColor.DARK_GREEN));
        menu.inventory(inv);
        int slot = 0;
        for (UUID uuid : alive) {
            if (uuid.equals(witch.getUniqueId())) {
                continue;
            }
            Player target = Bukkit.getPlayer(uuid);
            if (target == null || slot >= 27) {
                continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                skull.setOwningPlayer(target);
                skull.displayName(Component.text(target.getName(), NamedTextColor.WHITE));
                head.setItemMeta(skull);
            }
            inv.setItem(slot, head);
            menu.heads().put(slot, uuid);
            slot++;
        }

        ItemStack back = new ItemStack(Material.ARROW);
        if (back.getItemMeta() != null) {
            var meta = back.getItemMeta();
            meta.displayName(Component.text("Zurück", NamedTextColor.WHITE));
            back.setItemMeta(meta);
        }
        inv.setItem(26, back);
        witch.openInventory(inv);
    }

    /** Zurück aus dem Vergiftungs-Menü ins Hexen-Menü. */
    public void witchBack(Player witch) {
        if (roles.get(witch.getUniqueId()) != Role.HEXE) {
            return;
        }
        poisonSelecting = false;
        openWitchMenu(witch);
    }

    public void witchPoisonPick(Player witch, UUID targetId) {
        if (!poisonSelecting || roles.get(witch.getUniqueId()) != Role.HEXE) {
            return;
        }
        poisonSelecting = false;
        poisonUsed = true;
        poisonVictim = targetId;
        cancelWitchTimer();
        witch.closeInventory();
        Player target = Bukkit.getPlayer(targetId);
        witch.sendMessage(Component.text("Du hast " + (target != null ? target.getName() : "?") + " vergiftet.",
                NamedTextColor.DARK_GREEN));
        Bukkit.getScheduler().runTaskLater(plugin, this::startDay, 60L);
    }

    public void witchNothing(Player witch) {
        if (roles.get(witch.getUniqueId()) != Role.HEXE) {
            return;
        }
        cancelWitchTimer();
        witch.closeInventory();
        Bukkit.getScheduler().runTaskLater(plugin, this::startDay, 60L);
    }

    private void cancelWitchTimer() {
        if (witchTimer != null) {
            witchTimer.cancel();
            witchTimer = null;
        }
    }

    /* -------------------------------------------------------------- Jäger */

    public void startHunterMenu(Player hunter) {
        if (state == State.LOBBY || state == State.END) {
            return;
        }
        if (roles.get(hunter.getUniqueId()) != Role.JAEGER) {
            return;
        }
        hunterOpen = true;
        openHunterMenu(hunter);

        cancelHunterTimer();
        hunterSeconds = 20;
        hunterTimer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!hunterOpen || !hunter.isOnline()) {
                cancelHunterTimer();
                return;
            }
            if (hunterSeconds <= 0) {
                cancelHunterTimer();
                hunterOpen = false;
                hunter.closeInventory();
                hunter.sendMessage(Component.text("Du hast niemanden mitgenommen.", NamedTextColor.GRAY));
                return;
            }
            hunter.sendActionBar(Component.text("Jäger: ", NamedTextColor.GOLD)
                    .append(Component.text(hunterSeconds + " s", NamedTextColor.YELLOW)));
            hunterSeconds--;
        }, 0L, 20L);
    }

    private void openHunterMenu(Player hunter) {
        HunterMenu menu = new HunterMenu();
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(menu, 54,
                Component.text("Wen nimmst du mit? – " + hunterSeconds + " s", NamedTextColor.DARK_RED));
        menu.inventory(inv);

        ItemStack filler = new ItemStack(Material.ORANGE_STAINED_GLASS_PANE);
        if (filler.getItemMeta() != null) {
            var meta = filler.getItemMeta();
            meta.displayName(Component.text(" "));
            filler.setItemMeta(meta);
        }
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        int index = 0;
        for (UUID uuid : alive) {
            if (index >= HunterMenu.HEAD_SLOTS.length) {
                break;
            }
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) {
                continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                skull.setOwningPlayer(target);
                skull.displayName(Component.text(target.getName(), NamedTextColor.WHITE));
                skull.lore(List.of(Component.text("Klicken = erschießen", NamedTextColor.RED)));
                head.setItemMeta(skull);
            }
            inv.setItem(HunterMenu.HEAD_SLOTS[index], head);
            menu.heads().put(HunterMenu.HEAD_SLOTS[index], uuid);
            index++;
        }

        ItemStack nothing = new ItemStack(Material.RED_CONCRETE);
        if (nothing.getItemMeta() != null) {
            var meta = nothing.getItemMeta();
            meta.displayName(Component.text("Nicht schießen", NamedTextColor.RED,
                    net.kyori.adventure.text.format.TextDecoration.BOLD));
            nothing.setItemMeta(meta);
        }
        inv.setItem(HunterMenu.NOTHING_SLOT, nothing);

        hunter.openInventory(inv);
    }

    public void hunterShoot(Player hunter, UUID targetId) {
        if (!hunterOpen || roles.get(hunter.getUniqueId()) != Role.JAEGER) {
            return;
        }
        hunterOpen = false;
        cancelHunterTimer();
        hunter.closeInventory();

        Player target = Bukkit.getPlayer(targetId);
        if (target != null && alive.contains(targetId)) {
            alive.remove(targetId);
            Bukkit.broadcast(Component.text("Der Jäger hat " + target.getName() + " mit in den Tod gerissen.",
                    NamedTextColor.GOLD));
            target.setHealth(0);
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::checkWin, 3L);
    }

    public void hunterNothing(Player hunter) {
        if (roles.get(hunter.getUniqueId()) != Role.JAEGER) {
            return;
        }
        hunterOpen = false;
        cancelHunterTimer();
        hunter.closeInventory();
        hunter.sendMessage(Component.text("Du hast niemanden mitgenommen.", NamedTextColor.GRAY));
    }

    private void cancelHunterTimer() {
        if (hunterTimer != null) {
            hunterTimer.cancel();
            hunterTimer = null;
        }
    }

    /* ------------------------------------------------------- Dorfschlampe */

    public boolean slutSelecting() {
        return slutSelecting;
    }

    public void startSlutPhase() {
        if (state != State.NIGHT) {
            return;
        }
        Player slut = alivePlayerWithRole(Role.DORFSCHLAMPE);
        if (slut == null) {
            Bukkit.getScheduler().runTaskLater(plugin, this::beginWolfPhase, 40L);
            return;
        }
        plugin.cinematic().wake(slut);
        int houseId = houseOf(slut);
        if (plugin.config().house(houseId).spawn != null) {
            slut.teleport(plugin.config().house(houseId).spawn);
        }
        slut.sendMessage(Component.text("Du bist die Dorfschlampe. Wähle, bei wem du die Nacht verbringst.",
                NamedTextColor.LIGHT_PURPLE));
        slutSelecting = true;
        openSlutMenu(slut);

        cancelSlutTimer();
        slutSeconds = 45;
        slutTimer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (state != State.NIGHT) {
                cancelSlutTimer();
                return;
            }
            if (slutSeconds <= 0) {
                cancelSlutTimer();
                slutSelecting = false;
                slut.closeInventory();
                slut.sendMessage(Component.text("Du hast niemanden besucht.", NamedTextColor.GRAY));
                plugin.cinematic().sleepIn(slut, houseOf(slut));
                Bukkit.getScheduler().runTaskLater(plugin, this::beginWolfPhase, 60L);
                return;
            }
            slut.sendActionBar(Component.text("Dorfschlampe: ", NamedTextColor.LIGHT_PURPLE)
                    .append(Component.text(slutSeconds + " s", NamedTextColor.YELLOW)));
            slutSeconds--;
        }, 0L, 20L);
    }

    private void openSlutMenu(Player slut) {
        SeerMenu menu = new SeerMenu();
        org.bukkit.inventory.Inventory inv = Bukkit.createInventory(menu, 27,
                Component.text("Bei wem übernachtest du?", NamedTextColor.LIGHT_PURPLE));
        menu.inventory(inv);
        int slot = 0;
        for (UUID uuid : alive) {
            if (uuid.equals(slut.getUniqueId()) || slot >= 27) {
                continue;
            }
            Player target = Bukkit.getPlayer(uuid);
            if (target == null) {
                continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof org.bukkit.inventory.meta.SkullMeta skull) {
                skull.setOwningPlayer(target);
                skull.displayName(Component.text(target.getName(), NamedTextColor.WHITE));
                head.setItemMeta(skull);
            }
            inv.setItem(slot, head);
            menu.heads().put(slot, uuid);
            slot++;
        }
        slut.openInventory(inv);
    }

    public void slutPick(Player slut, UUID targetId) {
        if (!slutSelecting || roles.get(slut.getUniqueId()) != Role.DORFSCHLAMPE) {
            return;
        }
        slutSelecting = false;
        visitTarget = targetId;
        cancelSlutTimer();
        slut.closeInventory();
        Player target = Bukkit.getPlayer(targetId);
        slut.sendMessage(Component.text("Du verbringst die Nacht bei " + (target != null ? target.getName() : "?") + ".",
                NamedTextColor.LIGHT_PURPLE));
        plugin.cinematic().sleepIn(slut, houseOf(slut));
        Bukkit.getScheduler().runTaskLater(plugin, this::beginWolfPhase, 60L);
    }

    private void cancelSlutTimer() {
        if (slutTimer != null) {
            slutTimer.cancel();
            slutTimer = null;
        }
    }

    /** Morgen: alle aufwecken, Schwerter weg, Tag setzen. */
    public void startDay() {
        // Nachtopfer auflösen
        if (pendingVictim != null && !victimHealed) {
            Player victim = Bukkit.getPlayer(pendingVictim);
            if (victim != null && alive.contains(pendingVictim)) {
                alive.remove(pendingVictim);
                victim.setHealth(0);
                Bukkit.broadcast(Component.text(victim.getName() + " wurde in der Nacht von den Werwölfen getötet.",
                        NamedTextColor.DARK_RED));
            }
        }
        if (poisonVictim != null) {
            Player poisoned = Bukkit.getPlayer(poisonVictim);
            if (poisoned != null && alive.contains(poisonVictim)) {
                alive.remove(poisonVictim);
                poisoned.setHealth(0);
                Bukkit.broadcast(Component.text(poisoned.getName() + " wurde vergiftet.", NamedTextColor.DARK_GREEN));
            }
        }
        if (visitTarget != null) {
            Player slut = alivePlayerWithRole(Role.DORFSCHLAMPE);
            boolean victimDied = (pendingVictim != null && !victimHealed && pendingVictim.equals(visitTarget))
                    || (poisonVictim != null && poisonVictim.equals(visitTarget));
            if (slut != null && victimDied && alive.contains(slut.getUniqueId())) {
                alive.remove(slut.getUniqueId());
                Player visited = Bukkit.getPlayer(visitTarget);
                Bukkit.broadcast(Component.text("Die Dorfschlampe wurde bei "
                        + (visited != null ? visited.getName() : "?") + " gefunden und getötet.",
                        NamedTextColor.LIGHT_PURPLE));
                slut.setHealth(0);
            }
        }
        visitTarget = null;
        slutSelecting = false;

        pendingVictim = null;
        poisonVictim = null;
        victimHealed = false;
        poisonSelecting = false;

        java.util.List<Player> participants = new ArrayList<>();
        for (UUID id : alive) {
            Player player = Bukkit.getPlayer(id);
            if (player != null && player.isOnline()) {
                participants.add(player);
            }
        }
        if (participants.isEmpty()) {
            finishDay();
            return;
        }
        plugin.cinematic().playMorning(participants, this::finishDay);
    }

    /** Wird nach dem Morgen-Kino ausgeführt: Sitze, Tag, Broadcast. */
    private void finishDay() {
        if (narratorEnabled && narratorId != null) {
            Player narrator = Bukkit.getPlayer(narratorId);
            if (narrator != null) {
                Location seat = plugin.config().seat(20);
                if (seat != null) {
                    plugin.seats().sit(narrator, seat, plugin.config().seatOffset());
                }
                giveNarratorItems(narrator);
                narrator.setFlying(false);
                narrator.setAllowFlight(false);
            }
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (roles.get(player.getUniqueId()) == Role.WERWOLF) {
                player.getInventory().remove(Material.IRON_SWORD);
            }
        }
        if (plugin.config().center() != null && plugin.config().center().getWorld() != null) {
            plugin.cinematic().setDay(plugin.config().center().getWorld());
        }
        state = State.DAY;
        day++;
        applyVoicePermissions();
        plugin.hud().updateAll();
        Script.say(Script.MORNING);
        if (!narratorActive()) {
            Script.story(plugin, Script.MORNING_STORY);
        }
        if (day == 2) {
            Bukkit.getScheduler().runTaskLater(plugin, this::startMayorElection, 100L);
        }
        if (!narratorActive()) {
            scheduleVote(day == 2 ? 1200L : 900L);
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::checkWin, 5L);
    }

    /** Hält tote Zuschauer im Radius von 40 Blöcken um den Dorfmittelpunkt. */
    public void enforceSpectatorBounds() {
        if (state == State.LOBBY) {
            return;
        }
        Location center = plugin.config().center();
        if (center == null) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!roles.containsKey(player.getUniqueId()) || alive.contains(player.getUniqueId())) {
                continue;
            }
            if (player.getGameMode() != GameMode.SPECTATOR) {
                continue;
            }
            Location location = player.getLocation();
            if (location.getWorld() == null || !location.getWorld().equals(center.getWorld())) {
                player.teleport(center.clone().add(0, 5, 0));
                continue;
            }
            if (location.distance(center) > 40) {
                org.bukkit.util.Vector direction = location.toVector().subtract(center.toVector());
                if (direction.lengthSquared() < 0.01) {
                    direction = new org.bukkit.util.Vector(0, 1, 0);
                }
                direction.normalize().multiply(39);
                player.teleport(center.clone().add(direction));
            }
        }
    }

    private List<UUID> aliveWolves() {
        List<UUID> wolves = new ArrayList<>();
        for (Map.Entry<UUID, Role> entry : roles.entrySet()) {
            if (entry.getValue() == Role.WERWOLF && alive.contains(entry.getKey())) {
                wolves.add(entry.getKey());
            }
        }
        return wolves;
    }

    /** Startet die Nacht (Kino) für alle lebenden Spieler. */
    /** Siegprüfung: Dorf (keine Wölfe) oder Wölfe (Mehrheit). */
    public void checkWin() {
        if (state == State.LOBBY || state == State.END) {
            return;
        }
        int aliveCount = alive.size();
        if (aliveCount == 0) {
            return;
        }
        int wolves = 0;
        for (java.util.UUID id : alive) {
            if (roles.get(id) == Role.WERWOLF) {
                wolves++;
            }
        }
        if (wolves == 0) {
            endRound(true);
        } else if (wolves >= aliveCount - wolves) {
            endRound(false);
        }
    }

    private void endRound(boolean villageWins) {
        state = State.END;

        net.kyori.adventure.title.Title title = net.kyori.adventure.title.Title.title(
                Component.text(villageWins ? "DORF GEWINNT" : "WÖLFE GEWINNEN",
                        villageWins ? NamedTextColor.GREEN : NamedTextColor.DARK_RED,
                        net.kyori.adventure.text.format.TextDecoration.BOLD),
                Component.text(villageWins ? "Alle Werwölfe sind besiegt" : "Das Dorf ist gefallen",
                        NamedTextColor.GRAY),
                net.kyori.adventure.title.Title.Times.times(java.time.Duration.ofMillis(300),
                        java.time.Duration.ofSeconds(4), java.time.Duration.ofMillis(600)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            player.playSound(player.getLocation(), villageWins ? org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE
                    : org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            Bukkit.broadcast(Component.text("────────── Auflösung ──────────", NamedTextColor.DARK_GRAY));
            for (Player online : Bukkit.getOnlinePlayers()) {
                Role role = roles.get(online.getUniqueId());
                if (role != null) {
                    boolean isAlive = alive.contains(online.getUniqueId());
                    Bukkit.broadcast(Component.text(online.getName() + ": ", NamedTextColor.GRAY)
                            .append(Component.text(role.displayName(), role.color(),
                                    net.kyori.adventure.text.format.TextDecoration.BOLD))
                            .append(Component.text(isAlive ? "" : " (tot)", NamedTextColor.DARK_GRAY)));
                } else if (isNarrator(online.getUniqueId())) {
                    Bukkit.broadcast(Component.text(online.getName() + ": ", NamedTextColor.GRAY)
                            .append(Component.text("Erzähler", NamedTextColor.GOLD)));
                }
            }
        }, 20L);

        Bukkit.getScheduler().runTaskLater(plugin, this::stop, 260L);
    }

    public void startNight() {
        if (state != State.DAY) {
            return;
        }
        if (aliveWolves().isEmpty()) {
            endRound(true);
            return;
        }
        state = State.CINEMATIC;
        applyVoicePermissions();
        cancelNightCountdown();
        Script.say(Script.NIGHT_FALLS);
        if (!narratorActive()) {
            Script.story(plugin, Script.NIGHT_STORY);
        }
        pendingVictim = null;
        poisonVictim = null;
        victimHealed = false;
        Map<UUID, Integer> houseMap = new LinkedHashMap<>();
        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!alive.contains(player.getUniqueId())) {
                continue;
            }
            players.add(player);
            Integer house = houses.get(player.getUniqueId());
            if (house != null) {
                houseMap.put(player.getUniqueId(), house);
            }
        }
        plugin.cinematic().play(players, houseMap);
    }

    /* ---------------------------------------------------------------- Stop */

    /** Erzähler-Steuerung: Uhr = Nacht, Papier = Abstimmung, Barriere = Runde beenden. */
    public void giveNarratorItems(Player narrator) {
        ItemStack clock = new ItemStack(Material.CLOCK);
        ItemMeta clockMeta = clock.getItemMeta();
        if (clockMeta != null) {
            clockMeta.displayName(Component.text("Nacht starten", NamedTextColor.GOLD));
            clockMeta.lore(List.of(Component.text("Rechtsklick: Kino + Nacht einleiten", NamedTextColor.GRAY)
                    .decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)));
            clockMeta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            clockMeta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            clock.setItemMeta(clockMeta);
        }

        ItemStack vote = new ItemStack(Material.PAPER);
        ItemMeta voteMeta = vote.getItemMeta();
        if (voteMeta != null) {
            voteMeta.displayName(Component.text("Abstimmung starten", NamedTextColor.YELLOW));
            voteMeta.lore(List.of(Component.text("Rechtsklick: Abstimmung einleiten", NamedTextColor.GRAY)));
            vote.setItemMeta(voteMeta);
        }

        ItemStack stop = new ItemStack(Material.BARRIER);
        ItemMeta stopMeta = stop.getItemMeta();
        if (stopMeta != null) {
            stopMeta.displayName(Component.text("Runde beenden", NamedTextColor.RED));
            stopMeta.lore(List.of(Component.text("Rechtsklick: Runde sofort beenden", NamedTextColor.GRAY)));
            stop.setItemMeta(stopMeta);
        }

        narrator.getInventory().setItem(0, clock);
        narrator.getInventory().setItem(1, vote);
        narrator.getInventory().setItem(8, stop);
    }

    /**
     * Voice-Rechte: Lobby und Tag (Sitze am Lagerfeuer) dürfen alle sprechen,
     * nachts nur der Erzähler.
     */
    /** Beim Quit das gespeicherte Attachment vergessen (wird von Bukkit entfernt). */
    public void forgetVoice(Player player) {
        voicePermissions.remove(player.getUniqueId());
    }

    public void applyVoicePermissions() {
        clearVoicePermissions();
        if (state == State.LOBBY || state == State.END) {
            return;
        }
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) {
                continue;
            }
            boolean allowed;
            if (state == State.DAY) {
                // Tag: alle Lebenden + Erzähler, Tote nicht.
                allowed = alive.contains(player.getUniqueId())
                        || (narratorEnabled && narratorId != null && isNarrator(player.getUniqueId()));
            } else {
                allowed = narratorEnabled && narratorId != null && isNarrator(player.getUniqueId());
            }
            PermissionAttachment attachment = player.addAttachment(plugin);
            attachment.setPermission("voicechat.speak", allowed);
            voicePermissions.put(player.getUniqueId(), attachment);
        }
    }

    private void clearVoicePermissions() {
        for (Map.Entry<UUID, PermissionAttachment> entry : voicePermissions.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null || !player.isOnline()) {
                continue;
            }
            try {
                player.removeAttachment(entry.getValue());
            } catch (IllegalArgumentException ignored) {
                // Attachment wurde bereits automatisch entfernt (z. B. nach Reconnect).
            }
        }
        voicePermissions.clear();
    }

    public void stop() {
        plugin.vote().cancel();
        cancelNightTimer();
        cancelSeerTimer();
        cancelAmorTimer();
        cancelWitchTimer();
        cancelHunterTimer();
        cancelSlutTimer();
        cancelMayorTimer();
        cancelVoteCountdown();
        cancelNightCountdown();
        mayorElectionActive = false;
        mayorChoosingSuccessor = false;
        mayorId = null;
        mayorVotes.clear();
        hunterOpen = false;
        slutSelecting = false;
        visitTarget = null;
        amorSelecting = false;
        poisonSelecting = false;
        if (autoNightTask != null) {
            autoNightTask.cancel();
            autoNightTask = null;
        }
        state = State.LOBBY;
        applyVoicePermissions();
        plugin.hud().updateAll();
        roles.clear();
        houses.clear();
        alive.clear();

        plugin.cinematic().stopAll();
        plugin.seats().removeAll();

        Location lobby = plugin.config().lobbyOrFallback();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isSleeping()) {
                player.wakeup(true);
            }
            player.setGameMode(GameMode.ADVENTURE);
            player.setAllowFlight(false);
            player.setFlying(false);
            player.setInvulnerable(false);

            // Inventar und Effekte zurücksetzen (Erzähler-Items, Tränke, Darkness).
            player.getInventory().clear();
            player.getInventory().setArmorContents(null);
            for (PotionEffect effect : new ArrayList<>(player.getActivePotionEffects())) {
                player.removePotionEffect(effect.getType());
            }
            player.setFireTicks(0);
            player.setHealth(player.getMaxHealth());
            player.setFoodLevel(20);

            if (lobby != null) {
                player.teleport(lobby);
            }
        }
        Bukkit.broadcast(Component.text("Die Runde wurde beendet.", NamedTextColor.YELLOW));
    }

    /* ---------------------------------------------------------------- Join */

    /** Disconnect während der Runde = ausgeschieden. */
    public void handleQuit(Player player) {
        if (state == State.LOBBY || state == State.END) {
            return;
        }
        UUID id = player.getUniqueId();
        if (!alive.remove(id)) {
            return;
        }
        Bukkit.broadcast(Component.text(player.getName() + " hat das Spiel verlassen und ist ausgeschieden.",
                NamedTextColor.GRAY));

        UUID partner = lovePartnerOf(id);
        if (partner != null && alive.contains(partner)) {
            Player other = Bukkit.getPlayer(partner);
            if (other != null) {
                alive.remove(partner);
                Bukkit.broadcast(Component.text(other.getName() + " stirbt aus Liebeskummer.",
                        NamedTextColor.LIGHT_PURPLE));
                other.setHealth(0);
            }
        }
        if (id.equals(mayorId)) {
            mayorId = null;
        }
        Bukkit.getScheduler().runTaskLater(plugin, this::checkWin, 5L);
    }

    public void handleJoin(Player player) {
        if (state == State.LOBBY) {
            return;
        }
        player.setGameMode(GameMode.SPECTATOR);
        player.sendMessage(Component.text("Eine Runde läuft – du schaust zu.", NamedTextColor.GRAY));
    }
}
