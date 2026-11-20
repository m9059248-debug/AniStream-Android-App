
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
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
private val White = Color.White
private val Gray = Color(0xFF999BA5)

data class Anime(
    val id: String,
    val title: String,
    val posterUrl: String,
    val description: String,
    val animeType: String
)

data class AnimeSeason(
    val id: String,
    val animeId: String,
    val seasonNumber: Int,
    val title: String
)

data class AnimeEpisode(
    val id: String,
    val episodeNumber: Int,
    val title: String,
    val description: String
)

data class VideoServer(
    val id: String,
    val episodeId: String,
    val serverName: String,
    val videoUrl: String,
    val quality: String,
    val language: String
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
        setContent { AniStreamApp() }
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

        val array = JSONArray(request(endpoint, supabase = true))
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

private suspend fun fetchSeasons(animeId: String): List<AnimeSeason> =
    withContext(Dispatchers.IO) {
        val endpoint =
            "$SUPABASE_URL/rest/v1/seasons" +
            "?select=id,anime_id,season_number,title" +
            "&anime_id=eq.$animeId&order=season_number.asc"

        val array = JSONArray(request(endpoint, supabase = true))
        val list = mutableListOf<AnimeSeason>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            list.add(
                AnimeSeason(
                    id = item.optString("id"),
                    animeId = item.optString("anime_id"),
                    seasonNumber = item.optInt("season_number"),
                    title = item.optString("title")
                )
            )
        }
        list
    }

private suspend fun fetchEpisodes(seasonId: String): List<AnimeEpisode> =
    withContext(Dispatchers.IO) {
        val endpoint =
            "$SUPABASE_URL/rest/v1/episodes" +
            "?select=id,episode_number,title,description" +
            "&season_id=eq.$seasonId&is_published=eq.true" +
            "&order=episode_number.asc"

        val array = JSONArray(request(endpoint, supabase = true))
        val list = mutableListOf<AnimeEpisode>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            list.add(
                AnimeEpisode(
                    id = item.optString("id"),
                    episodeNumber = item.optInt("episode_number"),
                    title = item.optString("title"),
                    description = item.optString("description")
                )
            )
        }
        list
    }

private suspend fun fetchVideoServers(episodeId: String): List<VideoServer> =
    withContext(Dispatchers.IO) {
        val endpoint =
            "$SUPABASE_URL/rest/v1/video_servers" +
            "?select=id,episode_id,server_name,video_url,quality,language" +
            "&episode_id=eq.$episodeId"

        val array = JSONArray(request(endpoint, supabase = true))
        val list = mutableListOf<VideoServer>()

        for (i in 0 until array.length()) {
            val item = array.getJSONObject(i)
            val videoUrl = item.optString("video_url")

            if (videoUrl.isNotBlank() && videoUrl != "null") {
                list.add(
                    VideoServer(
                        id = item.optString("id"),
                        episodeId = item.optString("episode_id"),
                        serverName = item.optString("server_name"),
                        videoUrl = videoUrl,
                        quality = item.optString("quality"),
                        language = item.optString("language")
                    )
                )
            }
        }
        list
    }

