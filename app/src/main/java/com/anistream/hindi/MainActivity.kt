package com.anistream.hindi

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

private const val SUPABASE_URL = "https://cyftkgouolxcverswvpv.supabase.co"
private const val SUPABASE_KEY = "sb_publishable_h-Alm01lWCWvvlWcW-Uqyg_iAub4QH3"
private const val AUTH_PREFS = "anistream_auth_prefs"
private const val MY_LIST_PREFS = "anistream_my_list_prefs"
private const val DOWNLOAD_PREFS = "anistream_download_prefs"

private val Background = Color(0xFF08090D)
private val SurfaceDark = Color(0xFF14151C)
private val AccentRed = Color(0xFFFF1744)
private val SeekBlue = Color(0xFF5AB4FF)
private val White = Color.White
private val Gray = Color(0xFF999BA5)

private val PRESET_ANIME_AVATARS = listOf(
    "https://api.dicebear.com/9.x/adventurer/png?seed=Obito&backgroundColor=b6e3f4",
    "https://api.dicebear.com/9.x/adventurer/png?seed=Gojo&backgroundColor=c0aede",
    "https://api.dicebear.com/9.x/adventurer/png?seed=Naruto&backgroundColor=ffd5dc",
    "https://api.dicebear.com/9.x/adventurer/png?seed=Luffy&backgroundColor=ffdfbf",
    "https://api.dicebear.com/9.x/adventurer/png?seed=Jinwoo&backgroundColor=d1d4f9",
    "https://api.dicebear.com/9.x/adventurer/png?seed=Tanjiro&backgroundColor=b6e3f4",
    "https://api.dicebear.com/9.x/adventurer/png?seed=Goku&backgroundColor=ffdfbf",
    "https://api.dicebear.com/9.x/adventurer/png?seed=Nezuko&backgroundColor=ffd5dc"
)

private val animeDetailsCache = mutableMapOf<String, AnimeDetails>()
private val seasonsCache = mutableMapOf<String, List<AnimeSeason>>()
private val episodesCache = mutableMapOf<String, List<AnimeEpisode>>()
private val videoServersCache = mutableMapOf<String, List<VideoServer>>()

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
data class DownloadInfo(
    val id: Long,
    val title: String,
    val status: String,
    val progress: Int,
    val localUri: String = "",
    val sizeText: String = "",
    val sourceUrl: String = ""
)
data class UserSession(
    val userId: String,
    val email: String,
    val fullName: String,
    val accessToken: String,
    val createdAt: String,
    val avatarUrl: String = ""
)
data class AnimeComment(
    val id: String,
    val animeId: String,
    val userId: String,
    val userName: String,
    val content: String,
    val adminReply: String,
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
        connection.connectTimeout = 12000
        connection.readTimeout = 15000
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
    val prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
    val finalAvatar = session.avatarUrl.ifBlank { prefs.getString("avatar_backup_${session.userId}", "").orEmpty() }
    prefs.edit()
        .putString("user_id", session.userId)
        .putString("email", session.email)
        .putString("full_name", session.fullName)
        .putString("access_token", session.accessToken)
        .putString("created_at", session.createdAt)
        .putString("avatar_url", finalAvatar)
        .putString("avatar_backup_${session.userId}", finalAvatar)
        .apply()
}

private fun loadUserSession(context: Context): UserSession? {
    val prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
    val token = prefs.getString("access_token", null)?.takeIf { it.isNotBlank() } ?: return null
    val email = prefs.getString("email", "").orEmpty()
    val userId = prefs.getString("user_id", "").orEmpty()
    val fullName = prefs.getString("full_name", "").orEmpty()
    val createdAt = prefs.getString("created_at", "").orEmpty()
    val avatarUrl = prefs.getString("avatar_url", "").orEmpty()
        .ifBlank { prefs.getString("avatar_backup_$userId", "").orEmpty() }
    return UserSession(userId, email, fullName, token, createdAt, avatarUrl)
}

private fun clearUserSession(context: Context) {
    val prefs = context.getSharedPreferences(AUTH_PREFS, Context.MODE_PRIVATE)
    prefs.edit()
        .remove("user_id")
        .remove("email")
        .remove("full_name")
        .remove("access_token")
        .remove("created_at")
        .remove("avatar_url")
        .apply()
}

private fun uriToBase64Avatar(context: Context, uri: Uri): String? {
    return runCatching {
        val inputStream = context.contentResolver.openInputStream(uri) ?: return null
        val original = BitmapFactory.decodeStream(inputStream)
        inputStream.close()
        if (original == null) return null

        val minSide = minOf(original.width, original.height)
        val cropX = (original.width - minSide) / 2
        val cropY = (original.height - minSide) / 2
        val square = Bitmap.createBitmap(original, cropX, cropY, minSide, minSide)
        val scaled = Bitmap.createScaledBitmap(square, 180, 180, true)

        val output = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, 82, output)
        val base64 = Base64.encodeToString(output.toByteArray(), Base64.NO_WRAP)
        "data:image/jpeg;base64,$base64"
    }.getOrNull()
}

private fun decodeBase64ToBitmap(dataUri: String): Bitmap? {
    return runCatching {
        val cleanBase64 = dataUri.substringAfter("base64,", "")
        if (cleanBase64.isBlank()) return null
        val bytes = Base64.decode(cleanBase64, Base64.DEFAULT)
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }.getOrNull()
}

