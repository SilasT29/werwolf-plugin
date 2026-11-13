package de.silas.werwolf;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Seherinnen-GUI: Kopf anklicken, Rolle ansehen.
 */
public class SeerMenu implements InventoryHolder {

    private Inventory inventory;
    private final Map<Integer, UUID> heads = new HashMap<>();

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    public Map<Integer, UUID> heads() {
        return heads;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
