package br.com.gerenciamentogastos.data

import br.com.gerenciamentogastos.model.FinancialInstitution
import br.com.gerenciamentogastos.model.FinanceTransaction
import br.com.gerenciamentogastos.model.TransactionType
import java.time.LocalDate

class DemoOpenFinanceGateway : OpenFinanceGateway {
    override fun availableInstitutions(): List<FinancialInstitution> = listOf(
        FinancialInstitution("nubank", "Nubank", "Conta principal"),
        FinancialInstitution("inter", "Inter", "Conta digital"),
        FinancialInstitution("bb", "Banco do Brasil", "Conta corrente"),
        FinancialInstitution("mercadopago", "Mercado Pago", "Conta de pagamento"),
        FinancialInstitution("itau", "Itaú", "Conta corrente")
    )

    override fun transactions(): List<FinanceTransaction> {
        val raw = listOf(
            RawTransaction("1", "PIX RECEBIDO CLIENTE", 2800.00, TransactionType.INCOME, LocalDate.of(2026, 8, 2), "Nubank"),
            RawTransaction("2", "SUPERMERCADO BH", 186.42, TransactionType.EXPENSE, LocalDate.of(2026, 8, 3), "Nubank"),
            RawTransaction("3", "UBER *TRIP", 28.90, TransactionType.EXPENSE, LocalDate.of(2026, 8, 4), "Nubank"),
            RawTransaction("4", "PIX RECEBIDO FREELA", 950.00, TransactionType.INCOME, LocalDate.of(2026, 8, 5), "Inter"),
            RawTransaction("5", "CEMIG", 164.70, TransactionType.EXPENSE, LocalDate.of(2026, 8, 6), "Inter"),
            RawTransaction("6", "NETFLIX", 44.90, TransactionType.EXPENSE, LocalDate.of(2026, 8, 7), "Nubank"),
            RawTransaction("7", "FARMACIA CENTRAL", 72.35, TransactionType.EXPENSE, LocalDate.of(2026, 8, 9), "Inter"),
            RawTransaction("8", "AMAZON BR", 129.99, TransactionType.EXPENSE, LocalDate.of(2026, 8, 10), "Nubank"),
            RawTransaction("9", "ALUGUEL", 850.00, TransactionType.EXPENSE, LocalDate.of(2026, 8, 11), "Inter"),
            RawTransaction("10", "RESTAURANTE SABOR", 47.50, TransactionType.EXPENSE, LocalDate.of(2026, 8, 13), "Nubank"),
            RawTransaction("11", "POSTO AVENIDA", 80.00, TransactionType.EXPENSE, LocalDate.of(2026, 8, 14), "Inter"),
            RawTransaction("12", "PIX RECEBIDO SERVICO", 420.00, TransactionType.INCOME, LocalDate.of(2026, 8, 15), "Nubank")
        )

        return raw.map { item ->
            FinanceTransaction(
                id = item.id,
                description = item.description,
                amount = item.amount,
                type = item.type,
                category = TransactionCategorizer.categorize(item.description, item.type),
                date = item.date,
                source = item.source
            )
        }
    }

    private data class RawTransaction(
        val id: String,
        val description: String,
        val amount: Double,
        val type: TransactionType,
        val date: LocalDate,
        val source: String
    )
}
