package br.com.gerenciamentogastos.data

import br.com.gerenciamentogastos.model.FinancialInstitution
import br.com.gerenciamentogastos.model.FinanceTransaction

/**
 * Fronteira entre o aplicativo e o provedor de Open Finance.
 *
 * A implementação real deve fazer o fluxo de consentimento/OAuth pelo provedor
 * escolhido e nunca receber senha bancária diretamente do usuário.
 */
interface OpenFinanceGateway {
    fun availableInstitutions(): List<FinancialInstitution>
    fun transactions(): List<FinanceTransaction>
}