@Composable
fun UserAvatarView(
    avatarUrl: String,
    fallbackInitial: Char,
    size: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier
) {
    val base64Bitmap = remember(avatarUrl) {
        if (avatarUrl.startsWith("data:image")) decodeBase64ToBitmap(avatarUrl) else null
    }

    Box(
        modifier = modifier
            .size(size)
            .background(AccentRed),
        contentAlignment = Alignment.Center
    ) {
        when {
            base64Bitmap != null -> {
                Image(
                    bitmap = base64Bitmap.asImageBitmap(),
                    contentDescription = "Profile Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            avatarUrl.startsWith("http://") || avatarUrl.startsWith("https://") || avatarUrl.startsWith("content://") -> {
                AsyncImage(
                    model = avatarUrl,
                    contentDescription = "Profile Photo",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                Text(
                    text = fallbackInitial.toString(),
                    color = White,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
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
    runCatching {
        val url = "$SUPABASE_URL/rest/v1/my_list?select=anime_id&user_id=eq.${Uri.encode(session.userId)}&order=created_at.desc"
        val res = request(url, method = "GET", supabase = true, bearerToken = session.accessToken)
        val arr = JSONArray(res)
        for (i in 0 until arr.length()) {
            val id = arr.getJSONObject(i).optString("anime_id")
            if (id.isNotBlank() && id != "null") combined.add(id)
        }
    }
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

private suspend fun supabaseUpdateAvatar(session: UserSession, newAvatarUrl: String): UserSession = withContext(Dispatchers.IO) {
    val body = JSONObject().apply {
        put("data", JSONObject().put("avatar_url", newAvatarUrl))
    }
    val res = request(
        "$SUPABASE_URL/auth/v1/user",
        method = "PUT",
        body = body.toString(),
        supabase = true,
        bearerToken = session.accessToken
    )
    parseUserSession(JSONObject(res), fallbackToken = session.accessToken, fallbackAvatar = newAvatarUrl)
}

private suspend fun supabaseAddToMyList(session: UserSession, animeId: String, allIds: Set<String>) = withContext(Dispatchers.IO) {
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
    supabaseSyncMyListMetadata(session, allIds)
}

private suspend fun supabaseRemoveFromMyList(session: UserSession, animeId: String, allIds: Set<String>) = withContext(Dispatchers.IO) {
    runCatching {
        val url = "$SUPABASE_URL/rest/v1/my_list?user_id=eq.${Uri.encode(session.userId)}&anime_id=eq.${Uri.encode(animeId)}"
        request(
            url,
            method = "DELETE",
            supabase = true,
            bearerToken = session.accessToken
        )
    }
    supabaseSyncMyListMetadata(session, allIds)
}

/* COMMENTS & ADMIN REPLY SUPABASE API */
private suspend fun fetchCommentsForAnime(animeId: String): List<AnimeComment> = withContext(Dispatchers.IO) {
    val url = "$SUPABASE_URL/rest/v1/comments?select=*&anime_id=eq.${Uri.encode(animeId)}&order=created_at.desc"
    val res = request(url, method = "GET", supabase = true)
    val arr = JSONArray(res)
    (0 until arr.length()).mapNotNull { i ->
        val obj = arr.getJSONObject(i)
        val rawContent = obj.optString("content").takeUnless { it == "null" }.orEmpty()
            .ifBlank { obj.optString("comment_text").takeUnless { it == "null" }.orEmpty() }
            .ifBlank { obj.optString("comment").takeUnless { it == "null" }.orEmpty() }
        if (rawContent.isBlank()) return@mapNotNull null

        val userId = obj.optString("user_id").takeUnless { it == "null" }.orEmpty()
        val explicitName = obj.optString("user_name").takeUnless { it == "null" }.orEmpty()
            .ifBlank { obj.optString("username").takeUnless { it == "null" }.orEmpty() }

        val (parsedName, cleanContent) = if (explicitName.isBlank() && rawContent.startsWith("[") && rawContent.contains("] ")) {
            val endBracket = rawContent.indexOf("] ")
            val extractedName = rawContent.substring(1, endBracket).trim()
            val remainingText = rawContent.substring(endBracket + 2).trim()
            extractedName to remainingText
        } else {
            val fallbackName = explicitName.ifBlank {
                if (userId.length > 8) "User • ${userId.take(6)}" else userId.ifBlank { "Anime Fan" }
            }
            fallbackName to rawContent
        }

        val reply = obj.optString("admin_reply").takeUnless { it == "null" }.orEmpty()
        val created = obj.optString("created_at").takeUnless { it == "null" }.orEmpty().take(10)

        AnimeComment(
            id = obj.optString("id"),
            animeId = obj.optString("anime_id"),
            userId = userId,
            userName = parsedName,
            content = cleanContent,
            adminReply = reply,
            createdAt = created
        )
    }
}

private suspend fun postAnimeComment(
    animeId: String,
    commentText: String,
    session: UserSession
) = withContext(Dispatchers.IO) {
    val displayName = session.fullName.ifBlank { session.email.substringBefore("@") }.ifBlank { "Member" }
    val taggedContent = "[$displayName] ${commentText.trim()}"

    val primaryPayload = JSONObject().apply {
        put("anime_id", animeId)
        put("user_id", session.userId)
        put("content", taggedContent)
    }

    val primaryAttempt = runCatching {
        request(
            "$SUPABASE_URL/rest/v1/comments",
            method = "POST",
            body = primaryPayload.toString(),
            supabase = true,
            bearerToken = session.accessToken,
            extraHeaders = mapOf("Prefer" to "return=minimal")
        )
    }

    if (primaryAttempt.isFailure) {
        val fallbackPayload = JSONObject().apply {
            put("anime_id", animeId)
            put("user_id", session.userId)
            put("comment_text", taggedContent)
        }
        request(
            "$SUPABASE_URL/rest/v1/comments",
            method = "POST",
            body = fallbackPayload.toString(),
            supabase = true,
            bearerToken = session.accessToken,
            extraHeaders = mapOf("Prefer" to "return=minimal")
        )
    }
}

private suspend fun deleteAnimeComment(commentId: String, session: UserSession) = withContext(Dispatchers.IO) {
    val url = "$SUPABASE_URL/rest/v1/comments?id=eq.${Uri.encode(commentId)}"
    request(
        url,
        method = "DELETE",
        supabase = true,
        bearerToken = session.accessToken
    )
}

private fun parseUserSession(root: JSONObject, fallbackToken: String = "", fallbackAvatar: String = ""): UserSession {
    val token = root.optString("access_token").ifBlank { fallbackToken }
    val userObj = root.optJSONObject("user") ?: root
    val userId = userObj.optString("id")
    val email = userObj.optString("email")
    val createdAt = userObj.optString("created_at").take(10)
    val metadata = userObj.optJSONObject("user_metadata")
    val fullName = metadata?.optString("full_name").orEmpty()
        .ifBlank { metadata?.optString("name").orEmpty() }
        .ifBlank { email.substringBefore("@") }
    val avatarUrl = metadata?.optString("avatar_url").orEmpty()
        .takeUnless { it == "null" }.orEmpty()
        .ifBlank { fallbackAvatar }
    return UserSession(userId, email, fullName, token, createdAt, avatarUrl)
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
    parseUserSession(JSONObject(res), fallbackToken = session.accessToken, fallbackAvatar = session.avatarUrl)
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

private suspend fun fetchSeasons(animeId: String, forceRefresh: Boolean = false): List<AnimeSeason> = withContext(Dispatchers.IO) {
    if (!forceRefresh) {
        seasonsCache[animeId]?.let { return@withContext it }
    }
    val url = "$SUPABASE_URL/rest/v1/seasons?select=id,anime_id,season_number,title&anime_id=eq.${Uri.encode(animeId)}&order=season_number.asc"
    val a = JSONArray(request(url, supabase = true))
    val list = (0 until a.length()).map { i ->
        val x = a.getJSONObject(i)
        AnimeSeason(x.optString("id"), x.optString("anime_id"), x.optInt("season_number"), x.optString("title"))
    }
    seasonsCache[animeId] = list
    list
}

private suspend fun fetchEpisodes(seasonId: String, forceRefresh: Boolean = false): List<AnimeEpisode> = withContext(Dispatchers.IO) {
    if (!forceRefresh) {
        episodesCache[seasonId]?.let { return@withContext it }
    }
    val url = "$SUPABASE_URL/rest/v1/episodes?select=id,episode_number,title,description&season_id=eq.${Uri.encode(seasonId)}&is_published=eq.true&order=episode_number.asc"
    val a = JSONArray(request(url, supabase = true))
    val list = (0 until a.length()).map { i ->
        val x = a.getJSONObject(i)
        AnimeEpisode(x.optString("id"), x.optInt("episode_number"), x.optString("title"), x.optString("description"))
    }
    episodesCache[seasonId] = list
    list
}

private suspend fun fetchVideoServers(episodeId: String, forceRefresh: Boolean = false): List<VideoServer> = withContext(Dispatchers.IO) {
    if (!forceRefresh) {
        videoServersCache[episodeId]?.takeIf { it.isNotEmpty() }?.let { return@withContext it }
    }
    val url = "$SUPABASE_URL/rest/v1/video_servers?select=id,episode_id,server_name,video_url,download_url,quality,language&episode_id=eq.${Uri.encode(episodeId)}"
    val a = JSONArray(request(url, supabase = true))
    val list = (0 until a.length()).mapNotNull { i ->
        val x = a.getJSONObject(i)
        val watch = x.optString("video_url").takeUnless { it.isBlank() || it == "null" } ?: return@mapNotNull null
        VideoServer(
            x.optString("id"), x.optString("episode_id"), x.optString("server_name"), watch,
            x.optString("download_url").takeUnless { it == "null" }.orEmpty(),
            x.optString("quality"), x.optString("language").ifBlank { "Hindi" }
        )
    }
    if (list.isNotEmpty()) videoServersCache[episodeId] = list
    list
}

private suspend fun prefetchVideoServersForSeason(episodes: List<AnimeEpisode>) = withContext(Dispatchers.IO) {
    val missingIds = episodes.map { it.id }.filter { !videoServersCache.containsKey(it) }
    if (missingIds.isEmpty()) return@withContext
    runCatching {
        val joined = missingIds.joinToString(",") { Uri.encode(it) }
        val url = "$SUPABASE_URL/rest/v1/video_servers?select=id,episode_id,server_name,video_url,download_url,quality,language&episode_id=in.($joined)"
        val a = JSONArray(request(url, supabase = true))
        val grouped = mutableMapOf<String, MutableList<VideoServer>>()
        for (i in 0 until a.length()) {
            val x = a.getJSONObject(i)
            val watch = x.optString("video_url").takeUnless { it.isBlank() || it == "null" } ?: continue
            val epId = x.optString("episode_id")
            val server = VideoServer(
                x.optString("id"), epId, x.optString("server_name"), watch,
                x.optString("download_url").takeUnless { it == "null" }.orEmpty(),
                x.optString("quality"), x.optString("language").ifBlank { "Hindi" }
            )
            grouped.getOrPut(epId) { mutableListOf() }.add(server)
        }
        grouped.forEach { (epId, servers) -> videoServersCache[epId] = servers }
    }
}

private suspend fun fetchAnimeDetails(title: String, forceRefresh: Boolean = false): AnimeDetails = withContext(Dispatchers.IO) {
    val cacheKey = title.trim().lowercase(Locale.ROOT)
    if (!forceRefresh) {
        animeDetailsCache[cacheKey]?.let { return@withContext it }
    }
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
    val result = AnimeDetails(
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
    animeDetailsCache[cacheKey] = result
    result
}

private fun formatBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(Locale.US, "%.1f MB", mb)
}

private fun formatDuration(ms: Long): String {
    if (ms <= 0L) return "00:00"
    val totalSeconds = ms / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}

private fun saveDownloadSource(context: Context, id: Long, url: String, title: String) {
    context.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE).edit()
        .putString("url_$id", url)
        .putString("title_$id", title)
        .apply()
}

private fun getDownloadSourceUrl(context: Context, id: Long): String =
    context.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE).getString("url_$id", "").orEmpty()

private fun removeDownloadSource(context: Context, id: Long) {
    context.getSharedPreferences(DOWNLOAD_PREFS, Context.MODE_PRIVATE).edit()
        .remove("url_$id")
        .remove("title_$id")
        .apply()
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
    val id = manager.enqueue(request)
    saveDownloadSource(context, id, url, fileTitle)
    return id
}

private fun removeDownload(context: Context, downloadId: Long) {
    val manager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    manager.remove(downloadId)
    removeDownloadSource(context, downloadId)
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
        val uriCol = it.getColumnIndex(DownloadManager.COLUMN_URI)
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
            val sizeText = when {
                total > 0L && statusCode == DownloadManager.STATUS_SUCCESSFUL -> formatBytes(total)
                total > 0L -> "${formatBytes(done)} / ${formatBytes(total)}"
                done > 0L -> formatBytes(done)
                else -> ""
            }
            val localUri = if (statusCode == DownloadManager.STATUS_SUCCESSFUL) manager.getUriForDownloadedFile(id)?.toString().orEmpty() else ""
            val colSource = if (uriCol >= 0) it.getString(uriCol).orEmpty() else ""
            val savedSource = getDownloadSourceUrl(context, id).ifBlank { colSource }
            items.add(
                DownloadInfo(
                    id = id,
                    title = it.getString(titleCol) ?: "AniStream download",
                    status = status,
                    progress = progress,
                    localUri = localUri,
                    sizeText = sizeText,
                    sourceUrl = savedSource
                )
            )
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

    fun loadAllAnime() {
        scope.launch {
            loading = true
            error = null
            try {
                animeList = fetchAnime()
            } catch (e: Exception) {
                error = e.message ?: "Unable to load anime"
            } finally {
                loading = false
            }
        }
    }

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
        loadAllAnime()
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

    val badgeLetter = (userSession?.fullName?.firstOrNull() ?: userSession?.email?.firstOrNull() ?: 'A').uppercaseChar()

    MaterialTheme {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                if (selectedAnime == null && offlineDownload == null) {
                    NavigationBar(containerColor = SurfaceDark, tonalElevation = 0.dp) {
                        val labels = listOf("Home", "Explore", "My List", "Downloads", "Profile")
                        val icons = listOf("⌂", "⌕", "♡", "↓", "♙")
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Text(icons[index], fontSize = 18.sp) },
                                label = { Text(label, fontSize = 9.sp, maxLines = 1) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = AccentRed, selectedTextColor = AccentRed,
                                    indicatorColor = Color(0xFF281822), unselectedIconColor = Gray, unselectedTextColor = Gray
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
                        userSession = userSession,
                        onToggleMyList = { toggleMyList(selectedAnime!!.id) },
                        onLoginRequired = {
                            selectedAnime = null
                            tab = 4
                        },
                        onBack = { selectedAnime = null }
                    )
                    else -> {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Ani", color = White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Stream", color = AccentRed, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                            Spacer(Modifier.width(6.dp))
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(SurfaceDark)
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    "HINDI",
                                    color = AccentRed,
                                    fontSize = 8.sp,
                                    lineHeight = 9.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.weight(1f))
                            Box(
                                Modifier
                                    .size(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(SurfaceDark)
                                    .clickable { tab = 1 },
                                contentAlignment = Alignment.Center
                            ) {
                                Text("⌕", color = White, fontSize = 15.sp)
                            }
                            Spacer(Modifier.width(8.dp))
                            if (!userSession?.avatarUrl.isNullOrBlank()) {
                                UserAvatarView(
                                    avatarUrl = userSession?.avatarUrl.orEmpty(),
                                    fallbackInitial = badgeLetter,
                                    size = 30.dp,
                                    fontSize = 13.sp,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .border(1.dp, AccentRed, RoundedCornerShape(8.dp))
                                        .clickable { tab = 4 }
                                )
                            } else {
                                Box(
                                    Modifier
                                        .size(30.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SurfaceDark)
                                        .border(1.dp, Color(0xFF2B2D3C), RoundedCornerShape(8.dp))
                                        .clickable { tab = 4 },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(badgeLetter.toString(), color = AccentRed, fontSize = 13.sp, fontWeight = FontWeight.Black)
                                }
                            }
                        }

                        when (tab) {
                            0 -> HomeScreen(
                                animeList = animeList,
                                loading = loading,
                                error = error,
                                myListIds = myListIds,
                                onRefresh = { loadAllAnime() },
                                onToggleMyList = { toggleMyList(it.id) },
                                onExploreClick = { tab = 1 },
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

/* REUSABLE COMPACT COMMENTS & ADMIN REPLY SECTION */
@Composable
fun CommentsSection(
    animeId: String,
    userSession: UserSession?,
    onLoginRequired: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var comments by remember(animeId) { mutableStateOf<List<AnimeComment>>(emptyList()) }
    var loading by remember(animeId) { mutableStateOf(true) }
    var posting by remember { mutableStateOf(false) }
    var commentInput by remember { mutableStateOf("") }
    var statusMsg by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    fun loadComments() {
        scope.launch {
            loading = true
            runCatching { fetchCommentsForAnime(animeId) }
                .onSuccess { comments = it }
                .onFailure { comments = emptyList() }
            loading = false
        }
    }

    LaunchedEffect(animeId) {
        loadComments()
    }

    DetailSection("Comments (${comments.size})") {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
                .padding(10.dp)
        ) {
            if (userSession == null) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Comment karne ke liye Login karein",
                        color = Gray,
                        fontSize = 11.sp,
                        modifier = Modifier.weight(1f)
                    )
                    SmallOutlineButton(
                        text = "Login",
                        onClick = onLoginRequired,
                        selected = true
                    )
                }
            } else {
                OutlinedTextField(
                    value = commentInput,
                    onValueChange = { commentInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Write a comment as ${userSession.fullName.ifBlank { "Member" }}...", color = Gray, fontSize = 11.sp) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = Color(0xFF2D2F3E),
                        cursorColor = AccentRed
                    )
                )
                Spacer(Modifier.height(7.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallOutlineButton(
                        text = "↻ Refresh",
                        onClick = { loadComments() },
                        enabled = !loading && !posting
                    )
                    // Compact Sleek "Post Comment" Pill Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (posting) Color(0xFF7A1529) else AccentRed)
                            .clickable(enabled = !posting) {
                                val clean = commentInput.trim()
                                if (clean.length < 2) {
                                    isError = true
                                    statusMsg = "Kripya comment likhein."
                                    return@clickable
                                }
                                posting = true
                                statusMsg = null
                                scope.launch {
                                    try {
                                        postAnimeComment(animeId, clean, userSession)
                                        commentInput = ""
                                        isError = false
                                        statusMsg = "Comment post ho gaya!"
                                        loadComments()
                                    } catch (e: Exception) {
                                        isError = true
                                        statusMsg = e.message ?: "Comment post nahi ho paya."
                                    } finally {
                                        posting = false
                                    }
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (posting) "Posting..." else "Post Comment",
                            color = White,
                            fontSize = 9.sp,
                            lineHeight = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            statusMsg?.let { msg ->
                Spacer(Modifier.height(5.dp))
                Text(
                    msg,
                    color = if (isError) AccentRed else Color(0xFF4CAF50),
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        when {
            loading -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentRed, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
            comments.isEmpty() -> Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDark)
                    .padding(14.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Abhi koi comment nahi hai. Pehla comment aap karein!", color = Gray, fontSize = 11.sp)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                comments.forEach { item ->
                    val initial = (item.userName.firstOrNull() ?: 'U').uppercaseChar()
                    val isOwnComment = userSession != null && item.userId == userSession.userId

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF28151E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    initial.toString(),
                                    color = AccentRed,
                                    fontSize = 10.sp,
                                    lineHeight = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.width(7.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.userName, color = White, fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Bold)
                                if (item.createdAt.isNotBlank()) {
                                    Text(item.createdAt, color = Gray, fontSize = 8.sp, lineHeight = 10.sp)
                                }
                            }
                            if (isOwnComment) {
                                Text(
                                    "🗑 Delete",
                                    color = AccentRed,
                                    fontSize = 9.sp,
                                    lineHeight = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch {
                                                runCatching { deleteAnimeComment(item.id, userSession!!) }
                                                loadComments()
                                            }
                                        }
                                        .padding(horizontal = 5.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(5.dp))
                        Text(item.content, color = Color(0xFFE2E4EC), fontSize = 11.sp, lineHeight = 14.sp)

                        // Ultra-Compact Admin Reply Box & Badge
                        if (item.adminReply.isNotBlank()) {
                            Spacer(Modifier.height(6.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(Color(0xFF1A1218))
                                    .border(1.dp, Color(0xFF3A1621), RoundedCornerShape(7.dp))
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(AccentRed)
                                            .padding(horizontal = 4.dp, vertical = 1.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "ADMIN REPLY",
                                            color = White,
                                            fontSize = 6.5.sp,
                                            lineHeight = 8.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Spacer(Modifier.width(5.dp))
                                    Text(
                                        text = "AniStream Official",
                                        color = Gray,
                                        fontSize = 8.5.sp,
                                        lineHeight = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(3.dp))
                                Text(item.adminReply, color = White, fontSize = 10.5.sp, lineHeight = 13.sp)
                            }
                        }
                    }
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
    myListIds: Set<String>,
    onRefresh: () -> Unit,
    onToggleMyList: (Anime) -> Unit,
    onExploreClick: () -> Unit,
    onAnimeClick: (Anime) -> Unit
) {
    var selectedCategory by remember { mutableStateOf("Latest") }
    val categories = listOf(
        "Latest" to "⚡ Latest",
        "Hindi" to "文A Hindi dub",
        "Popular" to "🔥 Popular",
        "Series" to "📺 Series",
        "Movies" to "🎬 Movies"
    )

    val filteredList = remember(animeList, selectedCategory) {
        when (selectedCategory) {
            "Movies" -> animeList.filter { it.animeType.equals("Movie", true) }
            "Series" -> animeList.filter { !it.animeType.equals("Movie", true) }
            "Popular" -> animeList.reversed()
            else -> animeList
        }
    }
    val movies = remember(animeList) { animeList.filter { it.animeType.equals("Movie", true) } }
    val series = remember(animeList) { animeList.filter { !it.animeType.equals("Movie", true) } }
    val savedContinueList = remember(animeList, myListIds) { animeList.filter { myListIds.contains(it.id) } }

    val featuredSlides = remember(filteredList, animeList) {
        (filteredList.ifEmpty { animeList }).take(6)
    }
    var currentSlide by remember(featuredSlides.size) { mutableIntStateOf(0) }

    LaunchedEffect(featuredSlides.size, currentSlide) {
        if (featuredSlides.size > 1) {
            delay(4000L)
            currentSlide = (currentSlide + 1) % featuredSlides.size
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "YOUR NEXT FAVORITE",
                    color = AccentRed,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.2.sp
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    "Anime starts here",
                    color = White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
            Box(
                Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .border(1.dp, Color(0xFF282A38), RoundedCornerShape(14.dp))
                    .clickable { onExploreClick() }
                    .padding(horizontal = 9.dp, vertical = 4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧭", fontSize = 10.sp)
                    Spacer(Modifier.width(4.dp))
                    Text("Explore", color = White, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            categories.forEach { (key, label) ->
                val isSelected = selectedCategory == key
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) Color(0xFF29151E) else SurfaceDark)
                        .border(
                            1.dp,
                            if (isSelected) AccentRed else Color(0xFF252736),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { selectedCategory = key }
                        .padding(horizontal = 10.dp, vertical = 4.5.dp)
                ) {
                    Text(
                        text = label,
                        color = if (isSelected) White else Color(0xFFD0D2DC),
                        fontSize = 10.sp,
                        lineHeight = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        when {
            loading -> Box(Modifier.fillMaxWidth().height(320.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentRed, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
            error != null -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(16.dp)
            ) {
                Text("Unable to load anime", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(error, color = AccentRed, fontSize = 11.sp)
                Spacer(Modifier.height(10.dp))
                SmallOutlineButton(text = "↻ Try Again", onClick = onRefresh, selected = true)
            }
            featuredSlides.isEmpty() -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No published anime found yet.", color = Gray, fontSize = 12.sp)
            }
            else -> {
                val safeIndex = currentSlide.coerceIn(0, featuredSlides.lastIndex)
                val featured = featuredSlides[safeIndex]
                val nextFeatured = if (featuredSlides.size > 1) {
                    featuredSlides[(safeIndex + 1) % featuredSlides.size]
                } else null
                val isFeaturedSaved = myListIds.contains(featured.id)
                var dragDelta by remember { mutableFloatStateOf(0f) }

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp)
                        .height(330.dp)
                        .pointerInput(featuredSlides.size, safeIndex) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragDelta = 0f },
                                onDragEnd = {
                                    if (featuredSlides.size > 1) {
                                        if (dragDelta < -55f) {
                                            currentSlide = (safeIndex + 1) % featuredSlides.size
                                        } else if (dragDelta > 55f) {
                                            currentSlide = if (safeIndex - 1 < 0) featuredSlides.lastIndex else safeIndex - 1
                                        }
                                    }
                                    dragDelta = 0f
                                }
                            ) { change, dragAmount ->
                                change.consume()
                                dragDelta += dragAmount
                            }
                        },
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(20.dp))
                            .background(SurfaceDark)
                            .border(1.dp, Color(0xFF232533), RoundedCornerShape(20.dp))
                            .clickable { onAnimeClick(featured) }
                    ) {
                        AsyncImage(
                            model = featured.posterUrl,
                            contentDescription = featured.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Box(
                            Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0x22000000),
                                            Color(0x7708090D),
                                            Color(0xEE08090D),
                                            Color(0xFF08090D)
                                        )
                                    )
                                )
                        )

                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(5.dp))
                                .background(AccentRed)
                                .padding(horizontal = 7.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "✦ FEATURED",
                                color = White,
                                fontSize = 8.sp,
                                lineHeight = 9.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }

                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(if (isFeaturedSaved) AccentRed else Color(0xAA14151C))
                                .clickable { onToggleMyList(featured) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                if (isFeaturedSaved) "♥" else "♡",
                                color = White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(
                            Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .padding(14.dp)
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(AccentRed)
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "★ HINDI",
                                        color = White,
                                        fontSize = 7.5.sp,
                                        lineHeight = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color(0xFF252734))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        if (featured.animeType.equals("Movie", true)) "MOVIE" else "TV",
                                        color = White,
                                        fontSize = 7.5.sp,
                                        lineHeight = 9.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }
                            }

                            Spacer(Modifier.height(6.dp))
                            Text(
                                featured.title,
                                color = White,
                                fontSize = 19.sp,
                                fontWeight = FontWeight.ExtraBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                featured.description
                                    .ifBlank { "Stream in Hindi on AniStream." }
                                    .replace(Regex("\\<[^\\>]*\\>"), ""),
                                color = Gray,
                                fontSize = 10.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(9.dp))

                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(30.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AccentRed)
                                    .clickable { onAnimeClick(featured) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "▶  Watch Now",
                                    color = White,
                                    fontSize = 11.sp,
                                    lineHeight = 12.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }

                    if (nextFeatured != null) {
                        Box(
                            Modifier
                                .width(16.dp)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp))
                                .background(SurfaceDark)
                                .clickable {
                                    currentSlide = (safeIndex + 1) % featuredSlides.size
                                }
                        ) {
                            AsyncImage(
                                model = nextFeatured.posterUrl,
                                contentDescription = nextFeatured.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(Modifier.fillMaxSize().background(Color(0x8808090D)))
                        }
                    } else {
                        Spacer(Modifier.width(6.dp))
                    }
                }

                if (featuredSlides.size > 1) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 10.dp, bottom = 5.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        featuredSlides.indices.forEach { idx ->
                            val active = idx == safeIndex
                            Box(
                                Modifier
                                    .padding(horizontal = 3.dp)
                                    .height(4.5.dp)
                                    .width(if (active) 16.dp else 4.5.dp)
                                    .clip(CircleShape)
                                    .background(if (active) AccentRed else Color(0xFF343746))
                                    .clickable { currentSlide = idx }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                if (savedContinueList.isNotEmpty()) {
                    AnimeSection("Saved in Watchlist", savedContinueList, onAnimeClick)
                }

                if (selectedCategory == "Latest" || selectedCategory == "Hindi" || selectedCategory == "Popular") {
                    AnimeSection("Recently Added", filteredList, onAnimeClick)
                    AnimeSection("Anime Series", series, onAnimeClick)
                    if (movies.isNotEmpty()) AnimeSection("Anime Movies", movies, onAnimeClick)
                } else {
                    AnimeSection("All $selectedCategory (${filteredList.size})", filteredList, onAnimeClick)
                }

                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("ANISTREAM HINDI", color = AccentRed, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                    Text("Your anime world, all in one place.", color = Gray, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
fun AnimeSection(title: String, animeList: List<Anime>, onAnimeClick: (Anime) -> Unit) {
    if (animeList.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 18.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentRed)
            )
            Spacer(Modifier.width(7.dp))
            Text(title, color = White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
            Spacer(Modifier.weight(1f))
            Text("SWIPE ›", color = Gray, fontSize = 8.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            animeList.forEach { anime -> AnimeCard(anime) { onAnimeClick(anime) } }
        }
    }
}

@Composable
fun AnimeCard(anime: Anime, onClick: () -> Unit) {
    Column(Modifier.width(110.dp).clickable { onClick() }) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(148.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SurfaceDark)
        ) {
            AsyncImage(anime.posterUrl, anime.title, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            if (anime.animeType.equals("Movie", true)) {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(AccentRed)
                        .padding(horizontal = 4.dp, vertical = 1.5.dp)
                ) {
                    Text(
                        "MOVIE",
                        color = White,
                        fontSize = 6.5.sp,
                        lineHeight = 8.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            anime.title,
            color = White,
            fontSize = 10.5.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
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
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("My List", color = White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text(
                    if (userSession != null) "Synced with Supabase • ${savedAnime.size} Anime"
                    else "Saved on device • ${savedAnime.size} Anime",
                    color = if (userSession != null) Color(0xFF4CAF50) else Gray,
                    fontSize = 10.sp
                )
            }
            if (userSession != null) {
                SmallOutlineButton(
                    text = if (syncing) "Syncing..." else "↻ Sync",
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

        Spacer(Modifier.height(12.dp))

        if (savedAnime.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("♡", color = AccentRed, fontSize = 30.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Text("Your My List is Empty", color = White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Kisi bhi Anime par 'Add to My List' dabayein taaki aap use baad mein aasani se dekh sakein.",
                    color = Gray,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .clip(RoundedCornerShape(7.dp))
                        .background(AccentRed)
                        .clickable { onExploreClick() }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("EXPLORE ANIME", color = White, fontSize = 9.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                savedAnime.forEach { anime ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SurfaceDark)
                            .clickable { onAnimeClick(anime) }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            anime.posterUrl,
                            anime.title,
                            Modifier
                                .size(66.dp, 90.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Background),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(11.dp))
                        Column(Modifier.weight(1f)) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(if (anime.animeType.equals("Movie", true)) AccentRed else Color(0xFF282A36))
                                    .padding(horizontal = 5.dp, vertical = 1.5.dp)
                            ) {
                                Text(
                                    anime.animeType.ifBlank { "Series" }.uppercase(),
                                    color = White,
                                    fontSize = 6.5.sp,
                                    lineHeight = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            Text(anime.title, color = White, fontSize = 13.5.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Spacer(Modifier.height(2.dp))
                            Text(
                                anime.description.ifBlank { "Watch on AniStream Hindi" }.replace(Regex("\\<[^\\>]*\\>"), ""),
                                color = Gray,
                                fontSize = 10.sp,
                                maxLines = 2
                            )
                            Spacer(Modifier.height(7.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                                SmallOutlineButton(
                                    text = "▶ Watch",
                                    onClick = { onAnimeClick(anime) },
                                    selected = true
                                )
                                SmallOutlineButton(
                                    text = "✕ Remove",
                                    onClick = { onRemoveClick(anime) }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
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
    var showAvatarDialog by remember { mutableStateOf(false) }
    var customAvatarUrlInput by remember(session?.avatarUrl) {
        mutableStateOf(
            if (session?.avatarUrl?.startsWith("http") == true) session.avatarUrl else ""
        )
    }

    fun applyNewAvatar(newAvatarData: String) {
        val currentSession = session ?: return
        val localUpdated = currentSession.copy(avatarUrl = newAvatarData)
        onSessionUpdated(localUpdated)
        showAvatarDialog = false
        loading = true
        errorMsg = null
        infoMsg = null
        scope.launch {
            try {
                val cloudUpdated = supabaseUpdateAvatar(localUpdated, newAvatarData)
                onSessionUpdated(cloudUpdated)
                infoMsg = if (newAvatarData.isBlank()) "Profile photo hata di gayi." else "Profile photo update aur Supabase mein sync ho gayi!"
            } catch (e: Exception) {
                infoMsg = "Profile photo device par save ho gayi."
            } finally {
                loading = false
            }
        }
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                loading = true
                val base64Avatar = withContext(Dispatchers.IO) { uriToBase64Avatar(context, uri) }
                loading = false
                if (!base64Avatar.isNullOrBlank()) {
                    applyNewAvatar(base64Avatar)
                } else {
                    errorMsg = "Photo read nahi ho payi. Dusri photo try karein."
                }
            }
        }
    }

    LaunchedEffect(session) {
        if (session != null) {
            downloadsCount = withContext(Dispatchers.IO) { readDownloads(context).size }
        }
    }

    if (showAvatarDialog && session != null) {
        Dialog(onDismissRequest = { showAvatarDialog = false }) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .border(1.dp, Color(0xFF2D2F3E), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Text("Change Profile Photo", color = White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(3.dp))
                Text("Gallery se apni photo chunein ya Anime Avatar select karein:", color = Gray, fontSize = 10.5.sp)

                Spacer(Modifier.height(10.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(AccentRed)
                        .clickable { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Text("📷  CHOOSE FROM GALLERY", color = White, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.ExtraBold)
                }

                Spacer(Modifier.height(10.dp))
                Text("OR PICK ANIME AVATAR", color = Gray, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(7.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    PRESET_ANIME_AVATARS.forEach { presetUrl ->
                        val isSelected = session.avatarUrl == presetUrl
                        Box(
                            Modifier
                                .size(46.dp)
                                .clip(CircleShape)
                                .background(Background)
                                .border(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) AccentRed else Color(0xFF333646),
                                    shape = CircleShape
                                )
                                .clickable { applyNewAvatar(presetUrl) }
                        ) {
                            AsyncImage(
                                model = presetUrl,
                                contentDescription = "Preset Avatar",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = customAvatarUrlInput,
                    onValueChange = { customAvatarUrlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Or Paste Image URL", color = Gray, fontSize = 10.5.sp) },
                    placeholder = { Text("https://...", color = Gray, fontSize = 10.5.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = Gray,
                        cursorColor = AccentRed
                    )
                )

                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp), modifier = Modifier.fillMaxWidth()) {
                    if (customAvatarUrlInput.trim().startsWith("http")) {
                        SmallOutlineButton(
                            text = "Save URL",
                            onClick = { applyNewAvatar(customAvatarUrlInput.trim()) },
                            modifier = Modifier.weight(1f),
                            selected = true
                        )
                    }
                    if (session.avatarUrl.isNotBlank()) {
                        SmallOutlineButton(
                            text = "🗑 Remove",
                            onClick = { applyNewAvatar("") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    SmallOutlineButton(
                        text = "Close",
                        onClick = { showAvatarDialog = false },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        if (session == null) {
            Text(
                if (isSignUp) "Create Account" else "Welcome Back",
                color = White,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                if (isSignUp) "Sign up with Supabase to sync your profile & My List."
                else "Login to access your AniStream Hindi profile & cloud My List.",
                color = Gray,
                fontSize = 11.sp
            )
            Spacer(Modifier.height(12.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(9.dp))
                    .background(SurfaceDark)
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (!isSignUp) AccentRed else Color.Transparent)
                        .clickable {
                            isSignUp = false
                            errorMsg = null
                            infoMsg = null
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("LOGIN", color = White, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(7.dp))
                        .background(if (isSignUp) AccentRed else Color.Transparent)
                        .clickable {
                            isSignUp = true
                            errorMsg = null
                            infoMsg = null
                        }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("SIGN UP", color = White, fontSize = 10.sp, lineHeight = 11.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(Modifier.height(12.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
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
                            fontSize = 9.5.sp,
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
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF2D1219))
                            .padding(8.dp)
                    ) {
                        Text(it, color = AccentRed, fontSize = 10.5.sp)
                    }
                }

                infoMsg?.let {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(7.dp))
                            .background(Color(0xFF112A1B))
                            .padding(8.dp)
                    ) {
                        Text(it, color = Color(0xFF4CAF50), fontSize = 10.5.sp)
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (loading) Color(0xFF7A1529) else AccentRed)
                        .clickable(enabled = !loading) {
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
                    contentAlignment = Alignment.Center
                ) {
                    if (loading) {
                        CircularProgressIndicator(Modifier.size(15.dp), color = White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (isSignUp) "CREATE ACCOUNT" else "LOGIN",
                            color = White,
                            fontSize = 10.5.sp,
                            lineHeight = 12.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }
        } else {
            val initial = (session.fullName.firstOrNull() ?: session.email.firstOrNull() ?: 'A').uppercaseChar()

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(SurfaceDark)
                    .padding(13.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(56.dp)
                            .clickable { showAvatarDialog = true }
                    ) {
                        UserAvatarView(
                            avatarUrl = session.avatarUrl,
                            fallbackInitial = initial,
                            size = 52.dp,
                            fontSize = 20.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(1.5.dp, AccentRed, CircleShape)
                        )
                        Box(
                            Modifier
                                .size(18.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(AccentRed)
                                .border(1.5.dp, SurfaceDark, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✎", color = White, fontSize = 8.sp, lineHeight = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.width(11.dp))
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color(0xFF163222))
                                .padding(horizontal = 6.dp, vertical = 1.5.dp)
                        ) {
                            Text(
                                "VERIFIED MEMBER",
                                color = Color(0xFF4CAF50),
                                fontSize = 7.sp,
                                lineHeight = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(
                            session.fullName.ifBlank { "AniStream User" },
                            color = White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(session.email, color = Gray, fontSize = 10.5.sp)
                    }
                }

                Spacer(Modifier.height(9.dp))
                SmallOutlineButton(
                    text = if (session.avatarUrl.isBlank()) "📷 Set Profile Photo" else "📷 Change Profile Photo",
                    onClick = { showAvatarDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    selected = true
                )
            }

            Spacer(Modifier.height(10.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .padding(13.dp)
            ) {
                Text("ACCOUNT DETAILS", color = AccentRed, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(7.dp))
                InfoLine("Full Name", session.fullName.ifBlank { "Not set" })
                InfoLine("Email", session.email)
                InfoLine("User ID", session.userId.ifBlank { "N/A" })
                InfoLine("Joined On", session.createdAt.ifBlank { "Active" })
                InfoLine("My List Saved", "$myListCount Anime")
                InfoLine("Saved Downloads", "$downloadsCount Episodes")
            }

            Spacer(Modifier.height(10.dp))

            infoMsg?.let {
                Text(it, color = Color(0xFF4CAF50), fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 7.dp))
            }
            errorMsg?.let {
                Text(it, color = AccentRed, fontSize = 10.5.sp, modifier = Modifier.padding(bottom = 7.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                SmallOutlineButton(
                    text = "♡ My List ($myListCount)",
                    onClick = onOpenMyList,
                    modifier = Modifier.weight(1f)
                )
                SmallOutlineButton(
                    text = "↓ Downloads",
                    onClick = onOpenDownloads,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(7.dp))

            SmallOutlineButton(
                text = "↻ Sync Profile & Cloud Data",
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

            Spacer(Modifier.height(9.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(32.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (loading) Color(0xFF7A1529) else AccentRed)
                    .clickable(enabled = !loading) {
                        loading = true
                        scope.launch {
                            supabaseLogout(session.accessToken)
                            loading = false
                            onSessionUpdated(null)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (loading) "LOGGING OUT..." else "LOGOUT",
                    color = White,
                    fontSize = 10.5.sp,
                    lineHeight = 12.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
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
    var filterType by remember { mutableStateOf("All") }
    val filtered = animeList.filter { anime ->
        val matchesText = anime.title.contains(searchText, true) || anime.description.contains(searchText, true)
        val matchesType = when (filterType) {
            "Movies" -> anime.animeType.equals("Movie", true)
            "Series" -> !anime.animeType.equals("Movie", true)
            else -> true
        }
        matchesText && matchesType
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Text("Explore Anime", color = White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search anime title or story...", color = Gray, fontSize = 12.sp) },
            singleLine = true,
            trailingIcon = {
                if (searchText.isNotEmpty()) {
                    Text(
                        "✕",
                        color = AccentRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable { onSearchChange("") }
                            .padding(horizontal = 10.dp)
                    )
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = White, unfocusedTextColor = White, focusedBorderColor = AccentRed,
                unfocusedBorderColor = Gray, cursorColor = AccentRed
            )
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
            listOf("All", "Series", "Movies").forEach { type ->
                SmallOutlineButton(
                    text = type,
                    onClick = { filterType = type },
                    selected = filterType == type
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                "${filtered.size} Results",
                color = Gray,
                fontSize = 10.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .padding(20.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Koi Anime nahi mila. Dusra naam search karein.", color = Gray, fontSize = 12.sp)
            }
        } else {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                filtered.forEach { anime ->
                    val isSaved = myListIds.contains(anime.id)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(SurfaceDark)
                            .clickable { onAnimeClick(anime) }
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            anime.posterUrl,
                            anime.title,
                            Modifier.size(56.dp, 76.dp).clip(RoundedCornerShape(7.dp)).background(Background),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(anime.title, color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Spacer(Modifier.height(3.dp))
                            Text(anime.animeType.ifBlank { "Series" }, color = Gray, fontSize = 10.sp)
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
}

@Composable
fun AnimeDetailsScreen(
    anime: Anime,
    isInMyList: Boolean,
    userSession: UserSession?,
    onToggleMyList: () -> Unit,
    onLoginRequired: () -> Unit,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val cachedDetails = remember(anime.title) { animeDetailsCache[anime.title.trim().lowercase(Locale.ROOT)] }
    val cachedSeasons = remember(anime.id) { seasonsCache[anime.id] }

    var details by remember(anime.id) { mutableStateOf(cachedDetails) }
    var detailsLoading by remember(anime.id) { mutableStateOf(cachedDetails == null) }
    var detailsError by remember(anime.id) { mutableStateOf<String?>(null) }

    var seasons by remember(anime.id) { mutableStateOf(cachedSeasons ?: emptyList()) }
    var seasonsLoading by remember(anime.id) { mutableStateOf(cachedSeasons == null) }
    var seasonsError by remember(anime.id) { mutableStateOf<String?>(null) }
    var selectedSeason by remember(anime.id) { mutableStateOf(cachedSeasons?.firstOrNull()) }

    var episodes by remember(anime.id) { mutableStateOf<List<AnimeEpisode>>(emptyList()) }
    var episodesLoading by remember(anime.id) { mutableStateOf(false) }
    var episodesError by remember(anime.id) { mutableStateOf<String?>(null) }
    var playingEpisode by remember(anime.id) { mutableStateOf<AnimeEpisode?>(null) }

    fun loadDetails(force: Boolean = false) {
        scope.launch {
            detailsLoading = true
            detailsError = null
            try {
                details = fetchAnimeDetails(anime.title, forceRefresh = force)
            } catch (e: Exception) {
                detailsError = e.message
            } finally {
                detailsLoading = false
            }
        }
    }

    fun loadSeasons(force: Boolean = false) {
        scope.launch {
            seasonsLoading = true
            seasonsError = null
            try {
                seasons = fetchSeasons(anime.id, forceRefresh = force)
                if (selectedSeason == null) selectedSeason = seasons.firstOrNull()
            } catch (e: Exception) {
                seasonsError = e.message
            } finally {
                seasonsLoading = false
            }
        }
    }

    BackHandler {
        if (playingEpisode != null) playingEpisode = null else onBack()
    }
    LaunchedEffect(anime.id) {
        if (details == null) loadDetails()
        if (seasons.isEmpty()) loadSeasons()
    }
    LaunchedEffect(selectedSeason?.id) {
        val season = selectedSeason
        if (season == null) {
            episodes = emptyList()
        } else {
            val cachedEps = episodesCache[season.id]
            if (cachedEps != null) {
                episodes = cachedEps
                episodesLoading = false
                launch { prefetchVideoServersForSeason(cachedEps) }
            } else {
                episodesLoading = true
                episodesError = null
                try {
                    val fetched = fetchEpisodes(season.id)
                    episodes = fetched
                    launch { prefetchVideoServersForSeason(fetched) }
                } catch (e: Exception) {
                    episodesError = e.message
                    episodes = emptyList()
                }
                episodesLoading = false
            }
        }
    }
    val episodeIndex = episodes.indexOfFirst { it.id == playingEpisode?.id }
    if (playingEpisode != null) {
        PlayerScreen(
            animeId = anime.id,
            episode = playingEpisode!!,
            episodes = episodes,
            episodeIndex = episodeIndex,
            userSession = userSession,
            onLoginRequired = onLoginRequired,
            onBack = { playingEpisode = null },
            onEpisodeChange = { if (it in episodes.indices) playingEpisode = episodes[it] }
        )
        return
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 20.dp)) {
        Text(
            "‹ Back",
            color = White,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .clickable { onBack() }
                .padding(horizontal = 16.dp, vertical = 9.dp)
        )
        AsyncImage(
            details?.banner?.takeIf { it.isNotBlank() } ?: anime.posterUrl,
            anime.title,
            Modifier
                .fillMaxWidth()
                .height(150.dp)
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(SurfaceDark),
            contentScale = ContentScale.Crop
        )
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
            AsyncImage(
                anime.posterUrl,
                anime.title,
                Modifier
                    .width(94.dp)
                    .height(130.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDark),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    details?.englishTitle?.takeIf { it.isNotBlank() } ?: anime.title,
                    color = White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold
                )
                Text(details?.japaneseTitle.orEmpty(), color = Gray, fontSize = 10.sp)
                Spacer(Modifier.height(5.dp))
                Text("⭐ ${details?.rating ?: "Loading..."}", color = AccentRed, fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
                Text("Year: ${details?.year ?: "Loading..."}", color = Gray, fontSize = 10.sp)
                Text("Episodes: ${details?.episodes ?: "Loading..."}", color = Gray, fontSize = 10.sp)
                Spacer(Modifier.height(7.dp))
                SmallOutlineButton(
                    text = if (isInMyList) "♥ In My List" else "♡ Add to My List",
                    onClick = onToggleMyList,
                    selected = isInMyList
                )
            }
        }
        if (detailsLoading) CircularProgressIndicator(Modifier.padding(16.dp).size(20.dp), color = AccentRed)
        else if (detailsError != null) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Extra AniList details unavailable.", color = Gray, fontSize = 11.sp, modifier = Modifier.weight(1f))
                SmallOutlineButton(text = "↻ Retry", onClick = { loadDetails(force = true) })
            }
            DetailSection("Story") { Text(anime.description.ifBlank { "No description available." }, color = Gray, fontSize = 11.5.sp) }
        } else details?.let { info ->
            DetailSection("Story") { Text(info.description.replace(Regex("\\<[^\\>]*\\>"), ""), color = Gray, fontSize = 11.5.sp) }
            DetailSection("Anime Information") {
                InfoLine("Release", info.year); InfoLine("Status", info.status); InfoLine("Studio", info.studio)
                InfoLine("Episodes", info.episodes); InfoLine("Rating", info.rating)
            }
            if (info.genres.isNotEmpty()) {
                DetailSection("Genres") { Text(info.genres.joinToString("  •  "), color = AccentRed, fontSize = 11.sp) }
            }
            if (info.characters.isNotEmpty()) DetailSection("Characters") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    info.characters.forEach { c ->
                        Column(Modifier.width(80.dp)) {
                            AsyncImage(c.image, c.name, Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(8.dp)).background(SurfaceDark), contentScale = ContentScale.Crop)
                            Spacer(Modifier.height(4.dp))
                            Text(c.name, color = White, fontSize = 9.5.sp, lineHeight = 11.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                            Text(c.role, color = Gray, fontSize = 8.sp)
                        }
                    }
                }
            }
        }
        DetailSection("Watch Anime") {
            Text("Select Season", color = Gray, fontSize = 10.sp)
            Spacer(Modifier.height(5.dp))
            if (seasonsLoading) CircularProgressIndicator(Modifier.size(18.dp), color = AccentRed)
            else if (seasonsError != null) {
                Text("Season load error: $seasonsError", color = AccentRed, fontSize = 11.sp)
                Spacer(Modifier.height(5.dp))
                SmallOutlineButton(text = "↻ Retry Seasons", onClick = { loadSeasons(force = true) })
            } else if (seasons.isEmpty()) Text("Abhi is Anime ke Seasons add nahi kiye gaye hain.", color = Gray, fontSize = 11.sp)
            else {
                // Compact Sleek Season Pills
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    seasons.forEach { s ->
                        val selected = selectedSeason?.id == s.id
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(5.dp))
                                .background(if (selected) AccentRed else SurfaceDark)
                                .border(
                                    1.dp,
                                    if (selected) AccentRed else Color(0xFF282A36),
                                    RoundedCornerShape(5.dp)
                                )
                                .clickable { selectedSeason = s }
                                .padding(horizontal = 8.dp, vertical = 3.5.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = s.title.ifBlank { "Season ${s.seasonNumber}" },
                                color = White,
                                fontSize = 9.sp,
                                lineHeight = 10.5.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Spacer(Modifier.height(9.dp))
                Text(
                    selectedSeason?.title ?: "Episodes",
                    color = White,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(6.dp))
                if (episodesLoading) CircularProgressIndicator(Modifier.size(18.dp), color = AccentRed)
                else if (episodesError != null) Text("Episode load error: $episodesError", color = AccentRed, fontSize = 11.sp)
                else if (episodes.isEmpty()) Text("Is Season mein abhi koi Published Episode nahi hai.", color = Gray, fontSize = 11.sp)
                else Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    episodes.forEach { ep ->
                        EpisodeCard(ep, selected = playingEpisode?.id == ep.id) { playingEpisode = ep }
                    }
                }
            }
        }

        CommentsSection(
            animeId = anime.id,
            userSession = userSession,
            onLoginRequired = onLoginRequired
        )
    }
}

/* COMPACT & SMALLER EPISODE CARD */
@Composable
fun EpisodeCard(episode: AnimeEpisode, selected: Boolean = false, onClick: () -> Unit) {
    Column(
        Modifier
            .width(82.dp)
            .clip(RoundedCornerShape(7.dp))
            .background(if (selected) Color(0xFF35101C) else SurfaceDark)
            .border(
                width = 1.dp,
                color = if (selected) AccentRed else Color(0xFF242632),
                shape = RoundedCornerShape(7.dp)
            )
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 5.5.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "EP ${episode.episodeNumber}",
                color = AccentRed,
                fontSize = 9.5.sp,
                lineHeight = 11.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.weight(1f))
            Text("▶", color = White, fontSize = 8.5.sp, lineHeight = 10.sp)
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = episode.title.ifBlank { "Episode ${episode.episodeNumber}" },
            color = White,
            fontSize = 8.5.sp,
            lineHeight = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun PlayerScreen(
    animeId: String,
    episode: AnimeEpisode,
    episodes: List<AnimeEpisode>,
    episodeIndex: Int,
    userSession: UserSession?,
    onLoginRequired: () -> Unit,
    onBack: () -> Unit,
    onEpisodeChange: (Int) -> Unit
) {
    val cachedServers = remember(episode.id) { videoServersCache[episode.id].orEmpty() }
    val initialLang = remember(cachedServers) {
        cachedServers.firstOrNull { it.language.equals("Hindi", true) }?.language ?: cachedServers.firstOrNull()?.language.orEmpty()
    }
    val initialQual = remember(cachedServers, initialLang) {
        val m = cachedServers.filter { it.language.equals(initialLang, true) }
        m.firstOrNull { it.quality.equals("720p", true) }?.quality ?: m.firstOrNull()?.quality.orEmpty()
    }

    var servers by remember(episode.id) { mutableStateOf(cachedServers) }
    var loading by remember(episode.id) { mutableStateOf(cachedServers.isEmpty()) }
    var error by remember(episode.id) { mutableStateOf<String?>(null) }
    var language by remember(episode.id) { mutableStateOf(initialLang) }
    var quality by remember(episode.id) { mutableStateOf(initialQual) }
    var languageMenu by remember { mutableStateOf(false) }
    var qualityMenu by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var downloadMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    BackHandler {
        if (fullscreen) {
            fullscreen = false
        } else {
            onBack()
        }
    }

    LaunchedEffect(episode.id) {
        downloadMessage = null
        if (servers.isEmpty()) {
            loading = true
            error = null
            try {
                val fetched = fetchVideoServers(episode.id)
                servers = fetched
                if (fetched.isEmpty()) {
                    error = "Is Episode ke liye abhi video link add nahi hua."
                } else {
                    language = fetched.firstOrNull { it.language.equals("Hindi", true) }?.language ?: fetched.first().language
                    val matching = fetched.filter { it.language.equals(language, true) }
                    quality = matching.firstOrNull { it.quality.equals("720p", true) }?.quality ?: matching.first().quality
                }
            } catch (e: Exception) {
                error = e.message ?: "Video links load nahi ho paye"
            }
            loading = false
        }
    }

    val languageServers = servers.filter { it.language.equals(language, true) }
    val allLanguages = servers.map { it.language }.distinct()
    val qualities = languageServers.map { it.quality }.distinct().sortedBy {
        listOf("1080p", "720p", "480p").indexOf(it).let { n -> if (n < 0) 99 else n }
    }
    val selected = languageServers.firstOrNull { it.quality.equals(quality, true) } ?: languageServers.firstOrNull()
    val dlUrl = selected?.downloadUrl.orEmpty()

    fun triggerDownload() {
        if (dlUrl.isBlank()) {
            downloadMessage = "Is server par download link uplabdh nahi hai."
            return
        }
        try {
            startDownload(context, dlUrl, "AniStream_Ep_${episode.episodeNumber}_${selected?.quality}")
            downloadMessage = "Download start ho gaya! Downloads tab mein dekhein."
        } catch (e: Exception) {
            downloadMessage = e.message ?: "Download start nahi hua."
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(if (fullscreen) Color.Black else Background)
            .then(if (fullscreen) Modifier else Modifier.verticalScroll(rememberScrollState()))
    ) {
        if (!fullscreen) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(SurfaceDark)
                    .padding(horizontal = 14.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", color = White, fontSize = 22.sp, modifier = Modifier.clickable { onBack() })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("ANISTREAM PLAYER", color = AccentRed, fontSize = 8.5.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Episode ${episode.episodeNumber}", color = Gray, fontSize = 9.5.sp)
                }
                SmallOutlineButton("⛶", onClick = { fullscreen = true })
            }
            Text(
                episode.title.ifBlank { "Episode ${episode.episodeNumber}" },
                color = White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp)
            )
        }

        when {
            loading -> Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.height(225.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentRed)
            }
            error != null -> Box(
                Modifier
                    .fillMaxWidth()
                    .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.height(225.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(error ?: "Video unavailable", color = White, fontSize = 12.sp)
            }
            selected != null -> WatchPlayer(
                videoUrl = selected.videoUrl,
                episodeTitle = "EP ${episode.episodeNumber} • ${episode.title.ifBlank { "Episode ${episode.episodeNumber}" }}",
                fullscreen = fullscreen,
                hasPrevious = episodeIndex > 0,
                hasNext = episodeIndex >= 0 && episodeIndex < episodes.lastIndex,
                currentLanguage = language,
                availableLanguages = allLanguages,
                currentQuality = selected.quality,
                availableQualities = qualities,
                onSelectLanguage = { lang ->
                    language = lang
                    val avail = servers.filter { it.language.equals(lang, true) }
                    quality = avail.firstOrNull { it.quality.equals("720p", true) }?.quality ?: avail.firstOrNull()?.quality.orEmpty()
                },
                onSelectQuality = { q -> quality = q },
                onPreviousEpisode = { if (episodeIndex > 0) onEpisodeChange(episodeIndex - 1) },
                onNextEpisode = { if (episodeIndex < episodes.lastIndex) onEpisodeChange(episodeIndex + 1) },
                onToggleFullscreen = { fullscreen = !fullscreen },
                onBack = { if (fullscreen) fullscreen = false else onBack() }
            )
        }

        if (!fullscreen && servers.isNotEmpty()) {
            Column(Modifier.padding(14.dp)) {
                Text("PLAYBACK SETTINGS", color = Gray, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.weight(1f)) {
                        SmallOutlineButton("Audio: ${language.ifBlank { "Select" }}", onClick = { languageMenu = true }, modifier = Modifier.fillMaxWidth())
                        DropdownMenu(languageMenu, { languageMenu = false }) {
                            allLanguages.forEach { lang ->
                                DropdownMenuItem(text = { Text(lang, fontSize = 11.sp) }, onClick = {
                                    language = lang
                                    val avail = servers.filter { it.language.equals(lang, true) }
                                    quality = avail.firstOrNull { it.quality.equals("720p", true) }?.quality ?: avail.firstOrNull()?.quality.orEmpty()
                                    languageMenu = false
                                })
                            }
                        }
                    }
                    Box(Modifier.weight(1f)) {
                        SmallOutlineButton("Quality: ${quality.ifBlank { "Select" }}", onClick = { qualityMenu = true }, modifier = Modifier.fillMaxWidth())
                        DropdownMenu(qualityMenu, { qualityMenu = false }) {
                            qualities.forEach { q -> DropdownMenuItem(text = { Text(q, fontSize = 11.sp) }, onClick = { quality = q; qualityMenu = false }) }
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                SmallOutlineButton(
                    text = if (dlUrl.isBlank()) "↓ Download link unavailable" else "↓ Download Episode",
                    onClick = { triggerDownload() },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = dlUrl.isNotBlank()
                )
                downloadMessage?.let { Text(it, color = AccentRed, fontSize = 10.sp, modifier = Modifier.padding(top = 5.dp)) }
                selected?.let {
                    Text(
                        "Server: ${it.serverName}  •  ${it.quality}",
                        color = Gray,
                        fontSize = 9.5.sp,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }
        }

        if (!fullscreen) {
            Column(Modifier.padding(horizontal = 14.dp)) {
                Text("EPISODE NAVIGATION", color = Gray, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallOutlineButton("← Previous", onClick = { onEpisodeChange(episodeIndex - 1) }, modifier = Modifier.weight(1f), enabled = episodeIndex > 0)
                    SmallOutlineButton("Next Episode →", onClick = { onEpisodeChange(episodeIndex + 1) }, modifier = Modifier.weight(1f), enabled = episodeIndex >= 0 && episodeIndex < episodes.lastIndex, selected = true)
                }
                Spacer(Modifier.height(8.dp))
            }

            CommentsSection(
                animeId = animeId,
                userSession = userSession,
                onLoginRequired = onLoginRequired
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun WatchPlayer(
    videoUrl: String,
    episodeTitle: String,
    fullscreen: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    currentLanguage: String,
    availableLanguages: List<String>,
    currentQuality: String,
    availableQualities: List<String>,
    onSelectLanguage: (String) -> Unit,
    onSelectQuality: (String) -> Unit,
    onPreviousEpisode: () -> Unit,
    onNextEpisode: () -> Unit,
    onToggleFullscreen: () -> Unit,
    onBack: () -> Unit
) {
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
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
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
        Box(
            Modifier
                .fillMaxWidth()
                .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.height(225.dp))
                .background(Color.Black)
        ) {
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
                modifier = Modifier.fillMaxSize()
            )
            if (fullscreen) {
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .padding(10.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0x88000000))
                        .clickable { onToggleFullscreen() }
                        .padding(8.dp)
                ) {
                    MxFullscreenIcon(size = 16.dp)
                }
            }
        }
    } else {
        val player = remember(context) { ExoPlayer.Builder(context).build() }
        var isPlaying by remember { mutableStateOf(true) }
        var isBuffering by remember { mutableStateOf(true) }
        var currentPos by remember { mutableLongStateOf(0L) }
        var duration by remember { mutableLongStateOf(0L) }
        var controlsVisible by remember { mutableStateOf(true) }
        var isLocked by remember { mutableStateOf(false) }
        var normalSpeed by remember { mutableFloatStateOf(1.0f) }
        var isHolding2x by remember { mutableStateOf(false) }
        var isZoomed by remember { mutableStateOf(false) }
        var seekToast by remember { mutableStateOf<String?>(null) }
        var showAudioPopup by remember { mutableStateOf(false) }
        var showQualityPopup by remember { mutableStateOf(false) }

        DisposableEffect(player) {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(playing: Boolean) {
                    isPlaying = playing
                }
                override fun onPlaybackStateChanged(playbackState: Int) {
                    isBuffering = playbackState == Player.STATE_BUFFERING
                    if (playbackState == Player.STATE_READY) {
                        duration = player.duration.coerceAtLeast(0L)
                    }
                }
            }
            player.addListener(listener)
            onDispose {
                player.removeListener(listener)
                player.release()
            }
        }

        LaunchedEffect(player, videoUrl) {
            player.setMediaItem(MediaItem.fromUri(videoUrl))
            player.prepare()
            player.playWhenReady = true
            player.setPlaybackSpeed(normalSpeed)
        }

        LaunchedEffect(player) {
            while (true) {
                currentPos = player.currentPosition.coerceAtLeast(0L)
                val dur = player.duration
                if (dur > 0L) duration = dur
                delay(350)
            }
        }

        LaunchedEffect(controlsVisible, isPlaying, isLocked, showAudioPopup, showQualityPopup) {
            if (controlsVisible && isPlaying && !showAudioPopup && !showQualityPopup) {
                delay(3200)
                controlsVisible = false
            }
        }

        LaunchedEffect(seekToast) {
            if (seekToast != null) {
                delay(600)
                seekToast = null
            }
        }

        fun seekByMillis(deltaMs: Long) {
            if (isLocked) return
            val target = (player.currentPosition + deltaMs).coerceAtLeast(0L)
            val dur = player.duration
            val finalPos = if (dur > 0L) target.coerceAtMost(dur) else target
            player.seekTo(finalPos)
            currentPos = finalPos
            seekToast = if (deltaMs < 0L) "-10s" else "+10s"
        }

        Box(
            Modifier
                .fillMaxWidth()
                .then(if (fullscreen) Modifier.fillMaxSize() else Modifier.height(225.dp))
                .background(Color.Black)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        setPlayer(player)
                        useController = false
                        keepScreenOn = true
                        val detector = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
                            override fun onDown(e: MotionEvent): Boolean = true

                            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                                controlsVisible = !controlsVisible
                                return true
                            }

                            override fun onDoubleTap(e: MotionEvent): Boolean {
                                if (!isLocked) {
                                    val isLeft = e.x < (width / 2f)
                                    seekByMillis(if (isLeft) -10_000L else 10_000L)
                                }
                                return true
                            }

                            override fun onLongPress(e: MotionEvent) {
                                if (!isLocked) {
                                    isHolding2x = true
                                    player.setPlaybackSpeed(2.0f)
                                }
                            }
                        })
                        setOnTouchListener { _, event ->
                            detector.onTouchEvent(event)
                            if (event.action == MotionEvent.ACTION_UP || event.action == MotionEvent.ACTION_CANCEL) {
                                if (isHolding2x) {
                                    isHolding2x = false
                                    player.setPlaybackSpeed(normalSpeed)
                                }
                            }
                            true
                        }
                    }
                },
                update = { view ->
                    view.setPlayer(player)
                    view.useController = false
                    val scale = if (isZoomed) 1.16f else 1.0f
                    view.scaleX = scale
                    view.scaleY = scale
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isBuffering) {
                CircularProgressIndicator(
                    color = SeekBlue,
                    modifier = Modifier.align(Alignment.Center).size(30.dp),
                    strokeWidth = 2.5.dp
                )
            }

            if (isHolding2x) {
                Surface(
                    color = Color(0xAA000000),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp)
                ) {
                    Text(
                        "2X",
                        color = White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }

            seekToast?.let { text ->
                Surface(
                    color = Color(0xAA000000),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Text(
                        text,
                        color = White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
            }

            if (controlsVisible) {
                if (isLocked) {
                    Box(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(14.dp)
                            .clip(CircleShape)
                            .background(Color(0x8814151C))
                            .clickable { isLocked = false }
                            .padding(9.dp)
                    ) {
                        MxLockIcon(locked = true, size = 16.dp)
                    }
                } else {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .background(Color(0x44000000))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier
                                    .clickable { onBack() }
                                    .padding(end = 10.dp, top = 4.dp, bottom = 4.dp)
                            ) {
                                MxBackArrowIcon(size = 18.dp)
                            }
                            Text(
                                episodeTitle,
                                color = White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))

                            Box {
                                MxAudioNoteIcon(
                                    size = 16.dp,
                                    modifier = Modifier
                                        .clickable { showAudioPopup = true }
                                        .padding(4.dp)
                                )
                                DropdownMenu(expanded = showAudioPopup, onDismissRequest = { showAudioPopup = false }) {
                                    availableLanguages.forEach { lang ->
                                        DropdownMenuItem(
                                            text = { Text(if (lang.equals(currentLanguage, true)) "✓ $lang" else lang) },
                                            onClick = {
                                                onSelectLanguage(lang)
                                                showAudioPopup = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Box {
                                MxSubtitleTrackIcon(
                                    size = 16.dp,
                                    modifier = Modifier
                                        .clickable { showQualityPopup = true }
                                        .padding(4.dp)
                                )
                                DropdownMenu(expanded = showQualityPopup, onDismissRequest = { showQualityPopup = false }) {
                                    availableQualities.forEach { q ->
                                        DropdownMenuItem(
                                            text = { Text(if (q.equals(currentQuality, true)) "✓ $q" else q) },
                                            onClick = {
                                                onSelectQuality(q)
                                                showQualityPopup = false
                                            }
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.width(12.dp))
                            Text("HW", color = White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val speedLabel = if (normalSpeed == 1.0f) "1X" else "${normalSpeed}X"
                            PlayerSmallCirclePill(speedLabel) {
                                val speeds = listOf(1.0f, 1.25f, 1.5f, 2.0f)
                                val nextIdx = (speeds.indexOf(normalSpeed) + 1) % speeds.size
                                normalSpeed = speeds[nextIdx]
                                player.setPlaybackSpeed(normalSpeed)
                            }
                            PlayerSmallCirclePill(if (isZoomed) "ZOOM" else "FIT") {
                                isZoomed = !isZoomed
                            }
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0x7714151C))
                                    .clickable { onToggleFullscreen() },
                                contentAlignment = Alignment.Center
                            ) {
                                MxRotateScreenIcon(size = 13.dp)
                            }
                        }

                        Spacer(Modifier.weight(1f))

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                formatDuration(currentPos),
                                color = White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                            MxSlimSeekBar(
                                progress = if (duration > 0L) (currentPos.toFloat() / duration.toFloat()).coerceIn(0f, 1f) else 0f,
                                onSeek = { fraction ->
                                    if (duration > 0L) {
                                        val newPos = (fraction * duration).toLong()
                                        currentPos = newPos
                                        player.seekTo(newPos)
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 10.dp)
                            )
                            Text(
                                formatDuration(duration),
                                color = White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(
                                Modifier
                                    .clickable { isLocked = true }
                                    .padding(6.dp)
                            ) {
                                MxLockIcon(locked = false, size = 16.dp)
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(28.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .clickable(enabled = hasPrevious) { onPreviousEpisode() }
                                        .padding(6.dp)
                                ) {
                                    MxPrevNextIcon(isNext = false, enabled = hasPrevious, size = 16.dp)
                                }

                                Box(
                                    Modifier
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            if (player.isPlaying) player.pause() else player.play()
                                        }
                                        .padding(6.dp)
                                ) {
                                    MxPlayPauseIcon(isPlaying = isPlaying, size = 22.dp)
                                }

                                Box(
                                    Modifier
                                        .clickable(enabled = hasNext) { onNextEpisode() }
                                        .padding(6.dp)
                                ) {
                                    MxPrevNextIcon(isNext = true, enabled = hasNext, size = 16.dp)
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    Modifier
                                        .clickable { isZoomed = !isZoomed }
                                        .padding(6.dp)
                                ) {
                                    MxAspectFitIcon(size = 16.dp)
                                }
                                Box(
                                    Modifier
                                        .clickable { onToggleFullscreen() }
                                        .padding(6.dp)
                                ) {
                                    MxFullscreenIcon(size = 16.dp)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MxSlimSeekBar(
    progress: Float,
    onSeek: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    var widthPx by remember { mutableFloatStateOf(1f) }
    val clamped = progress.coerceIn(0f, 1f)

    Box(
        modifier = modifier
            .height(22.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    onSeek((offset.x / widthPx).coerceIn(0f, 1f))
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures { change, _ ->
                    change.consume()
                    onSeek((change.position.x / widthPx).coerceIn(0f, 1f))
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxWidth().height(14.dp)) {
            val trackHeight = 2.5.dp.toPx()
            val centerY = size.height / 2f
            val activeX = size.width * clamped

            drawLine(
                color = Color(0x66FFFFFF),
                start = Offset(0f, centerY),
                end = Offset(size.width, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )
            drawLine(
                color = SeekBlue,
                start = Offset(0f, centerY),
                end = Offset(activeX, centerY),
                strokeWidth = trackHeight,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = SeekBlue,
                radius = 5.dp.toPx(),
                center = Offset(activeX, centerY)
            )
        }
    }
}

@Composable
fun MxPlayPauseIcon(isPlaying: Boolean, size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        if (isPlaying) {
            val barWidth = w * 0.24f
            val gap = w * 0.22f
            val leftX = (w - (barWidth * 2f + gap)) / 2f
            drawRoundRect(
                color = White,
                topLeft = Offset(leftX, h * 0.12f),
                size = Size(barWidth, h * 0.76f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
            drawRoundRect(
                color = White,
                topLeft = Offset(leftX + barWidth + gap, h * 0.12f),
                size = Size(barWidth, h * 0.76f),
                cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx())
            )
        } else {
            val path = Path().apply {
                moveTo(w * 0.20f, h * 0.10f)
                lineTo(w * 0.88f, h * 0.50f)
                lineTo(w * 0.20f, h * 0.90f)
                close()
            }
            drawPath(path = path, color = White)
        }
    }
}

@Composable
fun MxPrevNextIcon(isNext: Boolean, enabled: Boolean, size: Dp) {
    val tint = if (enabled) White else Color(0x55FFFFFF)
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val barWidth = w * 0.14f
        if (isNext) {
            val path = Path().apply {
                moveTo(w * 0.12f, h * 0.15f)
                lineTo(w * 0.72f, h * 0.50f)
                lineTo(w * 0.12f, h * 0.85f)
                close()
            }
            drawPath(path, color = tint)
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.76f, h * 0.15f),
                size = Size(barWidth, h * 0.70f),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
        } else {
            drawRoundRect(
                color = tint,
                topLeft = Offset(w * 0.10f, h * 0.15f),
                size = Size(barWidth, h * 0.70f),
                cornerRadius = CornerRadius(1.5.dp.toPx(), 1.5.dp.toPx())
            )
            val path = Path().apply {
                moveTo(w * 0.88f, h * 0.15f)
                lineTo(w * 0.28f, h * 0.50f)
                lineTo(w * 0.88f, h * 0.85f)
                close()
            }
            drawPath(path, color = tint)
        }
    }
}

@Composable
fun MxLockIcon(locked: Boolean, size: Dp) {
    val tint = if (locked) SeekBlue else White
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = tint,
            topLeft = Offset(w * 0.18f, h * 0.45f),
            size = Size(w * 0.64f, h * 0.46f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawArc(
            color = tint,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.28f, h * 0.14f),
            size = Size(w * 0.44f, h * 0.50f),
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
    }
}

@Composable
fun MxAspectFitIcon(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = White,
            topLeft = Offset(w * 0.08f, h * 0.16f),
            size = Size(w * 0.84f, h * 0.68f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = White,
            start = Offset(w * 0.28f, h * 0.22f),
            end = Offset(w * 0.28f, h * 0.78f),
            strokeWidth = stroke
        )
        drawLine(
            color = White,
            start = Offset(w * 0.72f, h * 0.22f),
            end = Offset(w * 0.72f, h * 0.78f),
            strokeWidth = stroke
        )
    }
}

@Composable
fun MxFullscreenIcon(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = White,
            topLeft = Offset(w * 0.10f, h * 0.16f),
            size = Size(w * 0.80f, h * 0.68f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = White,
            start = Offset(w * 0.30f, h * 0.34f),
            end = Offset(w * 0.66f, h * 0.66f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun MxBackArrowIcon(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.8.dp.toPx()
        drawLine(
            color = White,
            start = Offset(w * 0.85f, h * 0.50f),
            end = Offset(w * 0.15f, h * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = White,
            start = Offset(w * 0.48f, h * 0.18f),
            end = Offset(w * 0.15f, h * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = White,
            start = Offset(w * 0.48f, h * 0.82f),
            end = Offset(w * 0.15f, h * 0.50f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun MxAudioNoteIcon(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.7.dp.toPx()
        drawCircle(
            color = White,
            radius = w * 0.20f,
            center = Offset(w * 0.38f, h * 0.74f)
        )
        drawLine(
            color = White,
            start = Offset(w * 0.54f, h * 0.74f),
            end = Offset(w * 0.54f, h * 0.16f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = White,
            start = Offset(w * 0.54f, h * 0.16f),
            end = Offset(w * 0.82f, h * 0.26f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun MxSubtitleTrackIcon(size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.6.dp.toPx()
        drawRoundRect(
            color = White,
            topLeft = Offset(w * 0.08f, h * 0.16f),
            size = Size(w * 0.84f, h * 0.68f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawLine(
            color = White,
            start = Offset(w * 0.24f, h * 0.44f),
            end = Offset(w * 0.76f, h * 0.44f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
        drawLine(
            color = White,
            start = Offset(w * 0.24f, h * 0.64f),
            end = Offset(w * 0.58f, h * 0.64f),
            strokeWidth = stroke,
            cap = StrokeCap.Round
        )
    }
}

@Composable
fun MxRotateScreenIcon(size: Dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val stroke = 1.4.dp.toPx()
        drawRoundRect(
            color = White,
            topLeft = Offset(w * 0.22f, h * 0.15f),
            size = Size(w * 0.56f, h * 0.70f),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = stroke)
        )
    }
}

@Composable
fun PlayerSmallCirclePill(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .defaultMinSize(minWidth = 26.dp, minHeight = 26.dp)
            .clip(CircleShape)
            .background(Color(0x7714151C))
            .clickable { onClick() }
            .padding(horizontal = 7.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DownloadsScreen(onPlay: (DownloadInfo) -> Unit) {
    val context = LocalContext.current
    var downloads by remember { mutableStateOf<List<DownloadInfo>>(emptyList()) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun refreshList() {
        downloads = readDownloads(context)
    }

    LaunchedEffect(Unit) {
        while (true) {
            downloads = withContext(Dispatchers.IO) { readDownloads(context) }
            delay(1000)
        }
    }

    val failedItems = downloads.filter { it.status == "Failed" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("My Downloads", color = White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                Text("Saved in Downloads/AniStream • ${downloads.size} items", color = Gray, fontSize = 10.sp)
            }
            if (failedItems.isNotEmpty()) {
                SmallOutlineButton(
                    text = "Clear Failed (${failedItems.size})",
                    onClick = {
                        failedItems.forEach { removeDownload(context, it.id) }
                        refreshList()
                        statusMessage = "Failed downloads hata diye gaye hain."
                    }
                )
            }
        }

        statusMessage?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, color = AccentRed, fontSize = 10.sp)
        }

        Spacer(Modifier.height(12.dp))
        if (downloads.isEmpty()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("↓", color = AccentRed, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(6.dp))
                Text("Abhi koi download nahi hai", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text("Kisi bhi episode ke Watch Page par 'Download Episode' dabayein.", color = Gray, fontSize = 11.sp)
            }
        }

        downloads.forEach { item ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceDark)
                    .padding(11.dp)
            ) {
                Text(item.title, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        item.status,
                        color = when (item.status) {
                            "Completed" -> Color(0xFF4CAF50)
                            "Failed" -> AccentRed
                            else -> Gray
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (item.sizeText.isNotBlank()) {
                        Text("  •  ${item.sizeText}", color = Gray, fontSize = 10.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    Text("${if (item.status == "Completed") 100 else item.progress}%", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                if (item.status == "Downloading" || item.status == "Waiting" || item.status == "Paused") {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { item.progress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = AccentRed,
                        trackColor = Color(0xFF33343A)
                    )
                    Spacer(Modifier.height(7.dp))
                    SmallOutlineButton(
                        text = "✕ Cancel Download",
                        onClick = {
                            removeDownload(context, item.id)
                            refreshList()
                            statusMessage = "${item.title} cancel kar diya gaya."
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (item.status == "Failed") {
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (item.sourceUrl.isNotBlank()) {
                            SmallOutlineButton(
                                text = "↻ Retry",
                                onClick = {
                                    runCatching {
                                        removeDownload(context, item.id)
                                        startDownload(context, item.sourceUrl, item.title)
                                        refreshList()
                                        statusMessage = "Download dubara start ho gaya."
                                    }.onFailure {
                                        statusMessage = it.message ?: "Retry fail ho gaya."
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                selected = true
                            )
                        }
                        SmallOutlineButton(
                            text = "🗑 Delete",
                            onClick = {
                                removeDownload(context, item.id)
                                refreshList()
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (item.status == "Completed") {
                    Spacer(Modifier.height(7.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SmallOutlineButton(
                            text = "▶ PLAY OFFLINE",
                            onClick = { if (item.localUri.isNotBlank()) onPlay(item) },
                            modifier = Modifier.weight(1f),
                            enabled = item.localUri.isNotBlank(),
                            selected = true
                        )
                        SmallOutlineButton(
                            text = "🗑 Delete",
                            onClick = {
                                removeDownload(context, item.id)
                                refreshList()
                                statusMessage = "${item.title} delete kar diya gaya."
                            }
                        )
                    }
                    if (item.localUri.isBlank()) {
                        Text("Downloaded file ka URI nahi mil raha.", color = AccentRed, fontSize = 9.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LocalDownloadedPlayer(download: DownloadInfo, onBack: () -> Unit) {
    var fullscreen by remember { mutableStateOf(false) }
    BackHandler {
        if (fullscreen) fullscreen = false else onBack()
    }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        if (!fullscreen) {
            Row(
                Modifier.fillMaxWidth().background(SurfaceDark).padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("‹", color = White, fontSize = 24.sp, modifier = Modifier.clickable { onBack() })
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("OFFLINE PLAYER", color = AccentRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text(download.title, color = White, fontSize = 10.sp, maxLines = 1)
                }
                SmallOutlineButton("⛶", onClick = { fullscreen = true })
            }
        }
        Box(Modifier.fillMaxWidth().weight(1f).background(Color.Black)) {
            WatchPlayer(
                videoUrl = download.localUri,
                episodeTitle = download.title,
                fullscreen = fullscreen,
                hasPrevious = false,
                hasNext = false,
                currentLanguage = "Offline",
                availableLanguages = listOf("Offline"),
                currentQuality = "Local",
                availableQualities = listOf("Local"),
                onSelectLanguage = {},
                onSelectQuality = {},
                onPreviousEpisode = {},
                onNextEpisode = {},
                onToggleFullscreen = { fullscreen = !fullscreen },
                onBack = { if (fullscreen) fullscreen = false else onBack() }
            )
        }
        if (!fullscreen) {
            Text(
                "✓ Saved on your device • Offline playback",
                color = Color(0xFF4CAF50),
                fontSize = 10.sp,
                modifier = Modifier.padding(12.dp)
            )
        }
    }
}

/* ULTRA-COMPACT CUSTOM OUTLINE BUTTON (No Material3 48dp forced height) */
@Composable
fun SmallOutlineButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false
) {
    val borderColor = when {
        !enabled -> Color(0xFF2D2E37)
        selected -> AccentRed
        else -> Color(0xFF434552)
    }
    val bgColor = if (selected) Color(0xFF28121B) else Color(0xFF171821)
    val txtColor = when {
        !enabled -> Gray
        selected -> AccentRed
        else -> White
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 9.dp, vertical = 4.5.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = txtColor,
            fontSize = 9.5.sp,
            lineHeight = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}

@Composable
fun DetailSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(13.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(AccentRed)
            )
            Spacer(Modifier.width(7.dp))
            Text(title, color = White, fontSize = 14.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(6.dp))
        content()
    }
}

@Composable
fun InfoLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text("$label: ", color = Gray, fontSize = 11.sp)
        Text(value, color = White, fontSize = 11.sp)
    }
}
