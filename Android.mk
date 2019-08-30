ifneq ($(TARGET_EXCLUDES_AUDIOFX),true)

LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_PACKAGE_NAME	:= AudioFX
LOCAL_MODULE_TAGS	:= optional

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-support-v4 \
    org.lineageos.platform.internal

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_PRIVILEGED_MODULE := true

LOCAL_CERTIFICATE := platform

include $(BUILD_PACKAGE)

# Use the following include to make our test apk.
ifeq (,$(ONE_SHOT_MAKEFILE))
include $(call all-makefiles-under,$(LOCAL_PATH))
endif

endif # TARGET_EXCLUDES_AUDIOFX
