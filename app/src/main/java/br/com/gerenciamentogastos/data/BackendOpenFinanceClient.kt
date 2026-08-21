package br.com.gerenciamentogastos.data

import android.os.Handler
import android.os.Looper
import br.com.gerenciamentogastos.BuildConfig
import br.com.gerenciamentogastos.model.FinanceTransaction
import br.com.gerenciamentogastos.model.TransactionStatus
import br.com.gerenciamentogastos.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

class BackendOpenFinanceClient(
    private val baseUrl: String = BuildConfig.BACKEND_BASE_URL.trimEnd('/')
) : AutoCloseable {
    data class LinkStatus(
        val accountsReady: Boolean,
        val transactionsReady: Boolean,
        val deletionPending: Boolean,
        val deleted: Boolean,
        val accountsError: String?,
        val transactionsError: String?,
        val lastWebhookAt: String?
    ) {
        val lastError: String?
            get() = transactionsError
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val closed = AtomicBoolean(false)

    fun isConfigured(): Boolean = runCatching {
        val url = URL(baseUrl)
        url.protocol == "https" && !baseUrl.contains("example.invalid") && url.userInfo == null
    }.getOrDefault(false)

    fun authenticate(
        accessCode: String,
        externalId: String,
        callback: (Result<String>) -> Unit
    ) = runAsync(callback) {
        val json = request(
            method = "POST",
            path = "/v1/auth/session",
            body = JSONObject()
                .put("accessCode", accessCode)
                .put("externalId", externalId)
        ) as JSONObject
        json.getString("token")
    }

    fun createWidgetSession(
        sessionToken: String,
        name: String,
        cpf: String,
        callback: (Result<String>) -> Unit
    ) = runAsync(callback) {
        val body = JSONObject()
            .put("name", name)
            .put("cpf", cpf)
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
            transactionsReady = json.optBoolean("transactionsReady"),
            deletionPending = json.optBoolean("deletionPending"),
            deleted = json.optBoolean("deleted"),
            accountsError = nullableString(json, "accountsError"),
            transactionsError = nullableString(json, "transactionsError"),
            lastWebhookAt = nullableString(json, "lastWebhookAt")
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
                val type = when (item.optString("type")) {
                    "INFLOW" -> TransactionType.INCOME
                    "OUTFLOW" -> TransactionType.EXPENSE
                    else -> continue
                }
                val amount = runCatching { BigDecimal(item.get("amount").toString()) }.getOrNull() ?: continue
                if (amount.signum() < 0) continue
                val currency = item.optString("currency").takeIf { it.matches(Regex("^[A-Z]{3}$")) } ?: "BRL"
                val description = item.optString("description").ifBlank { "Movimentação bancária" }
                val date = runCatching { LocalDate.parse(item.getString("value_date")) }.getOrNull() ?: continue
                val source = item.optString("source").takeIf { it.isNotBlank() } ?: "Open Finance"
                val transactionStatus = when (item.optString("status")) {
                    "PENDING" -> TransactionStatus.PENDING
                    "PROCESSED" -> TransactionStatus.PROCESSED
                    else -> TransactionStatus.UNKNOWN
                }

                add(
                    FinanceTransaction(
                        id = item.optString("id", "belvo-$index-$date"),
                        description = description,
                        amount = amount,
                        currency = currency,
                        type = type,
                        category = TransactionCategorizer.categorize(description, type),
                        date = date,
                        source = source,
                        status = transactionStatus
                    )
                )
            }
        }.distinctBy { it.id }.sortedByDescending { it.date }
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

    override fun close() {
        if (closed.compareAndSet(false, true)) {
            executor.shutdownNow()
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun nullableString(json: JSONObject, key: String): String? =
        json.optString(key).takeIf { it.isNotBlank() && it != "null" }

    private fun encodePath(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    private fun <T> runAsync(callback: (Result<T>) -> Unit, block: () -> T) {
        if (closed.get()) {
            callback(Result.failure(IllegalStateException("Cliente Open Finance encerrado.")))
            return
        }
        executor.execute {
            val result = runCatching(block)
            if (!closed.get()) mainHandler.post { if (!closed.get()) callback(result) }
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
            instanceFollowRedirects = false
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Cache-Control", "no-store")
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                outputStream.bufferedWriter(Charsets.UTF_8).use { writer -> writer.write(body.toString()) }
            }
        }

        try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader()?.use { reader -> reader.readText() }.orEmpty()
            if (code !in 200..299) {
                val message = runCatching {
                    (JSONTokener(text).nextValue() as JSONObject).optString("error")
                }.getOrNull().orEmpty()
                throw IllegalStateException(message.ifBlank { "Falha HTTP $code" })
            }
            if (text.isBlank()) return null
            return JSONTokener(text).nextValue()
        } finally {
            connection.disconnect()
        }
    }
}
