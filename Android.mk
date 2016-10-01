LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_PACKAGE_NAME	:= AudioFX
LOCAL_MODULE_TAGS	:= optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

ifeq ($(wildcard $(LOCAL_PATH)/src_effects_priv),)
LOCAL_SRC_FILES += $(call all-java-files-under, src_effects)
else
$(warning *** including private implementations of effects ***)
LOCAL_AAPT_FLAGS += --rename-manifest-package com.cyngn.audiofx
LOCAL_SRC_FILES += $(call all-java-files-under, src_effects_priv)
endif

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4 org.cyanogenmod.platform.sdk

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_PRIVILEGED_MODULE := true

# Sign the package when not using test-keys
ifneq ($(DEFAULT_SYSTEM_DEV_CERTIFICATE),build/target/product/security/testkey)
LOCAL_CERTIFICATE := cyngn-app
endif

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
ifeq (,$(ONE_SHOT_MAKEFILE))
include $(call all-makefiles-under,$(LOCAL_PATH))
endif
