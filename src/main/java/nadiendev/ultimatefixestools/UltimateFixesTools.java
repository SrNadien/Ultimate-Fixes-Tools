package nadiendev.ultimatefixestools;

import nadiendev.ultimatefixestools.commands.CommandHandler;
import nadiendev.ultimatefixestools.config.ModConfig;
import nadiendev.ultimatefixestools.events.FireFixHandler;
import nadiendev.ultimatefixestools.events.LagMonitorHandler;
import nadiendev.ultimatefixestools.logging.NftLogManager;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(UltimateFixesTools.MODID)
public class UltimateFixesTools {

    public static final String MODID = "ultimatefixestools";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    private static long serverStartTime = 0;

    public UltimateFixesTools(IEventBus modEventBus, ModContainer container) {
        ModConfig.load();

        modEventBus.addListener(this::commonSetup);

        NeoForge.EVENT_BUS.register(this);
        NeoForge.EVENT_BUS.register(new FireFixHandler());
        NeoForge.EVENT_BUS.register(new LagMonitorHandler());

        LOGGER.info("[UltimateFixesTools] Mod cargado. Fire fix: {}, Debug: {}",
                ModConfig.ENABLE_FIRE_FIX, ModConfig.DEBUG_FIRE);
    }

    private void commonSetup(final FMLCommonSetupEvent event) {
        NftLogManager.init();
        LOGGER.info("[NadienFixesTools] Sistema de logs inicializado en /UltimateFixesTools/");
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        serverStartTime = System.currentTimeMillis();
        LOGGER.info("[UltimateFixesTools] Servidor iniciado. Fire fix activo: {}", ModConfig.ENABLE_FIRE_FIX);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandHandler.register(event.getDispatcher());
        LOGGER.info("[UltimateFixesTools] Comandos registrados: /nftps /nfresources /nflag /nfuptime");
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        NftLogManager.flush();
        LOGGER.info("[NadienFixesTools] Servidor detenido. Logs guardados.");
    }

    public static long getServerStartTime() {
        return serverStartTime;
    }
}