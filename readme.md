📸 Cloud Photo Receiver

A smart Android-to-PC wireless media transfer system built using Android (Java) and Python Flask.
This project allows users to instantly capture photos/videos from their Android device and automatically upload them to a local server dashboard running on a computer.

🚀 Project Overview

This project solves a common real-world problem:

How can we instantly transfer captured photos/videos from a mobile device to a computer without USB cables, manual copying, or cloud storage?

Normally users:

Capture media on phone
Connect USB cable
Manually copy files
Organize them manually

This project automates the entire workflow.

✅ Solution Implemented

You developed a complete end-to-end solution consisting of:

📱 Android Application

Built using:

Java
Android SDK
OkHttp
FileProvider
SharedPreferences

Features:
Capture photos
Record videos
Auto-upload to server
Retry upload support
Save failed uploads to gallery
Dynamic server configuration
Real-time server testing
💻 Python Flask Server

Built using:

Flask
HTML/CSS/JS Dashboard
Pygame audio alerts

Features:

REST API upload endpoint
Media categorization
Real-time dashboard
Live media preview
Upload notifications
Connection monitoring


🏗️ System Architecture
 Android Device
      │
      │ Capture Photo/Video
      ▼
 Local Temporary Storage
      │
      │ Multipart Upload (HTTP POST)
      ▼
 Flask Server (Python)
      │
      ├── Save Photos
      ├── Save Videos
      ├── Save Others
      ▼
 Web Dashboard
      │
      ├── Live Gallery
      ├── Connection Status
      └── Auto Refresh
