import os
import shutil
import tempfile
import urllib.request
import uuid
from typing import Optional, List
import yt_dlp
from fastapi import FastAPI, HTTPException, BackgroundTasks, Query
from fastapi.responses import FileResponse
from pydantic import BaseModel
from mutagen.mp3 import MP3
from mutagen.id3 import ID3, TIT2, TPE1, TALB, APIC

app = FastAPI(title="YouTube MP3 Downloader API", version="1.0.0")

TEMP_DIR = os.path.join(tempfile.gettempdir(), "mp3player_downloads")
os.makedirs(TEMP_DIR, exist_ok=True)

class SearchResult(BaseModel):
    id: str
    title: str
    uploader: str
    duration: int
    thumbnail: str

class StreamRequest(BaseModel):
    video_id: str

class StreamResponse(BaseModel):
    stream_url: str
    title: str
    duration: int

class DownloadRequest(BaseModel):
    video_id: str
    title: Optional[str] = None
    artist: Optional[str] = None
    album: Optional[str] = "YouTube Downloads"
    thumbnail_url: Optional[str] = None

def cleanup_files(file_paths: List[str]):
    """Background task to delete temporary files after download."""
    for path in file_paths:
        try:
            if os.path.exists(path):
                os.remove(path)
        except Exception as e:
            print(f"Error deleting file {path}: {e}")

@app.get("/")
def read_root():
    return {"status": "running", "ffmpeg_available": shutil.which("ffmpeg") is not None}

@app.get("/search", response_model=List[SearchResult])
def search_youtube(q: str = Query(..., description="Search query")):
    if not q:
        raise HTTPException(status_code=400, detail="Search query cannot be empty")
    
    ydl_opts = {
        'skip_download': True,
        'quiet': True,
        'extract_flat': True,
    }
    
    results = []
    # Search for up to 10 results
    search_query = f"ytsearch10:{q}"
    
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(search_query, download=False)
            if 'entries' in info:
                for entry in info['entries']:
                    if not entry:
                        continue
                    # Extract fields, handling cases where they might be missing
                    video_id = entry.get('id') or entry.get('url')
                    if not video_id:
                        continue
                    
                    # Fetch thumbnail (fallback to default standard YT thumbnails if not present)
                    thumbnail = entry.get('thumbnail')
                    if not thumbnail:
                        thumbnail = f"https://img.youtube.com/vi/{video_id}/0.jpg"
                    
                    results.append(SearchResult(
                        id=video_id,
                        title=entry.get('title') or "Unknown Title",
                        uploader=entry.get('uploader') or entry.get('channel') or "Unknown Uploader",
                        duration=int(entry.get('duration') or 0),
                        thumbnail=thumbnail
                    ))
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Search failed: {str(e)}")
        
    return results

@app.post("/stream", response_model=StreamResponse)
def get_stream_url(request: StreamRequest):
    video_url = f"https://www.youtube.com/watch?v={request.video_id}"
    
    ydl_opts = {
        'format': 'bestaudio/best',
        'skip_download': True,
        'quiet': True,
    }
    
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(video_url, download=False)
            stream_url = info.get('url')
            if not stream_url:
                # Find direct format url from formats
                formats = info.get('formats', [])
                audio_formats = [f for f in formats if f.get('vcodec') == 'none' and f.get('acodec') != 'none']
                if audio_formats:
                    # Pick best audio
                    best = max(audio_formats, key=lambda f: f.get('abr', 0) or 0)
                    stream_url = best.get('url')
            
            if not stream_url:
                raise HTTPException(status_code=404, detail="Direct audio stream URL not found")
                
            return StreamResponse(
                stream_url=stream_url,
                title=info.get('title') or "Unknown Title",
                duration=int(info.get('duration') or 0)
            )
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Failed to get stream URL: {str(e)}")

@app.post("/download")
def download_mp3(request: DownloadRequest, background_tasks: BackgroundTasks):
    video_id = request.video_id
    video_url = f"https://www.youtube.com/watch?v={video_id}"
    
    unique_id = str(uuid.uuid4())
    temp_download_tmpl = os.path.join(TEMP_DIR, f"{unique_id}.%(ext)s")
    expected_mp3_path = os.path.join(TEMP_DIR, f"{unique_id}.mp3")
    
    ydl_opts = {
        'format': 'bestaudio/best',
        'outtmpl': temp_download_tmpl,
        'quiet': True,
        'postprocessors': [{
            'key': 'FFmpegExtractAudio',
            'preferredcodec': 'mp3',
            'preferredquality': '192',
        }],
    }
    
    # 1. Download and convert to MP3
    try:
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(video_url, download=True)
            # Gather fallbacks from video metadata if request fields are empty
            resolved_title = request.title or info.get('title') or "Unknown Song"
            resolved_artist = request.artist or info.get('uploader') or info.get('channel') or "Unknown Artist"
            resolved_thumb_url = request.thumbnail_url
            if not resolved_thumb_url and 'thumbnails' in info:
                thumbnails = info.get('thumbnails', [])
                if thumbnails:
                    resolved_thumb_url = thumbnails[-1].get('url') # Get highest quality
    except Exception as e:
        # Cleanup any stray files matching the uuid
        for f in os.listdir(TEMP_DIR):
            if f.startswith(unique_id):
                try:
                    os.remove(os.path.join(TEMP_DIR, f))
                except:
                    pass
        raise HTTPException(status_code=500, detail=f"YouTube download failed: {str(e)}")

    if not os.path.exists(expected_mp3_path):
        raise HTTPException(status_code=500, detail="Conversion to MP3 failed (file not found)")

    # 2. Download Album Art if available
    temp_cover_path = None
    if resolved_thumb_url:
        try:
            temp_cover_path = os.path.join(TEMP_DIR, f"{unique_id}_cover.jpg")
            urllib.request.urlretrieve(resolved_thumb_url, temp_cover_path)
        except Exception as e:
            print(f"Failed to fetch thumbnail: {e}")
            temp_cover_path = None

    # 3. Add ID3 Metadata
    try:
        audio = MP3(expected_mp3_path, ID3=ID3)
        if audio.tags is None:
            audio.add_tags()
        
        # Add basic info
        audio.tags.add(TIT2(encoding=3, text=resolved_title))
        audio.tags.add(TPE1(encoding=3, text=resolved_artist))
        if request.album:
            audio.tags.add(TALB(encoding=3, text=request.album))
            
        # Add cover art
        if temp_cover_path and os.path.exists(temp_cover_path):
            with open(temp_cover_path, "rb") as f:
                audio.tags.add(
                    APIC(
                        encoding=3,
                        mime='image/jpeg',
                        type=3, # front cover
                        desc='Cover',
                        data=f.read()
                    )
                )
        audio.save()
    except Exception as e:
        print(f"Error tagging MP3 metadata: {e}")
        # We proceed even if tagging fails, so the user still gets the file

    # 4. Schedule cleanup of temp files
    files_to_cleanup = [expected_mp3_path]
    if temp_cover_path:
        files_to_cleanup.append(temp_cover_path)
    background_tasks.add_task(cleanup_files, files_to_cleanup)

    # 5. Serve the file
    safe_filename = "".join(c for c in resolved_title if c.isalnum() or c in (' ', '_', '-')).strip() or "song"
    return FileResponse(
        path=expected_mp3_path,
        media_type="audio/mpeg",
        filename=f"{safe_filename}.mp3"
    )

if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="0.0.0.0", port=8000, reload=True)
