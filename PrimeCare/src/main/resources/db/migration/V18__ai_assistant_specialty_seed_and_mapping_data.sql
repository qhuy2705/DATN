-- AI Booking Assistant demo specialties and schedules.
-- Keep this migration additive because existing dev databases may already have V14.

SET @now = NOW(6);
SET @today = CURDATE();

INSERT INTO branches
(code, name_vn, name_en, address_vn, address_en, description_vn, description_en, phone, email, lat, lng, status, created_at, updated_at, version, deleted)
VALUES
('Q1', 'PrimeCare Quận 1', 'PrimeCare District 1', '88 Nguyễn Du, Phường Bến Nghé, Quận 1, TP.HCM', '88 Nguyen Du, Ben Nghe Ward, District 1, HCMC', 'Cơ sở trung tâm với đầy đủ chuyên khoa và dịch vụ cận lâm sàng.', 'Central branch with full specialties and diagnostic services.', '02838221111', 'q1@primecare.vn', 10.7797830, 106.6958450, 'ACTIVE', @now, @now, 0, 0)
ON DUPLICATE KEY UPDATE
  name_vn = VALUES(name_vn),
  name_en = VALUES(name_en),
  address_vn = VALUES(address_vn),
  address_en = VALUES(address_en),
  phone = VALUES(phone),
  email = VALUES(email),
  status = VALUES(status),
  updated_at = @now;

SELECT id INTO @branch_q1 FROM branches WHERE code = 'Q1' LIMIT 1;

INSERT IGNORE INTO branch_sessions
(branch_id, session, start_time, end_time, capacity_override, status, created_at, updated_at, version, deleted)
VALUES
(@branch_q1, 'AM', '08:00:00', '12:00:00', 40, 'ACTIVE', @now, @now, 0, 0),
(@branch_q1, 'PM', '13:30:00', '17:30:00', 36, 'ACTIVE', @now, @now, 0, 0);

INSERT INTO specialties
(code, name_vn, name_en, description_vn, description_en, icon_url, status, default_slot_minutes, buffer_every_n, buffer_minutes, max_per_session, created_at, updated_at, version, deleted)
VALUES
('GASTRO', 'Tiêu hóa', 'Gastroenterology', 'Khám và tư vấn các triệu chứng tiêu hóa như đau dạ dày, đau bụng, trào ngược, tiêu chảy, táo bón và khó tiêu.', 'Consultation for digestive symptoms such as stomach pain, abdominal pain, reflux, diarrhea, constipation and indigestion.', NULL, 'ACTIVE', 30, 4, 10, 16, @now, @now, 0, 0),
('DENTAL', 'Răng Hàm Mặt', 'Dentistry and Oral Medicine', 'Khám răng, nướu, đau răng, sâu răng và các vấn đề răng hàm mặt thường gặp.', 'Dental and oral medicine consultation for toothache, cavities, gum and oral problems.', NULL, 'ACTIVE', 30, 4, 10, 16, @now, @now, 0, 0)
ON DUPLICATE KEY UPDATE
  name_vn = VALUES(name_vn),
  name_en = VALUES(name_en),
  description_vn = VALUES(description_vn),
  description_en = VALUES(description_en),
  status = VALUES(status),
  default_slot_minutes = VALUES(default_slot_minutes),
  updated_at = @now;

SELECT id INTO @sp_gastro FROM specialties WHERE code = 'GASTRO' LIMIT 1;
SELECT id INTO @sp_dental FROM specialties WHERE code = 'DENTAL' LIMIT 1;

