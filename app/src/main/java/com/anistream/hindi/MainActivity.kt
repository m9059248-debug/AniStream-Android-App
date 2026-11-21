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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val SUPABASE_URL = "https://cyftkgouolxcverswvpv.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_h-Alm01lWCWvvlWcW-Uqyg_iAub4QH3"
private const val AUTH_PREFS = "anistream_auth_prefs"
private const val MY_LIST_PREFS = "anistream_my_list_prefs"

private val Background = Color(0xFF08090D)
private val SurfaceDark = Color(0xFF14151C)
private val AccentRed = Color(0xFFFF1744)
private val White = Color.White
private val Gray = Color(0xFF999BA5)

data class Anime(val id: String, val title: String, val posterUrl: String, val description: String, val animeType: String)
data class AnimeSeason(val id: String, val animeId: String, val seasonNumber: Int, val title: String)
data class AnimeEpisode(val id: String, val episodeNumber: Int, val title: String, val description: String)
data class VideoServer(val id: String, val episodeId: String, val serverName: String, val videoUrl: String, val downloadUrl: String, val quality: String, val language: String)
data class CharacterInfo(val name: String, val image: String, val role: String)
data class AnimeDetails(
    val englishTitle: String, val japaneseTitle: String, val description: String,
    val banner: String, val genres: List<String>, val year: String, val episodes: String,
    val rating: String, val status: String, val studio: String, val characters: List<CharacterInfo>
)
data class DownloadInfo(val id: Long, val title: String, val status: String, val progress: Int, val localUri: String = "")
data class UserSession(
    val userId: String,
    val email: String,
    val fullName: String,
    val accessToken: String,
    val createdAt: String
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
    supabase: Boolean = false,
    bearerToken: String? = null,
    extraHeaders: Map<String, String> = emptyMap()
): String {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 20000
        connection.setRequestProperty("Accept", "application/json")
        if (supabase) {
            connection.setRequestProperty("apikey", SUPABASE_KEY)
            connection.setRequestProperty("Authorization", "Bearer ${bearerToken ?: SUPABASE_KEY}")
        }
        extraHeaders.forEach { (k, v) -> connection.setRequestProperty(k, v) }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val result = stream?.bufferedReader()?.use { it.readText() } ?: ""
        if (code !in 200..299) {
            val cleanError = runCatching {
                val obj = JSONObject(result)
                obj.optString("msg").ifBlank {
                    obj.optString("error_description").ifBlank {
                        obj.optString("message").ifBlank { obj.optString("error") }
                    }
                }
            }.getOrNull()?.takeIf { it.isNotBlank() } ?: "Server error $code: $result"
            throw Exception(cleanError)
        }
        return result
    } finally {
        connection.disconnect()
    }
}

private fun saveUserSession(context: Context, session: UserSession) {
    context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).edit()
        .putString("user_id", session.userId)
        .putString("email", session.email)
        .putString("full_name", session.fullName)
        .putString("access_token", session.accessToken)
        .putString("created_at", session.createdAt)
        .apply()
}

private fun loadUserSession(context: Context): UserSession? {
    val prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", null)?.takeIf { it.isNotBlank() } ?: return null
    val email = prefs.getString("email", "").orEmpty()
    val userId = prefs.getString("user_id", "").orEmpty()
    val fullName = prefs.getString("full_name", "").orEmpty()
    val createdAt = prefs.getString("created_at", "").orEmpty()
    return UserSession(userId, email, fullName, token, createdAt)
}

