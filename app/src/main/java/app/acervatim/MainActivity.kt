package app.acervatim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import app.acervatim.ui.navigation.AcervatimNavGraph
import app.acervatim.ui.theme.AcervatimTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AcervatimTheme {
                AcervatimNavGraph()
            }
        }
    }
}
