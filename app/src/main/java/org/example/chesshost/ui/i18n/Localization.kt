package org.example.chesshost.ui.i18n

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text as MaterialText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import java.util.Locale

/**
 * Sprachen der Oberfläche. Deutsch ist die Quellsprache: Alle Texte stehen im
 * Code auf Deutsch, [Translations] liefert die englische Fassung. Fehlt ein
 * Eintrag, bleibt der deutsche Text stehen – die App bricht nie ab.
 */
enum class AppLanguage(val code: String, val label: String) {
    GERMAN("de", "Deutsch"),
    ENGLISH("en", "English"),
    ;

    companion object {
        fun fromCode(code: String?): AppLanguage =
            entries.firstOrNull { it.code == code } ?: GERMAN
    }
}

/** Aktuelle Sprache der Oberfläche; gesetzt in `MainActivity` aus den Einstellungen. */
val LocalLanguage = staticCompositionLocalOf { AppLanguage.GERMAN }

/**
 * Sprache außerhalb von Composables (ViewModels, Services). Wird beim Start und
 * bei jeder Änderung in `MainActivity` aktualisiert.
 */
object LanguageState {
    @Volatile
    var current: AppLanguage = AppLanguage.GERMAN
}

/** Übersetzt einen deutschen UI-Text. */
fun translate(language: AppLanguage, german: String): String {
    if (language == AppLanguage.GERMAN || german.isEmpty()) return german
    if (german.length > 400) return german
    return Translations.english[german] ?: german
}

/** Übersetzt außerhalb von Composables, etwa für Fehlermeldungen aus ViewModels. */
fun trNow(german: String, vararg args: Any): String {
    val text = translate(LanguageState.current, german)
    return if (args.isEmpty()) text else String.format(Locale.getDefault(), text, *args)
}

/** Wie [translate], aber für Aufrufe aus Composables (Sprache aus dem Kontext). */
@Composable
fun tr(german: String, vararg args: Any): String {
    val text = translate(LocalLanguage.current, german)
    return if (args.isEmpty()) text else String.format(Locale.getDefault(), text, *args)
}

/**
 * Anzeigename eines Engine-Pakets: Name und Version werden getrennt übersetzt,
 * damit „Fairy-Stockfish klassisch“ auf Englisch „Fairy-Stockfish classic“ heißt.
 */
@Composable
fun engineTitle(name: String, version: String): String =
    if (version.isBlank()) tr(name) else tr("%s %s", tr(name), tr(version))

/**
 * Ersatz für `androidx.compose.material3.Text`: übersetzt den Text automatisch
 * in die gewählte Sprache. Alle übrigen Parameter werden durchgereicht.
 */
@Composable
fun Text(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    fontSize: TextUnit = TextUnit.Unspecified,
    fontStyle: FontStyle? = null,
    fontWeight: FontWeight? = null,
    fontFamily: FontFamily? = null,
    textAlign: TextAlign? = null,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    onTextLayout: ((TextLayoutResult) -> Unit)? = null,
    style: TextStyle = LocalTextStyle.current,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    textDecoration: TextDecoration? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
) {
    MaterialText(
        text = tr(text),
        modifier = modifier,
        color = color,
        fontSize = fontSize,
        fontStyle = fontStyle,
        fontWeight = fontWeight,
        fontFamily = fontFamily,
        textAlign = textAlign,
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
        onTextLayout = onTextLayout,
        style = style,
        letterSpacing = letterSpacing,
        textDecoration = textDecoration,
        lineHeight = lineHeight,
    )
}

/** Übersetzungstabelle Deutsch → Englisch. Deutsch braucht keine Tabelle. */
object Translations {

