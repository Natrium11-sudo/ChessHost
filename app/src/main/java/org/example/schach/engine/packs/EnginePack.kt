package org.example.schach.engine.packs

import org.json.JSONArray
import org.json.JSONObject

/**
 * Wie eine Engine ausgeführt wird. Neue Arten bekommen hier einen Eintrag und
 * einen passenden [org.example.schach.engine.EngineProvider] – der Rest der App
 * kennt nur noch die Pack-ID.
 */
enum class EngineBackend(val key: String, val label: String) {
    UCI_NATIVE("uci_native", "Nativ (UCI)"),
    INTERNAL("internal", "Intern"),
    EXTERNAL_UCI("external_uci", "Extern (UCI)"),
    ONNX("onnx", "Neuronales Netz"),
    /** Sprachmodell über eine Chat-API – kein lokaler Prozess. */
    LLM("llm", "LLM (API)"),
    ;

    companion object {
        fun fromKey(value: String?): EngineBackend? =
            entries.firstOrNull { it.key.equals(value, ignoreCase = true) }
                ?: entries.firstOrNull { it.name.equals(value, ignoreCase = true) }
    }
}

/**
 * Woher eine Engine kommt.
 *
 * - [BUILTIN] ist immer aktiv (Notlösung, damit die App nie ohne Gegner dasteht).
 * - [OPTIONAL] liegt als Binary in der APK, wird aber erst aktiviert, wenn der
 *   Nutzer das im Store tut – ab Werk ist keine Engine aktiv.
 * - [DOWNLOAD] lädt Dateien (Netze) nach; Netze sind Daten, deshalb greift
 *   Androids Exec-Sperre hier nicht.
 */
enum class EngineOrigin(val key: String) {
    BUILTIN("builtin"),
    OPTIONAL("optional"),
    DOWNLOAD("download"),
    ;

    companion object {
        fun fromKey(value: String?): EngineOrigin =
            entries.firstOrNull { it.key.equals(value, ignoreCase = true) } ?: BUILTIN
    }
}

/**
 * UCI-Parameter, mit denen ein Pack gestartet wird.
 *
 * [evalFile] und [evalFileSmall] bezeichnen NNUE-Netze: im Katalog stehen dort
 * Dateinamen aus dem Paket, beim Engine-Start werden daraus absolute Pfade.
 * Großes Netz → `EvalFile`, kleines Netz → `EvalFileSmall`; Stockfish lehnt ein
 * Netz der falschen Architektur ab und beendet sich.
 */
data class UciConfig(
    val threads: Int = 1,
    val hashMb: Int = 32,
    val evalFile: String? = null,
    val evalFileSmall: String? = null,
) {

    companion object {
        fun fromJson(json: JSONObject?): UciConfig? {
            if (json == null) return null
            return UciConfig(
                threads = json.optInt("threads", 1).coerceIn(1, 32),
                hashMb = json.optInt("hashMb", 32).coerceIn(0, 8192),
                evalFile = json.optString("evalFile").trim().takeIf { it.isNotEmpty() },
                evalFileSmall = json.optString("evalFileSmall").trim().takeIf { it.isNotEmpty() },
            )
        }
    }
}

/** Eine Datei eines Download-Packs. */
data class EnginePackFile(
    val name: String,
    val url: String,
    val sha256: String?,
    val sizeBytes: Long,
) {
    companion object {
        fun fromJson(json: JSONObject): EnginePackFile? {
            val name = json.optString("name").trim()
            val url = json.optString("url").trim()
            if (name.isEmpty() || url.isEmpty()) return null
            val hash = json.optString("sha256").trim().takeIf { it.isNotEmpty() }
            return EnginePackFile(name, url, hash, json.optLong("size", 0L))
        }
    }
}

/**
 * Ein Eintrag des Engine-Katalogs. Der Katalog liegt als JSON in den Assets und
 * kann zur Laufzeit durch einen zweiten Katalog aus dem Netz ergänzt werden
 * (siehe [EngineCatalog]).
 */
