package com.cyngn.audiofx.stats;

import com.cyanogen.ambient.analytics.Event;
import com.cyngn.audiofx.Preset;
import com.cyngn.audiofx.activity.MasterConfigControl;
import com.cyngn.audiofx.eq.EqUtils;
import com.cyngn.audiofx.knobs.KnobCommander;

/**
 * Created by roman on 9/29/15.
 */
public class AppState {
    public static void appendState(MasterConfigControl control,
                                   KnobCommander knobs, Event.Builder builder) {
        // what's the current output device?
        builder.addField("state_current_device", control.getCurrentDeviceIdentifier());

        // what preset? if custom, what name/values?
        builder.addField("state_preset_name", control.getEqualizerManager().getCurrentPreset().getName());

        if (control.getEqualizerManager().getCurrentPreset() instanceof Preset.CustomPreset) {
            builder.addField("state_custom_preset_values",
                    EqUtils.floatLevelsToString(control.getEqualizerManager().getCurrentPreset().getLevels()));
        }

        // knob states
        if (control.hasMaxxAudio()) {
            builder.addField("state_maxx_volume", control.getMaxxVolumeEnabled());
        }

        if (knobs.hasBassBoost()) {
            builder.addField("state_knob_bass", knobs.getBassStrength());
        }
        if (knobs.hasTreble()) {
            builder.addField("state_knob_treble", knobs.getTrebleStrength());
        }
        if (knobs.hasVirtualizer()) {
            builder.addField("state_knob_virtualizer", knobs.getVirtualizerStrength());
        }
    }
}
