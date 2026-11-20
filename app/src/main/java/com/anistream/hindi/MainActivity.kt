
package com.anistream.hindi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
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
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val SUPABASE_URL =
    "https://cyftkgouolxcverswvpv.supabase.co"

private const val SUPABASE_KEY =
    "sb_publishable_h-Alm01lWCWvvlWcW-Uqyg_iAub4QH3"

private val Background = Color(0xFF08090D)
private val SurfaceDark = Color(0xFF14151C)
private val AccentRed = Color(0xFFFF1744)
private val White = Color.White
private val Gray = Color(0xFF999BA5)

data class Anime(
    val id: String,
    val title: String,
    val posterUrl: String,
    val description: String,
    val animeType: String
)

data class CharacterInfo(
    val name: String,
    val image: String,
    val role: String
)

data class AnimeDetails(
    val englishTitle: String,
    val japaneseTitle: String,
    val description: String,
    val banner: String,
    val genres: List<String>,
    val year: String,
    val episodes: String,
    val rating: String,
    val status: String,
    val studio: String,
    val characters: List<CharacterInfo>
)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AniStreamApp()
        }
    }
}

private fun request(
    url: String,
    method: String = "GET",
    body: String? = null,
    supabase: Boolean = false
): String {
    val connection = URL(url).openConnection() as HttpURLConnection

    try {
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/json")

        if (supabase) {
            connection.setRequestProperty("apikey", SUPABASE_KEY)
        }

        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty(
                "Content-Type", "application/json"
            )
            connection.outputStream.use {
                it.write(body.toByteArray(Charsets.UTF_8))
            }
        }

        val code = connection.responseCode
        val stream = if (code in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        }

        val result = stream?.bufferedReader()?.use {
            it.readText()
        } ?: ""

        if (code !in 200..299) {
            throw Exception("Server error $code: $result")
        }

        return result
    } finally {
        connection.disconnect()
    }
}

private suspend fun fetchAnime(): List<Anime> =
    withContext(Dispatchers.IO) {

        val endpoint =
            "$SUPABASE_URL/rest/v1/anime" +
            "?select=id,title,description,poster_url,anime_type,created_at" +
            "&is_published=eq.true&order=created_at.desc"

        val response = request(endpoint, supabase = true)
        val array = org.json.JSONArray(response)
        val list = mutableListOf<Anime>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)

            list.add(
                Anime(
                    id = item.optString("id"),
                    title = item.optString("title"),
                    posterUrl = item.optString("poster_url"),
                    description = item.optString("description"),
                    animeType = item.optString("anime_type")
                )
            )
        }

        list
    }

