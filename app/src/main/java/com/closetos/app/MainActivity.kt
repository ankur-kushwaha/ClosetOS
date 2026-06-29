package com.closetos.app

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.closetos.app.ui.screens.*
import com.closetos.app.ui.theme.ClosetOSTheme
import com.closetos.app.ui.theme.AccentGold
import com.closetos.app.ui.theme.ObsidianBg
import com.closetos.app.ui.theme.TextLight
import com.closetos.app.ui.theme.TextMuted
import com.closetos.app.ui.theme.CharcoalSurface
import kotlinx.coroutines.launch

// Screen routes definition
sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Ootd : Screen("ootd", "OOTD", Icons.Default.CalendarToday)
    object Ingest : Screen("ingest", "Digitize", Icons.Default.CloudUpload)
    object Wardrobe : Screen("wardrobe", "Wardrobe", Icons.Default.GridOn)
    object Builder : Screen("builder", "Lookbook", Icons.Default.AutoFixHigh)
    object Travel : Screen("travel", "Travel", Icons.Default.FlightTakeoff)
    object Resale : Screen("resale", "Sell", Icons.Default.Sell)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Initialize local storage repository
        com.closetos.app.data.repository.ClosetRepository.init(applicationContext)

        // Check if launched via Android Send Share intent
        var sharedUrl: String? = null
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            sharedUrl = intent.getStringExtra(Intent.EXTRA_TEXT)
        }

        setContent {
            ClosetOSTheme {
                val navController = rememberNavController()
                val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
                val scope = rememberCoroutineScope()
                val context = LocalContext.current

                var hasCompletedOnboarding by remember { mutableStateOf(false) }
                var activeRoute by remember { mutableStateOf(Screen.Ootd.route) }

                // Outer Modal navigation drawer serving as the Developer Overrides Jump Drawer
                ModalNavigationDrawer(
                    drawerState = drawerState,
                    drawerContent = {
                        ModalDrawerSheet(
                            drawerContainerColor = CharcoalSurface,
                            modifier = Modifier.width(300.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxHeight()
                                    .background(CharcoalSurface)
                                    .padding(24.dp)
                            ) {
                                Text(
                                    text = "ClosetOS Debug Panel",
                                    fontFamily = MaterialTheme.typography.displayMedium.fontFamily,
                                    fontSize = 20.sp,
                                    color = AccentGold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                Text(
                                    text = "Developer Drawer: bypass loops & jump screens instantly.",
                                    fontFamily = MaterialTheme.typography.bodyMedium.fontFamily,
                                    fontSize = 12.sp,
                                    color = TextMuted,
                                    modifier = Modifier.padding(bottom = 24.dp)
                                )

                                Divider(color = AccentGold.copy(alpha = 0.2f), thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(16.dp))

                                // Fast jump options
                                Text(
                                    text = "FAST NAV JUMPS",
                                    fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                val navItems = listOf(
                                    Screen.Ootd, Screen.Ingest, Screen.Wardrobe,
                                    Screen.Builder, Screen.Travel, Screen.Resale
                                )
                                
                                navItems.forEach { item ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(8.dp))
                                            .clickable {
                                                hasCompletedOnboarding = true
                                                activeRoute = item.route
                                                scope.launch {
                                                    drawerState.close()
                                                    navController.navigate(item.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            }
                                            .padding(vertical = 12.dp, horizontal = 8.dp)
                                    ) {
                                        Icon(item.icon, contentDescription = null, tint = AccentGold, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(item.title, color = TextLight, fontSize = 14.sp)
                                    }
                                }

                                Spacer(modifier = Modifier.height(20.dp))
                                Divider(color = AccentGold.copy(alpha = 0.2f), thickness = 0.5.dp)
                                Spacer(modifier = Modifier.height(16.dp))

                                // State utilities overrides
                                Text(
                                    text = "STATE CONTROLS",
                                    fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = AccentGold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )

                                Button(
                                    onClick = {
                                        hasCompletedOnboarding = !hasCompletedOnboarding
                                        val status = if (hasCompletedOnboarding) "Bypassed Onboarding" else "Onboarding Quiz Required"
                                        Toast.makeText(context, status, Toast.LENGTH_SHORT).show()
                                        scope.launch { drawerState.close() }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = AccentGold),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = if (hasCompletedOnboarding) "Require Onboarding" else "Skip Onboarding",
                                        color = ObsidianBg,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                ) {
                    Scaffold(
                        topBar = {
                            if (hasCompletedOnboarding) {
                                @OptIn(ExperimentalMaterial3Api::class)
                                CenterAlignedTopAppBar(
                                    title = {
                                        Text(
                                            text = "ClosetOS",
                                            fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                                            fontSize = 20.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = AccentGold
                                        )
                                    },
                                    navigationIcon = {
                                        // Menu button triggers developer drawer
                                        IconButton(onClick = { scope.launch { drawerState.open() } }) {
                                            Icon(Icons.Default.Settings, contentDescription = "Debug Drawer", tint = AccentGold)
                                        }
                                    },
                                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                                        containerColor = ObsidianBg,
                                        titleContentColor = AccentGold
                                    )
                                )
                            }
                        },
                        bottomBar = {
                            if (hasCompletedOnboarding) {
                                NavigationBar(
                                    containerColor = ObsidianBg,
                                    tonalElevation = 8.dp,
                                    modifier = Modifier.border(0.5.dp, AccentGold.copy(alpha = 0.15f), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                                ) {
                                    val items = listOf(
                                        Screen.Ootd, Screen.Ingest, Screen.Wardrobe,
                                        Screen.Builder, Screen.Travel, Screen.Resale
                                    )
                                    items.forEach { item ->
                                        NavigationBarItem(
                                            selected = activeRoute == item.route,
                                            onClick = {
                                                activeRoute = item.route
                                                navController.navigate(item.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            },
                                            icon = { Icon(item.icon, contentDescription = item.title) },
                                            label = { Text(item.title, fontSize = 10.sp) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = ObsidianBg,
                                                selectedTextColor = AccentGold,
                                                unselectedIconColor = TextMuted,
                                                unselectedTextColor = TextMuted,
                                                indicatorColor = AccentGold
                                            )
                                        )
                                    }
                                }
                            }
                        },
                        containerColor = ObsidianBg
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            if (!hasCompletedOnboarding) {
                                OnboardingScreen(onOnboardingComplete = {
                                    hasCompletedOnboarding = true
                                })
                            } else {
                                NavHost(
                                    navController = navController,
                                    startDestination = Screen.Ootd.route
                                ) {
                                    composable(Screen.Ootd.route) {
                                        OotdScreen()
                                    }
                                    composable(Screen.Ingest.route) {
                                        IngestionScreen(
                                            sharedUrl = sharedUrl,
                                            onNavigateToReview = {
                                                navController.navigate("review_sweep")
                                            }
                                        )
                                    }
                                    composable("review_sweep") {
                                        ReviewSweepScreen(
                                            onBack = {
                                                navController.navigateUp()
                                            }
                                        )
                                    }
                                    composable(Screen.Wardrobe.route) {
                                        WardrobeScreen()
                                    }
                                    composable(Screen.Builder.route) {
                                        OutfitBuilderScreen()
                                    }
                                    composable(Screen.Travel.route) {
                                        TripPlannerScreen()
                                    }
                                    composable(Screen.Resale.route) {
                                        ResaleScreen()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
