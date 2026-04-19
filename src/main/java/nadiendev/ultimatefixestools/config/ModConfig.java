package nadiendev.ultimatefixestools.config;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;


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
    public static int     SPAM_COOLDOWN_MS           = 5000;   // ms entre logs repetidos
    public static int     SPAM_MAX_SAME_MSG          = 3;      // máx repeticiones antes de suprimir

    // ── Lag Monitor ───────────────────────────────────────────────────────────
    public static boolean ENABLE_LAG_MONITOR         = true;
    public static double  LAG_THRESHOLD_MSPT         = 50.0;   // ms por tick para considerar lag
    public static int     LAG_LOG_INTERVAL_TICKS     = 200;    // cada cuántos ticks loguear lag

    public static void load() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
        } catch (IOException e) {
            LOGGER.warn("[NFT Config] No se pudo crear directorio config/", e);
        }

        Properties props = new Properties();

        if (Files.exists(CONFIG_PATH)) {
            try (InputStream in = Files.newInputStream(CONFIG_PATH)) {
                props.load(in);
                LOGGER.info("[NFT Config] Configuración cargada desde {}", CONFIG_PATH);
            } catch (IOException e) {
                LOGGER.warn("[NFT Config] Error leyendo config, usando valores por defecto", e);
            }
        } else {
            LOGGER.info("[NFT Config] Archivo de config no encontrado, creando con valores por defecto...");
        }

        // Leer valores (con fallback a defaults)
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

        // Guardar archivo con todos los valores (actualiza si faltaban entradas)
        save(props);
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

        try (OutputStream out = Files.newOutputStream(CONFIG_PATH)) {
            p.store(out, "UltimateFixesTools Configuration\n" +
                    "enableFireFix          - activa el sistema de restauracion de fuego\n" +
                    "maxFireRestoreAttempts - intentos maximos de restaurar fuego (1-10)\n" +
                    "ignorePlayerCausedEvents - ignora eventos causados por jugadores\n" +
                    "debugFire              - loguea detalle de eventos de fuego\n" +
                    "enableSpamControl      - filtra logs repetitivos\n" +
                    "spamCooldownMs         - ms minimos entre el mismo mensaje de log\n" +
                    "spamMaxSameMessage     - veces maximas que se muestra el mismo mensaje\n" +
                    "enableLagMonitor       - activa el monitor de lag\n" +
                    "lagThresholdMspt       - umbral de MSPT para considerar lag\n" +
                    "lagLogIntervalTicks    - cada cuantos ticks revisar lag");
        } catch (IOException e) {
            LOGGER.warn("[NFT Config] No se pudo guardar config", e);
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
