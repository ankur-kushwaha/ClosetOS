package com.closetos.app

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.ui.screens.*
import com.closetos.app.ui.theme.ClosetOSTheme
import com.closetos.app.ui.theme.AccentGold
import com.closetos.app.ui.theme.ObsidianBg
import com.closetos.app.ui.theme.TextLight
import com.closetos.app.ui.theme.TextMuted
import com.closetos.app.ui.theme.CharcoalSurface
import kotlinx.coroutines.launch

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    object Ootd : Screen("ootd", "OOTD", Icons.Default.CalendarToday)
    object Ingest : Screen("ingest", "Digitize", Icons.Default.CloudUpload)
    object Wardrobe : Screen("wardrobe", "Wardrobe", Icons.Default.GridOn)
    object Builder : Screen("builder", "Lookbook", Icons.Default.AutoFixHigh)
    object Travel : Screen("travel", "Travel", Icons.Default.FlightTakeoff)
    object Resale : Screen("resale", "Sell", Icons.Default.Sell)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    ClosetOSTheme {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        var hasCompletedOnboarding by remember {
            mutableStateOf(PlatformStorage.loadString("has_completed_onboarding") == "true")
        }
        var activeRoute by remember { mutableStateOf(Screen.Ootd.route) }

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
                                        PlatformStorage.saveString("has_completed_onboarding", "true")
                                        activeRoute = item.route
                                        scope.launch {
                                            drawerState.close()
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
                                PlatformStorage.saveString("has_completed_onboarding", hasCompletedOnboarding.toString())
                                val status = if (hasCompletedOnboarding) "Bypassed Onboarding" else "Onboarding Quiz Required"
                                showToast(status)
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
                    if (hasCompletedOnboarding && activeRoute != "review_sweep") {
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
                    if (hasCompletedOnboarding && activeRoute != "review_sweep") {
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
                            PlatformStorage.saveString("has_completed_onboarding", "true")
                        })
                    } else {
                        when (activeRoute) {
                            Screen.Ootd.route -> OotdScreen()
                            Screen.Ingest.route -> IngestionScreen(
                                sharedUrl = null,
                                onNavigateToReview = {
                                    activeRoute = "review_sweep"
                                }
                            )
                            "review_sweep" -> ReviewSweepScreen(
                                onBack = {
                                    activeRoute = Screen.Ingest.route
                                }
                            )
                            Screen.Wardrobe.route -> WardrobeScreen()
                            Screen.Builder.route -> OutfitBuilderScreen()
                            Screen.Travel.route -> TripPlannerScreen()
                            Screen.Resale.route -> ResaleScreen()
                        }
                    }
                }
            }
        }
    }
}
