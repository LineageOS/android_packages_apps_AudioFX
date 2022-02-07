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
package org.lineageos.audiofx.backends;

import android.content.Context;
import android.media.AudioDeviceInfo;

interface IEffectFactory {

    /**
     * Create a new EffectSet based on current stream parameters.
     *
     * @param context       context to create the effect with
     * @param sessionId     session id to attach the effect to
     * @param currentDevice current device that the effect should initially setup for
     * @return an {@link EffectSet}
     */
    EffectSet createEffectSet(Context context, int sessionId, AudioDeviceInfo currentDevice);
}
