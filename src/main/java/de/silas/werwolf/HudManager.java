package de.silas.werwolf;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

/**
 * Sidebar: Rolle, Status, Tag, Lebende.
 */
public class HudManager {

    private final WerwolfPlugin plugin;

    public HudManager(WerwolfPlugin plugin) {
        this.plugin = plugin;
    }

    public void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            update(player);
        }
    }

    public void update(Player player) {
        GameManager game = plugin.gameManager();
        if (game.state() == GameManager.State.LOBBY) {
            Scoreboard lobby = Bukkit.getScoreboardManager().getNewScoreboard();
            Objective lobbyObjective = lobby.registerNewObjective("lobby", Criteria.DUMMY,
                    Component.text("WERWOLF", NamedTextColor.DARK_RED));
            lobbyObjective.setDisplaySlot(DisplaySlot.SIDEBAR);
            setLine(lobby, lobbyObjective, 5, "§7Lobby", "");
            setLine(lobby, lobbyObjective, 4, "§7Online:", "§f " + Bukkit.getOnlinePlayers().size());
            setLine(lobby, lobbyObjective, 3, "§7Status:", "§a Warten…");
            setLine(lobby, lobbyObjective, 2, "", "");
            setLine(lobby, lobbyObjective, 1, "§8Düsterwald", "");
            player.setScoreboard(lobby);
            return;
        }

        Scoreboard board = Bukkit.getScoreboardManager().getNewScoreboard();
        Objective objective = board.registerNewObjective("werwolf", Criteria.DUMMY,
                Component.text("WERWOLF", NamedTextColor.DARK_RED));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);

        Role role = game.roleOf(player);
        boolean narrator = game.isNarrator(player.getUniqueId());
        String roleText = narrator ? "Erzähler" : (role != null ? role.displayName() : "Zuschauer");

        boolean dead = !game.alive().contains(player.getUniqueId()) && role != null;

        Player mayor = game.mayorId() != null ? Bukkit.getPlayer(game.mayorId()) : null;
        setLine(board, objective, 7, "§7Rolle:", "§f " + roleText);
        setLine(board, objective, 6, "§7Status:", dead ? "§c Tot" : "§a Lebend");
        setLine(board, objective, 5, "§7Tag:", "§f " + game.day());
        setLine(board, objective, 4, "§7Lebende:", "§f " + game.alive().size());
        setLine(board, objective, 3, "§7Bürgermeister:", "§6 " + (mayor != null ? mayor.getName() : "–"));
        setLine(board, objective, 2, "", "");
        setLine(board, objective, 1, "§8Düsterwald", "");

        player.setScoreboard(board);
    }

    private void setLine(Scoreboard board, Objective objective, int score, String prefix, String suffix) {
        String key = "§" + (char) ('a' + score);
        Team team = board.getTeam("line" + score);
        if (team == null) {
            team = board.registerNewTeam("line" + score);
        }
        team.addEntry(key);
        team.prefix(Component.text(prefix));
        team.suffix(Component.text(suffix));
        objective.getScore(key).setScore(score);
    }

    public void clear(Player player) {
        player.setScoreboard(Bukkit.getScoreboardManager().getMainScoreboard());
    }

    public void clearAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            clear(player);
        }
    }
}
