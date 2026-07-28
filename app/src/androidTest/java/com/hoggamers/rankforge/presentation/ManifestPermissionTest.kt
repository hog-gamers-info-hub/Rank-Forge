package com.hoggamers.rankforge.presentation

import android.content.pm.PackageManager
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ManifestPermissionTest {
    @Test
    fun manifestDoesNotRequestCameraOrMediaPermissions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        @Suppress("DEPRECATION")
        val packageInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS,
        )
        val requestedPermissions = packageInfo.requestedPermissions.orEmpty().toSet()

        setOf(
            "android.permission.CAMERA",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_VISUAL_USER_SELECTED",
        ).forEach { permission ->
            assertFalse("Unexpected permission requested: $permission", permission in requestedPermissions)
        }
    }
}
