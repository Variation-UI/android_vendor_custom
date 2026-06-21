#
# SPDX-FileCopyrightText: 2026 VariationUI
# SPDX-License-Identifier: Apache-2.0
#

custom_data_app_apks := $(sort $(shell find vendor/custom/data-app -name "*.apk" -type f 2>/dev/null))
custom_data_app_modules := $(foreach apk,$(custom_data_app_apks),vendor_custom_data_app_$(subst /,_,$(patsubst vendor/custom/data-app/%,%,$(basename $(apk)))))

PRODUCT_PACKAGES += \
    DataAppInstaller \
    $(custom_data_app_modules)

custom_data_app_apks :=
custom_data_app_modules :=
