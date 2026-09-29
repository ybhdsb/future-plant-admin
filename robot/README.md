# Robot Camera Service

This service runs on the robot and provides:

- `GET /capture`: returns one JPEG photo
- `GET /video_feed`: returns an MJPEG live stream
- `GET /health`: health check

The Spring Boot server is configured to call:

- `http://<robot-ip>:5000/capture`
- `http://<robot-ip>:5000/video_feed`

## Deploy on robot

Copy files to the robot:

```bash
scp robot/robot_camera_server.py hzauaiot@192.168.124.43:/home/hzauaiot/pig_project/
scp robot/robot-camera.service hzauaiot@192.168.124.43:/tmp/
```

Install dependencies on the robot:

```bash
sudo apt update
sudo apt install -y python3-flask python3-opencv
```

Install and start the service:

```bash
sudo cp /tmp/robot-camera.service /etc/systemd/system/robot-camera.service
sudo systemctl daemon-reload
sudo systemctl enable robot-camera
sudo systemctl start robot-camera
```

Check status:

```bash
sudo systemctl status robot-camera
journalctl -u robot-camera -f
```

Test from the server:

```bash
curl -v http://192.168.124.43:5000/health
curl -v http://192.168.124.43:5000/capture --output test.jpg
```

If the camera is not `/dev/video0`, change `--camera-index 0` in `robot-camera.service`.
