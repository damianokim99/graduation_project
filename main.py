# main.py
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.middleware.cors import CORSMiddleware

app = FastAPI()

# CORS 허용 (모바일 테스트 대비)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

connected_users = {}  # websocket: {"name": str, "distance": float}

@app.websocket("/ws")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    try:
        # 첫 메시지로 이름 받기
        join_msg = await websocket.receive_text()  # ex: "JOIN:김동현"
        name = join_msg.replace("JOIN:", "")
        connected_users[websocket] = {"name": name, "distance": 0.0}

        await broadcast_user_list()

        while True:
            msg = await websocket.receive_text()
            # ex: "DISTANCE:2.5"
            if msg.startswith("DISTANCE:"):
                dist = float(msg.replace("DISTANCE:", ""))
                connected_users[websocket]["distance"] = dist
                await broadcast_user_list()
    except WebSocketDisconnect:
        del connected_users[websocket]
        await broadcast_user_list()

async def broadcast_user_list():
    user_list = [
        {"name": data["name"], "distance": data["distance"]}
        for data in connected_users.values()
    ]
    for conn in connected_users:
        await conn.send_json(user_list)