private fun clearUserSession(context: Context) {
    context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

private fun myListKey(userId: String?): String =
    if (userId.isNullOrBlank()) "guest_my_list" else "user_my_list_$userId"

private fun loadLocalMyList(context: Context, userId: String?): Set<String> {
    val prefs = context.getSharedPreferences(MY_LIST_PREFS, Context.MODE_PRIVATE)
    return prefs.getStringSet(myListKey(userId), emptySet())?.toSet() ?: emptySet()
}

private fun saveLocalMyList(context: Context, userId: String?, ids: Set<String>) {
    context.getSharedPreferences(MY_LIST_PREFS, Context.MODE_PRIVATE).edit()
        .putStringSet(myListKey(userId), ids)
        .apply()
}

private suspend fun supabaseFetchMyList(session: UserSession): Set<String> = withContext(Dispatchers.IO) {
    val combined = mutableSetOf<String>()
    // 1. Try reading from dedicated Supabase `my_list` table
    runCatching {
        val url = "$SUPABASE_URL/rest/v1/my_list?select=anime_id&user_id=eq.${Uri.encode(session.userId)}&order=created_at.desc"
        val res = request(url, method = "GET", supabase = true, bearerToken = session.accessToken)
        val arr = JSONArray(res)
        for (i in 0 until arr.length()) {
            val id = arr.getJSONObject(i).optString("anime_id")
            if (id.isNotBlank() && id != "null") combined.add(id)
        }
    }
    // 2. Also read from Supabase Auth `user_metadata.my_list` as backup
    runCatching {
        val res = request("$SUPABASE_URL/auth/v1/user", method = "GET", supabase = true, bearerToken = session.accessToken)
        val userObj = JSONObject(res)
        val metaList = userObj.optJSONObject("user_metadata")?.optJSONArray("my_list")
        if (metaList != null) {
            for (i in 0 until metaList.length()) {
                val id = metaList.optString(i)
                if (id.isNotBlank() && id != "null") combined.add(id)
            }
        }
    }
    combined
}

private suspend fun supabaseSyncMyListMetadata(session: UserSession, allIds: Set<String>) = withContext(Dispatchers.IO) {
    runCatching {
        val body = JSONObject().apply {
            put("data", JSONObject().put("my_list", JSONArray(allIds.toList())))
        }
        request(
            "$SUPABASE_URL/auth/v1/user",
            method = "PUT",
            body = body.toString(),
            supabase = true,
            bearerToken = session.accessToken
        )
    }
}

private suspend fun supabaseAddToMyList(session: UserSession, animeId: String, allIds: Set<String>) = withContext(Dispatchers.IO) {
    // 1. Insert into Supabase `my_list` table if present
    runCatching {
        val payload = JSONObject().apply {
            put("user_id", session.userId)
            put("anime_id", animeId)
        }
        request(
            "$SUPABASE_URL/rest/v1/my_list",
            method = "POST",
            body = payload.toString(),
            supabase = true,
            bearerToken = session.accessToken,
            extraHeaders = mapOf("Prefer" to "resolution=merge-duplicates,return=minimal")
        )
    }
    // 2. Sync with Supabase Auth metadata so it always works even without custom SQL table
    supabaseSyncMyListMetadata(session, allIds)
}

private suspend fun supabaseRemoveFromMyList(session: UserSession, animeId: String, allIds: Set<String>) = withContext(Dispatchers.IO) {
    // 1. Delete from Supabase `my_list` table if present
    runCatching {
        val url = "$SUPABASE_URL/rest/v1/my_list?user_id=eq.${Uri.encode(session.userId)}&anime_id=eq.${Uri.encode(animeId)}"
        request(
            url,
            method = "DELETE",
            supabase = true,
            bearerToken = session.accessToken
        )
    }
    // 2. Sync updated list with Supabase Auth metadata
    supabaseSyncMyListMetadata(session, allIds)
}

private fun parseUserSession(root: JSONObject, fallbackToken: String = ""): UserSession {
    val token = root.optString("access_token").ifBlank { fallbackToken }
    val userObj = root.optJSONObject("user") ?: root
    val userId = userObj.optString("id")
    val email = userObj.optString("email")
    val createdAt = userObj.optString("created_at").take(10)
    val metadata = userObj.optJSONObject("user_metadata")
    val fullName = metadata?.optString("full_name").orEmpty()
        .ifBlank { metadata?.optString("name").orEmpty() }
        .ifBlank { email.substringBefore("@") }
    return UserSession(userId, email, fullName, token, createdAt)
}

private suspend fun supabaseSignUp(fullName: String, email: String, password: String): Pair<UserSession?, String> = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        put("email", email.trim())
        put("password", password)
        put("data", JSONObject().put("full_name", fullName.trim()))
    }
    val res = request("$SUPABASE_URL/auth/v1/signup", method = "POST", body = payload.toString(), supabase = true)
    val json = JSONObject(res)
    val accessToken = json.optString("access_token")
    if (accessToken.isNotBlank() && accessToken != "null") {
        Pair(parseUserSession(json), "Account ban gaya aur aap login ho gaye hain!")
    } else {
        Pair(null, "Signup successful! Agar email verification on hai toh apna email verify karke Login karein.")
    }
}

private suspend fun supabaseSignIn(email: String, password: String): UserSession = withContext(Dispatchers.IO) {
    val payload = JSONObject().apply {
        put("email", email.trim())
        put("password", password)
    }
    val res = request(
        "$SUPABASE_URL/auth/v1/token?grant_type=password",
        method = "POST",
        body = payload.toString(),
        supabase = true
    )
    val json = JSONObject(res)
    val session = parseUserSession(json)
    if (session.accessToken.isBlank()) throw Exception("Login token nahi mila. Kripya dubara try karein.")
    session
}

private suspend fun supabaseFetchProfile(session: UserSession): UserSession = withContext(Dispatchers.IO) {
    val res = request(
        "$SUPABASE_URL/auth/v1/user",
        method = "GET",
        supabase = true,
        bearerToken = session.accessToken
    )
    parseUserSession(JSONObject(res), fallbackToken = session.accessToken)
}

private suspend fun supabaseLogout(accessToken: String) = withContext(Dispatchers.IO) {
    runCatching {
        request(
            "$SUPABASE_URL/auth/v1/logout",
            method = "POST",
            supabase = true,
            bearerToken = accessToken
        )
    }
}

private suspend fun fetchAnime(): List<Anime> = withContext(Dispatchers.IO) {
    val url = "$SUPABASE_URL/rest/v1/anime?select=id,title,description,poster_url,anime_type,created_at&is_published=eq.true&order=created_at.desc"
    val a = JSONArray(request(url, supabase = true))
    (0 until a.length()).map { i ->
        val x = a.getJSONObject(i)
        Anime(x.optString("id"), x.optString("title"), x.optString("poster_url"), x.optString("description"), x.optString("anime_type"))
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
        VideoServer(
            x.optString("id"), x.optString("episode_id"), x.optString("server_name"), watch,
            x.optString("download_url").takeUnless { it == "null" }.orEmpty(),
            x.optString("quality"), x.optString("language").ifBlank { "Hindi" }
        )
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
    val media = JSONObject(response).getJSONObject("data").optJSONObject("Media") ?: throw Exception("Anime details not found")
    val titles = media.optJSONObject("title")
    val studios = media.optJSONObject("studios")?.optJSONArray("nodes")
    val studio = if (studios != null && studios.length() > 0) studios.getJSONObject(0).optString("name") else "Not available"
    val chars = mutableListOf<CharacterInfo>()
    val edges = media.optJSONObject("characters")?.optJSONArray("edges")
    if (edges != null) for (i in 0 until edges.length()) {
        val edge = edges.getJSONObject(i)
        val node = edge.getJSONObject("node")
        chars.add(CharacterInfo(
            node.optJSONObject("name")?.optString("full").orEmpty(),
            node.optJSONObject("image")?.optString("large").orEmpty(),
            edge.optString("role")
        ))
    }
    AnimeDetails(
        titles?.optString("english")?.takeIf { it.isNotBlank() && it != "null" } ?: titles?.optString("romaji").orEmpty(),
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
            val id = it.getLong(idCol)
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
            val localUri = if (statusCode == DownloadManager.STATUS_SUCCESSFUL) manager.getUriForDownloadedFile(id)?.toString().orEmpty() else ""
            items.add(DownloadInfo(id, it.getString(titleCol) ?: "AniStream download", status, progress, localUri))
        }
    }
    return items.reversed()
}

