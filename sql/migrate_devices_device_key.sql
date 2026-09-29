-- 设备稳定身份 + 类型字段；修复机器人新实验室 IP
-- MySQL 8 / 5.7 兼容写法（可重复执行）

USE rail_traffic;

SET @db := DATABASE();

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='devices' AND COLUMN_NAME='device_key');
SET @sql := IF(@exist=0, 'ALTER TABLE devices ADD COLUMN device_key VARCHAR(64) DEFAULT NULL COMMENT ''stable device key'' AFTER device_id', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

SET @exist := (SELECT COUNT(*) FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='devices' AND COLUMN_NAME='device_type');
SET @sql := IF(@exist=0, 'ALTER TABLE devices ADD COLUMN device_type VARCHAR(64) DEFAULT NULL AFTER device_name', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;

UPDATE `devices`
SET `device_key` = `device_name`
WHERE (`device_key` IS NULL OR `device_key` = '')
  AND `device_name` IS NOT NULL
  AND `device_name` <> '';

UPDATE `devices`
SET `device_key` = CONCAT('device-', `device_id`)
WHERE `device_key` IS NULL OR `device_key` = '';

UPDATE `devices` SET `device_type` = 'jetson' WHERE (`device_type` IS NULL OR `device_type` = '') AND LOWER(`device_name`) LIKE '%jetson%';
UPDATE `devices` SET `device_type` = 'raspberry' WHERE (`device_type` IS NULL OR `device_type` = '') AND (LOWER(`device_name`) LIKE '%raspberry%' OR `device_name` LIKE '%树莓%');
UPDATE `devices` SET `device_type` = 'robot' WHERE (`device_type` IS NULL OR `device_type` = '') AND (LOWER(`device_name`) LIKE '%robot%' OR `device_name` LIKE '%机器%');
UPDATE `devices` SET `device_type` = 'device' WHERE `device_type` IS NULL OR `device_type` = '';

-- 机器人搬到新实验室：hzauaiot@192.168.123.41
UPDATE `devices`
SET `ip_address` = '192.168.123.41',
    `device_type` = 'robot',
    `device_key` = IFNULL(NULLIF(`device_key`, ''), 'robot-001')
WHERE `device_name` = 'robot-001'
   OR `device_key` = 'robot-001'
   OR `ip_address` IN ('192.168.124.43', '192.168.123.41');

SET @exist := (SELECT COUNT(*) FROM information_schema.STATISTICS WHERE TABLE_SCHEMA=@db AND TABLE_NAME='devices' AND INDEX_NAME='uk_devices_device_key');
SET @sql := IF(@exist=0, 'ALTER TABLE devices ADD UNIQUE KEY uk_devices_device_key (device_key)', 'SELECT 1');
PREPARE stmt FROM @sql; EXECUTE stmt; DEALLOCATE PREPARE stmt;