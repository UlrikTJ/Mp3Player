# MP3 Player Backend Service Setup Guide

This folder contains the FastAPI Python backend that runs on your home server (Ubuntu Server) and handles:
1. YouTube music searching (`yt-dlp`).
2. Fetching direct audio streaming URLs for in-app preview/play without downloading.
3. Full audio downloading, MP3 conversion (`ffmpeg`), metadata tagging (`mutagen`), and transmission to your Android phone.

---

## Prerequisites (on Ubuntu Server)

First, make sure `ffmpeg` is installed:
```bash
sudo apt update
sudo apt install -y ffmpeg
```

---

## 1. Installation

1. Copy this `server` folder to your Ubuntu server (e.g., using `scp` or cloning your repository).
2. SSH into your Ubuntu server and navigate to the directory:
   ```bash
   cd path/to/server
   ```
3. Set up a Python virtual environment and install the dependencies:
   ```bash
   python3 -m venv venv
   source venv/bin/activate
   pip install -r requirements.txt
   ```

---

## 2. Running Locally (for testing)

You can run the server directly using Uvicorn to check if everything works:
```bash
source venv/bin/activate
python main.py
```
This starts the backend on port `8000`. You can visit `http://YOUR_SERVER_IP:8000/` in a browser to see if the server status is OK and if `ffmpeg` is detected.

---

## 3. Configuring as a Systemd Service (Auto-Start on Boot)

To make sure the backend runs automatically in the background and restarts if the server reboots:

1. Create a service file:
   ```bash
   sudo nano /etc/systemd/system/mp3player.service
   ```

2. Paste the following configuration, replacing `/path/to/server` with the absolute path where this directory lives:
   ```ini
   [Unit]
   Description=MP3 Player Backend FastAPI Service
   After=network.target

   [Service]
   User=ubuntu
   WorkingDirectory=/path/to/server
   ExecStart=/path/to/server/venv/bin/uvicorn main:app --host 0.0.0.0 --port 8000
   Restart=always
   RestartSec=5

   [Install]
   WantedBy=multi-user.target
   ```

3. Enable and start the service:
   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable mp3player.service
   sudo systemctl start mp3player.service
   ```

4. Check the logs to ensure it started successfully:
   ```bash
   sudo systemctl status mp3player.service
   ```

---

## 4. Tailscale Integration

Since you already have Tailscale running on both your laptop and phone, there's no need to expose ports or configure complex firewalls!

1. Find the Tailscale IP of your Ubuntu server:
   ```bash
   tailscale ip -4
   ```
   *(It will look like `100.x.y.z`)*
2. In your Android app settings, set the backend URL to:
   ```
   http://100.x.y.z:8000
   ```
   This secures the communication channel completely, encrypting traffic and allowing you to access your server whether you are on your dorm WiFi or out on mobile data!
