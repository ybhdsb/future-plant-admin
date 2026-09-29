# 未来植物边缘模拟器

用于在没有真实 RS485 / 执行器时，验证平台 MQTT 上下行：

- 上报：`plant/telemetry/{deviceKey}`
- 收令：`plant/cmd/{deviceKey}`
- 回执：`plant/ack/{deviceKey}`

```bash
pip install paho-mqtt
python scripts/plant-edge/mock_plant_edge.py --broker localhost --device-key plant-ctrl-01
```

平台侧请将「未来植物 → 系统总览」中的 **Mock** 关闭，即可改走真实 MQTT。
