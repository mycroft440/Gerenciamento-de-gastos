package br.com.gerenciamentogastos.data

import br.com.gerenciamentogastos.model.FinancialInstitution
import br.com.gerenciamentogastos.model.FinancialSummary
import br.com.gerenciamentogastos.model.FinanceTransaction
import br.com.gerenciamentogastos.model.TransactionStatus
import br.com.gerenciamentogastos.model.TransactionType
import java.math.BigDecimal

class FinanceRepository(
    private val gateway: OpenFinanceGateway = DemoOpenFinanceGateway()
) {
    fun transactions(): List<FinanceTransaction> = gateway.transactions()
        .sortedByDescending { it.date }

    fun institutions(): List<FinancialInstitution> = gateway.availableInstitutions()

    fun summary(transactions: List<FinanceTransaction> = transactions()): FinancialSummary {
        val brl = transactions.filter { it.currency == "BRL" }
        val settled = brl.filter { it.status != TransactionStatus.PENDING }
        val pending = brl.filter { it.status == TransactionStatus.PENDING }

        fun sum(items: List<FinanceTransaction>, type: TransactionType): BigDecimal =
            items.asSequence()
                .filter { it.type == type }
                .fold(BigDecimal.ZERO) { total, item -> total.add(item.amount) }

        return FinancialSummary(
            income = sum(settled, TransactionType.INCOME),
            expenses = sum(settled, TransactionType.EXPENSE),
            pendingIncome = sum(pending, TransactionType.INCOME),
            pendingExpenses = sum(pending, TransactionType.EXPENSE),
            pendingTransactions = pending.size,
            excludedForeignTransactions = transactions.count { it.currency != "BRL" }
        )
    }
}
