-- Keep PrimeCare AI demo data usable for longer-running dev/demo databases.
-- This migration is additive and relatively idempotent.

SET @now = NOW(6);
SET @today = CURDATE();

INSERT INTO specialties
(code, name_vn, name_en, description_vn, description_en, icon_url, status, default_slot_minutes, buffer_every_n, buffer_minutes, max_per_session, created_at, updated_at, version, deleted)
VALUES
('GENERAL', 'Nội tổng quát', 'General Internal Medicine', 'Khám và tư vấn các triệu chứng nội khoa thường gặp như sốt, mệt mỏi, đau đầu, chóng mặt và khám tổng quát.', 'General internal medicine consultation.', NULL, 'ACTIVE', 30, 4, 10, 16, @now, @now, 0, 0),
('CARDIO', 'Tim mạch', 'Cardiology', 'Khám và tư vấn các triệu chứng liên quan tim mạch như đau ngực, hồi hộp, huyết áp và khó thở.', 'Cardiology consultation.', NULL, 'ACTIVE', 30, 4, 10, 14, @now, @now, 0, 0),
('DERMA', 'Da liễu', 'Dermatology', 'Khám và tư vấn các vấn đề da liễu như nổi mẩn, mẩn ngứa, mụn, phát ban và viêm da.', 'Dermatology consultation.', NULL, 'ACTIVE', 20, 5, 10, 18, @now, @now, 0, 0),
('OBGYN', 'Sản phụ khoa', 'Obstetrics and Gynecology', 'Khám thai, phụ khoa, kinh nguyệt và các vấn đề sản phụ khoa.', 'Obstetrics and gynecology consultation.', NULL, 'ACTIVE', 30, 4, 10, 14, @now, @now, 0, 0),
('PED', 'Nhi khoa', 'Pediatrics', 'Khám và tư vấn sức khỏe cho trẻ em.', 'Pediatric consultation.', NULL, 'ACTIVE', 25, 4, 10, 16, @now, @now, 0, 0),
('EYE', 'Mắt', 'Ophthalmology', 'Khám mắt, đỏ mắt, đau mắt, nhìn mờ, khô mắt và các vấn đề thị lực.', 'Ophthalmology consultation.', NULL, 'ACTIVE', 20, 5, 10, 18, @now, @now, 0, 0),
('ENT', 'Tai Mũi Họng', 'ENT', 'Khám đau họng, viêm họng, nghẹt mũi, sổ mũi, ù tai, đau tai và ho.', 'ENT consultation.', NULL, 'ACTIVE', 20, 5, 10, 18, @now, @now, 0, 0),
('ORTHO', 'Chấn thương chỉnh hình', 'Orthopedics', 'Khám cơ xương khớp, đau lưng, đau gối, đau vai gáy, chấn thương và bong gân.', 'Orthopedics consultation.', NULL, 'ACTIVE', 30, 4, 10, 12, @now, @now, 0, 0),
('GASTRO', 'Tiêu hóa', 'Gastroenterology', 'Khám và tư vấn các triệu chứng tiêu hóa như đau dạ dày, đau bụng, trào ngược, đầy hơi, ợ nóng, tiêu chảy, táo bón và khó tiêu.', 'Gastroenterology consultation.', NULL, 'ACTIVE', 30, 4, 10, 16, @now, @now, 0, 0),
('DENTAL', 'Răng Hàm Mặt', 'Dentistry and Oral Medicine', 'Khám đau răng, sâu răng, nướu, lợi, chảy máu chân răng và ê buốt răng.', 'Dental and oral medicine consultation.', NULL, 'ACTIVE', 30, 4, 10, 16, @now, @now, 0, 0)
ON DUPLICATE KEY UPDATE
  name_vn = VALUES(name_vn),
  name_en = VALUES(name_en),
  description_vn = VALUES(description_vn),
  description_en = VALUES(description_en),
  status = 'ACTIVE',
  default_slot_minutes = VALUES(default_slot_minutes),
  updated_at = @now;

SET @branch_q1 = NULL;
SELECT id INTO @branch_q1 FROM branches WHERE code = 'Q1' LIMIT 1;

SET @sp_gastro = NULL;
SET @sp_dental = NULL;
SELECT id INTO @sp_gastro FROM specialties WHERE code = 'GASTRO' LIMIT 1;
SELECT id INTO @sp_dental FROM specialties WHERE code = 'DENTAL' LIMIT 1;

INSERT IGNORE INTO branch_specialties
(branch_id, specialty_id, status, display_order, consultation_fee, slot_minutes_override, note, created_at, updated_at, version, deleted)
SELECT @branch_q1, @sp_gastro, 'ACTIVE', 5, 300000, NULL, 'Chuyên khoa phục vụ PrimeCare AI demo', @now, @now, 0, 0
WHERE @branch_q1 IS NOT NULL AND @sp_gastro IS NOT NULL;

