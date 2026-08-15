package br.com.gerenciamentogastos.ui

import android.content.Intent
import android.net.Uri
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import br.com.gerenciamentogastos.data.BackendOpenFinanceClient
import br.com.gerenciamentogastos.data.FinanceRepository
import br.com.gerenciamentogastos.model.Category
import br.com.gerenciamentogastos.model.FinancialSummary
import br.com.gerenciamentogastos.model.FinanceTransaction
import br.com.gerenciamentogastos.model.TransactionType
import java.text.NumberFormat
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private val currencyFormat = NumberFormat.getCurrencyInstance(Locale("pt", "BR"))
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM")

@Composable
fun FinanceApp(
    callbackUri: Uri? = null,
    onCallbackHandled: () -> Unit = {},
    repository: FinanceRepository = remember { FinanceRepository() },
    openFinanceClient: BackendOpenFinanceClient = remember { BackendOpenFinanceClient() }
) {
    val demoTransactions = remember { repository.transactions() }
    var liveTransactions by remember { mutableStateOf<List<FinanceTransaction>?>(null) }
    val transactions = liveTransactions ?: demoTransactions
    val summary = remember(transactions) { repository.summary(transactions) }
    var selectedTab by remember { mutableIntStateOf(0) }

    LaunchedEffect(callbackUri) {
        if (callbackUri != null) selectedTab = 2
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Text("⌂") },
                    label = { Text("Resumo") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Text("≡") },
                    label = { Text("Transações") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Text("▣") },
                    label = { Text("Contas") }
                )
            }
        }
    ) { padding ->
        when (selectedTab) {
            0 -> DashboardScreen(
                transactions = transactions,
                summary = summary,
                live = liveTransactions != null,
                modifier = Modifier.padding(padding)
            )
            1 -> TransactionsScreen(transactions, Modifier.padding(padding))
            else -> AccountsScreen(
                client = openFinanceClient,
                callbackUri = callbackUri,
                onCallbackHandled = onCallbackHandled,
                onTransactionsLoaded = { liveTransactions = it },
                onDisconnected = { liveTransactions = null },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun ScreenHeader(title: String, subtitle: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DashboardScreen(
    transactions: List<FinanceTransaction>,
    summary: FinancialSummary,
    live: Boolean,
    modifier: Modifier = Modifier
) {
    val expensesByCategory = remember(transactions) {
        transactions
            .filter { it.type == TransactionType.EXPENSE }
            .groupBy { it.category }
            .mapValues { (_, items) -> items.sumOf { it.amount } }
            .toList()
            .sortedByDescending { it.second }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            ScreenHeader("Gerenciamento de gastos", "Visão consolidada • Agosto de 2026")
            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = {},
                label = { Text(if (live) "Open Finance conectado" else "Dados de demonstração") }
            )
        }

        item { BalanceCard(summary) }

        item {
            Text("Gastos por categoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        items(expensesByCategory) { (category, amount) ->
            CategoryRow(category, amount, summary.expenses)
        }

        item {
            Text("Últimas movimentações", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        items(transactions.take(5)) { transaction ->
            TransactionRow(transaction)
        }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

@Composable
private fun BalanceCard(summary: FinancialSummary) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Saldo do período", style = MaterialTheme.typography.labelLarge)
            Text(
                currencyFormat.format(summary.balance),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryMetric("Entradas", summary.income)
                SummaryMetric("Saídas", summary.expenses)
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: Double) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(currencyFormat.format(value), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CategoryRow(category: Category, amount: Double, total: Double) {
    val progress = if (total > 0) (amount / total).toFloat().coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(category.symbol, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(category.label, modifier = Modifier.weight(1f))
            Text(currencyFormat.format(amount), fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TransactionsScreen(transactions: List<FinanceTransaction>, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, transactions) {
        if (query.isBlank()) transactions
        else transactions.filter {
            it.description.contains(query, ignoreCase = true) ||
                it.category.label.contains(query, ignoreCase = true) ||
                it.source.contains(query, ignoreCase = true)
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(16.dp))
        ScreenHeader("Transações", "Tudo que entrou e saiu das contas conectadas")
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Buscar transação") },
            placeholder = { Text("Ex.: mercado, Nubank, transporte") }
        )
        Spacer(Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            items(filtered, key = { it.id }) { transaction ->
                TransactionRow(transaction)
                HorizontalDivider()
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun TransactionRow(transaction: FinanceTransaction) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                transaction.category.symbol,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                transaction.description,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
            Text(
                "${transaction.category.label} • ${transaction.source} • ${transaction.date.format(dateFormat)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        val prefix = if (transaction.type == TransactionType.INCOME) "+" else "−"
        Text(
            "$prefix ${currencyFormat.format(transaction.amount)}",
            fontWeight = FontWeight.SemiBold,
            color = if (transaction.type == TransactionType.INCOME)
                MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun AccountsScreen(
    client: BackendOpenFinanceClient,
    callbackUri: Uri?,
    onCallbackHandled: () -> Unit,
    onTransactionsLoaded: (List<FinanceTransaction>) -> Unit,
    onDisconnected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("open_finance", 0) }
    val externalId = remember {
        prefs.getString("external_id", null) ?: UUID.randomUUID().toString().also {
            prefs.edit().putString("external_id", it).apply()
        }
    }

    var accessCode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf("") }
    var linkId by remember { mutableStateOf(prefs.getString("belvo_link_id", null)) }
    var widgetUrl by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf(if (linkId == null) "Nenhum banco conectado." else "Banco conectado. Sincronize para buscar os dados.") }
    var busy by remember { mutableStateOf(false) }

    fun persistLink(id: String) {
        linkId = id
        prefs.edit().putString("belvo_link_id", id).apply()
        status = "Consentimento concluído. Aguarde a preparação do histórico e toque em Sincronizar."
    }

    LaunchedEffect(callbackUri) {
        if (callbackUri?.scheme == "gerenciamentogastos") {
            when (callbackUri.host) {
                "success" -> callbackUri.getQueryParameter("link")?.let(::persistLink)
                "exit" -> status = "Conexão cancelada antes da conclusão."
                "error" -> status = "A instituição retornou um erro durante a conexão."
            }
            onCallbackHandled()
        }
    }

    fun authenticateThen(action: (String) -> Unit) {
        if (accessCode.isBlank()) {
            status = "Informe o código de acesso do backend."
            return
        }
        busy = true
        client.authenticate(accessCode) { authResult ->
            authResult.onSuccess { token -> action(token) }
                .onFailure {
                    busy = false
                    status = "Não foi possível autenticar: ${it.message ?: "erro desconhecido"}"
                }
        }
    }

    fun connect() {
        if (!client.isConfigured()) {
            status = "Defina BACKEND_BASE_URL no build para habilitar o Open Finance."
            return
        }
        if (name.trim().length < 3 || cpf.filter(Char::isDigit).length != 11) {
            status = "Preencha nome completo e CPF antes de conectar."
            return
        }
        authenticateThen { token ->
            client.createWidgetSession(token, name.trim(), cpf, externalId) { result ->
                busy = false
                result.onSuccess {
                    widgetUrl = it
                    status = "Abrindo ambiente seguro de consentimento."
                }.onFailure {
                    status = "Falha ao iniciar o consentimento: ${it.message ?: "erro desconhecido"}"
                }
            }
        }
    }

    fun sync() {
        val currentLink = linkId
        if (currentLink == null) {
            status = "Conecte uma instituição primeiro."
            return
        }
        authenticateThen { token ->
            client.linkStatus(token, currentLink) { statusResult ->
                statusResult.onSuccess { readiness ->
                    if (!readiness.transactionsReady) {
                        busy = false
                        status = "O histórico de transações ainda está sendo preparado pela instituição. Tente sincronizar novamente depois."
                    } else {
                        client.transactions(token, currentLink) { transactionsResult ->
                            busy = false
                            transactionsResult.onSuccess { transactions ->
                                onTransactionsLoaded(transactions)
                                status = "Sincronização concluída: ${transactions.size} movimentações carregadas."
                            }.onFailure {
                                status = "Falha ao carregar transações: ${it.message ?: "erro desconhecido"}"
                            }
                        }
                    }
                }.onFailure {
                    busy = false
                    status = "Falha ao consultar o estado da conexão: ${it.message ?: "erro desconhecido"}"
                }
            }
        }
    }

    fun disconnect() {
        val currentLink = linkId ?: return
        authenticateThen { token ->
            client.deleteLink(token, currentLink) { result ->
                busy = false
                result.onSuccess {
                    prefs.edit().remove("belvo_link_id").apply()
                    linkId = null
                    onDisconnected()
                    status = "Conexão removida."
                }.onFailure {
                    status = "Falha ao remover a conexão: ${it.message ?: "erro desconhecido"}"
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            ScreenHeader("Contas", "Conecte seus bancos pelo Open Finance")
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    if (client.isConfigured())
                        "A senha do seu banco nunca é solicitada por este app. O consentimento acontece no fluxo da instituição, intermediado pela Belvo."
                    else
                        "O código do Open Finance está pronto, mas o APK atual foi gerado sem BACKEND_BASE_URL. Configure o backend antes de tentar conectar.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        item {
            OutlinedTextField(
                value = accessCode,
                onValueChange = { accessCode = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Código de acesso do app") },
                visualTransformation = PasswordVisualTransformation()
            )
        }

        if (linkId == null) {
            item {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Nome completo") }
                )
            }
            item {
                OutlinedTextField(
                    value = cpf,
                    onValueChange = { cpf = it.take(14) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("CPF") }
                )
            }
            item {
                Button(onClick = ::connect, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(if (busy) "Conectando…" else "Conectar banco")
                }
            }
        } else {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Conexão Open Finance", fontWeight = FontWeight.SemiBold)
                        Text("Link: ${linkId?.take(8)}…", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = ::sync, enabled = !busy) { Text("Sincronizar") }
                            OutlinedButton(onClick = ::disconnect, enabled = !busy) { Text("Desconectar") }
                        }
                    }
                }
            }
        }

        item {
            Text(status, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        item { Spacer(Modifier.height(16.dp)) }
    }

    widgetUrl?.let { url ->
        BelvoWidgetDialog(
            url = url,
            onSuccess = { id ->
                widgetUrl = null
                persistLink(id)
            },
            onExit = { message ->
                widgetUrl = null
                status = message
            }
        )
    }
}

@Composable
private fun BelvoWidgetDialog(
    url: String,
    onSuccess: (String) -> Unit,
    onExit: (String) -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = { onExit("Conexão fechada antes da conclusão.") }) {
        Surface(
            modifier = Modifier.fillMaxWidth().height(640.dp),
            shape = RoundedCornerShape(20.dp)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val uri = request?.url ?: return false
                                if (uri.scheme == "gerenciamentogastos") {
                                    when (uri.host) {
                                        "success" -> uri.getQueryParameter("link")?.let(onSuccess)
                                        "exit" -> onExit("Conexão cancelada.")
                                        "error" -> onExit("Erro durante o consentimento Open Finance.")
                                    }
                                    return true
                                }
                                if (uri.scheme != "http" && uri.scheme != "https") {
                                    runCatching {
                                        val intent = if (uri.toString().startsWith("intent://")) {
                                            Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                                        } else {
                                            Intent(Intent.ACTION_VIEW, uri)
                                        }
                                        context.startActivity(intent)
                                    }
                                    return true
                                }
                                return false
                            }
                        }
                        loadUrl(url)
                    }
                },
                update = { webView ->
                    if (webView.url.isNullOrBlank()) webView.loadUrl(url)
                }
            )
        }
    }
}
