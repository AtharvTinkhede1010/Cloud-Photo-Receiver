from flask import Flask, request, jsonify, send_from_directory, render_template_string
import os
from datetime import datetime
import webbrowser
import threading
from playsound import playsound

app = Flask(__name__)

# Base folder
BASE_FOLDER = "CameraUploads"
PHOTO_EXTS = {'.jpg', '.jpeg', '.png', '.gif', '.heic', '.webp'}
VIDEO_EXTS = {'.mp4', '.mov', '.avi', '.mkv', '.3gp'}

# Create base folders if not exist
for folder in ["photos", "videos", "others"]:
    os.makedirs(os.path.join(BASE_FOLDER, folder), exist_ok=True)

# =============================
#  1️⃣ FRONTEND DASHBOARD
# =============================
@app.route('/')
def index():
    return render_template_string(""" <!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<title>📸 Cloud Receiver</title>
<style>
  body { background: #111; color: white; font-family: Arial; text-align: center; margin: 0; padding: 0; }
  h1 { color: #00e676; margin: 15px 0; }
  h2 { color: #fff; margin-top: 20px; }
  .gallery { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; margin: 15px; }
  img, video { width: 200px; height: 200px; object-fit: cover; border-radius: 12px; border: 2px solid #00e676; transition: transform 0.3s ease; }
  img:hover, video:hover { transform: scale(1.05); }
  #status { margin: 10px; font-size: 1.1em; }
</style>
</head>
<body>
  <h1>📸 Cloud Photo Receiver</h1>
  <div id="status">🟠 Checking connection...</div>
  <h2>Photos</h2>
  <div id="photoGallery" class="gallery"></div>
  <h2>Videos</h2>
  <div id="videoGallery" class="gallery"></div>
  <audio id="notifySound" src="https://actions.google.com/sounds/v1/alarms/beep_short.ogg" preload="auto"></audio>

<script>
let prevPhotoCount = 0, prevVideoCount = 0;
async function checkConnection() {
  try {
    const res = await fetch('/list_files');
    if (!res.ok) throw new Error();
    document.getElementById('status').innerText = '🟢 Connected';
  } catch(e) {
    document.getElementById('status').innerText = '🔴 Disconnected';
  }
}

async function loadGallery() {
  const res = await fetch('/list_files');
  const data = await res.json();

  const photoGallery = document.getElementById('photoGallery');
  const videoGallery = document.getElementById('videoGallery');
  photoGallery.innerHTML = '';
  videoGallery.innerHTML = '';

  data.photos.forEach(url => {
    const img = document.createElement('img');
    img.src = url;
    photoGallery.appendChild(img);
  });

  data.videos.forEach(url => {
    const vid = document.createElement('video');
    vid.src = url;
    vid.controls = true;
    videoGallery.appendChild(vid);
  });

  // Play sound if new files arrive
  if (data.photos.length > prevPhotoCount || data.videos.length > prevVideoCount) {
    document.getElementById('notifySound').play();
  }
  prevPhotoCount = data.photos.length;
  prevVideoCount = data.videos.length;
}

setInterval(checkConnection, 4000);
setInterval(loadGallery, 5000);
checkConnection(); loadGallery();
</script>
</body>
</html>
 """)

# =============================
#  2️⃣ UPLOAD ENDPOINT
# =============================


import pygame

def play_sound_async(file):
    def _play():
        try:
            pygame.mixer.init()
            pygame.mixer.music.load(file)
            pygame.mixer.music.play()
        except Exception as e:
            print(f"Audio error: {e}")
    threading.Thread(target=_play, daemon=True).start()



@app.route('/upload', methods=['POST'])
def upload_file():
    if 'file' not in request.files:
        play_sound_async('error.mp3')
        return jsonify({"error": "No file part in request"}), 400

    file = request.files['file']
    if file.filename == '':
        play_sound_async('error.mp3')
        return jsonify({"error": "No selected file"}), 400

    ext = os.path.splitext(file.filename)[1].lower()
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")

    if ext in PHOTO_EXTS:
        subfolder = "photos"
    elif ext in VIDEO_EXTS:
        subfolder = "videos"
    else:
        subfolder = "others"

    filename = f"{timestamp}{ext}"
    save_path = os.path.join(BASE_FOLDER, subfolder, filename)
    file.save(save_path)

    play_sound_async('success.mp3')

    return jsonify({
        "message": f"File {filename} uploaded successfully",
        "category": subfolder,
        "path": save_path
    }), 200

# =============================
#  3️⃣ ERROR HANDLER FOR 405
# =============================
@app.errorhandler(405)
def handle_405(e):
    play_sound_async('error.mp3')
    return jsonify({"error": "Method Not Allowed"}), 405

# =============================
#  4️⃣ FILE LIST + SERVE ROUTES
# =============================
@app.route('/list_files')
def list_files():
    photos = [f"/files/photos/{f}" for f in os.listdir(os.path.join(BASE_FOLDER, "photos"))]
    videos = [f"/files/videos/{f}" for f in os.listdir(os.path.join(BASE_FOLDER, "videos"))]
    return jsonify({"photos": photos, "videos": videos})

@app.route('/files/<path:subpath>/<filename>')
def serve_file(subpath, filename):
    folder_path = os.path.join(BASE_FOLDER, subpath)
    return send_from_directory(folder_path, filename)

# =============================
#  5️⃣ AUTO-OPEN BROWSER
# =============================
def open_browser():
    webbrowser.open("http://127.0.0.1:8000/")

if __name__ == '__main__':
    threading.Timer(1.5, open_browser).start()
    app.run(host='0.0.0.0', port=8000, debug=True)
