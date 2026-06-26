package com.example

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.Locale

class SpotifyManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("spotify_prefs", Context.MODE_PRIVATE)
    private val client = OkHttpClient()

    private var mediaPlayer: MediaPlayer? = null

    // State flows for Compose observation
    private val _clientId = MutableStateFlow(prefs.getString("client_id", "") ?: "")
    val clientId = _clientId.asStateFlow()

    private val _clientSecret = MutableStateFlow(prefs.getString("client_secret", "") ?: "")
    val clientSecret = _clientSecret.asStateFlow()

    private val _username = MutableStateFlow(prefs.getString("username", "") ?: "")
    val username = _username.asStateFlow()

    private val _password = MutableStateFlow(prefs.getString("password", "") ?: "")
    val password = _password.asStateFlow()

    private val _isSimulated = MutableStateFlow(prefs.getBoolean("is_simulated", false))
    val isSimulated = _isSimulated.asStateFlow()

    private val _isConnected = MutableStateFlow(prefs.getString("access_token", null) != null || prefs.getBoolean("is_simulated", false))
    val isConnected = _isConnected.asStateFlow()

    private val _currentTrack = MutableStateFlow<SpotifyTrack?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    companion object {
        const val REDIRECT_URI = "https://localhost/callback"
        
        @Volatile
        private var INSTANCE: SpotifyManager? = null

        fun getInstance(context: Context): SpotifyManager {
            return INSTANCE ?: synchronized(this) {
                val instance = SpotifyManager(context)
                INSTANCE = instance
                instance
            }
        }
    }

    data class SpotifyTrack(
        val id: String,
        val name: String,
        val artist: String,
        val albumArtUrl: String?,
        val previewUrl: String?,
        val externalUrl: String
    )

    fun saveCredentials(id: String, secret: String) {
        _clientId.value = id.trim()
        _clientSecret.value = secret.trim()
        _isSimulated.value = false
        prefs.edit()
            .putString("client_id", id.trim())
            .putString("client_secret", secret.trim())
            .putBoolean("is_simulated", false)
            .apply()
    }

    fun loginWithUsernamePassword(user: String, pass: String) {
        val u = user.trim()
        val p = pass.trim()
        _username.value = u
        _password.value = p
        _isSimulated.value = true
        _isConnected.value = true
        _error.value = null
        prefs.edit()
            .putString("username", u)
            .putString("password", p)
            .putBoolean("is_simulated", true)
            .putString("access_token", "simulated_token")
            .apply()
    }

    fun disconnect() {
        stopPreview()
        _currentTrack.value = null
        _isConnected.value = false
        _isSimulated.value = false
        _username.value = ""
        _password.value = ""
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("expires_at")
            .remove("username")
            .remove("password")
            .remove("is_simulated")
            .apply()
    }

    fun getAuthorizeUrl(): String {
        return Uri.parse("https://accounts.spotify.com/authorize").buildUpon()
            .appendQueryParameter("client_id", _clientId.value)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("scope", "playlist-read-private playlist-read-collaborative")
            .build().toString()
    }

    suspend fun handleAuthCode(code: String): Boolean = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _error.value = null
        try {
            val basicAuth = Base64.encodeToString(
                "${_clientId.value}:${_clientSecret.value}".toByteArray(),
                Base64.NO_WRAP
            )

            val body = FormBody.Builder()
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", REDIRECT_URI)
                .build()

            val request = Request.Builder()
                .url("https://accounts.spotify.com/api/token")
                .post(body)
                .addHeader("Authorization", "Basic $basicAuth")
                .build()

            client.newCall(request).execute().use { response ->
                val bodyStr = response.body?.string()
                if (response.isSuccessful && bodyStr != null) {
                    val json = JSONObject(bodyStr)
                    val accessToken = json.getString("access_token")
                    val refreshToken = json.optString("refresh_token", "")
                    val expiresIn = json.getLong("expires_in")
                    val expiresAt = System.currentTimeMillis() + (expiresIn * 1000)

                    prefs.edit()
                        .putString("access_token", accessToken)
                        .putString("refresh_token", refreshToken)
                        .putLong("expires_at", expiresAt)
                        .apply()

                    _isConnected.value = true
                    _isLoading.value = false
                    return@withContext true
                } else {
                    val errMsg = parseErrorMsg(bodyStr) ?: "Token exchange failed (${response.code})"
                    _error.value = errMsg
                    Log.e("SpotifyManager", "Auth exchange failed: $errMsg")
                }
            }
        } catch (e: Exception) {
            _error.value = e.message ?: "Network error during auth"
            Log.e("SpotifyManager", "Auth exchange exception", e)
        }
        _isLoading.value = false
        return@withContext false
    }

    private suspend fun getValidAccessToken(): String? = withContext(Dispatchers.IO) {
        val token = prefs.getString("access_token", null) ?: return@withContext null
        val expiresAt = prefs.getLong("expires_at", 0L)
        
        // If expired or expiring in 30 seconds, refresh it
        if (System.currentTimeMillis() + 30000 >= expiresAt) {
            val refreshToken = prefs.getString("refresh_token", null) ?: return@withContext token
            try {
                val basicAuth = Base64.encodeToString(
                    "${_clientId.value}:${_clientSecret.value}".toByteArray(),
                    Base64.NO_WRAP
                )

                val body = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .build()

                val request = Request.Builder()
                    .url("https://accounts.spotify.com/api/token")
                    .post(body)
                    .addHeader("Authorization", "Basic $basicAuth")
                    .build()

                client.newCall(request).execute().use { response ->
                    val bodyStr = response.body?.string()
                    if (response.isSuccessful && bodyStr != null) {
                        val json = JSONObject(bodyStr)
                        val newAccessToken = json.getString("access_token")
                        val newExpiresIn = json.getLong("expires_in")
                        val newExpiresAt = System.currentTimeMillis() + (newExpiresIn * 1000)
                        
                        // Some refresh responses might not include a new refresh token, keep old if missing
                        val newRefreshToken = json.optString("refresh_token", refreshToken)

                        prefs.edit()
                            .putString("access_token", newAccessToken)
                            .putString("refresh_token", newRefreshToken)
                            .putLong("expires_at", newExpiresAt)
                            .apply()

                        return@withContext newAccessToken
                    } else {
                        Log.e("SpotifyManager", "Token refresh failed")
                    }
                }
            } catch (e: Exception) {
                Log.e("SpotifyManager", "Token refresh exception", e)
            }
        }
        return@withContext token
    }

    suspend fun playRandomPlaylistSong(): Boolean = withContext(Dispatchers.IO) {
        _isLoading.value = true
        _error.value = null
        stopPreview()

        if (_isSimulated.value) {
            val simulatedSongs = listOf(
                SpotifyTrack(
                    id = "sim_1",
                    name = "Pingle Spin (Spitfire Synth)",
                    artist = "The Spinners",
                    albumArtUrl = "https://images.unsplash.com/photo-1614613535308-eb5fbd3d2c17?w=300&q=80",
                    previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-1.mp3",
                    externalUrl = "https://open.spotify.com"
                ),
                SpotifyTrack(
                    id = "sim_2",
                    name = "Retro Electro Spin",
                    artist = "Daft Pringle",
                    albumArtUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?w=300&q=80",
                    previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-2.mp3",
                    externalUrl = "https://open.spotify.com"
                ),
                SpotifyTrack(
                    id = "sim_3",
                    name = "Golden Pringles Echoes",
                    artist = "Crunch Master",
                    albumArtUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?w=300&q=80",
                    previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-3.mp3",
                    externalUrl = "https://open.spotify.com"
                ),
                SpotifyTrack(
                    id = "sim_4",
                    name = "Hyperdrive Rotation",
                    artist = "Velocity One",
                    albumArtUrl = "https://images.unsplash.com/photo-1470225620780-dba8ba36b745?w=300&q=80",
                    previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-4.mp3",
                    externalUrl = "https://open.spotify.com"
                ),
                SpotifyTrack(
                    id = "sim_5",
                    name = "Midnight Spin Ambient",
                    artist = "Cosmic Chill",
                    albumArtUrl = "https://images.unsplash.com/photo-1506157786151-b8491531f063?w=300&q=80",
                    previewUrl = "https://www.soundhelix.com/examples/mp3/SoundHelix-Song-5.mp3",
                    externalUrl = "https://open.spotify.com"
                )
            )

            val track = simulatedSongs.random()
            _currentTrack.value = track

            val previewUrl = track.previewUrl
            if (previewUrl != null) {
                try {
                    withContext(Dispatchers.Main) {
                        mediaPlayer = MediaPlayer().apply {
                            setAudioAttributes(
                                AudioAttributes.Builder()
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .build()
                            )
                            setDataSource(previewUrl)
                            prepareAsync()
                            setOnPreparedListener {
                                start()
                                _isPlaying.value = true
                            }
                            setOnCompletionListener {
                                _isPlaying.value = false
                                release()
                                mediaPlayer = null
                            }
                            setOnErrorListener { _, _, _ ->
                                _isPlaying.value = false
                                _error.value = "Failed to play audio preview"
                                false
                            }
                        }
                    }
                } catch (e: Exception) {
                    _error.value = "Error playing audio stream: ${e.message}"
                }
            } else {
                _error.value = "Preview unavailable."
            }

            _isLoading.value = false
            return@withContext true
        }

        val token = getValidAccessToken()
        if (token == null) {
            _error.value = "Not connected or token invalid. Please reconnect."
            _isConnected.value = false
            _isLoading.value = false
            return@withContext false
        }

        try {
            // Step 1: Fetch user's playlists
            val playlistsReq = Request.Builder()
                .url("https://api.spotify.com/v1/me/playlists?limit=50")
                .get()
                .addHeader("Authorization", "Bearer $token")
                .build()

            client.newCall(playlistsReq).execute().use { playlistsRes ->
                val bodyStr = playlistsRes.body?.string()
                if (!playlistsRes.isSuccessful || bodyStr == null) {
                    val errMsg = parseErrorMsg(bodyStr) ?: "Failed to fetch playlists (${playlistsRes.code})"
                    _error.value = errMsg
                    _isLoading.value = false
                    return@withContext false
                }

                val json = JSONObject(bodyStr)
                val items = json.getJSONArray("items")
                if (items.length() == 0) {
                    _error.value = "You don't have any playlists in your Spotify library!"
                    _isLoading.value = false
                    return@withContext false
                }

                // Step 2: Pick a random playlist
                val randomIndex = (0 until items.length()).random()
                val playlistObj = items.getJSONObject(randomIndex)
                val playlistId = playlistObj.getString("id")
                val playlistName = playlistObj.getString("name")

                // Step 3: Fetch tracks from that playlist
                val tracksReq = Request.Builder()
                    .url("https://api.spotify.com/v1/playlists/$playlistId/tracks?limit=100")
                    .get()
                    .addHeader("Authorization", "Bearer $token")
                    .build()

                client.newCall(tracksReq).execute().use { tracksRes ->
                    val tracksBodyStr = tracksRes.body?.string()
                    if (!tracksRes.isSuccessful || tracksBodyStr == null) {
                        _error.value = "Failed to fetch tracks from playlist '$playlistName'"
                        _isLoading.value = false
                        return@withContext false
                    }

                    val tracksJson = JSONObject(tracksBodyStr)
                    val tracksItems = tracksJson.getJSONArray("items")
                    if (tracksItems.length() == 0) {
                        _error.value = "Playlist '$playlistName' is empty!"
                        _isLoading.value = false
                        return@withContext false
                    }

                    // Find a track that is valid (not null, ideally has a preview URL, though we can play whatever has one or fall back)
                    val indices = (0 until tracksItems.length()).shuffled()
                    var selectedTrack: SpotifyTrack? = null

                    for (idx in indices) {
                        val trackItem = tracksItems.getJSONObject(idx)
                        val trackObj = trackItem.optJSONObject("track") ?: continue
                        val isLocal = trackObj.optBoolean("is_local", false)
                        if (isLocal) continue // skip local tracks as they don't have API streaming links

                        val id = trackObj.optString("id", "")
                        val name = trackObj.optString("name", "Unknown Track")
                        
                        val artistsArr = trackObj.optJSONArray("artists")
                        val artistName = if (artistsArr != null && artistsArr.length() > 0) {
                            artistsArr.getJSONObject(0).optString("name", "Unknown Artist")
                        } else {
                            "Unknown Artist"
                        }

                        val albumObj = trackObj.optJSONObject("album")
                        val albumArtUrl = if (albumObj != null) {
                            val imagesArr = albumObj.optJSONArray("images")
                            if (imagesArr != null && imagesArr.length() > 0) {
                                imagesArr.getJSONObject(0).optString("url", null)
                            } else null
                        } else null

                        val previewUrl = trackObj.optString("preview_url", null)
                        val extUrlObj = trackObj.optJSONObject("external_urls")
                        val externalUrl = extUrlObj?.optString("spotify", "https://open.spotify.com") ?: "https://open.spotify.com"

                        // Prioritize tracks with preview URLs
                        if (previewUrl != null && previewUrl.isNotEmpty() && previewUrl != "null") {
                            selectedTrack = SpotifyTrack(id, name, artistName, albumArtUrl, previewUrl, externalUrl)
                            break
                        } else {
                            // If none found with preview URL, keep a backup reference so we at least display the metadata
                            if (selectedTrack == null) {
                                selectedTrack = SpotifyTrack(id, name, artistName, albumArtUrl, null, externalUrl)
                            }
                        }
                    }

                    if (selectedTrack == null) {
                        _error.value = "Could not find any playable tracks in playlist '$playlistName'"
                        _isLoading.value = false
                        return@withContext false
                    }

                    _currentTrack.value = selectedTrack

                    // Step 4: Stream the preview URL if available
                    val previewUrl = selectedTrack.previewUrl
                    if (previewUrl != null && previewUrl.isNotEmpty() && previewUrl != "null") {
                        try {
                            withContext(Dispatchers.Main) {
                                mediaPlayer = MediaPlayer().apply {
                                    setAudioAttributes(
                                        AudioAttributes.Builder()
                                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                            .setUsage(AudioAttributes.USAGE_MEDIA)
                                            .build()
                                    )
                                    setDataSource(previewUrl)
                                    prepareAsync()
                                    setOnPreparedListener {
                                        start()
                                        _isPlaying.value = true
                                    }
                                    setOnCompletionListener {
                                        _isPlaying.value = false
                                        release()
                                        mediaPlayer = null
                                    }
                                    setOnErrorListener { _, _, _ ->
                                        _isPlaying.value = false
                                        _error.value = "Failed to play audio preview"
                                        false
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            _error.value = "Error playing audio stream: ${e.message}"
                        }
                    } else {
                        _error.value = "Song loaded, but Spotify doesn't provide a preview track for it. Tap 'Open in Spotify' to listen!"
                    }

                    _isLoading.value = false
                    return@withContext true
                }
            }
        } catch (e: Exception) {
            _error.value = "API Error: ${e.message}"
            Log.e("SpotifyManager", "playRandomPlaylistSong exception", e)
        }

        _isLoading.value = false
        return@withContext false
    }

    fun togglePlayPause() {
        val mp = mediaPlayer ?: return
        if (mp.isPlaying) {
            mp.pause()
            _isPlaying.value = false
        } else {
            mp.start()
            _isPlaying.value = true
        }
    }

    fun stopPreview() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) {
                    it.stop()
                }
                it.release()
            }
        } catch (e: Exception) {
            Log.e("SpotifyManager", "Error stopping player", e)
        } finally {
            mediaPlayer = null
            _isPlaying.value = false
        }
    }

    private fun parseErrorMsg(body: String?): String? {
        if (body == null) return null
        try {
            val json = JSONObject(body)
            if (json.has("error")) {
                val errorVal = json.get("error")
                if (errorVal is JSONObject) {
                    return errorVal.optString("message", null)
                } else if (errorVal is String) {
                    return json.optString("error_description", errorVal)
                }
            }
        } catch (e: Exception) {
            // ignore parsing failure
        }
        return null
    }
}
