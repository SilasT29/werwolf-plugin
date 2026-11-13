package de.silas.werwolf;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Hexen-GUI: Heilen / Vergiften / Nichts.
 */
public class WitchMenu implements InventoryHolder {

    public static final int HEAL_SLOT = 11;
    public static final int POISON_SLOT = 13;
    public static final int NOTHING_SLOT = 15;

    private Inventory inventory;

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
