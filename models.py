from __future__ import annotations

from typing import Optional

from pydantic import BaseModel


class Movie(BaseModel):
    movie_id: str
    title: str
    genres: str
    overview: str


class MovieUpdate(BaseModel):
    title: Optional[str] = None
    genres: Optional[str] = None
    overview: Optional[str] = None


class MovieInteraction(BaseModel):
    movie_id: str
    status: str                   # watching | completed | plan_to_watch
    rating: Optional[int] = None  # 0–10, None means unrated


class InteractionUpdate(BaseModel):
    status: Optional[str] = None
    rating: Optional[int] = None