private suspend fun fetchAnimeDetails(title: String): AnimeDetails =
    withContext(Dispatchers.IO) {
        val query = """
            query (${'$'}search: String) {
              Media(search: ${'$'}search, type: ANIME) {
                title { english romaji native }
                description(asHtml: false)
                bannerImage
                genres
                seasonYear
                episodes
                averageScore
                status
                studios(isMain: true) { nodes { name } }
                characters(sort: ROLE, perPage: 12) {
                  edges {
                    role
                    node {
                      name { full }
                      image { large }
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
            "https://graphql.anilist.co",
            "POST",
            body.toString()
        )

        val media = JSONObject(response)
            .getJSONObject("data")
            .optJSONObject("Media")
            ?: throw Exception("Anime details not found")

        val titles = media.optJSONObject("title")
        val studioNodes = media.optJSONObject("studios")
            ?.optJSONArray("nodes")

        val studio = if (studioNodes != null && studioNodes.length() > 0) {
            studioNodes.getJSONObject(0).optString("name")
        } else {
            "Not available"
        }

        val characters = mutableListOf<CharacterInfo>()
        val edges = media.optJSONObject("characters")?.optJSONArray("edges")

        if (edges != null) {
            for (i in 0 until edges.length()) {
                val edge = edges.getJSONObject(i)
                val node = edge.getJSONObject("node")
                characters.add(
                    CharacterInfo(
                        name = node.optJSONObject("name")
                            ?.optString("full").orEmpty(),
                        image = node.optJSONObject("image")
                            ?.optString("large").orEmpty(),
                        role = edge.optString("role")
                    )
                )
            }
        }

        AnimeDetails(
            englishTitle = titles?.optString("english")
                ?.takeIf { it.isNotBlank() && it != "null" }
                ?: titles?.optString("romaji").orEmpty(),
            japaneseTitle = titles?.optString("native").orEmpty(),
            description = media.optString("description")
                .takeIf { it.isNotBlank() && it != "null" }
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
            status = media.optString("status").replace("_", " "),
            studio = studio,
            characters = characters
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
                        listOf("Home", "Search", "My List", "Profile")
                            .forEachIndexed { index, title ->
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
                Modifier.fillMaxSize()
                    .background(Background)
                    .padding(padding)
            ) {
                if (selectedAnime == null) {
                    Row(
                        Modifier.fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 18.dp),
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
                        0 -> HomeScreen(animeList, loading, error) {
                            selectedAnime = it
                        }
                        1 -> SearchScreen(
                            animeList, searchText, { searchText = it }
                        ) {
                            selectedAnime = it
                        }
                        2 -> SimpleScreen(
                            "My List", "Your favourite anime will appear here"
                        )
                        3 -> SimpleScreen(
                            "My Profile", "Login to access your profile"
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
        Modifier.fillMaxSize()
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
            Modifier.fillMaxWidth().height(48.dp)
                .background(SurfaceDark, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text("⌕  Search anime...", color = Gray)
        }

        Spacer(Modifier.height(22.dp))

        Box(
            Modifier.fillMaxWidth().height(170.dp)
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
                Modifier.fillMaxWidth().height(120.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentRed)
            }
        } else if (error != null) {
            Text("Anime load error: $error", color = AccentRed)
        } else if (animeList.isEmpty()) {
            Text("No published anime found.", color = Gray)
        } else {
            AnimeSection("✨ Recently Added", animeList, onAnimeClick)
            AnimeSection("🎬 Anime Series", series, onAnimeClick)
            if (movies.isNotEmpty()) {
                AnimeSection("🍿 Anime Movies", movies, onAnimeClick)
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
            Modifier.horizontalScroll(rememberScrollState()),
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
    Column(Modifier.width(125.dp).clickable { onClick() }) {
        AsyncImage(
            model = anime.posterUrl,
            contentDescription = anime.title,
            modifier = Modifier.fillMaxWidth().height(165.dp)
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

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
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
            Modifier.verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            filtered.forEach { anime ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onAnimeClick(anime)
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = anime.posterUrl,
                        contentDescription = anime.title,
                        modifier = Modifier.size(75.dp, 100.dp)
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
fun AnimeDetailsScreen(anime: Anime, onBack: () -> Unit) {
    var details by remember(anime.id) {
        mutableStateOf<AnimeDetails?>(null)
    }
    var detailsLoading by remember(anime.id) { mutableStateOf(true) }
    var detailsError by remember(anime.id) { mutableStateOf<String?>(null) }

    var seasons by remember(anime.id) {
        mutableStateOf<List<AnimeSeason>>(emptyList())
    }
    var seasonsLoading by remember(anime.id) { mutableStateOf(true) }
    var seasonsError by remember(anime.id) { mutableStateOf<String?>(null) }
    var selectedSeason by remember(anime.id) {
        mutableStateOf<AnimeSeason?>(null)
    }
    var episodes by remember(anime.id) {
        mutableStateOf<List<AnimeEpisode>>(emptyList())
    }
    var episodesLoading by remember(anime.id) { mutableStateOf(false) }
    var episodesError by remember(anime.id) { mutableStateOf<String?>(null) }
    var playingEpisode by remember(anime.id) {
        mutableStateOf<AnimeEpisode?>(null)
    }

    LaunchedEffect(anime.id) {
        try {
            details = fetchAnimeDetails(anime.title)
        } catch (e: Exception) {
            detailsError = e.message ?: "Details not available"
        }
        detailsLoading = false
    }

    LaunchedEffect(anime.id) {
        try {
            seasons = fetchSeasons(anime.id)
            selectedSeason = seasons.firstOrNull()
        } catch (e: Exception) {
            seasonsError = e.message ?: "Unable to load seasons"
        }
        seasonsLoading = false
    }

    LaunchedEffect(selectedSeason?.id) {
        val season = selectedSeason
        if (season == null) {
            episodes = emptyList()
        } else {
            episodesLoading = true
            episodesError = null
            try {
                episodes = fetchEpisodes(season.id)
            } catch (e: Exception) {
                episodesError = e.message ?: "Unable to load episodes"
                episodes = emptyList()
            }
            episodesLoading = false
        }
    }

    val currentEpisodeIndex = episodes.indexOfFirst {
        it.id == playingEpisode?.id
    }

    if (playingEpisode != null) {
        PlayerScreen(
            episode = playingEpisode!!,
            episodes = episodes,
            episodeIndex = currentEpisodeIndex,
            onBack = { playingEpisode = null },
            onEpisodeChange = { newIndex ->
                if (newIndex in episodes.indices) {
                    playingEpisode = episodes[newIndex]
                }
            }
        )
        return
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp)
    ) {
        Text(
            "‹  Back",
            color = White,
            fontSize = 18.sp,
            modifier = Modifier.clickable { onBack() }
                .padding(horizontal = 16.dp, vertical = 12.dp)
        )

        AsyncImage(
            model = details?.banner?.takeIf { it.isNotBlank() }
                ?: anime.posterUrl,
            contentDescription = anime.title,
            modifier = Modifier.fillMaxWidth().height(175.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(SurfaceDark),
            contentScale = ContentScale.Crop
        )

        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.Top
        ) {
            AsyncImage(
                model = anime.posterUrl,
                contentDescription = anime.title,
                modifier = Modifier.width(112.dp).height(155.dp)
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
                Text(details?.japaneseTitle.orEmpty(), color = Gray, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                Text(
                    "⭐ ${details?.rating ?: "Loading..."}",
                    color = AccentRed,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
                Text("Year: ${details?.year ?: "Loading..."}", color = Gray, fontSize = 12.sp)
                Text("Episodes: ${details?.episodes ?: "Loading..."}", color = Gray, fontSize = 12.sp)
            }
        }

        if (detailsLoading) {
            Box(
                Modifier.fillMaxWidth().padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentRed)
            }
        } else if (detailsError != null) {
            Text(
                "Extra details unavailable: $detailsError",
                color = Gray,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            DetailSection("Story") {
                Text(anime.description, color = Gray, fontSize = 14.sp)
            }
        } else {
            details?.let { info ->
                DetailSection("Story") {
                    Text(
                        info.description.replace(Regex("<[^>]*>"), ""),
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
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            info.characters.forEach { character ->
                                Column(Modifier.width(100.dp)) {
                                    AsyncImage(
                                        model = character.image,
                                        contentDescription = character.name,
                                        modifier = Modifier.fillMaxWidth()
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
                                    Text(character.role, color = Gray, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        DetailSection("Watch Anime") {
            Text("Select Season", color = Gray, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))

            if (seasonsLoading) {
                CircularProgressIndicator(
                    color = AccentRed,
                    modifier = Modifier.size(25.dp)
                )
            } else if (seasonsError != null) {
                Text("Season load error: $seasonsError", color = AccentRed)
            } else if (seasons.isEmpty()) {
                Text(
                    "Abhi is Anime ke Seasons add nahi kiye gaye hain.",
                    color = Gray
                )
            } else {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    seasons.forEach { season ->
                        val isSelected = selectedSeason?.id == season.id
                        Surface(
                            color = if (isSelected) AccentRed else SurfaceDark,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.clickable {
                                selectedSeason = season
                            }
                        ) {
                            Text(
                                season.title.ifBlank {
                                    "Season ${season.seasonNumber}"
                                },
                                color = White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(
                                    horizontal = 16.dp, vertical = 11.dp
                                )
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))
                Text(
                    selectedSeason?.title ?: "Episodes",
                    color = White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(10.dp))

                if (episodesLoading) {
                    CircularProgressIndicator(
                        color = AccentRed,
                        modifier = Modifier.size(25.dp)
                    )
                } else if (episodesError != null) {
                    Text("Episode load error: $episodesError", color = AccentRed)
                } else if (episodes.isEmpty()) {
                    Text(
                        "Is Season mein abhi koi Published Episode nahi hai.",
                        color = Gray
                    )
                } else {
                    episodes.forEach { episode ->
                        EpisodeCard(episode) {
                            playingEpisode = episode
                        }
                        Spacer(Modifier.height(9.dp))
                    }
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(episode: AnimeEpisode, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .clickable { onClick() }
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(48.dp)
                .clip(RoundedCornerShape(9.dp))
                .background(Color(0xFF30202A)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                episode.episodeNumber.toString(),
                color = AccentRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                episode.title.ifBlank { "Episode ${episode.episodeNumber}" },
                color = White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            if (episode.description.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    episode.description,
                    color = Gray,
                    fontSize = 11.sp,
                    maxLines = 2
                )
            }
        }
        Text("▶", color = AccentRed, fontSize = 20.sp)
    }
}

@Composable
fun PlayerScreen(
    episode: AnimeEpisode,
    episodes: List<AnimeEpisode>,
    episodeIndex: Int,
    onBack: () -> Unit,
    onEpisodeChange: (Int) -> Unit
) {
    var servers by remember(episode.id) {
        mutableStateOf<List<VideoServer>>(emptyList())
    }
    var loading by remember(episode.id) { mutableStateOf(true) }
    var error by remember(episode.id) { mutableStateOf<String?>(null) }
    var selectedLanguage by remember(episode.id) { mutableStateOf("") }
    var selectedQuality by remember(episode.id) { mutableStateOf("720p") }
    var languageMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }

    LaunchedEffect(episode.id) {
        loading = true
        error = null
        servers = emptyList()
        try {
            servers = fetchVideoServers(episode.id)
            if (servers.isEmpty()) {
                error = "Is Episode ke liye abhi video link add nahi hua."
            } else {
                selectedLanguage = servers.firstOrNull {
                    it.language.equals("Hindi", true)
                }?.language ?: servers.first().language

                val available = servers.filter {
                    it.language.equals(selectedLanguage, true)
                }
                selectedQuality = available.firstOrNull {
                    it.quality.equals("720p", true)
                }?.quality ?: available.first().quality
            }
        } catch (e: Exception) {
            error = e.message ?: "Video links load nahi ho paye"
        }
        loading = false
    }

    val languageServers = servers.filter {
        it.language.equals(selectedLanguage, true)
    }
    val qualityOrder = listOf("1080p", "720p", "480p")
    val qualities = languageServers.map { it.quality }
        .distinct()
        .sortedBy { qualityOrder.indexOf(it).let { index ->
            if (index < 0) Int.MAX_VALUE else index
        } }

    val selectedServer = languageServers.firstOrNull {
        it.quality.equals(selectedQuality, true)
    } ?: languageServers.firstOrNull()

    Column(
        Modifier.fillMaxSize().background(Background)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "‹  Back to Episodes",
            color = White,
            fontSize = 17.sp,
            modifier = Modifier.clickable { onBack() }.padding(16.dp)
        )
        Text(
            "AniStream Player",
            color = AccentRed,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "Episode ${episode.episodeNumber}: ${episode.title}",
            color = White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(
                start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp
            )
        )

        when {
            loading -> Box(
                Modifier.fillMaxWidth().height(220.dp).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentRed)
            }
            error != null -> Box(
                Modifier.fillMaxWidth().height(220.dp)
                    .background(Color.Black).padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(error ?: "Video unavailable", color = White, fontSize = 14.sp)
            }
            selectedServer != null -> VideoPlayerView(
                videoUrl = selectedServer.videoUrl,
                episodeId = episode.id
            )
        }

        if (servers.isNotEmpty()) {
            Spacer(Modifier.height(18.dp))
            Text(
                "Video Settings",
                color = White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { languageMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = White
                        )
                    ) {
                        Text(selectedLanguage.ifBlank { "Language" })
                    }
                    DropdownMenu(
                        expanded = languageMenu,
                        onDismissRequest = { languageMenu = false }
                    ) {
                        servers.map { it.language }.distinct().forEach { language ->
                            DropdownMenuItem(
                                text = { Text(language) },
                                onClick = {
                                    selectedLanguage = language
                                    val available = servers.filter {
                                        it.language.equals(language, true)
                                    }
                                    selectedQuality = available.firstOrNull {
                                        it.quality.equals("720p", true)
                                    }?.quality ?: available.firstOrNull()?.quality.orEmpty()
                                    languageMenu = false
                                }
                            )
                        }
                    }
                }

                Box(Modifier.weight(1f)) {
                    OutlinedButton(
                        onClick = { qualityMenu = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = White
                        )
                    ) {
                        Text(selectedQuality.ifBlank { "Quality" })
                    }
                    DropdownMenu(
                        expanded = qualityMenu,
                        onDismissRequest = { qualityMenu = false }
                    ) {
                        qualities.forEach { quality ->
                            DropdownMenuItem(
                                text = { Text(quality) },
                                onClick = {
                                    selectedQuality = quality
                                    qualityMenu = false
                                }
                            )
                        }
                    }
                }
            }

            selectedServer?.let {
                Text(
                    "Server: ${it.serverName}",
                    color = Gray,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 16.dp, top = 10.dp)
                )
            }
        }

        Spacer(Modifier.height(22.dp))
        Text(
            "Episode Navigation",
            color = White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = { onEpisodeChange(episodeIndex - 1) },
                enabled = episodeIndex > 0,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = White)
            ) {
                Text("← Previous")
            }
            Button(
                onClick = { onEpisodeChange(episodeIndex + 1) },
                enabled = episodeIndex >= 0 && episodeIndex < episodes.lastIndex,
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed)
            ) {
                Text("Next →")
            }
        }
        Spacer(Modifier.height(25.dp))
    }
}

@Composable
fun VideoPlayerView(videoUrl: String, episodeId: String) {
    val context = LocalContext.current

    val exoPlayer = remember(context, episodeId) {
        ExoPlayer.Builder(context).build()
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(exoPlayer, videoUrl) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    AndroidView(
        factory = { viewContext ->
            PlayerView(viewContext).apply {
                useController = true
                player = exoPlayer
            }
        },
        update = { playerView ->
            playerView.player = exoPlayer
        },
        modifier = Modifier.fillMaxWidth()
            .height(230.dp)
            .background(Color.Black)
    )
}

@Composable
fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
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
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("$label: ", color = Gray, fontSize = 13.sp)
        Text(value, color = White, fontSize = 13.sp)
    }
}

@Composable
fun SimpleScreen(title: String, subtitle: String) {
    Column(
        Modifier.fillMaxSize(),
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
