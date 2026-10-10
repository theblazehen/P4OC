package dev.blazelight.p4oc.core.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnsupportedV2ApiTest {
    @Test
    fun `v1-only route on a v2 server reports which operation is unavailable`() = runTest {
        val result = safeApiCall { unsupportedV2Api().getProviderAuthMethods(directory = null, workspace = null) }

        assertTrue(result is ApiResult.Error)
        assertEquals(
            "getProviderAuthMethods is not available on this OpenCode v2 server",
            (result as ApiResult.Error).message,
        )
    }
}
