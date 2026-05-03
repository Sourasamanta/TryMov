# TryMov — Intelligent Movie Discovery
## Technical Design Document

**Version:** 1.0  
**Author:** Sourajit Samanta  
**Date:** May 2026  
**Stack:** Android (Kotlin · Jetpack Compose) + FastAPI (Python) + Room + TMDB + OMDb  

---

## How to Read This Document

This document is structured so that a developer can pick it up cold and fully understand the system—both conceptually and at the code level.

**Recommended reading order:**

| Goal | Read These Sections |
|---|---|
| Understand the big picture | §1 Architecture Overview |
| Understand design decisions | §2 System Design Process, §8 Trade-offs |
| Trace a user action end-to-end | §4 Flowchart and Data Flow |
| Implement or extend a component | §5 Component-Level Design |
| Understand the database | §6 Data Model and Schema |
| Prepare for review or interview | §7 Non-Functional Requirements, §9 Interview Q&A |

**Mapping to code files:**

| Document Section | Key Source Files |
|---|---|
| Recommendation Engine | `main.py` |
| Android Networking | `Retrofit.kt`, `OmdbRetro.kt`, `MovieAPI.kt`, `OmbdModel.kt` |
| UI & State | `FirstScreen.kt`, `MyListScreen.kt`, `ViewModel.kt`, `MyListViewModel.kt` |
| Domain Models | `Movie.kt`, `MyListEntry.kt`, `WatchStatus.kt`, `DataModel.kt` |
| Database | `AppDatabase.kt`, `MovieEntity.kt`, `MyListEntryEntity.kt`, `MovieDao.kt`, `MyListDao.kt` |
| Repository / DI | `MovieRepository.kt`, `AppContainer.kt` |
| Theme / Colors | `TryMovUiColors.kt`, `Theme.kt`, `Color.kt`, `Type.kt` |
| App Bootstrap | `TryMovApplication.kt`, `MainActivity.kt`, `AndroidManifest.xml` |

---

# 1. Architecture Overview

## 1.1 High-Level Architecture

TryMov follows a **client-server architecture with a local persistence layer**. It is not a monolith, but it is also not a full microservices system. The design can be described as a **thin-server + fat-client** pattern:

- The **backend** (FastAPI) is intentionally stateless and lightweight. It owns only the ML recommendation logic.
- The **Android client** owns the full user experience, local data, third-party API orchestration, and UI state management.
- **Third-party APIs** (TMDB, OMDb) are consumed directly from the Android client, not proxied through the backend.

```
┌─────────────────────────────────────────────────────────────────┐
│                      ANDROID CLIENT                             │
│                                                                 │
│  ┌──────────────┐  ┌───────────────────┐  ┌─────────────────┐  │
│  │   Compose UI  │  │   ViewModels      │  │  Room Database  │  │
│  │ FirstScreen   │  │ MovieViewModel    │  │  MovieEntity    │  │
│  │ MyListScreen  │◄─┤ MyListViewModel   ├─►│  MyListEntry    │  │
│  └──────────────┘  └────────┬──────────┘  └─────────────────┘  │
│                             │                                   │
│              ┌──────────────┼────────────────┐                  │
│              │              │                │                  │
│         Retrofit.kt    OmdbRetro.kt    TmdbApi.kt               │
└─────────────────────────────────────────────────────────────────┘
         │                    │                    │
         ▼                    ▼                    ▼
  ┌─────────────┐    ┌──────────────┐    ┌─────────────────┐
  │  FastAPI    │    │  OMDb API    │    │   TMDB API      │
  │  Backend    │    │ (Posters)    │    │ (Movie Details) │
  │             │    │              │    │                 │
  │ TF-IDF +   │    │ GET /?t=     │    │ GET /find/      │
  │ Cosine Sim  │    │ {title}      │    │ GET /movie/{id} │
  └─────────────┘    └──────────────┘    └─────────────────┘
         │
         ▼
  tmdb_5000_movies.csv
  (4803 movies dataset)
```

## 1.2 Main Components and Responsibilities

| Component | File(s) | Responsibility |
|---|---|---|
| **Recommendation Engine** | `main.py` | Loads TMDB dataset, builds TF-IDF matrix at startup, serves cosine-similarity-based recommendations over HTTP |
| **API Client (FastAPI)** | `Retrofit.kt`, `MovieAPI.kt` | Wraps FastAPI calls via Retrofit; exposes `health()` and `recommend(title)` suspending functions |
| **OMDb Client** | `OmdbRetro.kt`, `OmbdModel.kt` | Fetches movie poster URLs from the OMDb API by title or IMDb ID |
| **TMDB Client** | `TmdbApi.kt`, `TmdbModels.kt` | Fetches full movie metadata (genres, runtime, poster path) from TMDB's REST API |
| **Room Database** | `AppDatabase.kt`, `*Entity.kt`, `*Dao.kt` | Persists the user's "My List" (movies + watchlist entries) locally on the device |
| **Repository** | `MovieRepository.kt` | Single source of truth; orchestrates Room, TMDB, and type conversions; exposes reactive Flow streams |
| **ViewModels** | `ViewModel.kt`, `MyListViewModel.kt` | Hold and transform UI state; survive configuration changes; isolate UI from data logic |
| **Compose UI** | `FirstScreen.kt`, `MyListScreen.kt` | Declarative UI; observes ViewModel state via `collectAsState`; renders search, results, and list screens |
| **DI Container** | `AppContainer.kt`, `TryMovApplication.kt` | Manual dependency injection; creates and wires together all repository/database/API instances at app start |

## 1.3 Deployment Pattern

| Layer | Current Setup | Production Recommendation |
|---|---|---|
| Android App | Debug APK distributed manually; installed via Android Studio | Google Play Store signed release APK |
| FastAPI Backend | Runs on developer laptop; exposed via **ngrok** HTTPS tunnel | Docker container on Render / Fly.io / AWS EC2 with a fixed HTTPS domain |
| Dataset | Static CSV file bundled with backend process | Pre-computed similarity matrix (`.npy` / pickle) loaded at startup for faster boot |
| Secrets | OMDb key hardcoded (warned against); TMDB key in `local.properties` / `BuildConfig` | Environment variables injected at CI/CD time; never committed to git |

## 1.4 How Components Connect

1. The **Android app** starts → `TryMovApplication.onCreate()` → `AppContainer` is built → `Room DB`, `TmdbApi`, `OmdbClient`, `MovieRepository` are all instantiated.
2. `MainActivity` sets up Compose navigation (Discover tab + My List tab).
3. On the **Discover** tab, `MovieViewModel` is created by the `viewModel()` factory. On first load it calls `pingServer()` to verify the FastAPI backend is reachable.
4. User types a movie title → `getRecommendations(movie)` → `ApiClient.api.recommend(movie)` → HTTP GET to FastAPI → response parsed as `RecommendResponse` → `_recommendations` StateFlow updated → Compose recomposes.
5. For each recommendation, `OmdbClient.api.getByTitle(apiKey, title)` is called to fetch the poster URL → `Coil` renders the poster asynchronously.
6. On the **My List** tab, the user enters an IMDb ID → `MyListViewModel.addMovie(imdbId)` → `MovieRepository.addMovieByImdbId()` → TMDB API called → `MovieEntity` and `MyListEntryEntity` inserted into Room → `myListDao.observeAll()` Flow emits new list → UI recomposes.

> **Developer note:** Understanding this connection chain is the key to tracing any bug. Every user action maps onto this exact call chain: UI event → ViewModel function → Repository method → (Network call and/or DB operation) → StateFlow update → Compose recomposition.

---

# 2. System Design Process

## 2.1 Requirements Analysis

### Functional Requirements

| ID | Requirement | Priority |
|---|---|---|
| FR-01 | User can search for a movie by title and receive up to 10 content-based recommendations | High |
| FR-02 | Each recommendation shows the movie title, similarity score, and poster image | High |
| FR-03 | User can add a movie to their personal watchlist by entering an IMDb ID | High |
| FR-04 | User can track watch status (Watching / Completed / Dropped / Planned) per movie | Medium |
| FR-05 | User can rate a movie (0–10), mark it as favorite, and track progress in minutes | Medium |
| FR-06 | My List persists across app restarts (local Room database) | High |
| FR-07 | Backend returns fuzzy-matched results (handles typos in movie titles) | Medium |

### Non-Functional Requirements (Summary)

- **Latency:** Search results should appear within 2 seconds under normal network conditions.
- **Offline support:** My List is fully functional offline; Discover tab requires network.
- **Scale:** Single-user Android app; backend designed for a single developer/demo context.
- **Security:** No user authentication; TMDB/OMDb API keys must not be committed to version control.