INSERT IGNORE INTO branch_specialties
(branch_id, specialty_id, status, display_order, consultation_fee, slot_minutes_override, note, created_at, updated_at, version, deleted)
SELECT @branch_q1, @sp_dental, 'ACTIVE', 6, 260000, NULL, 'Chuyên khoa phục vụ PrimeCare AI demo', @now, @now, 0, 0
WHERE @branch_q1 IS NOT NULL AND @sp_dental IS NOT NULL;

SET @doc_gastro = NULL;
SET @doc_dental = NULL;
SELECT id INTO @doc_gastro FROM doctor_profiles
WHERE full_name = 'BS.CKI. Nguyễn Hữu Phúc' AND branch_id = @branch_q1
ORDER BY id ASC
LIMIT 1;
SELECT id INTO @doc_dental FROM doctor_profiles
WHERE full_name = 'BS. Trần Minh Khang' AND branch_id = @branch_q1
ORDER BY id ASC
LIMIT 1;

UPDATE doctor_profiles
SET expertise_vn = 'Dạ dày, trào ngược, đau bụng, rối loạn tiêu hóa, đại tràng, đầy hơi, ợ nóng.',
    bio_vn = 'Bác sĩ khám và tư vấn các vấn đề tiêu hóa thường gặp từ dữ liệu PrimeCare.',
    status = 'ACTIVE',
    updated_at = @now
WHERE id = @doc_gastro;

UPDATE doctor_profiles
SET expertise_vn = 'Đau răng, sâu răng, viêm nướu, ê buốt răng, chảy máu chân răng.',
    bio_vn = 'Bác sĩ khám răng hàm mặt và tư vấn các vấn đề răng nướu thường gặp.',
    status = 'ACTIVE',
    updated_at = @now
WHERE id = @doc_dental;

INSERT IGNORE INTO doctor_specialties (doctor_id, specialty_id)
SELECT @doc_gastro, @sp_gastro
WHERE @doc_gastro IS NOT NULL AND @sp_gastro IS NOT NULL;

INSERT IGNORE INTO doctor_specialties (doctor_id, specialty_id)
SELECT @doc_dental, @sp_dental
WHERE @doc_dental IS NOT NULL AND @sp_dental IS NOT NULL;

INSERT INTO doctor_work_schedules (doctor_id, work_date, session, note)
SELECT @doc_gastro, DATE_ADD(@today, INTERVAL demo.day_offset DAY), demo.session, 'PrimeCare AI demo Tiêu hóa'
FROM (
    SELECT 1 day_offset, 'AM' session UNION ALL
    SELECT 4, 'PM' UNION ALL
    SELECT 8, 'AM' UNION ALL
    SELECT 12, 'PM' UNION ALL
    SELECT 16, 'AM' UNION ALL
    SELECT 20, 'PM' UNION ALL
    SELECT 24, 'AM' UNION ALL
    SELECT 28, 'PM' UNION ALL
    SELECT 32, 'AM' UNION ALL
    SELECT 36, 'PM' UNION ALL
    SELECT 40, 'AM' UNION ALL
    SELECT 44, 'PM' UNION ALL
    SELECT 48, 'AM' UNION ALL
    SELECT 52, 'PM' UNION ALL
    SELECT 56, 'AM' UNION ALL
    SELECT 60, 'PM'
) demo
WHERE @doc_gastro IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM doctor_work_schedules
      WHERE doctor_id = @doc_gastro
        AND work_date = DATE_ADD(@today, INTERVAL demo.day_offset DAY)
        AND session = demo.session
  );

INSERT INTO doctor_work_schedules (doctor_id, work_date, session, note)
SELECT @doc_dental, DATE_ADD(@today, INTERVAL demo.day_offset DAY), demo.session, 'PrimeCare AI demo Răng Hàm Mặt'
FROM (
    SELECT 2 day_offset, 'PM' session UNION ALL
    SELECT 5, 'AM' UNION ALL
    SELECT 9, 'PM' UNION ALL
    SELECT 13, 'AM' UNION ALL
    SELECT 17, 'PM' UNION ALL
    SELECT 21, 'AM' UNION ALL
    SELECT 25, 'PM' UNION ALL
    SELECT 29, 'AM' UNION ALL
    SELECT 33, 'PM' UNION ALL
    SELECT 37, 'AM' UNION ALL
    SELECT 41, 'PM' UNION ALL
    SELECT 45, 'AM' UNION ALL
    SELECT 49, 'PM' UNION ALL
    SELECT 53, 'AM' UNION ALL
    SELECT 57, 'PM' UNION ALL
    SELECT 60, 'AM'
) demo
WHERE @doc_dental IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM doctor_work_schedules
      WHERE doctor_id = @doc_dental
        AND work_date = DATE_ADD(@today, INTERVAL demo.day_offset DAY)
        AND session = demo.session
  );
