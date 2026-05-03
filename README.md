# TryMov — Intelligent Movie Discovery

**Android + FastAPI** movie recommendation platform using **TF-IDF cosine similarity**, **AWS Cognito** authentication, **DynamoDB** cloud persistence, and **Jetpack Compose** UI.

<p align="center">
  <img src="https://raw.githubusercontent.com/Sourasamanta/ScreenShots/main/TryMov/TryMov1.jpeg" width="220" />
  <img src="https://raw.githubusercontent.com/Sourasamanta/ScreenShots/main/TryMov/TryMov2.jpeg" width="220" />
  <img src="https://raw.githubusercontent.com/Sourasamanta/ScreenShots/main/TryMov/TryMov3.jpeg" width="220" />
</p>

<p align="center">
  <img src="https://raw.githubusercontent.com/Sourasamanta/ScreenShots/main/TryMov/TryMovDemo.gif" width="300" />
</p>

---

## Features

| Feature | Description |
|---------|-------------|
| **Content-Based Recommendations** | TF-IDF + cosine similarity on 4,803 movies from the TMDB dataset |
| **Fuzzy Search** | Handles typos via `difflib.get_close_matches` |
| **My List** | Add movies by IMDb ID, track status (Watching/Completed/Planned/Dropped), rate 0-10 |
| **Cloud Sync** | Push local list to DynamoDB, pull on login |
| **Authentication** | AWS Cognito OAuth 2.0 with JWT verification |
| **Poster Loading** | OMDb poster lookup proxied through the backend |
| **Offline Support** | Room database for local persistence; My List works without network |

---

## Architecture

```
Android App                    EC2 Backend                  AWS Services
+-----------------+           +------------------+         +---------------+
| Compose UI      |           | FastAPI + Uvicorn|         | Cognito       |
| ViewModels      |  HTTPS    | TF-IDF Engine    |         | (us-east-1)   |
| Room DB         |---------->| TMDB/OMDb Proxy  |-------->| DynamoDB      |
| Cognito OAuth   |  Bearer   | DynamoDB Client  |         | (eu-north-1)  |
+-----------------+  JWT      +------------------+         +---------------+
```

**Pattern:** Fat-client + thin-server. The backend owns ML recommendations and proxies external API calls. The Android client owns UI, local persistence, and authentication.

---

## Tech Stack

### Android
- **Kotlin** + **Jetpack Compose** (Material 3)
- **Room** (local database)
- **Retrofit** + **OkHttp** (networking with JWT interceptor)
- **Coil** (image loading)
- **AppAuth** (Cognito OAuth)
- **Coroutines** + **StateFlow** (reactive data)

### Backend
- **Python 3.12** + **FastAPI** + **Uvicorn**
- **scikit-learn** (TF-IDF vectorizer, cosine similarity)
- **NLTK** (Porter stemmer, stopwords)
- **pandas** (dataset processing)
- **boto3** (DynamoDB)
- **python-jose** (JWT verification)
- **httpx** (TMDB/OMDb proxy calls)

### AWS
- **Cognito** — User pool with OAuth 2.0 (us-east-1)
- **DynamoDB** — Movies, UserMovieInteraction, Users tables (eu-north-1)
- **EC2** — t3.micro running the FastAPI backend (eu-north-1)

---

## Project Structure

```
TryMov/
├── backend/
│   ├── main.py              # FastAPI app, TF-IDF pipeline, all endpoints
│   ├── auth.py              # Cognito JWT verification (JWKS)
│   ├── dynamo.py            # DynamoDB CRUD operations
│   ├── models.py            # Pydantic request/response models
│   ├── requirements.txt     # Python dependencies
│   └── trymov.service       # systemd unit file for EC2
│
├── TryMov/                  # Android project (Gradle)
│   └── app/src/main/java/com/example/trymov/
│       ├── auth/            # Cognito login flow
│       │   ├── CognitoConfig.kt
│       │   ├── LoginActivity.kt
│       │   ├── LoginScreen.kt
│       │   └── TokenManager.kt
│       ├── data/
│       │   ├── local/       # Room database, DAOs, entities
│       │   ├── remote/      # TMDB API client (now proxied)
│       │   └── repository/  # MovieRepository (single source of truth)
│       ├── di/              # Manual dependency injection
│       │   └── AppContainer.kt
│       ├── fastapi/         # Backend API clients
│       │   ├── Retrofit.kt
│       │   ├── MovieAPI.kt
│       │   ├── InteractionApi.kt
│       │   ├── FastApiRepository.kt
│       │   ├── InteractionRepository.kt
│       │   ├── DataModel.kt
│       │   └── InteractionModels.kt
│       ├── model/           # Domain models
│       ├── ui/
│       │   ├── mylist/      # My List screen + ViewModel
│       │   ├── discover/    # Discover screen factory
│       │   └── theme/       # Compose theme
│       ├── FirstScreen.kt   # Discover tab UI
│       ├── MainActivity.kt  # Entry point + navigation
│       ├── ViewModel.kt     # MovieViewModel (discover)
│       └── Colors.kt        # TryMovUiColors
│
├── ARCHITECTURE.md          # Full technical documentation
├── INTERVIEW_QA.md          # Interview preparation Q&A
└── README.md                # This file
```