data class EnginePack(
    val id: String,
    val name: String,
    val version: String,
    val backend: EngineBackend,
    val origin: EngineOrigin,
    val description: String,
    val elo: Int?,
    val license: String,
    val homepage: String?,
    val sourceUrl: String?,
    val tags: List<String>,
    /** Regelwerke, die diese Engine spielen kann; leer = alle. */
    val variants: List<String>,
    val binary: String?,
    val executable: String?,
    val uci: UciConfig?,
    val files: List<EnginePackFile>,
    val minAppVersion: Int,
    val available: Boolean,
    val note: String?,
) {
    /** Anzeigename inklusive Version, z. B. "Stockfish 17.1". */
    val title: String get() = if (version.isBlank()) name else "$name $version"

    /** Immer aktiv, ohne Zutun des Nutzers. */
    val isBuiltin: Boolean get() = origin == EngineOrigin.BUILTIN

    /** Ob die Engine ein Regelwerk spielen kann (leere Liste = alle). */
    fun supportsVariant(variantId: String): Boolean =
        variants.isEmpty() || variants.any { it.equals(variantId, ignoreCase = true) }


    /** Liegt in der APK, muss aber erst aktiviert werden. */
    val isOptional: Boolean get() = origin == EngineOrigin.OPTIONAL

    /** Binary ist in der APK enthalten (aktiviert oder nicht). */
    val isBundled: Boolean get() = origin != EngineOrigin.DOWNLOAD

    val downloadBytes: Long get() = files.sumOf { it.sizeBytes }

    companion object {
        fun fromJson(json: JSONObject): EnginePack? {
            val id = json.optString("id").trim()
            val name = json.optString("name").trim()
            if (id.isEmpty() || name.isEmpty()) return null

            val backend = EngineBackend.fromKey(json.optString("backend", "uci_native")) ?: return null
            val origin = EngineOrigin.fromKey(json.optString("origin", null))
            val files = json.optJSONArray("files").toList()
                .mapNotNull { EnginePackFile.fromJson(it) }

            return EnginePack(
                id = id,
                name = name,
                version = json.optString("version").trim(),
                backend = backend,
                origin = origin,
                description = json.optString("description").trim(),
                elo = json.optInt("elo", 0).takeIf { it > 0 },
                license = json.optString("license").trim(),
                homepage = json.optString("homepage").trim().takeIf { it.isNotEmpty() },
                sourceUrl = json.optString("sourceUrl").trim().takeIf { it.isNotEmpty() },
                tags = json.optJSONArray("tags").stringList(),
                variants = json.optJSONArray("variants").stringList(),
                binary = json.optString("binary").trim().takeIf { it.isNotEmpty() },
                executable = json.optString("executable").trim().takeIf { it.isNotEmpty() },
                uci = UciConfig.fromJson(json.optJSONObject("config")),
                files = files,
                minAppVersion = json.optInt("minAppVersion", 1),
                available = json.optBoolean("available", true),
                note = json.optString("note").trim().takeIf { it.isNotEmpty() },
            )
        }

        /**
         * Liest entweder `{ "engines": [...] }` oder direkt ein Array.
         * Unbekannte oder kaputte Einträge werden übersprungen.
         */
        fun listFromJson(text: String): List<EnginePack> {
            val trimmed = text.trim()
            if (trimmed.isEmpty()) return emptyList()
            return if (trimmed.startsWith("[")) {
                JSONArray(trimmed).toList().mapNotNull { fromJson(it) }
            } else {
                val root = JSONObject(trimmed)
                root.optJSONArray("engines").toList().mapNotNull { fromJson(it) }
            }
        }

        private fun JSONArray?.toList(): List<JSONObject> {
            if (this == null) return emptyList()
            return buildList {
                for (index in 0 until length()) {
                    optJSONObject(index)?.let { add(it) }
                }
            }
        }

        private fun JSONArray?.stringList(): List<String> {
            if (this == null) return emptyList()
            return buildList {
                for (index in 0 until length()) {
                    optString(index).trim().takeIf { value -> value.isNotEmpty() }?.let { add(it) }
                }
            }
        }
    }
}

/**
 * Ergänzt den eingebauten Katalog um Einträge aus einem zweiten Katalog.
 * Gleiche IDs werden überschrieben, neue IDs angehängt.
 */
fun mergeCatalogs(base: List<EnginePack>, extra: List<EnginePack>): List<EnginePack> {
    val merged = LinkedHashMap<String, EnginePack>()
    base.forEach { merged[it.id] = it }
    extra.forEach { merged[it.id] = it }
    return merged.values.toList()
}
