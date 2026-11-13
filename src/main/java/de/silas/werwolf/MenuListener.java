package de.silas.werwolf;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class MenuListener implements Listener {

    private final WerwolfPlugin plugin;
    private final MenuManager menus;

    public MenuListener(WerwolfPlugin plugin, MenuManager menus) {
        this.plugin = plugin;
        this.menus = menus;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        if (event.getInventory().getHolder() instanceof MayorMenu mayorMenu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player
                    && event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getInventory())) {
                java.util.UUID target = mayorMenu.heads().get(event.getRawSlot());
                if (target != null) {
                    if (plugin.gameManager().mayorChoosingSuccessor()) {
                        plugin.gameManager().mayorSuccessorPick(player, target);
                    } else {
                        plugin.gameManager().mayorVote(player, target);
                    }
                }
            }
            return;
        }
        if (event.getInventory().getHolder() instanceof HunterMenu hunterMenu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player
                    && event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getInventory())) {
                int slot = event.getRawSlot();
                if (slot == HunterMenu.NOTHING_SLOT) {
                    plugin.gameManager().hunterNothing(player);
                } else if (hunterMenu.heads().containsKey(slot)) {
                    plugin.gameManager().hunterShoot(player, hunterMenu.heads().get(slot));
                }
            }
            return;
        }
        if (event.getInventory().getHolder() instanceof AmorMenu amorMenu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player
                    && event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getInventory())) {
                int slot = event.getRawSlot();
                if (slot == AmorMenu.CONFIRM_SLOT) {
                    plugin.gameManager().amorConfirm(player);
                } else if (amorMenu.heads().containsKey(slot)) {
                    plugin.gameManager().amorToggle(player, amorMenu.heads().get(slot));
                }
            }
            return;
        }
        if (event.getInventory().getHolder() instanceof WitchMenu witchMenu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player
                    && event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getInventory())) {
                int slot = event.getRawSlot();
                if (slot == WitchMenu.HEAL_SLOT) {
                    plugin.gameManager().witchHeal(player);
                } else if (slot == WitchMenu.POISON_SLOT) {
                    plugin.gameManager().witchPoisonChoose(player);
                } else if (slot == WitchMenu.NOTHING_SLOT) {
                    plugin.gameManager().witchNothing(player);
                }
            }
            return;
        }
        if (event.getInventory().getHolder() instanceof SeerMenu seerMenu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player
                    && event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getInventory())) {
                if (event.getRawSlot() == 26 && plugin.gameManager().poisonSelecting()) {
                    plugin.gameManager().witchBack(player);
                    return;
                }
                java.util.UUID target = seerMenu.heads().get(event.getRawSlot());
                if (target != null) {
                    if (plugin.gameManager().slutSelecting()) {
                        plugin.gameManager().slutPick(player, target);
                    } else if (plugin.gameManager().poisonSelecting()) {
                        plugin.gameManager().witchPoisonPick(player, target);
                    } else {
                        plugin.gameManager().seerPick(player, target);
                    }
                }
            }
            return;
        }
        if (event.getInventory().getHolder() instanceof VoteMenu voteMenu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player
                    && event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getInventory())) {
                if (event.getRawSlot() == 26) {
                    plugin.vote().handleSkip(player);
                } else {
                    plugin.vote().handleClick(player, voteMenu, event.getRawSlot());
                }
            }
            return;
        }
        if (event.getInventory().getHolder() instanceof NarratorMenu narratorMenu) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof org.bukkit.entity.Player player
                    && event.getClickedInventory() != null
                    && event.getClickedInventory().equals(event.getInventory())) {
                menus.handleNarratorClick(player, narratorMenu, event.getRawSlot());
            }
            return;
        }
        if (!(event.getInventory().getHolder() instanceof WerwolfMenu menu)) {
            return;
        }
        // Jede Interaktion im Menü unterbinden (auch Shift-Klicks aus dem Spielerinventar).
        event.setCancelled(true);

        if (!(event.getWhoClicked() instanceof org.bukkit.entity.Player player)) {
            return;
        }
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(event.getInventory())) {
            return;
        }

        menus.handleClick(player, menu, event.getRawSlot(), event.getClick());
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof WerwolfMenu
                || event.getInventory().getHolder() instanceof NarratorMenu
                || event.getInventory().getHolder() instanceof VoteMenu
                || event.getInventory().getHolder() instanceof SeerMenu
                || event.getInventory().getHolder() instanceof WitchMenu
                || event.getInventory().getHolder() instanceof AmorMenu
                || event.getInventory().getHolder() instanceof HunterMenu
                || event.getInventory().getHolder() instanceof MayorMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        menus.refreshOpenMenus();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        menus.refreshOpenMenus();
    }
}
