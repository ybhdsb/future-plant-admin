-- 为所有设备写入实验室固定坐标（华中农业大学实验基地）
UPDATE `devices`
SET
  `latituded` = '30.475800',
  `longituded` = '114.353600'
WHERE `latituded` IS NULL
   OR `longituded` IS NULL
   OR TRIM(`latituded`) = ''
   OR TRIM(`longituded`) = '';
