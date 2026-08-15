package br.com.gerenciamentogastos.data

import br.com.gerenciamentogastos.model.Category
import br.com.gerenciamentogastos.model.TransactionType
import java.text.Normalizer
import java.util.Locale

object TransactionCategorizer {
    private val rules = listOf(
        Category.ALIMENTACAO to listOf("supermercado", "mercado", "padaria", "restaurante", "ifood", "lanche"),
        Category.TRANSPORTE to listOf("uber", "99app", "99 pop", "posto", "combustivel", "estacionamento", "pedagio"),
        Category.MORADIA to listOf("aluguel", "condominio", "cemig", "copasa", "energia", "agua"),
        Category.ASSINATURAS to listOf("netflix", "spotify", "youtube", "prime video", "google one", "assinatura"),
        Category.SAUDE to listOf("farmacia", "drogaria", "clinica", "laboratorio", "consulta"),
        Category.COMPRAS to listOf("amazon", "shopee", "mercado livre", "magalu", "loja"),
        Category.TRANSFERENCIAS to listOf("transferencia", "pix enviado")
    )

    fun categorize(description: String, type: TransactionType): Category {
        if (type == TransactionType.INCOME) return Category.RENDA

        val normalized = normalize(description)
        return rules.firstOrNull { (_, keywords) ->
            keywords.any { normalized.contains(it) }
        }?.first ?: Category.OUTROS
    }

    private fun normalize(value: String): String {
        return Normalizer.normalize(value, Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .lowercase(Locale.ROOT)
    }
}
