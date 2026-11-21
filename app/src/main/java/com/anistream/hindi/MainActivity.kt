package com.anistream.hindi

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val SUPABASE_URL = "https://cyftkgouolxcverswvpv.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_h-Alm01lWCWvvlWcW-Uqyg_iAub4QH3"

private val Background = Color(0xFF08090D)
private val SurfaceDark = Color(0xFF14151C)
private val AccentRed = Color(0xFFFF1744)
private val White = Color.White
private val Gray = Color(0xFF999BA5)

data class Anime(val id: String, val title: String, val posterUrl: String, val description: String, val animeType: String)
data class AnimeSeason(val id: String, val animeId: String, val seasonNumber: Int, val title: String)
data class AnimeEpisode(val id: String, val episodeNumber: Int, val title: String, val description: String)
data class VideoServer(
    val id: String,
    val episodeId: String,
    val serverName: String,
    val videoUrl: String,
    val downloadUrl: String,
    val quality: String,
    val language: String
)
data class CharacterInfo(val name: String, val image: String, val role: String)
data class AnimeDetails(
    val englishTitle: String, val japaneseTitle: String, val description: String,
    val banner: String, val genres: List<String>, val year: String,
    val episodes: String, val rating: String, val status: String,
    val studio: String, val characters: List<CharacterInfo>
)
data class DownloadInfo(val id: Long, val title: String, val status: String, val progress: Int)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { AniStreamApp() }
    }
}

private fun request(url: String, method: String = "GET", body: String? = null, supabase: Boolean = false): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/json")
        if (supabase) connection.setRequestProperty("apikey", SUPABASE_KEY)
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val result = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) throw Exception("Server error $code: $result")
        return result
    } finally {
        connection.disconnect()
    }
}

private suspend fun fetchAnime(): List<Anime> = withContext(Dispatchers.IO) {
    val url = "$SUPABASE_URL/rest/v1/anime?select=id,title,description,poster_url,anime_type,created_at&is_published=eq.true&order=created_at.desc"
    val array = JSONArray(request(url, supabase = true))
    (0 until array.length()).map { i ->
        val x = array.getJSONObject(i)
        Anime(x.optString("id"), x.optString("title"), x.optString("poster_url"),
            x.optString("description"), x.optString("anime_type"))
    }
}

private suspend fun fetchSeasons(animeId: String): List<AnimeSeason> = withContext(Dispatchers.IO) {
    val url = "$SUPABASE_URL/rest/v1/seasons?select=id,anime_id,season_number,title&anime_id=eq.${Uri.encode(animeId)}&order=season_number.asc"
    val a = JSONArray(request(url, supabase = true))
    (0 until a.length()).map { i ->
        val x = a.getJSONObject(i)
        AnimeSeason(x.optString("id"), x.optString("anime_id"), x.optInt("season_number"), x.optString("title"))
    }
}

private suspend fun fetchEpisodes(seasonId: String): List<AnimeEpisode> = withContext(Dispatchers.IO) {
    val url = "$SUPABASE_URL/rest/v1/episodes?select=id,episode_number,title,description&season_id=eq.${Uri.encode(seasonId)}&is_published=eq.true&order=episode_number.asc"
    val a = JSONArray(request(url, supabase = true))
    (0 until a.length()).map { i ->
        val x = a.getJSONObject(i)
        AnimeEpisode(x.optString("id"), x.optInt("episode_number"), x.optString("title"), x.optString("description"))
    }
}

private suspend fun fetchVideoServers(episodeId: String): List<VideoServer> = withContext(Dispatchers.IO) {
    val url = "$SUPABASE_URL/rest/v1/video_servers?select=id,episode_id,server_name,video_url,download_url,quality,language&episode_id=eq.${Uri.encode(episodeId)}"
    val a = JSONArray(request(url, supabase = true))
    (0 until a.length()).mapNotNull { i ->
        val x = a.getJSONObject(i)
        val watch = x.optString("video_url").takeUnless { it.isBlank() || it == "null" } ?: return@mapNotNull null
        VideoServer(x.optString("id"), x.optString("episode_id"), x.optString("server_name"),
            watch, x.optString("download_url").takeUnless { it == "null" }.orEmpty(),
            x.optString("quality"), x.optString("language").ifBlank { "Hindi" })
    }
}

