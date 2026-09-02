package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BookDto
import com.example.ui.components.BookCard
import com.example.ui.components.EmptyState
import com.example.ui.components.ErrorBanner
import com.example.ui.components.LoadingView
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.TextPrimary
import com.example.ui.viewmodel.FavoritesUiState

@Composable
fun FavoritesScreen(
    state: FavoritesUiState,
    onBookClick: (BookDto) -> Unit,
    onExploreBooks: () -> Unit,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Header
        Text(
            text = "Favorites",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.ExtraBold,
                color = TextPrimary,
                fontSize = 22.sp
            ),
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 12.dp)
        )

        if (!state.errorMessage.isNullOrBlank()) {
            ErrorBanner(message = state.errorMessage, onRetry = onRetry)
        }

        if (state.isLoading) {
            LoadingView(message = "Loading favorites from Supabase...")
        } else if (state.favorites.isEmpty()) {
            EmptyState(
                title = "No favorites yet",
                message = "Tap the heart icon on any book you love to save it to your favorites list.",
                icon = Icons.Default.FavoriteBorder,
                actionButtonText = "Browse Books",
                onActionClick = onExploreBooks
            )
        } else {
            val validBooks = state.favorites.mapNotNull { it.book }
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(validBooks, key = { it.id }) { book ->
                    BookCard(
                        book = book,
                        onClick = { onBookClick(book) },
                        width = 160,
                        height = 220
                    )
                }
            }
        }
    }
}
