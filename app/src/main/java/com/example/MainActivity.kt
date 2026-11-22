package com.example

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.capture.HardwareShutterBus
import com.example.data.local.AppDatabase
import com.example.data.preferences.StudioPreferencesRepository
import com.example.data.repository.ScriptsRepository
import com.example.ui.navigation.EditorRoute
import com.example.ui.navigation.LibraryRoute
import com.example.ui.navigation.StudioRoute
import com.example.ui.screens.ScriptEditorScreen
import com.example.ui.screens.ScriptLibraryScreen
import com.example.ui.screens.TeleprompterStudioScreen
import com.example.ui.theme.AppThemeMode
import com.example.ui.theme.EyelineTheme
import com.example.ui.viewmodel.ScriptsViewModel
import com.example.ui.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val appContext = applicationContext
        val database = AppDatabase.getInstance(appContext)
        val scriptsRepository = ScriptsRepository(database.scriptDao())
        val preferencesRepository = StudioPreferencesRepository(appContext)

        setContent {
            val scriptsViewModel: ScriptsViewModel = viewModel(
                factory = remember {
                    ScriptsViewModel.Factory(scriptsRepository, preferencesRepository)
                }
            )
            val settings by scriptsViewModel.settings.collectAsStateWithLifecycle()

            val navController = rememberNavController()
            val backStackEntry by navController.currentBackStackEntryAsState()
            val isStudioRoute = backStackEntry?.destination?.route?.contains("StudioRoute") == true

            val isSystemDark = isSystemInDarkTheme()
            val useDarkChrome = isStudioRoute || when (settings.themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemDark
            }

            SystemBarsEffect(useDarkChrome = useDarkChrome)

            EyelineTheme(themeMode = settings.themeMode) {
                NavHost(
                    navController = navController,
                    startDestination = LibraryRoute,
                ) {
                    composable<LibraryRoute> {
                        ScriptLibraryScreen(
                            viewModel = scriptsViewModel,
                            onCreateScript = {
                                navController.navigate(EditorRoute(scriptId = 0L))
                            },
                            onEditScript = { id ->
                                navController.navigate(EditorRoute(scriptId = id))
                            },
                            onRecordScript = { id ->
                                navController.navigate(StudioRoute(scriptId = id))
                            },
                        )
                    }

                    composable<EditorRoute> { entry ->
                        val route = entry.toRoute<EditorRoute>()
                        ScriptEditorScreen(
                            scriptId = route.scriptId,
                            viewModel = scriptsViewModel,
                            onNavigateBack = { navController.popBackStack() },
                            onOpenStudio = { savedScriptId ->
                                navController.navigate(StudioRoute(scriptId = savedScriptId))
                            },
                        )
                    }

                    composable<StudioRoute> { entry ->
                        val route = entry.toRoute<StudioRoute>()
                        val studioViewModel: StudioViewModel = viewModel(
                            key = "studio_${route.scriptId}",
                            factory = remember(route.scriptId) {
                                StudioViewModel.Factory(
                                    scriptId = route.scriptId,
                                    scriptsRepository = scriptsRepository,
                                    preferences = preferencesRepository,
                                )
                            },
                        )
                        TeleprompterStudioScreen(
                            viewModel = studioViewModel,
                            onCloseStudio = { navController.popBackStack() },
                            onEditScript = {
                                val poppedToEditor = navController.popBackStack(
                                    route = EditorRoute(scriptId = route.scriptId),
                                    inclusive = false,
                                )
                                if (!poppedToEditor) {
                                    navController.navigate(EditorRoute(scriptId = route.scriptId)) {
                                        popUpTo(StudioRoute(scriptId = route.scriptId)) {
                                            inclusive = true
                                        }
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN &&
            HardwareShutterBus.shouldInterceptVolumeDown()
        ) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                HardwareShutterBus.emitShutterTrigger()
            }
            // Consume both ACTION_DOWN and ACTION_UP so system volume slider never interrupts filming
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    @Composable
    private fun SystemBarsEffect(useDarkChrome: Boolean) {
        SideEffect {
            val style = if (useDarkChrome) {
                SystemBarStyle.dark(AndroidColor.TRANSPARENT)
            } else {
                SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
            }
            enableEdgeToEdge(
                statusBarStyle = style,
                navigationBarStyle = style,
            )
        }
    }
}
