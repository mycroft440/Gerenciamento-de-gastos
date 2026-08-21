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

    private fun transaction(
        id: String,
        amount: String,
        type: TransactionType,
        currency: String = "BRL"
    ) = FinanceTransaction(
        id = id,
        description = "teste",
        amount = BigDecimal(amount),
        currency = currency,
        type = type,
        category = if (type == TransactionType.INCOME) Category.RENDA else Category.OUTROS,
        date = LocalDate.of(2026, 8, 20),
        source = "teste",
        status = TransactionStatus.PROCESSED
    )
}