### Why Requirements Matter for Code

Requirements FR-01 and FR-07 directly caused the choice of TF-IDF + cosine similarity with `difflib` fuzzy matching in `main.py`. FR-06 drove the addition of Room—initially the app may have had no local persistence. FR-04/FR-05 drove the `WatchStatus` enum and the fields on `MyListEntryEntity`.

## 2.2 Capacity and Scalability Estimates

| Metric | Estimate | Implication |
|---|---|---|
| Dataset size | 4,803 movies | TF-IDF matrix is ~4803 × 4500 float32 = ~86 MB in memory |
| Startup time | 5–15 seconds | Matrix is recomputed each time `main.py` starts; no precomputation |
| Requests per second | < 5 RPS (demo use) | No load balancing or caching needed |
| Room DB rows | < 500 entries | No pagination or batch queries needed |
| OMDb calls per search | 10 (one per recommendation) | No throttling protection; potential for rate limiting |

**Implication for code:** The current `main.py` computes the entire TF-IDF + cosine similarity matrix on every cold start. This is acceptable for a demo but is noted as a future improvement: precompute and persist the matrix as a `.npy` file to reduce startup from ~15s to < 1s.

## 2.3 Data Model and Database Design

Three logical storage systems exist:

1. **In-memory (Python/FastAPI):** The `corpus` list, `x` TF-IDF array, and `cs` cosine similarity matrix. These are computed once at module load and held in RAM for the process lifetime.
2. **Remote APIs (TMDB, OMDb):** Queried on demand; no caching layer in the current design.
3. **Local Room Database (Android):** Two tables — `movies` (cache of TMDB metadata) and `my_list_entries` (user's watchlist). See §6 for full schema.

## 2.4 API Design

### FastAPI Endpoints

**Health Check**
```
GET /
Response: 200 OK
Body: { "status": "ok" }
```

**Recommendations**
```
GET /items/{item}
Path param: item (string) — movie title, case-insensitive, fuzzy matched
Response: 200 OK
Body:
{
  "movie": "inception",
  "top_n": 10,
  "recommendations": [
    { "title": "interstellar", "score": 0.812 },
    { "title": "the prestige", "score": 0.771 },
    ...
  ]
}

Error (movie not found):
{
  "error": "Movie not found in dataset."
}
```

**Known limitation:** The endpoint uses a path parameter (`/items/{item}`). Movie titles with spaces or special characters must be URL-encoded by the client. The README recommends migrating to a query parameter (`GET /recommend?title=...`) to avoid encoding edge cases.

### TMDB Endpoints (consumed by Android)

```
GET /3/find/{imdbId}?api_key={key}&external_source=imdb_id
GET /3/movie/{movieId}?api_key={key}
```

### OMDb Endpoints (consumed by Android)

```
GET https://www.omdbapi.com/?apikey={key}&t={title}
GET https://www.omdbapi.com/?apikey={key}&i={imdbId}
```

## 2.5 Caching, Performance, Resilience

| Area | Current State | Recommended Improvement |
|---|---|---|
| Recommendation results | Not cached | Cache response in Android ViewModel or Room for same-session queries |
| Movie posters (OMDb) | Cached by Coil (in-memory + disk) | Already handled |
| TF-IDF matrix | Recomputed on startup | Serialize with `pickle` / `numpy.save` and load on startup |
| TMDB metadata | Cached in Room `movies` table | Already handled — `upsert` on `OnConflictStrategy.REPLACE` |
| Error retries | None (single attempt) | Add OkHttp `Retry` interceptor with exponential backoff |

## 2.6 Security

| Area | Current Implementation | Risk | Fix |
|---|---|---|---|
| TMDB API Key | Stored in `local.properties`, injected via `BuildConfig` | Extracted from APK via reverse engineering | Use a proxy endpoint on backend |
| OMDb API Key | Hardcoded in source (warned in README) | Committed to git, exposed in APK | Move to `local.properties` / CI secrets |
| Backend URL | Hardcoded ngrok URL in `Retrofit.kt` | URL changes every ngrok restart | Use a stable domain + environment config |
| HTTPS | ngrok provides HTTPS; OMDb/TMDB use HTTPS | No TLS issues at runtime | Enforce `android:usesCleartextTraffic="false"` in manifest for production |
| Input sanitization | `difflib.get_close_matches` is read-only; no SQL injection surface on backend | Low risk | Sanitize path param on FastAPI side for production |

> **Developer note:** For a production deployment, API keys should never reach the Android APK. Route TMDB/OMDb calls through the FastAPI backend so only the server holds the keys.

---

# 3. Methodology and Design Principles

## 3.1 Design Principles Applied

### Separation of Concerns (MVVM)

The Android app strictly follows the **Model-View-ViewModel** pattern:

- **View** (`FirstScreen.kt`, `MyListScreen.kt`): Observes state, emits user events. Contains zero business logic.
- **ViewModel** (`MovieViewModel`, `MyListViewModel`): Holds UI state as `StateFlow`; calls repository; transforms domain objects to UI-ready state.
- **Model / Repository** (`MovieRepository`): Owns all data access logic (Room + TMDB). The ViewModel never directly touches a DAO or makes a network call.

### Reactive Data with Kotlin Flow

Room DAOs expose `Flow<List<...>>` return types. This means the UI automatically reacts to database changes without polling. The chain is:

```
Room DB change → DAO Flow emits → Repository transforms → ViewModel StateFlow updates → Compose recomposes
```

### Single Source of Truth

`MovieRepository` is the single source of truth for all movie data. The UI never reads from two competing sources simultaneously. This prevents stale-data bugs.

### Stateless Backend

The FastAPI backend holds no per-request state. The `cs` (cosine similarity) matrix is a module-level variable computed once. Every request reads from it in a thread-safe manner (read-only after initialization). This makes horizontal scaling trivial: spin up multiple Uvicorn workers and each holds its own copy of the matrix.

### Manual Dependency Injection

`AppContainer` and `TryMovApplication` implement a simple service-locator / manual DI pattern. This avoids the complexity of Hilt/Dagger for a small project while still keeping dependencies explicit and testable.

## 3.2 How Principles Affect the Code

| Principle | Where You See It in Code |
|---|---|
| MVVM | `ViewModel.kt` never imports `retrofit2`; `FirstScreen.kt` never imports `Room` |
| Reactive data | `MyListDao.observeAll()` returns `Flow`; `MyListViewModel` uses `viewModelScope.launch` |
| Single source of truth | `MovieRepository` is the only class that calls `movieDao` or `myListDao` |
| Stateless backend | No session/token storage in `main.py`; no database on backend |
| Manual DI | `AppContainer` is accessed via `(application as TryMovApplication).container` |

## 3.3 System Evolution and Versioning

| Change Type | Safe to Do | Risks |
|---|---|---|
| Add new FastAPI endpoint | Safe — additive | Update `MovieAPI.kt` interface; old clients still work |
| Add Room column | Run Room migration (`addMigrations`) | Forgetting migration crashes the app on upgrade |
| Change `WatchStatus` enum values | Safe if additive | Removing an existing value breaks `AppTypeConverters` deserialization |
| Swap ngrok URL with production URL | Change `BASE_URL` in `Retrofit.kt` | Must also update `AndroidManifest.xml` network security config |
| Upgrade TF-IDF `max_features` | Recompute matrix; test similarity quality | Higher features = more RAM; may change ranking results |

---

# 4. Flowchart and Data Flow

## 4.1 User Request Path: Discover a Movie

```
USER TYPES "inception" → TAPS SEARCH BUTTON
         │
         ▼
[FirstScreen.kt]
  onSearchClick(query = "inception")
  → viewModel.getRecommendations("inception")
         │
         ▼
[MovieViewModel.kt]  viewModelScope.launch {
  _status.value = "loading"   ← UI shows loading indicator
         │
         ▼
[ApiClient / Retrofit.kt]
  MovieApi.recommend("inception")
  → HTTP GET https://<ngrok-url>/items/inception
         │
         ▼
[FastAPI main.py]
  read_item("inception")
  → find_movie("inception")      ← fuzzy match
    → difflib.get_close_matches("inception", titles)
    → returns "inception"
  → recommend_similar("inception", top_n=10)
    → idx = dataset[dataset['title']=="inception"].index[0]
    → sim_scores = sorted cosine_similarity row
    → returns top 10 { title, score }
         │
         ▼
  HTTP 200 JSON response
         │
         ▼
[Retrofit / GsonConverter]
  JSON → RecommendResponse(movie, top_n, recommendations=[...])
         │
         ▼
[MovieViewModel.kt]
  _recommendations.value = res.recommendations
  _status.value = "ok"
         │
         ▼
[FirstScreen.kt]  (collectAsState triggers recompose)
  LazyColumn renders RecommendationCard for each item
         │
         ▼
[For each card — OmdbClient]
  OmdbApi.getByTitle(apiKey, title)
  → HTTP GET https://www.omdbapi.com/?t=inception&apikey=...
  → returns OmdbMovieResponse { Poster: "https://..." }
         │
         ▼
[Coil AsyncImage]
  loads poster URL from OMDb response
  → displays poster thumbnail in card
```

**Error Handling in This Flow:**

| Step | Error Condition | Handling |
|---|---|---|
| `ApiClient.recommend()` | Network timeout / ngrok down | `catch (e: Exception)` → `_status.value = "error: ${e.message}"` |
| FastAPI `find_movie()` | No close match found | returns `None` → `recommend_similar(None)` → `{"error": "Movie not found"}` |
| FastAPI response | `error` field present | ViewModel sets `_status.value = res.error`, `_recommendations = emptyList()` |
| OMDb call | Poster not available | `OmdbMovieResponse.Poster` is null → Coil shows placeholder |

## 4.2 Background Flow: Add Movie to My List

```
USER ENTERS IMDb ID "tt0816692" → TAPS ADD
         │
         ▼
[MyListScreen.kt]
  onAddClick(imdbId = "tt0816692")
  → myListViewModel.addMovie("tt0816692")
         │
         ▼
[MyListViewModel.kt]  viewModelScope.launch {
  _isAddingMovie.value = true
  movieRepository.addMovieByImdbId("tt0816692")
         │
         ▼
[MovieRepository.kt]
  Step 1: Validate IMDb ID format (regex: ^tt\d{7,8}$)
    → if invalid → return Result.failure("Invalid IMDb ID")
  Step 2: Check for duplicates
    → myListDao.existsByImdbId("tt0816692")
    → if exists → return Result.failure("Already in list")
  Step 3: Ensure movie is in local cache
    → ensureMovieCached("tt0816692")
       → movieDao.getByImdbId("tt0816692")
       → if null: call TMDB API
           TmdbApi.findByImdbId("tt0816692", apiKey)
           → TmdbFindResponse { movie_results: [{ id: 157336, title: "Interstellar", ... }] }
           TmdbApi.movieDetails(157336, apiKey)
           → TmdbMovieDetails { runtime: 169, genres: [...], ... }
           → movieDao.upsert(MovieEntity(...))
  Step 4: Insert watchlist entry
    → myListDao.insert(MyListEntryEntity(imdbId="tt0816692", status=PLANNED))
  Step 5: return Result.success(Unit)
         │
         ▼
[MyListViewModel.kt]
  _isAddingMovie.value = false
  if success → emit Snackbar "Movie added"
  if failure → emit Snackbar with error message
         │
         ▼
[MyListDao.observeAll() Flow]
  Room detects new insert → Flow emits updated list
         │
         ▼
[MyListViewModel.kt]
  myList StateFlow updated
         │
         ▼
[MyListScreen.kt]
  LazyColumn recomposes with new movie card
```

**Error Handling in This Flow:**

| Step | Error | Handling |
|---|---|---|
| IMDb ID validation | Format doesn't match `tt\d{7,8}` | Return failure immediately; no network call made |
| TMDB `findByImdbId` | HTTP error / wrong ID | `Result.failure` propagated to ViewModel → Snackbar error |
| `myListDao.insert` | Duplicate constraint (index on imdbId) | `existsByImdbId` check prevents this; index is a safety net |

---

# 5. Component-Level Design: Function Breakdown

## 5.1 Recommendation Engine (`main.py`)

**Responsibility:** Load the TMDB 5000 dataset at startup, build a TF-IDF representation of each movie's textual features, compute pairwise cosine similarity, and serve recommendations over HTTP.

### Startup Pipeline (module-level, runs once)

```python
# 1. Load dataset
dataset = pd.read_csv('tmdb_5000_movies.csv')

# 2. Build tags column: keywords + genres + overview
dataset["tags"] = (
    (dataset["keywords"] + " ") +
    (dataset["genres"] + " ") +
    dataset["overview"]
).str.strip()

# 3. Build corpus: clean, stem, remove stopwords
corpus = []
for i in range(0, 4803):
    review = re.sub(r'[^a-zA-Z]', ' ', str(dataset['tags'][i]))
    review = re.sub(r'\b(id|name)\b', ' ', review)
    review = review.lower()
    review = review.split()
    ps = PorterStemmer()
    review = [ps.stem(word) for word in review if word not in stopwords]
    review = ' '.join(review)
    corpus.append(review)

# 4. TF-IDF vectorization
tfidf = TfidfVectorizer(max_features=4500, stop_words="english", ngram_range=(1, 2))
x = tfidf.fit_transform(corpus).toarray()  # shape: (4803, 4500)

# 5. Pairwise cosine similarity
cs = cosine_similarity(x)  # shape: (4803, 4803)
```

**Why this matters:** `cs[i][j]` gives the similarity between movie `i` and movie `j`. It is pre-computed so lookup at request time is O(n log n) sort on a single row — extremely fast.

### Function: `find_movie(user_input, cutoff=0.6)`

- **Input:** Raw user string (e.g., `"Iception"`, `"the dark knight"`)
- **Output:** Matched movie title string from dataset, or `None`
- **What it does:**
  1. Lowercases and strips the input.
  2. Checks for exact match in `dataset['title']`.
  3. If no exact match, calls `difflib.get_close_matches(user_input, titles, n=1, cutoff=0.6)` for fuzzy matching.
  4. Returns the best match, or `None` if below the 0.6 similarity cutoff.
- **Side effects:** None (read-only).
- **Failure mode:** Returns `None` for inputs with no close match. The caller `read_item` passes `None` to `recommend_similar` which returns `{"error": "Movie not found in dataset."}`.

### Function: `recommend_similar(movie_name, top_n=10)`

- **Input:** Matched movie title string (lowercase), integer `top_n`
- **Output:** Dict with keys `movie`, `top_n`, `recommendations` (list of `{title, score}`) or `{"error": ...}`
- **What it does:**
  1. Looks up the row index of `movie_name` in `dataset`.
  2. Retrieves `cs[idx]` — the similarity row for that movie.
  3. Sorts by score descending, skips the first item (self-similarity = 1.0).
  4. Takes top `top_n` results and builds the response list.
- **Side effects:** None (read-only).
- **Pseudocode:**

```python
def recommend_similar(movie_name, top_n=10):
    if movie_name not in dataset['title'].values:
        return {"error": "Movie not found in dataset."}

    idx = dataset[dataset['title'] == movie_name].index[0]
    sim_scores = list(enumerate(cs[idx]))
    sim_scores = sorted(sim_scores, key=lambda x: x[1], reverse=True)[1:top_n+1]

    return {
        "movie": movie_name,
        "top_n": top_n,
        "recommendations": [
            {"title": dataset.iloc[i]['title'], "score": round(float(score), 3)}
            for i, score in sim_scores
        ]
    }
```

### Function: `read_item(item: str)` (FastAPI route handler)

- **Input:** URL path parameter `item`
- **Output:** JSON response from `recommend_similar`
- **What it does:** Chains `find_movie(item)` → `recommend_similar(result)`.

**Dependencies:** `pandas`, `numpy`, `scikit-learn` (TfidfVectorizer, cosine_similarity), `nltk` (stopwords, PorterStemmer), `difflib` (stdlib), `fastapi`.

**Failure modes:**

| Condition | Behavior |
|---|---|
| `tmdb_5000_movies.csv` not found | `FileNotFoundError` at startup; server crashes before accepting any requests |
| NLTK data not downloaded | `LookupError` at startup; add `nltk.download('stopwords')` guard |
| Movie title with slashes in path | URL routing conflict; use query param instead |

---

## 5.2 API Client (`Retrofit.kt`, `MovieAPI.kt`)

**Responsibility:** Provide a type-safe, coroutine-compatible HTTP client for the FastAPI backend.

### Object: `ApiClient`

```kotlin
object ApiClient {
    private const val BASE_URL = "https://miracle-unwilful-amira.ngrok-free.dev"

    private val logging = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(logging)
        .build()

    val api: MovieApi = Retrofit.Builder()
        .baseUrl(BASE_URL)
        .client(client)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(MovieApi::class.java)
}
```

- **Singleton:** `object` keyword ensures one instance per process.
- **Logging interceptor:** Logs request/response bodies at `Level.BODY`. Should be disabled in release builds.
- **GsonConverterFactory:** Automatically deserializes JSON to `RecommendResponse`, `StatusResponse`.

### Interface: `MovieApi`

```kotlin
interface MovieApi {
    @GET("/")
    suspend fun health(): StatusResponse

    @GET("/items/{item}")
    suspend fun recommend(@Path("item") item: String): RecommendResponse
}
```

- `suspend` functions integrate with Kotlin coroutines — called inside `viewModelScope.launch { }`.
- `@Path("item")` encodes the parameter into the URL path.

**Failure modes:** Any network error or non-2xx HTTP status throws an `IOException` or `HttpException`, caught by `catch (e: Exception)` in the ViewModel.

---

## 5.3 OMDb Client (`OmdbRetro.kt`, `OmbdModel.kt`)

**Responsibility:** Fetch movie poster URLs from the OMDb API by title or IMDb ID.

### Object: `OmdbClient`

Mirrors `ApiClient` in structure. Key difference: `BASE_URL = "https://www.omdbapi.com/"`.

### Interface: `OmdbApi`

```kotlin
interface OmdbApi {
    @GET("/")
    suspend fun getByTitle(
        @Query("apikey") apiKey: String,
        @Query("t") title: String,
        @Query("y") year: String? = null
    ): OmdbMovieResponse

    @GET("/")
    suspend fun getByImdbId(
        @Query("apikey") apiKey: String,
        @Query("i") imdbId: String
    ): OmdbMovieResponse
}
```

### Data Class: `OmdbMovieResponse`

```kotlin
data class OmdbMovieResponse(
    val Title: String? = null,
    val imdbID: String? = null,
    val Poster: String? = null,    // "https://m.media-amazon.com/images/..." or "N/A"
    val Response: String? = null,  // "True" or "False"
    val Error: String? = null
)
```

**Important:** OMDb returns `Poster = "N/A"` (string) when no poster exists, not `null`. Compose UI must guard against this. Use `if (poster != null && poster != "N/A")` before loading.

**Failure modes:** Rate limiting (1,000 req/day on free tier). No retry logic exists. If the API key is invalid, `Response = "False"` and `Error = "Invalid API key!"`.

---

## 5.4 TMDB Client (`TmdbApi.kt`, `TmdbModels.kt`)

**Responsibility:** Fetch authoritative movie metadata (title, runtime, genres, poster path) using an IMDb ID as the lookup key.

### Interface: `TmdbApi`

```kotlin
interface TmdbApi {
    @GET("find/{imdbId}")
    suspend fun findByImdbId(
        @Path("imdbId") imdbId: String,
        @Query("api_key") apiKey: String,
        @Query("external_source") source: String = "imdb_id"
    ): TmdbFindResponse

    @GET("movie/{movieId}")
    suspend fun movieDetails(
        @Path("movieId") movieId: Int,
        @Query("api_key") apiKey: String
    ): TmdbMovieDetails
}
```

**Two-step lookup:** TMDB does not provide full details via the IMDb ID directly. Step 1 (`findByImdbId`) returns the TMDB movie ID. Step 2 (`movieDetails`) returns full metadata. This two-call pattern is handled inside `MovieRepository.ensureMovieCached()`.

### Key Data Classes

```kotlin
data class TmdbFindResponse(
    @SerializedName("movie_results") val movieResults: List<TmdbMovieResult>
)

data class TmdbMovieResult(
    val id: Int,
    val title: String,
    @SerializedName("poster_path") val posterPath: String?
)

data class TmdbMovieDetails(
    val id: Int,
    val title: String,
    val runtime: Int,
    val genres: List<TmdbGenre>,
    @SerializedName("poster_path") val posterPath: String?
)

data class TmdbGenre(val id: Int, val name: String)
```

**Failure modes:** If `movie_results` is empty (bad IMDb ID), `ensureMovieCached` should handle `IndexOutOfBoundsException` or check `movieResults.isEmpty()`.

---

## 5.5 MovieViewModel (`ViewModel.kt`)

**Responsibility:** Manage discover-tab UI state; coordinate between `ApiClient` and Compose UI.

### State

```kotlin
private val _status = MutableStateFlow("idle")
val status: StateFlow<String> = _status

private val _recommendations = MutableStateFlow<List<Recommendation>>(emptyList())
val recommendations: StateFlow<List<Recommendation>> = _recommendations
```

### Function: `pingServer()`

- **Input:** None
- **Output:** Updates `_status` with `"ok"` or `"error: ..."` 
- **What it does:** Calls `ApiClient.api.health()` in a coroutine. Used on screen open to verify backend is reachable before the user searches.
- **Side effects:** Updates `_status` StateFlow.

### Function: `getRecommendations(movie: String)`

- **Input:** Movie title string from user search field
- **Output:** Updates `_recommendations` and `_status`
- **What it does:** Calls `ApiClient.api.recommend(movie)`; on success populates recommendations; on error clears recommendations and sets error status.
- **Side effects:** Updates `_recommendations` and `_status` StateFlow.

**How UI consumes it:**

```kotlin
// In FirstScreen.kt (conceptual)
val recommendations by viewModel.recommendations.collectAsState()
val status by viewModel.status.collectAsState()

when {
    status == "loading" -> SkeletonLoader()
    status.startsWith("error") -> ErrorState(message = status)
    recommendations.isEmpty() -> EmptyState()
    else -> RecommendationList(recommendations)
}
```

---

## 5.6 Movie Repository (`MovieRepository.kt`)

**Responsibility:** Single source of truth for all movie data; orchestrates Room, TMDB API, and domain model conversions.

### Function: `addMovieByImdbId(imdbId: String): Result<Unit>`

- **Input:** IMDb ID string (e.g., `"tt0816692"`)
- **Output:** `Result.success(Unit)` or `Result.failure(Exception)`
- **Steps:**
  1. Validate format with regex `^tt\d{7,8}$`.
  2. Check `myListDao.existsByImdbId(imdbId)` — prevent duplicates.
  3. Call `ensureMovieCached(imdbId)` — fetch from TMDB if not in Room.
  4. Insert `MyListEntryEntity` into Room.
- **Side effects:** Inserts into `movies` table and `my_list_entries` table.

### Function: `ensureMovieCached(imdbId: String)`

- **Input:** IMDb ID
- **Output:** None (throws on failure)
- **Steps:**
  1. Check `movieDao.getByImdbId(imdbId)`. If non-null, return immediately.
  2. Call `tmdbApi.findByImdbId(imdbId, apiKey)`.
  3. Call `tmdbApi.movieDetails(tmdbId, apiKey)`.
  4. Map `TmdbMovieDetails` → `MovieEntity` → call `movieDao.upsert(entity)`.
- **Side effects:** Writes to `movies` table.

### Function: `observeMyList(): Flow<List<MyListEntry>>`

- **Input:** None
- **Output:** Hot Flow that emits a new list every time the Room table changes.
- **Side effects:** None (read-only observer).

**Dependencies:** `MovieDao`, `MyListDao`, `TmdbApi`.

---

## 5.7 Room Database (`AppDatabase.kt`, DAOs, Entities)

**Responsibility:** Persist the user's movie watchlist and cached movie metadata across app sessions.

### `MovieDao`

```kotlin
@Dao
interface MovieDao {
    @Query("SELECT * FROM movies WHERE imdbId = :imdbId")
    suspend fun getByImdbId(imdbId: String): MovieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(movie: MovieEntity)
}
```

### `MyListDao` (key operations)

```kotlin
@Dao
interface MyListDao {
    fun observeAll(): Flow<List<MyListEntryWithMovie>>  // JOIN query
    suspend fun existsByImdbId(imdbId: String): Boolean
    suspend fun insert(entry: MyListEntryEntity)
    suspend fun delete(id: Int)
    suspend fun updateRating(id: Int, rating: Int)
    suspend fun updateStatus(id: Int, status: WatchStatus)
    suspend fun updateProgress(id: Int, progressMinutes: Int)
    suspend fun toggleFavorite(id: Int)
}
```

**Dependencies:** Room runtime, `AppTypeConverters` for `WatchStatus` ↔ String and `List<String>` ↔ JSON.

---

## 5.8 Compose UI — Discover Screen (`FirstScreen.kt`)

**Responsibility:** Render the movie search UI, handle user input, display recommendation results and posters.

### Key UI States

| `status` value | UI shown |
|---|---|
| `"idle"` | Empty search prompt |
| `"loading"` | Skeleton loading cards |
| `"ok"` | `LazyColumn` of `RecommendationCard` |
| Starts with `"error"` | Error message with retry hint |
| `"ok"` + empty list | Empty state with suggestions |

### `RecommendationCard` composable

- **Input:** `Recommendation(title, score)` + OMDb API call result
- **Renders:** Poster thumbnail (via Coil `AsyncImage`), movie title, similarity score badge
- **Side effect:** Triggers `OmdbClient.api.getByTitle()` via `LaunchedEffect(recommendation.title)` inside the composable

**Pattern used:** Each card manages its own poster-fetch coroutine using `LaunchedEffect`. This is a deliberate design choice — the ViewModel is not aware of poster URLs; they are a purely visual concern fetched at the card level.

---

# 6. Data Model and Schema

## 6.1 Storage Strategy

| Data | Storage | Reason |
|---|---|---|
| Recommendation engine vectors | Python in-process RAM | Speed — matrix lookups must be sub-millisecond |
| Movie metadata (TMDB) | Room `movies` table | Avoid repeated API calls; persist across sessions |
| User watchlist | Room `my_list_entries` table | Must survive app restarts; user-owned data |
| Movie posters | Coil disk+memory cache | Standard image caching; no custom logic needed |
| Recommendation results | Android ViewModel StateFlow | Session-scoped; no need to persist |

**Storage type choice:** SQLite via Room (relational). Chosen because:
- The data is relational: each `my_list_entries` row references exactly one `movies` row.
- Room provides type-safe query building, Flow integration, and migration support.
- Alternative (DataStore) is designed for key-value pairs, not structured relational data.

## 6.2 Entity: `movies`

| Column | Type | Constraints | Purpose |
|---|---|---|---|
| `imdbId` | TEXT | PRIMARY KEY | Unique identifier; used as the join key |
| `tmdbId` | INTEGER | NOT NULL | Used to call TMDB `/movie/{id}` endpoint |
| `title` | TEXT | NOT NULL | Display name |
| `posterPath` | TEXT | NULLABLE | TMDB relative poster path (e.g., `/abc123.jpg`) |
| `runtime` | INTEGER | NOT NULL | Runtime in minutes |
| `genres` | TEXT | NOT NULL | JSON-encoded `List<String>` via `AppTypeConverters` |

**Indexes:** Primary key on `imdbId` (implicit). No additional indexes needed at this scale.

**Invariants:** `imdbId` format is always `tt\d{7,8}` (enforced by repository before insert).

## 6.3 Entity: `my_list_entries`

| Column | Type | Constraints | Purpose |
|---|---|---|---|
| `id` | INTEGER | PRIMARY KEY AUTOINCREMENT | Internal surrogate key |
| `imdbId` | TEXT | NOT NULL, UNIQUE INDEX | Foreign key to `movies.imdbId` |
| `rating` | INTEGER | DEFAULT 0 | User rating 0–10 |
| `status` | TEXT | NOT NULL, DEFAULT 'PLANNED' | `WatchStatus` enum stored as string |
| `progressMinutes` | INTEGER | DEFAULT 0 | Watch progress in minutes |
| `isFavorite` | BOOLEAN | DEFAULT FALSE | Favorite flag |
| `createdAt` | INTEGER | NOT NULL | Unix timestamp (ms); for sorting |

**Indexes:** `UNIQUE INDEX on imdbId` — prevents adding the same movie twice. This index exists in addition to the primary key because the application uses `imdbId` (not `id`) as the business key.

**Type conversions** (`AppTypeConverters`):
- `WatchStatus` ↔ String: stored as enum name (e.g., `"COMPLETED"`)
- `List<String>` ↔ String: stored as JSON array (e.g., `["Action","Drama"]`)

## 6.4 In-Memory Data (FastAPI)

| Variable | Type | Size | Lifetime |
|---|---|---|---|
| `dataset` | `pd.DataFrame` | 4803 rows × ~20 columns | Process lifetime |
| `corpus` | `List[str]` | 4803 strings | Process lifetime |
| `x` | `np.ndarray` (float64) | 4803 × 4500 | Process lifetime (~173 MB) |
| `cs` | `np.ndarray` (float64) | 4803 × 4803 | Process lifetime (~185 MB) |

**Total estimated RAM usage of FastAPI process:** ~400–450 MB. This is the primary constraint for hosting on free-tier platforms (Render free tier provides 512 MB).

---

# 7. Non-Functional Requirements

## 7.1 Scalability

| Dimension | Current State | Bottleneck | Solution |
|---|---|---|---|
| Backend concurrent users | 1–5 (Uvicorn single worker) | Python GIL + matrix in RAM | Add Uvicorn workers: `--workers 4` (each loads its own matrix copy) |
| Android DB size | < 1,000 rows | No bottleneck | Pagination if > 10,000 rows |
| OMDb calls | 10 per search | 1,000/day free limit | Cache OMDb responses in Room; add a `posterUrl` column to `movies` |
| TMDB calls | 2 per "add to list" | 40 req/10s rate limit | Within limits for single-user app |

## 7.2 Reliability

| Component | Failure Mode | Current Behavior | Recommended |
|---|---|---|---|
| ngrok tunnel | Goes offline or URL changes | App shows network error | Use a stable hosting domain |
| FastAPI startup | CSV missing | Crash on startup | Add existence check + fallback error endpoint |
| OMDb rate limit | 429 error | Poster doesn't load (Coil shows placeholder) | Add retry with backoff |
| Room migration | Schema change without migration | `IllegalStateException` crash | Always define `Migration` objects; never use `fallbackToDestructiveMigration` in production |

## 7.3 Observability

| Layer | Current State | Recommended |
|---|---|---|
| Android | `OkHttp` logging interceptor (BODY level) | Add Crashlytics for crash reporting |
| FastAPI | `print(corpus)` statement only | Add structured logging (`logging` module), request middleware |
| Network errors | Caught in ViewModel; shown in UI | Add analytics event for error frequency |

## 7.4 Security

| Item | Severity | Status | Action |
|---|---|---|---|
| OMDb key in source code | HIGH | NOT FIXED | Move to `local.properties` |
| TMDB key in APK via BuildConfig | MEDIUM | Partially mitigated | Route through backend in production |
| HTTP cleartext traffic | MEDIUM | Only ngrok HTTPS used | Enforce in manifest for release |
| No input sanitization on FastAPI | LOW | difflib is read-only | Add FastAPI request validation (Pydantic model) |
| Logging interceptor in release | LOW | Not disabled | Wrap in `if (BuildConfig.DEBUG)` |

## 7.5 Maintainability

| Area | Current Approach | Quality |
|---|---|---|
| Code structure | MVVM + Repository pattern | Good — clear separation |
| Dependency injection | Manual (`AppContainer`) | Acceptable for project size |
| Testing | No tests present | Gap — unit tests for Repository and ViewModel should be added |
| CI/CD | None | Add GitHub Actions for build + lint |
| Hardcoded values | `BASE_URL`, `top_n=10`, `cutoff=0.6` | Move to config or constants file |

---

# 8. Assumptions, Trade-offs, and Alternatives

## 8.1 Key Assumptions

| Assumption | Implication |
|---|---|
| Single user per device | No server-side auth, no user accounts, no data sync |
| Demo/personal use scale | No rate limiting, no load balancing, ngrok acceptable |
| 4803-movie dataset is fixed | No incremental updates to the TF-IDF matrix |
| Android API 24+ (Android 7.0+) | Can use modern Kotlin coroutines, Compose, Room features |
| Developer runs backend locally | BASE_URL must be updated per session (ngrok restarts change URL) |

## 8.2 Trade-off 1: Content-Based vs. Collaborative Filtering

| | Content-Based (TF-IDF + Cosine Sim) | Collaborative Filtering |
|---|---|---|
| **Data required** | Only movie metadata (no user data) | User-movie interaction matrix (ratings, views) |
| **Cold start** | Works immediately for any movie | Fails for new users and new movies |
| **Accuracy** | Based on textual features only | Captures latent taste patterns |
| **Complexity** | Simple, interpretable | More complex (matrix factorization, SVD) |
| **Infrastructure** | Static CSV + sklearn | Requires user database, retraining pipeline |

**Chosen:** Content-based filtering. Justified because the app has no user accounts, so collaborative filtering has no interaction data to train on. Content-based is the only viable option.

## 8.3 Trade-off 2: Local Storage (Room) vs. Cloud Storage (Firebase / Supabase)

| | Room (SQLite) | Cloud DB (Firebase Firestore) |
|---|---|---|
| **Offline** | Fully offline | Limited without sync config |
| **Multi-device sync** | Not supported | Automatic |
| **Setup complexity** | Low | Medium (auth + security rules) |
| **Cost** | Free | Free tier; paid at scale |
| **Privacy** | Data stays on device | Data uploaded to cloud |

**Chosen:** Room. Justified because the app is single-user, no account system exists, and offline-first is a core requirement. Firebase would add significant complexity (auth, security rules, conflict resolution) with no benefit at this stage.

## 8.4 Trade-off 3: Manual DI vs. Hilt

| | Manual DI (`AppContainer`) | Hilt |
|---|---|---|
| **Boilerplate** | Low for small projects | Higher initial setup |
| **Compile-time safety** | No — runtime errors on misconfiguration | Yes — build fails on DI errors |
| **Scoping** | Manual | Automatic (`@Singleton`, `@ViewModelScoped`) |
| **Testing** | Requires test doubles by hand | Built-in test support |
| **Learning curve** | Zero | Moderate |

**Chosen:** Manual DI. Justified by project size. For a project with 2–3 ViewModels and 1 Repository, Hilt adds more ceremony than value. The recommendation is to migrate to Hilt if the project grows beyond 5–6 components.

---

# 9. Interview Questions and Answers

This section covers the full spectrum of likely interview questions arising from TryMov — from Android and ML fundamentals to system design and language-specific deep dives.

---

## Part A: Machine Learning & Recommendation Systems

**Q1. Explain TF-IDF. Why use it instead of raw word counts?**

**Answer:** TF-IDF stands for Term Frequency–Inverse Document Frequency. It is a numerical statistic that reflects the importance of a word in a document relative to a corpus.

- **TF (Term Frequency):** How often a term appears in a single document (movie tag string). Frequent terms get higher weight.
- **IDF (Inverse Document Frequency):** `log(N / df(t))` where N is total documents and df(t) is the number of documents containing term t. Words that appear in every movie (like "film", "story") get low IDF — their TF weight is dampened.
- **Why not raw counts?** Raw word counts give unfair weight to common words like "the", "a", "and". TF-IDF naturally suppresses these. In TryMov, genre words like "action" appear in many movies, so IDF reduces their weight, while distinctive keywords like "inception" or "cryogenic" are up-weighted.

**Q2. What is cosine similarity and why is it preferred over Euclidean distance for text vectors?**

**Answer:** Cosine similarity measures the cosine of the angle between two vectors:

```
cos(θ) = (A · B) / (‖A‖ × ‖B‖)
```

Range: [0, 1] for non-negative vectors (TF-IDF outputs are non-negative).

**Why prefer it for text:**
- **Length invariance:** A movie with a long overview produces a high-magnitude TF-IDF vector. Euclidean distance would incorrectly penalize longer texts. Cosine similarity normalizes by magnitude, so two movies with similar topics score high regardless of overview length.
- **Interpretability:** Score of 1.0 = identical direction (same topics); 0.0 = completely orthogonal (no shared topics).
- **Computational efficiency:** The `cs` matrix is pre-computed as `cosine_similarity(x)`. Lookup is just indexing into a precomputed 2D array — O(1) after sorting.

**Q3. What are n-grams and why does TryMov use `ngram_range=(1,2)`?**

**Answer:** N-grams are contiguous sequences of n words. With `ngram_range=(1,2)`, the TF-IDF vectorizer generates:
- **Unigrams (n=1):** Single words — "dark", "knight", "action"
- **Bigrams (n=2):** Word pairs — "dark knight", "action thriller"

Bigrams capture phrases that have meaning only in combination. "The Dark Knight" as a bigram strongly distinguishes Batman films from other action movies. Without bigrams, "dark" and "knight" are treated as independent signals with no phrase relationship.

**Trade-off:** Bigrams significantly increase vocabulary size (can be millions of features). `max_features=4500` caps this to the 4,500 most informative n-grams to control matrix size.

**Q4. What is the PorterStemmer and why stem words before vectorization?**

**Answer:** The Porter Stemmer is a rule-based algorithm that reduces words to their root form (stem):
- "running" → "run", "runner" → "run", "runs" → "run"
- "dreaming" → "dream", "dreamer" → "dream"

**Why stem in TryMov:** Without stemming, "science" and "scientist" would be treated as entirely different features, even though they share thematic meaning. Stemming reduces vocabulary size and improves recall — a movie tagged "scientists" will match queries about "science fiction" more effectively.

**Trade-off:** Stemming is aggressive and can produce non-words ("universities" → "univers"). Lemmatization (e.g., via NLTK WordNetLemmatizer) is more linguistically accurate but slower.

**Q5. How does `difflib.get_close_matches` work?**

**Answer:** It uses the Ratcliff/Obershelp algorithm (also called gestalt pattern matching). It computes the number of matching characters between two strings divided by the total characters in both strings. For `find_movie("Iception", cutoff=0.6)`:

1. Compare "iception" to every title in the dataset.
2. The Ratcliff ratio for "iception" vs "inception" ≈ 0.88.
3. Since 0.88 > 0.60 cutoff, "inception" is returned.

**`cutoff=0.6` meaning:** Only return matches where the ratio exceeds 0.6. Lower values return more (potentially wrong) matches; higher values are more strict.

---

## Part B: Android / Kotlin

**Q6. What is Jetpack Compose and how does it differ from the traditional View system?**

**Answer:** Jetpack Compose is Android's modern, declarative UI toolkit. Key differences:

| Aspect | Traditional Views (XML + View) | Jetpack Compose |
|---|---|---|
| **Programming model** | Imperative: mutate views via `setText()`, `setVisibility()` | Declarative: describe what the UI should look like; framework handles mutations |
| **State** | Managed manually; UI and state frequently desync | State drives UI; Compose automatically recomposes when state changes |
| **XML** | Required for layout | No XML; UI is pure Kotlin functions annotated with `@Composable` |
| **Interop** | N/A | Full interop with existing View-based code |
| **Preview** | Layout editor | `@Preview` annotation renders in IDE |

In TryMov: `FirstScreen.kt` and `MyListScreen.kt` are `@Composable` functions. When `_recommendations` StateFlow emits a new value, Compose automatically recomposes only the affected UI nodes.

**Q7. Explain StateFlow vs LiveData. Why does TryMov use StateFlow?**

**Answer:**

| Feature | StateFlow | LiveData |
|---|---|---|
| **Kotlin-native** | Yes (kotlinx.coroutines) | No (Android-specific) |
| **Initial value** | Required | Optional (can be null) |
| **Lifecycle awareness** | Manual (via `collectAsState` in Compose) | Built-in lifecycle awareness |
| **Cold vs Hot** | Hot (always has a value) | Hot |
| **Backpressure** | Drops intermediate values (conflated) | Dispatches every value |

**Why StateFlow in TryMov:** Compose has first-class support for StateFlow via `collectAsState()`. LiveData is a legacy API tied to the Android `Lifecycle` class. For new Kotlin + Compose projects, StateFlow is the idiomatic choice. It also works outside of Android contexts (unit testing without Android environment).

**Q8. What is `viewModelScope.launch` and why is it used instead of `GlobalScope`?**

**Answer:** `viewModelScope` is a `CoroutineScope` tied to the ViewModel's lifecycle. When the ViewModel is cleared (e.g., the user navigates away), `viewModelScope` automatically cancels all coroutines launched within it.

`GlobalScope.launch` creates a coroutine that lives as long as the process. If used for network calls:
- The call continues even after the user navigates away — wasted resources.
- The callback may try to update UI state that no longer exists — potential crash.

In TryMov's `ViewModel.kt`:
```kotlin
fun getRecommendations(movie: String) {
    viewModelScope.launch {         // tied to ViewModel lifecycle
        val res = ApiClient.api.recommend(movie)   // suspending call
        _recommendations.value = res.recommendations
    }
}
```

If the user backs out while the request is in-flight, the ViewModel is cleared, `viewModelScope` is cancelled, and the coroutine stops cleanly.

**Q9. Explain Room's `@TypeConverter`. Why is it needed in TryMov?**

**Answer:** SQLite supports only primitive types: INTEGER, REAL, TEXT, BLOB. Room's `@TypeConverter` provides a mechanism to serialize/deserialize custom types to SQLite-compatible types.

In TryMov, two converters are needed:

1. **`WatchStatus` (enum) ↔ String:** Room cannot store enums natively. `AppTypeConverters` converts `WatchStatus.COMPLETED` → `"COMPLETED"` for storage and `"COMPLETED"` → `WatchStatus.COMPLETED` on read.

2. **`List<String>` ↔ String (JSON):** The `genres` field on `MovieEntity` is `List<String>`. Room cannot store lists. The converter serializes `["Action", "Drama"]` → `"[\"Action\",\"Drama\"]"` (JSON string) using Gson.

Without `@TypeConverter`, Room would throw a compile-time error: "Cannot figure out how to save this field into database."

**Q10. What is the difference between `@Insert(onConflict = OnConflictStrategy.REPLACE)` and `@Update`?**

**Answer:**

- `@Update`: Uses SQL `UPDATE`. The row with the matching primary key is updated. If no row exists, **nothing happens** (0 rows affected).
- `@Insert(onConflict = REPLACE)`: Uses SQL `INSERT OR REPLACE`. If a row with the same primary key exists, it is first **deleted**, then the new row is **inserted**. Functionally equivalent to upsert.

In TryMov, `movieDao.upsert(movie)` uses `REPLACE` because:
- The first time a movie is fetched from TMDB, it's inserted.
- If TMDB data is re-fetched (e.g., after a cache miss), the new data replaces the old.
- Using `@Update` alone would fail on first insert.

**Subtle difference:** `REPLACE` deletes the old row before inserting. This resets any fields not in the new object and can cascade-delete child rows if foreign key constraints exist. In TryMov this isn't an issue because `movies` has no child rows with ON DELETE CASCADE.

**Q11. What is the MVVM pattern and how is it applied in TryMov?**

**Answer:** MVVM (Model-View-ViewModel) separates concerns into three layers:

- **Model:** Business data and logic. In TryMov: `MovieRepository`, `MovieDao`, `MyListDao`, `AppDatabase`.
- **View:** UI layer. In TryMov: `FirstScreen.kt`, `MyListScreen.kt`.
- **ViewModel:** Mediates between View and Model. In TryMov: `MovieViewModel`, `MyListViewModel`. Exposes state as `StateFlow`; the View observes and renders.

**Key benefits applied in TryMov:**
1. `FirstScreen.kt` has zero network code — it only calls ViewModel functions and reads StateFlow.
2. `MovieViewModel` has zero UI code (no Compose imports).
3. ViewModels survive configuration changes (screen rotation), so the in-flight network call is not cancelled and re-started.

**Q12. Explain Retrofit's `@GET`, `@Path`, and `@Query` annotations.**

**Answer:**

- `@GET("/items/{item}")`: Declares an HTTP GET request. The string is the URL path relative to `BASE_URL`.
- `@Path("item")`: Replaces `{item}` in the path with the function argument value. E.g., `recommend("inception")` → `GET /items/inception`.
- `@Query("t")`: Appends a query parameter. E.g., `getByTitle(apiKey="abc", title="inception")` → `GET /?apikey=abc&t=inception`.

**When to use `@Path` vs `@Query`:**
- Use `@Path` for resource identifiers that are part of the resource hierarchy: `/users/{userId}/posts`.
- Use `@Query` for optional filters, parameters, or API keys: `?sort=asc&limit=10`.

In TryMov, `item` uses `@Path` (it identifies which movie resource to retrieve). The OMDb API uses `@Query` because the API is designed around query parameters.

---

## Part C: System Design

**Q13. How would you scale the TryMov backend to 100,000 requests per day?**

**Answer:** The current architecture (single Uvicorn process, ngrok) cannot handle this. The upgrade path:

1. **Precompute the similarity matrix:** Save `cs` and `dataset` to disk with `numpy.save('cs.npy', cs)` and `dataset.to_pickle('dataset.pkl')`. On startup, load in ~1s instead of recomputing in ~15s.

2. **Deploy with multiple workers:** `uvicorn main:app --workers 4`. Each worker loads its own copy of the matrix. This increases concurrency at the cost of ~4× RAM.

3. **Add a CDN / API gateway:** Put the FastAPI behind nginx or an AWS API Gateway. Add rate limiting per IP to prevent abuse.

4. **Cache popular queries:** Add Redis. `GET /items/inception` is likely called frequently. Cache `{"movie": "inception", "recommendations": [...]}` with a 24-hour TTL. Cache hit rate would be very high for a fixed 4803-movie dataset.

5. **Move to a proper hosting provider:** AWS EC2 (t3.medium: 2 vCPU, 4 GB RAM), Google Cloud Run, or Render standard tier — all provide stable HTTPS URLs unlike ngrok.

6. **OMDb bottleneck:** Route OMDb calls through the backend and cache poster URLs in a database to avoid hitting the 1,000/day free tier limit.

**Q14. The FastAPI backend uses 400 MB of RAM just for the in-memory matrix. How would you reduce this?**

**Answer:** Several strategies:

1. **Use `float32` instead of `float64`:** `cs = cosine_similarity(x).astype(np.float32)`. Halves RAM: 185 MB → 92 MB.

2. **Use sparse TF-IDF matrix:** `tfidf.fit_transform(corpus)` returns a sparse matrix. Don't call `.toarray()`. `cosine_similarity` supports sparse input. Sparse storage uses only non-zero values — typically 95%+ of TF-IDF values are zero, reducing storage by ~20×.

3. **Approximate Nearest Neighbors (ANN):** Instead of exact cosine similarity over all 4803 movies, use `faiss` (Facebook AI Similarity Search) or `annoy`. These index the TF-IDF vectors and answer "top-10 similar" queries in O(log n) time with ~1% accuracy loss and far less RAM.

4. **Precompute and store top-N results:** For each of the 4803 movies, precompute and store the top-10 results in a JSON file or SQLite database at build time. At runtime, lookup is O(1). Total storage: 4803 × 10 entries ≈ trivial.

**Q15. How would you handle the case where the ngrok URL changes every session?**

**Answer:** This is a real operational pain point. Solutions in order of increasing robustness:

1. **Short-term:** Use ngrok's paid "reserved domain" feature — the URL never changes.

2. **Medium-term:** Deploy to a free-tier hosting service (Render, Fly.io, Railway) that provides a stable `*.onrender.com` or `*.fly.dev` URL. No code changes needed on the backend.

3. **Production-grade:** 
   - Register a custom domain (e.g., `api.trymov.com`).
   - Point it to the backend via DNS CNAME.
   - Deploy behind a reverse proxy (nginx) with TLS via Let's Encrypt.
   - The Android `BASE_URL` becomes a permanent constant.

4. **On Android side:** Make `BASE_URL` configurable via a build flavor or remote config (Firebase Remote Config) so it can be updated without a new APK release.

**Q16. The app currently makes 10 OMDb API calls per search (one per recommendation). How would you optimize this?**

**Answer:** This is a N+1 query problem applied to external API calls.

**Option A — Cache in Room:** Add a `posterUrl` column to `MovieEntity`. Before calling OMDb, check if `posterUrl` is already cached. If yes, use the cached value. OMDb is only called once per unique movie title, ever.

**Option B — Batch call (not supported by OMDb):** OMDb does not support batch requests. We're limited to one call per movie.

**Option C — Backend proxy with caching:** Move poster fetching to the FastAPI backend. When `GET /items/inception` is called, the backend also fetches OMDb data for the top-10 recommendations and returns poster URLs in the same response. On the backend, cache these in Redis or a SQLite file with a 7-day TTL. The Android client goes from 10 OMDb calls + 1 FastAPI call → 1 FastAPI call.

**Option D — TMDB posters instead of OMDb:** TMDB already provides poster paths (e.g., `/abc123.jpg`) in its movie details response. Construct the full URL as `https://image.tmdb.org/t/p/w500{posterPath}`. This eliminates OMDb entirely for poster fetching. For movies in "My List", TMDB is already used — extend this to the recommendation flow.

---

## Part D: Python / FastAPI

**Q17. Why does FastAPI use `async def` for route handlers? Should TryMov's routes be async?**

**Answer:** FastAPI supports both `def` and `async def` route handlers:

- `async def`: The handler is a coroutine. FastAPI runs it in the same async event loop. Ideal for I/O-bound operations (database calls, HTTP calls using `httpx`/`aiohttp`).
- `def`: FastAPI runs the handler in a **thread pool** to avoid blocking the event loop. Ideal for CPU-bound operations.

**In TryMov:** `read_item` does CPU-intensive work (NumPy sorting, Python list comprehension on `cs[idx]`). It does NOT do I/O (no database calls, no HTTP calls). Therefore, `def` (not `async def`) is actually correct — FastAPI will run it in a thread pool, preventing it from blocking other async handlers.

If TryMov added an async database (e.g., `asyncpg`) or used `httpx` for HTTP calls inside the handler, then `async def` would be appropriate.

**Q18. What happens if two requests hit `recommend_similar` simultaneously?**

**Answer:** In the current implementation, `recommend_similar` is pure read-only: it reads `cs` and `dataset` but never writes to them. Both are module-level variables that are set once at startup and never modified.

In Python, the GIL (Global Interpreter Lock) ensures that only one thread executes Python bytecode at a time. However, since `recommend_similar` only reads (no writes), there is no race condition even with multiple threads. NumPy operations release the GIL, so `cosine_similarity` lookups can run truly in parallel at the C level.

**Conclusion:** The current implementation is thread-safe for concurrent read requests. It would become unsafe only if a background thread were to modify `cs` or `dataset` while another thread reads them (which doesn't happen in TryMov).

**Q19. Explain Pydantic's role in FastAPI. Does TryMov use it?**

**Answer:** Pydantic is FastAPI's validation engine. It:
- Validates request body/query parameter types at runtime.
- Serializes Python objects to JSON responses.
- Generates OpenAPI documentation automatically.

**In TryMov:** The backend does NOT use Pydantic models for requests or responses. The route handler returns plain Python dicts, which FastAPI serializes to JSON automatically. This works but misses benefits:
- No automatic input validation (a malformed `item` path param passes through unchecked).
- No OpenAPI schema generation for the response body.
- No editor autocomplete / type safety for the response structure.

**What it should look like with Pydantic:**
```python
from pydantic import BaseModel
from typing import List

class RecommendationItem(BaseModel):
    title: str
    score: float

class RecommendResponse(BaseModel):
    movie: str
    top_n: int
    recommendations: List[RecommendationItem]

@app.get("/items/{item}", response_model=RecommendResponse)
def read_item(item: str):
    return recommend_similar(find_movie(item))
```

---

## Part E: Design & Architecture

**Q20. Why is TryMov a client-server app rather than running the ML model on-device?**

**Answer:** On-device ML (e.g., TensorFlow Lite) would be possible but impractical here for these reasons:

1. **Model size:** The TF-IDF matrix `x` is ~173 MB and `cs` is ~185 MB. Total ~358 MB of float64 data. Loading this on an Android device would consume a significant portion of available RAM and storage.

2. **Compute at startup:** Recomputing TF-IDF + cosine similarity takes 5–15 seconds on a laptop. On a mobile CPU, this would be 30–120 seconds — unacceptable.

3. **Updatability:** If the movie dataset is updated (new movies added), a server-side model update is invisible to clients. On-device models require APK updates or separate model download flows.

4. **Dataset management:** The 4803-movie CSV is ~5 MB — fine for a server. Including it in the APK would increase download size unnecessarily.

**When on-device makes sense:** If the model is small (< 10 MB), must work offline, or handles private user data that shouldn't leave the device (e.g., a personal health recommendation model).

**Q21. How would you add user accounts and sync "My List" across devices?**

**Answer:** This is a significant architectural change. The approach:

**Step 1 — Add authentication:**
- Integrate Firebase Authentication (Google Sign-In, email/password) or Auth0.
- Each user gets a unique `userId`.

**Step 2 — Add a sync backend:**
- Create a new FastAPI endpoint: `POST /users/{userId}/list`, `GET /users/{userId}/list`, `DELETE /users/{userId}/list/{imdbId}`.
- Use a cloud database (Firebase Firestore, Supabase/PostgreSQL) keyed by `userId`.

**Step 3 — Update Android:**
- `MovieRepository` gains a `syncMyList()` function that pushes local Room data to the backend.
- Add a `WorkManager` periodic sync job that runs every 15 minutes when network is available.
- Conflict resolution strategy: "last write wins" (simplest) or "merge" (complex).

**Step 4 — Offline-first:**
- Room remains the authoritative local cache.
- All writes go to Room first, then sync to backend asynchronously.
- This ensures the app works without network and syncs when connectivity is restored.

**Q22. What would you change in the architecture if TryMov needed to support 1 million users?**

**Answer:** At 1M users, the architecture changes fundamentally:

1. **Recommendation engine becomes a microservice:** Deploy independently with auto-scaling. Use Kubernetes (EKS/GKE) with horizontal pod autoscaler.

2. **Pre-compute all recommendations:** For 4803 movies, pre-compute all top-N lists and store in a Redis cluster. Request latency drops from ~50ms to ~2ms.

3. **Replace ngrok with API Gateway:** AWS API Gateway + ALB in front of FastAPI instances. Add WAF for security.

4. **Add user accounts and server-side "My List":** PostgreSQL (managed via AWS RDS) with read replicas. Room stays as local cache; server is the source of truth.

5. **CDN for posters:** Store movie posters in S3 + CloudFront CDN. Eliminate OMDb API dependency entirely. Cache posters at edge for sub-50ms global delivery.

6. **Add a recommendation personalization layer:** Use collaborative filtering on top of content-based. Feed user interaction events (search, add-to-list, rating) to a streaming pipeline (Kafka → Spark/Flink) to train personalized models.

7. **Observability stack:** Prometheus + Grafana for metrics, ELK for logs, Jaeger for distributed tracing.

---

## Part F: Code Quality & Best Practices

**Q23. What are the biggest code quality issues in TryMov and how would you fix them?**

**Answer:**

| Issue | Location | Fix |
|---|---|---|
| Hardcoded API key | `OmdbRetro.kt` or caller | Move to `local.properties` → `BuildConfig.OMDB_API_KEY` |
| Hardcoded ngrok URL | `Retrofit.kt` | Move to `BuildConfig` field; set per build flavor |
| No tests | Entire codebase | Add unit tests for `MovieRepository` (mock DAOs), ViewModel (test StateFlow) |
| `print(corpus)` in production code | `main.py` | Remove or replace with proper `logging.debug()` |
| No error type distinction | `ViewModel.kt` | Replace `String` status with sealed class: `sealed class UiState { object Loading; data class Error(val message: String); data class Success(...) }` |
| Magic numbers | `main.py` (`4803`, `4500`, `0.6`) | Extract to named constants: `DATASET_SIZE = 4803`, `MAX_FEATURES = 4500` |
| `GlobalScope` risk | N/A (not used) | Maintained correctly — `viewModelScope` is used |
| Logging interceptor in release | `Retrofit.kt`, `OmdbRetro.kt` | Wrap in `if (BuildConfig.DEBUG) addInterceptor(logging)` |

**Q24. How would you write a unit test for `recommend_similar` in `main.py`?**

**Answer:**

```python
import pytest
import pandas as pd
import numpy as np
from unittest.mock import patch

# Assume recommend_similar and dataset are importable
from main import recommend_similar, find_movie

def test_recommend_similar_returns_top_n():
    result = recommend_similar("inception", top_n=5)
    assert "recommendations" in result
    assert len(result["recommendations"]) == 5
    assert result["movie"] == "inception"

def test_recommend_similar_unknown_movie():
    result = recommend_similar("xyzzy_not_a_movie_1234")
    assert "error" in result

def test_find_movie_exact_match():
    assert find_movie("inception") == "inception"

def test_find_movie_fuzzy_match():
    result = find_movie("Iception")  # typo
    assert result == "inception"

def test_find_movie_no_match():
    result = find_movie("zzzzzqqqq")
    assert result is None

def test_scores_are_between_0_and_1():
    result = recommend_similar("inception", top_n=10)
    for rec in result["recommendations"]:
        assert 0.0 <= rec["score"] <= 1.0
```

**Q25. What is a `sealed class` in Kotlin and how would it improve TryMov's ViewModel state management?**

**Answer:** A sealed class in Kotlin is a restricted class hierarchy where all subclasses are known at compile time. This allows `when` expressions to be exhaustive (the compiler enforces handling every case).

**Current TryMov approach (fragile):**
```kotlin
private val _status = MutableStateFlow("idle")
// UI must check: status == "loading", status == "ok", status.startsWith("error")
// Brittle — string comparisons, easy to introduce typos
```

**Improved approach with sealed class:**
```kotlin
sealed class RecommendUiState {
    object Idle : RecommendUiState()
    object Loading : RecommendUiState()
    data class Success(val recommendations: List<Recommendation>) : RecommendUiState()
    data class Error(val message: String) : RecommendUiState()
}

private val _uiState = MutableStateFlow<RecommendUiState>(RecommendUiState.Idle)
val uiState: StateFlow<RecommendUiState> = _uiState
```

**In the Compose UI:**
```kotlin
when (val state = uiState.collectAsState().value) {
    is RecommendUiState.Idle -> IdleView()
    is RecommendUiState.Loading -> SkeletonLoader()
    is RecommendUiState.Success -> RecommendationList(state.recommendations)
    is RecommendUiState.Error -> ErrorView(state.message)
    // Compiler error if a case is missing — exhaustive!
}
```

**Benefits:** Type safety, no string parsing, IDE autocomplete, exhaustive `when` enforced by compiler, `Success` state carries its data directly.

---

*End of Document*

---

**Document Version History**

| Version | Date | Author | Notes |
|---|---|---|---|
| 1.0 | May 2026 | Sourajit Samanta | Initial release |
