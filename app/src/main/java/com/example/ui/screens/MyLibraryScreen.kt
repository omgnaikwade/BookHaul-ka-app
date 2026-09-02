package com.example.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.api.SupabaseConfig
import com.example.data.model.BookDto
import com.example.data.model.ReadingProgressDto
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.LoadingView
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.viewmodel.LibraryUiState

@Composable
fun MyLibraryScreen(
    state: LibraryUiState,
    onTabSelected: (Int) -> Unit,
    onBookClick: (BookDto) -> Unit,
    onResumeReading: (BookDto, Int) -> Unit,
    onMarkCompleted: (Long) -> Unit,
    onRemoveFromLibrary: (Long) -> Unit,
    onExploreBooks: () -> Unit,
    onRetry: () -> Unit
) {
    val tabs = listOf("Reading", "Completed", "All")

    val filteredList = when (state.selectedTab) {
        0 -> state.readingProgressList.filter { it.progress in 1..99 }
        1 -> state.readingProgressList.filter { it.progress >= 100 }
        else -> state.readingProgressList
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Header
        Text(
            text = "My Library",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                fontSize = 22.sp
            ),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
        )

        // Tab Row
        TabRow(
            selectedTabIndex = state.selectedTab,
            containerColor = DarkBackground,
            contentColor = VioletPrimary,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[state.selectedTab]),
                    color = VioletPrimary
                )
            },
            divider = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(DarkOutline)
                )
            }
        ) {
            tabs.forEachIndexed { index, tabTitle ->
                val count = when (index) {
                    0 -> state.readingProgressList.count { it.progress in 1..99 }
                    1 -> state.readingProgressList.count { it.progress >= 100 }
                    else -> state.readingProgressList.size
                }
                Tab(
                    selected = state.selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = {
                        Text(
                            text = "$tabTitle ($count)",
                            fontWeight = if (state.selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                            color = if (state.selectedTab == index) VioletPrimary else TextSecondary,
                            fontSize = 14.sp
                        )
                    }
                )
            }
        }

        if (!state.errorMessage.isNullOrBlank()) {
            ErrorBanner(message = state.errorMessage, onRetry = onRetry)
        }

        if (state.isLoading) {
            LoadingView(message = "Loading your library from Supabase...")
        } else if (filteredList.isEmpty()) {
            val emptyTitle = when (state.selectedTab) {
                0 -> "No books in progress"
                1 -> "No completed books yet"
                else -> "Your library is empty"
            }
            val emptyMsg = when (state.selectedTab) {
                0 -> "Start reading any book from the catalog and your progress will appear here."
                1 -> "Finish reading books to add them to your completed bookshelf."
                else -> "Explore the library catalog to start reading and saving books."
            }

            EmptyState(
                title = emptyTitle,
                message = emptyMsg,
                icon = Icons.Default.LocalLibrary,
                actionButtonText = "Explore Books",
                onActionClick = onExploreBooks
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredList, key = { it.bookId }) { progressItem ->
                    val book = progressItem.book
                    if (book != null) {
                        LibraryBookItem(
                            book = book,
                            progress = progressItem.progress,
                            onClick = { onBookClick(book) },
                            onResume = { onResumeReading(book, progressItem.progress) },
                            onMarkCompleted = { onMarkCompleted(book.id) },
                            onRemove = { onRemoveFromLibrary(book.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryBookItem(
    book: BookDto,
    progress: Int,
    onClick: () -> Unit,
    onResume: () -> Unit,
    onMarkCompleted: () -> Unit,
    onRemove: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val coverUrl = SupabaseConfig.getCoverUrl(book.coverPath)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = DarkSurfaceContainer,
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkOutline.copy(alpha = 0.7f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("library_book_${book.id}")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Book cover
            Box(
                modifier = Modifier
                    .width(70.dp)
                    .height(100.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(DarkSurfaceVariant)
                    .border(1.dp, DarkOutline, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                if (!coverUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(coverUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(listOf(Color(0xFF2E1B60), Color(0xFF160D30)))
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.MenuBook,
                            contentDescription = null,
                            tint = VioletPrimary,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            // Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary,
                        fontSize = 15.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = book.author,
                    style = MaterialTheme.typography.bodySmall.copy(
                        color = TextSecondary,
                        fontSize = 12.sp
                    ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Progress Indicator
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (progress >= 100) "Completed" else "$progress% read",
                        style = MaterialTheme.typography.labelSmall.copy(
                            color = if (progress >= 100) Color(0xFF34D399) else VioletPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color = if (progress >= 100) Color(0xFF34D399) else VioletPrimary,
                    trackColor = DarkOutline
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Action Row
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = onResume,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                        modifier = Modifier.height(34.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (progress >= 100) "Read Again" else "Resume",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // More actions dropdown
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Options",
                        tint = TextSecondary
                    )
                }

                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                    modifier = Modifier.background(DarkSurfaceVariant)
                ) {
                    if (progress < 100) {
                        DropdownMenuItem(
                            text = { Text("Mark as Completed", color = TextPrimary) },
                            leadingIcon = {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF34D399))
                            },
                            onClick = {
                                menuExpanded = false
                                onMarkCompleted()
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Remove from Library", color = Color(0xFFFB7185)) },
                        leadingIcon = {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFFB7185))
                        },
                        onClick = {
                            menuExpanded = false
                            onRemove()
                        }
                    )
                }
            }
        }
    }
}
