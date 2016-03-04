LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_PACKAGE_NAME := AudioFXTests
LOCAL_INSTRUMENTATION_FOR := AudioFX
LOCAL_JAVA_LIBRARIES := android.test.runner
LOCAL_PROGUARD_ENABLED := disabled

LOCAL_JACK_ENABLED := disabled

# Sign the package when not using test-keys
ifneq ($(DEFAULT_SYSTEM_DEV_CERTIFICATE),build/target/product/security/testkey)
LOCAL_CERTIFICATE := cyngn-app
else
$(warning *** SIGNING AUDIOFX WITH TEST KEY ***)
endif


include $(BUILD_PACKAGE)
