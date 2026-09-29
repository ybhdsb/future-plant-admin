import argparse
import threading
import time

import cv2
from flask import Flask, Response, jsonify, send_file


app = Flask(__name__)
camera = None
camera_lock = threading.Lock()
last_frame = None


def open_camera(camera_index):
    cap = cv2.VideoCapture(camera_index)
    if not cap.isOpened():
        raise RuntimeError("无法打开摄像头: {}".format(camera_index))

    cap.set(cv2.CAP_PROP_FRAME_WIDTH, 1280)
    cap.set(cv2.CAP_PROP_FRAME_HEIGHT, 720)
    return cap


def read_frame():
    global last_frame
    with camera_lock:
        ok, frame = camera.read()
        if ok:
            last_frame = frame
            return frame
        if last_frame is not None:
            return last_frame
    return None


@app.route("/health")
def health():
    return jsonify({"status": "ok"})


@app.route("/capture")
def capture():
    frame = read_frame()
    if frame is None:
        return jsonify({"success": False, "message": "camera frame unavailable"}), 500

    path = "/tmp/robot_capture.jpg"
    ok = cv2.imwrite(path, frame)
    if not ok:
        return jsonify({"success": False, "message": "failed to save image"}), 500

    return send_file(path, mimetype="image/jpeg", max_age=0)


@app.route("/video_feed")
def video_feed():
    def generate():
        while True:
            frame = read_frame()
            if frame is None:
                time.sleep(0.1)
                continue

            ok, buffer = cv2.imencode(".jpg", frame, [int(cv2.IMWRITE_JPEG_QUALITY), 80])
            if not ok:
                time.sleep(0.1)
                continue

            yield (
                b"--frame\r\n"
                b"Content-Type: image/jpeg\r\n\r\n" +
                buffer.tobytes() +
                b"\r\n"
            )
            time.sleep(0.04)

    return Response(generate(), mimetype="multipart/x-mixed-replace; boundary=frame")


def parse_args():
    parser = argparse.ArgumentParser(description="Robot camera HTTP service")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=5000)
    parser.add_argument("--camera-index", type=int, default=0)
    return parser.parse_args()


if __name__ == "__main__":
    args = parse_args()
    camera = open_camera(args.camera_index)
    app.run(host=args.host, port=args.port, threaded=True)
