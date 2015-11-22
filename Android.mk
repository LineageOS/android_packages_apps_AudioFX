LOCAL_PATH:= $(call my-dir)
include $(CLEAR_VARS)

LOCAL_MODULE_TAGS := optional

LOCAL_STATIC_JAVA_LIBRARIES := \
    android-visualizer \
    android-support-design-res

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_JAVA_LIBRARIES := framework

LOCAL_OVERRIDES_PACKAGES := DSPManager

LOCAL_PACKAGE_NAME := AudioFX

LOCAL_PRIVILEGED_MODULE := true

include $(BUILD_PACKAGE)
