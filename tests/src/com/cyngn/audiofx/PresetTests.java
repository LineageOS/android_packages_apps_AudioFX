package com.cyngn.audiofx;

import android.os.Parcel;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

/**
 * Created by roman on 9/29/15.
 */
public class PresetTests extends AndroidTestCase {

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
    public void testPresetsEqual() {
        assertNotSame(permPreset, customPreset);
        assertEquals(permPreset, permPresetCopy);
    }

    @SmallTest
    public void testPresetsFromString() {
        final String permPresetTest = permPreset.toString();
        final Preset.PermCustomPreset fromString = Preset.PermCustomPreset.fromString(permPresetTest);

        assertEquals(permPreset, fromString);
    }

    @SmallTest
    public void testPermPresetParcelable() {
        Parcel parcel = Parcel.obtain();

        // write the parcel
        permPreset.writeToParcel(parcel, 0);

        // reset position for reading
        parcel.setDataPosition(0);

        // reconstruct object
        final Preset.PermCustomPreset fromParcel = Preset.PermCustomPreset.CREATOR.createFromParcel(parcel);

        assertNotNull(fromParcel);

        assertEquals(permPreset.getName(), fromParcel.getName());
        assertPresetLevels(permPreset, fromParcel);

        assertEquals(permPreset, fromParcel);
    }

    @SmallTest
    public void testCustomPresetParcelable() {
        Parcel parcel = Parcel.obtain();

        // write the parcel
        customPreset.writeToParcel(parcel, 0);

        // reset position for reading
        parcel.setDataPosition(0);

        // reconstruct object
        final Preset.CustomPreset fromParcel = Preset.CustomPreset.CREATOR.createFromParcel(parcel);

        assertNotNull(fromParcel);

        assertEquals(customPreset.getName(), fromParcel.getName());
        assertPresetLevels(customPreset, fromParcel);
        assertEquals(customPreset.isLocked(), fromParcel.isLocked());

        assertEquals(customPreset, fromParcel);
    }

    private void assertPresetLevels(Preset p1, Preset p2) {
        for(int i = 0; i < p1.getLevels().length; i++) {
            assertEquals(p1.getLevels()[i], p2.getLevels()[i]);
        }
    }
}
