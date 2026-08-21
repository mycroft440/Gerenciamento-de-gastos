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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.com.gerenciamentogastos.data.BackendOpenFinanceClient
import br.com.gerenciamentogastos.data.FinanceRepository
import br.com.gerenciamentogastos.data.OpenFinanceLocalStore
import br.com.gerenciamentogastos.model.Category
import br.com.gerenciamentogastos.model.FinancialSummary
import br.com.gerenciamentogastos.model.FinanceTransaction
import br.com.gerenciamentogastos.model.TransactionStatus
import br.com.gerenciamentogastos.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

private val ptBr = Locale.forLanguageTag("pt-BR")
private val brlFormat = NumberFormat.getCurrencyInstance(ptBr)
private val dateFormat = DateTimeFormatter.ofPattern("dd/MM")

@Composable
fun FinanceApp(
    callbackUri: Uri? = null,
    onCallbackHandled: () -> Unit = {},
    repository: FinanceRepository = remember { FinanceRepository() },
    openFinanceClient: BackendOpenFinanceClient = remember { BackendOpenFinanceClient() }
) {
    val context = LocalContext.current
    val localStore = remember { OpenFinanceLocalStore(context.applicationContext) }
    val demoTransactions = remember { repository.transactions() }
    var connectedLinks by remember { mutableStateOf(localStore.linkIds()) }
    var liveTransactions by remember { mutableStateOf<List<FinanceTransaction>>(emptyList()) }
    var hasLoadedLiveData by remember { mutableStateOf(false) }
    var selectedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedTab by remember { mutableIntStateOf(0) }

    val usingDemo = connectedLinks.isEmpty()
    val transactions = if (usingDemo) demoTransactions else liveTransactions
    val monthTransactions = remember(transactions, selectedMonth) {
        transactions.filter { YearMonth.from(it.date) == selectedMonth }
    }
    val summary = remember(monthTransactions) { repository.summary(monthTransactions) }

    DisposableEffect(openFinanceClient) {
        onDispose { openFinanceClient.close() }
    }

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
                transactions = monthTransactions,
                summary = summary,
                selectedMonth = selectedMonth,
                onPreviousMonth = { selectedMonth = selectedMonth.minusMonths(1) },
                onNextMonth = { if (selectedMonth < YearMonth.now()) selectedMonth = selectedMonth.plusMonths(1) },
                usingDemo = usingDemo,
                connectionCount = connectedLinks.size,
                hasLoadedLiveData = hasLoadedLiveData,
                modifier = Modifier.padding(padding)
            )

            1 -> TransactionsScreen(
                transactions = transactions,
                waitingForLoad = !usingDemo && !hasLoadedLiveData,
                modifier = Modifier.padding(padding)
            )

            else -> AccountsScreen(
                client = openFinanceClient,
                localStore = localStore,
                connectedLinkIds = connectedLinks,
                callbackUri = callbackUri,
                onCallbackHandled = onCallbackHandled,
                onLinksChanged = { links ->
                    if (links != connectedLinks) {
                        connectedLinks = links
                        liveTransactions = emptyList()
                        hasLoadedLiveData = false
                    }
                },
                onTransactionsLoaded = { loaded ->
                    liveTransactions = loaded
                    hasLoadedLiveData = true
                },
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
    selectedMonth: YearMonth,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    usingDemo: Boolean,
    connectionCount: Int,
    hasLoadedLiveData: Boolean,
    modifier: Modifier = Modifier
) {
    val expensesByCategory = remember(transactions) {
        transactions
            .filter {
                it.type == TransactionType.EXPENSE &&
                    it.currency == "BRL" &&
                    it.status != TransactionStatus.PENDING
            }
            .groupBy { it.category }
            .mapValues { (_, items) ->
                items.fold(BigDecimal.ZERO) { total, item -> total.add(item.amount) }
            }
            .toList()
            .sortedByDescending { it.second }
    }
    val monthName = selectedMonth.month.getDisplayName(TextStyle.FULL, ptBr)
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(ptBr) else it.toString() }
    val currentMonth = YearMonth.now()

    LazyColumn(
        modifier = modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(16.dp))
            ScreenHeader("Gerenciamento de gastos", "Visão consolidada • $monthName de ${selectedMonth.year}")
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onPreviousMonth) { Text("‹ Mês anterior") }
                OutlinedButton(onClick = onNextMonth, enabled = selectedMonth < currentMonth) { Text("Próximo ›") }
            }
            Spacer(Modifier.height(8.dp))
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        when {
                            usingDemo -> "Dados de demonstração"
                            !hasLoadedLiveData -> "$connectionCount conexão(ões) • carregamento necessário"
                            else -> "Open Finance • $connectionCount conexão(ões)"
                        }
                    )
                }
            )
        }

        item { BalanceCard(summary) }

        if (summary.pendingTransactions > 0) {
            item { PendingTransactionsCard(summary) }
        }

        if (summary.excludedForeignTransactions > 0) {
            item {
                Text(
                    "${summary.excludedForeignTransactions} movimentação(ões) sem valor convertido para BRL não entram nos totais.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        item {
            Text("Gastos processados por categoria", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        if (expensesByCategory.isEmpty()) {
            item { Text("Nenhum gasto processado em BRL neste mês.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        } else {
            items(expensesByCategory) { (category, amount) ->
                CategoryRow(category, amount, summary.expenses)
            }
        }

        item {
            Text("Últimas movimentações do mês", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }

        if (transactions.isEmpty()) {
            item {
                Text(
                    if (!usingDemo && !hasLoadedLiveData) "Atualize o painel para carregar as movimentações já disponíveis."
                    else "Nenhuma movimentação neste mês.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(transactions.take(5), key = { it.id }) { transaction -> TransactionRow(transaction) }
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
            Text("Saldo líquido processado", style = MaterialTheme.typography.labelLarge)
            Text(brlFormat.format(summary.balance), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryMetric("Entradas", summary.income)
                SummaryMetric("Saídas", summary.expenses)
            }
        }
    }
}

@Composable
private fun PendingTransactionsCard(summary: FinancialSummary) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Movimentações pendentes", fontWeight = FontWeight.SemiBold)
            Text(
                "${summary.pendingTransactions} movimentação(ões) ainda não processada(s) pela instituição e, por isso, fora dos totais acima.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                SummaryMetric("Entradas pendentes", summary.pendingIncome)
                SummaryMetric("Saídas pendentes", summary.pendingExpenses)
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: BigDecimal) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium)
        Text(brlFormat.format(value), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun CategoryRow(category: Category, amount: BigDecimal, total: BigDecimal) {
    val progress = if (total.signum() > 0) {
        amount.divide(total, 6, RoundingMode.HALF_UP).toFloat().coerceIn(0f, 1f)
    } else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Text(category.symbol, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(10.dp))
            Text(category.label, modifier = Modifier.weight(1f))
            Text(brlFormat.format(amount), fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun TransactionsScreen(
    transactions: List<FinanceTransaction>,
    waitingForLoad: Boolean,
    modifier: Modifier = Modifier
) {
    var query by remember { mutableStateOf("") }
    var typeFilter by remember { mutableStateOf<TransactionType?>(null) }
    val filtered = remember(query, typeFilter, transactions) {
        transactions.filter { transaction ->
            val textMatches = query.isBlank() ||
                transaction.description.contains(query, ignoreCase = true) ||
                transaction.category.label.contains(query, ignoreCase = true) ||
                transaction.source.contains(query, ignoreCase = true)
            val typeMatches = typeFilter == null || transaction.type == typeFilter
            textMatches && typeMatches
        }
    }

    Column(modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        Spacer(Modifier.height(16.dp))
        ScreenHeader("Transações", "Histórico disponível das contas conectadas")
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Buscar transação") },
            placeholder = { Text("Ex.: mercado, banco, transporte") }
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = { typeFilter = null }, label = { Text(if (typeFilter == null) "✓ Todas" else "Todas") })
            AssistChip(
                onClick = { typeFilter = TransactionType.INCOME },
                label = { Text(if (typeFilter == TransactionType.INCOME) "✓ Entradas" else "Entradas") }
            )
            AssistChip(
                onClick = { typeFilter = TransactionType.EXPENSE },
                label = { Text(if (typeFilter == TransactionType.EXPENSE) "✓ Saídas" else "Saídas") }
            )
        }
        Spacer(Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Text(
                if (waitingForLoad) "Há contas conectadas, mas os dados ainda não foram carregados nesta abertura do app."
                else "Nenhuma transação encontrada.",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                items(filtered, key = { it.id }) { transaction ->
                    TransactionRow(transaction)
                    HorizontalDivider()
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
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
            Text(transaction.description, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            val pending = if (transaction.status == TransactionStatus.PENDING) " • pendente" else ""
            Text(
                "${transaction.category.label} • ${transaction.source} • ${transaction.date.format(dateFormat)}$pending",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.width(8.dp))
        val prefix = if (transaction.type == TransactionType.INCOME) "+" else "−"
        Text(
            "$prefix ${formatMoney(transaction.amount, transaction.currency)}",
            fontWeight = FontWeight.SemiBold,
            color = if (transaction.type == TransactionType.INCOME) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
        )
    }
}

private fun formatMoney(amount: BigDecimal, currencyCode: String): String {
    return runCatching {
        NumberFormat.getCurrencyInstance(ptBr).apply { currency = Currency.getInstance(currencyCode) }.format(amount)
    }.getOrElse { "$currencyCode ${amount.toPlainString()}" }
}

private fun institutionLabel(raw: String?): String? {
    val normalized = raw?.trim()?.takeIf { it.matches(Regex("^[a-z_]{1,80}$")) } ?: return null
    val withoutSuffix = normalized
        .removeSuffix("_br_retail")
        .removeSuffix("_br_business")
        .removeSuffix("_br")
    return withoutSuffix
        .split('_')
        .filter { it.isNotBlank() }
        .joinToString(" ") { part -> part.replaceFirstChar { it.titlecase(ptBr) } }
        .takeIf { it.isNotBlank() }
}

@Composable
private fun AccountsScreen(
    client: BackendOpenFinanceClient,
    localStore: OpenFinanceLocalStore,
    connectedLinkIds: Set<String>,
    callbackUri: Uri?,
    onCallbackHandled: () -> Unit,
    onLinksChanged: (Set<String>) -> Unit,
    onTransactionsLoaded: (List<FinanceTransaction>) -> Unit,
    modifier: Modifier = Modifier
) {
    var accessCode by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var cpf by remember { mutableStateOf("") }
    var pendingLinkId by remember { mutableStateOf<String?>(null) }
    var widgetUrl by remember { mutableStateOf<String?>(null) }
    var lastProviderEvent by remember { mutableStateOf<String?>(null) }
    var status by remember {
        mutableStateOf(
            if (connectedLinkIds.isEmpty())
                "Nenhuma conexão salva neste aparelho. Use Atualizar painel para recuperar conexões existentes."
            else
                "${connectedLinkIds.size} conexão(ões) salva(s). Atualize o painel para carregar os dados disponíveis."
        )
    }
    var busy by remember { mutableStateOf(false) }

    fun authenticateThen(action: (String) -> Unit) {
        if (accessCode.isBlank()) {
            busy = false
            status = "Informe o código de acesso do backend."
            return
        }
        busy = true
        client.authenticate(accessCode) { authResult ->
            authResult.onSuccess(action).onFailure {
                busy = false
                status = "Não foi possível autenticar: ${it.message ?: "erro desconhecido"}"
            }
        }
    }

    fun persistVerifiedLink(id: String, institution: String?) {
        val updated = localStore.addLink(id, institutionLabel(institution))
        pendingLinkId = null
        onLinksChanged(updated)
        status = "Consentimento confirmado. Aguarde a preparação do histórico e use Atualizar painel."
    }

    fun confirmLink(candidateId: String) {
        if (candidateId in connectedLinkIds) {
            pendingLinkId = null
            status = "Esta conexão já está salva."
            return
        }
        pendingLinkId = candidateId
        authenticateThen { token ->
            client.linkStatus(token, candidateId) { result ->
                busy = false
                result.onSuccess { readiness ->
                    if (readiness.deleted) {
                        pendingLinkId = null
                        status = "A conexão informada já foi removida."
                    } else {
                        persistVerifiedLink(candidateId, readiness.institution)
                    }
                }.onFailure {
                    status = "O retorno do banco não pôde ser validado. A conexão não foi salva."
                }
            }
        }
    }

    LaunchedEffect(callbackUri) {
        if (callbackUri?.scheme == "gerenciamentogastos") {
            when (callbackUri.host) {
                "success" -> {
                    val candidate = callbackUri.getQueryParameter("link")
                    if (candidate.isNullOrBlank()) status = "Retorno do banco sem identificador de conexão."
                    else confirmLink(candidate)
                }

                "exit" -> status = "Conexão cancelada antes da conclusão."
                "error" -> status = "A instituição retornou um erro durante a conexão."
            }
            onCallbackHandled()
        }
    }

    fun connectAnother() {
        if (!client.isConfigured()) {
            status = "Defina BACKEND_BASE_URL no build para habilitar o Open Finance."
            return
        }
        if (name.trim().length < 3 || cpf.filter(Char::isDigit).length != 11) {
            status = "Preencha nome completo e CPF antes de conectar."
            return
        }
        authenticateThen { token ->
            client.createWidgetSession(token, name.trim(), cpf) { result ->
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

    fun refreshDashboard() {
        if (!client.isConfigured()) {
            status = "Defina BACKEND_BASE_URL no build para habilitar o Open Finance."
            return
        }
        authenticateThen { token ->
            val collected = mutableListOf<FinanceTransaction>()
            val notes = mutableListOf<String>()
            var loadedAny = false
            var latestWebhook: String? = null

            fun rememberWebhook(value: String?) {
                if (value != null && (latestWebhook == null || value > latestWebhook!!)) latestWebhook = value
            }

            fun finish() {
                busy = false
                val currentLinks = localStore.linkIds()
                if (currentLinks != connectedLinkIds) onLinksChanged(currentLinks)
                if (loadedAny) {
                    onTransactionsLoaded(collected.distinctBy { it.id }.sortedByDescending { it.date })
                }
                lastProviderEvent = latestWebhook
                status = when {
                    loadedAny && notes.isEmpty() -> "Painel atualizado: ${collected.distinctBy { it.id }.size} movimentações consolidadas."
                    loadedAny -> "Painel atualizado parcialmente. ${notes.joinToString(" ")}"
                    notes.isNotEmpty() -> notes.joinToString(" ")
                    currentLinks.isEmpty() -> "Nenhuma conexão Open Finance foi encontrada para este perfil pessoal."
                    else -> "Nenhum dado disponível foi carregado."
                }
            }

            fun loadIndex(ids: List<String>, index: Int) {
                if (index >= ids.size) {
                    finish()
                    return
                }
                val id = ids[index]
                client.linkStatus(token, id) { statusResult ->
                    statusResult.onSuccess { readiness ->
                        rememberWebhook(readiness.lastWebhookAt)
                        institutionLabel(readiness.institution)?.let { localStore.setLinkLabel(id, it) }
                        when {
                            readiness.deleted -> {
                                localStore.removeLink(id)
                                notes += "Uma conexão removida pela instituição foi apagada localmente."
                                loadIndex(ids, index + 1)
                            }

                            readiness.deletionPending -> {
                                notes += "Uma conexão ainda aguarda confirmação de remoção."
                                loadIndex(ids, index + 1)
                            }

                            readiness.transactionsError != null -> {
                                notes += "Uma instituição informou erro ao preparar transações."
                                loadIndex(ids, index + 1)
                            }

                            !readiness.transactionsReady -> {
                                notes += "O histórico de uma instituição ainda está sendo preparado."
                                loadIndex(ids, index + 1)
                            }

                            else -> client.transactions(token, id) { transactionsResult ->
                                transactionsResult.onSuccess {
                                    loadedAny = true
                                    collected += it
                                }.onFailure {
                                    notes += "Falha ao carregar uma das conexões."
                                }
                                loadIndex(ids, index + 1)
                            }
                        }
                    }.onFailure {
                        notes += "Não foi possível verificar uma das conexões."
                        loadIndex(ids, index + 1)
                    }
                }
            }

            client.links(token) { linksResult ->
                linksResult.onSuccess { discovered ->
                    val recovered = localStore.replaceLinks(
                        discovered.map { it.id to institutionLabel(it.institution) }
                    )
                    if (recovered != connectedLinkIds) onLinksChanged(recovered)
                    if (recovered.isEmpty()) {
                        busy = false
                        lastProviderEvent = null
                        status = "Nenhuma conexão Open Finance foi encontrada para este perfil pessoal."
                    } else {
                        loadIndex(recovered.sorted(), 0)
                    }
                }.onFailure {
                    val localIds = localStore.linkIds().sorted()
                    if (localIds.isEmpty()) {
                        busy = false
                        status = "Não foi possível recuperar as conexões do servidor. Tente novamente."
                    } else {
                        notes += "Não foi possível reconciliar a lista de conexões; usando as referências locais desta instalação."
                        loadIndex(localIds, 0)
                    }
                }
            }
        }
    }

    fun requestDisconnect(id: String) {
        authenticateThen { token ->
            client.deleteLink(token, id) { result ->
                busy = false
                result.onSuccess {
                    status = "Remoção solicitada. A conexão permanece salva até a Belvo confirmar a exclusão; use Atualizar painel para verificar."
                }.onFailure {
                    status = "Falha ao solicitar a remoção: ${it.message ?: "erro desconhecido"}"
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
            ScreenHeader("Contas", "Conecte e consolide vários bancos pelo Open Finance")
        }

        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    if (client.isConfigured())
                        "A senha do seu banco nunca é solicitada por este app. O consentimento acontece no fluxo da instituição, intermediado pela Belvo. A carga inicial pode abranger até 365 dias."
                    else
                        "O código do Open Finance está pronto, mas o APK atual foi gerado sem BACKEND_BASE_URL. Configure o backend antes de tentar conectar.",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        if (client.isConfigured()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Como a atualização funciona", fontWeight = FontWeight.SemiBold)
                        Text(
                            "Atualizar painel recupera as conexões deste perfil pessoal e lê os dados que o provedor Open Finance já disponibilizou. Isso não força uma coleta nova no banco e não deve ser interpretado como tempo real.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        lastProviderEvent?.let {
                            Text(
                                "Último evento recebido do provedor: $it",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        item {
            OutlinedTextField(
                value = accessCode,
                onValueChange = { accessCode = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Código de acesso do app") },
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )
        }

        if (connectedLinkIds.isNotEmpty()) {
            item {
                Text("Conexões salvas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            items(connectedLinkIds.sorted()) { id ->
                val savedLabel = localStore.linkLabel(id)
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(savedLabel ?: "Instituição ainda não identificada", fontWeight = FontWeight.SemiBold)
                            Text("ID ${id.take(8)}…", style = MaterialTheme.typography.bodySmall)
                            if (savedLabel == null) {
                                Text(
                                    "Atualize o painel para identificar esta conexão antes de removê-la.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        OutlinedButton(
                            onClick = { requestDisconnect(id) },
                            enabled = !busy && savedLabel != null
                        ) { Text("Remover") }
                    }
                }
            }
        }

        if (client.isConfigured()) {
            item {
                Button(onClick = ::refreshDashboard, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        if (busy) "Atualizando…"
                        else if (connectedLinkIds.isEmpty()) "Recuperar conexões e atualizar"
                        else "Atualizar painel"
                    )
                }
            }
        }

        if (pendingLinkId != null) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Retorno bancário pendente", fontWeight = FontWeight.SemiBold)
                        Text("Confirme o código de acesso para validar que esta conexão realmente pertence a este app.")
                        Button(
                            onClick = { pendingLinkId?.let(::confirmLink) },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Confirmar conexão") }
                    }
                }
            }
        }

        item {
            Text(
                if (connectedLinkIds.isEmpty()) "Conectar banco" else "Adicionar outro banco",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
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
                onValueChange = { cpf = it.filter(Char::isDigit).take(11) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("CPF") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
        }
        item {
            Button(onClick = ::connectAnother, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
                Text(if (busy) "Processando…" else if (connectedLinkIds.isEmpty()) "Conectar banco" else "Adicionar banco")
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
                confirmLink(id)
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
    var webViewRef by remember { mutableStateOf<WebView?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                webViewClient = WebViewClient()
                loadUrl("about:blank")
                clearHistory()
                removeAllViews()
                destroy()
            }
            webViewRef = null
        }
    }

    Dialog(
        onDismissRequest = { onExit("Conexão fechada antes da conclusão.") },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.96f).fillMaxHeight(0.92f),
            shape = RoundedCornerShape(20.dp)
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    val initial = runCatching { Uri.parse(url) }.getOrNull()
                    if (initial?.scheme != "https" || initial.host != "widget.belvo.io") {
                        onExit("URL do ambiente de consentimento inválida.")
                    }
                    WebView(context).apply {
                        webViewRef = this
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.setGeolocationEnabled(false)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.safeBrowsingEnabled = true
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
                                if (uri.scheme == "http") return true
                                if (uri.scheme == "https") return false
                                if (uri.scheme in setOf("file", "content", "javascript", "data", "about")) return true
                                runCatching {
                                    val intent = if (uri.toString().startsWith("intent://")) {
                                        Intent.parseUri(uri.toString(), Intent.URI_INTENT_SCHEME)
                                    } else {
                                        Intent(Intent.ACTION_VIEW, uri)
                                    }
                                    intent.addCategory(Intent.CATEGORY_BROWSABLE)
                                    intent.component = null
                                    intent.selector = null
                                    context.startActivity(intent)
                                }
                                return true
                            }
                        }
                        if (initial?.scheme == "https" && initial.host == "widget.belvo.io") loadUrl(url)
                    }
                },
                update = { webView ->
                    if (webView.url.isNullOrBlank()) {
                        val parsed = Uri.parse(url)
                        if (parsed.scheme == "https" && parsed.host == "widget.belvo.io") webView.loadUrl(url)
                    }
                }
            )
        }
    }
}
