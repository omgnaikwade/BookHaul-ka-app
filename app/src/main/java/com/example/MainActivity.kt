package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocalLibrary
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.LocalLibrary
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.BookDto
import com.example.data.model.CategoryDto
import com.example.ui.screens.BookDetailsScreen
import com.example.ui.screens.CategoriesScreen
import com.example.ui.screens.FavoritesScreen
import com.example.ui.screens.HomeScreen
import com.example.ui.screens.MyLibraryScreen
import com.example.ui.screens.OnboardingScreen
import com.example.ui.screens.PdfReaderScreen
import com.example.ui.screens.ProfileScreen
import com.example.ui.screens.SearchScreen
import com.example.ui.theme.DarkBackground
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.VioletPrimary
import com.example.ui.viewmodel.AuthState
import com.example.ui.viewmodel.BookHaulViewModel

enum class MainTab(val title: String, val selectedIcon: ImageVector, val unselectedIcon: ImageVector) {
    HOME("Home", Icons.Filled.Home, Icons.Outlined.Home),
    CATEGORIES("Categories", Icons.Filled.GridView, Icons.Outlined.GridView),
    LIBRARY("Library", Icons.Filled.LocalLibrary, Icons.Outlined.LocalLibrary),
    FAVORITES("Favorites", Icons.Filled.Favorite, Icons.Filled.FavoriteBorder),
    PROFILE("Profile", Icons.Filled.Person, Icons.Outlined.Person)
}

sealed class AppDestination {
    object Main : AppDestination()
    object Search : AppDestination()
    data class Categories(val selectedCategory: CategoryDto? = null) : AppDestination()
    data class BookDetail(val book: BookDto) : AppDestination()
    data class PdfReader(val book: BookDto, val initialProgress: Int) : AppDestination()
}

class MainActivity : ComponentActivity() {

    private val viewModel: BookHaulViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                BookHaulRootApp(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun BookHaulRootApp(viewModel: BookHaulViewModel) {
    val authState by viewModel.authState.collectAsState()
    val onboardingLoading by viewModel.onboardingLoading.collectAsState()
    val onboardingError by viewModel.onboardingError.collectAsState()

    AnimatedContent(
        targetState = authState,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "auth_screen_transition"
    ) { currentAuth ->
        when (currentAuth) {
            is AuthState.Checking -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DarkBackground),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = VioletPrimary, modifier = Modifier.size(48.dp))
                }
            }
            is AuthState.NeedsOnboarding, is AuthState.SessionExpired, is AuthState.Error -> {
                val errorText = when (currentAuth) {
                    is AuthState.SessionExpired -> currentAuth.message
                    is AuthState.Error -> currentAuth.message
                    else -> onboardingError
                }
                OnboardingScreen(
                    isLoading = onboardingLoading,
                    errorMessage = errorText,
                    onContinue = { name ->
                        viewModel.submitOnboardingName(name)
                    }
                )
            }
            is AuthState.Authenticated -> {
                BookHaulMainContent(viewModel = viewModel, userName = currentAuth.profile.displayName)
            }
        }
    }
}

