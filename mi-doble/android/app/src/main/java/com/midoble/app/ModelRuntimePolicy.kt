package com.midoble.app

/** Model-specific limits must come from a tested model manifest, not guesses. */
data class ModelRuntimeRequirements(
    val minimumAndroidApi: Int,
    val minimumAvailableRamBytes: Long,
    val minimumFreeStorageBytes: Long,
    val supportedAbis: Set<String>,
    val allowLowRamDevice: Boolean = false
) {
    init {
        require(minimumAndroidApi >= 1) { "minimumAndroidApi must be positive" }
        require(minimumAvailableRamBytes > 0) { "minimumAvailableRamBytes must be positive" }
        require(minimumFreeStorageBytes > 0) { "minimumFreeStorageBytes must be positive" }
        require(supportedAbis.isNotEmpty()) { "supportedAbis must not be empty" }
    }
}

data class ModelRuntimeFacts(
    val androidApi: Int,
    val availableRamBytes: Long,
    val freeStorageBytes: Long,
    val deviceAbis: Set<String>,
    val isLowRamDevice: Boolean
)

data class ModelRuntimeReport(
    val androidSupported: Boolean,
    val ramAvailable: Boolean,
    val storageAvailable: Boolean,
    val abiSupported: Boolean,
    val lowRamDeviceAllowed: Boolean,
    val eligible: Boolean
)

object ModelRuntimePolicy {
    fun evaluate(
        requirements: ModelRuntimeRequirements,
        facts: ModelRuntimeFacts
    ): ModelRuntimeReport {
        val androidSupported = facts.androidApi >= requirements.minimumAndroidApi
        val ramAvailable = facts.availableRamBytes >= requirements.minimumAvailableRamBytes
        val storageAvailable = facts.freeStorageBytes >= requirements.minimumFreeStorageBytes
        val abiSupported = facts.deviceAbis.any(requirements.supportedAbis::contains)
        val lowRamDeviceAllowed = !facts.isLowRamDevice || requirements.allowLowRamDevice

        return ModelRuntimeReport(
            androidSupported = androidSupported,
            ramAvailable = ramAvailable,
            storageAvailable = storageAvailable,
            abiSupported = abiSupported,
            lowRamDeviceAllowed = lowRamDeviceAllowed,
            eligible = androidSupported && ramAvailable && storageAvailable &&
                abiSupported && lowRamDeviceAllowed
        )
    }
}
