package com.notmjonter.stairs;

import de.maxhenkel.voicechat.api.ForgeVoicechatPlugin;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.ClientSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientReceiveSoundEvent;
import de.maxhenkel.voicechat.api.events.ClientVoicechatConnectionEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;

/** Discovered only by Simple Voice Chat; the mod still loads when its API is absent. */
@ForgeVoicechatPlugin
public final class EchoVoicePlugin implements VoicechatPlugin {
    @Override public String getPluginId() { return Stairs.MODID + "_echo_basin"; }

    @Override public void registerEvents(EventRegistration events) {
        events.registerEvent(de.maxhenkel.voicechat.api.events.VoicechatServerStartedEvent.class,
                event -> EchoWellVoice.started(event.getVoicechat()));
        events.registerEvent(de.maxhenkel.voicechat.api.events.MicrophonePacketEvent.class, EchoWellVoice::microphone);
        events.registerEvent(de.maxhenkel.voicechat.api.events.VoicechatServerStoppedEvent.class,
                event -> EchoWellVoice.stopping());
        if (FMLEnvironment.dist != Dist.CLIENT) return;
        EchoVoiceClient.register();
        events.registerEvent(ClientSoundEvent.class, EchoVoiceClient::microphone);
        events.registerEvent(ClientReceiveSoundEvent.EntitySound.class, EchoVoiceClient::received);
        events.registerEvent(ClientVoicechatConnectionEvent.class, event -> EchoVoiceClient.connectionChanged());
    }
}
