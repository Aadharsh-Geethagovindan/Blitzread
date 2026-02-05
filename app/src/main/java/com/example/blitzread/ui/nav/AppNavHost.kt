package com.example.blitzread.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.blitzread.ui.library.LibraryScreen
import com.example.blitzread.ui.docnav.DocumentNavScreen
import com.example.blitzread.ui.reader.ReaderScreen
import com.example.blitzread.ui.components.BottomBar
import com.example.blitzread.ui.components.BottomBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import com.example.blitzread.data.repo.LibraryRepository

@Composable
fun AppNavHost() {
    val nav = rememberNavController()
    val backStackEntry by nav.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route.orEmpty()

    // Route matching helpers (because your routes include args like ".../{docId}")
    val isLibrary = route.startsWith(Route.Library.path)
    val isReader = route.startsWith("reader") // adjust if your Route.Reader.path differs
    val showBottomBar = true // keep always-on for now

    val bottomItems = if (isLibrary) {
        listOf(
            BottomBarItem(
                icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White) },
                onClick = { /* TODO */ }
            ),
            BottomBarItem(
                icon = { Icon(Icons.Filled.ExitToApp, contentDescription = "Logout", tint = Color.White) },
                onClick = { /* TODO */ }
            )
        )
    } else {
        listOf(
            BottomBarItem(
                icon = { Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White) },
                onClick = { nav.popBackStack() }
            ),
            BottomBarItem(
                icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = Color.White) },
                onClick = { /* TODO */ }
            ),
            BottomBarItem(
                icon = { Icon(Icons.Filled.Home, contentDescription = "Home", tint = Color.White) },
                onClick = { nav.navigate(Route.Library.path) { popUpTo(Route.Library.path) } }
            )
        )
    }

    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                BottomBar(items = bottomItems)
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = nav,
            startDestination = Route.Library.path,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Route.Library.path) {
                LibraryScreen(
                    onOpenDocument = { docId -> nav.navigate(Route.DocumentNav.create(docId)) }
                )
            }

            composable(
                route = Route.DocumentNav.path,
                arguments = listOf(navArgument("docId") { type = NavType.StringType })
            ) { backStack ->
                val docId = backStack.arguments?.getString("docId")!!
                DocumentNavScreen(
                    docId = docId,
                    onStartReading = { nav.navigate(Route.Reader.create(docId)) },
                    onDelete = {
                        LibraryRepository.remove(docId)
                        nav.popBackStack() // returns to Library
                    }
                )
            }

            composable(
                route = Route.Reader.path,
                arguments = listOf(navArgument("docId") { type = NavType.StringType })
            ) { backStack ->
                val docId = backStack.arguments?.getString("docId")!!
                ReaderScreen(docId = docId)
            }
        }
    }
}
