package com.tonezen.app.domain.avatar

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AvatarUrlTest {
  @Test
  fun normalizeAvatarUrl_rewritesEmulatorHostToClientBaseUrl() {
    val userId = "3957cba3-e20f-47f6-bd74-f4c5beaf7d08"
    val emulatorUrl =
      "http://10.0.2.2:8000/storage/v1/object/public/avatars/$userId/avatar.jpg"
    assertEquals(
      publicAvatarUrl("http://localhost:8000", userId),
      normalizeAvatarUrl(emulatorUrl, "http://localhost:8000"),
    )
  }

  @Test
  fun normalizeAvatarUrl_preservesNonStorageAvatarUrls() {
    assertEquals(
      "https://cdn.example.com/pic.png",
      normalizeAvatarUrl("https://cdn.example.com/pic.png", "http://localhost:8000"),
    )
  }

  @Test
  fun normalizeAvatarUrl_returnsNullForBlank() {
    assertNull(normalizeAvatarUrl(null, "http://localhost:8000"))
    assertNull(normalizeAvatarUrl("  ", "http://localhost:8000"))
  }
}
