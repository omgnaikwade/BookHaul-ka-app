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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BookDto
import com.example.data.model.CategoryDto
import com.example.data.model.ReadingProgressDto
import com.example.ui.components.BookCard
import com.example.ui.components.ContinueReadingCard
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.HomeCategoryCard
import com.example.ui.components.HomeCategoryCardSkeleton
import com.example.ui.components.LoadingView
import com.example.ui.components.SectionHeader
import com.example.ui.components.getCategoryIcon
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurfaceContainer
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.theme.VioletPrimaryDark
import com.example.ui.viewmodel.HomeUiState
import java.util.Calendar

@Composable
fun HomeScreen(
    userName: String,
    state: HomeUiState,
    favoriteBookIds: Set<Long> = emptySet(),
    continueReadingItem: ReadingProgressDto? = null,
    onBookClick: (BookDto) -> Unit,
    onCategoryClick: (CategoryDto) -> Unit,
    onSearchClick: () -> Unit,
    onViewAllCategories: () -> Unit,
    onToggleFavorite: ((BookDto) -> Unit)? = null,
    onResumeReading: ((BookDto, Int) -> Unit)? = null,
    onRetry: () -> Unit
) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 5..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            else -> "Good Evening"
        }
    }

    val displayName = remember(userName) {
        val trimmed = userName.trim()
        if (trimmed.isNotBlank()) trimmed else "Reader"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // 1. BOOKHAUL TOP HEADER (Brand Logo + Action Buttons)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // BookHaul Logo Brand
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    VioletPrimary,
                                    VioletPrimaryDark
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = "BookHaul Logo",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.ExtraBold)) {
                            append("Book")
                        }
                        withStyle(SpanStyle(color = VioletPrimary, fontWeight = FontWeight.ExtraBold)) {
                            append("Haul")
                        }
                    },
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp)
                )
            }

            // Top Action Buttons (Search & Notifications)
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search circular button
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceContainer)
                        .border(1.dp, DarkOutline.copy(alpha = 0.6f), CircleShape)
                        .clickable(onClick = onSearchClick)
                        .testTag("home_top_search_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Notification Bell with Violet Dot Badge
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceContainer)
                        .border(1.dp, DarkOutline.copy(alpha = 0.6f), CircleShape)
                        .clickable(onClick = {})
                        .testTag("home_top_notifications_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = TextPrimary,
                        modifier = Modifier.size(20.dp)
                    )

                    // Small indicator badge dot
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 8.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(VioletPrimary)
                    )
                }
            }
        }

        if (!state.errorMessage.isNullOrBlank() && state.books.isEmpty()) {
            ErrorBanner(message = state.errorMessage, onRetry = onRetry)
        }

        if (state.isLoading && state.books.isEmpty()) {
            LoadingView(message = "Loading books from library...")
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 24.dp)
            ) {
                // 2. GREETING SECTION
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "$greeting, $displayName 👋",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary,
                            fontSize = 22.sp
                        ),
                        modifier = Modifier.testTag("home_user_greeting")
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Find your next great read",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextSecondary,
                            fontSize = 14.sp
                        )
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 3. CATEGORIES SECTION (Prominent horizontal row from Supabase)
                if (state.categories.isNotEmpty()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.testTag("home_categories_row")
                    ) {
                        // Dynamically display real categories fetched from Supabase
                        items(state.categories) { category ->
                            val icon = getCategoryIcon(category.name)
                            HomeCategoryCard(
                                name = category.name,
                                icon = icon,
                                onClick = { onCategoryClick(category) }
                            )
                        }

                        // "More" card to explore all categories in dedicated screen
                        item {
                            HomeCategoryCard(
                                name = "More",
                                icon = Icons.Default.GridView,
                                onClick = onViewAllCategories,
                                isMoreButton = true
                            )
                        }
                    }
                } else if (state.isLoading) {
                    // Loading skeleton state for categories
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(5) {
                            HomeCategoryCardSkeleton()
                        }
                    }
                } else if (!state.errorMessage.isNullOrBlank()) {
                    // Friendly Category Error state
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 6.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(DarkSurfaceContainer)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Couldn't load categories",
                            style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                        )
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Retry", fontSize = 12.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // 4. RECOMMENDED FOR YOU
                if (state.recommendedBooks.isNotEmpty()) {
                    SectionHeader(
                        title = "Recommended for you",
                        actionText = "See all",
                        onActionClick = onSearchClick
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.recommendedBooks) { book ->
                            val isFav = favoriteBookIds.contains(book.id)
                            BookCard(
                                book = book,
                                onClick = { onBookClick(book) },
                                isFavorite = isFav,
                                onFavoriteToggle = if (onToggleFavorite != null) {
                                    { onToggleFavorite(book) }
                                } else null
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // 5. CONTINUE READING (Hero Card if user has reading in progress < 100%)
                if (continueReadingItem != null && continueReadingItem.progress in 1..99) {
                    val matchingBook = state.books.find { it.id == continueReadingItem.bookId }
                        ?: continueReadingItem.book
                    if (matchingBook != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 4.dp)
                        ) {
                            ContinueReadingCard(
                                book = matchingBook,
                                progress = continueReadingItem.progress,
                                onResumeClick = {
                                    if (onResumeReading != null) {
                                        onResumeReading(matchingBook, continueReadingItem.progress)
                                    } else {
                                        onBookClick(matchingBook)
                                    }
                                }
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                    }
                }

                // 6. RECENTLY ADDED
                if (state.recentBooks.isNotEmpty()) {
                    SectionHeader(
                        title = "Recently Added",
                        actionText = "See all",
                        onActionClick = onSearchClick
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.recentBooks) { book ->
                            val isFav = favoriteBookIds.contains(book.id)
                            BookCard(
                                book = book,
                                onClick = { onBookClick(book) },
                                isFavorite = isFav,
                                onFavoriteToggle = if (onToggleFavorite != null) {
                                    { onToggleFavorite(book) }
                                } else null
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(18.dp))
                }

                // 7. POPULAR READS
                if (state.popularBooks.isNotEmpty()) {
                    SectionHeader(
                        title = "Popular Reads",
                        actionText = "See all",
                        onActionClick = onSearchClick
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.popularBooks) { book ->
                            val isFav = favoriteBookIds.contains(book.id)
                            BookCard(
                                book = book,
                                onClick = { onBookClick(book) },
                                isFavorite = isFav,
                                onFavoriteToggle = if (onToggleFavorite != null) {
                                    { onToggleFavorite(book) }
                                } else null
                            )
                        }
                    }
                }

                // If no books at all in Supabase and no error occurred
                if (state.books.isEmpty() && !state.isLoading && state.errorMessage.isNullOrBlank()) {
                    EmptyState(
                        title = "No books available yet.",
                        message = "Approved books from your Supabase library will appear here automatically.",
                        actionButtonText = "Refresh Library",
                        onActionClick = onRetry
                    )
                }
            }
        }
    }
}