    val english: Map<String, String> = mapOf(
        // Menü und Navigation
        "Spielen, zusehen, analysieren – mit Stockfish an Bord." to
            "Play, watch, analyse – with Stockfish on board.",
        "Partie starten" to "Start a game",
        "Gegen Stockfish – normal oder Schach960" to "Against Stockfish – standard or Chess960",
        "Engine gegen Engine" to "Engine vs engine",
        "Zwei Stufen automatisch gegeneinander" to "Two levels playing each other automatically",
        "Turniere" to "Tournaments",
        "Engines automatisch gegeneinander – Tabelle und Diagramme" to
            "Engines playing each other – standings and charts",
        "Partien" to "Games",
        "Gespeicherte PGN-Partien ansehen und nachspielen" to
            "Review and replay saved PGN games",
        "Stellungen" to "Positions",
        "Stellung bauen, speichern und von dort spielen – inklusive Schach960" to
            "Build, save and play from a position – including Chess960",
        "Engines" to "Engines",
        "Katalog ansehen, auswählen und nachinstallieren" to
            "Browse the catalogue, choose and install engines",
        "Einstellungen" to "Settings",
        "Spielen" to "Play",
        "Wettkampf" to "Competition",
        "Werkzeuge" to "Tools",
        "App" to "App",
        "Sprache, Darstellung, Schwierigkeit, Ton" to
            "Language, appearance, difficulty, sound",
        // Allgemein
        "Zurück" to "Back",
        "Abbrechen" to "Cancel",
        "Speichern" to "Save",
        "Löschen" to "Delete",
        "Alle löschen" to "Delete all",
        "Neu" to "New",
        "Fertig" to "Done",
        "Prüfen" to "Check",
        "Teilen" to "Share",
        "Start" to "Start",
        "Starten" to "Start",
        "Pause" to "Pause",
        "Weiter" to "Resume",
        "Fortsetzen" to "Resume",
        "Neue Partie" to "New game",
        "Aufgeben" to "Resign",
        "Zug zurück" to "Undo",
        "Tipp" to "Hint",
        "Remis anbieten" to "Offer draw",
        "Bewertung" to "Evaluation",
        "Beste Variante" to "Best line",
        "Denken …" to "Thinking…",
        "Bereit – Start drücken" to "Ready – press start",
        "Partie beendet" to "Game over",
        "Am Zug" to "To move",
        "Du bist am Zug" to "Your move",
        "Weiß am Zug" to "White to move",
        "Schwarz am Zug" to "Black to move",
        "Du" to "You",
        "Spieler" to "Player",
        "Noch keine Züge" to "No moves yet",
        "Noch keine Partien gespeichert. Beendete Partien erscheinen hier mit PGN." to
            "No games saved yet. Finished games appear here with their PGN.",
        "Partie löschen" to "Delete game",
        "Alle Partien löschen?" to "Delete all games?",
        "Diese Aktion kann nicht rückgängig gemacht werden." to "This cannot be undone.",
        "PGN teilen" to "Share PGN",
        "Partie wird geladen …" to "Loading game…",
        "Partie" to "Game",
        "Züge" to "Moves",
        "Startstellung" to "Starting position",
        "Spiel gegen Engine" to "Play vs engine",
        "Züge ·" to "moves ·",
        // Einstellungen
        "Darstellung" to "Appearance",
        "Sprache" to "Language",
        "Farbschema" to "Colour scheme",
        "System" to "System",
        "Hell" to "Light",
        "Dunkel" to "Dark",
        "Brett" to "Board",
        "Koordinaten anzeigen" to "Show coordinates",
        "Datei- und Reihenbeschriftung auf dem Brett" to "File and rank labels on the board",
        "Legale Züge zeigen" to "Show legal moves",
        "Markiert erlaubte Zielfelder der gewählten Figur" to
            "Highlights the legal targets of the selected piece",
        "Letzten Zug markieren" to "Highlight the last move",
        "Hebt Start- und Zielfeld hervor" to "Highlights the from and to squares",
        "Ton und Haptik" to "Sound and haptics",
        "Ton" to "Sound",
        "Töne" to "Sounds",
        "Haptik" to "Haptics",
        "Vibration bei Zügen" to "Vibration on moves",
        "Züge und Schach hörbar machen" to "Make moves and checks audible",
        "Wird sofort übernommen – ohne Neustart." to "Applied immediately – no restart needed.",
        "Engine" to "Engine",
        "Standardstufe" to "Default level",
        "Engines verwalten" to "Manage engines",
        "Über" to "About",
        "Schach für Android · Version 1.0" to "Chess for Android · version 1.0",
        "Engine: Stockfish 17.1 (GPLv3, stockfishchess.org). Figuren: Cburnett-Schachfiguren von Colin M. L. Burnett, CC BY-SA 3.0, via Wikimedia Commons." to
            "Engine: Stockfish 17.1 (GPLv3, stockfishchess.org). Pieces: Cburnett chess pieces by Colin M. L. Burnett, CC BY-SA 3.0, via Wikimedia Commons.",
        // Spiel
        "Gegner" to "Opponent",
        "Schwierigkeit" to "Difficulty",
        "Regelwerk und Startstellung" to "Rule set and starting position",
        "Regelwerk, Startstellung und Bedenkzeit – eine Änderung beginnt eine neue Partie." to
            "Rule set, starting position and time control – a change starts a new game.",
        "Zufällige 960-Stellung" to "Random 960 position",
        "Standardstellung" to "Standard position",
        "Grundstellung" to "Initial position",
        "Bedenkzeit" to "Time control",
        "Zeitklasse" to "Time class",
        "Denkzeit je Zug" to "Thinking time per move",
        "Ohne Uhr" to "No clock",
        "Bullet" to "Bullet",
        "Blitz" to "Blitz",
        "Schnellschach" to "Rapid",
        "Klassisch" to "Classical",
        "Fernschach" to "Correspondence",
        "Anfänger" to "Beginner",
        "Leicht" to "Easy",
        "Verein" to "Club",
        "Vereinsspieler" to "Club player",
        "Fortgeschritten" to "Advanced",
        "Stark" to "Strong",
        "Meister" to "Master",
        "Maximal" to "Maximum",
        "Keine Engine aktiv" to "No engine active",
        "Engines wählen" to "Choose engines",
        "Diese App bringt ab Werk keine Engine mit. Wähle im Store eine Engine aus, danach kannst du hier gegen sie spielen. Alternativ lässt sich ein LLM-Gegner über eine API in den Einstellungen einrichten." to
            "This app ships without an engine. Choose one in the store, then you can play against it here. Alternatively, an LLM opponent can be set up via an API in the settings.",
        "Du · Weiß" to "You · White",
        "Du · Schwarz" to "You · Black",
        "%s überlegt …" to "%s is thinking…",
        "Analyse" to "Analysis",
        "Bewerten" to "Evaluate",
        "Rechnet …" to "Calculating…",
        "Engine rechnet …" to "Engine is thinking…",
        "Bewertet die Stellung und zeigt den besten Zug." to
            "Evaluates the position and shows the best move.",
        "Tiefe %s" to "Depth %s",
        "Tipp: %s" to "Hint: %s",
        "Beste Variante: %s" to "Best line: %s",
        "Matt in %s" to "Mate in %s",
        "50-Züge-Regel: %s von 100 Halbzügen" to "50-move rule: %s of 100 half-moves",
        // Stellungen
        "Eigene Stellungen" to "Your positions",
        "Noch keine eigenen Stellungen." to "No saved positions yet.",
        "Neue Stellung" to "New position",
        "Stellung bearbeiten" to "Edit position",
        "Stellung bauen" to "Build position",
        "Name der Stellung" to "Position name",
        "FEN einfügen" to "Paste FEN",
        "Gespeicherte FEN ist ungültig." to "Saved FEN is invalid.",
        "FEN ist ungültig oder unvollständig." to "FEN is invalid or incomplete.",
        "Die Stellung ist ungültig." to "The position is invalid.",
        "Stellung nicht gefunden." to "Position not found.",
        "Von einer Stellung spielen" to "Play from a position",
        "Seite am Zug" to "Side to move",
        "Rochaderechte" to "Castling rights",
        "Figuren" to "Pieces",
        "Leeren" to "Clear",
        "Gegen Engine" to "Against engine",
        "Verschiedene Startstellungen" to "Different starting positions",
        "Weiß kurz" to "White short",
        "Schwarz kurz" to "Black short",
        "Weiß" to "White",
        "Schwarz" to "Black",
        "Tippen setzt die gewählte Figur, der Radierer entfernt sie. Ein unbesetztes Feld lässt sich mit dem Radierer leeren." to
            "Tap to place the selected piece, the eraser removes it. An empty square can be cleared with the eraser.",
        "Baue im Editor eine Stellung, speichere sie und starte von dort eine Partie gegen eine Engine – oder ein Duell zweier Engines. Gespeichert wird eine FEN, alles andere läuft wie gewohnt." to
            "Build a position in the editor, save it and start a game against an engine from there – or a duel between two engines. Only a FEN is stored; everything else works as usual.",
        "Auf dieser Seite steht kein Turm." to "There is no rook on that side.",
        "Für die Rochade fehlt der %s König." to "Castling needs the %s king.",
        "Für die Rochade fehlt der %s Turm." to "Castling needs the %s rook.",
        // Store
        "Verfügbare Engines" to "Available engines",
        "Nicht verfügbar" to "Unavailable",
        "Auf diesem Gerät" to "On this device",
        "Zum Download" to "Download",
        "Katalog erweitern" to "Extend catalogue",
        "Noch keine Engine aktiviert" to "No engine activated yet",
        "Jede Engine kann im Spiel gegen dich oder im Duell gegen eine andere Engine antreten. Neue Einträge kommen über den Katalog, ohne dass die App neu gebaut werden muss." to
            "Every engine can play against you or against another engine. New entries come from the catalogue, without rebuilding the app.",
        "Neue Einträge kommen über den Katalog, ohne dass die App neu gebaut werden muss." to
            "New entries come from the catalogue, without rebuilding the app.",
        "Trage die URL einer JSON-Datei mit derselben Struktur wie engines/catalog.json ein. Einträge mit gleicher ID überschreiben den eingebauten Eintrag, neue IDs werden ergänzt." to
            "Enter the URL of a JSON file with the same structure as engines/catalog.json. Entries with the same ID replace the built-in one, new IDs are added.",
        "Nativ (UCI)" to "Native (UCI)",
        "Extern (UCI)" to "External (UCI)",
        "Neuronales Netz" to "Neural network",
        "Interne Engine" to "Internal engine",
        "Startet …" to "Starting…",
        "Auf diesem Gerät nicht verfügbar." to "Not available on this device.",
        "Engine-Datei fehlt in dieser App-Version." to "Engine file missing in this app version.",
        "Android 10+ erlaubt das Starten nachgeladener Programme nicht. Dafür kommt das Runtime-Paket." to
            "Android 10+ does not allow starting downloaded programs. The runtime package is used instead.",
        "Noch nicht installiert – im Store herunterladen." to "Not installed yet – download it in the store.",
        "Noch nicht heruntergeladen – im Store installieren." to "Not downloaded yet – install it in the store.",
        "Noch nicht aktiviert – im Store hinzufügen." to "Not activated yet – add it in the store.",
        "Noch nicht verfügbar." to "Not available yet.",
        "Nicht mehr im Katalog" to "No longer in the catalogue",
        "%s Paket(e) entfernt." to "%s package(s) removed.",
        "%s Paket(e) · %s belegt" to "%s package(s) · %s used",
        "%s ist installiert." to "%s is installed.",
        "%s ist aktiviert." to "%s is activated.",
        "%s wurde entfernt." to "%s was removed.",
        "%s und wurde wieder entfernt." to "%s and was removed again.",
        "%s konnte nicht vollständig gelöscht werden." to "%s could not be fully deleted.",
        "Download von %s abgebrochen." to "Download of %s cancelled.",
        "Installation von %s fehlgeschlagen." to "Installation of %s failed.",
        "%s läuft auf diesem Gerät nicht %s" to "%s does not run on this device %s",
        "%s läuft auf diesem Gerät nicht und wurde wieder entfernt." to
            "%s does not run on this device and was removed again.",
        "Enthalten" to "Included",
        "%s Einträge aus dem Netzkatalog aktiv." to "%s entries from the online catalogue active.",
        "%s Einträge aus dem Netzkatalog geladen." to "%s entries loaded from the online catalogue.",
        "Eingebauter Katalog aktiv." to "Built-in catalogue active.",
        // Turniere
        "Neues Turnier" to "New tournament",
        "Turnier starten" to "Start tournament",
        "Turnier abbrechen" to "Abort tournament",
        "Turnier fortsetzen" to "Resume tournament",
        "Turnier löschen" to "Delete tournament",
        "Turnier läuft" to "Tournament running",
        "Noch kein Turnier" to "No tournament yet",
        "Es läuft bereits ein Turnier." to "A tournament is already running.",
        "Es läuft bereits ein Turnier. Beende oder brich es zuerst ab." to
            "A tournament is already running. Finish or abort it first.",
        "Teilnehmer" to "Participants",
        "Partienzahl" to "Number of games",
        "Schwierigkeit je Engine" to "Difficulty per engine",
        "Eröffnungen" to "Openings",
        "mit wechselnden Eröffnungen" to "with rotating openings",
        "alle Partien ab Grundstellung" to "all games from the initial position",
        "Führt gerade" to "Currently leading",
        "Turniersieger" to "Tournament winner",
        "Läuft" to "Running",
        "Pausiert" to "Paused",
        "pausiert" to "paused",
        "Beendet" to "Finished",
        "Abgebrochen" to "Aborted",
        "Tabelle" to "Standings",
        "Kreuztabelle" to "Crosstable",
        "Live-Brett" to "Live board",
        "Zeile gegen Spalte, gelesen als Siege/Remis/Niederlagen." to
            "Row versus column, read as wins/draws/losses.",
        "Wähle zwei oder mehr Engines, lege die Partienzahl fest und lasse sie gegeneinander spielen. Die App zeigt danach Tabelle, Diagramme und Kreuztabelle." to
            "Choose two or more engines, set the number of games and let them play each other. Afterwards you get standings, charts and a crosstable.",
        "Mindestens zwei auswählen. Jede Engine spielt gegen jede andere mit beiden Farben." to
            "Choose at least two. Every engine plays every other engine with both colours.",
        "Ohne das spielen zwei Engines mit fester Bedenkzeit immer dieselbe Partie. Mit 27 Eröffnungen wird jede Partie von einer anderen Stellung gestartet." to
            "Without it, two engines with a fixed thinking time always play the same game. With 27 openings, every game starts from a different position.",
        "Schach960 startet jede Partie von einer anderen Grundstellung mit Rochade über die tatsächlichen Turmfelder. Maia spielt nur Standard-Schach und fehlt deshalb dort." to
            "Chess960 starts every game from a different initial position with castling over the actual rook squares. Maia only plays standard chess and is therefore missing there.",
        "Bullet, Blitz, Schnellschach oder klassisch: Die Uhr gilt für alle Partien. Läuft sie ab, verliert die Engine. „Ohne Uhr“ ist Fernschach – es wird nie auf Zeit verloren." to
            "Bullet, blitz, rapid or classical: the clock applies to every game. If it runs out, the engine loses. “No clock” is correspondence – you never lose on time.",
        "Obergrenze für das Rechnen je Zug. Mit Uhr denkt eine Engine höchstens ein Dreißigstel ihrer Restzeit; die Denkzeit wird von ihrer Uhr abgezogen." to
            "Upper bound for thinking per move. With a clock, an engine thinks at most one thirtieth of its remaining time; the thinking time is deducted from its clock.",
        "Wie lange eine Engine je Zug rechnen darf." to "How long an engine may think per move.",
        "Das Turnier wurde unterbrochen. Die gespielten Partien bleiben erhalten." to
            "The tournament was interrupted. Games already played are kept.",
        "Gerade: %s gegen %s" to "Now: %s vs %s",
        "Live-Brett · Partie %s" to "Live board · game %s",
        "%s (Weiß) gegen %s (Schwarz)" to "%s (White) vs %s (Black)",
        "Bedenkzeit %s · %s" to "Time control %s · %s",
        "Siege · Remis · Niederlagen" to "Wins · draws · losses",
        "%s Niederlagen" to "%s losses",
        "%s gespielte Partien" to "%s games played",
        "Die letzten 20 von %s Partien" to "The last 20 of %s games",
        "Stufe %s (Skill %s) · %s" to "Level %s (skill %s) · %s",
        "Threads %s · Hash %s MB" to "Threads %s · hash %s MB",
        "Geschätzte Spielstärke: %s Elo" to "Estimated strength: %s Elo",
        "(%s bis %s)" to "(%s to %s)",
        "%s von %s Punkten" to "%s of %s points",
        "%s Partien à ca. 60 Halbzüge: ungefähr %s. Das Gerät sollte dabei am Strom bleiben." to
            "%s games of about 60 half-moves each: roughly %s. Keep the device on power while it runs.",
        "%s Minuten" to "%s minutes",
        "%s Stunden %s Minuten" to "%s hours %s minutes",
        "%s ms" to "%s ms",
        "%s von %s Partien" to "%s of %s games",
        "%s Partien ·" to "%s games ·",
        "%s Engines ·" to "%s engines ·",
        "Turnier: %s Engines · %s Partien" to "Tournament: %s engines · %s games",
        "Turnier am Wochenende" to "Weekend tournament",
        // Partie- und Ergebnisbezeichnungen
        "Weiß gewinnt" to "White wins",
        "Schwarz gewinnt" to "Black wins",
        "Remis" to "Draw",
        "Matt" to "Checkmate",
        "Patt" to "Stalemate",
        "50-Züge-Regel" to "Fifty-move rule",
        "Dreifache Stellungswiederholung" to "Threefold repetition",
        "Unzureichendes Material" to "Insufficient material",
        "Aufgabe" to "Resignation",
        "Zeitablauf" to "Timeout",
        "Remis vereinbart" to "Draw agreed",
        "offen" to "unfinished",
        "Import" to "Import",
        // Fehler und Hinweise aus der Partielogik
        "Die Könige stehen nebeneinander." to "The kings are next to each other.",
        "Die Seite, die nicht zieht, steht im Schach." to "The side not to move is in check.",
        "Bauer auf der Grund- oder letzten Reihe (%s)." to "Pawn on the first or last rank (%s).",
        "Rochaderecht ohne König." to "Castling right without a king.",
        "Rochaderecht ohne Turm auf %s." to "Castling right without a rook on %s.",
        "Rochaderecht, obwohl der König nicht auf seinem Startfeld steht." to
            "Castling right although the king is not on its starting square.",
        "En-passant-Feld ist besetzt." to "The en passant square is occupied.",
        "En-passant-Feld liegt auf einer ungültigen Reihe." to
            "The en passant square is on an invalid rank.",
        // Engine-Meldungen
        "Engine konnte nicht gestartet werden." to "The engine could not be started.",
        "Engine lieferte keinen gültigen Zug. Partie abgebrochen." to
            "The engine returned no legal move. Game aborted.",
        "%s kann %s nicht spielen. Bitte eine andere Engine wählen." to
            "%s cannot play %s. Please choose another engine.",
        "%s ist nicht verfügbar (im Store aktivieren oder installieren)." to
            "%s is not available (activate or install it in the store).",
        "%s kann %s nicht spielen." to "%s cannot play %s.",
        "%s konnte nicht erzeugt werden." to "%s could not be created.",
        "%s startet auf diesem Gerät nicht." to "%s does not start on this device.",
        "Turnier abgebrochen." to "Tournament aborted.",
        "Ungültiger Zug" to "Illegal move",
        "Zuglimit" to "Move limit",
        "Engine-Fehler" to "Engine error",
        "Unbekannt" to "Unknown",
        // Zeitklassen-Hinweise
        "unter 3 Minuten" to "under 3 minutes",
        "3 bis 5 Minuten" to "3 to 5 minutes",
        "10 bis 15 Minuten" to "10 to 15 minutes",
        "ab 30 Minuten" to "from 30 minutes",
        "ohne Uhr" to "no clock",
        // Bausteine mit Platzhaltern
        "Regelwerk" to "Rule set",
        "Name" to "Name",
        "Weiß %s" to "White %s",
        "Schwarz %s" to "Black %s",
        "%s Siege, %s Remis, %s Niederlagen" to "%s wins, %s draws, %s losses",
        "%s · %s ms · %s %%" to "%s · %s ms · %s%%",
        " · Aufschlag %s s je Zug" to " · increment %s s per move",
        "%s ms Denkzeit je Zug" to "%s ms thinking time per move",
        "%s/%s Partien · %s Engines · %s" to "%s/%s games · %s engines · %s",
        "%s gegen %s" to "%s vs %s",
        "läuft" to "running",
        "beendet" to "finished",
        "abgebrochen" to "aborted",
        "%s · %s Züge · %s" to "%s · %s moves · %s",
        "%s · %s · %s Züge" to "%s · %s · %s moves",
        "S" to "W",
        "U" to "D",
        "N" to "L",
        "Pkt" to "Pts",
        "%s Elo" to "%s Elo",
        "Gerade: %s" to "Now: %s",
        "Eröffnung: %s" to "Opening: %s",
        "%s von %s Partien gespielt." to "%s of %s games played.",
        "Fortschritt laufender Engine-Turniere" to "Progress of running engine tournaments",
        "Keine der aktiven Engines kann %s spielen. Wähle im Store eine andere Engine oder ein anderes Regelwerk." to
            "None of the active engines can play %s. Choose another engine or rule set in the store.",
        "weiße" to "white",
        "schwarze" to "black",
        "PGN importieren" to "Import PGN",
        "PGN kopieren" to "Copy PGN",
        "PGN kopiert" to "PGN copied",
        "Import fehlgeschlagen" to "Import failed",
        "In der Zwischenablage steht keine lesbare PGN-Partie." to
            "The clipboard does not contain a readable PGN game.",
        "Zug %s von %s" to "Move %s of %s",
        "Letzter Zug: %s" to "Last move: %s",
        "%s · %s Engines verfügbar" to "%s · %s engines available",
        "durch %s" to "by %s",
        "%s ist am Zug" to "%s to move",
        "Der Katalog enthält keine Engines." to "The catalogue contains no engines.",
        "Katalog konnte nicht geladen werden." to "The catalogue could not be loaded.",
        "Nur HTTPS-Adressen werden unterstützt." to "Only HTTPS addresses are supported.",
        "Leere Antwort" to "Empty response",
        "Für dieses Paket gibt es keine Download-Dateien." to
            "There are no download files for this package.",
        "Ordner konnte nicht angelegt werden" to "Could not create the folder",
        "Download abgebrochen" to "Download cancelled",
        "Installation fehlgeschlagen." to "Installation failed.",
        "HTTP %s" to "HTTP %s",
        "HTTP %s bei %s" to "HTTP %s at %s",
        "Prüfsumme stimmt nicht (%s)" to "Checksum mismatch (%s)",
        "Datei konnte nicht gespeichert werden (%s)" to "Could not save the file (%s)",
        "Zuglimit (%s Halbzüge)" to "Move limit (%s half-moves)",

        // Store: Zustände und Aktionen
        "Aktiv" to "Active",
        "Inaktiv" to "Inactive",
        "Installiert" to "Installed",
        "installiert" to "installed",
        "Fehlgeschlagen" to "Failed",
        "Installieren" to "Install",
        "Aktivieren" to "Activate",
        "Deaktivieren" to "Deactivate",
        "Entfernen" to "Remove",
        "Katalog-URL" to "Catalogue URL",
        "Laden" to "Load",
        "klassisch" to "classic",
        // Ältere Turniere haben den Teilnehmernamen noch auf Deutsch gespeichert.
        "Fairy-Stockfish klassisch" to "Fairy-Stockfish classic",
        "Teil der App" to "Part of the app",

        // Store: Schlagwörter aus dem Katalog (bereits vorhandene Keys stehen oben)
        "Netz-Download" to "Network download",
        "Varianten" to "Variants",
        "Sparsam" to "Frugal",
        "Positionell" to "Positional",
        "Klein" to "Small",
        "Taktisch" to "Tactical",
        "Schwach" to "Weak",
        "Immer verfügbar" to "Always available",
        "Stärker" to "Stronger",
        "Menschenähnlich" to "Human-like",
        "Neuronal" to "Neural",

        // Store: Beschreibungen der Katalog-Einträge
        "Der stärkste freie Schachcomputer der Welt. Die Binary in der App ist nur 1 MB groß – die beiden NNUE-Netze (rund 78 MB) lädt sie beim Installieren nach. Kompromisslos, sehr tief, ideal zum Analysieren." to
            "The strongest free chess engine in the world. The binary in the app is only 1 MB; the two NNUE networks (around 78 MB) are downloaded on install. Uncompromising, very deep, ideal for analysis.",
        "Erweiterung von Stockfish für Schachvarianten (Chess960, Crazyhouse und viele mehr). Ohne Netz rechnet sie klassisch – dafür klein und sparsam." to
            "A Stockfish extension for chess variants (Chess960, Crazyhouse and many more). Without a network it evaluates classically – small and frugal instead.",
        "Dieselbe Fairy-Stockfish mit nachgeladenem NNUE-Netz. Deutlich stärker als die klassische Bewertung; das Netz ist rund 48 MB groß." to
            "The same Fairy-Stockfish with a downloaded NNUE network. Noticeably stronger than the classic evaluation; the network is around 48 MB.",
        "Klassische C-Engine mit handgebauter Bewertung. Spielt positioneller und ruhiger als Stockfish und ist dabei nur wenige hundert Kilobyte groß." to
            "A classic C engine with a hand-written evaluation. Plays more positionally and calmly than Stockfish, and is only a few hundred kilobytes in size.",
        "Klassische Engine mit sehr sauberem Code und aggressivem, taktischem Stil. Version 2.01, weil Version 3 C++-Threads nutzt, die Androids Standardbibliothek nicht anbietet." to
            "A classic engine with very clean code and an aggressive, tactical style. Version 2.01, because version 3 uses C++ threads that Android's standard library does not offer.",
        "Die eingebaute Notlösung: greift eine Figur, wenn sie sich lohnt, und sucht einen Zug tief. Für Anfänger und zum Testen." to
            "The built-in fallback: captures a piece when it pays off and searches one move deep. For beginners and for testing.",
        "Das aktuelle Release vom 05.09.2026, rund 40 Elo stärker als 17.1. Natives Binary für arm64-v8a." to
            "The current release from 2026-09-05, about 40 Elo stronger than 17.1. Native binary for arm64-v8a.",
        "Neuronales Netz, das den wahrscheinlichsten menschlichen Zug vorhersagt statt zu rechnen. Auf Spielstärke 1500 konditioniert – ein ehrlicher Trainingspartner. Kleinstes Maia-3-Netz, rund 10 MB. Spielt nur Standard-Schach, keine Varianten." to
            "A neural network that predicts the most likely human move instead of calculating. Conditioned to a playing strength of 1500 – an honest training partner. The smallest Maia 3 network, around 10 MB. Plays standard chess only, no variants.",
        "Die große Maia-3-Variante: deutlich feinere Zugauswahl, spielt eher wie ein starker Vereinsspieler. Rund 46 MB Download. Spielt nur Standard-Schach, keine Varianten." to
            "The large Maia 3 variant: much finer move selection, plays more like a strong club player. Around 46 MB download. Plays standard chess only, no variants.",
        "Native Engine-Binaries dürfen unter Android 10+ nicht aus dem App-Speicher gestartet werden. Der Download kommt mit dem Engine-Runtime-Paket." to
            "On Android 10+ native engine binaries may not be started from app storage. The download arrives with the engine runtime package.",

        // Turniere: Beschriftungen
        "Turnier" to "Tournament",
        "Punkte" to "Points",
        "Siege" to "Wins",
        "Niederlagen" to "Losses",
        "Stufe" to "Level",
        "Schach960" to "Chess960",
        "Schach960 · Grundstellung" to "Chess960 · starting position",
        "Schach960 #%s" to "Chess960 #%s",
        "Ein Turnier braucht mindestens zwei Engines." to
            "A tournament needs at least two engines.",
        "Das Turnier wurde abgebrochen. Die gespielten Partien bleiben erhalten." to
            "The tournament was aborted. The games played so far are kept.",

        // Turniere: Eröffnungsnamen aus dem Eröffnungsbuch
        "Italienisch" to "Italian Game",
        "Zweispringerspiel" to "Two Knights Defence",
        "Sizilianisch, Najdorf" to "Sicilian, Najdorf",
        "Sizilianisch, Drachen" to "Sicilian, Dragon",
        "Sizilianisch, geschlossen" to "Sicilian, Closed",
        "Französisch" to "French Defence",
        "Skandinavisch" to "Scandinavian",
        "Aljechin" to "Alekhine",
        "Wiener Partie" to "Vienna Game",
        "Schottisch" to "Scotch",
        "Russisch" to "Petrov",
        "Spanisch, Berliner" to "Spanish, Berlin",
        "Spanisch, geschlossen" to "Spanish, Closed",
        "Damengambit" to "Queen's Gambit",
        "Slawisch" to "Slav",
        "Nimzo-Indisch" to "Nimzo-Indian",
        "Königsindisch" to "King's Indian",
        "Grünfeld" to "Grünfeld",
        "Katalanisch" to "Catalan",
        "Holländisch" to "Dutch",
        "Réti" to "Réti",

        // LLM-Gegner
        "LLM-Gegner" to "LLM opponent",
        "LLM als Gegner" to "LLM as opponent",
        "LLM einrichten" to "Set up LLM",
        "LLM (API)" to "LLM (API)",
        "API-Modell einrichten – für Partie, Duell und Turnier" to
            "Set up an API model – for games, duels and tournaments",
        "Zwei Gegner automatisch gegeneinander – auch mit LLM" to
            "Two opponents playing each other automatically – with LLM too",
        "Sprache, Darstellung, Gegner, Ton" to "Language, appearance, opponents, sound",
        "API-Zugang" to "API access",
        "Ein OpenAI-kompatibler Endpunkt genügt: OpenAI, Ollama, LM Studio oder ein eigener Server. Der LLM-Gegner erscheint danach in der Gegnerwahl von Partie, Duell und Turnier." to
            "An OpenAI-compatible endpoint is enough: OpenAI, Ollama, LM Studio or your own server. The LLM opponent then appears in the opponent picker of games, duels and tournaments.",
        "Anzeigename" to "Display name",
        "z. B. GPT-4o mini" to "e.g. GPT-4o mini",
        "API-Adresse" to "API address",
        "Modell" to "Model",
        "API-Schlüssel (optional)" to "API key (optional)",
        "Bleibt nur auf diesem Gerät gespeichert." to "Stored on this device only.",
        "Zeitlimit (Sekunden)" to "Timeout (seconds)",
        "Bereit: erscheint als „%s“ bei der Gegnerwahl." to
            "Ready: appears as “%s” in the opponent picker.",
        "Noch nicht eingerichtet: API-Adresse und Modell fehlen." to
            "Not set up yet: API address and model are missing.",
        "Prüfe …" to "Testing…",
        "Verbindung testen" to "Test connection",
        "Frage das Modell …" to "Asking the model…",
        "So spielst du dagegen" to "How to play against it",
        "Partie: Unter „Partie starten“ den LLM-Gegner als Gegner wählen. Duell: Im Engine-Duell eine Seite auf das LLM stellen – etwa Engine gegen LLM oder LLM gegen LLM. Turnier: Das LLM als Teilnehmer auswählen, auch zusammen mit lokalen Engines." to
            "Game: choose the LLM opponent under “Start a game”. Duel: set one side to the LLM in the engine duel – engine vs LLM or LLM vs LLM. Tournament: pick the LLM as a participant, together with local engines if you like.",
        "Pro Zug geht eine Anfrage an die API. Die Antwortzeit zählt wie Bedenkzeit; die gewählte Stufe wird dem Modell als Spielstärke mitgegeben." to
            "One request per move goes to the API. The response time counts like thinking time; the chosen level is passed to the model as playing strength.",
        "Bereit: %s spielt über %s." to "Ready: %s plays via %s.",
        "Noch nicht eingerichtet. Mit einer API-Adresse und einem Modell spielt das LLM in Partie, Duell und Turnier mit." to
            "Not set up yet. With an API address and a model, the LLM joins games, duels and tournaments.",
        "LLM noch nicht eingerichtet – API-Adresse und Modell in den Einstellungen eintragen." to
            "LLM not set up yet – enter the API address and model in the settings.",
        "API-Adresse und Modell fehlen." to "API address and model are missing.",
        "%s antwortet: %s" to "%s replies: %s",
        "Verbindung fehlgeschlagen." to "Connection failed.",
        "%s lieferte keinen Zug." to "%s returned no move.",
        "Das Modell hat keine Antwort geliefert." to "The model returned no answer.",
    )
}
