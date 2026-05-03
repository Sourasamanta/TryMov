from __future__ import annotations

import difflib
import re
from contextlib import asynccontextmanager
from typing import Any, Optional

import nltk
import pandas as pd
from fastapi import Depends, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from nltk.corpus import stopwords
from nltk.stem.porter import PorterStemmer
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity

import os
import httpx

TMDB_API_KEY = os.environ.get("TMDB_API_KEY", "")
OMDB_API_KEY = os.environ.get("OMDB_API_KEY", "")
TMDB_BASE    = "https://api.themoviedb.org/3"
OMDB_BASE    = "http://www.omdbapi.com"

from auth import get_current_user
from dynamo import (
    delete_interaction,
    delete_movie,
    get_all_movies,
    get_all_users,
    get_interactions_by_user,
    put_interaction,
    put_movie,
    update_interaction,
    update_movie,
)
from models import Movie, MovieInteraction, MovieUpdate, InteractionUpdate

# ── ML state ──────────────────────────────────────────────────────────────────
_ml: dict[str, Any] = {}


def _build_pipeline() -> tuple[pd.DataFrame, Any]:
    nltk.download("stopwords", quiet=True)
    ps = PorterStemmer()
    stop_words = set(stopwords.words("english"))

    df = pd.read_csv("tmdb_5000_movies.csv")
    df["tags"] = (
        (df["keywords"] + " ") + (df["genres"] + " ") + df["overview"]
    ).str.strip()

    corpus: list[str] = []
    for i in range(len(df)):
        review = re.sub(r"[^a-zA-Z]", " ", str(df["tags"].iloc[i]))
        review = re.sub(r"\b(id|name)\b", " ", review)
        tokens = [ps.stem(w) for w in review.lower().split() if w not in stop_words]
        corpus.append(" ".join(tokens))

    tfidf = TfidfVectorizer(max_features=4500, stop_words="english", ngram_range=(1, 2))
    x = tfidf.fit_transform(corpus).toarray()
    cs_matrix = cosine_similarity(x)
    df["title"] = df["title"].str.lower()
    return df, cs_matrix


@asynccontextmanager
async def lifespan(app: FastAPI):
    _ml["dataset"], _ml["cs"] = _build_pipeline()
    yield
    _ml.clear()


# ── App ───────────────────────────────────────────────────────────────────────
app = FastAPI(title="TryMov API", lifespan=lifespan)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)


# ── ML helpers ────────────────────────────────────────────────────────────────
def _find_movie(user_input: str, cutoff: float = 0.6) -> str | None:
    df: pd.DataFrame = _ml["dataset"]
    query = user_input.lower().strip()
    titles = df["title"].tolist()
    if query in titles:
        return query
    matches = difflib.get_close_matches(query, titles, n=1, cutoff=cutoff)
    return matches[0] if matches else None


def _recommend(movie_name: str, top_n: int = 10) -> dict:
    df: pd.DataFrame = _ml["dataset"]
    cs = _ml["cs"]
    if movie_name not in df["title"].values:
        return {"error": "Movie not found in dataset."}
    idx = df[df["title"] == movie_name].index[0]
    sim_scores = sorted(enumerate(cs[idx]), key=lambda x: x[1], reverse=True)[1: top_n + 1]
    return {
        "movie": movie_name,
        "top_n": top_n,
        "recommendations": [
            {"title": df.iloc[i]["title"], "score": round(float(s), 3)}
            for i, s in sim_scores
        ],
    }


# ── Public endpoints ──────────────────────────────────────────────────────────
@app.get("/")
def health():
    return {"status": "ok"}


@app.get("/items/{item}")
def recommend(item: str):
    movie = _find_movie(item)
    if movie is None:
        raise HTTPException(status_code=404, detail="Movie not found")
    return _recommend(movie)


# ── Movies (DynamoDB) ─────────────────────────────────────────────────────────
@app.post("/movies", status_code=201)
def add_movie(movie: Movie, _: str = Depends(get_current_user)):
    put_movie(movie.model_dump())
    return {"message": "Movie stored"}


@app.get("/movies")
def list_movies(_: str = Depends(get_current_user)):
    return get_all_movies()


@app.put("/movies/{movie_id}")
def edit_movie(movie_id: str, body: MovieUpdate, _: str = Depends(get_current_user)):
    updates = {k: v for k, v in body.model_dump().items() if v is not None}
    if not updates:
        raise HTTPException(status_code=400, detail="No fields to update")
    update_movie(movie_id, updates)
    return {"message": "Movie updated"}


