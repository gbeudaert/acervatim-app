package fr.beudaert.acervatim

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import fr.beudaert.acervatim.ui.navigation.AcervatimNavGraph
import fr.beudaert.acervatim.ui.theme.AcervatimTheme

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
