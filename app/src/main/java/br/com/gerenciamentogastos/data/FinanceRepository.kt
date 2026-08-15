package br.com.gerenciamentogastos.data

import br.com.gerenciamentogastos.model.FinancialInstitution
import br.com.gerenciamentogastos.model.FinancialSummary
import br.com.gerenciamentogastos.model.FinanceTransaction
import br.com.gerenciamentogastos.model.TransactionType

class FinanceRepository(
    private val gateway: OpenFinanceGateway = DemoOpenFinanceGateway()
) {
    fun transactions(): List<FinanceTransaction> = gateway.transactions()
        .sortedByDescending { it.date }

    fun institutions(): List<FinancialInstitution> = gateway.availableInstitutions()

    fun summary(transactions: List<FinanceTransaction> = transactions()): FinancialSummary {
        val income = transactions
            .filter { it.type == TransactionType.INCOME }
            .sumOf { it.amount }
        val expenses = transactions
            .filter { it.type == TransactionType.EXPENSE }
            .sumOf { it.amount }
        return FinancialSummary(income = income, expenses = expenses)
    }
}
