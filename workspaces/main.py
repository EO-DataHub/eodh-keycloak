import json
import logging

from fastapi import FastAPI, Request

logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[logging.StreamHandler()],
)

app = FastAPI()


@app.get("/api/workspaces")
async def get_workspaces(request: Request):
    logging.info("REQUEST: GET /api/workspaces")
    logging.info("Headers: %s", json.dumps(dict(request.headers)))
    return {
        "workspaces": [
            {
                "name": "workspace1",
            },
            {
                "name": "workspace2",
            },
        ]
    }
