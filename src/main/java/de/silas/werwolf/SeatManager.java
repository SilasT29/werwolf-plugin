package de.silas.werwolf;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Unsichtbare Armor-Stand-Stühle für die Tagphase.
 */
public class SeatManager {

    private final Map<UUID, ArmorStand> seated = new HashMap<>();

    public void sit(Player player, Location seat, double offset) {
        standUp(player);

        Location base = seat.clone().subtract(0, offset, 0);
        ArmorStand chair = base.getWorld().spawn(base, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setBasePlate(false);
            stand.setSmall(false);
        });

        chair.addPassenger(player);
        seated.put(player.getUniqueId(), chair);
    }

    public void standUp(Player player) {
        ArmorStand chair = seated.remove(player.getUniqueId());
        if (chair != null) {
            chair.eject();
            chair.remove();
        }
    }

    public boolean isSeated(Player player) {
        return seated.containsKey(player.getUniqueId());
    }

    public void removeAll() {
        java.util.List<ArmorStand> stands = new java.util.ArrayList<>(seated.values());
        seated.clear();
        for (ArmorStand stand : stands) {
            stand.eject();
            stand.remove();
        }
    }
}
