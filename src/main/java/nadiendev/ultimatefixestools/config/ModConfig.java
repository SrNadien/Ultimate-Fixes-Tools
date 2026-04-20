package nadiendev.ultimatefixestools.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.apache.logging.log4j.Level;

import java.io.*;
import java.nio.file.*;
import java.util.*;

public class ModConfig {

    private static final Logger LOGGER = LogManager.getLogger("ultimatefixestools");
    private static final Path CONFIG_PATH = Paths.get("config", "ultimatefixestools.properties");

    // ── Fire Fix ──────────────────────────────────────────────────────────────
    public static boolean ENABLE_FIRE_FIX           = true;
    public static int     MAX_FIRE_RESTORE_ATTEMPTS  = 3;
    public static boolean IGNORE_PLAYER_CAUSED       = true;
    public static boolean DEBUG_FIRE                 = true;

    // ── Spam Control ──────────────────────────────────────────────────────────
    public static boolean ENABLE_SPAM_CONTROL        = true;
    public static int     SPAM_COOLDOWN_MS           = 5000;
    public static int     SPAM_MAX_SAME_MSG          = 3;

    // ── Lag Monitor ───────────────────────────────────────────────────────────
    // (50ms = TPS 20, cualquier pico mínimo lo disparaba)
    public static boolean ENABLE_LAG_MONITOR         = true;
    public static double  LAG_THRESHOLD_MSPT         = 100.0;
    public static int     LAG_LOG_INTERVAL_TICKS     = 200;

    // ── Logger Blacklist ──────────────────────────────────────────────────────
    public static List<String> LOGGER_BLACKLIST = new ArrayList<>(Arrays.asList(
            "actuallyadditionsaddon",
            "appeng.api.stacks.AEKey",
            "AEKey"
    ));

