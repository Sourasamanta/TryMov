package com.example.trymov

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.trymov.auth.LoginActivity
import com.example.trymov.auth.TokenManager
import com.example.trymov.ui.discover.MovieViewModelFactory
import com.example.trymov.ui.mylist.MyListScreen
import com.example.trymov.ui.mylist.MyListViewModel
import com.example.trymov.ui.mylist.MyListViewModelFactory
import com.example.trymov.ui.theme.TryMovTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Auth gate — redirect to login if no token present
        if (!TokenManager.isLoggedIn()) {
            startActivity(
                Intent(this, LoginActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                }
            )
            finish()
            return
        }

        val app = application as TryMovApplication

        setContent {
            TryMovTheme {
                val myListVm: MyListViewModel = viewModel(
                    factory = MyListViewModelFactory(
                        app.container.movieRepository,
                        app.container.interactionRepository
                    )
                )
                val discoverVm: MovieViewModel = viewModel(
                    factory = MovieViewModelFactory(app.container.fastApiRepository)
                )

                TryMovApp(
                    discoverVm = discoverVm,
                    myListVm = myListVm
                )
            }
        }
    }
}

private data class NavTab(
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
)

private val tabs = listOf(
    NavTab("Discover", Icons.Default.Search, Icons.Default.Search),
    NavTab("My List", Icons.Default.Bookmark, Icons.Default.BookmarkBorder)
)

@Composable
fun TryMovApp(
    discoverVm: MovieViewModel,
    myListVm: MyListViewModel
) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        containerColor = TryMovUiColors.Background,
        bottomBar = {
            TryMovNavBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TryMovUiColors.Background)
                .padding(innerPadding)
        ) {
            AnimatedVisibility(
                visible = selectedTab == 0,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                FirstScreen(vm = discoverVm)
            }

            AnimatedVisibility(
                visible = selectedTab == 1,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                MyListScreen(vm = myListVm)
            }
        }
    }
}

@Composable
private fun TryMovNavBar(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(TryMovUiColors.Background)
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        NavigationBar(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, TryMovUiColors.Border, RoundedCornerShape(20.dp)),
            containerColor = TryMovUiColors.Surface,
            tonalElevation = 0.dp
        ) {
            tabs.forEachIndexed { index, tab ->
                val selected = selectedTab == index
                NavigationBarItem(
                    selected = selected,
                    onClick = { onTabSelected(index) },
                    icon = {
                        Icon(
                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                            contentDescription = tab.label,
                            tint = if (selected) TryMovUiColors.Gold else TryMovUiColors.TextMuted
                        )
                    },
                    label = {
                        Text(
                            text = tab.label,
                            fontSize = 11.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (selected) TryMovUiColors.Gold else TryMovUiColors.TextMuted
                        )
                    },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = TryMovUiColors.Gold,
                        unselectedIconColor = TryMovUiColors.TextMuted,
                        indicatorColor = TryMovUiColors.Field
                    )
                )
            }
        }
    }
}
