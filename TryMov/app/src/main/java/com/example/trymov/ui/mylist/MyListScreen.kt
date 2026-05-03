package com.example.trymov.ui.mylist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.trymov.TryMovUiColors
import com.example.trymov.model.MyListEntry
import com.example.trymov.model.WatchStatus
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MyListScreen(vm: MyListViewModel) {
    val uiState by vm.uiState.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var showAddDialog by remember { mutableStateOf(false) }
    var imdbInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        vm.eventMessages.collect { msg ->
            if (msg == "Added") {
                imdbInput = ""
                showAddDialog = false
            }
            scope.launch { snackbarHostState.showSnackbar(msg) }
        }
    }

    Scaffold(
        containerColor = TryMovUiColors.Background,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "My List",
                        color = TryMovUiColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = TryMovUiColors.Background,
                    actionIconContentColor = TryMovUiColors.TextMuted
                ),
                modifier = Modifier.statusBarsPadding(),
                actions = {
                    IconButton(
                        onClick = { vm.syncAll() },
                        enabled = !uiState.isSyncing
                    ) {
                        if (uiState.isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = TryMovUiColors.TextMuted
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sync from cloud",
                                tint = TryMovUiColors.TextMuted
                            )
                        }
                    }

                    IconButton(onClick = {
                        vm.setListMode(
                            if (uiState.listMode == ListMode.LIST) ListMode.GRID else ListMode.LIST
                        )
                    }) {
                        Icon(
                            imageVector = if (uiState.listMode == ListMode.LIST) Icons.Default.ViewModule else Icons.Default.ViewList,
                            contentDescription = "Toggle view",
                            tint = TryMovUiColors.TextMuted
                        )
                    }

                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add movie",
                            tint = TryMovUiColors.Gold
                        )
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(TryMovUiColors.Background)
                .padding(padding)
        ) {
            when {
                uiState.entries.isEmpty() -> ListEmptyState(
                    onAddClick = { showAddDialog = true }
                )

                uiState.listMode == ListMode.LIST -> MyListListView(
                    entries = uiState.entries,
                    onToggleFavorite = vm::toggleFavorite,
                    onDelete = vm::removeEntry,
                    onUpdateRating = vm::updateRating,
                    onUpdateEntry = vm::updateEntry
                )

                else -> MyListGridView(
                    entries = uiState.entries,
                    onToggleFavorite = vm::toggleFavorite,
                    onDelete = vm::removeEntry
                )
            }
        }
    }

    if (showAddDialog) {
        AddByImdbDialog(
            imdbId = imdbInput,
            isLoading = uiState.isAdding,
            error = uiState.addError,
            onImdbIdChange = { imdbInput = it },
            onConfirm = { vm.addByImdbId(imdbInput) },
            onDismiss = { if (!uiState.isAdding) showAddDialog = false }
        )
    }
}

@Composable
private fun ListEmptyState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = TryMovUiColors.Surface),
            border = BorderStroke(1.dp, TryMovUiColors.Border)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Your list is empty",
                    color = TryMovUiColors.TextPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Add movies by IMDB ID (e.g. tt1375666) to track what you watch.",
                    color = TryMovUiColors.TextMuted,
                    fontSize = 14.sp,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { onAddClick() },
                    color = TryMovUiColors.Gold,
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text(
                        text = "Add your first movie",
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                        color = Color(0xFF1A0E00),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun MyListListView(
    entries: List<MyListEntry>,
    onToggleFavorite: (Int) -> Unit,
    onDelete: (Int) -> Unit,
    onUpdateRating: (Int, Int) -> Unit,
    onUpdateEntry: (MyListEntry) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(items = entries, key = { it.id }) { entry ->
            MyListEntryCard(
                entry = entry,
                onToggleFavorite = { onToggleFavorite(entry.id) },
                onDelete = { onDelete(entry.id) },
                onUpdateRating = { rating -> onUpdateRating(entry.id, rating) },
                onUpdateStatus = { status -> onUpdateEntry(entry.copy(status = status)) }
            )
        }
    }
}

@Composable
private fun MyListGridView(
    entries: List<MyListEntry>,
    onToggleFavorite: (Int) -> Unit,
    onDelete: (Int) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items = entries, key = { it.id }) { entry ->
            GridEntryCard(
                entry = entry,
                onToggleFavorite = { onToggleFavorite(entry.id) },
                onDelete = { onDelete(entry.id) }
            )
        }
    }
}

