package com.example.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.api.SupabaseConfig
import com.example.data.auth.SupabaseAuthEvent
import com.example.data.auth.SupabaseAuthManager
import com.example.data.local.SessionManager
import com.example.data.model.BookDto
import com.example.data.model.CategoryDto
import com.example.data.model.FavoriteDto
import com.example.data.model.ProfileDto
import com.example.data.model.ReadingProgressDto
import com.example.data.repository.BookHaulRepository
import com.example.data.repository.Resource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class AuthState {
    object Checking : AuthState()
    object NeedsOnboarding : AuthState()
    data class Authenticated(val profile: ProfileDto) : AuthState()
    data class SessionExpired(val message: String = "Your session has expired. Please log in again.") : AuthState()
    data class Error(val message: String) : AuthState()
}

data class HomeUiState(
    val isLoading: Boolean = false,
    val books: List<BookDto> = emptyList(),
    val categories: List<CategoryDto> = emptyList(),
    val recommendedBooks: List<BookDto> = emptyList(),
    val recentBooks: List<BookDto> = emptyList(),
    val popularBooks: List<BookDto> = emptyList(),
    val errorMessage: String? = null
)

data class SearchUiState(
    val query: String = "",
    val isSearching: Boolean = false,
    val searchResults: List<BookDto> = emptyList(),
    val recentSearches: List<String> = emptyList(),
    val popularKeywords: List<String> = listOf("Psychology", "Technology", "Engineering", "Business", "History", "Science"),
    val errorMessage: String? = null
)

data class LibraryUiState(
    val selectedTab: Int = 0, // 0: Reading, 1: Completed, 2: Downloaded
    val isLoading: Boolean = false,
    val readingProgressList: List<ReadingProgressDto> = emptyList(),
    val errorMessage: String? = null
)

data class FavoritesUiState(
    val isLoading: Boolean = false,
    val favorites: List<FavoriteDto> = emptyList(),
    val errorMessage: String? = null
)

data class ProfileUiState(
    val isLoading: Boolean = false,
    val profile: ProfileDto? = null,
    val booksReadCount: Int = 0,
    val favoritesCount: Int = 0,
    val libraryCount: Int = 0,
    val isEditingName: Boolean = false,
    val errorMessage: String? = null
)

class BookHaulViewModel(application: Application) : AndroidViewModel(application) {

