package com.jamal2367.uvsmobile

import com.jamal2367.uvsmobile.data.model.LibraryEntry
import com.jamal2367.uvsmobile.data.model.LibraryPage
import com.jamal2367.uvsmobile.data.repository.LibraryPageStore
import com.jamal2367.uvsmobile.data.repository.StoredLibraryPage
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LibraryPageStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true; explicitNulls = false }

    private fun store() = LibraryPageStore(folder.root.resolve("library_page.json"), json)

    private fun page(count: Int) = LibraryPage(
        success = true,
        count = count,
        total = count,
        files = (1..count).map { LibraryEntry(path = "/media/$it.mkv", filename = "$it.mkv") },
    )

    @Test
    fun `a kept page comes back as it went in`() {
        val store = store()
        store.write(StoredLibraryPage("sort=filename", "\"abc\"", page(3)))

        val read = store.read()
        assertEquals("sort=filename", read?.signature)
        assertEquals("\"abc\"", read?.etag)
        assertEquals(listOf("/media/1.mkv", "/media/2.mkv", "/media/3.mkv"), read?.page?.files?.map { it.path })
    }

    @Test
    fun `nothing kept reads as nothing`() {
        assertNull(store().read())
    }

    @Test
    fun `a file that is not a stored page reads as nothing`() {
        folder.root.resolve("library_page.json").writeText("{ not json")
        assertNull(store().read())
    }

    @Test
    fun `clearing leaves nothing behind`() {
        val store = store()
        store.write(StoredLibraryPage("sort=filename", "\"abc\"", page(2)))
        store.clear()

        assertNull(store.read())
        assertFalse(folder.root.resolve("library_page.json").exists())
    }

    @Test
    fun `a library too large to be worth the disk is not kept`() {
        val store = store()
        store.write(StoredLibraryPage("sort=filename", "\"abc\"", page(2)))
        store.write(StoredLibraryPage("sort=filename", "\"def\"", page(5_001)))

        // And the page it replaces goes with it, rather than being handed out
        // later as the answer to a question it is no longer the answer to.
        assertNull(store.read())
    }
}
