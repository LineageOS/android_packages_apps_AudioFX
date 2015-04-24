LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_PACKAGE_NAME	:= AudioFX
LOCAL_MODULE_TAGS	:= optional

LOCAL_OVERRIDES_PACKAGES := DSPManager
LOCAL_STATIC_JAVA_LIBRARIES := android-visualizer android-support-v13
LOCAL_SRC_FILES := $(call all-java-files-under, src)
LOCAL_JAVA_LIBRARIES := framework
LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4
LOCAL_PRIVILEGED_MODULE := true
include $(BUILD_PACKAGE)
