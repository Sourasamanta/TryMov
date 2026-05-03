from __future__ import annotations

import boto3
from datetime import datetime, timezone
from boto3.dynamodb.conditions import Key

REGION = "eu-north-1"
_session = boto3.Session(region_name=REGION)
_db = _session.resource("dynamodb")

_movies = _db.Table("Movies")
_interactions = _db.Table("UserMovieInteraction")
_users = _db.Table("Users")


# ── Movies ─────────────────────────────────────────────────────────────────────

def put_movie(item: dict) -> None:
    _movies.put_item(Item=item)


def get_all_movies() -> list[dict]:
    return _movies.scan().get("Items", [])


def update_movie(movie_id: str, updates: dict) -> None:
    if not updates:
        return
    expr_parts, names, values = [], {}, {}
    for i, (k, v) in enumerate(updates.items()):
        placeholder, val_key = f"#f{i}", f":v{i}"
        expr_parts.append(f"{placeholder} = {val_key}")
        names[placeholder] = k
        values[val_key] = v
    _movies.update_item(
        Key={"movie_id": movie_id},
        UpdateExpression="SET " + ", ".join(expr_parts),
        ExpressionAttributeNames=names,
        ExpressionAttributeValues=values,
    )


def delete_movie(movie_id: str) -> None:
    _movies.delete_item(Key={"movie_id": movie_id})


# ── Interactions ───────────────────────────────────────────────────────────────

def put_interaction(user_id: str, movie_id: str, status: str, rating: int | None) -> None:
    item = {
        "user_id": user_id,
        "movie_id": movie_id,
        "status": status,
        "timestamp": datetime.now(timezone.utc).isoformat(),
    }
    if rating is not None:
        item["rating"] = rating
    _interactions.put_item(Item=item)


def get_interactions_by_user(user_id: str) -> list[dict]:
    return _interactions.query(
        KeyConditionExpression=Key("user_id").eq(user_id)
    ).get("Items", [])


def update_interaction(user_id: str, movie_id: str, status: str | None, rating: int | None) -> None:
    expr_parts, names, values = [], {}, {}
    if status is not None:
        expr_parts.append("#s = :s")
        names["#s"] = "status"
        values[":s"] = status
    if rating is not None:
        expr_parts.append("#r = :r")
        names["#r"] = "rating"
        values[":r"] = rating
    expr_parts.append("#ts = :ts")
    names["#ts"] = "timestamp"
    values[":ts"] = datetime.now(timezone.utc).isoformat()
    _interactions.update_item(
        Key={"user_id": user_id, "movie_id": movie_id},
        UpdateExpression="SET " + ", ".join(expr_parts),
        ExpressionAttributeNames=names,
        ExpressionAttributeValues=values,
    )


def delete_interaction(user_id: str, movie_id: str) -> None:
    _interactions.delete_item(Key={"user_id": user_id, "movie_id": movie_id})


# ── Users ──────────────────────────────────────────────────────────────────────

def get_all_users() -> list[dict]:
    return _users.scan().get("Items", [])