@Composable
private fun GridEntryCard(
    entry: MyListEntry,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = TryMovUiColors.Surface),
        border = BorderStroke(1.dp, TryMovUiColors.Border)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            val poster = entry.movie?.poster
            if (poster != null) {
                AsyncImage(
                    model = poster,
                    contentDescription = entry.movie?.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TryMovUiColors.Field)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(TryMovUiColors.Field),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = TryMovUiColors.Gold
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = entry.movie?.title ?: entry.imdbId,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = TryMovUiColors.TextPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onToggleFavorite, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Favorite",
                        tint = if (entry.isFavorite) TryMovUiColors.Gold else TryMovUiColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = TryMovUiColors.TextMuted,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MyListEntryCard(
    entry: MyListEntry,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    onUpdateRating: (Int) -> Unit,
    onUpdateStatus: (WatchStatus) -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TryMovUiColors.Surface),
        border = BorderStroke(1.dp, TryMovUiColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PosterThumb(entry = entry)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.movie?.title ?: entry.imdbId,
                    color = TryMovUiColors.TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.imdbId,
                    color = TryMovUiColors.TextMuted,
                    fontSize = 12.sp
                )

                if (entry.movie?.genres?.isNotEmpty() == true) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.movie.genres.take(3).joinToString(" · "),
                        color = TryMovUiColors.Gold,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (entry.movie == null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = TryMovUiColors.Gold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fetching from TMDB…",
                            color = TryMovUiColors.TextMuted,
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    StatusPicker(
                        status = entry.status,
                        onStatusSelected = onUpdateStatus
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = if (entry.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "Favorite",
                                tint = if (entry.isFavorite) TryMovUiColors.Gold else TryMovUiColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "Delete",
                                tint = TryMovUiColors.TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                var sliderRating by remember(entry.id) { mutableStateOf(entry.rating.toFloat()) }
                Text(
                    text = "Rating  ${sliderRating.toInt()}/10",
                    color = TryMovUiColors.TextMuted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
                Slider(
                    value = sliderRating,
                    onValueChange = { sliderRating = it },
                    onValueChangeFinished = { onUpdateRating(sliderRating.toInt()) },
                    valueRange = 0f..10f,
                    steps = 9,
                    colors = SliderDefaults.colors(
                        thumbColor = TryMovUiColors.Gold,
                        activeTrackColor = TryMovUiColors.Gold,
                        inactiveTrackColor = TryMovUiColors.Border
                    )
                )
            }
        }
    }
}

@Composable
private fun PosterThumb(entry: MyListEntry) {
    val poster = entry.movie?.poster
    val shape = RoundedCornerShape(14.dp)

    if (poster != null) {
        AsyncImage(
            model = poster,
            contentDescription = entry.movie?.title,
            modifier = Modifier
                .width(90.dp)
                .height(134.dp)
                .clip(shape)
                .background(TryMovUiColors.Field)
        )
    } else {
        Box(
            modifier = Modifier
                .width(90.dp)
                .height(134.dp)
                .clip(shape)
                .background(TryMovUiColors.Field),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "🎬", fontSize = 24.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusPicker(
    status: WatchStatus,
    onStatusSelected: (WatchStatus) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded }
    ) {
        Box(
            modifier = Modifier
                .menuAnchor()
                .clip(RoundedCornerShape(999.dp))
                .background(statusColor(status).copy(alpha = 0.15f))
                .border(1.dp, statusColor(status).copy(alpha = 0.4f), RoundedCornerShape(999.dp))
                .clickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = status.displayName(),
                color = statusColor(status),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
        }

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            containerColor = TryMovUiColors.Surface
        ) {
            WatchStatus.entries.forEach { s ->
                DropdownMenuItem(
                    text = {
                        Text(
                            text = s.displayName(),
                            color = statusColor(s),
                            fontWeight = FontWeight.Medium
                        )
                    },
                    onClick = {
                        expanded = false
                        onStatusSelected(s)
                    }
                )
            }
        }
    }
}

private fun statusColor(status: WatchStatus): Color = when (status) {
    WatchStatus.WATCHING -> Color(0xFFE8B84A)
    WatchStatus.COMPLETED -> Color(0xFF4CAF6E)
    WatchStatus.DROPPED -> Color(0xFFFF6B6B)
    WatchStatus.PLANNED -> Color(0xFF5C9BFF)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddByImdbDialog(
    imdbId: String,
    isLoading: Boolean,
    error: String?,
    onImdbIdChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = TryMovUiColors.Surface,
        title = {
            Text(
                text = "Add movie",
                color = TryMovUiColors.TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Enter the IMDB ID of the movie to fetch its data from TMDB.",
                    color = TryMovUiColors.TextMuted,
                    fontSize = 13.sp
                )
                OutlinedTextField(
                    value = imdbId,
                    onValueChange = onImdbIdChange,
                    label = { Text("IMDB ID", color = TryMovUiColors.TextMuted) },
                    placeholder = { Text("tt1375666", color = TryMovUiColors.TextMuted) },
                    singleLine = true,
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = TryMovUiColors.Field,
                        unfocusedContainerColor = TryMovUiColors.Field,
                        focusedBorderColor = TryMovUiColors.Gold,
                        unfocusedBorderColor = TryMovUiColors.Border,
                        focusedTextColor = TryMovUiColors.TextPrimary,
                        unfocusedTextColor = TryMovUiColors.TextPrimary,
                        cursorColor = TryMovUiColors.Gold
                    )
                )
                if (error != null) {
                    Text(
                        text = error,
                        color = TryMovUiColors.Error,
                        fontSize = 12.sp
                    )
                }
                if (isLoading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = TryMovUiColors.Gold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Fetching from TMDB…",
                            color = TryMovUiColors.TextMuted,
                            fontSize = 13.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !isLoading
            ) {
                Text(
                    text = "Fetch & Add",
                    color = if (isLoading) TryMovUiColors.TextMuted else TryMovUiColors.Gold,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(text = "Cancel", color = TryMovUiColors.TextMuted)
            }
        }
    )
}
