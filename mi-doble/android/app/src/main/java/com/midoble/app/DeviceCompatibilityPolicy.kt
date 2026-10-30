package com.midoble.app

/** Installation requirements agreed for the first Mi Doble Android release. */
object DeviceCompatibilityPolicy {
    const val MIN_ANDROID_API = 34
    const val MIN_RAM_GIB = 4L
    const val MIN_STORAGE_GIB = 64

    private const val BYTES_PER_GIB = 1024L * 1024L * 1024L
    const val MIN_RAM_BYTES = MIN_RAM_GIB * BYTES_PER_GIB

    /**
     * declaredStorageGb must come from the device specifications or an explicit
     * user confirmation. Android's data-partition size is not the phone's
     * advertised storage capacity and must not be used as a substitute.
     */
    fun evaluate(
        androidApi: Int,
        totalRamBytes: Long,
        declaredStorageGb: Int?
    ): DeviceCompatibilityReport {
        val androidSupported = androidApi >= MIN_ANDROID_API
        val ramSupported = totalRamBytes >= MIN_RAM_BYTES
        val storageVerified = declaredStorageGb != null && declaredStorageGb >= MIN_STORAGE_GIB
        val storageBelowMinimum = declaredStorageGb != null && declaredStorageGb < MIN_STORAGE_GIB

        return DeviceCompatibilityReport(
            androidSupported = androidSupported,
            ramSupported = ramSupported,
            storageVerified = storageVerified,
            storageBelowMinimum = storageBelowMinimum,
            eligible = androidSupported && ramSupported && storageVerified
        )
    }
}

data class DeviceCompatibilityReport(
    val androidSupported: Boolean,
    val ramSupported: Boolean,
    val storageVerified: Boolean,
    val storageBelowMinimum: Boolean,
    val eligible: Boolean
)
