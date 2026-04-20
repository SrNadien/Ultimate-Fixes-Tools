package nadiendev.ultimatefixestools.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import nadiendev.ultimatefixestools.UltimateFixesTools;
import nadiendev.ultimatefixestools.events.LagMonitorHandler;
import nadiendev.ultimatefixestools.logging.LogManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import org.apache.logging.log4j.Logger;

import java.lang.management.ManagementFactory;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class CommandHandler {

    private static final Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("ultimatefixestools");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(tpsCommand());
        dispatcher.register(resourcesCommand());
        dispatcher.register(lagCommand());
        dispatcher.register(uptimeCommand());
        dispatcher.register(flagChunkCommand());
        dispatcher.register(killLagCommand());
        LOGGER.info("[UFT Commands] /nftps /nfresources /nflag /nfuptime /nflagchunk /nfkill registrados");
    }

    // ── Utilidad: formatear MB ────────────────────────────────────────────────
    private static String formatRam(long mb) {
        if (mb >= 1024) return String.format("%.1fGB", mb / 1024.0);
        return mb + "MB";
    }

    // ── Utilidad: nombre de archivo región a partir de chunk ─────────────────
    private static String regionFile(ChunkPos cp) {
        int rx = Math.floorDiv(cp.x, 32);
        int rz = Math.floorDiv(cp.z, 32);
        return "r." + rx + "." + rz + ".mca";
    }

    // ── Utilidad: TPS que quita un chunk basado en su tiempo de tick ─────────
    // Aproximación: si el servidor tarda X ms extra por este chunk,
    // restamos proporcionalmente del TPS ideal (20).
    private static double tpsLostByMspt(double chunkMspt, double totalMspt) {
        if (totalMspt <= 0) return 0;
        double fraction = chunkMspt / totalMspt;
        double tpsLost  = fraction * (20.0 - LagMonitorHandler.msptToTps(totalMspt));
        return Math.max(0, tpsLost);
    }

    // ── /nftps ────────────────────────────────────────────────────────────────
    private static LiteralArgumentBuilder<CommandSourceStack> tpsCommand() {
        return Commands.literal("nftps")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    double mspt   = LagMonitorHandler.getAverageMspt();
                    double tps    = LagMonitorHandler.msptToTps(mspt);
                    long   lastMs = LagMonitorHandler.getLastTickMs();
                    String color  = tps >= 19 ? "§a" : tps >= 15 ? "§e" : "§c";

                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6[UFT] §lEstadísticas de TPS\n" +
                            "§7TPS actual:    " + color + String.format("%.2f", tps) + " §7/ 20.0\n" +
                            "§7MSPT promedio: §b" + String.format("%.2f", mspt) + " ms\n" +
                            "§7Último tick:   §b" + lastMs + " ms\n" +
                            "§7Estado: " + (tps >= 19 ? "§aNORMAL" : tps >= 15 ? "§eLENTO" : "§cLAG SEVERO")
                    ), false);
                    return 1;
                });
    }

    // ── /nfresources ──────────────────────────────────────────────────────────
    private static LiteralArgumentBuilder<CommandSourceStack> resourcesCommand() {
        return Commands.literal("nfresources")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();

                    com.sun.management.OperatingSystemMXBean osMxBean =
                            (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
                    long totalSystemRamMB = osMxBean.getTotalMemorySize() / (1024 * 1024);
                    long freeSystemRamMB  = osMxBean.getFreeMemorySize()  / (1024 * 1024);
                    long usedSystemRamMB  = totalSystemRamMB - freeSystemRamMB;

                    long jvmUsedMB = LagMonitorHandler.getUsedRamMB();
                    long jvmMaxMB  = LagMonitorHandler.getMaxRamMB();
                    double cpu     = LagMonitorHandler.getCpuUsage();
                    double mspt    = LagMonitorHandler.getAverageMspt();

                    int entitiesCount = 0;
                    int chunksCount   = 0;
                    for (ServerLevel level : server.getAllLevels()) {
                        for (Entity e : level.getAllEntities()) entitiesCount++;
                        chunksCount += level.getChunkSource().getLoadedChunksCount();
                    }
                    final int totalEntities = entitiesCount;
                    final int totalChunks   = chunksCount;

                    String cpuStr     = cpu < 0 ? "§7N/A"
                            : (cpu > 0.8 ? "§c" : cpu > 0.5 ? "§e" : "§a") + String.format("%.1f%%", cpu * 100);
                    String sysColor   = usedSystemRamMB > totalSystemRamMB * 0.85 ? "§c"
                            : usedSystemRamMB > totalSystemRamMB * 0.65 ? "§e" : "§a";
                    String jvmColor   = jvmUsedMB > jvmMaxMB * 0.85 ? "§c"
                            : jvmUsedMB > jvmMaxMB * 0.65 ? "§e" : "§a";
                    String sysPct     = String.format("%.0f%%", (usedSystemRamMB * 100.0) / totalSystemRamMB);
                    String jvmPct     = String.format("%.0f%%", (jvmUsedMB * 100.0) / jvmMaxMB);

                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6[UFT] §lRecursos del Servidor\n" +
                            "§7CPU:          " + cpuStr + "\n" +
                            "§7RAM Del Sistema:  " + sysColor + formatRam(usedSystemRamMB) +
                                    " §7/ " + formatRam(totalSystemRamMB) + " §8(" + sysPct + ")\n" +
                            "§7RAM JVM(Xmx): " + jvmColor + formatRam(jvmUsedMB) +
                                    " §7/ " + formatRam(jvmMaxMB) + " §8(" + jvmPct + ")\n" +
                            "§7Entidades:    §b" + totalEntities + "\n" +
                            "§7Chunks:       §b" + totalChunks + "\n" +
                            "§7MSPT avg:     §b" + String.format("%.2f", mspt) + " ms\n" +
                            "§7TPS:          §b" + String.format("%.2f", LagMonitorHandler.msptToTps(mspt))
                    ), false);
                    return 1;
                });
    }

    // ── /nflag ────────────────────────────────────────────────────────────────
    private static LiteralArgumentBuilder<CommandSourceStack> lagCommand() {
        return Commands.literal("nflag")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    MinecraftServer server = ctx.getSource().getServer();
                    double mspt = LagMonitorHandler.getAverageMspt();
                    double tps  = LagMonitorHandler.msptToTps(mspt);

                    StringBuilder report = new StringBuilder();
                    report.append("§6[UFT] §lAnálisis de Lag\n");
                    report.append(String.format("§7TPS: §b%.2f §7| MSPT: §b%.2f ms\n", tps, mspt));

                    for (ServerLevel level : server.getAllLevels()) {
                        String dimName = level.dimension().location().toString();
                        int entities = 0;
                        for (Entity e : level.getAllEntities()) entities++;
                        int chunks = level.getChunkSource().getLoadedChunksCount();
                        report.append(String.format("§8  [%s] §7ents=§e%d §7chunks=§e%d\n",
                                dimName, entities, chunks));
                        LogManager.logLag(dimName, 0, 0, 0,
                                "MANUAL_SCAN_" + dimName, mspt,
                                "cmd=/nflag ents=" + entities + " chunks=" + chunks);
                    }

                    final String msg = report.toString().trim();
                    ctx.getSource().sendSuccess(() -> Component.literal(msg), false);
                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§7Guardado en §bUltimateFixesTools/quetolagea.log"), false);
                    return 1;
                });
    }

    // ── /nfuptime ─────────────────────────────────────────────────────────────
    private static LiteralArgumentBuilder<CommandSourceStack> uptimeCommand() {
        return Commands.literal("nfuptime")
                .requires(src -> src.hasPermission(0))
                .executes(ctx -> {
                    long start   = UltimateFixesTools.getServerStartTime();
                    long elapsed = System.currentTimeMillis() - start;
                    long hours   = TimeUnit.MILLISECONDS.toHours(elapsed);
                    long minutes = TimeUnit.MILLISECONDS.toMinutes(elapsed) % 60;
                    long seconds = TimeUnit.MILLISECONDS.toSeconds(elapsed) % 60;

                    ctx.getSource().sendSuccess(() -> Component.literal(
                            String.format("§6[UFT] §7Uptime del servidor: §b%dh %dm %ds",
                                    hours, minutes, seconds)
                    ), false);
                    return 1;
                });
    }

    // ── /nflagchunk ───────────────────────────────────────────────────────────
    // Muestra por chunk: número, región, jugadores, entidades con nombre exacto,
    // TileEntities, coordenadas, dimensión y TPS estimado que consume.
    private static LiteralArgumentBuilder<CommandSourceStack> flagChunkCommand() {
        return Commands.literal("nflagchunk")
                .requires(src -> src.hasPermission(2))
                // sin args: analiza todos los mundos y muestra top 5 chunks por entidades
                .executes(ctx -> runFlagChunk(ctx.getSource(), 5))
                // con arg: cuántos chunks mostrar
                .then(Commands.argument("top", IntegerArgumentType.integer(1, 20))
                        .executes(ctx -> runFlagChunk(ctx.getSource(),
                                IntegerArgumentType.getInteger(ctx, "top"))));
    }

    private static int runFlagChunk(CommandSourceStack src, int topN) {
        MinecraftServer server = src.getServer();
        double totalMspt = LagMonitorHandler.getAverageMspt();
        double globalTps = LagMonitorHandler.msptToTps(totalMspt);

        // Acumular datos por chunk en todos los mundos
        record ChunkData(
                ChunkPos pos,
                String dimension,
                int entityCount,
                double tpsLost,
                List<String> entityNames,
                List<String> tileEntityNames,
                List<String> playerNames,
                BlockPos centerBlock
        ) {}

        List<ChunkData> results = new ArrayList<>();

        for (ServerLevel level : server.getAllLevels()) {
            String dimName = level.dimension().location().toString();

            // Agrupar entidades por chunk
            Map<ChunkPos, List<Entity>> byChunk = new HashMap<>();
            for (Entity entity : level.getAllEntities()) {
                ChunkPos cp = new ChunkPos(entity.blockPosition());
                byChunk.computeIfAbsent(cp, k -> new ArrayList<>()).add(entity);
            }

            for (Map.Entry<ChunkPos, List<Entity>> entry : byChunk.entrySet()) {
                ChunkPos cp = entry.getKey();
                List<Entity> entities = entry.getValue();
                if (entities.size() < 10) continue; // ignorar chunks con poca actividad

                // Nombre exacto de cada entidad/jugador
                List<String> entityNames = new ArrayList<>();
                List<String> playerNames = new ArrayList<>();
                for (Entity e : entities) {
                    String name = e.getType().toShortString();
                    if (e instanceof Player p) {
                        playerNames.add(p.getName().getString());
                    } else {
                        entityNames.add(name);
                    }
                }

                // Contar nombres repetidos para mostrar "zombie x14"
                Map<String, Long> entityCount = entityNames.stream()
                        .collect(Collectors.groupingBy(n -> n, Collectors.counting()));
                List<String> entitySummary = entityCount.entrySet().stream()
                        .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                        .limit(5)
                        .map(e -> e.getKey() + " x" + e.getValue())
                        .collect(Collectors.toList());

                // TileEntities del chunk
                List<String> tileNames = new ArrayList<>();
                try {
                    LevelChunk chunk = level.getChunk(cp.x, cp.z);
                    Map<String, Long> teCount = chunk.getBlockEntities().values().stream()
                            .map(be -> be.getType().toString())
                            .collect(Collectors.groupingBy(n -> n, Collectors.counting()));
                    teCount.entrySet().stream()
                            .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                            .limit(5)
                            .forEach(e -> tileNames.add(e.getKey() + " x" + e.getValue()));
                } catch (Exception ignored) {}

                // TPS estimado perdido por este chunk (proporcional a entidades)
                double chunkMspt  = totalMspt * ((double) entities.size() /
                        Math.max(1, byChunk.values().stream().mapToInt(List::size).sum()));
                double chunkTpsLost = tpsLostByMspt(chunkMspt, totalMspt);

                BlockPos center = new BlockPos(cp.getMiddleBlockX(), 64, cp.getMiddleBlockZ());

                results.add(new ChunkData(cp, dimName, entities.size(), chunkTpsLost,
                        entitySummary, tileNames, playerNames, center));
            }
        }

        // Ordenar por entidades (más problemático primero)
        results.sort(Comparator.comparingInt(ChunkData::entityCount).reversed());

        if (results.isEmpty()) {
            src.sendSuccess(() -> Component.literal(
                    "§6[UFT] §aNingún chunk con actividad significativa detectado."), false);
            return 1;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(String.format("§6[UFT] §lAnálisis por Chunk §7(TPS global: §b%.2f§7)\n", globalTps));

        int shown = Math.min(topN, results.size());
        for (int i = 0; i < shown; i++) {
            ChunkData cd = results.get(i);
            String tpsColor = cd.tpsLost() > 2 ? "§c" : cd.tpsLost() > 0.5 ? "§e" : "§a";

            sb.append(String.format(
                    "§8─── §eChunk [%d, %d] §8───\n" +
                    "§7Región:      §b%s\n" +
                    "§7Coordenadas: §b%d, %d, %d\n" +
                    "§7Dimensión:   §b%s\n" +
                    "§7Entidades:   §c%d\n",
                    cd.pos().x, cd.pos().z,
                    regionFile(cd.pos()),
                    cd.centerBlock().getX(), cd.centerBlock().getY(), cd.centerBlock().getZ(),
                    cd.dimension(),
                    cd.entityCount()
            ));

            if (!cd.playerNames().isEmpty()) {
                sb.append("§7Jugadores:   §d").append(String.join(", ", cd.playerNames())).append("\n");
            }
            if (!cd.entityNames().isEmpty()) {
                sb.append("§7Top ents:    §f").append(String.join(" | ", cd.entityNames())).append("\n");
            }
            if (!cd.tileEntityNames().isEmpty()) {
                sb.append("§7TileEntity:  §f").append(String.join(" | ", cd.tileEntityNames())).append("\n");
            }
            sb.append(String.format("§7TPS perdido: %s-%.2f TPS\n", tpsColor, cd.tpsLost()));

            // Guardar en log
            LogManager.logLag(cd.dimension(),
                    cd.centerBlock().getX(), cd.centerBlock().getY(), cd.centerBlock().getZ(),
                    "CHUNK_SCAN",
                    totalMspt,
                    "chunk=[" + cd.pos().x + "," + cd.pos().z + "]" +
                    " region=" + regionFile(cd.pos()) +
                    " ents=" + cd.entityCount() +
                    " tps_lost=" + String.format("%.2f", cd.tpsLost()) +
                    " top=" + String.join(",", cd.entityNames()) +
                    " te=" + String.join(",", cd.tileEntityNames()) +
                    " players=" + String.join(",", cd.playerNames()));
        }

        final String report = sb.toString().trim();
        src.sendSuccess(() -> Component.literal(report), false);
        src.sendSuccess(() -> Component.literal(
                "§7Guardado en §bUltimateFixesTools/quetolagea.log"), false);
        return 1;
    }

    // ── /nfkill ───────────────────────────────────────────────────────────────
    // todo en "bloques-crasheantes.log".
    private static LiteralArgumentBuilder<CommandSourceStack> killLagCommand() {
        return Commands.literal("nfkilllaged")
                .requires(src -> src.hasPermission(4))
                .then(Commands.literal("entities")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 256))
                                .executes(ctx -> runKillEntities(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius")))))
                .then(Commands.literal("tileentities")
                        .then(Commands.argument("radius", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> runKillTileEntities(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "radius")))));
    }

    private static int runKillEntities(CommandSourceStack src, int radius) {
        if (!(src.getLevel() instanceof ServerLevel level)) return 0;

        BlockPos origin = BlockPos.containing(src.getPosition());
        String dimName  = level.dimension().location().toString();
        double mspt     = LagMonitorHandler.getAverageMspt();

        List<Entity> toKill = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Player) continue;
            if (entity.distanceToSqr(src.getPosition()) <= (double) radius * radius) {
                toKill.add(entity);
            }
        }

        StringBuilder logEntry = new StringBuilder();
        logEntry.append(String.format(
                "[KILL_ENTITIES] origen=(%d,%d,%d) radio=%d dim=%s mspt=%.2f eliminadas=%d\n",
                origin.getX(), origin.getY(), origin.getZ(),
                radius, dimName, mspt, toKill.size()));

        Map<String, Long> byType = toKill.stream()
                .collect(Collectors.groupingBy(e -> e.getType().toShortString(), Collectors.counting()));

        byType.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .forEach(e -> logEntry.append("  ").append(e.getKey()).append(" x").append(e.getValue()).append("\n"));

        for (Entity e : toKill) {
            logEntry.append(String.format("  → %s en (%d,%d,%d)\n",
                    e.getType().toShortString(),
                    (int) e.getX(), (int) e.getY(), (int) e.getZ()));
            e.discard();
        }

        LogManager.logCrash("KILL_ENTITIES", logEntry.toString());

        final int count = toKill.size();
        src.sendSuccess(() -> Component.literal(
                String.format("§6[UFT] §cEliminadas %d entidades §7en radio %d.\n" +
                        "§7Guardado en §bUltimateFixesTools/bloques-crasheantes.log", count, radius)
        ), true);

        return count;
    }

    private static int runKillTileEntities(CommandSourceStack src, int radius) {
        if (!(src.getLevel() instanceof ServerLevel level)) return 0;

        BlockPos origin = BlockPos.containing(src.getPosition());
        String dimName  = level.dimension().location().toString();
        double mspt     = LagMonitorHandler.getAverageMspt();

        // Buscar TileEntities en chunks dentro del radio
        Set<ChunkPos> chunksToScan = new HashSet<>();
        int chunkRadius = (radius / 16) + 1;
        ChunkPos originChunk = new ChunkPos(origin);
        for (int cx = originChunk.x - chunkRadius; cx <= originChunk.x + chunkRadius; cx++) {
            for (int cz = originChunk.z - chunkRadius; cz <= originChunk.z + chunkRadius; cz++) {
                chunksToScan.add(new ChunkPos(cx, cz));
            }
        }

        List<BlockPos> toRemove = new ArrayList<>();
        List<String> teLog     = new ArrayList<>();

        for (ChunkPos cp : chunksToScan) {
            try {
                LevelChunk chunk = level.getChunk(cp.x, cp.z);
                for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
                    BlockPos pos = entry.getKey();
                    if (pos.distSqr(origin) <= (double) radius * radius) {
                        String typeName = entry.getValue().getType().toString();
                        toRemove.add(pos);
                        teLog.add(typeName + " en (" + pos.getX() + "," + pos.getY() + "," + pos.getZ() + ")");
                    }
                }
            } catch (Exception ignored) {}
        }

        StringBuilder logEntry = new StringBuilder();
        logEntry.append(String.format(
                "[KILL_TILEENTITIES] origen=(%d,%d,%d) radio=%d dim=%s mspt=%.2f eliminadas=%d\n",
                origin.getX(), origin.getY(), origin.getZ(),
                radius, dimName, mspt, toRemove.size()));
        teLog.forEach(s -> logEntry.append("  → ").append(s).append("\n"));

        for (BlockPos pos : toRemove) {
            level.removeBlockEntity(pos);
            level.setBlock(pos, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), 3);
        }

        LogManager.logCrash("KILL_TILEENTITIES", logEntry.toString());

        final int count = toRemove.size();
        src.sendSuccess(() -> Component.literal(
                String.format("§6[UFT] §cEliminados %d TileEntities §7en radio %d.\n" +
                        "§7Guardado en §bUltimateFixesTools/bloques-crasheantes.log", count, radius)
        ), true);

        return count;
    }
}