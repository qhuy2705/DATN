-- Complete PrimeCare AI demo coverage for controlled specialty mapping.
-- Additive/idempotent for dev/demo databases that already ran earlier assistant migrations.

SET @now = NOW(6);
SET @today = CURDATE();

SET @branch_q1 = NULL;
SELECT id INTO @branch_q1 FROM branches WHERE code = 'Q1' LIMIT 1;

INSERT INTO specialties
(code, name_vn, name_en, description_vn, description_en, icon_url, status, default_slot_minutes, buffer_every_n, buffer_minutes, max_per_session, created_at, updated_at, version, deleted)
VALUES
('GENERAL', 'Nội tổng quát', 'General Internal Medicine', 'Khám sốt, mệt mỏi, đau đầu, chóng mặt, mất ngủ, suy nhược và khám tổng quát.', 'General internal medicine consultation.', NULL, 'ACTIVE', 30, 4, 10, 16, @now, @now, 0, 0),
('CARDIO', 'Tim mạch', 'Cardiology', 'Khám đau ngực, tức ngực, hồi hộp, tim đập nhanh, huyết áp và khó thở.', 'Cardiology consultation.', NULL, 'ACTIVE', 30, 4, 10, 14, @now, @now, 0, 0),
('DERMA', 'Da liễu', 'Dermatology', 'Khám nổi mẩn, mẩn ngứa, ngứa da, phát ban, dị ứng da, mụn, mề đay và bong tróc da.', 'Dermatology consultation.', NULL, 'ACTIVE', 20, 5, 10, 18, @now, @now, 0, 0),
('PED', 'Nhi khoa', 'Pediatrics', 'Khám và tư vấn sức khỏe cho trẻ em.', 'Pediatric consultation.', NULL, 'ACTIVE', 25, 4, 10, 16, @now, @now, 0, 0),
('EYE', 'Mắt', 'Ophthalmology', 'Khám đau mắt, đỏ mắt, nhìn mờ, khô mắt, cộm mắt, ngứa mắt, mỏi mắt và giảm thị lực.', 'Ophthalmology consultation.', NULL, 'ACTIVE', 20, 5, 10, 18, @now, @now, 0, 0),
('ENT', 'Tai Mũi Họng', 'ENT', 'Khám đau họng, viêm họng, ho nhiều, ho kéo dài, nghẹt mũi, sổ mũi, viêm xoang, ù tai và đau tai.', 'ENT consultation.', NULL, 'ACTIVE', 20, 5, 10, 18, @now, @now, 0, 0),
('ORTHO', 'Chấn thương chỉnh hình', 'Orthopedics', 'Khám đau lưng, đau gối, đau vai gáy, đau cổ, đau khớp, chấn thương, bong gân và trật khớp.', 'Orthopedics consultation.', NULL, 'ACTIVE', 30, 4, 10, 12, @now, @now, 0, 0),
('OBGYN', 'Sản phụ khoa', 'Obstetrics and Gynecology', 'Khám thai, phụ khoa, kinh nguyệt, rong kinh, đau bụng kinh, khí hư và sản phụ khoa.', 'Obstetrics and gynecology consultation.', NULL, 'ACTIVE', 30, 4, 10, 14, @now, @now, 0, 0),
('GASTRO', 'Tiêu hóa', 'Gastroenterology', 'Khám đau dạ dày, đau bụng, trào ngược, đầy hơi, ợ nóng, tiêu chảy, táo bón, buồn nôn, đại tràng và rối loạn tiêu hóa.', 'Gastroenterology consultation.', NULL, 'ACTIVE', 30, 4, 10, 16, @now, @now, 0, 0),
('DENTAL', 'Răng Hàm Mặt', 'Dentistry and Oral Medicine', 'Khám đau răng, sâu răng, ê buốt răng, viêm nướu, chảy máu chân răng, sưng lợi, răng khôn và nhổ răng.', 'Dental and oral medicine consultation.', NULL, 'ACTIVE', 30, 4, 10, 16, @now, @now, 0, 0)
ON DUPLICATE KEY UPDATE
  name_vn = VALUES(name_vn),
  name_en = VALUES(name_en),
  description_vn = VALUES(description_vn),
  description_en = VALUES(description_en),
  status = 'ACTIVE',
  default_slot_minutes = VALUES(default_slot_minutes),
  updated_at = @now;