INSERT IGNORE INTO branch_specialties
(branch_id, specialty_id, status, display_order, consultation_fee, slot_minutes_override, note, created_at, updated_at, version, deleted)
VALUES
(@branch_q1, @sp_gastro, 'ACTIVE', 5, 300000, NULL, 'Chuyên khoa phục vụ AI Booking Assistant demo', @now, @now, 0, 0),
(@branch_q1, @sp_dental, 'ACTIVE', 6, 260000, NULL, 'Chuyên khoa phục vụ AI Booking Assistant demo', @now, @now, 0, 0);

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
SELECT
'BS.CKI. Nguyễn Hữu Phúc', @branch_q1, 'Bác sĩ Chuyên khoa I Tiêu hóa', 'Specialist Level I, Gastroenterology', 'Bác sĩ khám và tư vấn các vấn đề tiêu hóa thường gặp.', 'Doctor for common digestive symptoms and gastroenterology consultation.', 'Đau dạ dày, trào ngược, rối loạn tiêu hóa.', 'Stomach pain, reflux and digestive disorders.', NULL, NULL, NULL, NULL, 11, NULL, NULL, 'ACTIVE', @now, @now, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM doctor_profiles WHERE full_name = 'BS.CKI. Nguyễn Hữu Phúc' AND branch_id = @branch_q1
);

SELECT id INTO @doc_gastro FROM doctor_profiles
WHERE full_name = 'BS.CKI. Nguyễn Hữu Phúc' AND branch_id = @branch_q1
ORDER BY id ASC
LIMIT 1;

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
SELECT
'BS. Trần Minh Khang', @branch_q1, 'Bác sĩ Răng Hàm Mặt', 'Dentist and Oral Medicine Doctor', 'Bác sĩ khám răng hàm mặt và tư vấn các vấn đề răng nướu.', 'Doctor for dental, gum and oral medicine consultation.', 'Đau răng, sâu răng, viêm nướu.', 'Toothache, cavities and gum inflammation.', NULL, NULL, NULL, NULL, 9, NULL, NULL, 'ACTIVE', @now, @now, 0, 0
WHERE NOT EXISTS (
    SELECT 1 FROM doctor_profiles WHERE full_name = 'BS. Trần Minh Khang' AND branch_id = @branch_q1
);

SELECT id INTO @doc_dental FROM doctor_profiles
WHERE full_name = 'BS. Trần Minh Khang' AND branch_id = @branch_q1
ORDER BY id ASC
LIMIT 1;

INSERT IGNORE INTO doctor_specialties (doctor_id, specialty_id)
VALUES
(@doc_gastro, @sp_gastro),
(@doc_dental, @sp_dental);

INSERT INTO doctor_work_schedules (doctor_id, work_date, session, note)
SELECT @doc_gastro, DATE_ADD(@today, INTERVAL 1 DAY), 'AM', 'AI demo Tiêu hóa'
WHERE NOT EXISTS (
    SELECT 1 FROM doctor_work_schedules
    WHERE doctor_id = @doc_gastro AND work_date = DATE_ADD(@today, INTERVAL 1 DAY) AND session = 'AM'
);

INSERT INTO doctor_work_schedules (doctor_id, work_date, session, note)
SELECT @doc_gastro, DATE_ADD(@today, INTERVAL 2 DAY), 'PM', 'AI demo Tiêu hóa'
WHERE NOT EXISTS (
    SELECT 1 FROM doctor_work_schedules
    WHERE doctor_id = @doc_gastro AND work_date = DATE_ADD(@today, INTERVAL 2 DAY) AND session = 'PM'
);

INSERT INTO doctor_work_schedules (doctor_id, work_date, session, note)
SELECT @doc_dental, DATE_ADD(@today, INTERVAL 1 DAY), 'PM', 'AI demo Răng Hàm Mặt'
WHERE NOT EXISTS (
    SELECT 1 FROM doctor_work_schedules
    WHERE doctor_id = @doc_dental AND work_date = DATE_ADD(@today, INTERVAL 1 DAY) AND session = 'PM'
);

INSERT INTO doctor_work_schedules (doctor_id, work_date, session, note)
SELECT @doc_dental, DATE_ADD(@today, INTERVAL 3 DAY), 'AM', 'AI demo Răng Hàm Mặt'
WHERE NOT EXISTS (
    SELECT 1 FROM doctor_work_schedules
    WHERE doctor_id = @doc_dental AND work_date = DATE_ADD(@today, INTERVAL 3 DAY) AND session = 'AM'
);
