package nadiendev.ultimatefixestools.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import nadiendev.ultimatefixestools.UltimateFixesTools;
import nadiendev.ultimatefixestools.events.LagMonitorHandler;
import nadiendev.ultimatefixestools.logging.LogManager;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.apache.logging.log4j.Logger;

import java.util.concurrent.TimeUnit;

/**
 * Registro de todos los comandos de NadienFixesTools.
 *
 *  /nftps        → TPS, MSPT y tick time
 *  /nfresources  → CPU, RAM, entidades, chunks, tick time
 *  /nflag        → Detectar lag y guardar en quetolagea.log
 *  /nfuptime     → Tiempo de uptime del servidor
 */
public class CommandHandler {

    private static final Logger LOGGER =
            org.apache.logging.log4j.LogManager.getLogger("ultimatefixestools");

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(tpsCommand());
        dispatcher.register(resourcesCommand());
        dispatcher.register(lagCommand());
        dispatcher.register(uptimeCommand());
        LOGGER.info("[NFT Commands] /nftps /nfresources /nflag /nfuptime registrados");
    }

    // ── /nftps ────────────────────────────────────────────────────────────────
    private static LiteralArgumentBuilder<CommandSourceStack> tpsCommand() {
        return Commands.literal("nftps")
                .requires(src -> src.hasPermission(2))
                .executes(ctx -> {
                    double mspt   = LagMonitorHandler.getAverageMspt();
                    double tps    = LagMonitorHandler.msptToTps(mspt);
                    long   lastMs = LagMonitorHandler.getLastTickMs();

                    String color = tps >= 19 ? "§a" : tps >= 15 ? "§e" : "§c";

                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6[NFT] §lEstadísticas de TPS\n" +
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

                    long usedRam  = LagMonitorHandler.getUsedRamMB();
                    long totalRam = LagMonitorHandler.getTotalRamMB();
                    long maxRam   = LagMonitorHandler.getMaxRamMB();
                    double cpu    = LagMonitorHandler.getCpuUsage();
                    double mspt   = LagMonitorHandler.getAverageMspt();

                    // Contar entidades y chunks en todos los mundos
                    int entitiesCount = 0;
                    int chunksCount   = 0;
                    for (ServerLevel level : server.getAllLevels()) {
                        for (Entity e : level.getAllEntities()) entitiesCount++;
                        chunksCount += level.getChunkSource().getLoadedChunksCount();
                    }

                    // FIX: copiar a variables final para poder usarlas en la lambda
                    final int totalEntities = entitiesCount;
                    final int totalChunks   = chunksCount;

                    String cpuStr = cpu < 0
                            ? "§7N/A"
                            : (cpu > 0.8 ? "§c" : cpu > 0.5 ? "§e" : "§a") + String.format("%.1f%%", cpu * 100);

                    String ramPercent = String.format("%.0f%%", (usedRam * 100.0) / maxRam);
                    String ramColor   = usedRam > maxRam * 0.85 ? "§c" : usedRam > maxRam * 0.65 ? "§e" : "§a";

                    ctx.getSource().sendSuccess(() -> Component.literal(
                            "§6[NFT] §lRecursos del Servidor\n" +
                            "§7CPU:      " + cpuStr + "\n" +
                            "§7RAM:      " + ramColor + usedRam + "MB §7/ " + maxRam + "MB §8(" + ramPercent + ")\n" +
                            "§7Entidades:§b " + totalEntities + "\n" +
                            "§7Chunks:   §b" + totalChunks + "\n" +
                            "§7MSPT avg: §b" + String.format("%.2f", mspt) + " ms\n" +
                            "§7TPS:      §b" + String.format("%.2f", LagMonitorHandler.msptToTps(mspt))
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
                    report.append("§6[NFT] §lAnálisis de Lag\n");
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
                            "§7Guardado en §bNadienFixesTools/quetolagea.log"), false);

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
                            String.format("§6[NFT] §7Uptime del servidor: §b%dh %dm %ds",
                                    hours, minutes, seconds)
                    ), false);

                    return 1;
                });
    }
}