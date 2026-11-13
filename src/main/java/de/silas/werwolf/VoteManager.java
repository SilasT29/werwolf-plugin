package de.silas.werwolf;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;

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
 * Dorfabstimmung: GUI mit Köpfen, Mehrheit, Stichwahl, Lynch.
 */
public class VoteManager {

    private static final int VOTE_SECONDS = 30;
    private static final int RUNOFF_SECONDS = 20;

    private final WerwolfPlugin plugin;
    private final Map<UUID, UUID> votes = new HashMap<>();
    private final Set<UUID> runoffCandidates = new HashSet<>();
    private final Set<UUID> skipVotes = new HashSet<>();

    private boolean active;
    private boolean runoff;
    private BukkitTask timer;
    private int seconds;

    public VoteManager(WerwolfPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean isActive() {
        return active;
    }

    public void start() {
        GameManager game = plugin.gameManager();
        if (game.state() != GameManager.State.DAY) {
            Bukkit.broadcast(Component.text("Die Abstimmung ist nur am Tag möglich.", NamedTextColor.RED));
            return;
        }
        plugin.gameManager().cancelVoteCountdown();
        active = true;
        runoff = false;
        votes.clear();
        runoffCandidates.clear();
        skipVotes.clear();
        Script.say(Script.VOTE);
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (game.alive().contains(player.getUniqueId())) {
                open(player);
            }
        }
        startTimer(VOTE_SECONDS);
    }

