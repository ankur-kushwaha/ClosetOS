package com.closetos.app

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.closetos.app.data.repository.NotificationCenter
import com.closetos.app.ui.components.NotificationBell
import com.closetos.app.ui.components.NotificationInboxSheet
import com.closetos.app.ui.components.NotificationScheduleHost
import com.closetos.app.ui.components.WardrobeEvolutionBanner
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
    object Builder : Screen("builder", "Lookbook", Icons.Default.Style)
    object Travel : Screen("travel", "Travel", Icons.Default.FlightTakeoff)
    object Resale : Screen("resale", "Sell", Icons.Default.Sell)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun App() {
    var isDarkTheme by remember {
        mutableStateOf(PlatformStorage.loadString("dark_theme") == "true")
    }
    ClosetOSTheme(darkTheme = isDarkTheme) {
        val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
        val scope = rememberCoroutineScope()

        var hasCompletedOnboarding by remember {
            mutableStateOf(PlatformStorage.loadString("has_completed_onboarding") == "true")
        }
        var activeRoute by remember { mutableStateOf(Screen.Ootd.route) }
        var showNotificationInbox by remember { mutableStateOf(false) }

        val activeBanner by NotificationCenter.activeBanner.collectAsState()
        val allNotifications by NotificationCenter.notifications.collectAsState()
        val unreadCount = remember(allNotifications) { NotificationCenter.unreadCount() }

        NotificationScheduleHost()

        fun handleNotificationAction(notification: com.closetos.app.data.model.AppNotification) {
            NotificationCenter.markRead(notification.id)
            NotificationCenter.dismissBanner()
            notification.payload?.occasionUnlocks
                ?.firstOrNull { it.count > 0 }
                ?.label
                ?.let { NotificationCenter.pendingLookbookOccasion = it }
            when (notification.actionRoute) {
                Screen.Ootd.route, "ootd" -> activeRoute = Screen.Ootd.route
                Screen.Builder.route, "builder" -> activeRoute = Screen.Builder.route
                else -> notification.actionRoute?.let { activeRoute = it }
            }
            showNotificationInbox = false
        }

        ModalNavigationDrawer(
            drawerState = drawerState,
            drawerContent = {
                ModalDrawerSheet(
                    drawerContainerColor = CharcoalSurface,
                    modifier = Modifier.width(310.dp)
                ) {
                    val scrollState = rememberScrollState()
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .background(CharcoalSurface)
                            .verticalScroll(scrollState)
                            .padding(24.dp)
                    ) {
                        // Title / Brand Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(bottom = 8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Checkroom,
                                contentDescription = "Logo",
                                tint = AccentGold,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "ClosetOS",
                                fontFamily = MaterialTheme.typography.displayMedium.fontFamily,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                color = AccentGold
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Text(
                                text = "v1.0.0",
                                fontSize = 12.sp,
                                color = TextMuted
                            )
                        }

                        Text(
                            text = "Smart Wardrobe Management & Try-On",
                            fontSize = 11.sp,
                            color = TextMuted,
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        HorizontalDivider(color = AccentGold.copy(alpha = 0.15f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // Wardrobe Stats Card
                        val garments = ClosetRepository.garments.collectAsState().value
                        val totalGarments = garments.size
                        val topsCount = garments.count { it.category.equals("Top", ignoreCase = true) }
                        val bottomsCount = garments.count { it.category.equals("Bottom", ignoreCase = true) }
                        val outerCount = garments.count { it.category.equals("Outerwear", ignoreCase = true) }
                        val shoesCount = garments.count { it.category.equals("Shoes", ignoreCase = true) }

                        Text(
                            text = "CLOSET STATISTICS",
                            fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(ObsidianBg.copy(alpha = 0.3f))
                                .border(0.5.dp, AccentGold.copy(alpha = 0.1f), RoundedCornerShape(8.dp))
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Total Items", color = TextLight, fontSize = 13.sp)
                                Text("$totalGarments", color = AccentGold, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            HorizontalDivider(color = AccentGold.copy(alpha = 0.05f), thickness = 0.5.dp)
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Tops: $topsCount", color = TextMuted, fontSize = 11.sp)
                                    Text("Bottoms: $bottomsCount", color = TextMuted, fontSize = 11.sp)
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Outerwear: $outerCount", color = TextMuted, fontSize = 11.sp)
                                    Text("Shoes: $shoesCount", color = TextMuted, fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))
                        HorizontalDivider(color = AccentGold.copy(alpha = 0.15f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(16.dp))

                        // App Settings
                        Text(
                            text = "APPLICATION SETTINGS",
                            fontFamily = MaterialTheme.typography.labelLarge.fontFamily,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AccentGold,
                            letterSpacing = 1.sp,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Theme switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = TextMuted, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Dark Theme", color = TextLight, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            Switch(
                                checked = isDarkTheme,
                                onCheckedChange = {
                                    isDarkTheme = it
                                    PlatformStorage.saveString("dark_theme", it.toString())
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = ObsidianBg,
                                    checkedTrackColor = AccentGold,
                                    uncheckedThumbColor = AccentGold,
                                    uncheckedTrackColor = CharcoalSurface
                                )
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
                                    Icon(Icons.Default.Settings, contentDescription = "Settings", tint = AccentGold)
                                }
                            },
                            actions = {
                                NotificationBell(
                                    unreadCount = unreadCount,
                                    onClick = { showNotificationInbox = true }
                                )
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
                                Screen.Builder, Screen.Travel
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
                            Screen.Ingest.route -> IngestionScreen()
                            Screen.Wardrobe.route -> WardrobeScreen()
                            Screen.Builder.route -> LookbookScreen()
                            Screen.Travel.route -> TripPlannerScreen()
                            Screen.Resale.route -> ResaleScreen()
                        }
                    }

                    WardrobeEvolutionBanner(
                        notification = activeBanner,
                        onDismiss = { NotificationCenter.dismissBanner() },
                        onAction = { handleNotificationAction(it) },
                        modifier = Modifier.align(Alignment.TopCenter)
                    )
                }

                NotificationInboxSheet(
                    visible = showNotificationInbox,
                    notifications = allNotifications,
                    onDismiss = { showNotificationInbox = false },
                    onNotificationClick = { handleNotificationAction(it) },
                    onMarkAllRead = { NotificationCenter.markAllRead() }
                )
            }
        }
    }
}
