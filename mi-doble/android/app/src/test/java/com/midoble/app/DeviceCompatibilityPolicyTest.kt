package com.midoble.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCompatibilityPolicyTest {
    @Test
    fun acceptsMinimumSupportedAndroidVersion() {
        val report = DeviceCompatibilityPolicy.evaluate(
            androidApi = 34,
            totalRamBytes = DeviceCompatibilityPolicy.MIN_RAM_BYTES,
            declaredStorageGb = 64
        )
        assertTrue(report.androidSupported)
        assertTrue(report.eligible)
    }

    @Test
    fun rejectsAndroidVersionsBelow14() {
        val report = DeviceCompatibilityPolicy.evaluate(
            androidApi = 33,
            totalRamBytes = DeviceCompatibilityPolicy.MIN_RAM_BYTES,
            declaredStorageGb = 64
        )
        assertFalse(report.androidSupported)
        assertFalse(report.eligible)
    }

    @Test
    fun acceptsExactlyFourGiBOfRam() {
        val report = DeviceCompatibilityPolicy.evaluate(
            androidApi = 34,
            totalRamBytes = DeviceCompatibilityPolicy.MIN_RAM_BYTES,
            declaredStorageGb = 64
        )
        assertTrue(report.ramSupported)
    }

    @Test
    fun rejectsRamBelowFourGiB() {
        val report = DeviceCompatibilityPolicy.evaluate(
            androidApi = 34,
            totalRamBytes = DeviceCompatibilityPolicy.MIN_RAM_BYTES - 1,
            declaredStorageGb = 64
        )
        assertFalse(report.ramSupported)
        assertFalse(report.eligible)
    }

    @Test
    fun acceptsExactlySixtyFourGbOfDeclaredStorage() {
        val report = DeviceCompatibilityPolicy.evaluate(
            androidApi = 34,
            totalRamBytes = DeviceCompatibilityPolicy.MIN_RAM_BYTES,
            declaredStorageGb = 64
        )
        assertTrue(report.storageVerified)
        assertFalse(report.storageBelowMinimum)
        assertTrue(report.eligible)
    }

    @Test
    fun rejectsDeclaredStorageBelowSixtyFourGb() {
        val report = DeviceCompatibilityPolicy.evaluate(
            androidApi = 34,
            totalRamBytes = DeviceCompatibilityPolicy.MIN_RAM_BYTES,
            declaredStorageGb = 63
        )
        assertFalse(report.storageVerified)
        assertTrue(report.storageBelowMinimum)
        assertFalse(report.eligible)
    }

    @Test
    fun leavesStorageUnverifiedWhenCapacityIsUnknown() {
        val report = DeviceCompatibilityPolicy.evaluate(
            androidApi = 34,
            totalRamBytes = DeviceCompatibilityPolicy.MIN_RAM_BYTES,
            declaredStorageGb = null
        )
        assertFalse(report.storageVerified)
        assertFalse(report.storageBelowMinimum)
        assertFalse(report.eligible)
    }

    @Test
    fun rejectsDeviceWhenMoreThanOneRequirementFails() {
        val report = DeviceCompatibilityPolicy.evaluate(
            androidApi = 33,
            totalRamBytes = DeviceCompatibilityPolicy.MIN_RAM_BYTES - 1,
            declaredStorageGb = 32
        )
        assertFalse(report.androidSupported)
        assertFalse(report.ramSupported)
        assertTrue(report.storageBelowMinimum)
        assertFalse(report.eligible)
    }
}
