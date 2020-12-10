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
        OCastRawDeviceLayer.Status.WIFI_PASSWORD_TOO_SHORT -> OCastError.Status.DEVICE_LAYER_WIFI_PASSWORD_TOO_SHORT
        OCastRawDeviceLayer.Status.INTERNAL_ERROR -> OCastError.Status.DEVICE_LAYER_INTERNAL_ERROR
        null -> OCastError.Status.DEVICE_LAYER_MISSING_STATUS_ERROR
    }
}