SET @sp_general = NULL;
SET @sp_cardio = NULL;
SET @sp_derma = NULL;
SET @sp_ped = NULL;
SET @sp_eye = NULL;
SET @sp_ent = NULL;
SET @sp_ortho = NULL;
SELECT id INTO @sp_general FROM specialties WHERE code = 'GENERAL' LIMIT 1;
SELECT id INTO @sp_cardio FROM specialties WHERE code = 'CARDIO' LIMIT 1;
SELECT id INTO @sp_derma FROM specialties WHERE code = 'DERMA' LIMIT 1;
SELECT id INTO @sp_ped FROM specialties WHERE code = 'PED' LIMIT 1;
SELECT id INTO @sp_eye FROM specialties WHERE code = 'EYE' LIMIT 1;
SELECT id INTO @sp_ent FROM specialties WHERE code = 'ENT' LIMIT 1;
SELECT id INTO @sp_ortho FROM specialties WHERE code = 'ORTHO' LIMIT 1;

INSERT IGNORE INTO branch_specialties
(branch_id, specialty_id, status, display_order, consultation_fee, slot_minutes_override, note, created_at, updated_at, version, deleted)
SELECT @branch_q1, specialty_id, 'ACTIVE', display_order, consultation_fee, NULL, 'Chuyên khoa phục vụ PrimeCare AI demo', @now, @now, 0, 0
FROM (
    SELECT @sp_general specialty_id, 1 display_order, 250000 consultation_fee UNION ALL
    SELECT @sp_cardio, 2, 320000 UNION ALL
    SELECT @sp_derma, 3, 280000 UNION ALL
    SELECT @sp_ped, 4, 260000 UNION ALL
    SELECT @sp_eye, 7, 260000 UNION ALL
    SELECT @sp_ent, 8, 260000 UNION ALL
    SELECT @sp_ortho, 9, 300000
) demo_specialties
WHERE @branch_q1 IS NOT NULL AND specialty_id IS NOT NULL;

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
SELECT 'BS. Lê Thu Hà', @branch_q1, 'Bác sĩ Mắt', 'Ophthalmologist', 'Bác sĩ khám các vấn đề về mắt thường gặp.', 'Ophthalmology consultation.', 'Đau mắt, đỏ mắt, nhìn mờ, khô mắt, cộm mắt, giảm thị lực.', 'Eye pain, red eye, blurred vision, dry eye.', NULL, NULL, NULL, NULL, 8, NULL, NULL, 'ACTIVE', @now, @now, 0, 0
WHERE @branch_q1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM doctor_profiles WHERE full_name = 'BS. Lê Thu Hà' AND branch_id = @branch_q1);

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
SELECT 'BS. Phạm Quốc Bảo', @branch_q1, 'Bác sĩ Da liễu', 'Dermatologist', 'Bác sĩ khám các vấn đề da liễu thường gặp.', 'Dermatology consultation.', 'Nổi mẩn, mẩn ngứa, ngứa da, phát ban, dị ứng da, mụn, nổi mề đay.', 'Rash, itching, acne and allergy.', NULL, NULL, NULL, NULL, 10, NULL, NULL, 'ACTIVE', @now, @now, 0, 0
WHERE @branch_q1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM doctor_profiles WHERE full_name = 'BS. Phạm Quốc Bảo' AND branch_id = @branch_q1);

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
SELECT 'BS. Đỗ Minh Anh', @branch_q1, 'Bác sĩ Nhi khoa', 'Pediatrician', 'Bác sĩ khám sức khỏe trẻ em.', 'Pediatric consultation.', 'Sốt ở trẻ em, đau bụng ở trẻ em, ho ở trẻ em, khám nhi tổng quát.', 'Pediatric fever, stomach pain and cough.', NULL, NULL, NULL, NULL, 12, NULL, NULL, 'ACTIVE', @now, @now, 0, 0
WHERE @branch_q1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM doctor_profiles WHERE full_name = 'BS. Đỗ Minh Anh' AND branch_id = @branch_q1);

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
SELECT 'BS. Nguyễn Thị Hương', @branch_q1, 'Bác sĩ Tai Mũi Họng', 'ENT Doctor', 'Bác sĩ khám tai mũi họng.', 'ENT consultation.', 'Đau họng, viêm họng, ho nhiều, ho kéo dài, nghẹt mũi, sổ mũi, viêm xoang, ù tai, đau tai.', 'Sore throat, cough, rhinitis, sinusitis and ear pain.', NULL, NULL, NULL, NULL, 9, NULL, NULL, 'ACTIVE', @now, @now, 0, 0
WHERE @branch_q1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM doctor_profiles WHERE full_name = 'BS. Nguyễn Thị Hương' AND branch_id = @branch_q1);

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
SELECT 'BS. Võ Thành Nam', @branch_q1, 'Bác sĩ Chấn thương chỉnh hình', 'Orthopedics Doctor', 'Bác sĩ khám cơ xương khớp và chấn thương.', 'Orthopedics consultation.', 'Đau lưng, đau gối, đau vai gáy, đau cổ, đau khớp, chấn thương, bong gân, trật khớp.', 'Back pain, knee pain, shoulder-neck pain and injuries.', NULL, NULL, NULL, NULL, 13, NULL, NULL, 'ACTIVE', @now, @now, 0, 0
WHERE @branch_q1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM doctor_profiles WHERE full_name = 'BS. Võ Thành Nam' AND branch_id = @branch_q1);

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
SELECT 'BS. Hoàng Minh Sơn', @branch_q1, 'Bác sĩ Tim mạch', 'Cardiologist', 'Bác sĩ khám tim mạch.', 'Cardiology consultation.', 'Đau ngực, tức ngực, hồi hộp, tim đập nhanh, huyết áp, khó thở.', 'Chest pain, palpitations, blood pressure and shortness of breath.', NULL, NULL, NULL, NULL, 14, NULL, NULL, 'ACTIVE', @now, @now, 0, 0
WHERE @branch_q1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM doctor_profiles WHERE full_name = 'BS. Hoàng Minh Sơn' AND branch_id = @branch_q1);

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
SELECT 'BS. Trần An Bình', @branch_q1, 'Bác sĩ Nội tổng quát', 'General Doctor', 'Bác sĩ khám nội tổng quát.', 'General consultation.', 'Sốt, mệt mỏi, đau đầu, chóng mặt, mất ngủ, kiểm tra sức khỏe, suy nhược.', 'Fever, fatigue, headache, dizziness and general check-up.', NULL, NULL, NULL, NULL, 11, NULL, NULL, 'ACTIVE', @now, @now, 0, 0
WHERE @branch_q1 IS NOT NULL AND NOT EXISTS (SELECT 1 FROM doctor_profiles WHERE full_name = 'BS. Trần An Bình' AND branch_id = @branch_q1);

