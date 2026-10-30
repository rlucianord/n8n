package com.midoble.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ModelRuntimePolicyTest {
    private val requirements = ModelRuntimeRequirements(
        minimumAndroidApi = 34,
        minimumAvailableRamBytes = 2L * 1024L * 1024L * 1024L,
        minimumFreeStorageBytes = 5L * 1024L * 1024L * 1024L,
        supportedAbis = setOf("arm64-v8a")
    )

    private fun capableFacts() = ModelRuntimeFacts(
        androidApi = 34,
        availableRamBytes = 3L * 1024L * 1024L * 1024L,
        freeStorageBytes = 8L * 1024L * 1024L * 1024L,
        deviceAbis = setOf("arm64-v8a", "armeabi-v7a"),
        isLowRamDevice = false
    )

    @Test
    fun acceptsDeviceWhenEveryModelRequirementIsMet() {
        val report = ModelRuntimePolicy.evaluate(requirements, capableFacts())
        assertTrue(report.eligible)
    }

    @Test
    fun rejectsUnsupportedAndroidApi() {
        val report = ModelRuntimePolicy.evaluate(requirements, capableFacts().copy(androidApi = 33))
        assertFalse(report.androidSupported)
        assertFalse(report.eligible)
    }

    @Test
    fun rejectsInsufficientAvailableRam() {
        val report = ModelRuntimePolicy.evaluate(
            requirements,
            capableFacts().copy(availableRamBytes = requirements.minimumAvailableRamBytes - 1)
        )
        assertFalse(report.ramAvailable)
        assertFalse(report.eligible)
    }

    @Test
    fun rejectsInsufficientFreeStorage() {
        val report = ModelRuntimePolicy.evaluate(
            requirements,
            capableFacts().copy(freeStorageBytes = requirements.minimumFreeStorageBytes - 1)
        )
        assertFalse(report.storageAvailable)
        assertFalse(report.eligible)
    }

    @Test
    fun rejectsUnsupportedDeviceAbi() {
        val report = ModelRuntimePolicy.evaluate(
            requirements,
            capableFacts().copy(deviceAbis = setOf("x86_64"))
        )
        assertFalse(report.abiSupported)
        assertFalse(report.eligible)
    }

    @Test
    fun rejectsLowRamDeviceUnlessModelManifestAllowsIt() {
        val report = ModelRuntimePolicy.evaluate(
            requirements,
            capableFacts().copy(isLowRamDevice = true)
        )
        assertFalse(report.lowRamDeviceAllowed)
        assertFalse(report.eligible)
    }

    @Test
    fun acceptsLowRamDeviceOnlyWhenModelManifestAllowsIt() {
        val lowRamRequirements = requirements.copy(allowLowRamDevice = true)
        val report = ModelRuntimePolicy.evaluate(
            lowRamRequirements,
            capableFacts().copy(isLowRamDevice = true)
        )
        assertTrue(report.lowRamDeviceAllowed)
        assertTrue(report.eligible)
    }
}
