/*
 * Copyright (C) 2026 Geliştirici
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package com.denile.musiclink

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.edit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        var incomingUrl: String? = null
        
        if (intent?.action == Intent.ACTION_VIEW) {
            incomingUrl = intent.dataString
        } else if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""
            val urlRegex = "(?i)\\b((?:https?://|www\\d{0,3}[.]|[a-z0-9.\\-]+[.][a-z]{2,4}/)(?:[^\\s()<>]+|\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\))+(?:\\(([^\\s()<>]+|(\\([^\\s()<>]+\\)))*\\)|[^\\s`!()\\[\\]{};:'\".,<>?«»“”‘’]))".toRegex()
            val match = urlRegex.find(sharedText)
            incomingUrl = match?.value
        }

        setContent {
            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Color(0xFF6200EE),
                    secondary = Color(0xFF03DAC5),
                    background = Color(0xFFF5F5F5),
                    surface = Color.White
                )
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MusicLinkApp(incomingUrl) { platformId ->
                        if(platformId == "FINISH_ACTIVITY") {
                            finish()
                        }
                    }
                }
            }
        }
    }
}

data class PlatformLink(val id: String, val name: String, val url: String)

@Composable
fun MusicLinkApp(incomingUrl: String?, onFinish: (String) -> Unit) {
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("MusicLinkPrefs", Context.MODE_PRIVATE) }
    
    // First Launch onboarding flag
    var isFirstLaunch by remember {
        mutableStateOf(sharedPrefs.getBoolean("IS_FIRST_LAUNCH", true))
    }
    
    var defaultPlatformId by remember { 
        mutableStateOf(sharedPrefs.getString("DEFAULT_PLATFORM_ID", null)) 
    }
    var defaultPlatformName by remember { 
        mutableStateOf(sharedPrefs.getString("DEFAULT_PLATFORM_NAME", null)) 
    }

    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var platformLinks by remember { mutableStateOf<List<PlatformLink>>(emptyList()) }
    var launchedDirectly by remember { mutableStateOf(false) }

    LaunchedEffect(incomingUrl) {
        if (incomingUrl != null) {
            // If user comes from a link directly, immediately skip onboarding
            // so we don't interrupt the urgent link opening action
            if (isFirstLaunch) {
                isFirstLaunch = false
                sharedPrefs.edit { putBoolean("IS_FIRST_LAUNCH", false) }
            }
            
            isLoading = true
            try {
                val links = fetchLinksFromOdesli(context, incomingUrl)
                
                // Direct launch if default exists
                if (defaultPlatformId != null) {
                    val defaultLink = links.find { it.id == defaultPlatformId }
                    if (defaultLink != null) {
                        launchUrl(context, defaultLink.url)
                        onFinish("FINISH_ACTIVITY")
                        return@LaunchedEffect
                    }
                }
                
                platformLinks = links
            } catch (e: Exception) {
                errorMessage = e.message ?: context.getString(R.string.error_fetching_links)
                Log.e("MusicLink", "Error fetching links", e)
            } finally {
                isLoading = false
            }
        } else {
            launchedDirectly = true
        }
    }

    // Routing Logic
    if (launchedDirectly) {
        if (isFirstLaunch) {
            // First time opening the app from launcher -> Show Onboarding
            OnboardingScreen(onComplete = {
                isFirstLaunch = false
                sharedPrefs.edit { putBoolean("IS_FIRST_LAUNCH", false) }
            })
        } else {
            // Not first time -> Show Settings Screen
            SettingsScreen(
                defaultPlatformName = defaultPlatformName,
                onClearDefault = {
                    sharedPrefs.edit {
                        remove("DEFAULT_PLATFORM_ID")
                        remove("DEFAULT_PLATFORM_NAME")
                    }
                    defaultPlatformId = null
                    defaultPlatformName = null
                }
            )
        }
    } else {
        // App started from an Intent URL -> Show Link Redirections List
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(64.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    stringResource(R.string.searching_links),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            } else if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Error", tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.error_prefix, errorMessage ?: ""), 
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            } else if (platformLinks.isNotEmpty()) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(500)) + slideInVertically(animationSpec = tween(500)) { 40 }
                ) {
                    Column {
                        Text(
                            text = stringResource(R.string.choose_platform),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                        
                        Text(
                            text = stringResource(R.string.choose_platform_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                            modifier = Modifier.padding(bottom = 24.dp)
                        )

                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(platformLinks) { link ->
                                PlatformCard(
                                    link = link,
                                    onOpenClick = {
                                        launchUrl(context, link.url)
                                        // Once launched, we can consider our job done.
                                        onFinish("FINISH_ACTIVITY")
                                    },
                                    onSetDefaultClick = {
                                        sharedPrefs.edit {
                                            putString("DEFAULT_PLATFORM_ID", link.id)
                                            putString("DEFAULT_PLATFORM_NAME", link.name)
                                        }
                                        launchUrl(context, link.url)
                                        onFinish("FINISH_ACTIVITY")
                                    }
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    stringResource(R.string.no_links_found),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    var currentPage by remember { mutableStateOf(0) }
    
    val pages = listOf(
        Triple(
            Icons.Default.Share,
            stringResource(R.string.onboarding_title_1),
            stringResource(R.string.onboarding_desc_1)
        ),
        Triple(
            Icons.Default.Refresh,
            stringResource(R.string.onboarding_title_2),
            stringResource(R.string.onboarding_desc_2)
        ),
        Triple(
            Icons.Default.Star,
            stringResource(R.string.onboarding_title_3),
            stringResource(R.string.onboarding_desc_3)
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AnimatedContent(targetState = currentPage, label = "OnboardingPages") { pageIndex ->
            val page = pages[pageIndex]
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(120.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = page.first,
                            contentDescription = null,
                            modifier = Modifier.size(60.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                Spacer(modifier = Modifier.height(32.dp))
                
                Text(
                    text = page.second,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = page.third,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Page indicators
        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            repeat(pages.size) { index ->
                val color = if (index == currentPage) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant
                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(10.dp)
                        .clip(CircleShape)

                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))

        // Navigation Buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            if (currentPage > 0) {
                TextButton(onClick = { currentPage-- }) {
                    Text(stringResource(R.string.back))
                }
            } else {
                Spacer(modifier = Modifier.width(64.dp))
            }

            if (currentPage < pages.size - 1) {
                Button(onClick = { currentPage++ }) {
                    Text(stringResource(R.string.next))
                }
            } else {
                Button(onClick = onComplete) {
                    Text(stringResource(R.string.start))
                }
            }
        }
    }
}

@Composable
fun SettingsScreen(defaultPlatformName: String?, onClearDefault: () -> Unit) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = "Settings",
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(24.dp))
        
        Text(
            text = stringResource(R.string.settings_title), 
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(32.dp))

        if (defaultPlatformName != null) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.current_default),
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = defaultPlatformName,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onClearDefault,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(stringResource(R.string.clear_default))
                    }
                }
            }
        } else {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.no_default_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.no_default_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            OutlinedButton(
                onClick = {
                    try {
                        val intent = Intent(Settings.ACTION_APP_OPEN_BY_DEFAULT_SETTINGS).apply {
                            data = Uri.parse("package:${context.packageName}")
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    } catch (e: Exception) {
                        Log.e("MusicLink", "Cannot open defaults settings", e)
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Build, contentDescription = "Settings", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.open_android_settings))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                stringResource(R.string.open_android_settings_desc),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
fun PlatformCard(link: PlatformLink, onOpenClick: () -> Unit, onSetDefaultClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onOpenClick() },
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = link.name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.tap_to_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                FilledTonalButton(onClick = onSetDefaultClick) {
                    Icon(
                        Icons.Default.Star, 
                        contentDescription = "Set Default",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.default_button))
                }
            }
        }
    }
}

private fun launchUrl(context: Context, url: String) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.e("MusicLink", "Error launching intent: $url", e)
    }
}

suspend fun fetchLinksFromOdesli(context: Context, incomingUrl: String): List<PlatformLink> = withContext(Dispatchers.IO) {
    val client = OkHttpClient()
    val encodedUrl = URLEncoder.encode(incomingUrl, "UTF-8")
    val apiUrl = "https://api.song.link/v1-alpha.1/links?url=$encodedUrl"

    val request = Request.Builder()
        .url(apiUrl)
        .build()

    val response = client.newCall(request).execute()
    if (!response.isSuccessful) {
        throw Exception(context.getString(R.string.error_api_failed, response.code))
    }

    val responseBody = response.body?.string() ?: throw Exception(context.getString(R.string.empty_response_body))
    val jsonObject = JSONObject(responseBody)
    val linksByPlatform = jsonObject.getJSONObject("linksByPlatform")

    val result = mutableListOf<PlatformLink>()
    
    val platformNames = mapOf(
        "youtubeMusic" to "YouTube Music",
        "youtube" to "YouTube",
        "appleMusic" to "Apple Music",
        "spotify" to "Spotify",
        "itunes" to "iTunes",
        "deezer" to "Deezer",
        "tidal" to "Tidal",
        "amazonMusic" to "Amazon Music",
        "soundcloud" to "SoundCloud",
        "yandex" to "Yandex Music"
    )

    platformNames.forEach { (id, name) ->
        if (linksByPlatform.has(id)) {
            val platformInfo = linksByPlatform.getJSONObject(id)
            if (platformInfo.has("url")) {
                result.add(PlatformLink(id, name, platformInfo.getString("url")))
            }
        }
    }

    if (result.none { it.id == "youtubeMusic" }) {
        try {
            val entities = jsonObject.getJSONObject("entitiesByUniqueId")
            val keys = entities.keys()
            var title: String? = null
            var artist: String? = null
            
            if (keys.hasNext()) {
                val firstKey = keys.next()
                val entity = entities.getJSONObject(firstKey)
                if (entity.has("title") && entity.has("artistName")) {
                    title = entity.getString("title")
                    artist = entity.getString("artistName")
                }
            }
            
            if (title != null && artist != null) {
                val query = URLEncoder.encode("$title $artist", "UTF-8")
                val searchUrl = "https://music.youtube.com/search?q=$query"
                result.add(PlatformLink("youtubeMusic", context.getString(R.string.youtube_music_search), searchUrl))
            }
        } catch (e: Exception) {
            Log.e("MusicLink", "Failed to build fallback YouTube Music link", e)
        }
    }
    
    result.sortBy { 
        when(it.id) {
            "youtubeMusic" -> 1
            "spotify" -> 2
            "appleMusic" -> 3
            "youtube" -> 4
            else -> 5
        }
    }

    return@withContext result
}
