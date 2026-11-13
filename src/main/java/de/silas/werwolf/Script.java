package de.silas.werwolf;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;

/**
 * Erzähler-Skript: feste Texte zu jedem Phasewechsel.
 */
public final class Script {

    private Script() {
    }

    public static void say(String text) {
        Bukkit.broadcast(Component.text("[Erzähler] ", NamedTextColor.GOLD)
                .append(Component.text(text, NamedTextColor.GRAY)));
    }

    public static final String NIGHT_FALLS =
            "Die Sonne versinkt hinter den Hügeln. Das Dorf schließt die Türen – die Nacht beginnt.";
    public static final String SLUT =
            "Eine Gestalt schleicht durch die Gassen und sucht sich ein warmes Bett für die Nacht …";
    public static final String WOLVES =
            "Die Werwölfe erwachen. Sie hungern, und sie müssen sich einig werden.";
    public static final String SEER =
            "Die Seherin blickt in ihre Kugel und sucht die Wahrheit hinter den Gesichtern.";
    public static final String WITCH =
            "Die Hexe steht über ihren Tränken. Ein Leben retten oder ein Leben nehmen – sie entscheidet.";
    public static final String MORNING =
            "Der Morgen graut. Das Dorf erwacht und schaut, wer die Nacht nicht überlebt hat.";
    public static final String VOTE =
            "Das Dorf versammelt sich. Einer von euch ist ein Mörder – stimmt ab.";
    public static final String MAYOR =
            "Das Dorf braucht eine Stimme, die zählt. Wählt euren Bürgermeister.";
    /** Mehrere Zeilen mit 3 s Abstand (nur ohne Erzähler). */
    public static void story(WerwolfPlugin plugin, String... lines) {
        for (int i = 0; i < lines.length; i++) {
            final String line = lines[i];
            org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> say(line), i * 60L);
        }
    }

    public static final String[] INTRO = {
            "Der Wald um das Dorf ist alt. Älter als die Häuser, älter als die Wege, älter als jedes Wort, das hier gesprochen wird.",
            "Man sagt, wer nach Einbruch der Dunkelheit noch draußen ist, hört sie atmen – zwischen den Bäumen, hinter den Zäunen.",
            "Heute Nacht hat der Wald einen neuen Bewohner. Und das Dorf hat einen neuen Grund, die Türen zu verriegeln."
    };

    public static final String[] NIGHT_STORY = {
            "Die letzten Lichter erlöschen. Der Wald schluckt jedes Geräusch.",
            "Irgendwo zwischen den Häusern knirscht ein Ast. Dann ist es still – zu still."
    };

    public static final String[] MORNING_STORY = {
            "Graues Licht kriecht über die Dächer. Der Nebel hängt zwischen den Bäumen wie eine Warnung.",
            "Der Wald gibt seine Geheimnisse nicht preis. Aber das Dorf zählt, wer noch da ist."
    };

    public static final String LYNCH =
            "Das Urteil ist gefallen. Das Dorf lässt den Strick nicht lange warten.";
}
