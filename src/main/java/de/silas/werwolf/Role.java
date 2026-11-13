package de.silas.werwolf;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.potion.PotionType;

/**
 * Die Rollen, die im Spielleitungs-Menü vergeben werden können.
 * Slot = Position im 54er-Inventar (Reihe 2).
 */
public enum Role {

    WERWOLF("Werwolf", NamedTextColor.DARK_RED, Material.NETHERITE_SWORD, 10, null,
            "Tötet nachts einen Spieler."),
    DORFBEWOHNER("Dorfbewohner", NamedTextColor.GREEN, Material.EMERALD, 11, null,
            "Hat keine Nachtaktion, stimmt tagsüber ab."),
    HEXE("Hexe", NamedTextColor.LIGHT_PURPLE, Material.POTION, 12, PotionType.HEALING,
            "Ein Heiltrank und ein Gifttrank pro Spiel."),
    EINZELGAENGER("Blinzelmädchen", NamedTextColor.AQUA, Material.POTION, 22, PotionType.INVISIBILITY,
            "Steht nachts auf, ohne Schwert – wird sie gesehen, ist sie entdeckt."),
    SEHER("Seher", NamedTextColor.WHITE, Material.ENDER_EYE, 4, null,
            "Sieht nachts die Rolle eines Spielers."),
    DORFSCHLAMPE("Dorfschlampe", NamedTextColor.RED, Material.END_ROD, 14, null,
            "Übernachtet bei einem Spieler."),
    AMOR("Amor", NamedTextColor.RED, Material.BOW, 15, null,
            "Verkuppelt in der ersten Nacht zwei Spieler."),
    JAEGER("Jäger", NamedTextColor.GREEN, Material.CROSSBOW, 16, null,
            "Darf beim Tod einen Spieler mitnehmen.");

    private final String displayName;
    private final NamedTextColor color;
    private final Material material;
    private final int slot;
    private final PotionType potionType;
    private final String description;

    Role(String displayName, NamedTextColor color, Material material, int slot, PotionType potionType, String description) {
        this.displayName = displayName;
        this.color = color;
        this.material = material;
        this.slot = slot;
        this.potionType = potionType;
        this.description = description;
    }

    public String displayName() {
        return displayName;
    }

    public NamedTextColor color() {
        return color;
    }

    public Material material() {
        return material;
    }

    public int slot() {
        return slot;
    }

    public PotionType potionType() {
        return potionType;
    }

    public String description() {
        return description;
    }

    /** Anleitung für den Spieler beim Rundenstart. */
    public String[] guide() {
        return switch (this) {
            case WERWOLF -> new String[]{
                    "Du wachst jede Nacht mit den anderen Wölfen auf und bekommst die Werwolfsklaue.",
                    "Schleiche zu einem Schlafenden und schlage mit Rechtsklick zu.",
                    "Alle Wölfe müssen dasselbe Opfer treffen – sonst stirbt niemand. Zeit: 60 s."};
            case DORFBEWOHNER -> new String[]{
                    "Du hast keine Nachtaktion.",
                    "Tagsüber diskutierst du mit und stimmst bei der Abstimmung ab.",
                    "Ziel: Finde die Wölfe, bevor sie das Dorf auslöschen."};
            case HEXE -> new String[]{
                    "Nachts (nach der Seherin) öffnet sich dein Trank-Menü.",
                    "Heiltrank (1× pro Spiel): rettet das Opfer der Wölfe.",
                    "Gifttrank (1× pro Spiel): tötet einen Spieler deiner Wahl.",
                    "Zeit: 45 s – danach verfällt die Aktion."};
            case EINZELGAENGER -> new String[]{
                    "Du bist das Blinzelmädchen und darfst nachts aufstehen und herumlaufen.",
                    "Du hast kein Schwert – aber wenn ein Wolf dich wach erwischt, stirbst du sofort.",
                    "Du gewinnst mit dem Dorf: Alle Wölfe müssen sterben."};
            case SEHER -> new String[]{
                    "Nachts (nach den Wölfen) öffnet sich dein Kopf-Menü.",
                    "Klicke einen Spieler an und du siehst seine Rolle – nur du.",
                    "Zeit: 45 s. Nutze die Information am Tag, ohne dich zu verraten."};
            case DORFSCHLAMPE -> new String[]{
                    "Nachts wählst du einen Spieler, bei dem du übernachtest.",
                    "Stirbt dieser Spieler in derselben Nacht, stirbst du mit.",
                    "Zeit: 45 s."};
            case AMOR -> new String[]{
                    "Nur in der ersten Nacht: Wähle zwei Spieler aus.",
                    "Sie werden ein Liebespaar – stirbt einer, stirbt der andere aus Liebeskummer.",
                    "Klicke beide an und bestätige mit dem grünen Button. Zeit: 45 s."};
            case JAEGER -> new String[]{
                    "Stirbst du (nachts oder durch Lynch), öffnet sich dein Ziel-Menü.",
                    "Klicke einen Spieler an, um ihn mit in den Tod zu nehmen.",
                    "Oder wähle \"Nicht schießen\". Zeit: 20 s."};
        };
    }

    public static Role bySlot(int slot) {
        for (Role role : values()) {
            if (role.slot == slot) {
                return role;
            }
        }
        return null;
    }
}