@Composable
fun BookHaulMainContent(viewModel: BookHaulViewModel, userName: String) {
    var currentTab by remember { mutableStateOf(MainTab.HOME) }
    var currentDestination by remember { mutableStateOf<AppDestination>(AppDestination.Main) }

    val homeState by viewModel.homeState.collectAsState()
    val searchState by viewModel.searchState.collectAsState()
    val libraryState by viewModel.libraryState.collectAsState()
    val favoritesState by viewModel.favoritesState.collectAsState()
    val profileState by viewModel.profileState.collectAsState()
    val isCurrentBookFavorite by viewModel.isCurrentBookFavorite.collectAsState()
    val currentBookProgress by viewModel.currentBookProgress.collectAsState()

    // Back button handling
    BackHandler(enabled = currentDestination != AppDestination.Main || currentTab != MainTab.HOME) {
        when {
            currentDestination is AppDestination.PdfReader -> {
                val pdfDest = currentDestination as AppDestination.PdfReader
                currentDestination = AppDestination.BookDetail(pdfDest.book)
            }
            currentDestination != AppDestination.Main -> {
                currentDestination = AppDestination.Main
            }
            currentTab != MainTab.HOME -> {
                currentTab = MainTab.HOME
            }
        }
    }

    when (val dest = currentDestination) {
        is AppDestination.Search -> {
            SearchScreen(
                state = searchState,
                onQueryChanged = { query -> viewModel.onSearchQueryChanged(query) },
                onBookClick = { book ->
                    viewModel.selectBook(book)
                    currentDestination = AppDestination.BookDetail(book)
                },
                onRemoveRecentSearch = { keyword -> viewModel.removeRecentSearch(keyword) },
                onClearRecentSearches = { viewModel.clearRecentSearches() },
                onBack = { currentDestination = AppDestination.Main }
            )
        }

        is AppDestination.Categories -> {
            CategoriesScreen(
                categories = homeState.categories,
                allBooks = homeState.books,
                isLoading = homeState.isLoading,
                errorMessage = homeState.errorMessage,
                initialSelectedCategory = dest.selectedCategory,
                onBookClick = { book ->
                    viewModel.selectBook(book)
                    currentDestination = AppDestination.BookDetail(book)
                },
                onBack = { currentDestination = AppDestination.Main },
                onRetry = { viewModel.loadHomeData() }
            )
        }

        is AppDestination.BookDetail -> {
            val related = homeState.books.filter {
                it.id != dest.book.id && (it.categoryId == dest.book.categoryId || it.author.equals(dest.book.author, ignoreCase = true))
            }.take(5)

            BookDetailsScreen(
                book = dest.book,
                isFavorite = isCurrentBookFavorite,
                progress = currentBookProgress,
                relatedBooks = related,
                onBack = { currentDestination = AppDestination.Main },
                onReadClick = {
                    currentDestination = AppDestination.PdfReader(dest.book, currentBookProgress)
                },
                onToggleFavorite = {
                    viewModel.toggleCurrentBookFavorite()
                },
                onAddToLibrary = {
                    if (currentBookProgress == 0) {
                        viewModel.updateProgressForBook(dest.book.id, 1)
                    } else {
                        viewModel.removeBookFromLibrary(dest.book.id)
                    }
                },
                onRelatedBookClick = { relBook ->
                    viewModel.selectBook(relBook)
                    currentDestination = AppDestination.BookDetail(relBook)
                }
            )
        }

        is AppDestination.PdfReader -> {
            PdfReaderScreen(
                book = dest.book,
                initialProgress = dest.initialProgress,
                onProgressUpdated = { progress ->
                    viewModel.updateProgressForBook(dest.book.id, progress)
                },
                onBack = {
                    currentDestination = AppDestination.BookDetail(dest.book)
                }
            )
        }

        is AppDestination.Main -> {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
                containerColor = DarkBackground,
                bottomBar = {
                    NavigationBar(
                        containerColor = DarkSurface,
                        contentColor = TextPrimary,
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .navigationBarsPadding()
                            .testTag("main_bottom_navigation")
                    ) {
                        MainTab.values().forEach { tab ->
                            val selected = currentTab == tab
                            NavigationBarItem(
                                selected = selected,
                                onClick = { currentTab = tab },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.title,
                                        modifier = Modifier.size(22.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = tab.title,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = VioletPrimary,
                                    selectedTextColor = VioletPrimary,
                                    unselectedIconColor = TextMuted,
                                    unselectedTextColor = TextMuted,
                                    indicatorColor = VioletPrimary.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.testTag("tab_${tab.title.lowercase()}")
                            )
                        }
                    }
                }
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    when (currentTab) {
                        MainTab.HOME -> {
                            val favoriteBookIds = remember(favoritesState.favorites) {
                                favoritesState.favorites.map { it.bookId }.toSet()
                            }
                            val continueReadingItem = remember(libraryState.readingProgressList) {
                                libraryState.readingProgressList.filter { it.progress in 1..99 }
                                    .maxByOrNull { it.updatedAt ?: "" }
                            }

                            HomeScreen(
                                userName = userName,
                                state = homeState,
                                favoriteBookIds = favoriteBookIds,
                                continueReadingItem = continueReadingItem,
                                onBookClick = { book ->
                                    viewModel.selectBook(book)
                                    currentDestination = AppDestination.BookDetail(book)
                                },
                                onCategoryClick = { category ->
                                    currentDestination = AppDestination.Categories(category)
                                },
                                onSearchClick = {
                                    currentDestination = AppDestination.Search
                                },
                                onViewAllCategories = {
                                    currentDestination = AppDestination.Categories(null)
                                },
                                onToggleFavorite = { book ->
                                    viewModel.selectBook(book)
                                    viewModel.toggleCurrentBookFavorite()
                                },
                                onResumeReading = { book, progress ->
                                    viewModel.selectBook(book)
                                    currentDestination = AppDestination.PdfReader(book, progress)
                                },
                                onRetry = { viewModel.loadAllData() }
                            )
                        }

                        MainTab.CATEGORIES -> {
                            CategoriesScreen(
                                categories = homeState.categories,
                                allBooks = homeState.books,
                                isLoading = homeState.isLoading,
                                errorMessage = homeState.errorMessage,
                                initialSelectedCategory = null,
                                onBookClick = { book ->
                                    viewModel.selectBook(book)
                                    currentDestination = AppDestination.BookDetail(book)
                                },
                                onBack = null,
                                onRetry = { viewModel.loadHomeData() }
                            )
                        }

                        MainTab.LIBRARY -> {
                            MyLibraryScreen(
                                state = libraryState,
                                onTabSelected = { tabIdx -> viewModel.setLibraryTab(tabIdx) },
                                onBookClick = { book ->
                                    viewModel.selectBook(book)
                                    currentDestination = AppDestination.BookDetail(book)
                                },
                                onResumeReading = { book, progress ->
                                    viewModel.selectBook(book)
                                    currentDestination = AppDestination.PdfReader(book, progress)
                                },
                                onMarkCompleted = { bookId -> viewModel.markBookAsCompleted(bookId) },
                                onRemoveFromLibrary = { bookId -> viewModel.removeBookFromLibrary(bookId) },
                                onExploreBooks = { currentTab = MainTab.HOME },
                                onRetry = { viewModel.loadReadingProgress() }
                            )
                        }

                        MainTab.FAVORITES -> {
                            FavoritesScreen(
                                state = favoritesState,
                                onBookClick = { book ->
                                    viewModel.selectBook(book)
                                    currentDestination = AppDestination.BookDetail(book)
                                },
                                onExploreBooks = { currentTab = MainTab.HOME },
                                onRetry = { viewModel.loadFavorites() }
                            )
                        }

                        MainTab.PROFILE -> {
                            ProfileScreen(
                                state = profileState,
                                onUpdateName = { newName -> viewModel.updateProfileName(newName) },
                                onLogout = { viewModel.logout() }
                            )
                        }
                    }
                }
            }
        }
    }
}
