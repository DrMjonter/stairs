package com.notmjonter.stairs;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Author-supplied Echo Basin sound assets; shared by clients and servers. */
public final class EchoSounds {
    public static final DeferredRegister<SoundEvent> EVENTS =
            DeferredRegister.create(Registries.SOUND_EVENT, Stairs.MODID);
    public static final SoundEvent STONE_BREAK = sound("stone.break");
    public static final SoundEvent STONE_STEP = sound("stone.step");
    public static final SoundEvent STONE_PLACE = sound("stone.place");
    public static final SoundEvent STONE_HIT = sound("stone.hit");
    public static final SoundEvent STONE_FALL = sound("stone.fall");
    public static final SoundEvent GRASS_BREAK = sound("grass.break");
    public static final SoundEvent GRASS_PLACE = sound("grass.place");
    public static final SoundEvent GRASS_HIT = sound("grass.hit");
    public static final SoundEvent GRASS_FALL = sound("grass.fall");
    public static final SoundEvent LANTERN_BREAK = sound("lantern.break");
    public static final SoundEvent LANTERN_STEP = sound("lantern.step");
    public static final SoundEvent LANTERN_PLACE = sound("lantern.place");
    public static final SoundEvent LANTERN_HIT = sound("lantern.hit");
    public static final SoundEvent LANTERN_FALL = sound("lantern.fall");
    public static final SoundEvent LAVA_AMBIENT = sound("lava.ambient");
    public static final SoundEvent LAVA_POP = sound("lava.pop");
    public static final SoundEvent LAVA_FILL = sound("lava.fill");
    public static final SoundEvent LAVA_EMPTY = sound("lava.empty");
    public static final SoundEvent AMBIENCE = sound("ambience");
    public static final SoundEvent WELL_CUE = sound("well.cue");
    public static final SoundEvent WELL_QUESTION_MATTERS = sound("well.question_matters");
    public static final SoundEvent WELL_QUESTION_FEAR = sound("well.question_fear");
    public static final SoundEvent WELL_QUESTION_PURPOSE = sound("well.question_purpose");
    public static final SoundEvent WELL_ACTIVATION = sound("well.activation");
    public static final SoundEvent WELL_SUCCESS = sound("well.success");
    public static final SoundEvent WELL_DENY = sound("well.deny");
    public static final SoundEvent WELL_GUIDANCE_WHISPER = sound("well.guidance_whisper");
    public static final SoundEvent[] WELL_QUESTIONS = {
            WELL_QUESTION_MATTERS, WELL_QUESTION_FEAR, WELL_QUESTION_PURPOSE
    };
    public static final SoundType STONE = new net.neoforged.neoforge.common.util.DeferredSoundType(1, 1,
            () -> STONE_BREAK, () -> STONE_STEP, () -> STONE_PLACE, () -> STONE_HIT, () -> STONE_FALL);
    public static final SoundType GRASS = new net.neoforged.neoforge.common.util.DeferredSoundType(1, 1,
            () -> GRASS_BREAK, () -> SoundEvents.NYLIUM_STEP, () -> GRASS_PLACE, () -> GRASS_HIT, () -> GRASS_FALL);
    public static final SoundType LANTERN = new net.neoforged.neoforge.common.util.DeferredSoundType(1, 1,
            () -> LANTERN_BREAK, () -> LANTERN_STEP, () -> LANTERN_PLACE, () -> LANTERN_HIT, () -> LANTERN_FALL);

    private EchoSounds() {}

    private static SoundEvent sound(String suffix) {
        String name = "echo_basin." + suffix;
        SoundEvent event = SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(Stairs.MODID, name));
        EVENTS.register(name, () -> event);
        return event;
    }
}