@Composable
fun AniStreamApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var tab by remember { mutableIntStateOf(0) }
    var animeList by remember { mutableStateOf<List<Anime>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    var selectedAnime by remember { mutableStateOf<Anime?>(null) }
    var offlineDownload by remember { mutableStateOf<DownloadInfo?>(null) }
    var userSession by remember { mutableStateOf(loadUserSession(context)) }
    var myListIds by remember { mutableStateOf(loadLocalMyList(context, userSession?.userId)) }
    var myListSyncing by remember { mutableStateOf(false) }

    fun toggleMyList(animeId: String) {
        val currentlySaved = myListIds.contains(animeId)
        val updated = if (currentlySaved) myListIds - animeId else myListIds + animeId
        myListIds = updated
        saveLocalMyList(context, userSession?.userId, updated)
        val currentSession = userSession
        if (currentSession != null) {
            scope.launch {
                myListSyncing = true
                if (currentlySaved) {
                    supabaseRemoveFromMyList(currentSession, animeId, updated)
                } else {
                    supabaseAddToMyList(currentSession, animeId, updated)
                }
                myListSyncing = false
            }
        }
    }

    fun syncMyListFromCloud() {
        val currentSession = userSession ?: return
        scope.launch {
            myListSyncing = true
            val localIds = loadLocalMyList(context, currentSession.userId)
            val guestIds = loadLocalMyList(context, null)
            val cloudIds = supabaseFetchMyList(currentSession)
            val merged = localIds + guestIds + cloudIds
            myListIds = merged
            saveLocalMyList(context, currentSession.userId, merged)
            if (merged != cloudIds) {
                merged.subtract(cloudIds).forEach { id ->
                    supabaseAddToMyList(currentSession, id, merged)
                }
            }
            myListSyncing = false
        }
    }

    LaunchedEffect(Unit) {
        try { animeList = fetchAnime() } catch (e: Exception) { error = e.message ?: "Unable to load anime" }
        loading = false
    }

    LaunchedEffect(userSession?.accessToken) {
        val current = userSession
        if (current == null) {
            myListIds = loadLocalMyList(context, null)
            return@LaunchedEffect
        }
        myListIds = loadLocalMyList(context, current.userId)
        syncMyListFromCloud()
        runCatching {
            val refreshed = supabaseFetchProfile(current)
            userSession = refreshed
            saveUserSession(context, refreshed)
        }
    }

    BackHandler(enabled = offlineDownload != null) { offlineDownload = null }
    BackHandler(enabled = offlineDownload == null && selectedAnime == null && tab != 0) {
        tab = 0
        search = ""
    }

    MaterialTheme {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                if (selectedAnime == null && offlineDownload == null) {
                    NavigationBar(containerColor = SurfaceDark) {
                        val labels = listOf("Home", "Search", "My List", "Downloads", "Profile")
                        val icons = listOf("⌂", "⌕", "♡", "↓", "♙")
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Text(icons[index], fontSize = 21.sp) },
                                label = { Text(label, fontSize = 9.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentRed, selectedTextColor = AccentRed,
                                    indicatorColor = Color(0xFF30202A), unselectedIconColor = Gray, unselectedTextColor = Gray
                                )
                            )
                        }
                    }
                }
            }
        ) { padding ->
            Column(Modifier.fillMaxSize().background(Background).padding(padding)) {
                when {
                    offlineDownload != null -> LocalDownloadedPlayer(offlineDownload!!, onBack = { offlineDownload = null })
                    selectedAnime != null -> AnimeDetailsScreen(
                        anime = selectedAnime!!,
                        isInMyList = myListIds.contains(selectedAnime!!.id),
                        onToggleMyList = { toggleMyList(selectedAnime!!.id) },
                        onBack = { selectedAnime = null }
                    )
                    else -> {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ani", color = White, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Stream", color = AccentRed, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.weight(1f))
                            Text("HINDI", color = Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                        when (tab) {
                            0 -> HomeScreen(
                                animeList = animeList,
                                loading = loading,
                                error = error,
                                userSession = userSession,
                                myListIds = myListIds,
                                onToggleMyList = { toggleMyList(it.id) },
                                onProfileClick = { tab = 4 },
                                onAnimeClick = { selectedAnime = it }
                            )
                            1 -> SearchScreen(
                                animeList = animeList,
                                searchText = search,
                                myListIds = myListIds,
                                onToggleMyList = { toggleMyList(it.id) },
                                onSearchChange = { search = it },
                                onAnimeClick = { selectedAnime = it }
                            )
                            2 -> MyListScreen(
                                animeList = animeList,
                                myListIds = myListIds,
                                userSession = userSession,
                                syncing = myListSyncing,
                                onSyncClick = { syncMyListFromCloud() },
                                onRemoveClick = { toggleMyList(it.id) },
                                onAnimeClick = { selectedAnime = it },
                                onExploreClick = { tab = 0 },
                                onLoginClick = { tab = 4 }
                            )
                            3 -> DownloadsScreen { offlineDownload = it }
                            4 -> ProfileScreen(
                                session = userSession,
                                myListCount = myListIds.size,
                                onSessionUpdated = { newSession ->
                                    userSession = newSession
                                    if (newSession != null) saveUserSession(context, newSession)
                                    else clearUserSession(context)
                                },
                                onOpenMyList = { tab = 2 },
                                onOpenDownloads = { tab = 3 }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MyListScreen(
    animeList: List<Anime>,
    myListIds: Set<String>,
    userSession: UserSession?,
    syncing: Boolean,
    onSyncClick: () -> Unit,
    onRemoveClick: (Anime) -> Unit,
    onAnimeClick: (Anime) -> Unit,
    onExploreClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val savedAnime = animeList.filter { myListIds.contains(it.id) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("My List", color = White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (userSession != null) "Synced with Supabase • ${savedAnime.size} Anime"
                    else "Saved on device • ${savedAnime.size} Anime",
                    color = if (userSession != null) Color(0xFF4CAF50) else Gray,
                    fontSize = 11.sp
                )
            }
            if (userSession != null) {
                SmallOutlineButton(
                    text = if (syncing) "Syncing..." else "↻  Sync",
                    onClick = onSyncClick,
                    enabled = !syncing
                )
            } else {
                SmallOutlineButton(
                    text = "Login to Sync",
                    onClick = onLoginClick
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        if (savedAnime.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("♡", color = AccentRed, fontSize = 38.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Your My List is Empty", color = White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Kisi bhi Anime par 'Add to My List' dabayein taaki aap use baad mein aasani se dekh sakein.",
                    color = Gray,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = onExploreClick,
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = White)
                ) {
                    Text("EXPLORE ANIME", fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                savedAnime.forEach { anime ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceDark)
                            .clickable { onAnimeClick(anime) }
                            .padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            anime.posterUrl,
                            anime.title,
                            Modifier
                                .size(78.dp, 106.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Background),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(13.dp))
                        Column(Modifier.weight(1f)) {
                            Surface(
                                color = if (anime.animeType.equals("Movie", true)) AccentRed else Color(0xFF282A36),
                                shape = RoundedCornerShape(5.dp)
                            ) {
                                Text(
                                    anime.animeType.ifBlank { "Series" }.uppercase(),
                                    color = White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(anime.title, color = White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                anime.description.ifBlank { "Watch on AniStream Hindi" }.replace(Regex("\\<[^\\>]*\\>"), ""),
                                color = Gray,
                                fontSize = 11.sp,
                                maxLines = 2
                            )
                            Spacer(Modifier.height(9.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SmallOutlineButton(
                                    text = "▶  Watch",
                                    onClick = { onAnimeClick(anime) },
                                    selected = true
                                )
                                SmallOutlineButton(
                                    text = "✕  Remove",
                                    onClick = { onRemoveClick(anime) }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
fun ProfileScreen(
    session: UserSession?,
    myListCount: Int,
    onSessionUpdated: (UserSession?) -> Unit,
    onOpenMyList: () -> Unit,
    onOpenDownloads: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isSignUp by remember { mutableStateOf(false) }
    var fullName by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var infoMsg by remember { mutableStateOf<String?>(null) }
    var downloadsCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(session) {
        if (session != null) {
            downloadsCount = withContext(Dispatchers.IO) { readDownloads(context).size }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        if (session == null) {
            Text(
                if (isSignUp) "Create Account" else "Welcome Back",
                color = White,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                if (isSignUp) "Sign up with Supabase to sync your profile & My List."
                else "Login to access your AniStream Hindi profile & cloud My List.",
                color = Gray,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(16.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Surface(
                    color = if (!isSignUp) AccentRed else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            isSignUp = false
                            errorMsg = null
                            infoMsg = null
                        }
                ) {
                    Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text("LOGIN", color = White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
                Surface(
                    color = if (isSignUp) AccentRed else Color.Transparent,
                    shape = RoundedCornerShape(9.dp),
                    modifier = Modifier
                        .weight(1f)
                        .clickable {
                            isSignUp = true
                            errorMsg = null
                            infoMsg = null
                        }
                ) {
                    Box(Modifier.padding(vertical = 10.dp), contentAlignment = Alignment.Center) {
                        Text("SIGN UP", color = White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (isSignUp) {
                    OutlinedTextField(
                        value = fullName,
                        onValueChange = { fullName = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Full Name", color = Gray) },
                        placeholder = { Text("Enter your name", color = Gray) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = AccentRed,
                            unfocusedBorderColor = Gray,
                            cursorColor = AccentRed
                        )
                    )
                }

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Email Address", color = Gray) },
                    placeholder = { Text("name@example.com", color = Gray) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = Gray,
                        cursorColor = AccentRed
                    )
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Password", color = Gray) },
                    placeholder = { Text("Minimum 6 characters", color = Gray) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        Text(
                            if (showPassword) "HIDE" else "SHOW",
                            color = AccentRed,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier
                                .clickable { showPassword = !showPassword }
                                .padding(horizontal = 10.dp)
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = Gray,
                        cursorColor = AccentRed
                    )
                )

                if (isSignUp) {
                    OutlinedTextField(
                        value = confirmPassword,
                        onValueChange = { confirmPassword = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Confirm Password", color = Gray) },
                        placeholder = { Text("Re-enter password", color = Gray) },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = White,
                            unfocusedTextColor = White,
                            focusedBorderColor = AccentRed,
                            unfocusedBorderColor = Gray,
                            cursorColor = AccentRed
                        )
                    )
                }

                errorMsg?.let {
                    Surface(color = Color(0xFF2D1219), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            it,
                            color = AccentRed,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                infoMsg?.let {
                    Surface(color = Color(0xFF112A1B), shape = RoundedCornerShape(8.dp)) {
                        Text(
                            it,
                            color = Color(0xFF4CAF50),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(10.dp)
                        )
                    }
                }

                Button(
                    onClick = {
                        errorMsg = null
                        infoMsg = null
                        val cleanEmail = email.trim()
                        when {
                            isSignUp && fullName.trim().length < 2 -> errorMsg = "Kripya apna naam likhein."
                            !cleanEmail.contains("@") || !cleanEmail.contains(".") -> errorMsg = "Valid email address dalein."
                            password.length < 6 -> errorMsg = "Password kam se kam 6 characters ka hona chahiye."
                            isSignUp && password != confirmPassword -> errorMsg = "Password aur Confirm Password match nahi kar rahe."
                            else -> {
                                loading = true
                                scope.launch {
                                    try {
                                        if (isSignUp) {
                                            val (newSession, message) = supabaseSignUp(fullName, cleanEmail, password)
                                            if (newSession != null) {
                                                onSessionUpdated(newSession)
                                            } else {
                                                infoMsg = message
                                                isSignUp = false
                                                password = ""
                                                confirmPassword = ""
                                            }
                                        } else {
                                            val loggedIn = supabaseSignIn(cleanEmail, password)
                                            onSessionUpdated(loggedIn)
                                        }
                                    } catch (e: Exception) {
                                        errorMsg = e.message ?: "Authentication fail ho gaya."
                                    } finally {
                                        loading = false
                                    }
                                }
                            }
                        }
                    },
                    enabled = !loading,
                    modifier = Modifier.fillMaxWidth().height(46.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = White)
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (isSignUp) "CREATE ACCOUNT" else "LOGIN",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        } else {
            val initial = (session.fullName.firstOrNull() ?: session.email.firstOrNull() ?: 'A').uppercaseChar()

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(SurfaceDark)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(62.dp)
                        .clip(CircleShape)
                        .background(AccentRed),
                    contentAlignment = Alignment.Center
                ) {
                    Text(initial.toString(), color = White, fontSize = 26.sp, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Surface(color = Color(0xFF163222), shape = RoundedCornerShape(5.dp)) {
                        Text(
                            "VERIFIED MEMBER",
                            color = Color(0xFF4CAF50),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    Spacer(Modifier.height(5.dp))
                    Text(
                        session.fullName.ifBlank { "AniStream User" },
                        color = White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(session.email, color = Gray, fontSize = 12.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(16.dp)
            ) {
                Text("ACCOUNT DETAILS", color = AccentRed, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(10.dp))
                InfoLine("Full Name", session.fullName.ifBlank { "Not set" })
                InfoLine("Email", session.email)
                InfoLine("User ID", session.userId.ifBlank { "N/A" })
                InfoLine("Joined On", session.createdAt.ifBlank { "Active" })
                InfoLine("My List Saved", "$myListCount Anime")
                InfoLine("Saved Downloads", "$downloadsCount Episodes")
            }

            Spacer(Modifier.height(14.dp))

            infoMsg?.let {
                Text(it, color = Color(0xFF4CAF50), fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
            }
            errorMsg?.let {
                Text(it, color = AccentRed, fontSize = 12.sp, modifier = Modifier.padding(bottom = 10.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                SmallOutlineButton(
                    text = "♡  My List ($myListCount)",
                    onClick = onOpenMyList,
                    modifier = Modifier.weight(1f)
                )
                SmallOutlineButton(
                    text = "↓  Downloads",
                    onClick = onOpenDownloads,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(10.dp))

            SmallOutlineButton(
                text = "↻  Sync Profile & Cloud Data",
                onClick = {
                    loading = true
                    errorMsg = null
                    infoMsg = null
                    scope.launch {
                        try {
                            val refreshed = supabaseFetchProfile(session)
                            onSessionUpdated(refreshed)
                            infoMsg = "Profile details sync ho gayi hain."
                        } catch (e: Exception) {
                            errorMsg = e.message ?: "Sync fail ho gaya."
                        } finally {
                            loading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !loading
            )

            Spacer(Modifier.height(14.dp))

            Button(
                onClick = {
                    loading = true
                    scope.launch {
                        supabaseLogout(session.accessToken)
                        loading = false
                        onSessionUpdated(null)
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth().height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AccentRed, contentColor = White)
            ) {
                Text(
                    if (loading) "LOGGING OUT..." else "LOGOUT",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

@Composable
fun HomeScreen(
    animeList: List<Anime>,
    loading: Boolean,
    error: String?,
    userSession: UserSession?,
    myListIds: Set<String>,
    onToggleMyList: (Anime) -> Unit,
    onProfileClick: () -> Unit,
    onAnimeClick: (Anime) -> Unit
) {
    val movies = animeList.filter { it.animeType.equals("Movie", true) }
    val series = animeList.filter { !it.animeType.equals("Movie", true) }
    val featured = animeList.firstOrNull()
    val badgeLetter = (userSession?.fullName?.firstOrNull() ?: userSession?.email?.firstOrNull() ?: 'A').uppercaseChar()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    if (userSession != null) "HELLO, ${userSession.fullName.uppercase()}" else "WELCOME BACK",
                    color = AccentRed,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp
                )
                Text("Discover Anime", color = White, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Text(badgeLetter.toString(), color = AccentRed, fontSize = 21.sp, fontWeight = FontWeight.Black)
            }
        }
        Spacer(Modifier.height(12.dp))
        when {
            loading -> Box(Modifier.fillMaxWidth().height(230.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentRed) }
            error != null -> Column(Modifier.padding(horizontal = 18.dp, vertical = 20.dp)) {
                Text("Unable to load anime", color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(error, color = AccentRed, fontSize = 12.sp)
            }
            featured == null -> Box(Modifier.fillMaxWidth().height(210.dp), contentAlignment = Alignment.Center) {
                Text("No published anime found yet.", color = Gray, fontSize = 13.sp)
            }
            else -> {
                val isFeaturedSaved = myListIds.contains(featured.id)
                Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(228.dp).clip(RoundedCornerShape(22.dp)).background(SurfaceDark).clickable { onAnimeClick(featured) }) {
                    AsyncImage(featured.posterUrl, featured.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    Box(Modifier.fillMaxSize().background(Color(0xCC050509)))
                    Column(Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(18.dp)) {
                        Surface(color = AccentRed, shape = RoundedCornerShape(5.dp)) {
                            Text("FEATURED ANIME", color = White, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
                        }
                        Spacer(Modifier.height(9.dp))
                        Text(featured.title, color = White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2)
                        Spacer(Modifier.height(5.dp))
                        Text(featured.description.ifBlank { "Explore this anime on AniStream Hindi." }.replace(Regex("\\<[^\\>]*\\>"), ""), color = Color(0xFFD0D0D5), fontSize = 11.sp, maxLines = 2)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Surface(color = White, shape = RoundedCornerShape(8.dp), modifier = Modifier.clickable { onAnimeClick(featured) }) {
                                Text("▶  VIEW DETAILS", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp))
                            }
                            Surface(
                                color = if (isFeaturedSaved) AccentRed else Color(0xFF252733),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.clickable { onToggleMyList(featured) }
                            ) {
                                Text(
                                    if (isFeaturedSaved) "✓  IN MY LIST" else "+  MY LIST",
                                    color = White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                AnimeSection("Recently Added", animeList, onAnimeClick)
                AnimeSection("Anime Series", series, onAnimeClick)
                if (movies.isNotEmpty()) AnimeSection("Anime Movies", movies, onAnimeClick)
                Column(Modifier.padding(horizontal = 18.dp).padding(bottom = 18.dp)) {
                    Text("ANISTREAM HINDI", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp)
                    Text("Your anime world, all in one place.", color = Gray, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun AnimeSection(title: String, animeList: List<Anime>, onAnimeClick: (Anime) -> Unit) {
    if (animeList.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text("SWIPE  ›", color = Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 18.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            animeList.forEach { anime -> AnimeCard(anime) { onAnimeClick(anime) } }
        }
    }
}

@Composable
fun AnimeCard(anime: Anime, onClick: () -> Unit) {
    Column(Modifier.width(126.dp).clickable { onClick() }) {
        Box(Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(13.dp)).background(SurfaceDark)) {
            AsyncImage(anime.posterUrl, anime.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (anime.animeType.equals("Movie", true)) {
                Surface(Modifier.align(Alignment.TopStart).padding(7.dp), color = AccentRed, shape = RoundedCornerShape(5.dp)) {
                    Text("MOVIE", color = White, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp))
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(anime.title, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2)
    }
}

@Composable
fun SearchScreen(
    animeList: List<Anime>,
    searchText: String,
    myListIds: Set<String>,
    onToggleMyList: (Anime) -> Unit,
    onSearchChange: (String) -> Unit,
    onAnimeClick: (Anime) -> Unit
) {
    val filtered = animeList.filter { it.title.contains(searchText, true) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Search Anime", color = White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            searchText, onSearchChange, Modifier.fillMaxWidth(),
            placeholder = { Text("Search anime...", color = Gray) }, singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = AccentRed,
                unfocusedBorderColor = Gray, cursorColor = AccentRed
            )
        )
        Spacer(Modifier.height(14.dp))
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(11.dp)) {
            filtered.forEach { anime ->
                val isSaved = myListIds.contains(anime.id)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(SurfaceDark)
                        .clickable { onAnimeClick(anime) }
                        .padding(9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        anime.posterUrl,
                        anime.title,
                        Modifier.size(64.dp, 86.dp).clip(RoundedCornerShape(8.dp)).background(Background),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(anime.title, color = White, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                        Spacer(Modifier.height(4.dp))
                        Text(anime.animeType.ifBlank { "Series" }, color = Gray, fontSize = 11.sp)
                    }
                    SmallOutlineButton(
                        text = if (isSaved) "♥ Saved" else "+ List",
                        onClick = { onToggleMyList(anime) },
                        selected = isSaved
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun AnimeDetailsScreen(
    anime: Anime,
    isInMyList: Boolean,
    onToggleMyList: () -> Unit,
    onBack: () -> Unit
) {
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
        if (season == null) episodes = emptyList() else {
            episodesLoading = true
            episodesError = null
            try { episodes = fetchEpisodes(season.id) } catch (e: Exception) { episodesError = e.message; episodes = emptyList() }
            episodesLoading = false
        }
    }
    val episodeIndex = episodes.indexOfFirst { it.id == playingEpisode?.id }
    if (playingEpisode != null) {
        PlayerScreen(playingEpisode!!, episodes, episodeIndex, onBack = { playingEpisode = null }) {
            if (it in episodes.indices) playingEpisode = episodes[it]
        }
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 22.dp)) {
        Text("‹  Back", color = White, fontSize = 17.sp, modifier = Modifier.clickable { onBack() }.padding(horizontal = 16.dp, vertical = 11.dp))
        AsyncImage(details?.banner?.takeIf { it.isNotBlank() } ?: anime.posterUrl, anime.title, Modifier.fillMaxWidth().height(165.dp).padding(horizontal = 16.dp).clip(RoundedCornerShape(15.dp)).background(SurfaceDark), contentScale = ContentScale.Crop)
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(anime.posterUrl, anime.title, Modifier.width(108.dp).height(148.dp).clip(RoundedCornerShape(11.dp)).background(SurfaceDark), contentScale = ContentScale.Crop)
            Spacer(Modifier.width(13.dp))
            Column(Modifier.weight(1f)) {
                Text(details?.englishTitle?.takeIf { it.isNotBlank() } ?: anime.title, color = White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
                Text(details?.japaneseTitle.orEmpty(), color = Gray, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Text("⭐ ${details?.rating ?: "Loading..."}", color = AccentRed, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Year: ${details?.year ?: "Loading..."}", color = Gray, fontSize = 11.sp)
                Text("Episodes: ${details?.episodes ?: "Loading..."}", color = Gray, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                SmallOutlineButton(
                    text = if (isInMyList) "♥  In My List (Remove)" else "♡  Add to My List",
                    onClick = onToggleMyList,
                    modifier = Modifier.fillMaxWidth(),
                    selected = isInMyList
                )
            }
        }
        if (detailsLoading) CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp), color = AccentRed)
        else if (detailsError != null) {
            Text("Extra details unavailable. $detailsError", color = Gray, modifier = Modifier.padding(horizontal = 16.dp))
            DetailSection("Story") { Text(anime.description, color = Gray, fontSize = 13.sp) }
        } else details?.let { info ->
            DetailSection("Story") { Text(info.description.replace(Regex("\\<[^\\>]*\\>"), ""), color = Gray, fontSize = 13.sp) }
            DetailSection("Anime Information") {
                InfoLine("Release", info.year); InfoLine("Status", info.status); InfoLine("Studio", info.studio)
                InfoLine("Episodes", info.episodes); InfoLine("Rating", info.rating)
            }
            DetailSection("Genres") { Text(info.genres.joinToString("  •  "), color = AccentRed, fontSize = 12.sp) }
            if (info.characters.isNotEmpty()) DetailSection("Characters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    info.characters.forEach { c ->
                        Column(Modifier.width(92.dp)) {
                            AsyncImage(c.image, c.name, Modifier.fillMaxWidth().height(116.dp).clip(RoundedCornerShape(9.dp)).background(SurfaceDark), contentScale = ContentScale.Crop)
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
                        Surface(color = if (selected) AccentRed else SurfaceDark, shape = RoundedCornerShape(9.dp), modifier = Modifier.clickable { selectedSeason = s }) {
                            Text(s.title.ifBlank { "Season ${s.seasonNumber}" }, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 13.dp, vertical = 9.dp))
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
    Column(Modifier.width(112.dp).clip(RoundedCornerShape(12.dp)).background(if (selected) Color(0xFF35101C) else SurfaceDark).clickable { onClick() }.padding(11.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("EP ${episode.episodeNumber}", color = AccentRed, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text("▶", color = White, fontSize = 13.sp)
        }
        Spacer(Modifier.height(7.dp))
        Text(episode.title.ifBlank { "Episode ${episode.episodeNumber}" }, color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2, minLines = 2)
    }
}

@Composable
fun PlayerScreen(episode: AnimeEpisode, episodes: List<AnimeEpisode>, episodeIndex: Int, onBack: () -> Unit, onEpisodeChange: (Int) -> Unit) {
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
    val qualities = languageServers.map { it.quality }.distinct().sortedBy {
        listOf("1080p", "720p", "480p").indexOf(it).let { n -> if (n < 0) 99 else n }
    }
    val selected = languageServers.firstOrNull { it.quality.equals(quality, true) } ?: languageServers.firstOrNull()

    Column(Modifier.fillMaxSize().background(Background).then(if (fullscreen) Modifier else Modifier.verticalScroll(rememberScrollState()))) {
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth().background(SurfaceDark).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", color = White, fontSize = 27.sp, modifier = Modifier.clickable { onBack() })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("ANISTREAM PLAYER", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Episode ${episode.episodeNumber}", color = Gray, fontSize = 10.sp)
                }
                SmallOutlineButton("⛶", onClick = { fullscreen = true })
            }
            Text(episode.title.ifBlank { "Episode ${episode.episodeNumber}" }, color = White, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(15.dp))
        }
        when {
            loading -> Box(Modifier.fillMaxWidth().height(if (fullscreen) 1.dp else 220.dp).background(Color.Black), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = AccentRed) }
            error != null -> Box(Modifier.fillMaxWidth().height(220.dp).background(Color.Black), contentAlignment = Alignment.Center) { Text(error ?: "Video unavailable", color = White) }
            selected != null -> WatchPlayer(selected.videoUrl, episode.id, fullscreen) { fullscreen = false }
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
                SmallOutlineButton(
                    if (dlUrl.isBlank()) "↓  Download link unavailable" else "↓  Download Episode",
                    onClick = {
                        try {
                            startDownload(context, dlUrl, "AniStream_Ep_${episode.episodeNumber}_${selected?.quality}")
                            downloadMessage = "Download start ho gaya. Progress Downloads tab mein dekho."
                        } catch (e: Exception) { downloadMessage = e.message ?: "Download start nahi hua." }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = dlUrl.isNotBlank()
                )
                downloadMessage?.let { Text(it, color = AccentRed, fontSize = 11.sp, modifier = Modifier.padding(top = 7.dp)) }
                selected?.let { Text("Server: ${it.serverName}  •  ${it.quality}", color = Gray, fontSize = 10.sp, modifier = Modifier.padding(top = 9.dp)) }
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
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, true)
                WindowInsetsControllerCompat(it, it.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.requestedOrientation = oldOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, true)
                WindowInsetsControllerCompat(it, it.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
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
                    settings.loadsImagesAutomatically = true
                    webChromeClient = WebChromeClient()
                    webViewClient = WebViewClient()
                    loadUrl(videoUrl)
                }
            },
            update = { if (it.url != videoUrl) it.loadUrl(videoUrl) },
            modifier = Modifier.fillMaxWidth().height(if (fullscreen) 300.dp else 230.dp).background(Color.Black)
        )
    } else {
        val player = remember(context, episodeId) { ExoPlayer.Builder(context).build() }
        DisposableEffect(player) { onDispose { player.release() } }
        LaunchedEffect(player, videoUrl) {
            player.stop()
            player.clearMediaItems()
            player.setMediaItem(MediaItem.fromUri(videoUrl))
            player.prepare()
            player.playWhenReady = true
        }
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply {
                setPlayer(player)
                useController = true
                setControllerShowTimeoutMs(3500)
                setControllerHideOnTouch(true)
                setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
            } },
            update = { it.setPlayer(player); it.useController = true },
            modifier = Modifier.fillMaxWidth().then(if (fullscreen) Modifier.fillMaxHeight() else Modifier.height(230.dp)).background(Color.Black)
        )
    }
    if (fullscreen) Box(Modifier.fillMaxWidth().padding(6.dp), contentAlignment = Alignment.TopEnd) {
        SmallOutlineButton("✕", onExitFullscreen)
    }
}

@Composable
fun DownloadsScreen(onPlay: (DownloadInfo) -> Unit) {
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
                    Text(item.status, color = when (item.status) {
                        "Completed" -> Color(0xFF4CAF50)
                        "Failed" -> AccentRed
                        else -> Gray
                    }, fontSize = 11.sp)
                    Spacer(Modifier.weight(1f))
                    Text("${if (item.status == "Completed") 100 else item.progress}%", color = AccentRed, fontSize = 11.sp)
                }
                if (item.status == "Downloading" || item.status == "Waiting" || item.status == "Paused") {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(progress = { item.progress / 100f }, modifier = Modifier.fillMaxWidth(), color = AccentRed, trackColor = Color(0xFF33343A))
                }
                if (item.status == "Completed") {
                    Spacer(Modifier.height(10.dp))
                    SmallOutlineButton("▶  PLAY OFFLINE", onClick = { if (item.localUri.isNotBlank()) onPlay(item) }, modifier = Modifier.fillMaxWidth(), enabled = item.localUri.isNotBlank())
                    if (item.localUri.isBlank()) Text("Downloaded file ka URI nahi mil raha.", color = AccentRed, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun LocalDownloadedPlayer(download: DownloadInfo, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = context as? Activity
    var fullscreen by remember { mutableStateOf(false) }
    val player = remember(download.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(download.localUri)))
            prepare()
            playWhenReady = true
        }
    }
    BackHandler(enabled = fullscreen) { fullscreen = false }
    DisposableEffect(player) { onDispose { player.release() } }
    DisposableEffect(fullscreen) {
        val window = activity?.window
        val oldOrientation = activity?.requestedOrientation
        if (fullscreen) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, false)
                WindowInsetsControllerCompat(it, it.decorView).hide(WindowInsetsCompat.Type.systemBars())
            }
        } else {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, true)
                WindowInsetsControllerCompat(it, it.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            activity?.requestedOrientation = oldOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            window?.let {
                WindowCompat.setDecorFitsSystemWindows(it, true)
                WindowInsetsControllerCompat(it, it.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (!fullscreen) {
            Row(Modifier.fillMaxWidth().background(SurfaceDark).padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("‹", color = White, fontSize = 27.sp, modifier = Modifier.clickable { onBack() })
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("OFFLINE PLAYER", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(download.title, color = White, fontSize = 11.sp, maxLines = 1)
                }
                SmallOutlineButton("⛶", onClick = { fullscreen = true })
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            AndroidView(
                factory = { ctx -> PlayerView(ctx).apply {
                    setPlayer(player)
                    useController = true
                    setControllerShowTimeoutMs(3500)
                    setControllerHideOnTouch(true)
                    setShowBuffering(PlayerView.SHOW_BUFFERING_WHEN_PLAYING)
                } },
                update = { it.setPlayer(player); it.useController = true },
                modifier = Modifier.fillMaxSize()
            )
            if (fullscreen) SmallOutlineButton("✕", onClick = { fullscreen = false }, modifier = Modifier.align(Alignment.TopEnd).padding(12.dp))
        }
        if (!fullscreen) Text("✓  Saved on your device • Offline playback", color = Color(0xFF4CAF50), fontSize = 11.sp, modifier = Modifier.padding(14.dp))
    }
}

@Composable
fun SmallOutlineButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, selected: Boolean = false) {
    OutlinedButton(
        onClick = onClick, modifier = modifier.heightIn(min = 36.dp), enabled = enabled,
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(9.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (selected) AccentRed else Color(0xFF777982)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = if (selected) AccentRed else White, disabledContentColor = Gray)
    ) { Text(text, fontSize = 11.sp, maxLines = 1) }
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
