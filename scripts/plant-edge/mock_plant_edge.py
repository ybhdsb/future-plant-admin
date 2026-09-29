#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""未来植物边缘侧模拟器：周期上报遥测，并订阅控制指令回 ACK。

依赖: pip install paho-mqtt
用法:
  python mock_plant_edge.py --broker localhost --device-key plant-ctrl-01
"""

from __future__ import print_function

import argparse
import json
import random
import time
from datetime import datetime

try:
    import paho.mqtt.client as mqtt
except ImportError:
    raise SystemExit("请先安装: pip install paho-mqtt")


def now_iso():
    return datetime.now().astimezone().isoformat(timespec="milliseconds")


def build_telemetry(device_key):
    return {
        "schemaVersion": "1.0",
        "deviceKey": device_key,
        "msgId": "t-" + str(int(time.time() * 1000)),
        "sampledAt": now_iso(),
        "metrics": [
            {"metric": "air.temperature", "value": round(24 + random.random() * 4, 1), "unit": "°C", "quality": "GOOD"},
            {"metric": "air.humidity", "value": round(50 + random.random() * 20, 1), "unit": "%RH", "quality": "GOOD"},
            {"metric": "nutrient.ph", "value": round(5.8 + random.random() * 0.8, 2), "unit": "", "quality": "GOOD"},
            {"metric": "nutrient.ec", "value": round(1.5 + random.random() * 0.5, 2), "unit": "mS/cm", "quality": "GOOD"},
        ],
        "actuators": [
            {"actuatorId": "pump.water", "on": False},
            {"actuatorId": "led.ch1", "on": True, "brightness": 60},
        ],
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--broker", default="localhost")
    parser.add_argument("--port", type=int, default=1883)
    parser.add_argument("--device-key", default="plant-ctrl-01")
    parser.add_argument("--interval", type=float, default=5.0)
    args = parser.parse_args()

    telemetry_topic = "plant/telemetry/" + args.device_key
    cmd_topic = "plant/cmd/" + args.device_key
    ack_topic = "plant/ack/" + args.device_key

    client = mqtt.Client()

    def on_connect(c, userdata, flags, rc):
        print("connected rc=", rc)
        c.subscribe(cmd_topic)

    def on_message(c, userdata, msg):
        print("CMD <=", msg.topic, msg.payload)
        try:
            payload = json.loads(msg.payload.decode("utf-8"))
        except Exception as e:
            print("bad cmd json", e)
            return
        ack = {
            "schemaVersion": "1.0",
            "deviceKey": args.device_key,
            "commandId": payload.get("commandId"),
            "status": "ACKED",
            "message": "ok",
            "finishedAt": now_iso(),
        }
        c.publish(ack_topic, json.dumps(ack, ensure_ascii=False), qos=1)
        print("ACK =>", ack_topic)

    client.on_connect = on_connect
    client.on_message = on_message
    client.connect(args.broker, args.port, 60)
    client.loop_start()

    try:
        while True:
            body = build_telemetry(args.device_key)
            client.publish(telemetry_topic, json.dumps(body, ensure_ascii=False), qos=1)
            print("TEL =>", telemetry_topic)
            time.sleep(args.interval)
    except KeyboardInterrupt:
        pass
    finally:
        client.loop_stop()
        client.disconnect()


if __name__ == "__main__":
    main()
