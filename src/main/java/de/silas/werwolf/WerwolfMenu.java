package de.silas.werwolf;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

/**
 * Marker-Holder, damit der Listener das Werwolf-Menü sicher erkennt.
 */
public class WerwolfMenu implements InventoryHolder {

    private Inventory inventory;

    public void inventory(Inventory inventory) {
        this.inventory = inventory;
    }

    @Override
    public @NotNull Inventory getInventory() {
        return inventory;
    }
}
