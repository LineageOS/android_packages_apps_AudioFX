package org.lineageos.audiofx.eq;

import org.lineageos.audiofx.Preset;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

/**
 * Created by roman on 9/29/15.
 */
public class EqUtilTests {

    private Preset.PermCustomPreset permPreset;
    private Preset.PermCustomPreset permPresetCopy;
    private Preset.CustomPreset customPreset;

    @Before
    public void setUp() throws Exception {
        permPreset = new Preset.PermCustomPreset("test perm preset", new float[]{10, 50, 20, 30, 10000});
        permPresetCopy = new Preset.PermCustomPreset("test perm preset", new float[]{10, 50, 20, 30, 10000});
        customPreset = new Preset.CustomPreset("test custom preset", new float[]{10, 50, 20, 30, 10000}, false);
    }

    @Test
    public void testConvertDecibelsToMillibels() {
        final float[] convertedMillibels = EqUtils.convertDecibelsToMillibels(permPreset.getLevels());

        float[] manualMillibels = new float[permPreset.getLevels().length];
        for (int i = 0; i < manualMillibels.length; i++) {
            manualMillibels[i] = permPreset.getLevels()[i] * 100;
        }

        for (int i = 0; i < manualMillibels.length; i++) {
            Assert.assertEquals(manualMillibels[i], convertedMillibels[i], 0);
        }
    }

}
