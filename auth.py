from __future__ import annotations

import os
import logging

import httpx
from fastapi import HTTPException, Security
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from jose import JWTError, jwt

log = logging.getLogger("auth")

COGNITO_REGION = os.environ["COGNITO_REGION"]
COGNITO_USER_POOL_ID = os.environ["COGNITO_USER_POOL_ID"]
COGNITO_APP_CLIENT_ID = os.environ["COGNITO_APP_CLIENT_ID"]

JWKS_URL = (
    f"https://cognito-idp.{COGNITO_REGION}.amazonaws.com/"
    f"{COGNITO_USER_POOL_ID}/.well-known/jwks.json"
)

_bearer = HTTPBearer()
_jwks_cache: dict | None = None


def _jwks() -> dict:
    global _jwks_cache
    if _jwks_cache is None:
        resp = httpx.get(JWKS_URL, timeout=5)
        resp.raise_for_status()
        _jwks_cache = resp.json()
    return _jwks_cache


def get_current_user(
    credentials: HTTPAuthorizationCredentials = Security(_bearer),
) -> str:
    """Verify Cognito ID token, return user sub. Never trust caller-supplied user_id."""
    token = credentials.credentials
    try:
        header = jwt.get_unverified_header(token)
        key = next(
            (k for k in _jwks()["keys"] if k["kid"] == header["kid"]),
            None,
        )
        if key is None:
            raise HTTPException(status_code=401, detail="Public key not found")

        # Skip library-level audience check — validate manually below to
        # handle both ID tokens (aud=client_id) and access tokens (client_id field).
        payload = jwt.decode(
            token,
            key,
            algorithms=["RS256"],
            options={"verify_aud": False, "verify_at_hash": False},
        )

        # Validate audience manually
        aud = payload.get("aud") or payload.get("client_id")
        if isinstance(aud, list):
            aud = aud[0] if aud else None
        log.warning("token aud=%s expected=%s token_use=%s", aud, COGNITO_APP_CLIENT_ID, payload.get("token_use"))
        if aud != COGNITO_APP_CLIENT_ID:
            raise HTTPException(status_code=401, detail=f"Invalid audience: {aud}")

        sub: str | None = payload.get("sub")
        if not sub:
            raise HTTPException(status_code=401, detail="Missing sub in token")
        return sub
    except HTTPException:
        raise
    except JWTError as exc:
        log.error("JWTError: %s", exc)
        raise HTTPException(status_code=401, detail=f"JWT error: {exc}") from exc
    except Exception as exc:
        log.error("Auth error: %s", exc)
        raise HTTPException(status_code=401, detail=f"Auth error: {exc}") from exc
