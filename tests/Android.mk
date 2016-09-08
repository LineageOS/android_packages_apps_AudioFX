LOCAL_PATH:= $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := tests

LOCAL_SRC_FILES := $(call all-subdir-java-files)

LOCAL_PACKAGE_NAME := AudioFXTests
LOCAL_INSTRUMENTATION_FOR := AudioFX

LOCAL_STATIC_JAVA_LIBRARIES := \
        audiofx-android-support-test

LOCAL_JAVA_LIBRARIES := \
        android-support-v4 \

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_JACK_ENABLED := disabled

# Sign the package when not using test-keys
ifneq ($(DEFAULT_SYSTEM_DEV_CERTIFICATE),build/target/product/security/testkey)
LOCAL_CERTIFICATE := cyngn-app
else
$(warning *** SIGNING MODIOFX WITH TEST KEY ***)
endif

include $(BUILD_PACKAGE)

include $(CLEAR_VARS)

LOCAL_PREBUILT_STATIC_JAVA_LIBRARIES := \
    audiofx-android-support-test:lib/rules-0.3-release.jar

include $(BUILD_MULTI_PREBUILT)
