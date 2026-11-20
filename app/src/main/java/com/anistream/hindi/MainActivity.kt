
package com.anistream.hindi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

private const val SUPABASE_URL =
    "https://cyftkgouolxcverswvpv.supabase.co"

private const val SUPABASE_KEY =
    "sb_publishable_h-Alm01lWCWvvlWcW-Uqyg_iAub4QH3"

private val Background = Color(0xFF08090D)
private val SurfaceDark = Color(0xFF14151C)
private val AccentRed = Color(0xFFFF1744)
private val White = Color(0xFFFFFFFF)
private val Gray = Color(0xFF999BA5)

data class Anime(
    val id: String,
    val title: String,
    val posterUrl: String,
    val description: String,
    val animeType: String
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AniStreamApp(
                loadAnime = { fetchAnime() }
            )
        }
    }

    private suspend fun fetchAnime(): List<Anime> =
        withContext(Dispatchers.IO) {

            val endpoint =
                "$SUPABASE_URL/rest/v1/anime" +
                "?select=id,title,description,poster_url,anime_type,created_at" +
                "&is_published=eq.true" +
                "&order=created_at.desc"

            val connection =
                URL(endpoint).openConnection() as HttpURLConnection

            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = 15000
                connection.readTimeout = 15000
                connection.setRequestProperty("apikey", SUPABASE_KEY)
                connection.setRequestProperty("Accept", "application/json")

                val responseCode = connection.responseCode

                if (responseCode !in 200..299) {
                    val error = connection.errorStream
                        ?.bufferedReader()?.use { it.readText() }
                    throw Exception(error ?: "Server error: $responseCode")
                }

                val response = connection.inputStream
                    .bufferedReader().use { it.readText() }

                val jsonArray = JSONArray(response)
                val result = mutableListOf<Anime>()

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)

                    result.add(
                        Anime(
                            id = item.optString("id"),
                            title = item.optString("title", "Untitled Anime"),
                            posterUrl = item.optString("poster_url"),
                            description = item.optString("description"),
                            animeType = item.optString("anime_type")
                        )
                    )
                }

                result

            } finally {
                connection.disconnect()
            }
        }
}

@Composable
fun AniStreamApp(loadAnime: suspend () -> List<Anime>) {

    var selectedTab by remember { mutableIntStateOf(0) }
    var animeList by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }

    suspend fun refreshAnime() {
        loading = true
        errorMessage = null

        try {
            animeList = loadAnime()
        } catch (e: Exception) {
            errorMessage = e.message ?: "Unable to load anime"
        }

        loading = false
    }

    LaunchedEffect(Unit) {
        refreshAnime()
    }

    MaterialTheme {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = White
                ) {
                    val tabs = listOf("Home", "Search", "My List", "Profile")

                    tabs.forEachIndexed { index, title ->
                        NavigationBarItem(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            icon = {
                                Text(
                                    listOf("⌂", "⌕", "♡", "♙")[index],
                                    fontSize = 23.sp
                                )
                            },
                            label = { Text(title, fontSize = 10.sp) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = AccentRed,
                                selectedTextColor = AccentRed,
                                indicatorColor = Color(0xFF30202A),
                                unselectedIconColor = Gray,
                                unselectedTextColor = Gray
                            )
                        )
                    }
                }
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Background)
                    .padding(padding)
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 18.dp, bottom = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Ani",
                        color = White,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "Stream",
                        color = AccentRed,
                        fontSize = 27.sp,
                        fontWeight = FontWeight.ExtraBold
                    )

                    Spacer(Modifier.weight(1f))

                    Text("🔔", fontSize = 23.sp)
                }

                when (selectedTab) {
                    0 -> HomeScreen(
                        animeList = animeList,
                        loading = loading,
                        errorMessage = errorMessage,
                        onRetry = {
                            // Refresh handled by the screen callback below
                        }
                    )

                    1 -> SearchScreen(
                        animeList = animeList,
                        searchText = searchText,
                        onSearchChange = { searchText = it }
                    )

                    2 -> SimpleScreen(
                        "My List",
                        "Your favourite anime will appear here"
                    )

                    3 -> SimpleScreen(
                        "My Profile",
                        "Login to access your profile"
                    )
                }
            }
        }
    }
}

