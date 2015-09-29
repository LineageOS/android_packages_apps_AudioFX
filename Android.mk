LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_PACKAGE_NAME	:= AudioFX
LOCAL_MODULE_TAGS	:= optional

LOCAL_OVERRIDES_PACKAGES := DSPManager

LOCAL_SRC_FILES := $(call all-java-files-under, src)

LOCAL_STATIC_JAVA_LIBRARIES := android-support-v4

LOCAL_PROGUARD_ENABLED := disabled

LOCAL_RESOURCE_DIR := $(addprefix $(LOCAL_PATH)/, res)
LOCAL_AAPT_FLAGS := --auto-add-overlay
LOCAL_AAPT_FLAGS += --extra-packages com.cyanogen.ambient

LOCAL_STATIC_JAVA_AAR_LIBRARIES := ambientsdk

LOCAL_PRIVILEGED_MODULE := true

# Sign the package when not using test-keys
ifneq ($(DEFAULT_SYSTEM_DEV_CERTIFICATE),build/target/product/security/testkey)
LOCAL_CERTIFICATE := cyngn-app
else
$(warning *** SIGNING AUDIOFX WITH TEST KEY ***)
endif

include $(BUILD_PACKAGE)
