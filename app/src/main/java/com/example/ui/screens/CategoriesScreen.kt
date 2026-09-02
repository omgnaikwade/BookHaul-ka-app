package com.example.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BookDto
import com.example.data.model.CategoryDto
import com.example.ui.components.BookCard
import com.example.ui.components.CategoryGridCard
import com.example.ui.components.EmptyState
import com.example.ui.components.HomeCategoryCardSkeleton
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary

@Composable
fun CategoriesScreen(
    categories: List<CategoryDto>,
    allBooks: List<BookDto>,
    isLoading: Boolean = false,
    errorMessage: String? = null,
    initialSelectedCategory: CategoryDto? = null,
    onBookClick: (BookDto) -> Unit,
    onBack: (() -> Unit)? = null,
    onRetry: (() -> Unit)? = null
) {
    var selectedCategory by remember(initialSelectedCategory) { mutableStateOf(initialSelectedCategory) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectedCategory != null || onBack != null) {
                IconButton(
                    onClick = {
                        if (selectedCategory != null) {
                            selectedCategory = null
                        } else if (onBack != null) {
                            onBack()
                        }
                    },
                    modifier = Modifier.testTag("categories_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            }

            Text(
                text = selectedCategory?.name ?: "Categories",
                style = MaterialTheme.typography.titleLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    fontSize = 22.sp
                ),
                modifier = Modifier.padding(start = if (selectedCategory != null || onBack != null) 4.dp else 4.dp)
            )
        }

        // Category Detail View (Books in this category)
        if (selectedCategory != null) {
            val cat = selectedCategory!!
            // Real Supabase filtering: filter books by category_id or category relation
            val categoryBooks = remember(allBooks, cat.id) {
                allBooks.filter { it.categoryId == cat.id || it.category?.id == cat.id }
            }

            if (categoryBooks.isEmpty()) {
                EmptyState(
                    title = "No books in ${cat.name}",
                    message = "Books in this category will appear here once added in Supabase.",
                    icon = Icons.Default.Category
                )
            } else {
                Text(
                    text = if (categoryBooks.size == 1) "1 book available" else "${categoryBooks.size} books available",
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = TextSecondary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
                )

                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(categoryBooks) { book ->
                        BookCard(
                            book = book,
                            onClick = { onBookClick(book) },
                            width = 160,
                            height = 220
                        )
                    }
                }
            }
        } else {
            // Main Categories Overview Screen
            if (isLoading && categories.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = VioletPrimary, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Loading categories from Supabase...",
                        style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
                    )
                }
            } else if (!errorMessage.isNullOrBlank() && categories.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Couldn't load categories",
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Please check your network connection and try again.",
                        style = MaterialTheme.typography.bodySmall.copy(color = TextSecondary)
                    )
                    if (onRetry != null) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onRetry,
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Retry")
                        }
                    }
                }
            } else if (categories.isEmpty()) {
                EmptyState(
                    title = "No categories available",
                    message = "Categories from your Supabase library will appear here automatically.",
                    icon = Icons.Default.Category,
                    actionButtonText = if (onRetry != null) "Refresh" else null,
                    onActionClick = onRetry
                )
            } else {
                // List/Grid of categories loaded from Supabase
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(categories) { category ->
                        val count = allBooks.count { it.categoryId == category.id || it.category?.id == category.id }
                        CategoryGridCard(
                            category = category,
                            bookCount = count,
                            onClick = { selectedCategory = category }
                        )
                    }
                }
            }
        }
    }
}
