package nadiendev.ultimatefixestools.events;

import nadiendev.ultimatefixestools.config.ModConfig;
import nadiendev.ultimatefixestools.logging.NftLogManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.lang.management.OperatingSystemMXBean;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Monitorea el rendimiento del servidor tick a tick.
 * Guarda histórico de MSPT para calcular TPS real.
 */
public class LagMonitorHandler {

    private static final Logger LOGGER = LogManager.getLogger("ultimatefixestools");

    private static final int HISTORY_SIZE = 100;
    private static final long[] tickHistory = new long[HISTORY_SIZE];
    private static int historyIndex = 0;
    private static int historyCount = 0;

    private long tickStartNano = 0;
    private long tickCounter = 0;

    private static final AtomicLong lastLagLogTick = new AtomicLong(0);

    // ── ServerTickEvent ───────────────────────────────────────────────────────

    @SubscribeEvent
    public void onServerTickStart(ServerTickEvent.Pre event) {
        tickStartNano = System.nanoTime();
    }

    @SubscribeEvent
    public void onServerTickEnd(ServerTickEvent.Post event) {
        long elapsed = System.nanoTime() - tickStartNano;
        long elapsedMs = elapsed / 1_000_000;

        tickHistory[historyIndex % HISTORY_SIZE] = elapsedMs;
        historyIndex++;
        historyCount = Math.min(historyCount + 1, HISTORY_SIZE);

        tickCounter++;

        if (tickCounter % ModConfig.LAG_LOG_INTERVAL_TICKS == 0) {
            double mspt = getAverageMspt();
            if (mspt > ModConfig.LAG_THRESHOLD_MSPT) {
                // FIX: Log4j no soporta {:.2f} — usar String.format antes de pasar el argumento
                LOGGER.warn("[NFT Lag] MSPT alto detectado: {} ms/tick (TPS ~{})",
                        String.format("%.2f", mspt),
                        String.format("%.1f", msptToTps(mspt)));
            }
        }
    }

    // ── LevelTickEvent: buscar entidades laggeras ─────────────────────────────

    @SubscribeEvent
    public void onLevelTickEnd(LevelTickEvent.Post event) {
        if (!ModConfig.ENABLE_LAG_MONITOR) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        long currentTick = level.getGameTime();

        if (currentTick - lastLagLogTick.get() < ModConfig.LAG_LOG_INTERVAL_TICKS) return;

        double mspt = getAverageMspt();
        if (mspt <= ModConfig.LAG_THRESHOLD_MSPT) return;

        lastLagLogTick.set(currentTick);

        Iterable<Entity> allEntities = level.getAllEntities();

        Map<ChunkPos, Integer> entityByChunk = new HashMap<>();
        int totalEntities = 0;
        for (Entity entity : allEntities) {
            totalEntities++;
            ChunkPos cp = new ChunkPos(entity.blockPosition());
            entityByChunk.merge(cp, 1, Integer::sum);
        }

        // Top 3 chunks con más entidades
        entityByChunk.entrySet().stream()
                .filter(e -> e.getValue() >= 50)
                .sorted(Map.Entry.<ChunkPos, Integer>comparingByValue().reversed())
                .limit(3)
                .forEach(e -> {
                    ChunkPos cp = e.getKey();
                    String dimName = level.dimension().location().toString();
                    NftLogManager.logLag(dimName,
                            cp.getMiddleBlockX(), 64, cp.getMiddleBlockZ(),
                            "ENTITY_CLUSTER",
                            mspt,
                            "entidades=" + e.getValue() + " chunk=" + cp);
                });

        int loadedChunks = level.getChunkSource().getLoadedChunksCount();
        String dimName = level.dimension().location().toString();

        NftLogManager.logLag(dimName, 0, 0, 0, "SERVER_GENERAL", mspt,
                "entidades_totales=" + totalEntities +
                        " chunks_cargados=" + loadedChunks +
                        " tps=" + String.format("%.1f", msptToTps(mspt)));
    }

    // ── Métricas públicas (para comandos) ─────────────────────────────────────

    public static double getAverageMspt() {
        if (historyCount == 0) return 0.0;
        long sum = 0;
        int count = Math.min(historyCount, HISTORY_SIZE);
        for (int i = 0; i < count; i++) {
            sum += tickHistory[i];
        }
        return (double) sum / count;
    }

    public static double msptToTps(double mspt) {
        if (mspt <= 0) return 20.0;
        return Math.min(20.0, 1000.0 / mspt);
    }

    public static long getLastTickMs() {
        if (historyCount == 0) return 0;
        int last = (historyIndex - 1 + HISTORY_SIZE) % HISTORY_SIZE;
        return tickHistory[last];
    }

    public static double getCpuUsage() {
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        if (os instanceof com.sun.management.OperatingSystemMXBean sunOs) {
            return sunOs.getCpuLoad();
        }
        double load = os.getSystemLoadAverage();
        return load < 0 ? -1.0 : load;
    }

    public static long getUsedRamMB() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024);
    }

    public static long getTotalRamMB() {
        return Runtime.getRuntime().totalMemory() / (1024 * 1024);
    }

    public static long getMaxRamMB() {
        return Runtime.getRuntime().maxMemory() / (1024 * 1024);
    }
}