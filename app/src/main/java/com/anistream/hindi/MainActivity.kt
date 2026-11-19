
package com.anistream.hindi

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Background = Color(0xFF08090D)
private val SurfaceDark = Color(0xFF14151C)
private val AccentRed = Color(0xFFFF1744)
private val White = Color(0xFFFFFFFF)
private val Gray = Color(0xFF999BA5)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            AniStreamApp()
        }
    }
}

@Composable
fun AniStreamApp() {
    var selectedTab by remember { mutableStateOf(0) }

    MaterialTheme {
        Scaffold(
            containerColor = Background,
            bottomBar = {
                NavigationBar(
                    containerColor = SurfaceDark,
                    contentColor = White
                ) {
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
        ) { padding ->

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Background)
                    .padding(padding)
                    .padding(horizontal = 16.dp)
            ) {

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 18.dp, bottom = 20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Ani", color = White, fontSize = 27.sp,
                        fontWeight = FontWeight.ExtraBold)
                    Text("Stream", color = AccentRed, fontSize = 27.sp,
                        fontWeight = FontWeight.ExtraBold)

                    Spacer(Modifier.weight(1f))

                    Text("🔔", fontSize = 23.sp)
                }

                when (selectedTab) {
                    0 -> HomeScreen()
                    1 -> SimpleScreen("Search Anime", "Find your favourite anime")
                    2 -> SimpleScreen("My List", "Your favourite anime will appear here")
                    3 -> SimpleScreen("My Profile", "Login to access your profile")
                }
            }
        }
    }
}

@Composable
fun HomeScreen() {
    Column(
        modifier = Modifier.fillMaxSize()
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
                .background(
                    Color(0xFF30101B),
                    RoundedCornerShape(18.dp)
                )
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

        Spacer(Modifier.height(25.dp))

        AnimeSection("🔥 Trending Anime")
        AnimeSection("✨ Recently Added")
        AnimeSection("🎬 Anime Movies")
    }
}

@Composable
fun AnimeSection(title: String) {
    Column {
        Text(
            title,
            color = White,
            fontSize = 19.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(12.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(3) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(110.dp)
                        .background(
                            SurfaceDark,
                            RoundedCornerShape(12.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ANIME", color = Gray, fontSize = 12.sp)
                }
            }
        }

        Spacer(Modifier.height(22.dp))
    }
}

@Composable
fun SimpleScreen(title: String, subtitle: String) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(title, color = White, fontSize = 25.sp,
            fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(subtitle, color = Gray, fontSize = 14.sp)
    }
}
