# Werwolf – Minecraft Minigame

Ein vollständiges Werwolf-/Mafia-Minigame als Paper-Plugin (Minecraft 26.3), gebaut für einen eigenen Dorf-Server mit 20 Häusern, Kino-Nächten und Erzähler.

## Features

- **Lobby & Menü:** Lobby-Spawn, Menü-NPC mit Spieler-Skin (sieht jedem Spieler in die Augen), Rollenmenü (27 Slots) mit klickbaren Rollen und grünem Start-Button
- **Rollen:** Werwolf, Dorfbewohner, Hexe, Blinzelmädchen, Seherin, Dorfschlampe, Amor, Jäger
- **Bürgermeister:** Wahl nach der ersten Nacht, doppelte Stimme, Nachfolger bei Tod
- **Tag:** alle sitzen auf unsichtbaren Stühlen am Lagerfeuer, sind schadensimmun, dürfen sprechen
- **Nacht:** Kino-Sequenz (Kamera, NPCs laufen zu den Häusern, Türen, Betten), Rollenphasen mit Actionbar-Countdown (45 s, Wölfe 60 s)
- **Wölfe:** gemeinsames Opfer per Werwolfsklaue, vergrößerte Bett-Hitbox, 2 s Puffer, zurück ins Bett
- **Hexe:** Heiltrank/Gifttrank (je 1× pro Spiel), Zurück-Button im Untermenü
- **Amor:** 54-Slot-Auswahl mit zwei Köpfen und grünem Bestätigen-Button, Liebeskummer
- **Jäger:** Schuss-Menü beim Tod
- **Tote:** Spectator, Toten-Chat, 40-Block-Radius, kein Kino/Bett
- **Abstimmung:** Kopf-Menü, Live-Stimmenzahlen, Stichwahl, Skip-Button (Mehrheit)
- **Sieg:** Dorf/Wölfe-Sieg mit Titel, Sound und Rollen-Auflösung für alle
- **Erzähler:** eigene Rolle, Sitz 20, Kamera in der Nacht, nur er spricht nachts, Items mit 3-s-Cooldown
- **Ohne Erzähler:** kompletter Auto-Ablauf inkl. Wald-Geschichte und Countdowns
- **Scoreboard:** Lobby- und Spiel-Board (Rolle, Status, Tag, Lebende, Bürgermeister)
- **Verwaltung:** `/build`, Overlay, Set-/Delete-Befehle für Häuser, Betten, Türen, Wege, Sitze, NPC-Cleanup

## Bauen

```bash
mvn clean package
# -> target/Werwolf-1.1.0.jar
```

Voraussetzungen: Java 25, Maven 3.9+, Paper 26.3, Citizens 2.0.44 (soft-depend).

## Installation

1. `Werwolf-1.1.0.jar` nach `plugins/` kopieren
2. Server starten (Citizens wird benötigt)
3. Welt einrichten:
   - `/ww setcenter`, `/ww setcamera`, `/ww setlobby`
   - `/ww sethouse <1-20>`, `/ww setbed <1-20>`, `/ww setdoor <1-20>`, `/ww addpath <1-20>`, `/ww setseat <1-20>`
4. `/ww overlay` zum Prüfen der Marker
5. Menü-NPC erscheint automatisch in der Lobby

## Wichtige Befehle

| Befehl | Beschreibung |
|---|---|
| `/ww` | Rollenmenü öffnen |
| `/ww start` | Runde starten |
| `/ww stop` | Runde beenden |
| `/ww narrator <Name>` | Erzähler setzen |
| `/ww narrator off` | Erzähler aus |
| `/ww cleanup [all]` | Werwolf-NPCs (oder alle Citizens-NPCs) entfernen |
| `/build` | Kreativ-Baubereich |

## Projektstruktur

```
src/main/java/de/silas/werwolf/
├── WerwolfPlugin.java        # Einstieg, Verdrahtung
├── GameManager.java          # Rundenlogik, Rollen, Phasen, Sieg
├── CinematicManager.java     # Kino-Nacht, Morgen-Kino, Betten, NPCs
├── VoteManager.java          # Abstimmung, Stichwahl, Skip
├── MenuManager.java          # Rollenmenü
├── MenuListener.java         # GUI-Klicks
├── GameListener.java         # Events (Tod, Chat, Sitze, NPC-Items)
├── ConfigStore.java          # world.yml (Häuser, Sitze, Kamera, Lobby)
├── SeatManager.java          # unsichtbare Stühle
├── CitizensHook.java         # Citizens-NPCs
├── MirrorNpcManager.java     # Lobby-Mannequins
├── HudManager.java           # Scoreboards
├── Script.java               # Erzähler-Texte
├── Role.java                 # Rollen + Anleitungen
└── …                         # Menüs, Commands, Overlay
```

## Hinweise

- Das Plugin erwartet eine eingerichtete Welt mit 20 Häusern und Sitzen; ohne Konfiguration startet keine Runde.
- Voice-Chat-Rechte werden über die Permission `voicechat.speak` gesetzt (Simple Voice Chat).
- Kino- und Morgen-NPCs werden markiert und beim Serverstart automatisch aufgeräumt.
