package cz.notecat.app.net

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.json.JSONObject

/** Výsledek zpracování poznámky backendem. */
data class ProcessResult(
    val transcript: String,
    val polished: String,
    /** STT uspěl, ale AI úprava selhala – přepis slouží jako dočasný text. */
    val polishFailed: Boolean = false,
)

sealed class BackendError(message: String) : Exception(message) {
    /** Dočasná chyba – má smysl opakovat (síť, 5xx, 429). */
    class Transient(message: String) : BackendError(message)
    /** Trvalá chyba – opakování bez zásahu uživatele nepomůže (4xx, špatný token). */
    class Permanent(message: String) : BackendError(message)
    /** Backend vrátil prázdný přepis. */
    class EmptyTranscript : BackendError("Přepis je prázdný – v nahrávce nebyla rozpoznána žádná řeč.")
}

/**
 * Tenký HTTP klient: pošle WAV jako raw body na POST {base}/v1/notes/process.
 * Idempotence je zajištěna hlavičkou X-Idempotency-Key – backend na opakovaný
 * požadavek vrátí uložený výsledek bez nového volání modelů.
 */
class BackendClient(
    private val baseUrl: String,
    private val token: String,
    client: OkHttpClient? = null,
) {
    private val http = client ?: OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .readTimeout(180, TimeUnit.SECONDS)
        .build()

    fun processNote(idempotencyKey: String, audioFile: File): ProcessResult {
        val request = Request.Builder()
            .url("$baseUrl/v1/notes/process")
            .header("Authorization", "Bearer $token")
            .header("X-Idempotency-Key", idempotencyKey)
            .post(audioFile.asRequestBody("audio/wav".toMediaType()))
            .build()

        val response = try {
            http.newCall(request).execute()
        } catch (e: IOException) {
            throw BackendError.Transient("Síť není dostupná (${e.message ?: "chyba spojení"})")
        }

        response.use { res ->
            val body = res.body?.string().orEmpty()
            when {
                res.isSuccessful -> {
                    val json = runCatching { JSONObject(body) }.getOrElse {
                        throw BackendError.Transient("Backend vrátil neplatnou odpověď.")
                    }
                    val transcript = json.optString("transcript")
                    val polished = json.optString("polished")
                    val polishFailed = json.optBoolean("polishFailed", false)
                    if (transcript.isBlank()) throw BackendError.EmptyTranscript()
                    return if (polishFailed) {
                        ProcessResult(transcript, "", polishFailed = true)
                    } else {
                        ProcessResult(transcript, polished.ifBlank { transcript })
                    }
                }
                res.code == 401 || res.code == 403 ->
                    throw BackendError.Permanent("Backend odmítl přístup – zkontroluj token v nastavení.")
                res.code == 429 || res.code >= 500 ->
                    throw BackendError.Transient("Backend je dočasně nedostupný (HTTP ${res.code}).")
                else ->
                    throw BackendError.Permanent("Backend vrátil chybu HTTP ${res.code}.")
            }
        }
    }
}