private suspend fun fetchAnimeDetails(title: String): AnimeDetails = withContext(Dispatchers.IO) {
    val query = """
        query (${'$'}search: String) {
          Media(search: ${'$'}search, type: ANIME) {
            title { english romaji native }
            description(asHtml: false) bannerImage genres seasonYear episodes averageScore status
            studios(isMain: true) { nodes { name } }
            characters(sort: ROLE, perPage: 12) {
              edges { role node { name { full } image { large } } }
            }
          }
        }
    """.trimIndent()
    val body = JSONObject().apply {
        put("query", query)
        put("variables", JSONObject().put("search", title))
    }
    val response = request("https://graphql.anilist.co", "POST", body.toString())
    val media = JSONObject(response).getJSONObject("data").optJSONObject("Media")
        ?: throw Exception("Anime details not found")
    val titles = media.optJSONObject("title")
    val studios = media.optJSONObject("studios")?.optJSONArray("nodes")
    val studio = if (studios != null && studios.length() > 0) studios.getJSONObject(0).optString("name") else "Not available"
    val chars = mutableListOf<CharacterInfo>()
    val edges = media.optJSONObject("characters")?.optJSONArray("edges")
    if (edges != null) for (i in 0 until edges.length()) {
        val edge = edges.getJSONObject(i)
        val node = edge.getJSONObject("node")
        chars.add(CharacterInfo(node.optJSONObject("name")?.optString("full").orEmpty(),
            node.optJSONObject("image")?.optString("large").orEmpty(), edge.optString("role")))
    }
    AnimeDetails(
        titles?.optString("english")?.takeIf { it.isNotBlank() && it != "null" }
            ?: titles?.optString("romaji").orEmpty(),
        titles?.optString("native").orEmpty(),
        media.optString("description").takeIf { it.isNotBlank() && it != "null" } ?: "Description not available.",
        media.optString("bannerImage").takeUnless { it == "null" }.orEmpty(),
        media.optJSONArray("genres")?.let { a -> (0 until a.length()).map { a.getString(it) } } ?: emptyList(),
        media.optString("seasonYear").takeUnless { it == "null" } ?: "Unknown",
        media.optString("episodes").takeUnless { it == "null" } ?: "Unknown",
        media.optString("averageScore").takeUnless { it == "null" }?.let { "$it/100" } ?: "Not rated",
        media.optString("status").replace("_", " "), studio, chars
    )
}

private fun startDownload(context: Context, url: String, fileTitle: String): Long {
    require(url.startsWith("https://") || url.startsWith("http://")) { "Download link is invalid." }
    val originalPath = Uri.parse(url).lastPathSegment.orEmpty()
    val extension = originalPath.substringAfterLast('.', "mp4").takeIf { it.matches(Regex("[A-Za-z0-9]{2,5}")) } ?: "mp4"
    val fileName = fileTitle.replace(Regex("[^A-Za-z0-9._-]"), "_").take(90) + "_" + System.currentTimeMillis() + "." + extension
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(fileTitle)
        .setDescription("AniStream Hindi download")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setAllowedOverMetered(true)
        .setAllowedOverRoaming(false)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AniStream/$fileName")
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    return manager.enqueue(request)
}

private fun readDownloads(context: Context): List<DownloadInfo> {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val cursor = manager.query(DownloadManager.Query())
    val items = mutableListOf<DownloadInfo>()
    cursor?.use {
        val idCol = it.getColumnIndex(DownloadManager.COLUMN_ID)
        val titleCol = it.getColumnIndex(DownloadManager.COLUMN_TITLE)
        val statusCol = it.getColumnIndex(DownloadManager.COLUMN_STATUS)
        val soFarCol = it.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val totalCol = it.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        while (it.moveToNext()) {
            val statusCode = it.getInt(statusCol)
            val status = when (statusCode) {
                DownloadManager.STATUS_SUCCESSFUL -> "Completed"
                DownloadManager.STATUS_FAILED -> "Failed"
                DownloadManager.STATUS_PAUSED -> "Paused"
                DownloadManager.STATUS_PENDING -> "Waiting"
                DownloadManager.STATUS_RUNNING -> "Downloading"
                else -> "Unknown"
            }
            val total = it.getLong(totalCol)
            val done = it.getLong(soFarCol)
            val progress = if (total > 0) ((done * 100) / total).toInt().coerceIn(0, 100) else 0
            items.add(DownloadInfo(it.getLong(idCol), it.getString(titleCol) ?: "AniStream download", status, progress))
        }
    }
    return items.reversed()
}

