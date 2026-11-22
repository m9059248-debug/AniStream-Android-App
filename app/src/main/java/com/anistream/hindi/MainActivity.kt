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
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
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
private val SurfaceDark = Color(0xFF12141C)
private val CardDark = Color(0xFF151822)
private val AccentRed = Color(0xFFFF1744)
private val OngoingGreen = Color(0xFF00C853)
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

private val DEFAULT_GENRES = listOf(
    "All", "Action", "Adventure", "Anime Movie", "Comedy", "Dark Fantasy",
    "Drama", "Fantasy", "Historical", "Horror", "Magic", "Mystery", "Romance", "Sci-Fi", "Supernatural"
)

private val animeDetailsCache = mutableMapOf<String, AnimeDetails>()
private val seasonsCache = mutableMapOf<String, List<AnimeSeason>>()
private val episodesCache = mutableMapOf<String, List<AnimeEpisode>>()
private val videoServersCache = mutableMapOf<String, List<VideoServer>>()

data class Anime(
    val id: String,
    val title: String,
    val posterUrl: String,
    val bannerUrl: String,
    val description: String,
    val animeType: String,
    val status: String = "ongoing",
    val season: String = "TV",
    val language: String = "Hindi",
    val genres: List<String> = emptyList()
)
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
    val url = "$SUPABASE_URL/rest/v1/anime?select=*&is_published=eq.true&order=created_at.desc"
    val a = JSONArray(request(url, supabase = true))
    (0 until a.length()).map { i ->
        val x = a.getJSONObject(i)
        val poster = x.optString("poster_url").takeUnless { it == "null" }.orEmpty()
        val banner = x.optString("banner_url").takeUnless { it == "null" || it.isBlank() } ?: poster
        val rawGenres = x.opt("genres")
        val genresList = when (rawGenres) {
            is JSONArray -> (0 until rawGenres.length()).mapNotNull { idx ->
                rawGenres.optString(idx).takeIf { it.isNotBlank() && it != "null" }
            }
            is String -> rawGenres.split(",").map { it.trim() }.filter { it.isNotBlank() && it != "null" }
            else -> emptyList()
        }
        val seasonVal = x.optString("season").takeUnless { it == "null" || it.isBlank() }
            ?: x.optString("release_year").takeUnless { it == "null" || it.isBlank() }
            ?: "2026"
        val langVal = x.optString("language").takeUnless { it == "null" || it.isBlank() } ?: "Hindi"
        val statusVal = x.optString("status").takeUnless { it == "null" || it.isBlank() } ?: "ongoing"
        val typeVal = x.optString("anime_type").takeUnless { it == "null" || it.isBlank() } ?: "Series"

        Anime(
            id = x.optString("id"),
            title = x.optString("title"),
            posterUrl = poster,
            bannerUrl = banner,
            description = x.optString("description").takeUnless { it == "null" }.orEmpty(),
            animeType = typeVal,
            status = statusVal,
            season = seasonVal,
            language = langVal,
            genres = genresList
        )
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
    var topNavCategory by remember { mutableStateOf("Home") }
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
    val displayUserName = userSession?.fullName?.takeIf { it.isNotBlank() }?.take(10) ?: "Login"

    MaterialTheme {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                if (selectedAnime == null && offlineDownload == null) {
                    NavigationBar(containerColor = SurfaceDark, tonalElevation = 0.dp) {
                        val labels = listOf("Home", "Search", "My List", "Downloads", "Profile")
                        val icons = listOf("⌂", "⌕", "♡", "↓", "♙")
                        labels.forEachIndexed { index, label ->
                            NavigationBarItem(
                                selected = tab == index,
                                onClick = { tab = index },
                                icon = { Text(icons[index], fontSize = 19.sp) },
                                label = { Text(label, fontSize = 10.sp, maxLines = 1) },
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
                                .background(Color(0xFF0B0D13))
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    tab = 0
                                    topNavCategory = "Home"
                                }
                            ) {
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(AccentRed)
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                ) {
                                    Text("HD", color = White, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                }
                                Spacer(Modifier.width(6.dp))
                                Text("Ani", color = White, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                                Text("Stream", color = AccentRed, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                            }

                            Spacer(Modifier.width(10.dp))

                            // Top Nav Links: Replaced "Home" button with "↻ Refresh" button
                            Row(
                                Modifier
                                    .weight(1f)
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val navItems = listOf(
                                    "Home" to "↻ Refresh",
                                    "Ongoing" to "⚡ Ongoing",
                                    "Series" to "📺 Series",
                                    "Movies" to "🎬 Movies",
                                    "All" to "📚 All Anime"
                                )
                                navItems.forEach { (key, label) ->
                                    val active = tab == 0 && topNavCategory == key
                                    Text(
                                        text = label,
                                        color = if (active) White else Gray,
                                        fontSize = 11.sp,
                                        fontWeight = if (active) FontWeight.ExtraBold else FontWeight.Medium,
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (active) Color(0xFF23151D) else Color.Transparent)
                                            .clickable {
                                                tab = 0
                                                topNavCategory = key
                                                if (key == "Home") loadAllAnime()
                                            }
                                            .padding(horizontal = 7.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.width(8.dp))

                            Row(
                                Modifier
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(Color(0xFF1A1218))
                                    .border(1.dp, AccentRed, RoundedCornerShape(20.dp))
                                    .clickable { tab = 4 }
                                    .padding(start = 4.dp, end = 9.dp, top = 3.dp, bottom = 3.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                UserAvatarView(
                                    avatarUrl = userSession?.avatarUrl.orEmpty(),
                                    fallbackInitial = badgeLetter,
                                    size = 22.dp,
                                    fontSize = 10.sp,
                                    modifier = Modifier.clip(CircleShape)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = displayUserName,
                                    color = White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1
                                )
                            }
                        }

                        when (tab) {
                            0 -> HomeScreen(
                                animeList = animeList,
                                loading = loading,
                                error = error,
                                myListIds = myListIds,
                                topNavCategory = topNavCategory,
                                onTopNavChange = { topNavCategory = it },
                                onRefresh = { loadAllAnime() },
                                onToggleMyList = { toggleMyList(it.id) },
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

/* HOME SCREEN WITH COMPACT FULL-VISIBLE PHONE BANNER + SINGLE-LINE SEARCH + NORMAL POSTER CARDS */
@Composable
fun HomeScreen(
    animeList: List<Anime>,
    loading: Boolean,
    error: String?,
    myListIds: Set<String>,
    topNavCategory: String,
    onTopNavChange: (String) -> Unit,
    onRefresh: () -> Unit,
    onToggleMyList: (Anime) -> Unit,
    onAnimeClick: (Anime) -> Unit
) {
    var homeSearchQuery by remember { mutableStateOf("") }
    var selectedLanguage by remember { mutableStateOf("All Languages") }
    var selectedGenre by remember { mutableStateOf("All") }
    var showLangDropdown by remember { mutableStateOf(false) }
    var showGenreDropdown by remember { mutableStateOf(false) }

    val availableLanguages = remember(animeList) {
        val langs = mutableSetOf("All Languages", "Hindi", "English", "Japanese", "Tamil", "Telugu")
        animeList.forEach { a ->
            a.language.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEach { langs.add(it) }
        }
        langs.toList()
    }

    val availableGenres = remember(animeList) {
        val gSet = linkedSetOf<String>()
        gSet.addAll(DEFAULT_GENRES)
        animeList.forEach { a -> a.genres.forEach { if (it.isNotBlank()) gSet.add(it) } }
        gSet.toList()
    }

    val filteredAnime = remember(animeList, homeSearchQuery, selectedLanguage, selectedGenre, topNavCategory) {
        animeList.filter { anime ->
            val matchesQuery = homeSearchQuery.isBlank() ||
                anime.title.contains(homeSearchQuery, true) ||
                anime.description.contains(homeSearchQuery, true) ||
                anime.language.contains(homeSearchQuery, true) ||
                anime.genres.any { it.contains(homeSearchQuery, true) }

            val matchesLang = selectedLanguage == "All Languages" ||
                anime.language.contains(selectedLanguage, true)

            val matchesGenre = when (selectedGenre) {
                "All" -> true
                "Anime Movie" -> anime.animeType.equals("Movie", true)
                else -> anime.genres.any { it.equals(selectedGenre, true) } ||
                    anime.description.contains(selectedGenre, true)
            }

            val matchesTopNav = when (topNavCategory) {
                "Ongoing" -> anime.status.equals("ongoing", true)
                "Series" -> !anime.animeType.equals("Movie", true)
                "Movies" -> anime.animeType.equals("Movie", true)
                else -> true
            }

            matchesQuery && matchesLang && matchesGenre && matchesTopNav
        }
    }

    val ongoingSeries = remember(filteredAnime) {
        filteredAnime.filter { it.status.equals("ongoing", true) && !it.animeType.equals("Movie", true) }
            .ifEmpty { filteredAnime.filter { !it.animeType.equals("Movie", true) } }
    }
    val animeSeries = remember(filteredAnime) {
        filteredAnime.filter { !it.animeType.equals("Movie", true) }
    }
    val animeMovies = remember(filteredAnime) {
        filteredAnime.filter { it.animeType.equals("Movie", true) }
    }

    val heroSlides = remember(animeList) { animeList.take(7) }
    var currentSlide by remember(heroSlides.size) { mutableIntStateOf(0) }

    LaunchedEffect(heroSlides.size, currentSlide) {
        if (heroSlides.size > 1) {
            delay(4500L)
            currentSlide = (currentSlide + 1) % heroSlides.size
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        when {
            loading -> Box(Modifier.fillMaxWidth().height(215.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AccentRed)
            }
            error != null -> Column(
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(SurfaceDark)
                    .padding(16.dp)
            ) {
                Text("Unable to load anime", color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(4.dp))
                Text(error, color = AccentRed, fontSize = 12.sp)
                Spacer(Modifier.height(10.dp))
                SmallOutlineButton(text = "↻ Try Again", onClick = onRefresh, selected = true)
            }
            heroSlides.isEmpty() -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
                Text("No published anime found yet.", color = Gray, fontSize = 13.sp)
            }
            else -> {
                val safeIndex = currentSlide.coerceIn(0, heroSlides.lastIndex)
                val hero = heroSlides[safeIndex]
                val isHeroInList = myListIds.contains(hero.id)
                var dragDelta by remember { mutableFloatStateOf(0f) }

                // 1. COMPACT FULL-VISIBLE PHONE BANNER (215.dp height)
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(215.dp)
                        .background(SurfaceDark)
                        .pointerInput(heroSlides.size, safeIndex) {
                            detectHorizontalDragGestures(
                                onDragStart = { dragDelta = 0f },
                                onDragEnd = {
                                    if (heroSlides.size > 1) {
                                        if (dragDelta < -50f) {
                                            currentSlide = (safeIndex + 1) % heroSlides.size
                                        } else if (dragDelta > 50f) {
                                            currentSlide = if (safeIndex - 1 < 0) heroSlides.lastIndex else safeIndex - 1
                                        }
                                    }
                                    dragDelta = 0f
                                }
                            ) { change, amount ->
                                change.consume()
                                dragDelta += amount
                            }
                        }
                ) {
                    AsyncImage(
                        model = hero.bannerUrl.ifBlank { hero.posterUrl },
                        contentDescription = hero.title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xEE08090D),
                                        Color(0xBB08090D),
                                        Color(0x4408090D)
                                    )
                                )
                            )
                    )
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color(0x2208090D),
                                        Color(0x7708090D),
                                        Color(0xFF08090D)
                                    )
                                )
                            )
                    )

                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2D1018))
                                .border(1.dp, Color(0xFF5A1A29), RoundedCornerShape(10.dp))
                                .padding(horizontal = 8.dp, vertical = 2.5.dp)
                        ) {
                            Text(
                                "• ANISTREAM IN HINDI",
                                color = AccentRed,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.7.sp
                            )
                        }

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = hero.title,
                            color = White,
                            fontSize = 18.sp,
                            lineHeight = 21.sp,
                            fontWeight = FontWeight.Black,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        Text(
                            text = "Watch in ${hero.language.ifBlank { "Hindi" }}",
                            color = AccentRed,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = hero.description
                                .ifBlank { "Stream full episodes in Hindi dub and official subtitles on AniStream." }
                                .replace(Regex("\\<[^\\>]*\\>"), ""),
                            color = Color(0xFFC8CAD4),
                            fontSize = 10.5.sp,
                            lineHeight = 13.5.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        Spacer(Modifier.height(6.dp))

                        Row(
                            Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val metaTags = buildList {
                                add(hero.season.ifBlank { "TV Series" })
                                if (hero.genres.isNotEmpty()) add(hero.genres.take(3).joinToString(", "))
                                add(hero.language.ifBlank { "Hindi" })
                            }
                            metaTags.forEach { tag ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(5.dp))
                                        .background(Color(0xFF191C26))
                                        .border(1.dp, Color(0xFF2C3040), RoundedCornerShape(5.dp))
                                        .padding(horizontal = 7.dp, vertical = 2.5.dp)
                                ) {
                                    Text(tag, color = Color(0xFFE0E2EC), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AccentRed)
                                        .clickable { onAnimeClick(hero) }
                                        .padding(horizontal = 12.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "▶  Details",
                                        color = White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                Box(
                                    Modifier
                                        .height(32.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isHeroInList) AccentRed else Color(0xFF1C1F2B))
                                        .border(
                                            1.dp,
                                            if (isHeroInList) AccentRed else Color(0xFF353849),
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onToggleMyList(hero) }
                                        .padding(horizontal = 11.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (isHeroInList) "♥  In My List" else "+  My List",
                                        color = White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            if (heroSlides.size > 1) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF191C26))
                                            .border(1.dp, Color(0xFF2F3242), CircleShape)
                                            .clickable {
                                                currentSlide = if (safeIndex - 1 < 0) heroSlides.lastIndex else safeIndex - 1
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("‹", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Box(
                                        Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFF191C26))
                                            .border(1.dp, Color(0xFF2F3242), CircleShape)
                                            .clickable {
                                                currentSlide = (safeIndex + 1) % heroSlides.size
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text("›", color = White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Spacer(Modifier.width(2.dp))
                                    heroSlides.indices.forEach { idx ->
                                        val active = idx == safeIndex
                                        Box(
                                            Modifier
                                                .height(4.5.dp)
                                                .width(if (active) 12.dp else 4.5.dp)
                                                .clip(CircleShape)
                                                .background(if (active) AccentRed else Color(0xFF3B3E4F))
                                                .clickable { currentSlide = idx }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. COMPACT SINGLE-LINE SEARCH BAR + LANGUAGE & GENRE DROPDOWNS + GENRE CHIPS
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(38.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceDark)
                            .border(
                                1.dp,
                                if (homeSearchQuery.isNotEmpty()) AccentRed else Color(0xFF252836),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                Modifier.weight(1f),
                                contentAlignment = Alignment.CenterStart
                            ) {
                                if (homeSearchQuery.isEmpty()) {
                                    Text(
                                        text = "🔍 Search anime, movies, genre or language...",
                                        color = Gray,
                                        fontSize = 11.5.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                BasicTextField(
                                    value = homeSearchQuery,
                                    onValueChange = { homeSearchQuery = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(AccentRed),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            if (homeSearchQuery.isNotEmpty()) {
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "✕",
                                    color = AccentRed,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { homeSearchQuery = "" }
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(Modifier.weight(1f)) {
                            SmallOutlineButton(
                                text = "$selectedLanguage  ▾",
                                onClick = { showLangDropdown = true },
                                modifier = Modifier.fillMaxWidth(),
                                selected = selectedLanguage != "All Languages"
                            )
                            DropdownMenu(
                                expanded = showLangDropdown,
                                onDismissRequest = { showLangDropdown = false }
                            ) {
                                availableLanguages.forEach { lang ->
                                    DropdownMenuItem(
                                        text = { Text(lang, fontSize = 12.sp) },
                                        onClick = {
                                            selectedLanguage = lang
                                            showLangDropdown = false
                                        }
                                    )
                                }
                            }
                        }

                        Box(Modifier.weight(1f)) {
                            SmallOutlineButton(
                                text = "${if (selectedGenre == "All") "All genres" else selectedGenre}  ▾",
                                onClick = { showGenreDropdown = true },
                                modifier = Modifier.fillMaxWidth(),
                                selected = selectedGenre != "All"
                            )
                            DropdownMenu(
                                expanded = showGenreDropdown,
                                onDismissRequest = { showGenreDropdown = false }
                            ) {
                                availableGenres.forEach { g ->
                                    DropdownMenuItem(
                                        text = { Text(if (g == "All") "All genres" else g, fontSize = 12.sp) },
                                        onClick = {
                                            selectedGenre = g
                                            showGenreDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        availableGenres.forEach { genre ->
                            val isSelected = selectedGenre.equals(genre, true)
                            Box(
                                Modifier
                                    .height(32.dp)
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(if (isSelected) AccentRed else SurfaceDark)
                                    .border(
                                        1.dp,
                                        if (isSelected) AccentRed else Color(0xFF262938),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .clickable { selectedGenre = genre }
                                    .padding(horizontal = 13.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = genre,
                                    color = White,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(6.dp))

                // 3. ANIME SECTIONS
                if (filteredAnime.isEmpty()) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(SurfaceDark)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("No matching anime found", color = White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(6.dp))
                            SmallOutlineButton(
                                text = "Reset Filters",
                                onClick = {
                                    homeSearchQuery = ""
                                    selectedLanguage = "All Languages"
                                    selectedGenre = "All"
                                    onTopNavChange("Home")
                                },
                                selected = true
                            )
                        }
                    }
                } else {
                    if (topNavCategory == "Home") {
                        AnimeCatalogRowSection(
                            title = "⚡ Ongoing Series",
                            subtitle = "Currently running weekly episodes",
                            animeList = ongoingSeries,
                            onAnimeClick = onAnimeClick
                        )
                        AnimeCatalogRowSection(
                            title = "📺 Anime Series",
                            subtitle = "Complete seasons and episodic series",
                            animeList = animeSeries,
                            onAnimeClick = onAnimeClick
                        )
                        if (animeMovies.isNotEmpty()) {
                            AnimeCatalogRowSection(
                                title = "🎬 Anime Movies",
                                subtitle = "Standalone movies & theatrical releases",
                                animeList = animeMovies,
                                onAnimeClick = onAnimeClick
                            )
                        }
                        AnimeCatalogRowSection(
                            title = "📚 All Anime Catalog",
                            subtitle = "Search and filter through the complete library (${filteredAnime.size})",
                            animeList = filteredAnime,
                            onAnimeClick = onAnimeClick
                        )
                    } else {
                        AnimeCatalogRowSection(
                            title = "📚 $topNavCategory Catalog (${filteredAnime.size})",
                            subtitle = "Showing filtered results",
                            animeList = filteredAnime,
                            onAnimeClick = onAnimeClick
                        )
                    }
                }

                Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                    Text("ANISTREAM HINDI", color = AccentRed, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.3.sp)
                    Text("Your anime world, all in one place.", color = Gray, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun AnimeCatalogRowSection(
    title: String,
    subtitle: String,
    animeList: List<Anime>,
    onAnimeClick: (Anime) -> Unit
) {
    if (animeList.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(bottom = 22.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, color = AccentRed, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, color = Gray, fontSize = 10.5.sp)
            }
            Text("Browse all ›", color = AccentRed, fontSize = 10.5.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            animeList.forEach { anime ->
                WebsiteStyleAnimeCard(
                    anime = anime,
                    onClick = { onAnimeClick(anime) }
                )
            }
        }
    }
}

/* NORMAL POSTER CARD: Tap poster directly to open Details */
@Composable
fun WebsiteStyleAnimeCard(
    anime: Anime,
    onClick: () -> Unit
) {
    val isMovie = anime.animeType.equals("Movie", true)
    val isCompleted = anime.status.equals("completed", true)
    val badgeText = when {
        isMovie -> "🎬 Movie"
        isCompleted -> "Completed"
        else -> "Ongoing"
    }
    val badgeColor = when {
        isMovie -> AccentRed
        isCompleted -> Color(0xFF232734)
        else -> OngoingGreen
    }

    Column(
        Modifier
            .width(136.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardDark)
            .border(1.dp, Color(0xFF222534), RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(178.dp)
                .background(SurfaceDark)
        ) {
            AsyncImage(
                model = anime.posterUrl,
                contentDescription = anime.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(7.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeColor)
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    color = if (!isMovie && !isCompleted) Color.Black else White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }

        Column(Modifier.padding(horizontal = 9.dp, vertical = 8.dp)) {
            Text(
                text = anime.title,
                color = White,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "${anime.season.take(12)} • ${anime.language.substringBefore(",")}",
                color = Gray,
                fontSize = 9.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/* REUSABLE COMMENTS & ADMIN REPLY SECTION */
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
                .clip(RoundedCornerShape(12.dp))
                .background(SurfaceDark)
                .padding(12.dp)
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
                        fontSize = 12.sp,
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
                    placeholder = { Text("Write a comment as ${userSession.fullName.ifBlank { "Member" }}...", color = Gray, fontSize = 12.sp) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = Color(0xFF2D2F3E),
                        cursorColor = AccentRed
                    )
                )
                Spacer(Modifier.height(9.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallOutlineButton(
                        text = "↻  Refresh",
                        onClick = { loadComments() },
                        enabled = !loading && !posting
                    )
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
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
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (posting) "Posting..." else "Post Comment",
                            color = White,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            statusMsg?.let { msg ->
                Spacer(Modifier.height(6.dp))
                Text(
                    msg,
                    color = if (isError) AccentRed else Color(0xFF4CAF50),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        when {
            loading -> Box(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AccentRed, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            comments.isEmpty() -> Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(11.dp))
                    .background(SurfaceDark)
                    .padding(16.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Abhi koi comment nahi hai. Pehla comment aap karein!", color = Gray, fontSize = 12.sp)
            }
            else -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                comments.forEach { item ->
                    val initial = (item.userName.firstOrNull() ?: 'U').uppercaseChar()
                    val isOwnComment = userSession != null && item.userId == userSession.userId

                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(11.dp))
                            .background(SurfaceDark)
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                Modifier
                                    .size(26.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF28151E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    initial.toString(),
                                    color = AccentRed,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text(item.userName, color = White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                if (item.createdAt.isNotBlank()) {
                                    Text(item.createdAt, color = Gray, fontSize = 9.5.sp)
                                }
                            }
                            if (isOwnComment) {
                                Text(
                                    "🗑 Delete",
                                    color = AccentRed,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable {
                                            scope.launch {
                                                runCatching { deleteAnimeComment(item.id, userSession!!) }
                                                loadComments()
                                            }
                                        }
                                        .padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(item.content, color = Color(0xFFE2E4EC), fontSize = 12.sp)

                        if (item.adminReply.isNotBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF1A1218))
                                    .border(1.dp, Color(0xFF3A1621), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(AccentRed)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "ADMIN REPLY",
                                            color = White,
                                            fontSize = 8.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        )
                                    }
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "AniStream Official",
                                        color = Gray,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Spacer(Modifier.height(4.dp))
                                Text(item.adminReply, color = White, fontSize = 11.5.sp)
                            }
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
                Text("My List", color = White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
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
                    .padding(vertical = 44.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("♡", color = AccentRed, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("Your My List is Empty", color = White, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(5.dp))
                Text(
                    "Kisi bhi Anime par 'Add to My List' dabayein taaki aap use baad mein aasani se dekh sakein.",
                    color = Gray,
                    fontSize = 12.sp
                )
                Spacer(Modifier.height(14.dp))
                Box(
                    Modifier
                        .height(38.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(AccentRed)
                        .clickable { onExploreClick() }
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("EXPLORE ANIME", color = White, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(11.dp)) {
                savedAnime.forEach { anime ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(13.dp))
                            .background(SurfaceDark)
                            .clickable { onAnimeClick(anime) }
                            .padding(11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            anime.posterUrl,
                            anime.title,
                            Modifier
                                .size(74.dp, 100.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(Background),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (anime.animeType.equals("Movie", true)) AccentRed else Color(0xFF282A36))
                                    .padding(horizontal = 7.dp, vertical = 2.5.dp)
                            ) {
                                Text(
                                    anime.animeType.ifBlank { "Series" }.uppercase(),
                                    color = White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            }
                            Spacer(Modifier.height(5.dp))
                            Text(anime.title, color = White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                            Spacer(Modifier.height(3.dp))
                            Text(
                                anime.description.ifBlank { "Watch on AniStream Hindi" }.replace(Regex("\\<[^\\>]*\\>"), ""),
                                color = Gray,
                                fontSize = 11.sp,
                                maxLines = 2
                            )
                            Spacer(Modifier.height(8.dp))
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
                Text("Change Profile Photo", color = White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(4.dp))
                Text("Gallery se apni photo chunein ya Anime Avatar select karein:", color = Gray, fontSize = 11.sp)

                Spacer(Modifier.height(12.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(38.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(AccentRed)
                        .clickable { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    Text("📷  CHOOSE FROM GALLERY", color = White, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                }

                Spacer(Modifier.height(12.dp))
                Text("OR PICK ANIME AVATAR", color = Gray, fontSize = 9.5.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    PRESET_ANIME_AVATARS.forEach { presetUrl ->
                        val isSelected = session.avatarUrl == presetUrl
                        Box(
                            Modifier
                                .size(50.dp)
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

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = customAvatarUrlInput,
                    onValueChange = { customAvatarUrlInput = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Or Paste Image URL", color = Gray, fontSize = 11.sp) },
                    placeholder = { Text("https://...", color = Gray, fontSize = 11.sp) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = White,
                        unfocusedTextColor = White,
                        focusedBorderColor = AccentRed,
                        unfocusedBorderColor = Gray,
                        cursorColor = AccentRed
                    )
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
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
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        if (session == null) {
            Text(
                if (isSignUp) "Create Account" else "Welcome Back",
                color = White,
                fontSize = 22.sp,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                if (isSignUp) "Sign up with Supabase to sync your profile & My List."
                else "Login to access your AniStream Hindi profile & cloud My List.",
                color = Gray,
                fontSize = 12.sp
            )
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(SurfaceDark)
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (!isSignUp) AccentRed else Color.Transparent)
                        .clickable {
                            isSignUp = false
                            errorMsg = null
                            infoMsg = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("LOGIN", color = White, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                }
                Box(
                    Modifier
                        .weight(1f)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSignUp) AccentRed else Color.Transparent)
                        .clickable {
                            isSignUp = true
                            errorMsg = null
                            infoMsg = null
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("SIGN UP", color = White, fontSize = 11.5.sp, fontWeight = FontWeight.ExtraBold)
                }
            }

            Spacer(Modifier.height(14.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(SurfaceDark)
                    .padding(15.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp)
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
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF2D1219))
                            .padding(10.dp)
                    ) {
                        Text(it, color = AccentRed, fontSize = 11.5.sp)
                    }
                }

                infoMsg?.let {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF112A1B))
                            .padding(10.dp)
                    ) {
                        Text(it, color = Color(0xFF4CAF50), fontSize = 11.5.sp)
                    }
                }

                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(9.dp))
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
                        CircularProgressIndicator(Modifier.size(18.dp), color = White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            if (isSignUp) "CREATE ACCOUNT" else "LOGIN",
                            color = White,
                            fontSize = 12.sp,
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
                    .clip(RoundedCornerShape(16.dp))
                    .background(SurfaceDark)
                    .padding(15.dp)
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(62.dp)
                            .clickable { showAvatarDialog = true }
                    ) {
                        UserAvatarView(
                            avatarUrl = session.avatarUrl,
                            fallbackInitial = initial,
                            size = 58.dp,
                            fontSize = 22.sp,
                            modifier = Modifier
                                .clip(CircleShape)
                                .border(1.5.dp, AccentRed, CircleShape)
                        )
                        Box(
                            Modifier
                                .size(20.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(AccentRed)
                                .border(1.5.dp, SurfaceDark, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✎", color = White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF163222))
                                .padding(horizontal = 7.dp, vertical = 2.5.dp)
                        ) {
                            Text(
                                "VERIFIED MEMBER",
                                color = Color(0xFF4CAF50),
                                fontSize = 8.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            session.fullName.ifBlank { "AniStream User" },
                            color = White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(session.email, color = Gray, fontSize = 11.5.sp)
                    }
                }

                Spacer(Modifier.height(11.dp))
                SmallOutlineButton(
                    text = if (session.avatarUrl.isBlank()) "📷  Set Profile Photo" else "📷  Change Profile Photo",
                    onClick = { showAvatarDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    selected = true
                )
            }

            Spacer(Modifier.height(12.dp))

            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(15.dp))
                    .background(SurfaceDark)
                    .padding(15.dp)
            ) {
                Text("ACCOUNT DETAILS", color = AccentRed, fontSize = 10.5.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.height(8.dp))
                InfoLine("Full Name", session.fullName.ifBlank { "Not set" })
                InfoLine("Email", session.email)
                InfoLine("User ID", session.userId.ifBlank { "N/A" })
                InfoLine("Joined On", session.createdAt.ifBlank { "Active" })
                InfoLine("My List Saved", "$myListCount Anime")
                InfoLine("Saved Downloads", "$downloadsCount Episodes")
            }

            Spacer(Modifier.height(12.dp))

            infoMsg?.let {
                Text(it, color = Color(0xFF4CAF50), fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 8.dp))
            }
            errorMsg?.let {
                Text(it, color = AccentRed, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 8.dp))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(9.dp), modifier = Modifier.fillMaxWidth()) {
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

            Spacer(Modifier.height(9.dp))

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

            Spacer(Modifier.height(11.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .clip(RoundedCornerShape(9.dp))
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
                    fontSize = 12.sp,
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
        val matchesText = anime.title.contains(searchText, true) ||
            anime.description.contains(searchText, true) ||
            anime.language.contains(searchText, true) ||
            anime.genres.any { it.contains(searchText, true) }
        val matchesType = when (filterType) {
            "Movies" -> anime.animeType.equals("Movie", true)
            "Series" -> !anime.animeType.equals("Movie", true)
            else -> true
        }
        matchesText && matchesType
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Text("Search Anime", color = White, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = searchText,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search anime title, genre or language...", color = Gray, fontSize = 12.sp) },
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
        Spacer(Modifier.height(9.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
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
                fontSize = 11.sp
            )
        }
        Spacer(Modifier.height(10.dp))
        if (filtered.isEmpty()) {
            Box(