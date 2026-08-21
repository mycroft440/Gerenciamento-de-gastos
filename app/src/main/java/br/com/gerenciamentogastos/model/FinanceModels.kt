package br.com.gerenciamentogastos.model

import java.math.BigDecimal
import java.time.LocalDate

enum class TransactionType {
    INCOME,
    EXPENSE
}

enum class TransactionStatus {
    PENDING,
    PROCESSED,
    UNKNOWN
}

enum class Category(val label: String, val symbol: String) {
    RENDA("Renda", "R\$"),
    ALIMENTACAO("Alimentação", "A"),
    TRANSPORTE("Transporte", "T"),
    MORADIA("Moradia", "M"),
    ASSINATURAS("Assinaturas", "S"),
    SAUDE("Saúde", "+"),
    COMPRAS("Compras", "C"),
    TRANSFERENCIAS("Transferências", "↔"),
    OUTROS("Outros", "•")
}

data class FinanceTransaction(
    val id: String,
    val description: String,
    val amount: BigDecimal,
    val currency: String,
    val type: TransactionType,
    val category: Category,
    val date: LocalDate,
    val source: String,
    val status: TransactionStatus = TransactionStatus.UNKNOWN
)

data class FinancialInstitution(
    val id: String,
    val name: String,
    val accountLabel: String
)

data class FinancialSummary(
    val income: BigDecimal,
    val expenses: BigDecimal,
    val excludedForeignTransactions: Int = 0
) {
    val balance: BigDecimal
        get() = income.subtract(expenses)
}
