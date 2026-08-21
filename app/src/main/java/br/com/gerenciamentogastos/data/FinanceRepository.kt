package br.com.gerenciamentogastos.data

import br.com.gerenciamentogastos.model.FinancialInstitution
import br.com.gerenciamentogastos.model.FinancialSummary
import br.com.gerenciamentogastos.model.FinanceTransaction
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
        val income = brl
            .asSequence()
            .filter { it.type == TransactionType.INCOME }
            .fold(BigDecimal.ZERO) { total, item -> total.add(item.amount) }
        val expenses = brl
            .asSequence()
            .filter { it.type == TransactionType.EXPENSE }
            .fold(BigDecimal.ZERO) { total, item -> total.add(item.amount) }
        return FinancialSummary(
            income = income,
            expenses = expenses,
            excludedForeignTransactions = transactions.count { it.currency != "BRL" }
        )
    }
}
