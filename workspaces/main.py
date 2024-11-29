import logging

from fastapi import FastAPI

logging.basicConfig(
    level=logging.DEBUG,
    format="%(asctime)s - %(name)s - %(levelname)s - %(message)s",
    datefmt="%Y-%m-%d %H:%M:%S",
    handlers=[logging.StreamHandler()],
)

app = FastAPI()


@app.get("/api/workspaces")
async def get_workspaces():
    logging.info("REQUEST: GET /api/workspaces")
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
