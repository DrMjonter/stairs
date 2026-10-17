package com.notmjonter.stairs;

import net.neoforged.fml.common.Mod;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

@Mod(Stairs.MODID)
public class Stairs {
    public static final String MODID = "mjontersstairs";

    public Stairs(IEventBus modEventBus, net.neoforged.fml.ModContainer modContainer) {
        modContainer.registerConfig(net.neoforged.fml.config.ModConfig.Type.CLIENT,
                EchoReplayConfig.SPEC, Stairs.MODID + "-client.toml");
        PlasticBlocks.initialize();
        DimensionBlocks.initialize();
        EchoListeningWells.FEATURES.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(EchoListeningWells::tick);
        NeoForge.EVENT_BUS.addListener(EchoListeningWells::stopped);
        NeoForge.EVENT_BUS.addListener(EchoBasinProgress::tick);
        NeoForge.EVENT_BUS.addListener(EchoBasinProgress::stopped);
        NeoForge.EVENT_BUS.addListener(EchoExitStaircases::tick);
        NeoForge.EVENT_BUS.addListener(EchoExitStaircases::stopped);
        NeoForge.EVENT_BUS.addListener(UnfinishedSunLighting::tick);
        NeoForge.EVENT_BUS.addListener(TickstormWeather::tick);
        NeoForge.EVENT_BUS.addListener(TickstormWeather::changedDimension);
        NeoForge.EVENT_BUS.addListener(TickstormWeather::login);
        NeoForge.EVENT_BUS.addListener(TickstormWeather::stopped);
        EchoSounds.EVENTS.register(modEventBus);
        EchoLava.initialize();
        EchoLava.TYPES.register(modEventBus);
        EchoLava.FLUIDS.register(modEventBus);
        FutureReplays.ENTITIES.register(modEventBus);
        modEventBus.addListener(FutureReplays::attributes);
        NeoForge.EVENT_BUS.addListener(FutureReplays::tick);
        NeoForge.EVENT_BUS.addListener(FutureReplays::stopped);
        NeoForge.EVENT_BUS.addListener(PlasticBlocks::strip);
        NeoForge.EVENT_BUS.addListener(StaircaseProtection::breakBlock);
        NeoForge.EVENT_BUS.addListener(StaircaseProtection::placeBlock);
        NeoForge.EVENT_BUS.addListener(StaircaseProtection::explosion);
        NeoForge.EVENT_BUS.addListener(StaircaseProtection::piston);
        NeoForge.EVENT_BUS.addListener(GarageTravel::tick);
        NeoForge.EVENT_BUS.addListener(GarageTravel::stopped);
        NeoForge.EVENT_BUS.addListener(FutureClock::loaded);
        NeoForge.EVENT_BUS.addListener(FutureClock::tick);
        NeoForge.EVENT_BUS.addListener(FutureClock::changedDimension);
        NeoForge.EVENT_BUS.addListener(FutureClock::login);
        NeoForge.EVENT_BUS.addListener(FutureClock::respawn);
        NeoForge.EVENT_BUS.addListener(FutureClock::sleep);
        NeoForge.EVENT_BUS.addListener(FutureClock::continueSleeping);
        NeoForge.EVENT_BUS.addListener(FutureClock::sleepingTick);
        NeoForge.EVENT_BUS.addListener(FutureFinale::tick);
        NeoForge.EVENT_BUS.addListener(FutureFinale::stopped);
        NeoForge.EVENT_BUS.addListener(FutureMobSpawns::joining);
        NeoForge.EVENT_BUS.addListener(FutureMobSpawns::tick);
        NeoForge.EVENT_BUS.addListener(FutureMobSpawns::stopped);
        NeoForge.EVENT_BUS.addListener(FutureVillagerPrayer::tick);
        NeoForge.EVENT_BUS.addListener(FutureVillagerPrayer::interact);
        NeoForge.EVENT_BUS.addListener(FutureVillagerPrayer::interactSpecific);
        NeoForge.EVENT_BUS.addListener(FutureVillagerReactions::damaged);
        NeoForge.EVENT_BUS.addListener(FutureVillagerReactions::stopped);
        NeoForge.EVENT_BUS.addListener(FutureContainment::tick);
        NeoForge.EVENT_BUS.addListener(FutureContainment::stopped);
        NeoForge.EVENT_BUS.addListener(MirrorSnapshots::tick);
        NeoForge.EVENT_BUS.addListener(MirrorSnapshots::stopping);
        NeoForge.EVENT_BUS.addListener(MirrorSnapshots::login);
        NeoForge.EVENT_BUS.addListener(MirrorSnapshots::changedDimension);
        GarageDimension.GENERATORS.register(modEventBus);
        GarageDimension.BLOCKS.register(modEventBus);
        FutureBlocks.BLOCKS.register(modEventBus);
        FutureBlocks.ITEMS.register(modEventBus);
        StairsCreativeTab.TABS.register(modEventBus);
        StaircaseEffects.EFFECTS.register(modEventBus);
        modEventBus.addListener(StaircaseEffects::registerPayloads);
        NeoForge.EVENT_BUS.addListener(StaircaseEffects::playerTick);
        NeoForge.EVENT_BUS.addListener(StaircaseEffects::jump);
        NeoForge.EVENT_BUS.addListener(StairsCommands::register);
        NeoForge.EVENT_BUS.addListener(StaircaseLifecycle::tick);
    }
}