private suspend fun fetchAnimeDetails(
    title: String
): AnimeDetails = withContext(Dispatchers.IO) {

    val query = """
        query (${'$'}search: String) {
          Media(search: ${'$'}search, type: ANIME) {
            title {
              english
              romaji
              native
            }
            description(asHtml: false)
            bannerImage
            genres
            seasonYear
            episodes
            averageScore
            status
            studios(isMain: true) {
              nodes {
                name
              }
            }
            characters(sort: ROLE, perPage: 12) {
              edges {
                role
                node {
                  name {
                    full
                  }
                  image {
                    large
                  }
                }
              }
            }
          }
        }
    """.trimIndent()

    val body = JSONObject().apply {
        put("query", query)
        put("variables", JSONObject().put("search", title))
    }

    val response = request(
        url = "https://graphql.anilist.co",
        method = "POST",
        body = body.toString()
    )

    val media = JSONObject(response)
        .getJSONObject("data")
        .optJSONObject("Media")
        ?: throw Exception("Anime details not found")

    val titles = media.optJSONObject("title")
    val studioNodes = media.optJSONObject("studios")
        ?.optJSONArray("nodes")

    val studio = if (studioNodes != null &&
        studioNodes.length() > 0
    ) {
        studioNodes.getJSONObject(0).optString("name")
    } else {
        "Not available"
    }

    val characterList = mutableListOf<CharacterInfo>()
    val edges = media.optJSONObject("characters")
        ?.optJSONArray("edges")

    if (edges != null) {
        for (i in 0 until edges.length()) {
            val edge = edges.getJSONObject(i)
            val node = edge.getJSONObject("node")
            val name = node.optJSONObject("name")
                ?.optString("full").orEmpty()

            val image = node.optJSONObject("image")
                ?.optString("large").orEmpty()

            characterList.add(
                CharacterInfo(
                    name = name,
                    image = image,
                    role = edge.optString("role")
                )
            )
        }
    }

    AnimeDetails(
        englishTitle = titles?.optString("english")
            ?.takeIf { !it.isNullOrBlank() }
            ?: titles?.optString("romaji").orEmpty(),

        japaneseTitle = titles?.optString("native").orEmpty(),

        description = media.optString("description")
            .takeIf { it != "null" && it.isNotBlank() }
            ?: "Description not available.",

        banner = media.optString("bannerImage")
            .takeIf { it != "null" }.orEmpty(),

        genres = media.optJSONArray("genres")?.let { array ->
            (0 until array.length()).map { array.getString(it) }
        } ?: emptyList(),

        year = media.optString("seasonYear")
            .takeIf { it != "null" } ?: "Unknown",

        episodes = media.optString("episodes")
            .takeIf { it != "null" } ?: "Unknown",

        rating = media.optString("averageScore")
            .takeIf { it != "null" }?.let { "$it/100" }
            ?: "Not rated",

        status = media.optString("status")
            .replace("_", " "),

        studio = studio,
        characters = characterList
    )
}

@Composable
fun AniStreamApp() {
    var selectedTab by remember { mutableIntStateOf(0) }
    var animeList by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var searchText by remember { mutableStateOf("") }
    var selectedAnime by remember { mutableStateOf<Anime?>(null) }

    LaunchedEffect(Unit) {
        try {
            animeList = fetchAnime()
            error = null
        } catch (e: Exception) {
            error = e.message ?: "Unable to load anime"
        }
        loading = false
    }

    MaterialTheme {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                if (selectedAnime == null) {
                    NavigationBar(containerColor = SurfaceDark) {
                        val tabs = listOf(
                            "Home", "Search", "My List", "Profile"
                        )

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
            }
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Background)
                    .padding(padding)
            ) {

                if (selectedAnime == null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .padding(top = 18.dp, bottom = 16.dp),
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
                            animeList, loading, error
                        ) { selectedAnime = it }

                        1 -> SearchScreen(
                            animeList,
                            searchText,
                            { searchText = it }
                        ) { selectedAnime = it }

                        2 -> SimpleScreen(
                            "My List",
                            "Your favourite anime will appear here"
                        )

                        3 -> SimpleScreen(
                            "My Profile",
                            "Login to access your profile"
                        )
                    }
                } else {
                    AnimeDetailsScreen(
                        anime = selectedAnime!!,
                        onBack = { selectedAnime = null }
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
    error: String?,
    onAnimeClick: (Anime) -> Unit
) {
    val movies = animeList.filter {
        it.animeType.equals("Movie", true)
    }
    val series = animeList.filter {
        !it.animeType.equals("Movie", true)
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
            Text("⌕  Search anime...", color = Gray)
        }

        Spacer(Modifier.height(22.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
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
                modifier = Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentRed)
            }
        } else if (error != null) {
            Text("Anime load error: $error", color = AccentRed)
        } else if (animeList.isEmpty()) {
            Text("No published anime found.", color = Gray)
        } else {
            AnimeSection(
                "✨ Recently Added", animeList, onAnimeClick
            )
            AnimeSection(
                "🎬 Anime Series", series, onAnimeClick
            )
            if (movies.isNotEmpty()) {
                AnimeSection(
                    "🍿 Anime Movies", movies, onAnimeClick
                )
            }
        }
    }
}

