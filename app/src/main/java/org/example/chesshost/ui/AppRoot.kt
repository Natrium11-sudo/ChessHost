package org.example.chesshost.ui

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.example.chesshost.ui.navigation.Routes
import org.example.chesshost.ui.screens.EngineMatchScreen
import org.example.chesshost.ui.screens.GameDetailScreen
import org.example.chesshost.ui.screens.HistoryScreen
import org.example.chesshost.ui.screens.LlmScreen
import org.example.chesshost.ui.screens.MenuScreen
import org.example.chesshost.ui.screens.PlayScreen
import org.example.chesshost.ui.screens.PositionEditorScreen
import org.example.chesshost.ui.screens.PositionsScreen
import org.example.chesshost.ui.screens.SettingsScreen
import org.example.chesshost.ui.screens.StoreScreen
import org.example.chesshost.ui.screens.TournamentListScreen
import org.example.chesshost.ui.screens.TournamentScreen
import org.example.chesshost.ui.screens.TournamentSetupScreen

@Composable
fun AppRoot() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.MENU) {
        composable(Routes.MENU) {
            MenuScreen(
                onNavigate = { route ->
                    navController.navigate(route) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = Routes.PLAY,
            arguments = listOf(
                navArgument("start") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            PlayScreen(
                startSpec = entry.arguments?.getString("start"),
                onBack = { navController.popBackStack() },
                onOpenStore = { navController.navigate(Routes.STORE) { launchSingleTop = true } },
            )
        }
        composable(Routes.POSITIONS) {
            PositionsScreen(
                onBack = { navController.popBackStack() },
                onNewPosition = { navController.navigate(Routes.positionEditor(0L)) },
                onEdit = { id -> navController.navigate(Routes.positionEditor(id)) },
                onPlay = { fen, variant ->
                    navController.navigate(Routes.play(fen, variant)) { launchSingleTop = true }
                },
                onNewChess960 = {
                    val fen = org.example.chesshost.chess.Chess960Rules.randomStartFen(kotlin.random.Random.Default)
                    navController.navigate(Routes.play(fen, org.example.chesshost.chess.Chess960Rules.id)) {
                        launchSingleTop = true
                    }
                },
                onMatch = { fen, variant ->
                    navController.navigate(Routes.engineMatch(fen, variant)) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = Routes.POSITION_EDITOR,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            PositionEditorScreen(
                positionId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { navController.popBackStack() },
                onPlay = { fen, variant ->
                    navController.navigate(Routes.play(fen, variant)) { launchSingleTop = true }
                },
            )
        }
        composable(
            route = Routes.ENGINE_MATCH,
            arguments = listOf(
                navArgument("start") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            EngineMatchScreen(
                startSpec = entry.arguments?.getString("start"),
                onBack = { navController.popBackStack() },
                onOpenStore = { navController.navigate(Routes.STORE) { launchSingleTop = true } },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenGame = { id -> navController.navigate(Routes.gameDetail(id)) },
            )
        }
        composable(Routes.STORE) {
            StoreScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TOURNAMENTS) {
            TournamentListScreen(
                onBack = { navController.popBackStack() },
                onNewTournament = {
                    navController.navigate(Routes.TOURNAMENT_SETUP) { launchSingleTop = true }
                },
                onOpen = { id -> navController.navigate(Routes.tournamentDetail(id)) },
            )
        }
        composable(Routes.TOURNAMENT_SETUP) {
            TournamentSetupScreen(
                onBack = { navController.popBackStack() },
                onStarted = { id ->
                    navController.navigate(Routes.tournamentDetail(id)) {
                        popUpTo(Routes.TOURNAMENT_SETUP) { inclusive = true }
                    }
                },
                onOpenStore = { navController.navigate(Routes.STORE) { launchSingleTop = true } },
            )
        }
        composable(
            route = Routes.TOURNAMENT_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            TournamentScreen(
                tournamentId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenStore = { navController.navigate(Routes.STORE) { launchSingleTop = true } },
                onOpenLlm = { navController.navigate(Routes.LLM) { launchSingleTop = true } },
            )
        }
        composable(Routes.LLM) {
            LlmScreen(onBack = { navController.popBackStack() })
        }
        composable(
            route = Routes.GAME_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            GameDetailScreen(
                gameId = entry.arguments?.getLong("id") ?: 0L,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
