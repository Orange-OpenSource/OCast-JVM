/*
 * Copyright 2020 Orange
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.ocast.sdk.core.models

/**
 * Converts the status of an OCast device layer to an OCast error status.
 *
 * @return The OCast error status.
 */
internal fun OCastRawDeviceLayer.Status?.toOCastErrorStatus(): OCastError.Status? {
    return when (this) {
        OCastRawDeviceLayer.Status.OK -> null
        OCastRawDeviceLayer.Status.JSON_FORMAT_ERROR -> OCastError.Status.DEVICE_LAYER_JSON_FORMAT_ERROR
        OCastRawDeviceLayer.Status.VALUE_FORMAT_ERROR -> OCastError.Status.DEVICE_LAYER_VALUE_FORMAT_ERROR
        OCastRawDeviceLayer.Status.MISSING_MANDATORY_FIELD -> OCastError.Status.DEVICE_LAYER_MISSING_MANDATORY_FIELD
        OCastRawDeviceLayer.Status.FORBIDDEN_UNSECURE_MODE -> OCastError.Status.DEVICE_LAYER_FORBIDDEN_UNSECURE_MODE
        OCastRawDeviceLayer.Status.INTERNAL_ERROR -> OCastError.Status.DEVICE_LAYER_INTERNAL_ERROR
        OCastRawDeviceLayer.Status.UNKNOWN_ERROR -> OCastError.Status.DEVICE_LAYER_UNKNOWN_ERROR
        null -> OCastError.Status.DEVICE_LAYER_MISSING_STATUS
    }
}