    val sessionManager = SessionManager(application)
    val repository = BookHaulRepository(sessionManager)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Checking)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _homeState = MutableStateFlow(HomeUiState())
    val homeState: StateFlow<HomeUiState> = _homeState.asStateFlow()

    private val _searchState = MutableStateFlow(SearchUiState())
    val searchState: StateFlow<SearchUiState> = _searchState.asStateFlow()

    private val _libraryState = MutableStateFlow(LibraryUiState())
    val libraryState: StateFlow<LibraryUiState> = _libraryState.asStateFlow()

    private val _favoritesState = MutableStateFlow(FavoritesUiState())
    val favoritesState: StateFlow<FavoritesUiState> = _favoritesState.asStateFlow()

    private val _profileState = MutableStateFlow(ProfileUiState())
    val profileState: StateFlow<ProfileUiState> = _profileState.asStateFlow()

    private val _selectedBook = MutableStateFlow<BookDto?>(null)
    val selectedBook: StateFlow<BookDto?> = _selectedBook.asStateFlow()

    private val _isCurrentBookFavorite = MutableStateFlow(false)
    val isCurrentBookFavorite: StateFlow<Boolean> = _isCurrentBookFavorite.asStateFlow()

    private val _currentBookProgress = MutableStateFlow(0)
    val currentBookProgress: StateFlow<Int> = _currentBookProgress.asStateFlow()

    private val _onboardingLoading = MutableStateFlow(false)
    val onboardingLoading: StateFlow<Boolean> = _onboardingLoading.asStateFlow()

    private val _onboardingError = MutableStateFlow<String?>(null)
    val onboardingError: StateFlow<String?> = _onboardingError.asStateFlow()

    private var searchJob: Job? = null

    init {
        SupabaseAuthManager.init(sessionManager)
        observeAuthEvents()
        checkInitialAuth()
    }

    private fun observeAuthEvents() {
        viewModelScope.launch {
            SupabaseAuthManager.authEvents.collect { event ->
                Log.d("BookHaul Auth", "[BookHaul Auth] Observed event: $event")
                when (event) {
                    SupabaseAuthEvent.INITIAL_SESSION -> {
                        // Handled by checkInitialAuth
                    }
                    SupabaseAuthEvent.SIGNED_IN -> {
                        // User signed in
                    }
                    SupabaseAuthEvent.TOKEN_REFRESHED -> {
                        Log.d("BookHaul Auth", "[BookHaul Auth] Token refreshed event received, keeping active session")
                    }
                    SupabaseAuthEvent.USER_UPDATED -> {
                        val currentName = sessionManager.getDisplayName()
                        _profileState.update { it.copy(profile = it.profile?.copy(displayName = currentName)) }
                    }
                    SupabaseAuthEvent.SIGNED_OUT -> {
                        _authState.value = AuthState.NeedsOnboarding
                    }
                }
            }
        }
    }

    fun checkInitialAuth() {
        viewModelScope.launch {
            _authState.value = AuthState.Checking
            if (repository.hasCompletedOnboarding()) {
                // 1. Restore & refresh session first before firing queries
                SupabaseAuthManager.restoreSession()

                // 2. Restore user profile
                val res = repository.restoreProfile()
                when (res) {
                    is Resource.Success -> {
                        _authState.value = AuthState.Authenticated(res.data)
                        _profileState.update { it.copy(profile = res.data) }
                        loadAllData()
                    }
                    is Resource.Error -> {
                        // Gracefully retain local profile info without dropping user to onboarding
                        val localName = sessionManager.getDisplayName()
                        val userId = sessionManager.getUserId()
                        if (!userId.isNullOrBlank() && localName.isNotBlank()) {
                            val localProfile = ProfileDto(id = userId, displayName = localName)
                            _authState.value = AuthState.Authenticated(localProfile)
                            _profileState.update { it.copy(profile = localProfile) }
                            loadAllData()
                        } else {
                            _authState.value = AuthState.NeedsOnboarding
                        }
                    }
                    else -> {}
                }
            } else {
                _authState.value = AuthState.NeedsOnboarding
            }
        }
    }

    fun submitOnboardingName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) {
            _onboardingError.value = "Please enter your name."
            return
        }
        viewModelScope.launch {
            _onboardingLoading.value = true
            _onboardingError.value = null
            when (val res = repository.signupAnonymousAndCreateProfile(trimmed)) {
                is Resource.Success -> {
                    _onboardingLoading.value = false
                    _authState.value = AuthState.Authenticated(res.data)
                    _profileState.update { it.copy(profile = res.data) }
                    loadAllData()
                }
                is Resource.Error -> {
                    _onboardingLoading.value = false
                    _onboardingError.value = res.message
                }
                else -> {
                    _onboardingLoading.value = false
                }
            }
        }
    }

    fun loadAllData() {
        loadHomeData()
        loadFavorites()
        loadReadingProgress()
        refreshRecentSearches()
    }

    fun loadHomeData() {
        viewModelScope.launch {
            _homeState.update { it.copy(isLoading = true, errorMessage = null) }

            val booksRes = repository.getApprovedBooks()
            val categoriesRes = repository.getCategories()

            var booksList: List<BookDto> = emptyList()
            var categoriesList: List<CategoryDto> = emptyList()
            var error: String? = null

            when (booksRes) {
                is Resource.Success -> {
                    booksList = booksRes.data
                }
                is Resource.Error -> {
                    error = booksRes.message
                }
                else -> {}
            }

            when (categoriesRes) {
                is Resource.Success -> {
                    categoriesList = categoriesRes.data
                }
                is Resource.Error -> {
                    if (error == null) error = categoriesRes.message
                }
                else -> {}
            }

            val recommended = booksList.take(6)
            val recent = booksList.sortedByDescending { it.createdAt }.take(6)
            val popular = booksList.reversed().take(6)

            _homeState.update {
                it.copy(
                    isLoading = false,
                    books = booksList,
                    categories = categoriesList,
                    recommendedBooks = recommended,
                    recentBooks = recent,
                    popularBooks = popular,
                    errorMessage = error
                )
            }

            updateProfileStats()
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchState.update { it.copy(query = query) }
        searchJob?.cancel()

        if (query.trim().isBlank()) {
            _searchState.update { it.copy(isSearching = false, searchResults = emptyList(), errorMessage = null) }
            return
        }

        searchJob = viewModelScope.launch {
            delay(350)
            _searchState.update { it.copy(isSearching = true, errorMessage = null) }
            when (val res = repository.searchBooks(query)) {
                is Resource.Success -> {
                    _searchState.update {
                        it.copy(
                            isSearching = false,
                            searchResults = res.data,
                            recentSearches = sessionManager.getRecentSearches()
                        )
                    }
                }
                is Resource.Error -> {
                    _searchState.update { it.copy(isSearching = false, errorMessage = res.message) }
                }
                else -> {}
            }
        }
    }

    fun removeRecentSearch(keyword: String) {
        sessionManager.removeRecentSearch(keyword)
        refreshRecentSearches()
    }

    fun clearRecentSearches() {
        sessionManager.clearRecentSearches()
        refreshRecentSearches()
    }

    fun refreshRecentSearches() {
        _searchState.update { it.copy(recentSearches = sessionManager.getRecentSearches()) }
    }

    fun selectBook(book: BookDto) {
        _selectedBook.value = book
        checkBookFavoriteStatus(book.id)
        checkBookProgress(book.id)
    }

    fun selectBookById(bookId: Long) {
        val existing = _homeState.value.books.find { it.id == bookId }
        if (existing != null) {
            selectBook(existing)
        } else {
            viewModelScope.launch {
                when (val res = repository.getBookById(bookId)) {
                    is Resource.Success -> selectBook(res.data)
                    else -> {}
                }
            }
        }
    }

    private fun checkBookFavoriteStatus(bookId: Long) {
        viewModelScope.launch {
            val isFav = repository.isBookFavorited(bookId)
            _isCurrentBookFavorite.value = isFav
        }
    }

    private fun checkBookProgress(bookId: Long) {
        val prog = _libraryState.value.readingProgressList.find { it.bookId == bookId }
        _currentBookProgress.value = prog?.progress ?: 0
    }

    fun toggleCurrentBookFavorite() {
        val book = _selectedBook.value ?: return
        val currentFav = _isCurrentBookFavorite.value
        viewModelScope.launch {
            // Optimistic update
            _isCurrentBookFavorite.value = !currentFav
            val res = repository.toggleFavorite(book.id, currentFav)
            if (res is Resource.Success) {
                _isCurrentBookFavorite.value = res.data
                loadFavorites()
            } else if (res is Resource.Error) {
                // Revert
                _isCurrentBookFavorite.value = currentFav
            }
        }
    }

    fun toggleFavoriteForBook(bookId: Long, isFav: Boolean) {
        viewModelScope.launch {
            repository.toggleFavorite(bookId, isFav)
            loadFavorites()
        }
    }

    fun loadFavorites() {
        viewModelScope.launch {
            _favoritesState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = repository.getFavorites()) {
                is Resource.Success -> {
                    _favoritesState.update { it.copy(isLoading = false, favorites = res.data) }
                    updateProfileStats()
                }
                is Resource.Error -> {
                    _favoritesState.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                else -> {}
            }
        }
    }

    fun loadReadingProgress() {
        viewModelScope.launch {
            _libraryState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = repository.getReadingProgressList()) {
                is Resource.Success -> {
                    _libraryState.update { it.copy(isLoading = false, readingProgressList = res.data) }
                    updateProfileStats()
                }
                is Resource.Error -> {
                    _libraryState.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                else -> {}
            }
        }
    }

    fun setLibraryTab(tabIndex: Int) {
        _libraryState.update { it.copy(selectedTab = tabIndex) }
    }

    fun updateProgressForBook(bookId: Long, progressPercent: Int) {
        viewModelScope.launch {
            val clamped = progressPercent.coerceIn(0, 100)
            _currentBookProgress.value = clamped
            repository.saveReadingProgress(bookId, clamped)
            loadReadingProgress()
        }
    }

    fun removeBookFromLibrary(bookId: Long) {
        viewModelScope.launch {
            repository.deleteReadingProgress(bookId)
            loadReadingProgress()
        }
    }

    fun markBookAsCompleted(bookId: Long) {
        updateProgressForBook(bookId, 100)
    }

    fun updateProfileName(newName: String) {
        viewModelScope.launch {
            _profileState.update { it.copy(isLoading = true, errorMessage = null) }
            when (val res = repository.updateProfileName(newName)) {
                is Resource.Success -> {
                    _profileState.update { it.copy(isLoading = false, profile = res.data, isEditingName = false) }
                    if (_authState.value is AuthState.Authenticated) {
                        _authState.value = AuthState.Authenticated(res.data)
                    }
                }
                is Resource.Error -> {
                    _profileState.update { it.copy(isLoading = false, errorMessage = res.message) }
                }
                else -> {}
            }
        }
    }

    private fun updateProfileStats() {
        val readingList = _libraryState.value.readingProgressList
        val completedCount = readingList.count { it.progress >= 100 }
        val favoritesCount = _favoritesState.value.favorites.size
        val totalLibrary = readingList.size

        _profileState.update {
            it.copy(
                booksReadCount = completedCount,
                favoritesCount = favoritesCount,
                libraryCount = totalLibrary
            )
        }
    }

    fun logout() {
        repository.logout()
        _authState.value = AuthState.NeedsOnboarding
        _homeState.value = HomeUiState()
        _favoritesState.value = FavoritesUiState()
        _libraryState.value = LibraryUiState()
        _profileState.value = ProfileUiState()
        _selectedBook.value = null
    }
}
