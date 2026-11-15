package com.makambox.studio.model;

import com.makambox.studio.i18n.I18n;

/**
 * Selects the source channel that is converted to the mono analysis signal.
 *
 * <p>The analysis pipeline is deliberately monophonic. Keeping this choice
 * explicit prevents a stereo mix with partial phase cancellation from hiding
 * an otherwise clear melodic line.</p>
 */
public enum AudioChannelMode {
    AUTO("audio.channel.auto", "Otomatik (melodik kanal)"),
    MIX("audio.channel.mix", "Stereo karışım"),
    LEFT("audio.channel.left", "Sol kanal"),
    RIGHT("audio.channel.right", "Sağ kanal");

    private final String messageKey;
    private final String fallback;

    AudioChannelMode(String messageKey, String fallback) {
        this.messageKey = messageKey;
        this.fallback = fallback;
    }

    public String messageKey() { return messageKey; }

    @Override public String toString() {
        return I18n.get().textOrDefault(messageKey, fallback);
    }
}
