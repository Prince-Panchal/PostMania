package com.ai.postmania

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.ai.postmania.presentation.PostManiaViewModel

class MainActivity : ComponentActivity() {
    private val viewModel = PostManiaViewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        handleDeepLink(intent)

        setContent {
            App(viewModel)
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        handleDeepLink(intent)
    }

    private fun handleDeepLink(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "postmania" && uri.host == "callback") {
            val authCode = uri.getQueryParameter("code")
            if (!authCode.isNullOrBlank()) {
                viewModel.finishAuthFlow(
                    code = authCode,
                    onSuccess = {
                        viewModel.showLinkedInAuthDialog.value = false
                    },
                    onFailure = { /* Log or toast error message */ }
                )
            }
        }
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}