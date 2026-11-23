package org.example.chesshost.engine.packs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnginePackTest {

    private fun pack(
        id: String,
        name: String = id,
        version: String = "",
        backend: EngineBackend = EngineBackend.UCI_NATIVE,
        origin: EngineOrigin = EngineOrigin.BUILTIN,
        available: Boolean = true,
        files: List<EnginePackFile> = emptyList(),
    ) = EnginePack(
        id = id,
        name = name,
        version = version,
        backend = backend,
        origin = origin,
        description = "",
        elo = null,
        license = "",
        homepage = null,
        sourceUrl = null,
        tags = emptyList(),
        variants = emptyList(),
        binary = null,
        executable = null,
        uci = null,
        files = files,
        minAppVersion = 1,
        available = available,
        note = null,
    )

    @Test
    fun `title appends version only when present`() {
        assertEquals("Stockfish 17.1", pack("sf", "Stockfish", "17.1").title)
        assertEquals("Interne Engine", pack("internal", "Interne Engine").title)
    }

    @Test
    fun `merge overrides matching ids and appends new ones`() {
        val base = listOf(pack("alpha", "Alpha"), pack("beta", "Beta"))
        val extra = listOf(pack("alpha", "Alpha Plus"), pack("gamma", "Gamma"))

        val merged = mergeCatalogs(base, extra)

        assertEquals(listOf("alpha", "beta", "gamma"), merged.map { it.id })
        assertEquals("Alpha Plus", merged.first { it.id == "alpha" }.name)
        assertEquals("Beta", merged.first { it.id == "beta" }.name)
    }

    @Test
    fun `merge without remote keeps bundled order`() {
        val base = listOf(pack("a"), pack("b"))
        assertEquals(listOf("a", "b"), mergeCatalogs(base, emptyList()).map { it.id })
    }

    @Test
    fun `download size sums file sizes`() {
        val files = listOf(
            EnginePackFile("net.nnue", "https://example.org/net.nnue", null, 1_000),
            EnginePackFile("book.bin", "https://example.org/book.bin", "abc", 2_500),
        )
        assertEquals(3_500, pack("net", origin = EngineOrigin.DOWNLOAD, files = files).downloadBytes)
    }

    @Test
    fun `uci config keeps nnue net names`() {
        val config = UciConfig.fromJson(
            org.json.JSONObject("""{"threads":2,"hashMb":128,"evalFile":"nn-1111cefa1111.nnue"}"""),
        )
        assertEquals(2, config?.threads)
        assertEquals("nn-1111cefa1111.nnue", config?.evalFile)
        assertNull(config?.evalFileSmall)
    }

    @Test
    fun `download pack with nnue net is parsed from catalog json`() {
        val json = """
            {"engines":[{"id":"sf-net","name":"Stockfish · Netz","backend":"uci_native",
            "origin":"download","binary":"libstockfish.so",
            "config":{"evalFile":"nn-1111cefa1111.nnue"},
            "files":[{"name":"nn-1111cefa1111.nnue","url":"https://example.org/x.nnue",
            "sha256":"abc","size":74874478}]}]}
        """
        val pack = EnginePack.listFromJson(json).single()

        assertTrue(!pack.isBuiltin)
        assertEquals("nn-1111cefa1111.nnue", pack.uci?.evalFile)
        assertEquals(74_874_478L, pack.downloadBytes)
    }

    @Test
    fun `builtin and availability flags are derived from origin`() {
        assertTrue(pack("sf").isBuiltin)
        assertTrue(!pack("ext", origin = EngineOrigin.DOWNLOAD).isBuiltin)
        assertTrue(!pack("ext", origin = EngineOrigin.DOWNLOAD, available = false).available)
        assertNull(pack("sf").note)
    }
}
