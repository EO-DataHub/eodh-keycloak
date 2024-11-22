import logging
import os

from fastapi import FastAPI, Request

logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[logging.StreamHandler()],
)

app = FastAPI()
allowed_user = os.environ.get("ALLOWED_USER", "test")


@app.post("/api/workspaces/authorized")
async def is_authorized(request: Request):
    is_authorized = request.data["user"] == allowed_user
    logging.info(f"User {request.data['user']} is authorized: {is_authorized}")
    return {"authorized": is_authorized}


@app.get("/api/workspaces")
async def get_workspaces():
    logging.info("Getting workspaces")
    if True:
        workspaces = ["workspace1", "workspace2"]
    else:
        workspaces = []
    return {"workspaces": workspaces}
