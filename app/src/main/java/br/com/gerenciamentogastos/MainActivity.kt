package br.com.gerenciamentogastos

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import br.com.gerenciamentogastos.ui.FinanceApp
import br.com.gerenciamentogastos.ui.theme.GerenciamentoGastosTheme

class MainActivity : ComponentActivity() {
    private var callbackUri by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        callbackUri = intent?.data
        setContent {
            GerenciamentoGastosTheme {
                FinanceApp(
                    callbackUri = callbackUri,
                    onCallbackHandled = { callbackUri = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        callbackUri = intent.data
    }
}
