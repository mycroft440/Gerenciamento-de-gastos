package br.com.gerenciamentogastos.data

import br.com.gerenciamentogastos.model.Category
import br.com.gerenciamentogastos.model.FinanceTransaction
import br.com.gerenciamentogastos.model.TransactionStatus
import br.com.gerenciamentogastos.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal
import java.time.LocalDate

class FinanceRepositoryTest {
    private val repository = FinanceRepository()

    @Test
    fun `soma centavos sem erro binario`() {
        val transactions = listOf(
            transaction("a", "0.10", TransactionType.INCOME),
            transaction("b", "0.20", TransactionType.INCOME),
            transaction("c", "0.05", TransactionType.EXPENSE)
        )

        val summary = repository.summary(transactions)

        assertEquals(BigDecimal("0.30"), summary.income)
        assertEquals(BigDecimal("0.05"), summary.expenses)
        assertEquals(BigDecimal("0.25"), summary.balance)
    }

    @Test
    fun `nao mistura moeda estrangeira nos totais em reais`() {
        val transactions = listOf(
            transaction("brl", "10.00", TransactionType.INCOME, "BRL"),
            transaction("usd", "100.00", TransactionType.INCOME, "USD")
        )

        val summary = repository.summary(transactions)

        assertEquals(BigDecimal("10.00"), summary.income)
        assertEquals(1, summary.excludedForeignTransactions)
    }

    @Test
    fun `pendentes ficam separados dos totais processados`() {
        val transactions = listOf(
            transaction("ok-in", "100.00", TransactionType.INCOME),
            transaction("ok-out", "40.00", TransactionType.EXPENSE),
            transaction("pending-in", "15.00", TransactionType.INCOME, status = TransactionStatus.PENDING),
            transaction("pending-out", "12.50", TransactionType.EXPENSE, status = TransactionStatus.PENDING)
        )

        val summary = repository.summary(transactions)

        assertEquals(BigDecimal("100.00"), summary.income)
        assertEquals(BigDecimal("40.00"), summary.expenses)
        assertEquals(BigDecimal("60.00"), summary.balance)
        assertEquals(BigDecimal("15.00"), summary.pendingIncome)
        assertEquals(BigDecimal("12.50"), summary.pendingExpenses)
        assertEquals(2, summary.pendingTransactions)
    }

    private fun transaction(
        id: String,
        amount: String,
        type: TransactionType,
        currency: String = "BRL",
        status: TransactionStatus = TransactionStatus.PROCESSED
    ) = FinanceTransaction(
        id = id,
        description = "teste",
        amount = BigDecimal(amount),
        currency = currency,
        type = type,
        category = if (type == TransactionType.INCOME) Category.RENDA else Category.OUTROS,
        date = LocalDate.of(2026, 8, 20),
        source = "teste",
        status = status
    )
}