    public static void load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
        } catch (IOException e) {
            LOGGER.warn("[UFT Config] No se pudo crear directorio config/", e);
        }

        Properties props = new Properties();

        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                props.load(in);
                LOGGER.info("[UFT Config] Configuración cargada desde {}", CONFIG_PATH);
            } catch (IOException e) {
                LOGGER.warn("[UFT Config] Error leyendo config, usando valores por defecto", e);
            }
        } else {
            LOGGER.info("[UFT Config] Archivo de config no encontrado, creando con valores por defecto...");
        }

        ENABLE_FIRE_FIX          = bool(props, "enableFireFix",            ENABLE_FIRE_FIX);
        MAX_FIRE_RESTORE_ATTEMPTS = intVal(props, "maxFireRestoreAttempts", MAX_FIRE_RESTORE_ATTEMPTS);
        IGNORE_PLAYER_CAUSED     = bool(props, "ignorePlayerCausedEvents",  IGNORE_PLAYER_CAUSED);
        DEBUG_FIRE               = bool(props, "debugFire",                 DEBUG_FIRE);
        ENABLE_SPAM_CONTROL      = bool(props, "enableSpamControl",         ENABLE_SPAM_CONTROL);
        SPAM_COOLDOWN_MS         = intVal(props, "spamCooldownMs",          SPAM_COOLDOWN_MS);
        SPAM_MAX_SAME_MSG        = intVal(props, "spamMaxSameMessage",       SPAM_MAX_SAME_MSG);
        ENABLE_LAG_MONITOR       = bool(props, "enableLagMonitor",          ENABLE_LAG_MONITOR);
        LAG_THRESHOLD_MSPT       = doubleVal(props, "lagThresholdMspt",     LAG_THRESHOLD_MSPT);
        LAG_LOG_INTERVAL_TICKS   = intVal(props, "lagLogIntervalTicks",     LAG_LOG_INTERVAL_TICKS);

        // Leer blacklist — separada por comas, ignorar espacios y vacíos
        String blacklistRaw = props.getProperty("loggerBlacklist", "actuallyadditionsaddon");
        LOGGER_BLACKLIST = new ArrayList<>();
        for (String entry : blacklistRaw.split(",")) {
            String trimmed = entry.trim();
            if (!trimmed.isEmpty()) LOGGER_BLACKLIST.add(trimmed);
        }

        // Aplicar silenciado a todos los loggers en la blacklist
        applyBlacklist();

        save(props);
    }

    /**
     * Silencia cada logger de la blacklist a nivel ERROR.
     * Esto suprime INFO/WARN/DEBUG de ese mod en la consola y en latest.log,
     * sin tocar los archivos de log propios del UFT.
     */
    private static void applyBlacklist() {
        for (String loggerName : LOGGER_BLACKLIST) {
            try {
                Configurator.setLevel(loggerName, Level.ERROR);
                LOGGER.info("[UFT Blacklist] Logger silenciado: '{}' → solo ERROR pasará", loggerName);
            } catch (Exception e) {
                LOGGER.warn("[UFT Blacklist] No se pudo silenciar logger '{}': {}", loggerName, e.getMessage());
            }
        }
    }

    private static void save(Properties existing) {
        Properties p = new Properties();
        p.setProperty("enableFireFix",            String.valueOf(ENABLE_FIRE_FIX));
        p.setProperty("maxFireRestoreAttempts",    String.valueOf(MAX_FIRE_RESTORE_ATTEMPTS));
        p.setProperty("ignorePlayerCausedEvents",  String.valueOf(IGNORE_PLAYER_CAUSED));
        p.setProperty("debugFire",                 String.valueOf(DEBUG_FIRE));
        p.setProperty("enableSpamControl",         String.valueOf(ENABLE_SPAM_CONTROL));
        p.setProperty("spamCooldownMs",            String.valueOf(SPAM_COOLDOWN_MS));
        p.setProperty("spamMaxSameMessage",        String.valueOf(SPAM_MAX_SAME_MSG));
        p.setProperty("enableLagMonitor",          String.valueOf(ENABLE_LAG_MONITOR));
        p.setProperty("lagThresholdMspt",          String.valueOf(LAG_THRESHOLD_MSPT));
        p.setProperty("lagLogIntervalTicks",       String.valueOf(LAG_LOG_INTERVAL_TICKS));
        p.setProperty("loggerBlacklist",           String.join(",", LOGGER_BLACKLIST));

        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            p.store(out,
                    "UltimateFixesTools Configuration\n" +
                    "#\n" +
                    "# enableFireFix          - activa restauracion de fuego\n" +
                    "# maxFireRestoreAttempts - intentos maximos (1-10)\n" +
                    "# ignorePlayerCausedEvents - ignora eventos de jugadores\n" +
                    "# debugFire              - loguea detalle de fuego (muy verboso, dejar false)\n" +
                    "# enableSpamControl      - filtra logs repetitivos\n" +
                    "# spamCooldownMs         - ms minimos entre el mismo mensaje\n" +
                    "# spamMaxSameMessage     - veces maximas antes de suprimir\n" +
                    "# enableLagMonitor       - activa monitor de lag\n" +
                    "# lagThresholdMspt       - MSPT para considerar lag (recomendado: 100)\n" +
                    "#                          50ms = TPS 20, picos normales lo disparan\n" +
                    "#                          100ms = TPS ~10, lag real de servidor\n" +
                    "# lagLogIntervalTicks    - cada cuantos ticks revisar lag\n" +
                    "# loggerBlacklist        - loggers de otros mods a silenciar (separados por coma)\n" +
                    "#                          Ejemplo: loggerBlacklist=actuallyadditionsaddon,otromod\n" +
                    "#                          Solo deja pasar mensajes de nivel ERROR de esos mods"
            );
        } catch (IOException e) {
            LOGGER.warn("[UFT Config] No se pudo guardar config", e);
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    private static boolean bool(Properties p, String key, boolean def) {
        return Boolean.parseBoolean(p.getProperty(key, String.valueOf(def)));
    }
    private static int intVal(Properties p, String key, int def) {
        try { return Integer.parseInt(p.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }
    private static double doubleVal(Properties p, String key, double def) {
        try { return Double.parseDouble(p.getProperty(key, String.valueOf(def))); }
        catch (NumberFormatException e) { return def; }
    }
}