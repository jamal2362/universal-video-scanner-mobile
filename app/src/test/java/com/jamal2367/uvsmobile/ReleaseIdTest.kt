package com.jamal2367.uvsmobile

import com.jamal2367.uvsmobile.data.remote.ReleaseId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Telling two releases of this app apart.
 *
 * The release workflow publishes a build per commit as `build-<run number>`,
 * titled `UVS <version> - build <run number>`, and the version name stands
 * still for dozens of them. So neither number alone answers the question the
 * update check asks: the version says whether something changed, and the build
 * number is the only thing separating two releases that carry the same one.
 *
 * Getting it wrong is either an app that never mentions an update, or one that
 * announces the same release on every launch.
 */
class ReleaseIdTest {

    @Test
    fun `reads the version off a release title and the build off its tag`() {
        val release = ReleaseId.of("UVS 1.1.0 - build 31", "build-31")

        assertEquals(listOf(1, 1, 0), release.version)
        assertEquals(31, release.build)
    }

    /** A debug build carries a suffix the version is still readable through. */
    @Test
    fun `reads the version off a suffixed version name`() {
        assertEquals(listOf(1, 1, 0), ReleaseId.of("1.1.0-debug").version)
    }

    /** The tag on its own is a build number, never a version numbered 31. */
    @Test
    fun `does not mistake a build number for a version`() {
        val release = ReleaseId.of("build-31")

        assertTrue(release.version.isEmpty())
        assertEquals(31, release.build)
    }

    @Test
    fun `a newer version is newer whatever the build numbers say`() {
        val installed = ReleaseId.of("1.1.0", "build-90")
        val published = ReleaseId.of("UVS 1.2.0 - build 3", "build-3")

        assertTrue(installed.isBehind(published))
        assertFalse(published.isBehind(installed))
    }

    @Test
    fun `an older version is never offered as an update`() {
        val installed = ReleaseId.of("1.2.0", "build-3")
        val published = ReleaseId.of("UVS 1.1.0 - build 90", "build-90")

        assertFalse(installed.isBehind(published))
    }

    /** The case this project is actually in: the version rarely moves. */
    @Test
    fun `on the same version the build number decides`() {
        val installed = ReleaseId.of("1.1.0", "build-29")
        val published = ReleaseId.of("UVS 1.1.0 - build 31", "build-31")

        assertTrue(installed.isBehind(published))
        assertFalse(published.isBehind(installed))
    }

    @Test
    fun `the release it is already running is not an update`() {
        val installed = ReleaseId.of("1.1.0", "build-31")
        val published = ReleaseId.of("UVS 1.1.0 - build 31", "build-31")

        assertFalse(installed.isBehind(published))
    }

    /**
     * A build with no run behind it - one assembled from a checkout - cannot
     * be one build behind anything, so it is only ever told about a version.
     */
    @Test
    fun `a build with no number is not told it is behind by a build`() {
        val local = ReleaseId.of("1.1.0", "build-0")

        assertFalse(local.isBehind(ReleaseId.of("UVS 1.1.0 - build 31", "build-31")))
        assertTrue(local.isBehind(ReleaseId.of("UVS 1.2.0 - build 31", "build-31")))
    }

    /** `1.2` and `1.2.0` are the same release written two ways. */
    @Test
    fun `a missing version part counts as zero`() {
        assertEquals(0, ReleaseId.compareVersions(listOf(1, 2), listOf(1, 2, 0)))
        assertTrue(ReleaseId.compareVersions(listOf(1, 2, 1), listOf(1, 2)) > 0)
    }

    /**
     * A release nobody could read a version out of is no answer either way,
     * so the build number is left to settle it.
     */
    @Test
    fun `an unreadable version leaves the decision to the build number`() {
        assertEquals(0, ReleaseId.compareVersions(emptyList(), listOf(1, 2, 0)))

        val installed = ReleaseId.of("1.1.0", "build-29")
        assertTrue(installed.isBehind(ReleaseId.of("Nightly", "build-31")))
    }
}