@Composable
fun AniStreamApp() {
    var tab by remember { mutableIntStateOf(0) }
    var animeList by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var selectedAnime by remember { mutableStateOf<Anime?>(null) }

    LaunchedEffect(Unit) {
        try { animeList = fetchAnime() } catch (e: Exception) { error = e.message ?: "Unable to load anime" }
        loading = false
    }
    BackHandler(enabled = selectedAnime == null && tab != 0) { tab = 0; search = "" }

    MaterialTheme {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                if (selectedAnime == null) {
                    NavigationBar(containerColor = SurfaceDark) {
                        val labels = listOf("Home", "Search", "My List", "Downloads", "Profile")
                        val icons = listOf("⌂", "⌕", "♡", "↓", "♙")
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = tab == index, onClick = { tab = index },
                                icon = { Text(icons[index], fontSize = 21.sp) },
                                label = { Text(label, fontSize = 9.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentRed, selectedTextColor = AccentRed,
                                    indicatorColor = Color(0xFF30202A),
                                    unselectedIconColor = Gray, unselectedTextColor = Gray
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().background(Background).padding(padding)) {
                if (selectedAnime == null) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Text("Ani", color = White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Stream", color = AccentRed, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                        Spacer(Modifier.weight(1f))
                        Text("HINDI", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                    when (tab) {
                        0 -> HomeScreen(animeList, loading, error) { selectedAnime = it }
                        1 -> SearchScreen(animeList, search, { search = it }) { selectedAnime = it }
                        2 -> SimpleScreen("My List", "Your favourite anime will appear here")
                        3 -> DownloadsScreen()
                        4 -> SimpleScreen("My Profile", "Login to access your profile")
                    }
                } else {
                    AnimeDetailsScreen(selectedAnime!!, onBack = { selectedAnime = null })
                }
            }
        }
    }
}

@Composable
fun HomeScreen(animeList: List<Anime>, loading: Boolean, error: String?, onAnimeClick: (Anime) -> Unit) {
    val movies = animeList.filter { it.animeType.equals("Movie", true) }
    val series = animeList.filter { !it.animeType.equals("Movie", true) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("WELCOME TO ANISTREAM", color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(7.dp))
        Text("Anime World Awaits You.", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(18.dp))
        Box(Modifier.fillMaxWidth().height(155.dp).clip(RoundedCornerShape(18.dp))
            .background(Color(0xFF30101B)).padding(18.dp), contentAlignment = Alignment.BottomStart) {
            Column {
                Text("ANIME STREAMING", color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(7.dp))
                Text("Your Anime.\nYour World.", color = White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                Text("Discover your next adventure.", color = Gray, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(22.dp))
        if (loading) Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = AccentRed)
        } else if (error != null) Text("Anime load error: $error", color = AccentRed)
        else if (animeList.isEmpty()) Text("No published anime found.", color = Gray)
        else {
            AnimeSection("✨ Recently Added", animeList, onAnimeClick)
            AnimeSection("🎬 Anime Series", series, onAnimeClick)
            if (movies.isNotEmpty()) AnimeSection("🍿 Anime Movies", movies, onAnimeClick)
        }
    }
}

@Composable
fun AnimeSection(title: String, animeList: List<Anime>, onAnimeClick: (Anime) -> Unit) {
    if (animeList.isEmpty()) return
    Column {
        Text(title, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(11.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            animeList.forEach { AnimeCard(it) { onAnimeClick(it) } }
        }
        Spacer(Modifier.height(23.dp))
    }
}

@Composable
fun AnimeCard(anime: Anime, onClick: () -> Unit) {
    Column(Modifier.width(124.dp).clickable { onClick() }) {
        AsyncImage(anime.posterUrl, anime.title, Modifier.fillMaxWidth().height(164.dp)
            .clip(RoundedCornerShape(11.dp)).background(SurfaceDark), contentScale = ContentScale.Crop)
        Spacer(Modifier.height(6.dp))
        Text(anime.title, color = White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
    }
}

@Composable
fun SearchScreen(animeList: List<Anime>, searchText: String, onSearchChange: (String) -> Unit, onAnimeClick: (Anime) -> Unit) {
    val filtered = animeList.filter { it.title.contains(searchText, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Search Anime", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(searchText, onSearchChange, Modifier.fillMaxWidth(),
            placeholder = { Text("Search anime...", color = Gray) }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White, unfocusedTextColor = White,
                focusedBorderColor = AccentRed, unfocusedBorderColor = Gray, cursorColor = AccentRed
            ))
        Spacer(Modifier.height(14.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            filtered.forEach { anime ->
                Row(Modifier.fillMaxWidth().clickable { onAnimeClick(anime) }, verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(anime.posterUrl, anime.title, Modifier.size(70.dp, 92.dp)
                        .clip(RoundedCornerShape(9.dp)).background(SurfaceDark), contentScale = ContentScale.Crop)
                    Spacer(Modifier.width(12.dp))
                    Text(anime.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun AnimeDetailsScreen(anime: Anime, onBack: () -> Unit) {
    var details by remember(anime.id) { mutableStateOf<AnimeDetails?>(null) }
    var detailsLoading by remember(anime.id) { mutableStateOf(true) }
    var detailsError by remember(anime.id) { mutableStateOf<String?>(null) }
    var seasons by remember(anime.id) { mutableStateOf<List<AnimeSeason>>(emptyList()) }
    var seasonsLoading by remember(anime.id) { mutableStateOf(true) }
    var seasonsError by remember(anime.id) { mutableStateOf<String?>(null) }
    var selectedSeason by remember(anime.id) { mutableStateOf<AnimeSeason?>(null) }
    var episodes by remember(anime.id) { mutableStateOf<List<AnimeEpisode>>(emptyList()) }
    var episodesLoading by remember(anime.id) { mutableStateOf(false) }
    var episodesError by remember(anime.id) { mutableStateOf<String?>(null) }
    var playingEpisode by remember(anime.id) { mutableStateOf<AnimeEpisode?>(null) }

    BackHandler {
        if (playingEpisode != null) playingEpisode = null else onBack()
    }
    LaunchedEffect(anime.id) {
        try { details = fetchAnimeDetails(anime.title) } catch (e: Exception) { detailsError = e.message }
        detailsLoading = false
    }
    LaunchedEffect(anime.id) {
        try { seasons = fetchSeasons(anime.id); selectedSeason = seasons.firstOrNull() }
        catch (e: Exception) { seasonsError = e.message }
        seasonsLoading = false
    }
    LaunchedEffect(selectedSeason?.id) {
        val season = selectedSeason
        if (season == null) episodes = emptyList()
        else {
            episodesLoading = true; episodesError = null
            try { episodes = fetchEpisodes(season.id) }
            catch (e: Exception) { episodesError = e.message; episodes = emptyList() }
            episodesLoading = false
        }
    }
    val episodeIndex = episodes.indexOfFirst { it.id == playingEpisode?.id }
    if (playingEpisode != null) {
        PlayerScreen(playingEpisode!!, episodes, episodeIndex,
            onBack = { playingEpisode = null },
            onEpisodeChange = { if (it in episodes.indices) playingEpisode = episodes[it] })
        return
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 22.dp)) {
        Text("‹  Back", color = White, fontSize = 17.sp,
            modifier = Modifier.clickable { onBack() }.padding(horizontal = 16.dp, vertical = 11.dp))
        AsyncImage(details?.banner?.takeIf { it.isNotBlank() } ?: anime.posterUrl, anime.title,
            Modifier.fillMaxWidth().height(165.dp).padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(15.dp)).background(SurfaceDark), contentScale = ContentScale.Crop)
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(anime.posterUrl, anime.title, Modifier.width(108.dp).height(148.dp)
                .clip(RoundedCornerShape(11.dp)).background(SurfaceDark), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(13.dp))
            Column {
                Text(details?.englishTitle?.takeIf { it.isNotBlank() } ?: anime.title,
                    color = White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text(details?.japaneseTitle.orEmpty(), color = Gray, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Text("⭐ ${details?.rating ?: "Loading..."}", color = AccentRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Year: ${details?.year ?: "Loading..."}", color = Gray, fontSize = 11.sp)
                Text("Episodes: ${details?.episodes ?: "Loading..."}", color = Gray, fontSize = 11.sp)
            }
        }
        if (detailsLoading) CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp), color = AccentRed)
        else if (detailsError != null) {
            Text("Extra details unavailable. ${detailsError ?: ""}", color = Gray, modifier = Modifier.padding(horizontal = 16.dp))
            DetailSection("Story") { Text(anime.description, color = Gray, fontSize = 13.sp) }
        } else details?.let { info ->
            DetailSection("Story") { Text(info.description.replace(Regex("<[^>]*>"), ""), color = Gray, fontSize = 13.sp) }
            DetailSection("Anime Information") {
                InfoLine("Release", info.year); InfoLine("Status", info.status)
                InfoLine("Studio", info.studio); InfoLine("Episodes", info.episodes); InfoLine("Rating", info.rating)
            }
            DetailSection("Genres") { Text(info.genres.joinToString("  •  "), color = AccentRed, fontSize = 12.sp) }
            if (info.characters.isNotEmpty()) DetailSection("Characters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    info.characters.forEach { c ->
                        Column(Modifier.width(92.dp)) {
                            AsyncImage(c.image, c.name, Modifier.fillMaxWidth().height(116.dp)
                                .clip(RoundedCornerShape(9.dp)).background(SurfaceDark), contentScale = ContentScale.Crop)
                            Spacer(Modifier.height(5.dp))
                            Text(c.name, color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(c.role, color = Gray, fontSize = 9.sp)
                        }
                    }
                }
            }
        }
        DetailSection("Watch Anime") {
            Text("Select Season", color = Gray, fontSize = 12.sp)
            Spacer(Modifier.height(9.dp))
            if (seasonsLoading) CircularProgressIndicator(Modifier.size(23.dp), color = AccentRed)
            else if (seasonsError != null) Text("Season load error: $seasonsError", color = AccentRed)
            else if (seasons.isEmpty()) Text("Abhi is Anime ke Seasons add nahi kiye gaye hain.", color = Gray)
            else {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    seasons.forEach { s ->
                        val selected = selectedSeason?.id == s.id
                        Surface(color = if (selected) AccentRed else SurfaceDark, shape = RoundedCornerShape(9.dp),
                            modifier = Modifier.clickable { selectedSeason = s }) {
                            Text(s.title.ifBlank { "Season ${s.seasonNumber}" }, color = White, fontSize = 12.sp,
                                fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp))
                        }
                    }
                }
                Spacer(Modifier.height(15.dp))
                Text(selectedSeason?.title ?: "Episodes", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(9.dp))
                if (episodesLoading) CircularProgressIndicator(Modifier.size(23.dp), color = AccentRed)
                else if (episodesError != null) Text("Episode load error: $episodesError", color = AccentRed)
                else if (episodes.isEmpty()) Text("Is Season mein abhi koi Published Episode nahi hai.", color = Gray)
                else Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    episodes.forEach { ep -> EpisodeCard(ep, selected = playingEpisode?.id == ep.id) { playingEpisode = ep } }
                }
            }
        }
    }
}

@Composable
fun EpisodeCard(episode: AnimeEpisode, selected: Boolean = false, onClick: () -> Unit) {
    Column(Modifier.width(112.dp).clip(RoundedCornerShape(12.dp))
        .background(if (selected) Color(0xFF35101C) else SurfaceDark)
        .clickable { onClick() }.padding(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EP ${episode.episodeNumber}", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text("▶", color = White, fontSize = 13.sp)
        }
        Spacer(Modifier.height(7.dp))
        Text(episode.title.ifBlank { "Episode ${episode.episodeNumber}" },
            color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2)
    }
}

@Composable
fun PlayerScreen(episode: AnimeEpisode, episodes: List<AnimeEpisode>, episodeIndex: Int,
                 onBack: () -> Unit, onEpisodeChange: (Int) -> Unit) {
    var servers by remember(episode.id) { mutableStateOf<List<VideoServer>>(emptyList()) }
    var loading by remember(episode.id) { mutableStateOf(true) }
    var error by remember(episode.id) { mutableStateOf<String?>(null) }
    var language by remember(episode.id) { mutableStateOf("") }
    var quality by remember(episode.id) { mutableStateOf("720p") }
    var languageMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var downloadMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    BackHandler(enabled = fullscreen) { fullscreen = false }
    LaunchedEffect(episode.id) {
        loading = true; error = null; servers = emptyList(); fullscreen = false; downloadMessage = null
        try {
            servers = fetchVideoServers(episode.id)
            if (servers.isEmpty()) error = "Is Episode ke liye abhi video link add nahi hua."
            else {
                language = servers.firstOrNull { it.language.equals("Hindi", true) }?.language ?: servers.first().language
                val matching = servers.filter { it.language.equals(language, true) }
                quality = matching.firstOrNull { it.quality.equals("720p", true) }?.quality ?: matching.first().quality
            }
        } catch (e: Exception) { error = e.message ?: "Video links load nahi ho paye" }
        loading = false
    }
    val languageServers = servers.filter { it.language.equals(language, true) }
    val qualities = languageServers.map { it.quality }.distinct().sortedBy { listOf("1080p", "720p", "480p").indexOf(it).let { n -> if (n < 0) 99 else n } }
    val selected = languageServers.firstOrNull { it.quality.equals(quality, true) } ?: languageServers.firstOrNull()

    Column(Modifier.fillMaxSize().background(Background).then(if (fullscreen) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("‹", color = White, fontSize = 27.sp, modifier = Modifier.clickable { onBack() })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("ANISTREAM PLAYER", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Episode ${episode.episodeNumber}", color = Gray, fontSize = 10.sp)
                }
                SmallOutlineButton("⛶", onClick = { fullscreen = true })
            }
            Text(episode.title.ifBlank { "Episode ${episode.episodeNumber}" }, color = White,
                fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(15.dp))
        }
        when {
            loading -> Box(Modifier.fillMaxWidth().height(if (fullscreen) 1.dp else 220.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentRed)
            }
            error != null -> Box(Modifier.fillMaxWidth().height(220.dp).background(Color.Black), contentAlignment = Alignment.Center) {
                Text(error ?: "Video unavailable", color = White)
            }
            selected != null -> WatchPlayer(selected.videoUrl, episode.id, fullscreen, { fullscreen = false })
        }
        if (!fullscreen && servers.isNotEmpty()) {
            Column(Modifier.padding(14.dp)) {
                Text("PLAYBACK SETTINGS", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        SmallOutlineButton("Audio: ${language.ifBlank { "Select" }}", { languageMenu = true }, Modifier.fillMaxWidth())
                        DropdownMenu(languageMenu, { languageMenu = false }) {
                            servers.map { it.language }.distinct().forEach { lang ->
                                DropdownMenuItem(text = { Text(lang) }, onClick = {
                                    language = lang
                                    val avail = servers.filter { it.language.equals(lang, true) }
                                    quality = avail.firstOrNull { it.quality.equals("720p", true) }?.quality ?: avail.firstOrNull()?.quality.orEmpty()
                                    languageMenu = false
                                })
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        SmallOutlineButton("Quality: ${quality.ifBlank { "Select" }}", { qualityMenu = true }, Modifier.fillMaxWidth())
                        DropdownMenu(qualityMenu, { qualityMenu = false }) {
                            qualities.forEach { q -> DropdownMenuItem(text = { Text(q) }, onClick = { quality = q; qualityMenu = false }) }
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                val dlUrl = selected?.downloadUrl.orEmpty()
                SmallOutlineButton(if (dlUrl.isBlank()) "↓  Download link unavailable" else "↓  Download Episode",
                    onClick = {
                        if (dlUrl.isBlank()) downloadMessage = "Admin ne is quality ka Download Link add nahi kiya."
                        else try {
                            startDownload(context, dlUrl, "AniStream_Ep_${episode.episodeNumber}_${selected?.quality}")
                            downloadMessage = "Download start ho gaya. Progress Downloads tab mein dekho."
                        } catch (e: Exception) { downloadMessage = e.message ?: "Download start nahi hua." }
                    }, modifier = Modifier.fillMaxWidth(), enabled = dlUrl.isNotBlank())
                downloadMessage?.let { Text(it, color = if (dlUrl.isBlank()) Gray else AccentRed, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp)) }
                selected?.let {
                    Text("Server: ${it.serverName}  •  ${it.quality}", color = Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 9.dp))
                }
            }
        }
        if (!fullscreen) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Text("EPISODE NAVIGATION", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallOutlineButton("← Previous", { onEpisodeChange(episodeIndex - 1) }, Modifier.weight(1f), episodeIndex > 0)
                    SmallOutlineButton("Next Episode →", { onEpisodeChange(episodeIndex + 1) }, Modifier.weight(1f), episodeIndex >= 0 && episodeIndex < episodes.lastIndex, true)
                }
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
fun WatchPlayer(videoUrl: String, episodeId: String, fullscreen: Boolean, onExitFullscreen: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    val isWebPage = remember(videoUrl) {
        val lower = videoUrl.lowercase()
        !(lower.contains(".mp4") || lower.contains(".m3u8") || lower.contains(".mpd") || lower.contains(".mkv") || lower.contains(".webm"))
    }
    DisposableEffect(activity, fullscreen) {
        val window = activity?.window
        val oldOrientation = activity?.requestedOrientation
        if (fullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                WindowInsetsControllerCompat(it, it.decorView).apply {
                    systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                    hide(WindowInsetsCompat.Type.systemBars())
                }
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, true); WindowInsetsControllerCompat(it, it.decorView).show(WindowInsetsCompat.Type.systemBars()) }
        }
        onDispose {
            activity?.requestedOrientation = oldOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let { WindowCompat.setDecorFitsSystemWindows(it, true); WindowInsetsControllerCompat(it, it.decorView).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    if (isWebPage) {
        AndroidView(
            factory = { ctx ->
                WebView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    loadUrl(videoUrl)
                }
            },
            update = { if (it.url != videoUrl) it.loadUrl(videoUrl) },
            modifier = Modifier.fillMaxWidth().height(if (fullscreen) 300.dp else 230.dp).background(Color.Black)
        )
    } else {
        val exoPlayer = remember(context, episodeId) { ExoPlayer.Builder(context).build() }
        DisposableEffect(exoPlayer) { onDispose { exoPlayer.release() } }
        LaunchedEffect(exoPlayer, videoUrl) {
            exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
            exoPlayer.prepare()
            exoPlayer.playWhenReady = true
            exoPlayer.playbackParameters = PlaybackParameters(1f)
        }
        AndroidView(factory = { ctx -> PlayerView(ctx).apply {
            useController = true
            this.player = exoPlayer
            setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
        } }, update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxWidth().then(if (fullscreen) Modifier.fillMaxHeight() else Modifier.height(230.dp)).background(Color.Black))
    }
    if (fullscreen) {
        Box(Modifier.fillMaxWidth().padding(6.dp), contentAlignment = Alignment.TopEnd) {
            SmallOutlineButton("✕", onExitFullscreen)
        }
    }
}

@Composable
fun DownloadsScreen() {
    val context = LocalContext.current
    var downloads by remember { mutableStateOf<List<DownloadInfo>>(emptyList()) }
    LaunchedEffect(Unit) {
        while (true) {
            downloads = withContext(Dispatchers.IO) { readDownloads(context) }
            delay(1000)
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("My Downloads", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Text("Downloaded videos are saved in Downloads/AniStream.", color = Gray, fontSize = 11.sp)
        Spacer(Modifier.height(15.dp))
        if (downloads.isEmpty()) Text("Abhi koi download nahi hai.", color = Gray)
        downloads.forEach { item ->
            Column(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(11.dp)).background(SurfaceDark).padding(13.dp)) {
                Text(item.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(item.status, color = if (item.status == "Failed") AccentRed else Gray, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text(if (item.status == "Completed") "100%" else "${item.progress}%", color = AccentRed, fontSize = 11.sp)
                }
                if (item.status == "Downloading" || item.status == "Waiting" || item.status == "Paused") {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = AccentRed, trackColor = Color(0xFF33343A))
                }
            }
        }
    }
}

@Composable
fun SmallOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false) {
    OutlinedButton(onClick = onClick, modifier = modifier.heightIn(min = 36.dp),
        enabled = enabled, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(9.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) AccentRed else Color(0xFF777982)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selected) AccentRed else White, disabledContentColor = Gray)) {
        Text(text, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp)) {
        Text(title, color = White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        content()
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text("$label: ", color = Gray, fontSize = 12.sp)
        Text(value, color = White, fontSize = 12.sp)
    }
}

@Composable
fun SimpleScreen(title: String, subtitle: String) {
    Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(title, color = White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(9.dp))
        Text(subtitle, color = Gray, fontSize = 13.sp)
    }
}
