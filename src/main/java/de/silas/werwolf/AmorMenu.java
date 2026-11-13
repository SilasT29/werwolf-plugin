package de.silas.werwolf;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Amor-GUI: 54 Slots, Köpfe anklicken, zwei auswählen, grün bestätigen.
 */
public class AmorMenu implements InventoryHolder {

    public static final int CONFIRM_SLOT = 53;
    public static final int[] HEAD_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            19, 20, 21, 22, 23, 24, 25,
            28, 29, 30, 31, 32, 33, 34,
            37, 38, 39, 40, 41, 42, 43
    };

    private Inventory inventory;
    private final Map<Integer, UUID> heads = new HashMap<>();
    private final List<UUID> selected = new ArrayList<>();

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Map<Integer, UUID> heads() {
        return heads;
    }

    public List<UUID> selected() {
        return selected;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
