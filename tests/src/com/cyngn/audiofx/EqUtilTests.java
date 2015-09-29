package com.cyngn.audiofx;

import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import com.cyngn.audiofx.eq.EqUtils;

/**
 * Created by roman on 9/29/15.
 */
public class EqUtilTests extends AndroidTestCase {

    private Preset.PermCustomPreset permPreset;
    private Preset.PermCustomPreset permPresetCopy;
    private Preset.CustomPreset customPreset;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        permPreset = new Preset.PermCustomPreset("test perm preset", new float[]{10, 50, 20, 30, 10000});
        permPresetCopy = new Preset.PermCustomPreset("test perm preset", new float[]{10, 50, 20, 30, 10000});
        customPreset = new Preset.CustomPreset("test custom preset", new float[]{10, 50, 20, 30, 10000}, false);
    }

    @SmallTest
    public void testConvertDecibelsToMillibels() {
        final float[] convertedMillibels = EqUtils.convertDecibelsToMillibels(permPreset.getLevels());

        float[] manualMillibels = new float[permPreset.getLevels().length];
        for(int i = 0; i < manualMillibels.length; i++) {
            manualMillibels[i] = permPreset.mLevels[i] * 100;
        }

        for(int i = 0 ; i < manualMillibels.length; i++) {
            assertEquals(manualMillibels[i], convertedMillibels[i]);
        }
    }

}
