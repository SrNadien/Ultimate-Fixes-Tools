package nadiendev.ultimatefixestools.logging;

import nadiendev.ultimatefixestools.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;

public class NftLogManager {

    private static final Logger LOGGER = LogManager.getLogger("ultimatefixestools");
    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Path BASE_DIR  = Paths.get("UltimateFixesTools");
    private static final Path LOG_FIRE  = BASE_DIR.resolve("firelogs.log");
    private static final Path LOG_RUTAS = BASE_DIR.resolve("rutas.log");
    private static final Path LOG_LAG   = BASE_DIR.resolve("quetolagea.log");
    private static final Path LOG_CRASH = BASE_DIR.resolve("bloques-crasheantes.log");

    private static final Map<String, long[]> spamTracker = new ConcurrentHashMap<>();
    private static final BlockingQueue<String[]> writeQueue = new LinkedBlockingQueue<>(2000);
    private static Thread writerThread;
    private static volatile boolean running = false;

    public static void init() {
        try {
            Files.createDirectories(BASE_DIR);
            writeHeader(LOG_FIRE,  "=== UltimateFixesTools - firelogs.log (fire events) ===");
            writeHeader(LOG_RUTAS, "=== UltimateFixesTools - rutas.log (class paths) ===");
            writeHeader(LOG_LAG,   "=== UltimateFixesTools - quetolagea.log (lag sources) ===");
            writeHeader(LOG_CRASH, "=== UltimateFixesTools - bloques-crasheantes.log (entidades y TileEntities eliminadas) ===");
        } catch (IOException e) {
            LOGGER.error("[UFT Log] No se pudo crear directorio UltimateFixesTools/", e);
            return;
        }

        running = true;
        writerThread = new Thread(NftLogManager::writerLoop, "UFT-LogWriter");
        writerThread.setDaemon(true);
        writerThread.start();

        LOGGER.info("[UltimateFixesTools Log] Sistema de logs iniciado en {}", BASE_DIR.toAbsolutePath());
    }

    /** Loguea un evento de fuego (firelogs.log) */
    public static void logFire(String eventType, String cause, String details) {
        String msg = String.format("[%s] [FIRE/%s] causa=%s | %s", now(), eventType, cause, details);
        if (spamCheck("fire:" + eventType + ":" + cause)) {
            enqueue(LOG_FIRE.toString(), msg);
            if (ModConfig.DEBUG_FIRE) LOGGER.info("[UFT Fire] {}", msg);
        }
    }

    /** Loguea una ruta de clase identificada (rutas.log) */
    public static void logRuta(String sourceType, String className, String context) {
        String msg = String.format("[%s] [RUTA/%s] clase=%s | ctx=%s", now(), sourceType, className, context);
        if (spamCheck("ruta:" + className)) {
            enqueue(LOG_RUTAS.toString(), msg);
        }
    }

    /** Loguea una fuente de lag (quetolagea.log) */
    public static void logLag(String dimension, double x, double y, double z,
                               String source, double mspt, String extra) {
        String msg = String.format("[%s] [LAG] dim=%s pos=(%.1f,%.1f,%.1f) mspt=%.2f src=%s %s",
                now(), dimension, x, y, z, mspt, source, extra);
        if (spamCheck("lag:" + source + dimension)) {
            enqueue(LOG_LAG.toString(), msg);
            if (ModConfig.ENABLE_LAG_MONITOR) LOGGER.warn("[UFT Lag] {}", msg);
        }
    }

    /** Loguea lag sin coordenadas específicas */
    public static void logLagGeneral(String source, double mspt, String extra) {
        logLag("unknown", 0, 0, 0, source, mspt, extra);
    }

    /**
     * Loguea eliminaciones manuales de entidades/TileEntities en bloques-crasheantes.log.
     * Sin control de spam — cada acción manual debe quedar registrada completa.
     *
     * @param action  Tipo: KILL_ENTITIES, KILL_TILEENTITIES, etc.
     * @param details Bloque de texto con coords, tipos y cantidad eliminada.
     */
    public static void logCrash(String action, String details) {
        String entry = String.format("[%s] [%s]\n%s%s", now(), action, details, "─".repeat(60));
        enqueue(LOG_CRASH.toString(), entry);
        LOGGER.warn("[UFT Crash] {} → ver bloques-crasheantes.log", action);
    }

    // ── Spam control ──────────────────────────────────────────────────────────
    private static boolean spamCheck(String key) {
        if (!ModConfig.ENABLE_SPAM_CONTROL) return true;

        long now = System.currentTimeMillis();
        long[] data = spamTracker.computeIfAbsent(key, k -> new long[]{0, 0});
        long timeSinceLast = now - data[1];

        if (timeSinceLast >= ModConfig.SPAM_COOLDOWN_MS) {
            data[0] = 1; data[1] = now;
            return true;
        }

        data[0]++; data[1] = now;
        if (data[0] <= ModConfig.SPAM_MAX_SAME_MSG) return true;
        if (data[0] == ModConfig.SPAM_MAX_SAME_MSG + 1) {
            LOGGER.debug("[UFT Spam] Suprimiendo mensaje repetitivo: {}", key);
        }
        return false;
    }

    // ── Writer asíncrono ──────────────────────────────────────────────────────
    private static void enqueue(String path, String msg) {
        if (!writeQueue.offer(new String[]{path, msg})) {
            LOGGER.warn("[UFT Log] Cola de escritura llena, descartando mensaje");
        }
    }

    private static void writerLoop() {
        while (running || !writeQueue.isEmpty()) {
            try {
                String[] entry = writeQueue.poll(500, TimeUnit.MILLISECONDS);
                if (entry != null) appendLine(Paths.get(entry[0]), entry[1]);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
    }

    private static void appendLine(Path file, String line) {
        try (BufferedWriter w = Files.newBufferedWriter(file,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            w.write(line);
            w.newLine();
        } catch (IOException e) {
            LOGGER.error("[UFT Log] Error escribiendo en {}: {}", file, e.getMessage());
        }
    }

    private static void writeHeader(Path file, String header) throws IOException {
        if (!Files.exists(file)) {
            try (BufferedWriter w = Files.newBufferedWriter(file,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                w.write(header);
                w.newLine();
                w.write("Iniciado: " + now());
                w.newLine();
                w.write("─".repeat(80));
                w.newLine();
            }
        }
    }

    public static void flush() {
        running = false;
        if (writerThread != null) {
            try { writerThread.join(3000); } catch (InterruptedException ignored) {}
        }
    }

    private static String now() {
        return LocalDateTime.now().format(FMT);
    }

    public static void clearSpamCache() {
        spamTracker.clear();
    }
}