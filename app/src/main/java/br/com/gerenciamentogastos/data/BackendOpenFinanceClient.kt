package br.com.gerenciamentogastos.data

import android.os.Handler
import android.os.Looper
import br.com.gerenciamentogastos.BuildConfig
import br.com.gerenciamentogastos.model.FinanceTransaction
import br.com.gerenciamentogastos.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.Executors

class BackendOpenFinanceClient(
    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
) {
    data class LinkStatus(
        val accountsReady: Boolean,
        val transactionsReady: Boolean
    )

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun isConfigured(): Boolean = baseUrl.startsWith("https://") && !baseUrl.contains("example.invalid")

    fun authenticate(accessCode: String, callback: (Result<String>) -> Unit) = runAsync(callback) {
        val json = request(
            method = "POST",
            path = "/v1/auth/session",
            body = JSONObject().put("accessCode", accessCode)
        ) as JSONObject
        json.getString("token")
    }

    fun createWidgetSession(
        sessionToken: String,
        name: String,
        cpf: String,
        externalId: String,
        callback: (Result<String>) -> Unit
    ) = runAsync(callback) {
        val body = JSONObject()
            .put("name", name)
            .put("cpf", cpf)
            .put("externalId", externalId)
        val json = request(
            method = "POST",
            path = "/v1/open-finance/widget-session",
            bearer = sessionToken,
            body = body
        ) as JSONObject
        json.getString("widgetUrl")
    }

    fun linkStatus(
        sessionToken: String,
        linkId: String,
        callback: (Result<LinkStatus>) -> Unit
    ) = runAsync(callback) {
        val json = request(
            method = "GET",
            path = "/v1/open-finance/links/${encodePath(linkId)}/status",
            bearer = sessionToken
        ) as JSONObject
        LinkStatus(
            accountsReady = json.optBoolean("accountsReady"),
            transactionsReady = json.optBoolean("transactionsReady")
        )
    }

    fun transactions(
        sessionToken: String,
        linkId: String,
        callback: (Result<List<FinanceTransaction>>) -> Unit
    ) = runAsync(callback) {
        val payload = request(
            method = "GET",
            path = "/v1/open-finance/links/${encodePath(linkId)}/transactions",
            bearer = sessionToken
        )
        val items = when (payload) {
            is JSONArray -> payload
            is JSONObject -> payload.optJSONArray("results") ?: JSONArray()
            else -> JSONArray()
        }

        buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val providerType = item.optString("type")
                val type = if (providerType == "INFLOW") TransactionType.INCOME else TransactionType.EXPENSE
                val description = item.optString("description").ifBlank { "Movimentação bancária" }
                val date = runCatching { LocalDate.parse(item.getString("value_date")) }.getOrNull() ?: continue
                val institution = item.optJSONObject("institution")
                val source = institution?.optString("display_name")
                    ?.takeIf { it.isNotBlank() }
                    ?: institution?.optString("name")?.takeIf { it.isNotBlank() }
                    ?: "Open Finance"

                add(
                    FinanceTransaction(
                        id = item.optString("id", "belvo-$index-${date}"),
                        description = description,
                        amount = item.optDouble("amount", 0.0),
                        type = type,
                        category = TransactionCategorizer.categorize(description, type),
                        date = date,
                        source = source
                    )
                )
            }
        }.sortedByDescending { it.date }
    }

    fun deleteLink(
        sessionToken: String,
        linkId: String,
        callback: (Result<Unit>) -> Unit
    ) = runAsync(callback) {
        request(
            method = "DELETE",
            path = "/v1/open-finance/links/${encodePath(linkId)}",
            bearer = sessionToken
        )
        Unit
    }

    private fun encodePath(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun <T> runAsync(callback: (Result<T>) -> Unit, block: () -> T) {
        executor.execute {
            val result = runCatching(block)
            mainHandler.post { callback(result) }
        }
    }

    private fun request(
        method: String,
        path: String,
        bearer: String? = null,
        body: JSONObject? = null
    ): Any? {
        check(isConfigured()) { "Backend Open Finance ainda não configurado no build." }
        val connection = (URL(baseUrl + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            }
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText).orEmpty()
        if (code !in 200..299) {
            val message = runCatching {
                (JSONTokener(text).nextValue() as JSONObject).optString("error")
            }.getOrNull().orEmpty()
            throw IllegalStateException(message.ifBlank { "Falha HTTP $code" })
        }
        if (text.isBlank()) return null
        return JSONTokener(text).nextValue()
    }
}
