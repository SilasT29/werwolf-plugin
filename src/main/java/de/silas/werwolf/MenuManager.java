package de.silas.werwolf;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Baut und verwaltet das Spielleitungs-Menü.
 * Rollenanzahlen liegen im Speicher (Standard 0).
 */
public class MenuManager {

    public static final int HEAD_SLOT = 26;
    public static final int START_SLOT = 18;
    public static final int NARRATOR_SLOT = 8;
    private static final int SIZE = 27;

    private final WerwolfPlugin plugin;
    private final EnumMap<Role, Integer> counts = new EnumMap<>(Role.class);
    private boolean narratorEnabled;

    public MenuManager(WerwolfPlugin plugin) {
        this.plugin = plugin;
        for (Role role : Role.values()) {
            counts.put(role, 0);
        }
    }

    public void open(Player player) {
        WerwolfMenu menu = new WerwolfMenu();
        Inventory inv = Bukkit.createInventory(menu, SIZE,
                Component.text("Werwolf – Spielleitung", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
        menu.inventory(inv);
        render(menu);
        player.openInventory(inv);
    }

    public Map<Role, Integer> snapshot() {
        return new EnumMap<>(counts);
    }

    public int total() {
        int sum = 0;
        for (int value : counts.values()) {
            sum += value;
        }
        return sum;
    }

    public int onlineCount() {
        return Bukkit.getOnlinePlayers().size();
    }

    /**
     * Zeichnet alle Items neu. Wird nach jedem Klick und bei Join/Quit aufgerufen.
     */
    public void render(WerwolfMenu menu) {
        Inventory inv = menu.getInventory();
        if (inv == null) {
            return;
        }

        ItemStack filler = fillerItem();
        for (int slot = 0; slot < SIZE; slot++) {
            inv.setItem(slot, filler);
        }

        for (Role role : Role.values()) {
            inv.setItem(role.slot(), roleItem(role));
        }

        inv.setItem(HEAD_SLOT, headItem());
        inv.setItem(NARRATOR_SLOT, narratorItem());

        int online = onlineCount();
        int required = online - (narratorEnabled ? 1 : 0);
        if (total() == required && online > 0) {
            inv.setItem(START_SLOT, startItem(required));
        }
    }

    private ItemStack fillerItem() {
        ItemStack item = new ItemStack(Material.BROWN_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(" ", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack roleItem(Role role) {
        int count = counts.get(role);
        // Stack-Anzahl zeigt die vergebene Zahl; 0 lässt sich nicht als Stack darstellen -> 1 mit Lore "0".
        int amount = Math.max(1, Math.min(64, count));

        ItemStack item = new ItemStack(role.material(), amount);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        NamedTextColor nameColor = count > 0 ? role.color() : NamedTextColor.DARK_GRAY;
        meta.displayName(Component.text(role.displayName(), nameColor)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Anzahl: ", NamedTextColor.GRAY)
                .append(Component.text(count, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(role.description(), NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Linksklick: +1", NamedTextColor.DARK_GRAY)
                .append(Component.text("   Rechtsklick: -1", NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);

        if (role.potionType() != null && meta instanceof PotionMeta potionMeta) {
            potionMeta.setBasePotionType(role.potionType());
        }

        item.setItemMeta(meta);
        return item;
    }

    private ItemStack narratorItem() {
        ItemStack item = new ItemStack(narratorEnabled ? Material.WRITABLE_BOOK : Material.BOOK);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Component.text("Erzähler", NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Status: ", NamedTextColor.GRAY)
                .append(Component.text(narratorEnabled ? "AN" : "AUS", narratorEnabled ? NamedTextColor.GREEN : NamedTextColor.RED))
                .decoration(TextDecoration.ITALIC, false));
        String narrator = plugin.gameManager().narratorName();
        lore.add(Component.text("Erzähler: ", NamedTextColor.GRAY)
                .append(Component.text(narrator != null ? narrator : "nicht gewählt", NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(narrator == null ? "/ww narrator <Name>" : "Sitzt auf Sitz 20, spricht als Einziger",
                NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());
        lore.add(Component.text("Linksklick: an/aus", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack headItem() {
        int online = onlineCount();
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, Math.max(1, Math.min(64, online)));
        if (!(item.getItemMeta() instanceof SkullMeta skullMeta)) {
            return item;
        }

        Player first = Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        if (first != null) {
            skullMeta.setOwningPlayer(first);
        }

        skullMeta.displayName(Component.text("Anzahl der Spieler", NamedTextColor.WHITE)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Online: ", NamedTextColor.GRAY)
                .append(Component.text(online, NamedTextColor.WHITE))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("Vergeben: ", NamedTextColor.GRAY)
                .append(Component.text(total(), NamedTextColor.WHITE))
                .append(Component.text(" / " + online, NamedTextColor.DARK_GRAY))
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.empty());

        List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        int shown = 0;
        for (String name : names) {
            if (shown++ >= 10) {
                lore.add(Component.text("… und " + (names.size() - 10) + " weitere", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
                break;
            }
            lore.add(Component.text("• " + name, NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false));
        }
        skullMeta.lore(lore);
        item.setItemMeta(skullMeta);
        return item;
    }

    private ItemStack startItem(int online) {
        ItemStack item = new ItemStack(Material.LIME_CONCRETE);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }
        meta.displayName(Component.text("Spiel starten", NamedTextColor.GREEN, TextDecoration.BOLD)
                .decoration(TextDecoration.ITALIC, false));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Rollen vollständig vergeben", NamedTextColor.GRAY)
                .decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(online + " / " + online + " Spieler", NamedTextColor.DARK_GRAY)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    public static final int NARRATOR_TOGGLE_SLOT = 4;
    public static final int NARRATOR_CLEAR_SLOT = 35;
    public static final int NARRATOR_BACK_SLOT = 27;

    public void openNarratorMenu(Player viewer) {
        NarratorMenu menu = new NarratorMenu();
        Inventory inv = Bukkit.createInventory(menu, 36,
                Component.text("Erzähler wählen", NamedTextColor.DARK_GRAY)
                        .decoration(TextDecoration.ITALIC, false));
        menu.inventory(inv);

        ItemStack toggle = new ItemStack(narratorEnabled ? Material.LIME_DYE : Material.GRAY_DYE);
        ItemMeta toggleMeta = toggle.getItemMeta();
        if (toggleMeta != null) {
            toggleMeta.displayName(Component.text("Erzähler: " + (narratorEnabled ? "AN" : "AUS"),
                    narratorEnabled ? NamedTextColor.GREEN : NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            toggleMeta.lore(List.of(Component.text("Linksklick: umschalten", NamedTextColor.DARK_GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            toggle.setItemMeta(toggleMeta);
        }
        inv.setItem(NARRATOR_TOGGLE_SLOT, toggle);

        int slot = 9;
        for (Player online : Bukkit.getOnlinePlayers()) {
            while (slot == NARRATOR_BACK_SLOT || slot == NARRATOR_CLEAR_SLOT) {
                slot++;
            }
            if (slot >= 36) {
                break;
            }
            ItemStack head = new ItemStack(Material.PLAYER_HEAD);
            if (head.getItemMeta() instanceof SkullMeta skull) {
                skull.setOwningPlayer(online);
                skull.displayName(Component.text(online.getName(), NamedTextColor.WHITE)
                        .decoration(TextDecoration.ITALIC, false));
                boolean isNarrator = plugin.gameManager().isNarrator(online.getUniqueId());
                skull.lore(List.of(
                        Component.text(isNarrator ? "Aktueller Erzähler" : "Klicken zum Auswählen",
                                isNarrator ? NamedTextColor.GOLD : NamedTextColor.GRAY)
                                .decoration(TextDecoration.ITALIC, false)));
                head.setItemMeta(skull);
            }
            inv.setItem(slot, head);
            menu.heads().put(slot, online.getUniqueId());
            slot++;
        }

        ItemStack clear = new ItemStack(Material.BARRIER);
        ItemMeta clearMeta = clear.getItemMeta();
        if (clearMeta != null) {
            clearMeta.displayName(Component.text("Erzähler entfernen", NamedTextColor.RED)
                    .decoration(TextDecoration.ITALIC, false));
            clear.setItemMeta(clearMeta);
        }
        inv.setItem(NARRATOR_CLEAR_SLOT, clear);

        ItemStack back = new ItemStack(Material.ARROW);
        ItemMeta backMeta = back.getItemMeta();
        if (backMeta != null) {
            backMeta.displayName(Component.text("Zurück", NamedTextColor.WHITE)
                    .decoration(TextDecoration.ITALIC, false));
            backMeta.lore(List.of(Component.text("Zurück zum Werwolf-Menü", NamedTextColor.GRAY)
                    .decoration(TextDecoration.ITALIC, false)));
            back.setItemMeta(backMeta);
        }
        inv.setItem(NARRATOR_BACK_SLOT, back);

        viewer.openInventory(inv);
    }

    public void handleNarratorClick(Player player, NarratorMenu menu, int slot) {
        if (slot == NARRATOR_TOGGLE_SLOT) {
            narratorEnabled = !narratorEnabled;
            openNarratorMenu(player);
            return;
        }
        if (slot == NARRATOR_BACK_SLOT) {
            player.closeInventory();
            open(player);
            return;
        }
        if (slot == NARRATOR_CLEAR_SLOT) {
            plugin.gameManager().clearNarrator();
            player.sendMessage(Component.text("Erzähler entfernt.", NamedTextColor.YELLOW));
            openNarratorMenu(player);
            return;
        }
        UUID uuid = menu.heads().get(slot);
        if (uuid == null) {
            return;
        }
        Player target = Bukkit.getPlayer(uuid);
        if (target == null) {
            return;
        }
        plugin.gameManager().setNarrator(target);
        player.sendMessage(Component.text(target.getName() + " ist jetzt Erzähler.", NamedTextColor.GOLD));
        openNarratorMenu(player);
    }

    public void handleClick(Player player, WerwolfMenu menu, int slot, ClickType clickType) {
        Role role = Role.bySlot(slot);
        if (role != null) {
            int current = counts.get(role);
            int online = Math.max(1, onlineCount());

            if (clickType.isLeftClick()) {
                current = Math.min(online, current + 1);
            } else if (clickType.isRightClick()) {
                current = Math.max(0, current - 1);
            } else {
                return;
            }

            counts.put(role, current);
            render(menu);
            return;
        }

        if (slot == NARRATOR_SLOT) {
            player.closeInventory();
            openNarratorMenu(player);
            return;
        }

        int required = onlineCount() - (narratorEnabled ? 1 : 0);
        if (slot == START_SLOT && total() == required && onlineCount() > 0) {
            if (counts.getOrDefault(Role.WERWOLF, 0) < 1) {
                player.sendMessage(Component.text("Mindestens ein Werwolf wird benötigt.", NamedTextColor.RED));
                return;
            }
            player.closeInventory();
            plugin.gameManager().requestStart(player, snapshot(), narratorEnabled, plugin.gameManager().narratorId());
        }
    }

    public void refreshOpenMenus() {
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.getOpenInventory().getTopInventory().getHolder() instanceof WerwolfMenu menu) {
                render(menu);
            }
        }
    }
}
