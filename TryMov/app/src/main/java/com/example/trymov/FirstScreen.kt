package com.example.trymov

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.example.trymov.omdb.OmdbClient
import com.example.trymov.fastapi.Recommendation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirstScreen(vm: MovieViewModel) {
    val status by vm.status.collectAsState()
    val recommendations by vm.recommendations.collectAsState()
    var query by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        TopBrandSection()

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Discover your next movie",
                color = TryMovUiColors.TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 36.sp
            )

            Text(
                text = "Search a title, explore recommendations, and find something worth watching tonight.",
                color = TryMovUiColors.TextMuted,
                fontSize = 14.sp,
                lineHeight = 22.sp
            )
        }

        SearchCard(
            query = query,
            onQueryChange = { query = it },
            onSearch = { vm.getRecommendations(query) }
        )

        ResultsSection(
            query = query,
            status = status,
            recommendations = recommendations
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchCard(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = TryMovUiColors.Surface,
                shape = RoundedCornerShape(24.dp)
            )
            .border(
                width = 1.dp,
                color = TryMovUiColors.Border,
                shape = RoundedCornerShape(24.dp)
            )
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Search movies",
            color = TryMovUiColors.TextPrimary,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold
        )

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            leadingIcon = {
                Icon(
                    painter = painterResource(id = R.drawable.search),
                    contentDescription = "Search",
                    tint = TryMovUiColors.TextMuted
                )
            },
            placeholder = {
                Text(
                    text = "Try Interstellar, Inception, Parasite...",
                    color = TryMovUiColors.TextMuted
                )
            },
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

        Button(
            onClick = onSearch,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = TryMovUiColors.Gold,
                contentColor = Color(0xFF1A0E00)
            )
        ) {
            Icon(
                painter = painterResource(id = R.drawable.search),
                contentDescription = null
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Find Recommendations",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ResultsSection(
    query: String,
    status: String,
    recommendations: List<Recommendation>
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Recommendations",
                color = TryMovUiColors.TextPrimary,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (query.isNotBlank()) {
                AssistChip(
                    onClick = { },
                    label = {
                        Text(
                            text = "For: $query",
                            color = TryMovUiColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = TryMovUiColors.Surface
                    ),
                    border = BorderStroke(1.dp, TryMovUiColors.Border)
                )
            }
        }

        Text(
            text = statusLine(status, recommendations),
            color = TryMovUiColors.TextMuted,
            style = MaterialTheme.typography.bodySmall
        )

        when {
            isLoadingStatus(status) -> ResultsSkeleton()

            recommendations.isEmpty() -> EmptyStateCard(
                title = "No results yet",
                subtitle = "Type a movie title above and tap \"Find Recommendations\"."
            )

            else -> Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                recommendations.forEach { rec ->
                    RecommendationCard(rec = rec)
                }
            }
        }
    }
}

private fun isLoadingStatus(status: String): Boolean {
    return status.contains("loading", ignoreCase = true) ||
            status.contains("fetch", ignoreCase = true)
}

private fun statusLine(status: String, recs: List<Recommendation>): String {
    return when {
        status.isBlank() && recs.isNotEmpty() -> "${recs.size} recommendations found"
        status.isBlank() -> "Ready"
        else -> status
    }
}

@Composable
private fun RecommendationCard(rec: Recommendation) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TryMovUiColors.Surface),
        border = BorderStroke(1.dp, TryMovUiColors.Border)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            PosterThumbByTitle(
                title = rec.title,
                modifier = Modifier
                    .width(100.dp)
                    .height(150.dp)
                    .clip(RoundedCornerShape(16.dp))
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = rec.title,
                    color = TryMovUiColors.TextPrimary,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )

                ScoreBadge(score = rec.score)
            }
        }
    }
}

@Composable
private fun ScoreBadge(score: Double) {
    val label = String.format("Score: %.2f", score)

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = TryMovUiColors.Field,
        contentColor = TryMovUiColors.TextPrimary,
        border = BorderStroke(1.dp, TryMovUiColors.Border)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun PosterThumbByTitle(
    title: String,
    modifier: Modifier = Modifier
) {
    var posterUrl by remember(title) { mutableStateOf<String?>(null) }

    LaunchedEffect(title) {
        posterUrl = try {
            withContext(Dispatchers.IO) { posterUrlFromTitle(title) }
        } catch (_: Exception) {
            null
        }
    }

    if (posterUrl.isNullOrBlank()) {
        Spacer(
            modifier = modifier.background(Color(0xFF1B1B1B))
        )
    } else {
        AsyncImage(
            model = posterUrl,
            contentDescription = "$title poster",
            modifier = modifier
        )
    }
}

@Composable
private fun TopBrandSection() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier
                .background(
                    color = TryMovUiColors.Surface,
                    shape = RoundedCornerShape(100.dp)
                )
                .border(
                    width = 1.dp,
                    color = TryMovUiColors.Border,
                    shape = RoundedCornerShape(100.dp)
                )
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                painter = painterResource(id = R.drawable.outline_add_diamond_24),
                contentDescription = "Brand",
                tint = TryMovUiColors.Gold
            )

            Text(
                text = "TRYMOV",
                color = TryMovUiColors.TextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )
        }

        Text(
            text = "INTELLIGENT DISCOVERY",
            color = TryMovUiColors.Gold,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.1.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ResultsSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        repeat(3) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = TryMovUiColors.Surface),
                border = BorderStroke(1.dp, TryMovUiColors.Border)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(
                        modifier = Modifier
                            .width(100.dp)
                            .height(150.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color(0xFF1B1B1B))
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Spacer(
                            modifier = Modifier
                                .width(180.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1B1B1B))
                        )
                        Spacer(
                            modifier = Modifier
                                .width(100.dp)
                                .height(18.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1B1B1B))
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyStateCard(title: String, subtitle: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = TryMovUiColors.Surface),
        border = BorderStroke(1.dp, TryMovUiColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = TryMovUiColors.TextPrimary,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = subtitle,
                color = TryMovUiColors.TextMuted,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

suspend fun posterUrlFromTitle(title: String): String? {
    val res = OmdbClient.api.getByTitle(
        apiKey = "YOUR_API_KEY",
        title = title
    )

    return if (res.Response == "True" && !res.Poster.isNullOrBlank() && res.Poster != "N/A") {
        res.Poster
    } else null
}