---

## Setup

### Prerequisites
- Android Studio Ladybug or later
- Python 3.10+
- AWS account (Cognito + DynamoDB + EC2)

### Backend (Local Development)

```bash
# Clone and enter the project
git clone https://github.com/Sourasamanta/TryMov.git
cd TryMov

# Create virtual environment
python -m venv venv
source venv/bin/activate   # Windows: venv\Scripts\activate

# Install dependencies
pip install -r requirements.txt

# Download NLTK data
python -c "import nltk; nltk.download('stopwords')"

# Place the dataset
# Download tmdb_5000_movies.csv from Kaggle and place in project root

# Set environment variables
export COGNITO_REGION=us-east-1
export COGNITO_USER_POOL_ID=<your-pool-id>
export COGNITO_APP_CLIENT_ID=<your-client-id>
export AWS_DEFAULT_REGION=eu-north-1
export TMDB_API_KEY=<your-tmdb-key>
export OMDB_API_KEY=<your-omdb-key>

# Run
uvicorn main:app --host 0.0.0.0 --port 8000
```

### Backend (EC2 Production)

```bash
# Copy files to EC2
scp -i key.pem main.py auth.py dynamo.py models.py requirements.txt ubuntu@<EC2_IP>:/home/ubuntu/trymov/

# SSH in and set up
ssh -i key.pem ubuntu@<EC2_IP>
cd /home/ubuntu/trymov
python3 -m venv venv
source venv/bin/activate
pip install -r requirements.txt

# Copy systemd service
sudo cp trymov.service /etc/systemd/system/
# Edit to add your environment variables:
sudo nano /etc/systemd/system/trymov.service

sudo systemctl daemon-reload
sudo systemctl enable trymov
sudo systemctl start trymov
```

### Android

1. Open the `TryMov/` directory in Android Studio
2. Create `local.properties` with:
   ```properties
   sdk.dir=/path/to/Android/sdk
   EC2_BASE_URL=http://<your-ec2-ip>:8000/
   ```
3. Update `auth/CognitoConfig.kt` with your Cognito settings
4. Build and run on device

---

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| `GET` | `/` | No | Health check |
| `GET` | `/items/{title}` | No | Content-based recommendations |
| `POST` | `/movies` | Yes | Store movie in DynamoDB |
| `GET` | `/movies` | Yes | List all stored movies |
| `PUT` | `/movies/{id}` | Yes | Update movie fields |
| `DELETE` | `/movies/{id}` | Yes | Delete a movie |
| `POST` | `/interaction` | Yes | Save watch status + rating |
| `GET` | `/interaction/{user_id}` | Yes | Get user's interaction history |
| `PUT` | `/interaction/{movie_id}` | Yes | Update interaction |
| `DELETE` | `/interaction/{movie_id}` | Yes | Delete interaction |
| `GET` | `/tmdb/{imdb_id}` | Yes | Proxy: fetch movie from TMDB |
| `GET` | `/poster/{title}` | Yes | Proxy: fetch poster from OMDb |
| `GET` | `/users` | Yes | List all users |

---

## How It Works

1. **User opens app** -> Cognito OAuth login -> ID token stored locally
2. **Discover tab** -> User types movie title -> FastAPI fuzzy-matches against 4,803 titles -> Returns top 10 by cosine similarity -> Posters fetched via OMDb proxy
3. **My List tab** -> User adds movie by IMDb ID -> Backend fetches metadata from TMDB -> Stored in Room (local) + DynamoDB (cloud)
4. **Sync button** -> Pushes all local movies + interactions to DynamoDB
5. **On login** -> Pulls movies + interactions from DynamoDB to local Room DB

---

## DynamoDB Tables

| Table | Partition Key | Sort Key | Attributes |
|-------|--------------|----------|------------|
| `Movies` | `movie_id` (S) | — | title, genres, overview |
| `UserMovieInteraction` | `user_id` (S) | `movie_id` (S) | status, rating, timestamp |
| `Users` | `user_id` (S) | — | name, email, created_at |

---

## Future Improvements

- Collaborative filtering using user interaction data
- Pre-computed similarity matrix (`.npy`) for faster cold starts
- Pagination on `/movies` and `/interaction` endpoints
- Push notifications for new recommendations
- Multi-device sync with conflict resolution
- CI/CD pipeline with GitHub Actions
- ProGuard/R8 obfuscation for release builds
- Unit and integration tests

---

## Author

**Sourajit Samanta**
Android Developer | Kotlin | Jetpack Compose | AWS

---
