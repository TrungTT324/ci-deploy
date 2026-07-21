#!/usr/bin/env python3
import asyncio
from fastapi import FastAPI, WebSocket, WebSocketDisconnect
from fastapi.responses import HTMLResponse
import uvicorn
import argparse
import sys
import json
import socket
import os
from datetime import datetime

# Initialize FastAPI App
app = FastAPI(title="CI-Deploy Logcat Server")

# Dictionary to manage all connected clients
# Key: websocket, Value: dict metadata (id, type, model, ip, port, connected_at)
connected_clients = {}

# Cache for the last N log lines (to show history to newly connected web viewers)
MAX_HISTORY = 200
log_history = []

# Path to the dashboard.html file
dashboard_file_path = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dashboard.html")

def log_message(level, text):
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]
    print(f"[{timestamp}] [{level}] {text}")

def get_client_id(websocket: WebSocket):
    ip, port = websocket.client.host, websocket.client.port
    return f"{ip}:{port}"

def get_lan_ip():
    s = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    try:
        # Doesn't even have to be reachable
        s.connect(('8.8.8.8', 1))
        ip = s.getsockname()[0]
    except Exception:
        ip = '127.0.0.1'
    finally:
        s.close()
    return ip

async def send_client_list_to_dashboards():
    # Gather client list data
    client_list = []
    for ws, meta in connected_clients.items():
        client_list.append({
            "id": meta["id"],
            "type": meta["type"],
            "model": meta["model"],
            "ip": meta["ip"],
            "port": meta["port"],
            "connected_at": meta["connected_at"]
        })
    
    update_msg = json.dumps({
        "type": "client_list_update",
        "clients": client_list
    })
    
    # Send to all connected dashboards
    dashboards = [ws for ws, meta in connected_clients.items() if meta["type"] == "Dashboard"]
    if dashboards:
        await asyncio.gather(*[ws.send_text(update_msg) for ws in dashboards], return_exceptions=True)

async def broadcast_log(message, sender_websocket):
    # Send log messages to all Web Viewers and Dashboards (optionally), but not back to the sender
    # Also skip sending to Android App producers or Unidentified clients to save bandwidth
    tasks = []
    for ws, meta in connected_clients.items():
        if ws != sender_websocket and meta["type"] in ("Web Viewer", "Dashboard"):
            tasks.append(asyncio.create_task(ws.send_text(message)))
            
    if tasks:
        await asyncio.gather(*[asyncio.shield(t) for t in tasks], return_exceptions=True)

# HTTP Route "/" and "/dashboard" to serve Dashboard UI
@app.get("/")
@app.get("/dashboard", response_class=HTMLResponse)
async def get_dashboard():
    try:
        if os.path.exists(dashboard_file_path):
            with open(dashboard_file_path, "r", encoding="utf-8") as f:
                return HTMLResponse(content=f.read(), status_code=200)
        else:
            return HTMLResponse(content="Error: dashboard.html not found.", status_code=404)
    except Exception as e:
        return HTMLResponse(content=f"Error loading dashboard: {str(e)}", status_code=500)

