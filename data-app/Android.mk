#
# SPDX-FileCopyrightText: 2026 VariationUI
# SPDX-License-Identifier: Apache-2.0
#

LOCAL_PATH := $(call my-dir)

custom_data_app_apks := $(sort $(shell find $(LOCAL_PATH) -name "*.apk" -type f 2>/dev/null))
custom_data_app_modules :=

define declare-custom-data-app-apk
include $$(CLEAR_VARS)
LOCAL_MODULE := vendor_custom_data_app_$(subst /,_,$(patsubst $(LOCAL_PATH)/%,%,$(basename $(1))))
LOCAL_MODULE_CLASS := ETC
LOCAL_MODULE_TAGS := optional
LOCAL_MODULE_SUFFIX := .apk
LOCAL_SRC_FILES := $(patsubst $(LOCAL_PATH)/%,%,$(1))
LOCAL_INSTALLED_MODULE_STEM := $(notdir $(1))
LOCAL_MODULE_PATH := $(patsubst %/,%,$$(TARGET_OUT_PRODUCT)/data-app/$(dir $(patsubst $(LOCAL_PATH)/%,%,$(1))))
include $$(BUILD_PREBUILT)
custom_data_app_modules += $$(LOCAL_MODULE)
endef

$(foreach apk,$(custom_data_app_apks),$(eval $(call declare-custom-data-app-apk,$(apk))))

custom_data_app_apks :=
custom_data_app_modules :=
