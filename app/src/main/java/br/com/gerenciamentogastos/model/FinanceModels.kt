package br.com.gerenciamentogastos.model

import java.time.LocalDate

enum class TransactionType {
    INCOME,
    EXPENSE
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
    val amount: Double,
    val type: TransactionType,
    val category: Category,
    val date: LocalDate,
    val source: String
)

data class FinancialInstitution(
    val id: String,
    val name: String,
    val accountLabel: String
)

data class FinancialSummary(
    val income: Double,
    val expenses: Double
) {
    val balance: Double = income - expenses
}
