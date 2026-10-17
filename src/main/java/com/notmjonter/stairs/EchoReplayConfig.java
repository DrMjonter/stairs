package com.notmjonter.stairs;

import java.util.List;
import java.util.regex.Pattern;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Client-local delayed echoes; does not alter server sounds or Sound Physics settings. */
public final class EchoReplayConfig {
    public static final float REPLAY_VOLUME = 0.30f;
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.DoubleValue DELAY_SECONDS;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> BLACKLIST;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.push("echoBasin");
        ENABLED = builder.comment("Replay each sound once while in Echo Basin.")
                .translation("config.mjontersstairs.echo_enabled").define("enabled", true);
        DELAY_SECONDS = builder.comment("Delay in seconds, rounded to client ticks. Zero disables the delay, not the replay.")
                .translation("config.mjontersstairs.echo_delay").defineInRange("delaySeconds", 1.0, 0.0, 60.0);
        BLACKLIST = builder.comment("Excluded sound event IDs or audio asset IDs. * matches any characters.")
                .translation("config.mjontersstairs.echo_blacklist")
                .defineListAllowEmpty("blacklist", List.<String>of(), () -> "minecraft:ui.*",
                        value -> value instanceof String entry && validPattern(entry));
        builder.pop();
        SPEC = builder.build();
    }

    private EchoReplayConfig() {}

    public record Settings(boolean enabled, int delayTicks, List<String> blacklist) {}

    public static Settings settings() {
        if (!SPEC.isLoaded()) return new Settings(true, 20, List.of());
        return new Settings(ENABLED.get(), delayTicks(DELAY_SECONDS.get()), List.copyOf(BLACKLIST.get()));
    }

    public static int delayTicks(double seconds) {
        return (int) Math.clamp(Math.round((Double.isFinite(seconds) ? seconds : 1.0) * 20), 0, 1200);
    }

    public static boolean validPattern(String value) {
        return value != null && value.matches("[a-z0-9_.*-]+:[a-z0-9_./*-]+");
    }

    public static boolean blacklisted(List<String> entries, String eventId, String assetId) {
        for (String entry : entries) {
            String regex = String.join(".*", java.util.Arrays.stream(entry.split("\\*", -1))
                    .map(Pattern::quote).toList());
            if (eventId.matches(regex) || assetId != null && assetId.matches(regex)) return true;
        }
        return false;
    }
}
