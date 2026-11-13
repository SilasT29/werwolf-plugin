package de.silas.werwolf;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * /build – Teleport in den Baubereich und Kreativmodus (nur OPs).
 */
public class BuildCommand implements CommandExecutor {

    private static final double X = -31.03;
    private static final double Y = 120.12;
    private static final double Z = -420.03;

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("Nur ingame verfügbar.", NamedTextColor.RED));
            return true;
        }
        if (!player.hasPermission("werwolf.admin")) {
            player.sendMessage(Component.text("Keine Berechtigung.", NamedTextColor.RED));
            return true;
        }

        World world = Bukkit.getWorld("world");
        if (world == null) {
            world = player.getWorld();
        }
        player.teleport(new Location(world, X, Y, Z));
        player.setGameMode(GameMode.CREATIVE);
        player.sendMessage(Component.text("Baumodus: " + String.format("%.2f %.2f %.2f", X, Y, Z), NamedTextColor.GREEN));
        return true;
    }
}
