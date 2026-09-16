#!/usr/bin/env python3
import os
import sys
import subprocess
import re
import json
import shutil
from datetime import datetime

def main():
    # 1. Paths configuration
    current_dir = os.path.dirname(os.path.abspath(__file__))
    project_root = os.path.dirname(current_dir)
    debug_apk_source = os.path.join(project_root, "app", "build", "outputs", "apk", "debug", "app-debug.apk")
    release_apk_source = os.path.join(project_root, "app", "build", "outputs", "apk", "release", "app-release.apk")
    
    nginx_dir = "/opt/homebrew/var/www/ci-deploy"
    debug_apk_dest = os.path.join(nginx_dir, "CI-Deploy_debug.apk")
    release_apk_dest = os.path.join(nginx_dir, "CI-Deploy_release.apk")
    version_json_dest = os.path.join(nginx_dir, "ci-deploy-version.json")
    
    print("=== Starting Local Deployment for CI-Deploy ===")
    
    # 2. Run Gradle Build
    print("Building debug APK using Gradle...")
    try:
        # Run ./gradlew assembleDebug and capture stdout to get the generated build number
        process = subprocess.Popen(
            ["./gradlew", "assembleDebug", "assembleRelease"],
            cwd=project_root,
            stdout=subprocess.PIPE,
            stderr=subprocess.STDOUT,
            text=True
        )
        
        build_no = None
        # Stream output in real-time
        for line in process.stdout:
            print(line, end="")
            # Check for build number output
            match = re.search(r"CI-DEPLOY BUILD_NO GENERATED:\s*(\d+)", line)
            if match:
                build_no = int(match.group(1))
                
        process.wait()
        
        if process.returncode != 0:
            print(f"\nError: Gradle build failed with exit code {process.returncode}")
            sys.exit(1)
            
    except Exception as e:
        print(f"\nError running gradle build: {e}")
        sys.exit(1)
        
    # 3. Determine Build Number
    if build_no:
        print(f"\nSuccessfully parsed build number from Gradle: {build_no}")
    else:
        # Fallback to current time if parse failed
        build_no = int(datetime.now().strftime("%Y%m%d%H%M"))
        print(f"\nCould not parse build number from Gradle output. Using fallback: {build_no}")
        
    # 4. Create target directory
    print(f"Ensuring target directory exists: {nginx_dir}")
    try:
        os.makedirs(nginx_dir, exist_ok=True)
    except Exception as e:
        print(f"Error creating target directory: {e}")
        sys.exit(1)
        
    # 5. Copy APK
    if not os.path.exists(debug_apk_source) or not os.path.exists(release_apk_source):
        print("Error: Debug or release APK file was not generated")
        sys.exit(1)
        
    print(f"Copying APK to deployment folder...")
    try:
        shutil.copy2(debug_apk_source, debug_apk_dest)
        shutil.copy2(release_apk_source, release_apk_dest)
        print(f"Debug APK deployed to: {debug_apk_dest}")
        print(f"OTA release APK deployed to: {release_apk_dest}")
    except Exception as e:
        print(f"Error copying APK: {e}")
        sys.exit(1)
        
    # 6. Generate/Update version JSON
    # When deploying local, the APK we just built has the buildNo we parsed.
    # To force the update detection on testing devices (even the device with the exact same build),
    # or for normal update testing, we can write the exact build_no.
    # If the user wants to force update check, they can also increment it manually.
    version_data = {
        "buildNo": build_no,
        "version": "1.0.1",
        "buildNote": "Add Logcat Viewer screen and local logging support",
        "url": "/ci-deploy/CI-Deploy_release.apk",
        "sha256": __import__("hashlib").sha256(open(release_apk_dest, "rb").read()).hexdigest(),
        "sizeBytes": os.path.getsize(release_apk_dest),
        "packageName": "hdisoft.app.cideploy"
    }
    
    print(f"Writing updated version JSON to: {version_json_dest}")
    try:
        with open(version_json_dest, "w", encoding="utf-8") as f:
            json.dump(version_data, f, indent=2, ensure_ascii=False)
        print("Version JSON successfully updated!")
    except Exception as e:
        print(f"Error writing version JSON: {e}")
        sys.exit(1)
        
    print("\n=== Local Deployment Completed Successfully! ===")

if __name__ == "__main__":
    main()
