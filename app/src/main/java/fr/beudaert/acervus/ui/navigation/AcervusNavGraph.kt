package fr.beudaert.acervus.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.beudaert.acervus.ui.detail.RecordDetailScreen
import fr.beudaert.acervus.ui.edit.AddEditRecordScreen
import fr.beudaert.acervus.ui.list.CollectionScreen
import fr.beudaert.acervus.ui.scan.BarcodeScanScreen
import fr.beudaert.acervus.ui.settings.SettingsScreen

@Composable
fun AcervusNavGraph(
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = AcervusDestinations.COLLECTION_ROUTE
    ) {
        composable(AcervusDestinations.COLLECTION_ROUTE) {
            CollectionScreen(
                onAddRecord = { navController.navigate(AcervusDestinations.addRecord()) },
                onScanRecord = { navController.navigate(AcervusDestinations.SCAN_ROUTE) },
                onOpenRecord = { id ->
                    navController.navigate(AcervusDestinations.recordDetail(id))
                },
                onOpenSettings = { navController.navigate(AcervusDestinations.SETTINGS_ROUTE) }
            )
        }
        composable(
            route = AcervusDestinations.ADD_RECORD_ROUTE,
            arguments = listOf(
                navArgument(AcervusNavArgs.BARCODE) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) {
            AddEditRecordScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(
            route = AcervusDestinations.RECORD_DETAIL_ROUTE,
            arguments = listOf(
                navArgument(AcervusNavArgs.RECORD_ID) { type = NavType.StringType }
            )
        ) {
            RecordDetailScreen(
                onBack = { navController.popBackStack() },
                onEdit = { id -> navController.navigate(AcervusDestinations.editRecord(id)) }
            )
        }
        composable(
            route = AcervusDestinations.EDIT_RECORD_ROUTE,
            arguments = listOf(
                navArgument(AcervusNavArgs.RECORD_ID) { type = NavType.StringType }
            )
        ) {
            AddEditRecordScreen(
                onSaved = { navController.popBackStack() },
                onCancel = { navController.popBackStack() }
            )
        }
        composable(AcervusDestinations.SETTINGS_ROUTE) {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
        composable(AcervusDestinations.SCAN_ROUTE) {
            BarcodeScanScreen(
                onBack = { navController.popBackStack() },
                onExistingRecord = { id ->
                    navController.navigate(AcervusDestinations.recordDetail(id)) {
                        popUpTo(AcervusDestinations.SCAN_ROUTE) { inclusive = true }
                    }
                },
                onNewBarcode = { barcode ->
                    navController.navigate(AcervusDestinations.addRecord(barcode)) {
                        popUpTo(AcervusDestinations.SCAN_ROUTE) { inclusive = true }
                    }
                }
            )
        }
    }
}