    public void open(Player viewer) {
        VoteMenu menu = new VoteMenu();
        Inventory inv = Bukkit.createInventory(menu, 27,
                Component.text("Abstimmung", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        menu.inventory(inv);

        GameManager game = plugin.gameManager();
        List<UUID> candidates = new ArrayList<>();
        for (UUID uuid : game.alive()) {
            if (!runoff || runoffCandidates.contains(uuid)) {
                candidates.add(uuid);
            }
        }
        candidates.sort((a, b) -> {
            Player pa = Bukkit.getPlayer(a);
            Player pb = Bukkit.getPlayer(b);
            String na = pa != null ? pa.getName() : "";
            String nb = pb != null ? pb.getName() : "";
            return na.compareToIgnoreCase(nb);
        });

        int slot = 0;
        for (UUID uuid : candidates) {
            Player target = Bukkit.getPlayer(uuid);
            if (target == null || slot >= 27) {
                continue;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                skull.setOwningPlayer(target);
                skull.displayName(Component.text(target.getName(), NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));

                List<Component> lore = new ArrayList<>();
                int count = countVotes(uuid);
                lore.add(Component.text("Stimmen: ", NamedTextColor.GRAY)
                        .append(Component.text(count, NamedTextColor.WHITE))
                        .decoration(TextDecoration.ITALIC, false));
                if (uuid.equals(votes.get(viewer.getUniqueId()))) {
                    lore.add(Component.text("Deine Stimme", NamedTextColor.GREEN)
                            .decoration(TextDecoration.ITALIC, false));
                }
                skull.lore(lore);
                head.setItemMeta(skull);
            }
            inv.setItem(slot, head);
            menu.heads().put(slot, uuid);
            slot++;
        }

        ItemStack skip = new ItemStack(Material.BARRIER);
        if (skip.getItemMeta() != null) {
            var meta = skip.getItemMeta();
            meta.displayName(Component.text("Abstimmung überspringen", NamedTextColor.GRAY,
                    TextDecoration.BOLD));
            int needed = game.alive().size() / 2 + 1;
            meta.lore(List.of(Component.text("Übersprungen: " + skipVotes.size() + "/" + needed,
                    NamedTextColor.GRAY)));
            skip.setItemMeta(meta);
        }
        inv.setItem(26, skip);

        viewer.openInventory(inv);
    }

    public void handleClick(Player voter, VoteMenu menu, int slot) {
        UUID target = menu.heads().get(slot);
        if (target == null || !active) {
            return;
        }
        GameManager game = plugin.gameManager();
        if (!game.alive().contains(voter.getUniqueId())) {
            return;
        }
        votes.put(voter.getUniqueId(), target);
        Player targetPlayer = Bukkit.getPlayer(target);
        voter.sendMessage(Component.text("Deine Stimme: " + (targetPlayer != null ? targetPlayer.getName() : "?"),
                NamedTextColor.GREEN));
        refreshMenus();
    }

    public void handleSkip(Player voter) {
        if (!active) {
            return;
        }
        GameManager game = plugin.gameManager();
        if (!game.alive().contains(voter.getUniqueId())) {
            return;
        }
        skipVotes.add(voter.getUniqueId());
        votes.remove(voter.getUniqueId());
        int needed = game.alive().size() / 2 + 1;
        if (skipVotes.size() >= needed) {
            skipVotes.clear();
            active = false;
            closeMenus();
            if (timer != null) {
                timer.cancel();
                timer = null;
            }
            Bukkit.broadcast(Component.text("Das Dorf überspringt die Abstimmung – niemand wird gelyncht.",
                    NamedTextColor.GRAY));
            autoNight();
            return;
        }
        refreshMenus();
    }

    /** Aktualisiert alle offenen Abstimmungs-Menüs, ohne sie zu schließen. */
    private void refreshMenus() {
        GameManager game = plugin.gameManager();
        for (Player online : Bukkit.getOnlinePlayers()) {
            Inventory top = online.getOpenInventory().getTopInventory();
            if (!(top.getHolder() instanceof VoteMenu menu)) {
                continue;
            }
            for (Map.Entry<Integer, UUID> entry : menu.heads().entrySet()) {
                Player target = Bukkit.getPlayer(entry.getValue());
                if (target == null) {
                    continue;
                }
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                if (head.getItemMeta() instanceof SkullMeta skull) {
                    skull.setOwningPlayer(target);
                    skull.displayName(Component.text(target.getName(), NamedTextColor.WHITE)
                            .decoration(TextDecoration.ITALIC, false));
                    List<Component> lore = new ArrayList<>();
                    lore.add(Component.text("Stimmen: " + countVotes(entry.getValue()), NamedTextColor.GRAY)
                            .decoration(TextDecoration.ITALIC, false));
                    if (entry.getValue().equals(votes.get(online.getUniqueId()))) {
                        lore.add(Component.text("Deine Stimme", NamedTextColor.GREEN)
                                .decoration(TextDecoration.ITALIC, false));
                    }
                    skull.lore(lore);
                    head.setItemMeta(skull);
                }
                top.setItem(entry.getKey(), head);
            }
            ItemStack skip = top.getItem(26);
            if (skip != null && skip.getType() == Material.BARRIER) {
                ItemStack updated = new ItemStack(Material.BARRIER);
                var meta = updated.getItemMeta();
                if (meta != null) {
                    meta.displayName(Component.text("Abstimmung überspringen", NamedTextColor.GRAY,
                            TextDecoration.BOLD));
                    meta.lore(List.of(Component.text("Übersprungen: " + skipVotes.size() + "/"
                            + (game.alive().size() / 2 + 1), NamedTextColor.GRAY)));
                    updated.setItemMeta(meta);
                }
                top.setItem(26, updated);
            }
        }
    }

    private int countVotes(UUID target) {
        int count = 0;
        UUID mayor = plugin.gameManager().mayorId();
        for (Map.Entry<UUID, UUID> entry : votes.entrySet()) {
            if (entry.getValue().equals(target)) {
                count += mayor != null && mayor.equals(entry.getKey()) ? 2 : 1;
            }
        }
        return count;
    }

    private void startTimer(int duration) {
        if (timer != null) {
            timer.cancel();
        }
        seconds = duration;
        timer = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!active) {
                timer.cancel();
                return;
            }
            if (seconds <= 0) {
                timer.cancel();
                finish();
                return;
            }
            if (seconds <= 5 || seconds % 5 == 0) {
                Component bar = Component.text("Abstimmung: " + seconds + " s", NamedTextColor.YELLOW);
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.sendActionBar(bar);
                }
            }
            seconds--;
        }, 0L, 20L);
    }

    private void finish() {
        Map<UUID, Integer> counts = new LinkedHashMap<>();
        UUID mayor = plugin.gameManager().mayorId();
        for (Map.Entry<UUID, UUID> entry : votes.entrySet()) {
            counts.merge(entry.getValue(), mayor != null && mayor.equals(entry.getKey()) ? 2 : 1, Integer::sum);
        }

        if (counts.isEmpty()) {
            Bukkit.broadcast(Component.text("Niemand hat abgestimmt – niemand wird gelyncht.", NamedTextColor.GRAY));
            active = false;
            closeMenus();
            autoNight();
            return;
        }

        int max = Collections.max(counts.values());
        List<UUID> top = new ArrayList<>();
        for (Map.Entry<UUID, Integer> entry : counts.entrySet()) {
            if (entry.getValue() == max) {
                top.add(entry.getKey());
            }
        }

        if (top.size() > 1) {
            if (!runoff) {
                runoff = true;
                runoffCandidates.clear();
                runoffCandidates.addAll(top);
                votes.clear();
                StringBuilder names = new StringBuilder();
                for (UUID uuid : top) {
                    Player player = Bukkit.getPlayer(uuid);
                    names.append(player != null ? player.getName() : "?").append(" ");
                }
                Bukkit.broadcast(Component.text("Stichwahl: " + names.toString().trim(), NamedTextColor.YELLOW));
                for (Player player : Bukkit.getOnlinePlayers()) {
                    if (plugin.gameManager().alive().contains(player.getUniqueId())) {
                        open(player);
                    }
                }
                startTimer(RUNOFF_SECONDS);
                return;
            }
            Bukkit.broadcast(Component.text("Gleichstand – niemand wird gelyncht.", NamedTextColor.GRAY));
            active = false;
            closeMenus();
            autoNight();
            return;
        }

        UUID victimId = top.get(0);
        Player victim = Bukkit.getPlayer(victimId);
        active = false;
        closeMenus();
        if (victim != null) {
            Bukkit.broadcast(Component.text(victim.getName() + " wurde vom Dorf gelyncht (" + max + " Stimmen).",
                    NamedTextColor.RED));
            victim.setHealth(0);
            Script.say(Script.LYNCH);
            plugin.gameManager().checkWin();
        }
        autoNight();
    }

    /** Ohne Erzähler zieht die Nacht nach der Abstimmung automatisch auf. */
    private void autoNight() {
        if (plugin.gameManager().narratorActive()) {
            return;
        }
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (plugin.gameManager().state() == GameManager.State.DAY) {
                plugin.gameManager().startNight();
            }
        }, 200L);
    }

    private void closeMenus() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof VoteMenu) {
                online.closeInventory();
            }
        }
    }

    public void cancel() {
        closeMenus();
        active = false;
        runoff = false;
        votes.clear();
        runoffCandidates.clear();
        skipVotes.clear();
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
    }
}
