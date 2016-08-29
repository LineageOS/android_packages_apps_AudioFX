/*
 * Copyright (C) 2016 The CyanogenMod Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.cyanogenmod.audiofx.stats;

import com.cyanogen.ambient.analytics.Event;
import org.cyanogenmod.audiofx.Preset;
import org.cyanogenmod.audiofx.activity.MasterConfigControl;
import org.cyanogenmod.audiofx.eq.EqUtils;
import org.cyanogenmod.audiofx.knobs.KnobCommander;

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