@Composable
fun HomeScreen(
    animeList: List<Anime>,
    loading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit
) {
    val movies = animeList.filter {
        it.animeType.equals("Movie", ignoreCase = true)
    }

    val series = animeList.filter {
        !it.animeType.equals("Movie", ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "WELCOME TO ANISTREAM",
            color = AccentRed,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(8.dp))

        Text(
            "Anime World Awaits You.",
            color = White,
            fontSize = 23.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(18.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .background(SurfaceDark, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("⌕  Search anime...", color = Gray, fontSize = 15.sp)
        }

        Spacer(Modifier.height(22.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .background(Color(0xFF30101B), RoundedCornerShape(18.dp))
                .padding(18.dp),
            contentAlignment = Alignment.BottomStart
        ) {
            Column {
                Text(
                    "ANIME STREAMING",
                    color = AccentRed,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Your Anime.\nYour World.",
                    color = White,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(8.dp))

                Text(
                    "Discover your next adventure.",
                    color = Gray,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        if (loading) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentRed)
            }
        } else if (errorMessage != null) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Anime load nahi ho paye", color = White)
                Spacer(Modifier.height(6.dp))
                Text(errorMessage, color = Gray, fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Refresh ke liye app dobara kholo.",
                    color = AccentRed,
                    fontSize = 13.sp
                )
            }
        } else if (animeList.isEmpty()) {
            Text(
                "Abhi koi published anime nahi hai.",
                color = Gray
            )
        } else {
            AnimeSection("✨ Recently Added", animeList)
            AnimeSection("🎬 Anime Series", series)

            if (movies.isNotEmpty()) {
                AnimeSection("🍿 Anime Movies", movies)
            }
        }

        Spacer(Modifier.height(20.dp))
    }
}

@Composable
fun AnimeSection(title: String, animeList: List<Anime>) {
    if (animeList.isEmpty()) return

    Column {
        Text(
            title,
            color = White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            animeList.forEach { anime ->
                AnimeCard(anime)
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AnimeCard(anime: Anime) {
    Column(
        modifier = Modifier.width(125.dp)
    ) {
        AsyncImage(
            model = anime.posterUrl,
            contentDescription = anime.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(165.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark),
            contentScale = ContentScale.Crop
        )

        Spacer(Modifier.height(7.dp))

        Text(
            anime.title,
            color = White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2
        )
    }
}

@Composable
fun SearchScreen(
    animeList: List<Anime>,
    searchText: String,
    onSearchChange: (String) -> Unit
) {
    val filtered = animeList.filter {
        it.title.contains(searchText, ignoreCase = true)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Text(
            "Search Anime",
            color = White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(14.dp))

        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search anime...", color = Gray) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White,
                unfocusedTextColor = White,
                focusedBorderColor = AccentRed,
                unfocusedBorderColor = Gray,
                cursorColor = AccentRed
            )
        )

        Spacer(Modifier.height(16.dp))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(filtered) { anime ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = anime.posterUrl,
                        contentDescription = anime.title,
                        modifier = Modifier
                            .size(width = 75.dp, height = 100.dp)
                            .clip(RoundedCornerShape(9.dp))
                            .background(SurfaceDark),
                        contentScale = ContentScale.Crop
                    )

                    Spacer(Modifier.width(12.dp))

                    Text(
                        anime.title,
                        color = White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun SimpleScreen(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            title,
            color = White,
            fontSize = 25.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))

        Text(subtitle, color = Gray, fontSize = 14.sp)
    }
}
