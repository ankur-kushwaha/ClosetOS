package com.closetos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.closetos.app.data.model.LookbookCollection
import com.closetos.app.data.model.Outfit
import com.closetos.app.data.repository.ClosetRepository
import com.closetos.app.*
import com.closetos.app.ui.components.*
import com.closetos.app.ui.theme.*

private enum class LookbookSection {
    Recommended, Collections, RecentlyWorn, Saved, AiGenerated, Trending, Search
}

private sealed class LookbookNav {
    data object Landing : LookbookNav()
    data class Section(val type: LookbookSection, val title: String) : LookbookNav()
    data class Collection(val collection: LookbookCollection) : LookbookNav()
    data class OutfitDetail(val outfitId: String) : LookbookNav()
    data object Search : LookbookNav()
    data object Builder : LookbookNav()
}

private fun timeGreeting(): String {
    val hour = ((getEpochTimeMillis() / 3_600_000) % 24).toInt()
    return when (hour) {
        in 5..11 -> "Good Morning"
        in 12..16 -> "Good Afternoon"
        in 17..21 -> "Good Evening"
        else -> "Good Night"
    }
}

@Composable
fun LookbookScreen() {
    var nav by remember { mutableStateOf<LookbookNav>(LookbookNav.Landing) }
  val outfits by ClosetRepository.lookbookOutfits.collectAsState()

  LaunchedEffect(outfits.isEmpty()) {
        if (outfits.isEmpty()) {
            ClosetRepository.seedLookbookData()
        }
    }

    when (val current = nav) {
        LookbookNav.Landing -> LookbookLanding(
            onSection = { type, title -> nav = LookbookNav.Section(type, title) },
            onCollection = { nav = LookbookNav.Collection(it) },
            onOutfit = { nav = LookbookNav.OutfitDetail(it.id) },
            onSearch = { nav = LookbookNav.Search },
            onGenerate = { nav = LookbookNav.Builder }
        )
        is LookbookNav.Section -> LookbookSectionScreen(
            section = current.type,
            title = current.title,
            onBack = { nav = LookbookNav.Landing },
            onOutfit = { nav = LookbookNav.OutfitDetail(it.id) }
        )
        is LookbookNav.Collection -> LookbookCollectionScreen(
            collection = current.collection,
            onBack = { nav = LookbookNav.Landing },
            onOutfit = { nav = LookbookNav.OutfitDetail(it.id) }
        )
        is LookbookNav.OutfitDetail -> {
            val outfit = ClosetRepository.getOutfitById(current.outfitId)
            if (outfit != null) {
                OutfitDetailScreen(
                    outfit = outfit,
                    onBack = { nav = LookbookNav.Landing },
                    onEdit = { nav = LookbookNav.Builder }
                )
            } else {
                LaunchedEffect(Unit) { nav = LookbookNav.Landing }
            }
        }
        LookbookNav.Search -> LookbookSearchScreen(
            onBack = { nav = LookbookNav.Landing },
            onOutfit = { nav = LookbookNav.OutfitDetail(it.id) }
        )
        LookbookNav.Builder -> OutfitBuilderScreen(onBack = { nav = LookbookNav.Landing })
    }
}

