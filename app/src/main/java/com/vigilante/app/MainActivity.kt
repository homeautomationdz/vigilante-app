package com.vigilante.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.navigation.compose.rememberNavController
import com.vigilante.app.data.repository.AuthRepository
import com.vigilante.app.ui.login.SplashContent
import com.vigilante.app.ui.navigation.Route
import com.vigilante.app.ui.navigation.VigilanteNavHost
import com.vigilante.app.ui.theme.VigilanteTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject
    lateinit var authRepository: AuthRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VigilanteTheme {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background
                    ) {
                        var startDestination by remember { mutableStateOf<String?>(null) }
                        LaunchedEffect(Unit) {
                            startDestination =
                                if (authRepository.hasAnyAccount()) Route.Login.route
                                else Route.FirstRun.route
                        }
                        val start = startDestination
                        if (start == null) {
                            SplashContent()
                        } else {
                            val navController = rememberNavController()
                            VigilanteNavHost(
                                navController = navController,
                                startDestination = start
                            )
                        }
                    }
                }
            }
        }
    }
}
