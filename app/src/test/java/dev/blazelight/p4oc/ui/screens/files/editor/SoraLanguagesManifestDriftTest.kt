package dev.blazelight.p4oc.ui.screens.files.editor

import dev.blazelight.p4oc.core.filetype.FileTypeClassifier
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guards against drift between [SoraLanguageRegistry]'s mapping tables and the
 * curated grammar manifest at `app/src/main/assets/textmate/languages.json`.
 *
 * Pure JVM: reads the manifest off disk via gradle's default unit-test
 * working directory (the `app/` module root). If the manifest moves, update
 * [MANIFEST_PATH] below.
 */
class SoraLanguagesManifestDriftTest {

    private companion object {
        // Path relative to the `app/` module root. The unit-test working
        // directory varies between AGP versions and IDE runners, so we probe
        // a couple of plausible roots before giving up.
        val MANIFEST_CANDIDATES = listOf(
            "src/main/assets/textmate/languages.json",
            "app/src/main/assets/textmate/languages.json",
        )
    }

    private fun resolveManifest(): File {
        for (candidate in MANIFEST_CANDIDATES) {
            val f = File(candidate)
            if (f.exists()) return f
        }
        // Walk upwards looking for the bundle — handles unexpected runner cwd.
        var dir: File? = File("").absoluteFile
        repeat(5) {
            val probe = File(dir, "app/src/main/assets/textmate/languages.json")
            if (probe.exists()) return probe
            dir = dir?.parentFile
        }
        error(
            "manifest not found under any of $MANIFEST_CANDIDATES (cwd=" +
                File("").absolutePath + ")",
        )
    }

    private fun loadManifestScopes(): Set<String> {
        val file = resolveManifest()
        assertTrue("manifest empty: ${file.absolutePath}", file.length() > 0)
        // org.json is Android-stubbed (not-mocked) under unit tests, so we
        // parse the small manifest with a regex. Keeps the test JVM-pure
        // without pulling in a JSON dependency just for one assertion.
        val text = file.readText()
        val pattern = Regex("\"scopeName\"\\s*:\\s*\"([^\"]+)\"")
        return pattern.findAll(text).map { it.groupValues[1] }.toSet()
    }

    @Test
    fun `every mapped scope is present in the shipped grammar manifest`() {
        val manifestScopes = loadManifestScopes()
        val mappedScopes = FileTypeClassifier.mappedTextMateScopes()

        val missing = mappedScopes - manifestScopes
        assertTrue(
            "scope(s) referenced by SoraLanguageRegistry but absent from " +
                "${MANIFEST_CANDIDATES.first()}: $missing",
            missing.isEmpty(),
        )
        // Sanity: all manifest scopes resolve to at least one filename in the
        // mapper. Skip-listed scopes that are intentionally not exposed via
        // filename can be added here if that ever becomes a thing.
        val unreferenced = manifestScopes - mappedScopes
        assertTrue(
            "scope(s) shipped in manifest but unreachable via " +
                "SoraLanguageRegistry: $unreferenced",
            unreferenced.isEmpty(),
        )
    }
}
