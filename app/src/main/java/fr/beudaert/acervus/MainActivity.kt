package fr.beudaert.acervus

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import fr.beudaert.acervus.ui.navigation.AcervusNavGraph
import fr.beudaert.acervus.ui.theme.AcervusTheme

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AcervusTheme {
                AcervusNavGraph()
            }
        }
    }
}