@app.delete("/movies/{movie_id}", status_code=204)
def remove_movie(movie_id: str, _: str = Depends(get_current_user)):
    delete_movie(movie_id)


# ── Interactions (DynamoDB) ───────────────────────────────────────────────────
@app.post("/interaction", status_code=201)
def add_interaction(body: MovieInteraction, user_id: str = Depends(get_current_user)):
    if body.status not in ("watching", "completed", "plan_to_watch"):
        raise HTTPException(status_code=400, detail="status must be watching | completed | plan_to_watch")
    if body.rating is not None and not (0 <= body.rating <= 10):
        raise HTTPException(status_code=400, detail="rating must be 0–10")
    put_interaction(user_id, body.movie_id, body.status, body.rating)
    return {"message": "Interaction saved"}


@app.get("/interaction/{user_id}")
def get_interactions(user_id: str, token_user_id: str = Depends(get_current_user)):
    if user_id != token_user_id:
        raise HTTPException(status_code=403, detail="Forbidden")
    return get_interactions_by_user(user_id)


@app.put("/interaction/{movie_id}")
def edit_interaction(movie_id: str, body: InteractionUpdate, user_id: str = Depends(get_current_user)):
    if body.status is not None and body.status not in ("watching", "completed", "plan_to_watch"):
        raise HTTPException(status_code=400, detail="status must be watching | completed | plan_to_watch")
    if body.rating is not None and not (0 <= body.rating <= 10):
        raise HTTPException(status_code=400, detail="rating must be 0–10")
    update_interaction(user_id, movie_id, body.status, body.rating)
    return {"message": "Interaction updated"}


@app.delete("/interaction/{movie_id}", status_code=204)
def remove_interaction(movie_id: str, user_id: str = Depends(get_current_user)):
    delete_interaction(user_id, movie_id)


# ── Users (DynamoDB) ──────────────────────────────────────────────────────────
@app.get("/users")
def list_users(_: str = Depends(get_current_user)):
    return get_all_users()


# ── TMDB proxy ────────────────────────────────────────────────────────────────
@app.get("/tmdb/{imdb_id}")
def tmdb_movie(imdb_id: str, _: str = Depends(get_current_user)):
    if not TMDB_API_KEY:
        raise HTTPException(status_code=503, detail="TMDB API key is not configured on the server")
    try:
        with httpx.Client(timeout=10) as client:
            find = client.get(
                f"{TMDB_BASE}/find/{imdb_id}",
                params={"api_key": TMDB_API_KEY, "external_source": "imdb_id"},
            )
            find.raise_for_status()
            results = find.json().get("movie_results", [])
            if not results:
                raise HTTPException(status_code=404, detail="Movie not found on TMDB")

            tmdb_id = results[0]["id"]
            det = client.get(
                f"{TMDB_BASE}/movie/{tmdb_id}",
                params={"api_key": TMDB_API_KEY},
            )
            det.raise_for_status()
            d = det.json()
    except HTTPException:
        raise
    except httpx.HTTPStatusError as e:
        if e.response.status_code == 401:
            raise HTTPException(status_code=503, detail="TMDB API key is invalid or expired")
        raise HTTPException(status_code=502, detail=f"TMDB returned error {e.response.status_code}")
    except httpx.RequestError:
        raise HTTPException(status_code=504, detail="Could not reach TMDB — network error")

    return {
        "movie_id":    imdb_id,
        "title":       d.get("title", ""),
        "genres":      [g["name"] for g in d.get("genres", [])],
        "overview":    d.get("overview", ""),
        "poster_path": d.get("poster_path") or "",
        "runtime":     d.get("runtime") or 0,
    }


# ── OMDB poster proxy ─────────────────────────────────────────────────────────
@app.get("/poster/{title}")
def omdb_poster(title: str, _: str = Depends(get_current_user)):
    if not OMDB_API_KEY:
        raise HTTPException(status_code=503, detail="OMDB API key is not configured on the server")
    try:
        with httpx.Client(timeout=10) as client:
            resp = client.get(OMDB_BASE, params={"t": title, "apikey": OMDB_API_KEY})
            resp.raise_for_status()
            data = resp.json()
        poster = data.get("Poster")
        return {"poster": poster if poster and poster != "N/A" else None}
    except httpx.HTTPStatusError as e:
        if e.response.status_code == 401:
            raise HTTPException(status_code=503, detail="OMDB API key is invalid or expired")
        raise HTTPException(status_code=502, detail=f"OMDB returned error {e.response.status_code}")
    except httpx.RequestError:
        raise HTTPException(status_code=504, detail="Could not reach OMDB — network error")


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=False)
