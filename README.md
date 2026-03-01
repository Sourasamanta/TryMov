# 🎬 TryMov — Intelligent Movie Discovery (Android + FastAPI)

**TryMov** is a **movie recommendation app** built with **Android (Kotlin + Jetpack Compose)** and a **FastAPI backend** that returns **content-based recommendations** using **TF-IDF + cosine similarity** on the **TMDB 5000 movies dataset**.

The Android app lets users search a movie title, fetches recommendations from the backend, and loads posters using the **OMDb API**.

---

## 🚀 Overview

**TryMov** focuses on:
- Modern UI using **Jetpack Compose (Material 3)**
- Clean UI states: loading, empty state, results list
- Backend recommendations via **TF-IDF Vectorizer + Cosine Similarity**
- Poster fetching via **OMDb API**
- Remote backend access via **ngrok** for easy demo/testing

---

## ✨ Key Features

- 🔎 **Search & Recommend**  
  Enter a movie title (supports fuzzy match) and get top similar recommendations from the backend.

- 🎨 **Modern Compose UI**  
  Branded dark theme, search card + results section, loading skeleton, and empty state UI.

- 🧠 **Content-Based Recommendation Engine**  
  Builds tags from `keywords + genres + overview`, uses TF-IDF vectorization (with n-grams) and cosine similarity.

- 🖼️ **Auto Poster Loading**  
  Uses OMDb `t=title` endpoint and displays poster thumbnails via Coil.

- 🌍 **Backend Exposure via ngrok**  
  Run backend locally and expose it via HTTPS ngrok URL for easy mobile testing.

---

## 🏗️ Project Structure

### Android (Kotlin / Compose)

- `FirstScreen.kt` → UI: search, status, results list, cards, skeleton  
- `TryMovUiColors.kt` → Color system (dark + gold)  
- `ApiClient.kt` → Retrofit client for FastAPI endpoint  
- `OmdbClient.kt` → Retrofit client for OMDb poster lookup  
- `Recommendation.kt` → DTO models from backend  

### Backend (FastAPI / Python)

- `main.py` → FastAPI app exposing:
  - `GET /` → health check
  - `GET /items/{item}` → returns recommended movies
- TF-IDF + cosine similarity pipeline using `tmdb_5000_movies.csv`

---

## 🖼️ Screenshots & Demo

Screenshots and demo are stored here:

`https://github.com/Sourasamanta/ScreenShots/tree/main/TryMov`

### App Screenshots

<p align="center">
  <img src="https://raw.githubusercontent.com/Sourasamanta/ScreenShots/main/TryMov/TryMov1.jpeg" width="240" />
  <img src="https://raw.githubusercontent.com/Sourasamanta/ScreenShots/main/TryMov/TryMov2.jpeg" width="240" />
  <img src="https://raw.githubusercontent.com/Sourasamanta/ScreenShots/main/TryMov/TryMov3.jpeg" width="240" />
</p>

### Demo

<p align="center">
  <img src="https://raw.githubusercontent.com/Sourasamanta/ScreenShots/main/TryMov/TryMovDemo.gif" width="320" />
</p>

---

## 🛠️ Tech Stack

### Android

- **Kotlin**
- **Jetpack Compose (Material 3)**
- **Retrofit + OkHttp**
- **Coroutines**
- **Coil** (poster image loading)

### Backend

- **Python**
- **FastAPI**
- **Uvicorn**
- **Pandas / NumPy**
- **NLTK** (stopwords + stemming)
- **Scikit-learn** (TF-IDF + cosine similarity)

---

## ⚙️ Backend Setup (FastAPI)

### 1️⃣ Create virtual environment & install dependencies

```bash
python -m venv .venv

# Windows:
.venv\Scripts\activate
# Mac/Linux:
source .venv/bin/activate

pip install fastapi uvicorn pandas numpy scikit-learn nltk
```

### 2️⃣ Place dataset

