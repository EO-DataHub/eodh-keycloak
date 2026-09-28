import logging
import os

import jwt
from fastapi import FastAPI, HTTPException, Request

logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[logging.StreamHandler()],
)

ALLOWED_USERS = os.environ.get("ALLOWED_USERS", "").split(",")
KEYCLOAK_JWKS_URL = os.environ.get(
    "KEYCLOAK_JWKS_URL",
    "http://keycloak:8080/realms/test/protocol/openid-connect/certs",
)
# Optional: the token Keycloak's API claims mapper sends is still being built,
# so it may not carry a final audience, and its issuer depends on the hostname
# the user reached Keycloak on. Only enforce these when explicitly configured.
KEYCLOAK_AUDIENCE = os.environ.get("KEYCLOAK_AUDIENCE") or None
KEYCLOAK_ISSUER = os.environ.get("KEYCLOAK_ISSUER") or None

jwks_client = jwt.PyJWKClient(KEYCLOAK_JWKS_URL)

app = FastAPI()


@app.middleware("http")
async def extract_oidc_claims(request: Request, call_next):
    logging.debug("oidc: %s", request.url)
    authorization: str = request.headers.get("Authorization")
    if authorization is None or not authorization.startswith("Bearer "):
        logging.debug("oidc: no or invalid authorization header")
        request.state.token = None
        request.state.claims = {}
    else:
        logging.debug("oidc: found valid authorization header")
        request.state.token = authorization.removeprefix("Bearer ")
        try:
            signing_key = jwks_client.get_signing_key_from_jwt(request.state.token)
            request.state.claims = jwt.decode(
                request.state.token,
                signing_key.key,
                algorithms=["RS256"],
                audience=KEYCLOAK_AUDIENCE,
                issuer=KEYCLOAK_ISSUER,
                options={"verify_aud": KEYCLOAK_AUDIENCE is not None},
            )
            logging.debug("oidc: decoded claims: %s", request.state.claims)
        except jwt.PyJWTError as e:
            logging.error("oidc: failed to verify token: %s", e)
            request.state.claims = {}

    response = await call_next(request)
    return response


@app.get("/api/workspaces")
async def get_workspaces(request: Request):
    logging.info("REQUEST: GET /api/workspaces")

    user = request.state.claims.get("preferred_username")
    if user is None:
        logging.error("no verified user")
        raise HTTPException(status_code=401, detail="Unauthorized")
    if user not in ALLOWED_USERS:
        logging.error("user %s forbidden", user)
        raise HTTPException(status_code=403, detail="Forbidden")

    return {
        "success": 1,
        "data": {
            "workspaces": [
                {
                    "name": "workspace1",
                },
                {
                    "name": "workspace2",
                },
            ]
        },
    }
