package de.silas.werwolf;

import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class WerwolfPlugin extends JavaPlugin {

    private MenuManager menuManager;
    private GameManager gameManager;
    private ConfigStore config;
    private SeatManager seats;
    private CitizensHook citizens;

    public CitizensHook citizens() {
        return citizens;
    }
    private CinematicManager cinematic;
    private OverlayManager overlay;
    private MirrorNpcManager mirror;
    private VoteManager vote;
    private HudManager hud;

    @Override
    public void onEnable() {
        this.citizens = new CitizensHook();
        this.config = new ConfigStore(this);
        this.seats = new SeatManager();
        this.cinematic = new CinematicManager(this, config, citizens);
        this.overlay = new OverlayManager(this, config);
        this.mirror = new MirrorNpcManager(this);
        this.vote = new VoteManager(this);
        this.hud = new HudManager(this);
        this.gameManager = new GameManager(this);
        this.menuManager = new MenuManager(this);

        getServer().getPluginManager().registerEvents(new MenuListener(this, menuManager), this);
        getServer().getPluginManager().registerEvents(new GameListener(this, cinematic, seats), this);

        if (citizens.available()) {
            // Citizens lädt seine NPCs erst nach uns – deshalb mehrfach verzögert aufräumen.
            for (long delay : new long[]{100L, 300L, 600L}) {
                Bukkit.getScheduler().runTaskLater(this, MenuNpc::destroyAll, delay);
            }
        }
        getServer().getPluginManager().registerEvents(mirror, this);
        mirror.start();

        PluginCommand buildCommand = getCommand("build");
        if (buildCommand != null) {
            buildCommand.setExecutor(new BuildCommand());
        }

        PluginCommand command = getCommand("werwolf");
        if (command != null) {
            WerwolfCommand executor = new WerwolfCommand(this);
            command.setExecutor(executor);
            command.setTabCompleter(executor);
        }

        for (org.bukkit.World world : Bukkit.getWorlds()) {
            world.setGameRule(org.bukkit.GameRule.DO_IMMEDIATE_RESPAWN, true);
        }

        Bukkit.getScheduler().runTaskTimer(this, cinematic::enforceSleep, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(this, () -> gameManager.enforceSpectatorBounds(), 20L, 10L);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (gameManager.state() == GameManager.State.LOBBY) {
                hud.updateAll();
            }
        }, 100L, 100L);

        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (citizens.available()) {
                int removed = citizens.cleanupMarked();
                getLogger().info("NPC-Aufräumen beim Start: " + removed + " entfernt.");
            }
        }, 40L);
        getLogger().info("Werwolf aktiviert (Citizens: " + (citizens.available() ? "ja" : "nein") + ").");
    }

    @Override
    public void onDisable() {
        if (seats != null) {
            seats.removeAll();
        }
        if (overlay != null) {
            overlay.hide();
        }
        if (mirror != null) {
            mirror.stop();
        }
        getLogger().info("Werwolf deaktiviert.");
    }

    public MenuManager menus() {
        return menuManager;
    }

    public GameManager gameManager() {
        return gameManager;
    }

    public ConfigStore config() {
        return config;
    }

    public SeatManager seats() {
        return seats;
    }

    public CinematicManager cinematic() {
        return cinematic;
    }

    public OverlayManager overlay() {
        return overlay;
    }

    public int overlayCount() {
        return overlay.count();
    }

    public org.bukkit.entity.Mannequin mirrorDebugSpawn(org.bukkit.entity.Player player) {
        return mirror.debugSpawn(player);
    }

    public int mirrorDebugInfo(org.bukkit.entity.Player player) {
        return mirror.debugInfo(player);
    }

    public HudManager hud() {
        return hud;
    }

    public VoteManager vote() {
        return vote;
    }

    public boolean citizensAvailable() {
        return citizens.available();
    }
}
