package de.silas.werwolf;

import net.citizensnpcs.api.CitizensAPI;
import net.citizensnpcs.api.npc.NPC;
import net.citizensnpcs.trait.SitTrait;
import net.citizensnpcs.trait.SkinTrait;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.event.player.PlayerTeleportEvent;

/**
 * Dünne Hülle um Citizens. Nur aufrufen, wenn Citizens installiert ist (available()).
 */
public class CitizensHook {

    public boolean available() {
        return Bukkit.getPluginManager().isPluginEnabled("Citizens");
    }

    private NPC create(Player player) {
        NPC npc = CitizensAPI.getNPCRegistry().createNPC(EntityType.PLAYER, player.getName());
        npc.data().setPersistent("werwolf_npc", true);
        npc.data().setPersistent(NPC.Metadata.NAMEPLATE_VISIBLE, false);
        npc.data().setPersistent(NPC.Metadata.COLLIDABLE, false);
        npc.data().setPersistent(NPC.Metadata.DEFAULT_PROTECTED, true);
        npc.getOrAddTrait(SkinTrait.class).setSkinPersistent(player);
        return npc;
    }

    public NPC spawnWalker(Player player, Location spawn, List<Location> waypoints, Location target, double speed) {
        NPC npc = create(player);
        npc.spawn(spawn);
        tuneNavigation(npc, speed);
        setRoute(npc, waypoints, target);
        return npc;
    }

    /** Läuft die Wegpunkte der Reihe nach ab und endet am Ziel. */
    public void setRoute(NPC npc, List<Location> waypoints, Location target) {
        List<Vector> route = new ArrayList<>();
        if (waypoints != null) {
            for (Location waypoint : waypoints) {
                route.add(waypoint.toVector());
            }
        }
        route.add(target.toVector());
        if (route.size() == 1) {
            npc.getNavigator().setTarget(target);
        } else {
            npc.getNavigator().setTarget(route);
        }
        Bukkit.getLogger().info("[Werwolf] NPC " + npc.getName() + " Route: " + route.size()
                + " Punkte, navigating=" + npc.getNavigator().isNavigating());
    }

    /** NPC sitzt auf dem Sitz und wartet. */
    public NPC spawnSitting(Player player, Location seat) {
        NPC npc = create(player);
        npc.spawn(seat);
        npc.getOrAddTrait(SitTrait.class).setSitting(seat);
        return npc;
    }

    /** NPC steht vom Sitz auf und läuft los. */
    public void standAndWalk(NPC npc, Location from, Location target, double speed) {
        if (npc == null || !npc.isSpawned()) {
            return;
        }
        if (npc.hasTrait(SitTrait.class)) {
            npc.removeTrait(SitTrait.class);
        }
        // Falls der NPC noch auf dem Sitz-Entity sitzt: lösen und auf den Boden setzen.
        if (npc.getEntity() != null && npc.getEntity().isInsideVehicle()) {
            npc.getEntity().leaveVehicle();
        }
        npc.teleport(from, PlayerTeleportEvent.TeleportCause.PLUGIN);
        tuneNavigation(npc, speed);
        npc.getNavigator().setTarget(target);
        Bukkit.getLogger().info("[Werwolf] NPC " + npc.getName() + " navigating=" + npc.getNavigator().isNavigating());
    }

    private void tuneNavigation(NPC npc, double speed) {
        // Kein Teleport bei "stuck" – sonst springt der NPC zum Ziel.
        npc.data().setPersistent(NPC.Metadata.DISABLE_DEFAULT_STUCK_ACTION, true);
        npc.getNavigator().getDefaultParameters()
                .baseSpeed((float) speed)
                .distanceMargin(2.5)
                .pathDistanceMargin(2.5);
    }

    /** Entfernt alle von diesem Plugin erzeugten NPCs (auch alte Reste). */
    public int cleanupMarked() {
        int removed = 0;
        List<NPC> all = new ArrayList<>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            all.add(npc);
        }
        for (NPC npc : all) {
            if (Boolean.TRUE.equals(npc.data().get("werwolf_npc"))) {
                npc.destroy();
                removed++;
            }
        }
        return removed;
    }

    /** Entfernt ALLE Citizens-NPCs (nur auf ausdrücklichen Befehl). */
    public int cleanupAll() {
        int removed = 0;
        List<NPC> all = new ArrayList<>();
        for (NPC npc : CitizensAPI.getNPCRegistry()) {
            all.add(npc);
        }
        for (NPC npc : all) {
            npc.destroy();
            removed++;
        }
        return removed;
    }

    public void remove(NPC npc) {
        if (npc != null) {
            npc.destroy();
        }
    }
}
