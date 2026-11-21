package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app identity and firebase config strings from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("StudyFlow", context.getString(R.string.app_name))
        assertEquals("Study smarter. Stay organized. Achieve more.", context.getString(R.string.app_tagline))
        assertTrue(context.getString(R.string.firestore_database_id).isNotBlank())
        assertTrue(context.getString(R.string.ai_disclaimer).contains("educational"))
    }
}
