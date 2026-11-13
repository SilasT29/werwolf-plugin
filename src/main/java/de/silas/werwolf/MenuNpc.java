package de.silas.werwolf;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;

/**
 * Stellt den Werwolf-NPC in der Lobby sicher (Rechtsklick öffnet das Menü).
 */
public final class MenuNpc {

    public static final String MARKER = "werwolf_menu";
    private static final Location LOCATION = new Location(null, -34.46, 87.0, -98.70);

    private MenuNpc() {
    }

    public static int destroyAll() {
        int removed = 0;
        int total = 0;
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            total++;
            if (Boolean.TRUE.equals(npc.data().get(MARKER))) {
                npc.destroy();
                removed++;
            }
        }
        Bukkit.getLogger().info("[Werwolf] MenuNpc-Aufräumen: " + removed + " entfernt (Registry: " + total + ")");
        return removed;
    }

    public static void ensure(String skinName) {
        World world = Bukkit.getWorld("world");
        if (world == null) {
            return;
        }
        Location location = new Location(world, LOCATION.getX(), LOCATION.getY(), LOCATION.getZ(), 180f, 0f);

        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            if (Boolean.TRUE.equals(npc.data().get(MARKER))) {
                if (!npc.isSpawned()) {
                    npc.spawn(location);
                }
                return;
            }
        }

        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, "Werwolf");
        npc.data().setPersistent(MARKER, true);
        npc.data().setPersistent(NPC.Metadata.COLLIDABLE, false);
        npc.data().setPersistent(NPC.Metadata.DEFAULT_PROTECTED, true);
        if (skinName != null && !skinName.isBlank()) {
            npc.getOrAddTrait(SkinTrait.class).setSkinName(skinName);
        }
        npc.spawn(location);
        Bukkit.getLogger().info("[Werwolf] Menü-NPC in der Lobby erstellt.");
    }
}