@Composable
private fun LookbookLanding(
    onSection: (LookbookSection, String) -> Unit,
    onCollection: (LookbookCollection) -> Unit,
    onOutfit: (Outfit) -> Unit,
    onSearch: () -> Unit,
    onGenerate: () -> Unit
) {
    val collections by ClosetRepository.collections.collectAsState()
    var temperatureC by remember { mutableStateOf(24f) }

    LaunchedEffect(Unit) {
        val weather = fetchWeatherTemp()
        temperatureC = (weather.first - 32f) * 5f / 9f
    }

    val outfits by ClosetRepository.lookbookOutfits.collectAsState()
    val recommended = remember(outfits) { ClosetRepository.getRecommendedOutfits(3) }
    val recent = remember(outfits) { ClosetRepository.getRecentlyWornOutfits(4) }
    val favorites = remember(outfits) { ClosetRepository.getFavoriteOutfits(4) }
    val weekOccasions = listOf("Office", "Weekend", "Dinner", "Travel")
    val featuredCollections = collections.take(6)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))
        Column(modifier = Modifier.padding(horizontal = 16.dp)) {
            Text(
                text = "${timeGreeting()} Ankur",
                fontFamily = PlayfairFont,
                fontWeight = FontWeight.Bold,
                fontSize = 26.sp,
                color = TextLight
            )
            Text(
                text = "Explore your wardrobe",
                fontFamily = OutfitFont,
                fontSize = 14.sp,
                color = TextMuted,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
            )
        }

        LookbookSearchBar(query = "", onClick = onSearch)

        Spacer(modifier = Modifier.height(20.dp))

        LookbookSectionHeader(
            title = "Recommended For You",
            emoji = "⭐",
            onSeeAll = { onSection(LookbookSection.Recommended, "Recommended") }
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(recommended, key = { it.id }) { outfit ->
                OutfitCard(
                    outfit = outfit,
                    temperatureC = temperatureC,
                    compact = true,
                    onClick = { onOutfit(outfit) },
                    onFavorite = { ClosetRepository.toggleOutfitFavorite(outfit.id) },
                    onWearToday = {
                        ClosetRepository.wearOutfitToday(outfit.id)
                        showToast("Logged as today's outfit")
                    },
                    onTryOn = { showToast("Opening virtual try-on…") },
                    onSave = { ClosetRepository.toggleOutfitSaved(outfit.id) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LookbookSectionHeader(title = "This Week", emoji = "📅")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            weekOccasions.forEach { occasion ->
                TagChip(
                    text = occasion,
                    onClick = { onSection(LookbookSection.Recommended, occasion) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LookbookSectionHeader(
            title = "Collections",
            onSeeAll = { onSection(LookbookSection.Collections, "Collections") }
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(featuredCollections, key = { it.id }) { collection ->
                CollectionPlaylistCard(
                    collection = collection,
                    outfitCount = collection.outfitIds.size,
                    onClick = { onCollection(collection) }
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        LookbookSectionHeader(
            title = "Recent",
            onSeeAll = { onSection(LookbookSection.RecentlyWorn, "Recently Worn") }
        )
        if (recent.isEmpty()) {
            EmptyLookbookRow("No recent outfits yet — wear a look today")
        } else {
            OutfitRow(recent, temperatureC, onOutfit)
        }

        Spacer(modifier = Modifier.height(16.dp))

        LookbookSectionHeader(
            title = "Favorites",
            onSeeAll = { onSection(LookbookSection.Saved, "Saved") }
        )
        if (favorites.isEmpty()) {
            EmptyLookbookRow("Tap ❤️ on any outfit to save favorites")
        } else {
            OutfitRow(favorites, temperatureC, onOutfit)
        }

        Spacer(modifier = Modifier.height(24.dp))

        ElegantButton(
            text = "Generate New Outfit",
            onClick = onGenerate,
            icon = Icons.Default.AutoFixHigh,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )
    }
}

@Composable
private fun EmptyLookbookRow(message: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(GlassOverlay)
            .border(0.5.dp, GlassBorder, RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
    ) {
        Text(message, fontFamily = OutfitFont, fontSize = 12.sp, color = TextMuted, textAlign = TextAlign.Center)
    }
}

@Composable
private fun OutfitRow(
    outfits: List<Outfit>,
    temperatureC: Float,
    onOutfit: (Outfit) -> Unit
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(outfits, key = { it.id }) { outfit ->
            OutfitCard(
                outfit = outfit,
                temperatureC = temperatureC,
                compact = true,
                onClick = { onOutfit(outfit) },
                onFavorite = { ClosetRepository.toggleOutfitFavorite(outfit.id) },
                onWearToday = { ClosetRepository.wearOutfitToday(outfit.id) },
                onTryOn = { showToast("Opening virtual try-on…") },
                onSave = { ClosetRepository.toggleOutfitSaved(outfit.id) }
            )
        }
    }
}

@Composable
private fun LookbookSectionScreen(
    section: LookbookSection,
    title: String,
    onBack: () -> Unit,
    onOutfit: (Outfit) -> Unit
) {
  val outfits = remember(section, title) {
        when (section) {
            LookbookSection.Recommended -> {
                if (title in listOf("Office", "Weekend", "Dinner", "Travel")) {
                    ClosetRepository.getOutfitsForOccasion(title, 20)
                } else {
                    ClosetRepository.getRecommendedOutfits(20)
                }
            }
            LookbookSection.Collections -> emptyList()
            LookbookSection.RecentlyWorn -> ClosetRepository.getRecentlyWornOutfits(20)
            LookbookSection.Saved -> ClosetRepository.getSavedOutfits(20)
            LookbookSection.AiGenerated -> ClosetRepository.getAiGeneratedOutfits(20)
            LookbookSection.Trending -> ClosetRepository.getTrendingOutfits(20)
            LookbookSection.Search -> emptyList()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        LookbookTopBar(title = title, onBack = onBack)
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(outfits, key = { it.id }) { outfit ->
                OutfitCard(
                    outfit = outfit,
                    onClick = { onOutfit(outfit) },
                    onFavorite = { ClosetRepository.toggleOutfitFavorite(outfit.id) },
                    onWearToday = { ClosetRepository.wearOutfitToday(outfit.id) },
                    onTryOn = { showToast("Opening virtual try-on…") },
                    onSave = { ClosetRepository.toggleOutfitSaved(outfit.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LookbookCollectionScreen(
    collection: LookbookCollection,
    onBack: () -> Unit,
    onOutfit: (Outfit) -> Unit
) {
    val outfits = remember(collection.id) { ClosetRepository.getOutfitsForCollection(collection.id) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        LookbookTopBar(title = collection.name, onBack = onBack)
        Text(
            text = "${outfits.size} looks · Spotify-style playlist",
            fontFamily = OutfitFont,
            fontSize = 13.sp,
            color = TextMuted,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(outfits, key = { it.id }) { outfit ->
                OutfitCard(
                    outfit = outfit,
                    onClick = { onOutfit(outfit) },
                    onFavorite = { ClosetRepository.toggleOutfitFavorite(outfit.id) },
                    onWearToday = { ClosetRepository.wearOutfitToday(outfit.id) },
                    onTryOn = { showToast("Opening virtual try-on…") },
                    onSave = { ClosetRepository.toggleOutfitSaved(outfit.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun LookbookSearchScreen(
    onBack: () -> Unit,
    onOutfit: (Outfit) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val results = remember(query) { ClosetRepository.searchOutfits(query) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
    ) {
        LookbookTopBar(title = "Search", onBack = onBack)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search outfits", color = TextMuted) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = AccentGold) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AccentGold,
                unfocusedBorderColor = GlassBorder,
                focusedTextColor = TextLight,
                unfocusedTextColor = TextLight,
                cursorColor = AccentGold
            ),
            singleLine = true
        )
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            items(results, key = { it.id }) { outfit ->
                OutfitCard(
                    outfit = outfit,
                    onClick = { onOutfit(outfit) },
                    onFavorite = { ClosetRepository.toggleOutfitFavorite(outfit.id) },
                    onWearToday = { ClosetRepository.wearOutfitToday(outfit.id) },
                    onTryOn = { showToast("Opening virtual try-on…") },
                    onSave = { ClosetRepository.toggleOutfitSaved(outfit.id) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
fun OutfitDetailScreen(
    outfit: Outfit,
    onBack: () -> Unit,
    onEdit: () -> Unit
) {
    val liveOutfit = ClosetRepository.getOutfitById(outfit.id) ?: outfit
    var temperatureC by remember { mutableStateOf(liveOutfit.temperatureC) }

    LaunchedEffect(Unit) {
        val weather = fetchWeatherTemp()
        temperatureC = (weather.first - 32f) * 5f / 9f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .verticalScroll(rememberScrollState())
    ) {
        LookbookTopBar(title = liveOutfit.name, onBack = onBack)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .padding(16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF1A1A22), Color(0xFF0E0E12)))
                )
                .border(0.5.dp, GlassBorder, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center
        ) {
            OutfitPreviewStack(outfit = liveOutfit, modifier = Modifier.fillMaxSize().padding(24.dp))
        }

        val orderedGarments = liveOutfit.garments.sortedBy {
            when (it.category) {
                "Top" -> 0
                "Bottom" -> 1
                "Shoes" -> 2
                else -> 3
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            orderedGarments.forEachIndexed { index, garment ->
                GarmentThumbnail(
                    garment = garment,
                    modifier = Modifier
                        .fillMaxWidth(0.55f)
                        .aspectRatio(0.85f),
                    cornerRadius = 12
                )
                Text(
                    text = "${garment.colorName} ${garment.subcategory}",
                    fontFamily = OutfitFont,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = TextLight,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                if (index < orderedGarments.lastIndex) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AccentGold.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        Divider(
            color = GoldBorder,
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp)
        )

        DetailInfoRow("Weather", "${temperatureC.toInt()}°C")
        DetailInfoRow("Best For", liveOutfit.bestFor.joinToString(" · "))

        if (liveOutfit.aiNote.isNotBlank()) {
            Spacer(modifier = Modifier.height(16.dp))
            Text("AI Notes", fontFamily = PlayfairFont, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = AccentGold, modifier = Modifier.padding(horizontal = 24.dp))
            Text(
                text = "\"${liveOutfit.aiNote}\"",
                fontFamily = OutfitFont,
                fontSize = 14.sp,
                color = TextLight,
                lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ElegantButton(
                text = "Wear",
                onClick = {
                    ClosetRepository.wearOutfitToday(liveOutfit.id)
                    showToast("Logged as today's outfit")
                },
                modifier = Modifier.weight(1f),
                icon = Icons.Default.Checkroom
            )
            ElegantButton(
                text = "Try On",
                onClick = { showToast("Opening virtual try-on…") },
                modifier = Modifier.weight(1f),
                isSecondary = true,
                icon = Icons.Default.AutoFixHigh
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf(
                Triple("Edit", Icons.Default.Edit, onEdit),
                Triple("Duplicate", Icons.Default.ContentCopy) { showToast("Outfit duplicated") },
                Triple("Share", Icons.Default.Share) { showToast("Share link copied") }
            ).forEach { (label, icon, action) ->
                OutlinedButton(
                    onClick = action,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AccentGold),
                    border = ButtonDefaults.outlinedButtonBorder.copy(brush = Brush.linearGradient(listOf(AccentGold, AccentGoldMuted)))
                ) {
                    Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(label, fontFamily = OutfitFont, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun DetailInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontFamily = OutfitFont, fontSize = 13.sp, color = TextMuted)
        Text(value, fontFamily = OutfitFont, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = TextLight)
    }
}

@Composable
private fun LookbookTopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AccentGold)
        }
        Text(
            text = title,
            fontFamily = PlayfairFont,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            color = TextLight
        )
    }
}
