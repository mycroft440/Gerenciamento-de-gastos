package br.com.gerenciamentogastos.data

import br.com.gerenciamentogastos.model.Category
import br.com.gerenciamentogastos.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Test

class TransactionCategorizerTest {
    @Test
    fun `categoriza supermercado como alimentacao`() {
        assertEquals(
            Category.ALIMENTACAO,
            TransactionCategorizer.categorize("SUPERMERCADO BH", TransactionType.EXPENSE)
        )
    }

    @Test
    fun `categoriza uber como transporte`() {
        assertEquals(
            Category.TRANSPORTE,
            TransactionCategorizer.categorize("UBER *TRIP", TransactionType.EXPENSE)
        )
    }

    @Test
    fun `toda entrada e renda`() {
        assertEquals(
            Category.RENDA,
            TransactionCategorizer.categorize("PIX RECEBIDO", TransactionType.INCOME)
        )
    }

    @Test
    fun `descricao desconhecida cai em outros`() {
        assertEquals(
            Category.OUTROS,
            TransactionCategorizer.categorize("PAGAMENTO XYZ", TransactionType.EXPENSE)
        )
    }
}
