package br.com.gerenciamentogastos

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import br.com.gerenciamentogastos.ui.FinanceApp
import br.com.gerenciamentogastos.ui.theme.GerenciamentoGastosTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            GerenciamentoGastosTheme {
                FinanceApp()
            }
        }
    }
}