SET @doc_eye = NULL;
SET @doc_derma = NULL;
SET @doc_ped = NULL;
SET @doc_ent = NULL;
SET @doc_ortho = NULL;
SET @doc_cardio = NULL;
SET @doc_general = NULL;
SELECT id INTO @doc_eye FROM doctor_profiles WHERE full_name = 'BS. Lê Thu Hà' AND branch_id = @branch_q1 LIMIT 1;
SELECT id INTO @doc_derma FROM doctor_profiles WHERE full_name = 'BS. Phạm Quốc Bảo' AND branch_id = @branch_q1 LIMIT 1;
SELECT id INTO @doc_ped FROM doctor_profiles WHERE full_name = 'BS. Đỗ Minh Anh' AND branch_id = @branch_q1 LIMIT 1;
SELECT id INTO @doc_ent FROM doctor_profiles WHERE full_name = 'BS. Nguyễn Thị Hương' AND branch_id = @branch_q1 LIMIT 1;
SELECT id INTO @doc_ortho FROM doctor_profiles WHERE full_name = 'BS. Võ Thành Nam' AND branch_id = @branch_q1 LIMIT 1;
SELECT id INTO @doc_cardio FROM doctor_profiles WHERE full_name = 'BS. Hoàng Minh Sơn' AND branch_id = @branch_q1 LIMIT 1;
SELECT id INTO @doc_general FROM doctor_profiles WHERE full_name = 'BS. Trần An Bình' AND branch_id = @branch_q1 LIMIT 1;