@Composable
fun AnimeSection(
    title: String,
    animeList: List<Anime>,
    onAnimeClick: (Anime) -> Unit
) {
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
                AnimeCard(anime) { onAnimeClick(anime) }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
fun AnimeCard(anime: Anime, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(125.dp)
            .clickable { onClick() }
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
    onSearchChange: (String) -> Unit,
    onAnimeClick: (Anime) -> Unit
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

        Column(
            modifier = Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            filtered.forEach { anime ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAnimeClick(anime) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
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
fun AnimeDetailsScreen(
    anime: Anime,
    onBack: () -> Unit
) {
    var details by remember(anime.id) {
        mutableStateOf<AnimeDetails?>(null)
    }
    var loading by remember(anime.id) {
        mutableStateOf(true)
    }
    var error by remember(anime.id) {
        mutableStateOf<String?>(null)
    }

    LaunchedEffect(anime.id) {
        try {
            details = fetchAnimeDetails(anime.title)
        } catch (e: Exception) {
            error = e.message ?: "Details not available"
        }
        loading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            "‹  Back",
            color = White,
            fontSize = 18.sp,
            modifier = Modifier
                .clickable { onBack() }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        AsyncImage(
            model = details?.banner?.takeIf { it.isNotBlank() }
                ?: anime.posterUrl,
            contentDescription = anime.title,
            modifier = Modifier
                .fillMaxWidth()
                .height(175.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark),
            contentScale = ContentScale.Crop
        )

        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = anime.posterUrl,
                contentDescription = anime.title,
                modifier = Modifier
                    .width(112.dp)
                    .height(155.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark),
                contentScale = ContentScale.Crop
            )

            Spacer(Modifier.width(14.dp))

            Column {
                Text(
                    details?.englishTitle?.takeIf { it.isNotBlank() }
                        ?: anime.title,
                    color = White,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.ExtraBold
                )

                Spacer(Modifier.height(7.dp))

                Text(
                    details?.japaneseTitle.orEmpty(),
                    color = Gray,
                    fontSize = 12.sp
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    "⭐ ${details?.rating ?: "Loading..."}",
                    color = AccentRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    "Year: ${details?.year ?: "Loading..."}",
                    color = Gray,
                    fontSize = 12.sp
                )

                Text(
                    "Episodes: ${details?.episodes ?: "Loading..."}",
                    color = Gray,
                    fontSize = 12.sp
                )
            }
        }

        if (loading) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentRed)
            }
        } else if (error != null) {
            Text(
                "Extra details unavailable: $error",
                color = Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        } else {
            details?.let { info ->

                DetailSection("Story") {
                    Text(
                        info.description.replace(
                            Regex("<[^>]*>"), ""
                        ),
                        color = Gray,
                        fontSize = 14.sp
                    )
                }

                DetailSection("Anime Information") {
                    InfoLine("Release", info.year)
                    InfoLine("Status", info.status)
                    InfoLine("Studio", info.studio)
                    InfoLine("Episodes", info.episodes)
                    InfoLine("Rating", info.rating)
                }

                DetailSection("Genres") {
                    Text(
                        info.genres.joinToString("  •  "),
                        color = AccentRed,
                        fontSize = 13.sp
                    )
                }

                if (info.characters.isNotEmpty()) {
                    DetailSection("Characters") {
                        Row(
                            modifier = Modifier.horizontalScroll(
                                rememberScrollState()
                            ),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            info.characters.forEach { character ->
                                Column(
                                    modifier = Modifier.width(100.dp)
                                ) {
                                    AsyncImage(
                                        model = character.image,
                                        contentDescription = character.name,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(125.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(SurfaceDark),
                                        contentScale = ContentScale.Crop
                                    )

                                    Spacer(Modifier.height(6.dp))

                                    Text(
                                        character.name,
                                        color = White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2
                                    )

                                    Text(
                                        character.role,
                                        color = Gray,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        DetailSection("Watch Anime") {
            Text(
                "Season aur Episode system agle update mein connect hoga.",
                color = Gray,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            title,
            color = White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(10.dp))
        content()
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
    ) {
        Text("$label: ", color = Gray, fontSize = 13.sp)
        Text(value, color = White, fontSize = 13.sp)
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