# WebSocket connection endpoint on "/"
@app.websocket("/")
async def websocket_endpoint(websocket: WebSocket):
    await websocket.accept()
    
    ip = websocket.client.host
    port = websocket.client.port
    client_id = get_client_id(websocket)
    
    # Register client initially as Unknown (waiting for registration message)
    connected_clients[websocket] = {
        "id": client_id,
        "type": "Unknown",
        "model": "Connecting...",
        "ip": ip,
        "port": port,
        "connected_at": datetime.now().strftime("%H:%M:%S")
    }
    
    log_message("SYSTEM", f"Client connected: {client_id} (Total: {len(connected_clients)})")
    await send_client_list_to_dashboards()

    try:
        while True:
            # Wait for messages from client
            message = await websocket.receive_text()
            
            # Check if it's a JSON command (from Dashboard)
            if message.startswith("{") and message.endswith("}"):
                try:
                    data = json.loads(message)
                    action = data.get("action")
                    
                    if action == "disconnect_client":
                        target_id = data.get("target_id")
                        # Find the target websocket and close it
                        target_ws = None
                        for ws, meta in connected_clients.items():
                            if meta["id"] == target_id:
                                target_ws = ws
                                break
                        if target_ws:
                            log_message("SYSTEM", f"Dashboard requested disconnect for client: {target_id}")
                            await target_ws.close(code=1000, reason="Disconnected by Dashboard admin")
                        continue
                        
                    elif action == "send_test_message":
                        target_id = data.get("target_id")
                        msg_text = data.get("message", "Test message from Dashboard")
                        target_ws = None
                        for ws, meta in connected_clients.items():
                            if meta["id"] == target_id:
                                target_ws = ws
                                break
                        if target_ws:
                            await target_ws.send_text(f"[SYSTEM]: {msg_text}")
                        continue
                except json.JSONDecodeError:
                    pass # Not a valid JSON command, treat as normal message

            # Check for protocol registration messages
            if message.startswith("[SYSTEM]: Device ") and "connected as log producer" in message:
                # Extract device model name
                # format: "[SYSTEM]: Device MODEL connected as log producer"
                try:
                    parts = message.split("Device ")
                    if len(parts) > 1:
                        model = parts[1].split(" connected")[0]
                        connected_clients[websocket]["type"] = "Android App"
                        connected_clients[websocket]["model"] = model
                        log_message("SYSTEM", f"Client {client_id} identified as Android App ({model})")
                        await send_client_list_to_dashboards()
                except Exception as e:
                    log_message("ERROR", f"Error parsing device info: {str(e)}")
                    
            elif message == "[SYSTEM]: Register Dashboard":
                connected_clients[websocket]["type"] = "Dashboard"
                connected_clients[websocket]["model"] = "Admin Panel"
                log_message("SYSTEM", f"Client {client_id} identified as Dashboard Console")
                await send_client_list_to_dashboards()
                continue # Don't print registration message as raw log or broadcast it
                
            elif message == "[SYSTEM]: Register Web Viewer":
                connected_clients[websocket]["type"] = "Web Viewer"
                connected_clients[websocket]["model"] = "Browser"
                log_message("SYSTEM", f"Client {client_id} identified as Web Viewer")
                await send_client_list_to_dashboards()
                
                # Send log history to the newly registered Web Viewer client
                if log_history:
                    log_message("SYSTEM", f"Sending {len(log_history)} cached logs to Web Viewer {client_id}")
                    for log_line in log_history:
                        try:
                            await websocket.send_text(log_line)
                        except Exception:
                            break
                continue

            # Print normal logs to console and broadcast
            # But do not print Dashboard-specific control messages or logs from Unknown/Dashboard clients
            if connected_clients[websocket]["type"] not in ("Dashboard", "Unknown"):
                print(message)
                
                # Cache log in history
                log_history.append(message)
                if len(log_history) > MAX_HISTORY:
                    log_history.pop(0)
                    
                # Broadcast log to other clients (Web Viewers, etc.)
                await broadcast_log(message, websocket)
            
    except WebSocketDisconnect:
        log_message("SYSTEM", f"Connection closed: {client_id}")
    except Exception as e:
        log_message("ERROR", f"Error with client {client_id}: {str(e)}")
    finally:
        # Unregister client
        if websocket in connected_clients:
            del connected_clients[websocket]
        log_message("SYSTEM", f"Client disconnected: {client_id} (Total: {len(connected_clients)})")
        await send_client_list_to_dashboards()

def start_server():
    parser = argparse.ArgumentParser(description="CI-Deploy FastAPI Logcat Server")
    parser.add_argument("--host", default="0.0.0.0", help="Host to bind the server to (default: 0.0.0.0)")
    parser.add_argument("--port", type=int, default=8082, help="Port to run the server on (default: 8082)")
    args = parser.parse_args()

    lan_ip = get_lan_ip()
    log_message("SYSTEM", "Starting FastAPI & WebSocket Server on:")
    log_message("SYSTEM", f"  - Dashboard: http://127.0.0.1:{args.port} or http://{lan_ip}:{args.port}")
    log_message("SYSTEM", f"  - WebSocket: ws://127.0.0.1:{args.port} or ws://{lan_ip}:{args.port}")
    log_message("SYSTEM", "Press Ctrl+C to stop the server")

    uvicorn.run(app, host=args.host, port=args.port, log_level="warning")

if __name__ == "__main__":
    try:
        start_server()
    except KeyboardInterrupt:
        log_message("SYSTEM", "Server stopped by user")
        sys.exit(0)
