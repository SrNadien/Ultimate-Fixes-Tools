package nadiendev.ultimatefixestools.events;

import nadiendev.ultimatefixestools.config.ModConfig;
import nadiendev.ultimatefixestools.logging.NftLogManager;
import nadiendev.ultimatefixestools.util.StackTraceAnalyzer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.BlockEvent.NeighborNotifyEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;


public class FireFixHandler {

    private static final Logger LOGGER = LogManager.getLogger("nadienfixestools");

    private record FireEntry(BlockPos pos, long placedTick, int attempts, BlockState fireState) {}

    private final Map<BlockPos, FireEntry> trackedFires = new ConcurrentHashMap<>();
    private final Set<BlockPos> restoredByUs = ConcurrentHashMap.newKeySet();

    // ── Detectar colocación de fuego ─────────────────────────────────────────

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!ModConfig.ENABLE_FIRE_FIX) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockState placed = event.getPlacedBlock();
        if (!placed.is(Blocks.FIRE) && !placed.is(Blocks.SOUL_FIRE)) return;

        BlockPos pos = event.getPos();
        if (restoredByUs.contains(pos)) return;

        if (ModConfig.IGNORE_PLAYER_CAUSED && event.getEntity() instanceof Player) return;

        long tick = level.getGameTime();
        trackedFires.put(pos, new FireEntry(pos, tick, 0, placed));

        if (ModConfig.DEBUG_FIRE) {
            LOGGER.debug("[NFT Fire] Fuego registrado en {} (tick {})", pos, tick);
        }
    }

    // ── Detectar remoción de fuego via NeighborNotify ─────────────────────────

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onNeighborNotify(NeighborNotifyEvent event) {
        if (!ModConfig.ENABLE_FIRE_FIX) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;

        BlockPos pos = event.getPos();
        BlockState current = level.getBlockState(pos);

        FireEntry entry = trackedFires.get(pos);
        if (entry == null) return;

        if (!current.isAir()) {
            trackedFires.remove(pos);
            return;
        }

        StackTraceAnalyzer.AnalysisResult result = StackTraceAnalyzer.analyze();
        String typeStr = StackTraceAnalyzer.typeName(result.type());
        long ticksSincePlaced = level.getGameTime() - entry.placedTick();

        NftLogManager.logFire("FIRE_REMOVED",
                typeStr + ":" + result.className(),
                "pos=" + pos + " ticks_tras_colocacion=" + ticksSincePlaced
                        + " traza=" + result.fullTrace());

        NftLogManager.logRuta(typeStr, result.className(),
                "Fuego removido en " + pos + " (" + ticksSincePlaced + " ticks después)");

        if (ModConfig.DEBUG_FIRE) {
            LOGGER.warn("[NFT Fire] Fuego REMOVIDO en {} ({} ticks) por {} [{}]",
                    pos, ticksSincePlaced, result.className(), typeStr);
        }

        if (ticksSincePlaced <= 2 && entry.attempts() < ModConfig.MAX_FIRE_RESTORE_ATTEMPTS) {
            // FIX: +1 para que el límite de intentos funcione correctamente
            trackedFires.put(pos, new FireEntry(pos, entry.placedTick(),
                    entry.attempts() + 1, entry.fireState()));
        } else {
            trackedFires.remove(pos);
        }
    }

    // ── Tick: restaurar fuegos removidos ─────────────────────────────────────

    @SubscribeEvent
    public void onLevelTick(LevelTickEvent.Post event) {
        if (!ModConfig.ENABLE_FIRE_FIX) return;
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (trackedFires.isEmpty()) return;

        long currentTick = level.getGameTime();

        Iterator<Map.Entry<BlockPos, FireEntry>> it = trackedFires.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, FireEntry> mapEntry = it.next();
            BlockPos pos = mapEntry.getKey();
            FireEntry entry = mapEntry.getValue();

            if (currentTick <= entry.placedTick()) continue;

            BlockState current = level.getBlockState(pos);

            if (current.isAir()) {
                if (entry.attempts() < ModConfig.MAX_FIRE_RESTORE_ATTEMPTS) {
                    restoredByUs.add(pos);
                    level.setBlock(pos, entry.fireState(), 3);
                    restoredByUs.remove(pos);

                    LOGGER.info("[NFT Fire] Fuego RESTAURADO en {} (intento {}/{})",
                            pos, entry.attempts() + 1, ModConfig.MAX_FIRE_RESTORE_ATTEMPTS);

                    NftLogManager.logFire("FIRE_RESTORED", "NFT_SYSTEM",
                            "pos=" + pos + " intento=" + (entry.attempts() + 1));

                    trackedFires.put(pos, new FireEntry(pos, currentTick,
                            entry.attempts() + 1, entry.fireState()));
                } else {
                    LOGGER.warn("[NFT Fire] Fuego en {} no pudo ser restaurado ({} intentos agotados).",
                            pos, ModConfig.MAX_FIRE_RESTORE_ATTEMPTS);
                    NftLogManager.logFire("FIRE_RESTORE_FAILED", "NFT_SYSTEM",
                            "pos=" + pos + " intentos_agotados=" + ModConfig.MAX_FIRE_RESTORE_ATTEMPTS);
                    it.remove();
                }
            } else {
                it.remove();
            }
        }
    }
}