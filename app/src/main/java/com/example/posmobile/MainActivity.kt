package com.example.posmobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.posmobile.data.Container
import com.example.posmobile.ui.AuthState
import com.example.posmobile.ui.SessionViewModel
import com.example.posmobile.ui.login.LoginScreen
import com.example.posmobile.ui.order.OrderScreen
import com.example.posmobile.ui.orders.RecentOrdersScreen
import com.example.posmobile.ui.outlet.OutletScreen
import com.example.posmobile.ui.printer.PrinterScreen
import com.example.posmobile.ui.profile.ProfileScreen
import com.example.posmobile.ui.theme.PosmobileTheme
import com.example.posmobile.ui.update.UpdateGate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Container.init(applicationContext)
        enableEdgeToEdge()
        setContent {
            PosmobileTheme(dynamicColor = false) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    AppRoot()
                    UpdateGate()
                }
            }
        }
    }
}

@Composable
private fun AppRoot(session: SessionViewModel = viewModel()) {
    when (session.authState) {
        AuthState.Loading -> Box(Modifier.fillMaxSize(), Alignment.Center) { CircularProgressIndicator() }
        AuthState.LoggedOut -> LoginScreen(session)
        AuthState.LoggedIn -> SignedInNav(session)
    }
}

@Composable
private fun SignedInNav(session: SessionViewModel) {
    val nav = rememberNavController()

    NavHost(navController = nav, startDestination = "outlets") {
        composable("outlets") {
            OutletScreen(
                session = session,
                onOutletChosen = { nav.navigate("order") },
                onOpenPrinter = { nav.navigate("printer") },
                onOpenProfile = { nav.navigate("profile") },
            )
        }
        composable("order") {
            val outlet = session.selectedOutlet
            if (outlet == null) {
                // Outlet was cleared — bounce back to the picker.
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    nav.popBackStack("outlets", inclusive = false)
                }
            } else {
                OrderScreen(
                    outlet = outlet,
                    onChangeOutlet = {
                        session.clearOutlet()
                        nav.popBackStack("outlets", inclusive = false)
                    },
                    onOpenPrinter = { nav.navigate("printer") },
                    onOpenProfile = { nav.navigate("profile") },
                    onOpenOrders = { nav.navigate("orders") },
                )
            }
        }
        composable("orders") {
            val outlet = session.selectedOutlet
            if (outlet == null) {
                androidx.compose.runtime.LaunchedEffect(Unit) {
                    nav.popBackStack("outlets", inclusive = false)
                }
            } else {
                RecentOrdersScreen(
                    outlet = outlet,
                    onBack = { nav.popBackStack() },
                    onOpenPrinter = { nav.navigate("printer") },
                )
            }
        }
        composable("printer") {
            PrinterScreen(onBack = { nav.popBackStack() })
        }
        composable("profile") {
            ProfileScreen(
                onBack = { nav.popBackStack() },
                onOpenPrinter = { nav.navigate("printer") },
                onLogout = { session.logout() },
            )
        }
    }
}