Put `tmdb_5000_movies.csv` in the same folder as `main.py`.

### 3️⃣ Run backend locally

```bash
python -m uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Check in your browser:

- `http://127.0.0.1:8000/` → `{"status":"ok"}`
- `http://127.0.0.1:8000/items/inception`

---

## 🌐 Expose Backend Using ngrok (for Android Testing)

### Step-by-step (recommended)

#### 1. Install ngrok

Download and install ngrok from the official site, then authenticate once:

```bash
ngrok config add-authtoken YOUR_NGROK_TOKEN
```

#### 2. Start your FastAPI server

Make sure this is running:

```bash
python -m uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

#### 3. Start ngrok tunnel

In a new terminal:

```bash
ngrok http 8000
```

You'll get something like:

```text
Forwarding  https://miracle-unwilful-amira.ngrok-free.dev -> http://localhost:8000
```

#### 4. Update Android `BASE_URL`

In your Android Retrofit client (`ApiClient.kt` or equivalent):

```kotlin
private const val BASE_URL = "https://miracle-unwilful-amira.ngrok-free.dev/"
```

**Important:** include the trailing `/`.

#### 5. Test from Android

Now the app can call:

- `GET /` → health  
- `GET /items/{item}` → recommendations  

---

## 🚦 Quick Start: End-to-End Demo

Follow these steps to go from zero to a working Android + FastAPI + ngrok setup.

1️⃣ **Start FastAPI (backend)**

```bash
# (Optional) activate venv first
# Windows: .venv\Scripts\activate
# Mac/Linux: source .venv/bin/activate

python -m uvicorn main:app --reload --host 0.0.0.0 --port 8000
```

Verify:

- `http://127.0.0.1:8000/`  
- `http://127.0.0.1:8000/items/inception`

2️⃣ **Expose backend with ngrok**

```bash
ngrok http 8000
```

Copy the HTTPS URL, e.g. `https://your-subdomain.ngrok-free.dev`

3️⃣ **Update Android base URL**

```kotlin
private const val BASE_URL = "https://your-subdomain.ngrok-free.dev/"
```

- Must be **HTTPS**  
- Must end with `/`  

4️⃣ **Run the Android app**

- Connect device or start emulator  
- Run from Android Studio  
- Search a movie (e.g., `inception`) and verify recommendations + posters  

---

## ✅ Important Android Notes (to avoid common errors)

**Ensure `BASE_URL` has trailing slash**  
Retrofit requires:

```kotlin
private const val BASE_URL = "https://xxxxx.ngrok-free.dev/"
```

**Encode movie titles for the path**  
Your endpoint uses `/items/{item}`.  
If the user searches `"the dark knight"`, spaces will break the URL unless encoded.

**Option A** (keep path param, encode on Android):

```kotlin
val safe = URLEncoder.encode(query, "UTF-8")
api.recommend(safe)
```

**Option B** (recommended): change FastAPI to use query param:

```text
GET /recommend?title=The%20Dark%20Knight
```

**OMDb API key**  
Replace:

```kotlin
apiKey = "YOUR_API_KEY"
```

with a real key (store it in `local.properties` / `BuildConfig`).

---

## 🧪 Example API Response

`GET /items/inception`

```json
{
  "movie": "inception",
  "top_n": 10,
  "recommendations": [
    { "title": "interstellar", "score": 0.812 },
    { "title": "the prestige", "score": 0.771 }
  ]
}
```

---

## 🛣️ Future Improvements

- Add caching for posters and recommendation results  
- Add pagination / "load more"  
- Improve fuzzy matching + handle duplicates by year/imdbId  
- Precompute vectors and persist (faster startup)  
- Add tests (unit + UI)  
- Move from ngrok to deployed backend (Render / Fly.io / AWS)  

---

## 👨‍💻 Author

**Sourajit Samanta**  
Android Developer | Kotlin | Jetpack Compose  

⭐ If you like this project, consider giving it a star!

---