INSERT IGNORE INTO doctor_specialties (doctor_id, specialty_id)
SELECT doctor_id, specialty_id
FROM (
    SELECT @doc_eye doctor_id, @sp_eye specialty_id UNION ALL
    SELECT @doc_derma, @sp_derma UNION ALL
    SELECT @doc_ped, @sp_ped UNION ALL
    SELECT @doc_ent, @sp_ent UNION ALL
    SELECT @doc_ortho, @sp_ortho UNION ALL
    SELECT @doc_cardio, @sp_cardio UNION ALL
    SELECT @doc_general, @sp_general
) mappings
WHERE doctor_id IS NOT NULL AND specialty_id IS NOT NULL;

INSERT INTO doctor_work_schedules (doctor_id, work_date, session, note)
SELECT demo_doctors.doctor_id, DATE_ADD(@today, INTERVAL demo_days.day_offset DAY), demo_days.session, demo_doctors.note
FROM (
    SELECT @doc_eye doctor_id, 'PrimeCare AI demo Mắt' note UNION ALL
    SELECT @doc_derma, 'PrimeCare AI demo Da liễu' UNION ALL
    SELECT @doc_ped, 'PrimeCare AI demo Nhi khoa' UNION ALL
    SELECT @doc_ent, 'PrimeCare AI demo Tai Mũi Họng' UNION ALL
    SELECT @doc_ortho, 'PrimeCare AI demo Cơ xương khớp' UNION ALL
    SELECT @doc_cardio, 'PrimeCare AI demo Tim mạch' UNION ALL
    SELECT @doc_general, 'PrimeCare AI demo Nội tổng quát'
) demo_doctors
JOIN (
    SELECT 1 day_offset, 'AM' session UNION ALL
    SELECT 1, 'PM' UNION ALL
    SELECT 8, 'AM' UNION ALL
    SELECT 8, 'PM' UNION ALL
    SELECT 15, 'AM' UNION ALL
    SELECT 15, 'PM' UNION ALL
    SELECT 22, 'AM' UNION ALL
    SELECT 22, 'PM' UNION ALL
    SELECT 29, 'AM' UNION ALL
    SELECT 29, 'PM' UNION ALL
    SELECT 36, 'AM' UNION ALL
    SELECT 36, 'PM' UNION ALL
    SELECT 43, 'AM' UNION ALL
    SELECT 43, 'PM' UNION ALL
    SELECT 50, 'AM' UNION ALL
    SELECT 50, 'PM' UNION ALL
    SELECT 57, 'AM' UNION ALL
    SELECT 57, 'PM'
) demo_days
WHERE demo_doctors.doctor_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1 FROM doctor_work_schedules
      WHERE doctor_id = demo_doctors.doctor_id
        AND work_date = DATE_ADD(@today, INTERVAL demo_days.day_offset DAY)
        AND session = demo_days.session
  );
