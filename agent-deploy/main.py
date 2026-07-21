#!/usr/bin/env python3
import json
import os
import re
import shutil
import hashlib
import subprocess
import threading
from datetime import datetime
from pathlib import Path

from fastapi import FastAPI, HTTPException
from fastapi.responses import FileResponse, HTMLResponse

BASE_DIR = Path(__file__).resolve().parent
PROJECTS_FILE = BASE_DIR / "projects.json"
INDEX_FILE = BASE_DIR / "static" / "index.html"

app = FastAPI(title="CI Deploy Agent")

# In-memory build status, keyed by project name. Not persisted across restarts.
build_status: dict[str, dict] = {}
status_lock = threading.Lock()


def load_projects() -> list[dict]:
    with open(PROJECTS_FILE, "r", encoding="utf-8") as f:
        return json.load(f)["projects"]


def get_git_info(source_dir: str, branch: str) -> dict:
    """`git pull` the project's sourceDir then return info about the latest commit."""
    try:
        pull = subprocess.run(
            ["git", "pull", "origin", branch],
            cwd=source_dir,
            capture_output=True,
            text=True,
            timeout=30,
        )
        pull_output = (pull.stdout + pull.stderr).strip()
        if pull.returncode != 0:
            return {"pullOk": False, "error": pull_output or "git pull thất bại"}
    except Exception as e:
        return {"pullOk": False, "error": f"git pull lỗi: {e}"}

    try:
        log = subprocess.run(
            ["git", "log", "-1", "--date=iso-strict", "--format=%H%n%an%n%ad%n%s"],
            cwd=source_dir,
            capture_output=True,
            text=True,
            timeout=10,
        )
        if log.returncode != 0:
            return {"pullOk": True, "pullOutput": pull_output, "error": log.stderr.strip() or "git log thất bại"}
        commit_hash, author, date, subject = (log.stdout.strip("\n").split("\n", 3) + ["", "", "", ""])[:4]
    except Exception as e:
        return {"pullOk": True, "pullOutput": pull_output, "error": f"git log lỗi: {e}"}

    return {
        "pullOk": True,
        "pullOutput": pull_output,
        "commitHash": commit_hash[:8],
        "commitAuthor": author,
        "commitDate": date,
        "commitMessage": subject,
    }


def load_projects_with_git() -> list[dict]:
    projects = load_projects()
    for p in projects:
        p["git"] = get_git_info(p["sourceDir"], p.get("branch", "main"))
    return projects


def get_status(name: str) -> dict:
    with status_lock:
        return build_status.get(
            name, {"state": "idle", "buildNo": None, "log": "", "updatedAt": None}
        )


def set_status(name: str, **fields):
    with status_lock:
        current = build_status.get(
            name, {"state": "idle", "buildNo": None, "log": "", "updatedAt": None}
        )
        current.update(fields)
        current["updatedAt"] = datetime.now().isoformat(timespec="seconds")
        build_status[name] = current


# framework -> (build command, cwd relative to sourceDir, apk path relative to sourceDir)
FRAMEWORK_BUILD_CONFIG = {
    "native": (["./gradlew", "assembleDebug"], ".", "app/build/outputs/apk/debug/app-debug.apk"),
    "reactnative": (["./gradlew", "assembleDebug"], "android", "android/app/build/outputs/apk/debug/app-debug.apk"),
    "flutter": (["flutter", "build", "apk", "--debug"], ".", "build/app/outputs/flutter-apk/app-debug.apk"),
}


def resolve_build(project: dict):
    """Return (cmd, cwd, apk_source, shell) for the project's framework."""
    framework = project.get("framework", "native")
    source_dir = project["sourceDir"]

    if framework == "custom":
        build_command = project.get("buildCommand")
        apk_path = project.get("apkPath")
        if not build_command or not apk_path:
            raise ValueError(
                "framework=custom yêu cầu khai báo 'buildCommand' và 'apkPath' trong projects.json"
            )
        return build_command, source_dir, os.path.join(source_dir, apk_path), True

    if framework not in FRAMEWORK_BUILD_CONFIG:
        raise ValueError(f"framework không hỗ trợ: {framework}")

    cmd, cwd_rel, apk_rel = FRAMEWORK_BUILD_CONFIG[framework]
    return cmd, os.path.join(source_dir, cwd_rel), os.path.join(source_dir, apk_rel), False


def run_build(project: dict):
    name = project["name"]
    dest_dir = project["destDir"]

    set_status(name, state="building", log="")
    log_lines: list[str] = []

    try:
        cmd, cwd, apk_source, shell = resolve_build(project)
    except ValueError as e:
        set_status(name, state="failed", log=str(e))
        return

    try:
        process = subprocess.Popen(
            cmd,
            cwd=cwd,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True,
            shell=shell,
        )
        build_no = None
        for line in process.stdout:
            log_lines.append(line.rstrip("\n"))
            match = re.search(r"CI-DEPLOY BUILD_NO GENERATED:\s*(\d+)", line)
            if match:
                build_no = int(match.group(1))
        process.wait()

        if process.returncode != 0:
            log_text = "\n".join(log_lines[-200:])
            set_status(name, state="failed", log=log_text)
            return

        if not build_no:
            build_no = int(datetime.now().strftime("%Y%m%d%H%M"))

        if not os.path.exists(apk_source):
            set_status(name, state="failed", log=f"APK not found at {apk_source}")
            return

        os.makedirs(dest_dir, exist_ok=True)
        artifact_name = project.get("artifactFileName", f"{name}_debug.apk")
        apk_dest = os.path.join(dest_dir, artifact_name)
        shutil.copy2(apk_source, apk_dest)
        debug_apk_path = project.get("debugApkPath")
        if debug_apk_path:
            debug_source = os.path.join(project["sourceDir"], debug_apk_path)
            if not os.path.exists(debug_source):
                raise FileNotFoundError(f"Debug APK not found at {debug_source}")
            debug_name = project.get("debugArtifactFileName", f"{name}_debug.apk")
            shutil.copy2(debug_source, os.path.join(dest_dir, debug_name))

        version_data = {
            "buildNo": build_no,
            "version": project.get("version", "1.0.0"),
            "buildNote": project.get("buildNote", ""),
            "url": project.get("url", ""),
            "sha256": hashlib.sha256(open(apk_dest, "rb").read()).hexdigest(),
            "sizeBytes": os.path.getsize(apk_dest),
            "packageName": project.get("packageName", ""),
        }
        with open(os.path.join(dest_dir, f"{name}-version.json"), "w", encoding="utf-8") as f:
            json.dump(version_data, f, indent=2, ensure_ascii=False)

        log_text = "\n".join(log_lines[-200:])
        set_status(name, state="success", buildNo=build_no, log=log_text)
    except Exception as e:
        log_text = "\n".join(log_lines[-200:])
        set_status(name, state="failed", log=f"{log_text}\nError: {e}")


@app.get("/", response_class=HTMLResponse)
def index():
    return FileResponse(INDEX_FILE)


@app.get("/api/projects")
def api_projects():
    projects = load_projects_with_git()
    return [{**p, "status": get_status(p["name"])} for p in projects]


@app.post("/api/projects/{name}/build")
def api_build(name: str):
    projects = {p["name"]: p for p in load_projects()}
    if name not in projects:
        raise HTTPException(status_code=404, detail="Project not found")

    if get_status(name)["state"] == "building":
        raise HTTPException(status_code=409, detail="Build already in progress")

    threading.Thread(target=run_build, args=(projects[name],), daemon=True).start()
    return {"state": "building"}


if __name__ == "__main__":
    import uvicorn

    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
