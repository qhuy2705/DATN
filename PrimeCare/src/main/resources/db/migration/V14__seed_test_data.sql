-- PrimeCare test database seed
-- Flyway migration: place at src/main/resources/db/migration/V14__seed_test_data.sql
-- Login password for all seeded accounts: password
-- BCrypt hash below matches raw password: password

SET @now = NOW(6);
SET @today = CURDATE();
SET @yesterday = DATE_SUB(@today, INTERVAL 1 DAY);
SET @tomorrow = DATE_ADD(@today, INTERVAL 1 DAY);
SET @pwd = '$2a$10$7EqJtq98hPqEX7fNZaFWoOHiJ9UEdxfCWsQc0gnXkE7bCrsZ.EBC6';

-- -----------------------------------------------------------------------------
-- 1) Master data: branches, sessions, specialties
-- -----------------------------------------------------------------------------
INSERT INTO branches
(code, name_vn, name_en, address_vn, address_en, description_vn, description_en, phone, email, lat, lng, status, created_at, updated_at, version, deleted)
VALUES
('Q1', 'PrimeCare Quận 1', 'PrimeCare District 1', '88 Nguyễn Du, Phường Bến Nghé, Quận 1, TP.HCM', '88 Nguyen Du, Ben Nghe Ward, District 1, HCMC', 'Cơ sở trung tâm với đầy đủ chuyên khoa và dịch vụ cận lâm sàng.', 'Central branch with full specialties and diagnostic services.', '02838221111', 'q1@primecare.vn', 10.7797830, 106.6958450, 'ACTIVE', @now, @now, 0, 0),
('Q7', 'PrimeCare Quận 7', 'PrimeCare District 7', '255 Nguyễn Lương Bằng, Phường Tân Phú, Quận 7, TP.HCM', '255 Nguyen Luong Bang, Tan Phu Ward, District 7, HCMC', 'Chi nhánh phía Nam, thuận tiện cho khu Phú Mỹ Hưng.', 'Southern branch near Phu My Hung area.', '02838222222', 'q7@primecare.vn', 10.7285860, 106.7218710, 'ACTIVE', @now, @now, 0, 0),
('TD', 'PrimeCare Thủ Đức', 'PrimeCare Thu Duc', '15 Võ Văn Ngân, TP. Thủ Đức, TP.HCM', '15 Vo Van Ngan, Thu Duc City, HCMC', 'Chi nhánh mới với khu khám chuyên khoa mắt và tai mũi họng.', 'New branch with ophthalmology and ENT focus.', '02838223333', 'thuduc@primecare.vn', 10.8495660, 106.7716220, 'ACTIVE', @now, @now, 0, 0)
ON DUPLICATE KEY UPDATE
  name_vn = VALUES(name_vn), address_vn = VALUES(address_vn), phone = VALUES(phone), email = VALUES(email), status = VALUES(status), updated_at = @now;

SELECT id INTO @branch_q1 FROM branches WHERE code = 'Q1';
SELECT id INTO @branch_q7 FROM branches WHERE code = 'Q7';
SELECT id INTO @branch_td FROM branches WHERE code = 'TD';

INSERT IGNORE INTO branch_sessions
(branch_id, session, start_time, end_time, capacity_override, status, created_at, updated_at, version, deleted)
VALUES
(@branch_q1, 'AM', '08:00:00', '12:00:00', 40, 'ACTIVE', @now, @now, 0, 0),
(@branch_q1, 'PM', '13:30:00', '17:30:00', 36, 'ACTIVE', @now, @now, 0, 0),
(@branch_q7, 'AM', '08:00:00', '12:00:00', 32, 'ACTIVE', @now, @now, 0, 0),
(@branch_q7, 'PM', '13:30:00', '17:30:00', 28, 'ACTIVE', @now, @now, 0, 0),
(@branch_td, 'AM', '08:00:00', '12:00:00', 24, 'ACTIVE', @now, @now, 0, 0),
(@branch_td, 'PM', '13:30:00', '17:30:00', 22, 'ACTIVE', @now, @now, 0, 0);

INSERT INTO specialties
(code, name_vn, name_en, description_vn, description_en, icon_url, status, default_slot_minutes, buffer_every_n, buffer_minutes, max_per_session, created_at, updated_at, version, deleted)
VALUES
('GENERAL', 'Nội tổng quát', 'General Internal Medicine', 'Khám và điều trị các bệnh nội khoa thông thường.', 'General internal medicine consultation and treatment.', NULL, 'ACTIVE', 30, 4, 10, 16, @now, @now, 0, 0),
('CARDIO', 'Tim mạch', 'Cardiology', 'Chẩn đoán và điều trị bệnh lý tim mạch.', 'Diagnosis and treatment of cardiovascular diseases.', NULL, 'ACTIVE', 30, 4, 10, 14, @now, @now, 0, 0),
('DERMA', 'Da liễu', 'Dermatology', 'Điều trị bệnh lý da, tóc, móng và da liễu thẩm mỹ.', 'Skin, hair, nail and aesthetic dermatology services.', NULL, 'ACTIVE', 20, 5, 10, 18, @now, @now, 0, 0),
('OBGYN', 'Sản phụ khoa', 'Obstetrics and Gynecology', 'Chăm sóc sức khỏe phụ nữ, thai sản và phụ khoa.', 'Women health, maternity and gynecology care.', NULL, 'ACTIVE', 30, 4, 10, 14, @now, @now, 0, 0),
('PED', 'Nhi khoa', 'Pediatrics', 'Khám và điều trị bệnh cho trẻ em.', 'Pediatric consultation and treatment.', NULL, 'ACTIVE', 25, 4, 10, 16, @now, @now, 0, 0),
('EYE', 'Mắt', 'Ophthalmology', 'Khám, đo thị lực và điều trị bệnh về mắt.', 'Eye examination and ophthalmology treatment.', NULL, 'ACTIVE', 20, 5, 10, 18, @now, @now, 0, 0),
('ENT', 'Tai Mũi Họng', 'ENT', 'Điều trị bệnh lý tai, mũi, họng.', 'Ear, nose and throat care.', NULL, 'ACTIVE', 20, 5, 10, 18, @now, @now, 0, 0),
('ORTHO', 'Chấn thương chỉnh hình', 'Orthopedics', 'Điều trị cơ xương khớp và chấn thương.', 'Musculoskeletal and trauma care.', NULL, 'ACTIVE', 30, 4, 10, 12, @now, @now, 0, 0)
ON DUPLICATE KEY UPDATE
  name_vn = VALUES(name_vn), description_vn = VALUES(description_vn), status = VALUES(status), updated_at = @now;

SELECT id INTO @sp_general FROM specialties WHERE code = 'GENERAL';
SELECT id INTO @sp_cardio FROM specialties WHERE code = 'CARDIO';
SELECT id INTO @sp_derma FROM specialties WHERE code = 'DERMA';
SELECT id INTO @sp_obgyn FROM specialties WHERE code = 'OBGYN';
SELECT id INTO @sp_ped FROM specialties WHERE code = 'PED';
SELECT id INTO @sp_eye FROM specialties WHERE code = 'EYE';
SELECT id INTO @sp_ent FROM specialties WHERE code = 'ENT';
SELECT id INTO @sp_ortho FROM specialties WHERE code = 'ORTHO';

INSERT IGNORE INTO branch_specialties
(branch_id, specialty_id, status, display_order, consultation_fee, slot_minutes_override, note, created_at, updated_at, version, deleted)
VALUES
(@branch_q1, @sp_general, 'ACTIVE', 1, 200000, NULL, 'Chuyên khoa chủ lực tại Q1', @now, @now, 0, 0),
(@branch_q1, @sp_cardio, 'ACTIVE', 2, 350000, NULL, NULL, @now, @now, 0, 0),
(@branch_q1, @sp_derma, 'ACTIVE', 3, 280000, NULL, NULL, @now, @now, 0, 0),
(@branch_q1, @sp_ortho, 'ACTIVE', 4, 320000, NULL, NULL, @now, @now, 0, 0),
(@branch_q7, @sp_obgyn, 'ACTIVE', 1, 350000, NULL, NULL, @now, @now, 0, 0),
(@branch_q7, @sp_ped, 'ACTIVE', 2, 260000, NULL, NULL, @now, @now, 0, 0),
(@branch_q7, @sp_general, 'ACTIVE', 3, 200000, NULL, NULL, @now, @now, 0, 0),
(@branch_td, @sp_eye, 'ACTIVE', 1, 250000, NULL, NULL, @now, @now, 0, 0),
(@branch_td, @sp_ent, 'ACTIVE', 2, 240000, NULL, NULL, @now, @now, 0, 0),
(@branch_td, @sp_general, 'ACTIVE', 3, 200000, NULL, NULL, @now, @now, 0, 0);

INSERT IGNORE INTO reception_queue_counters (branch_id, queue_date, next_queue_no)
VALUES (@branch_q1, @today, 18), (@branch_q7, @today, 9), (@branch_td, @today, 6);

INSERT IGNORE INTO department_queue_counters (department_code, queue_date, next_queue_no)
VALUES ('LAB', @today, 21), ('IMAGING', @today, 13), ('FUNCTIONAL', @today, 7), ('PHARMACY', @today, 15);

-- -----------------------------------------------------------------------------
-- 2) Doctors, staff/admin profiles, users
-- -----------------------------------------------------------------------------
INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
VALUES
('PGS.TS. Nguyễn Văn Minh', @branch_q1, 'Phó Giáo sư, Tiến sĩ', 'Associate Professor, PhD', 'Hơn 20 năm kinh nghiệm trong lĩnh vực tim mạch can thiệp.', 'Over 20 years of interventional cardiology experience.', 'Tim mạch can thiệp, tăng huyết áp, rối loạn nhịp.', 'Interventional cardiology, hypertension, arrhythmia.', 'Đại học Y Hà Nội, Tiến sĩ Y khoa.', 'Hanoi Medical University, PhD in Medicine.', 'Bác sĩ Tim mạch tiêu biểu 2023.', 'Outstanding Cardiologist 2023.', 22, NULL, NULL, 'ACTIVE', @now, @now, 0, 0);
SET @doc_minh = LAST_INSERT_ID();

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
VALUES
('TS. Trần Thị Hương', @branch_q1, 'Tiến sĩ, Bác sĩ Da liễu', 'PhD, Dermatologist', 'Chuyên gia da liễu lâm sàng và thẩm mỹ.', 'Clinical and aesthetic dermatology specialist.', 'Mụn, viêm da cơ địa, laser da liễu.', 'Acne, atopic dermatitis, dermatology laser.', 'Đại học Y Dược TP.HCM.', 'University of Medicine and Pharmacy at HCMC.', NULL, NULL, 15, NULL, NULL, 'ACTIVE', @now, @now, 0, 0);
SET @doc_huong = LAST_INSERT_ID();

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
VALUES
('ThS.BS. Lê Hoàng Nam', @branch_q7, 'Thạc sĩ, Bác sĩ Nhi khoa', 'MSc, Pediatrician', 'Bác sĩ nhi khoa với kinh nghiệm chăm sóc trẻ em và tiêm chủng.', 'Pediatrician experienced in child care and vaccination.', 'Hô hấp nhi, dinh dưỡng nhi, sốt trẻ em.', 'Pediatric respiratory disease, nutrition, childhood fever.', 'Đại học Y khoa Phạm Ngọc Thạch.', 'Pham Ngoc Thach University of Medicine.', NULL, NULL, 12, NULL, NULL, 'ACTIVE', @now, @now, 0, 0);
SET @doc_nam = LAST_INSERT_ID();

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
VALUES
('BS.CKI. Phạm Thanh Hải', @branch_q1, 'Bác sĩ Chuyên khoa I', 'Specialist Level I', 'Kinh nghiệm phong phú trong nội tổng quát và quản lý bệnh mạn tính.', 'Experienced in general medicine and chronic disease management.', 'Đái tháo đường, tăng huyết áp, bệnh tiêu hóa thường gặp.', 'Diabetes, hypertension and common digestive conditions.', NULL, NULL, NULL, NULL, 10, NULL, NULL, 'ACTIVE', @now, @now, 0, 0);
SET @doc_hai = LAST_INSERT_ID();

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
VALUES
('PGS.TS. Võ Minh Tuấn', @branch_q7, 'Phó Giáo sư, Tiến sĩ', 'Associate Professor, PhD', 'Chuyên gia sản phụ khoa uy tín.', 'Senior obstetrics and gynecology specialist.', 'Khám thai, phụ khoa, tầm soát ung thư cổ tử cung.', 'Prenatal care, gynecology, cervical cancer screening.', NULL, NULL, NULL, NULL, 18, NULL, NULL, 'ACTIVE', @now, @now, 0, 0);
SET @doc_tuan = LAST_INSERT_ID();

INSERT INTO doctor_profiles
(full_name, branch_id, display_title_vn, display_title_en, bio_vn, bio_en, expertise_vn, expertise_en, education_vn, education_en, achievements_vn, achievements_en, years_exp, avatar_url, slot_minutes_override, status, created_at, updated_at, version, deleted)
VALUES
('TS.BS. Đỗ Thị Lan', @branch_td, 'Tiến sĩ, Bác sĩ Mắt', 'PhD, Ophthalmologist', 'Chuyên về mắt trẻ em và phẫu thuật khúc xạ.', 'Specialist in pediatric ophthalmology and refractive surgery.', 'Tật khúc xạ, khô mắt, bệnh mắt trẻ em.', 'Refractive errors, dry eyes, pediatric eye diseases.', NULL, NULL, NULL, NULL, 14, NULL, NULL, 'ACTIVE', @now, @now, 0, 0);
SET @doc_lan = LAST_INSERT_ID();

INSERT IGNORE INTO doctor_specialties (doctor_id, specialty_id)
VALUES
(@doc_minh, @sp_cardio),
(@doc_huong, @sp_derma),
(@doc_nam, @sp_ped),
(@doc_hai, @sp_general),
(@doc_tuan, @sp_obgyn),
(@doc_lan, @sp_eye),
(@doc_hai, @sp_ortho);

INSERT INTO doctor_work_schedules (doctor_id, work_date, session, note)
VALUES
(@doc_minh, @today, 'AM', 'Khám thường quy'),
(@doc_minh, @today, 'PM', 'Tái khám tim mạch'),
(@doc_huong, @today, 'AM', 'Da liễu lâm sàng'),
(@doc_hai, @today, 'PM', 'Nội tổng quát'),
(@doc_nam, @today, 'AM', 'Nhi khoa'),
(@doc_tuan, @tomorrow, 'AM', 'Khám thai'),
(@doc_lan, @tomorrow, 'PM', 'Khám mắt'),
(@doc_minh, DATE_ADD(@today, INTERVAL 2 DAY), 'AM', 'Khám tim mạch'),
(@doc_huong, DATE_ADD(@today, INTERVAL 2 DAY), 'PM', 'Da liễu thẩm mỹ'),
(@doc_hai, DATE_ADD(@today, INTERVAL 3 DAY), 'AM', 'Nội tổng quát');

INSERT INTO admin_profiles (full_name, branch_id, created_at, updated_at, version, deleted)
VALUES ('Trần Quốc Bảo', @branch_q1, @now, @now, 0, 0);
SET @admin_bao_profile = LAST_INSERT_ID();

INSERT INTO admin_profiles (full_name, branch_id, created_at, updated_at, version, deleted)
VALUES ('Lê Thị Kim', @branch_q1, @now, @now, 0, 0);
SET @admin_kim_profile = LAST_INSERT_ID();

INSERT INTO staff_profiles (full_name, branch_id, status, created_at, updated_at, version, deleted)
VALUES ('Nguyễn Minh Tú', @branch_q1, 'ACTIVE', @now, @now, 0, 0);
SET @staff_tu_profile = LAST_INSERT_ID();

INSERT INTO staff_profiles (full_name, branch_id, status, created_at, updated_at, version, deleted)
VALUES ('Phạm Thị Hoa', @branch_q7, 'ACTIVE', @now, @now, 0, 0);
SET @staff_hoa_profile = LAST_INSERT_ID();

INSERT INTO staff_profiles (full_name, branch_id, status, created_at, updated_at, version, deleted)
VALUES ('Đặng Quốc Huy', @branch_q1, 'ACTIVE', @now, @now, 0, 0);
SET @staff_huy_profile = LAST_INSERT_ID();

INSERT INTO staff_profiles (full_name, branch_id, status, created_at, updated_at, version, deleted)
VALUES ('Vũ Thị Mai Anh', @branch_q7, 'ACTIVE', @now, @now, 0, 0);
SET @staff_maianh_profile = LAST_INSERT_ID();

INSERT INTO users
(email, phone, password_hash, role, status, last_login_at, doctor_profile_id, staff_profile_id, admin_profile_id, patient_id, created_at, updated_at, version, deleted)
VALUES
    ('sysadmin@primecare.vn', '0900000001', @pwd, 'SYSTEM_ADMIN', 'ACTIVE', NULL, NULL, NULL, @admin_bao_profile, NULL, @now, @now, 0, 0),
('ops@primecare.vn', '0900000002', @pwd, 'OPERATIONS_ADMIN', 'ACTIVE', NULL, NULL, NULL, @admin_kim_profile, NULL, @now, @now, 0, 0),
('reception@primecare.vn', '0900000003', @pwd, 'STAFF', 'ACTIVE', NULL, NULL, @staff_tu_profile, NULL, NULL, @now, @now, 0, 0),
('cashier@primecare.vn', '0900000004', @pwd, 'CASHIER', 'ACTIVE', NULL, NULL, @staff_hoa_profile, NULL, NULL, @now, @now, 0, 0),
('pharmacist@primecare.vn', '0900000005', @pwd, 'PHARMACIST', 'ACTIVE', NULL, NULL, @staff_huy_profile, NULL, NULL, @now, @now, 0, 0),
('service.ops@primecare.vn', '0900000006', @pwd, 'SERVICE_TECHNICIAN', 'ACTIVE', NULL, NULL, @staff_maianh_profile, NULL, NULL, @now, @now, 0, 0),
('dr.minh@primecare.vn', '0900000101', @pwd, 'DOCTOR', 'ACTIVE', NULL, @doc_minh, NULL, NULL, NULL, @now, @now, 0, 0),
('dr.huong@primecare.vn', '0900000102', @pwd, 'DOCTOR', 'ACTIVE', NULL, @doc_huong, NULL, NULL, NULL, @now, @now, 0, 0),
('dr.nam@primecare.vn', '0900000103', @pwd, 'DOCTOR', 'ACTIVE', NULL, @doc_nam, NULL, NULL, NULL, @now, @now, 0, 0),
('dr.hai@primecare.vn', '0900000104', @pwd, 'DOCTOR', 'ACTIVE', NULL, @doc_hai, NULL, NULL, NULL, @now, @now, 0, 0),
('dr.tuan@primecare.vn', '0900000105', @pwd, 'DOCTOR', 'ACTIVE', NULL, @doc_tuan, NULL, NULL, NULL, @now, @now, 0, 0),
('dr.lan@primecare.vn', '0900000106', @pwd, 'DOCTOR', 'ACTIVE', NULL, @doc_lan, NULL, NULL, NULL, @now, @now, 0, 0);

SELECT id INTO @u_sysadmin FROM users WHERE email = 'sysadmin@primecare.vn';
SELECT id INTO @u_ops FROM users WHERE email = 'ops@primecare.vn';
SELECT id INTO @u_reception FROM users WHERE email = 'reception@primecare.vn';
SELECT id INTO @u_cashier FROM users WHERE email = 'cashier@primecare.vn';
SELECT id INTO @u_pharmacist FROM users WHERE email = 'pharmacist@primecare.vn';
SELECT id INTO @u_service_ops FROM users WHERE email = 'service.ops@primecare.vn';
SELECT id INTO @u_dr_minh FROM users WHERE email = 'dr.minh@primecare.vn';
SELECT id INTO @u_dr_huong FROM users WHERE email = 'dr.huong@primecare.vn';
SELECT id INTO @u_dr_nam FROM users WHERE email = 'dr.nam@primecare.vn';
SELECT id INTO @u_dr_hai FROM users WHERE email = 'dr.hai@primecare.vn';
SELECT id INTO @u_dr_tuan FROM users WHERE email = 'dr.tuan@primecare.vn';
SELECT id INTO @u_dr_lan FROM users WHERE email = 'dr.lan@primecare.vn';

-- -----------------------------------------------------------------------------
-- 3) Patients and patient accounts
-- -----------------------------------------------------------------------------
INSERT INTO patients
(code, full_name, phone, email, avatar_url, dob, gender, address, identity_number, insurance_number, insurance_expiry_date, insurance_registered_hospital, blood_type, ethnicity, nationality, occupation, province, district, ward, emergency_contact_name, emergency_contact_phone, allergy_note, chronic_disease_note, note, status, created_at, updated_at, version, deleted)
VALUES
('BN0001', 'Nguyễn Thị Mai', '0912345678', 'mai.nguyen@example.com', NULL, '1990-05-15', 'FEMALE', '123 Nguyễn Huệ, Phường Bến Nghé, Quận 1, TP.HCM', '079190000001', 'GD4012345678901', DATE_ADD(@today, INTERVAL 18 MONTH), 'Bệnh viện Quận 1', 'A_PLUS', 'Kinh', 'Việt Nam', 'Kế toán', 'TP.HCM', 'Quận 1', 'Bến Nghé', 'Nguyễn Văn An', '0912000001', 'Dị ứng penicillin nhẹ.', 'Tăng huyết áp độ 1.', 'Bệnh nhân cần nhắc lịch qua email.', 'ACTIVE', @now, @now, 0, 0),
('BN0002', 'Trần Văn Bình', '0987654321', 'binh.tran@example.com', NULL, '1985-08-20', 'MALE', '45 Lê Lợi, Quận 3, TP.HCM', '079185000002', 'GD4012345678902', DATE_ADD(@today, INTERVAL 10 MONTH), 'Bệnh viện Quận 3', 'O_PLUS', 'Kinh', 'Việt Nam', 'Kỹ sư', 'TP.HCM', 'Quận 3', 'Võ Thị Sáu', 'Trần Thị Thu', '0987000002', NULL, 'Viêm dạ dày mạn tính.', NULL, 'ACTIVE', @now, @now, 0, 0),
('BN0003', 'Lê Thị Hồng', '0901234567', 'hong.le@example.com', NULL, '1978-12-03', 'FEMALE', '78 Trần Hưng Đạo, Quận 5, TP.HCM', '079178000003', 'GD4012345678903', DATE_ADD(@today, INTERVAL 8 MONTH), 'Bệnh viện Chợ Rẫy', 'B_PLUS', 'Kinh', 'Việt Nam', 'Giáo viên', 'TP.HCM', 'Quận 5', 'Phường 6', 'Lê Văn Bình', '0901000003', NULL, 'Đái tháo đường type 2.', NULL, 'ACTIVE', @now, @now, 0, 0),
('BN0004', 'Phạm Minh Đức', '0918765432', 'duc.pham@example.com', NULL, '2020-03-10', 'MALE', '12 Nguyễn Thị Thập, Quận 7, TP.HCM', NULL, NULL, NULL, NULL, 'O_PLUS', 'Kinh', 'Việt Nam', 'Trẻ em', 'TP.HCM', 'Quận 7', 'Tân Phú', 'Phạm Thu Hà', '0918000004', NULL, NULL, 'Bệnh nhi đi cùng mẹ.', 'ACTIVE', @now, @now, 0, 0),
('BN0005', 'Võ Thanh Tùng', '0909876543', 'tung.vo@example.com', NULL, '1995-07-22', 'MALE', '200 Điện Biên Phủ, Bình Thạnh, TP.HCM', '079195000005', 'GD4012345678905', DATE_ADD(@today, INTERVAL 20 MONTH), 'Bệnh viện Gia Định', 'AB_PLUS', 'Kinh', 'Việt Nam', 'Nhân viên kinh doanh', 'TP.HCM', 'Bình Thạnh', 'Phường 15', 'Võ Minh Tâm', '0909000005', NULL, NULL, NULL, 'ACTIVE', @now, @now, 0, 0),
('BN0006', 'Đặng Thị Lan Anh', '0932123456', 'lananh.dang@example.com', NULL, '1988-11-08', 'FEMALE', '55 Phú Mỹ Hưng, Quận 7, TP.HCM', '079188000006', 'GD4012345678906', DATE_ADD(@today, INTERVAL 6 MONTH), 'Bệnh viện Quận 7', 'A_MINUS', 'Kinh', 'Việt Nam', 'Quản lý nhân sự', 'TP.HCM', 'Quận 7', 'Tân Phong', 'Đặng Văn Hòa', '0932000006', NULL, NULL, 'Đang theo dõi thai kỳ.', 'ACTIVE', @now, @now, 0, 0),
('BN0007', 'Bùi Gia Hân', '0923456789', 'han.bui@example.com', NULL, '2016-09-18', 'FEMALE', '9 Võ Văn Ngân, TP. Thủ Đức, TP.HCM', NULL, NULL, NULL, NULL, 'B_PLUS', 'Kinh', 'Việt Nam', 'Học sinh', 'TP.HCM', 'Thủ Đức', 'Linh Chiểu', 'Bùi Quốc Dũng', '0923000007', 'Dị ứng hải sản.', NULL, NULL, 'ACTIVE', @now, @now, 0, 0),
('BN0008', 'Hoàng Minh Quân', '0965432109', 'quan.hoang@example.com', NULL, '1969-02-14', 'MALE', '32 Pasteur, Quận 1, TP.HCM', '079169000008', 'GD4012345678908', DATE_ADD(@today, INTERVAL 4 MONTH), 'Bệnh viện Nhân dân 115', 'O_MINUS', 'Kinh', 'Việt Nam', 'Nghỉ hưu', 'TP.HCM', 'Quận 1', 'Bến Nghé', 'Hoàng Thị Hoa', '0965000008', NULL, 'Tăng huyết áp, rối loạn mỡ máu.', NULL, 'ACTIVE', @now, @now, 0, 0)
ON DUPLICATE KEY UPDATE full_name = VALUES(full_name), phone = VALUES(phone), email = VALUES(email), status = VALUES(status), updated_at = @now;

SELECT id INTO @p_mai FROM patients WHERE code = 'BN0001';
SELECT id INTO @p_binh FROM patients WHERE code = 'BN0002';
SELECT id INTO @p_hong FROM patients WHERE code = 'BN0003';
SELECT id INTO @p_duc FROM patients WHERE code = 'BN0004';
SELECT id INTO @p_tung FROM patients WHERE code = 'BN0005';
SELECT id INTO @p_lananh FROM patients WHERE code = 'BN0006';
SELECT id INTO @p_han FROM patients WHERE code = 'BN0007';
SELECT id INTO @p_quan FROM patients WHERE code = 'BN0008';

INSERT INTO users
(email, phone, password_hash, role, status, last_login_at, doctor_profile_id, staff_profile_id, admin_profile_id, patient_id, created_at, updated_at, version, deleted)
VALUES
('mai.nguyen@example.com', '0912345678', @pwd, 'PATIENT', 'ACTIVE', NULL, NULL, NULL, NULL, @p_mai, @now, @now, 0, 0),
('binh.tran@example.com', '0987654321', @pwd, 'PATIENT', 'ACTIVE', NULL, NULL, NULL, NULL, @p_binh, @now, @now, 0, 0),
('tung.vo@example.com', '0909876543', @pwd, 'PATIENT', 'ACTIVE', NULL, NULL, NULL, NULL, @p_tung, @now, @now, 0, 0),
('lananh.dang@example.com', '0932123456', @pwd, 'PATIENT', 'ACTIVE', NULL, NULL, NULL, NULL, @p_lananh, @now, @now, 0, 0);

SELECT id INTO @u_patient_mai FROM users WHERE email = 'mai.nguyen@example.com';
SELECT id INTO @u_patient_binh FROM users WHERE email = 'binh.tran@example.com';
SELECT id INTO @u_patient_tung FROM users WHERE email = 'tung.vo@example.com';
SELECT id INTO @u_patient_lananh FROM users WHERE email = 'lananh.dang@example.com';

INSERT IGNORE INTO notification_preferences
(user_id, allow_email, allow_sms, appointment_reminders, result_ready_alerts, invoice_updates, security_alerts, preferred_channel, created_at, updated_at, version, deleted)
VALUES
(@u_patient_mai, 1, 1, 1, 1, 1, 1, 'EMAIL', @now, @now, 0, 0),
(@u_patient_binh, 1, 0, 1, 1, 1, 1, 'EMAIL', @now, @now, 0, 0),
(@u_patient_tung, 1, 1, 1, 1, 1, 1, 'SMS', @now, @now, 0, 0),
(@u_patient_lananh, 1, 1, 1, 1, 1, 1, 'EMAIL', @now, @now, 0, 0);

INSERT INTO patient_allergies
(patient_id, allergen_name, allergy_type, severity, reaction, noted_by, created_at, updated_at, version, deleted)
VALUES
(@p_mai, 'Penicillin', 'DRUG', 'MILD', 'Nổi mẩn nhẹ sau dùng thuốc.', @u_dr_hai, @now, @now, 0, 0),
(@p_han, 'Hải sản', 'FOOD', 'MODERATE', 'Ngứa, nổi mề đay.', @u_dr_nam, @now, @now, 0, 0),
(@p_quan, 'Bụi nhà', 'ENVIRONMENTAL', 'MILD', 'Hắt hơi, nghẹt mũi khi tiếp xúc.', @u_dr_hai, @now, @now, 0, 0);

-- -----------------------------------------------------------------------------
-- 4) Medical services, medications, inventory, ICD-10
-- -----------------------------------------------------------------------------
INSERT INTO medical_services
(code, name_vn, name_en, description_vn, description_en, service_type, department_code, base_price, status, public_visible, display_order, thumbnail_url, default_turnaround_minutes, requires_file_result, requires_numeric_result, result_template_code, result_template_schema_json, result_report_title, created_at, updated_at, version, deleted)
VALUES
('CONS-GEN', 'Khám nội tổng quát', 'General consultation', 'Khám lâm sàng nội tổng quát.', 'General physician consultation.', 'PROCEDURE', 'CONSULT', 200000, 'ACTIVE', 1, 1, NULL, 0, 0, 0, 'GENERIC_NARRATIVE', NULL, 'Phiếu khám nội tổng quát', @now, @now, 0, 0),
('CONS-CARDIO', 'Khám tim mạch', 'Cardiology consultation', 'Khám chuyên khoa tim mạch.', 'Cardiology specialist consultation.', 'PROCEDURE', 'CONSULT', 350000, 'ACTIVE', 1, 2, NULL, 0, 0, 0, 'GENERIC_NARRATIVE', NULL, 'Phiếu khám tim mạch', @now, @now, 0, 0),
('LAB-CBC', 'Công thức máu toàn phần', 'Complete blood count', 'Xét nghiệm huyết học cơ bản.', 'Basic hematology test.', 'LAB', 'LAB', 180000, 'ACTIVE', 1, 3, NULL, 120, 0, 1, 'LAB_TABLE', '{"fields":["WBC","RBC","HGB","PLT"]}', 'Kết quả công thức máu', @now, @now, 0, 0),
('LAB-GLU', 'Đường huyết lúc đói', 'Fasting blood glucose', 'Định lượng glucose máu.', 'Blood glucose measurement.', 'LAB', 'LAB', 90000, 'ACTIVE', 1, 4, NULL, 90, 0, 1, 'LAB_TABLE', '{"fields":["Glucose"]}', 'Kết quả đường huyết', @now, @now, 0, 0),
('IMG-XRAY-CHEST', 'Chụp X-quang ngực', 'Chest X-ray', 'Chụp X-quang ngực thẳng.', 'PA chest X-ray.', 'IMAGING', 'IMAGING', 250000, 'ACTIVE', 1, 5, NULL, 180, 1, 0, 'IMAGING_REPORT', NULL, 'Kết quả X-quang ngực', @now, @now, 0, 0),
('IMG-ECHO', 'Siêu âm tim Doppler', 'Doppler echocardiography', 'Siêu âm tim Doppler màu.', 'Color Doppler echocardiography.', 'IMAGING', 'IMAGING', 500000, 'ACTIVE', 1, 6, NULL, 240, 1, 0, 'IMAGING_REPORT', NULL, 'Kết quả siêu âm tim', @now, @now, 0, 0),
('FUNC-ECG', 'Điện tâm đồ', 'Electrocardiogram', 'Ghi điện tâm đồ 12 chuyển đạo.', '12-lead ECG.', 'FUNCTIONAL_TEST', 'FUNCTIONAL', 150000, 'ACTIVE', 1, 7, NULL, 60, 1, 0, 'IMAGING_REPORT', NULL, 'Kết quả điện tâm đồ', @now, @now, 0, 0),
('PROC-ENDO', 'Nội soi dạ dày', 'Gastroscopy', 'Nội soi dạ dày chẩn đoán.', 'Diagnostic gastroscopy.', 'PROCEDURE', 'ENDOSCOPY', 1200000, 'ACTIVE', 1, 8, NULL, 1440, 1, 0, 'GENERIC_NARRATIVE', NULL, 'Kết quả nội soi dạ dày', @now, @now, 0, 0)
ON DUPLICATE KEY UPDATE name_vn = VALUES(name_vn), base_price = VALUES(base_price), status = VALUES(status), updated_at = @now;

SELECT id INTO @svc_cons_gen FROM medical_services WHERE code = 'CONS-GEN';
SELECT id INTO @svc_cons_cardio FROM medical_services WHERE code = 'CONS-CARDIO';
SELECT id INTO @svc_cbc FROM medical_services WHERE code = 'LAB-CBC';
SELECT id INTO @svc_glu FROM medical_services WHERE code = 'LAB-GLU';
SELECT id INTO @svc_xray FROM medical_services WHERE code = 'IMG-XRAY-CHEST';
SELECT id INTO @svc_echo FROM medical_services WHERE code = 'IMG-ECHO';
SELECT id INTO @svc_ecg FROM medical_services WHERE code = 'FUNC-ECG';
SELECT id INTO @svc_endo FROM medical_services WHERE code = 'PROC-ENDO';

INSERT INTO medications
(code, name, generic_name, strength, dosage_form, unit, manufacturer, indication_note, contraindication_note, category, route_of_administration, storage_conditions, requires_prescription, max_daily_dose, unit_price, status, created_at, updated_at, version, deleted)
VALUES
('MED-AMOX500', 'Amoxicillin 500mg', 'Amoxicillin', '500mg', 'Viên nang', 'Viên', 'DHG Pharma', 'Kháng sinh nhóm penicillin.', 'Dị ứng penicillin.', 'ANTIBIOTIC', 'ORAL', 'Nhiệt độ phòng', 1, 'Theo chỉ định bác sĩ', 2500, 'ACTIVE', @now, @now, 0, 0),
('MED-PARA500', 'Paracetamol 500mg', 'Acetaminophen', '500mg', 'Viên nén', 'Viên', 'Traphaco', 'Giảm đau, hạ sốt.', 'Suy gan nặng.', 'ANALGESIC', 'ORAL', 'Nơi khô mát', 0, '4000mg/ngày', 1200, 'ACTIVE', @now, @now, 0, 0),
('MED-OME20', 'Omeprazole 20mg', 'Omeprazole', '20mg', 'Viên nang', 'Viên', 'Stada', 'Trào ngược dạ dày, viêm loét dạ dày.', NULL, 'GASTRO', 'ORAL', 'Nhiệt độ phòng', 0, '40mg/ngày', 1800, 'ACTIVE', @now, @now, 0, 0),
('MED-MET500', 'Metformin 500mg', 'Metformin', '500mg', 'Viên nén', 'Viên', 'Sanofi', 'Đái tháo đường type 2.', 'Suy thận nặng.', 'ENDOCRINE', 'ORAL', 'Nhiệt độ phòng', 1, '2000mg/ngày', 1600, 'ACTIVE', @now, @now, 0, 0),
('MED-LOS50', 'Losartan 50mg', 'Losartan', '50mg', 'Viên nén', 'Viên', 'MSD', 'Tăng huyết áp.', 'Phụ nữ có thai.', 'CARDIO', 'ORAL', 'Nhiệt độ phòng', 1, '100mg/ngày', 3200, 'ACTIVE', @now, @now, 0, 0),
('MED-CET10', 'Cetirizine 10mg', 'Cetirizine', '10mg', 'Viên nén', 'Viên', 'OPC', 'Dị ứng, mề đay.', NULL, 'ANTIHISTAMINE', 'ORAL', 'Nơi khô mát', 0, '10mg/ngày', 1500, 'ACTIVE', @now, @now, 0, 0),
('MED-PRED5', 'Prednisolone 5mg', 'Prednisolone', '5mg', 'Viên nén', 'Viên', 'Imexpharm', 'Kháng viêm corticosteroid.', 'Nhiễm nấm toàn thân.', 'CORTICOID', 'ORAL', 'Nơi khô mát', 1, 'Theo chỉ định bác sĩ', 1000, 'ACTIVE', @now, @now, 0, 0),
('MED-ATOR20', 'Atorvastatin 20mg', 'Atorvastatin', '20mg', 'Viên nén', 'Viên', 'Pfizer', 'Rối loạn lipid máu.', 'Bệnh gan tiến triển.', 'CARDIO', 'ORAL', 'Nhiệt độ phòng', 1, '80mg/ngày', 4500, 'ACTIVE', @now, @now, 0, 0)
ON DUPLICATE KEY UPDATE name = VALUES(name), unit_price = VALUES(unit_price), status = VALUES(status), updated_at = @now;

SELECT id INTO @med_amox FROM medications WHERE code = 'MED-AMOX500';
SELECT id INTO @med_para FROM medications WHERE code = 'MED-PARA500';
SELECT id INTO @med_ome FROM medications WHERE code = 'MED-OME20';
SELECT id INTO @med_met FROM medications WHERE code = 'MED-MET500';
SELECT id INTO @med_los FROM medications WHERE code = 'MED-LOS50';
SELECT id INTO @med_cet FROM medications WHERE code = 'MED-CET10';
SELECT id INTO @med_pred FROM medications WHERE code = 'MED-PRED5';
SELECT id INTO @med_ator FROM medications WHERE code = 'MED-ATOR20';

INSERT IGNORE INTO drug_interactions
(medication_a_id, medication_b_id, severity, description, clinical_effect, created_at, updated_at, version, deleted)
VALUES
(@med_los, @med_met, 'MINOR', 'Theo dõi chức năng thận khi phối hợp thuốc trên bệnh nhân nguy cơ.', 'Tăng nguy cơ ảnh hưởng chức năng thận ở bệnh nhân mất nước.', @now, @now, 0, 0),
(@med_pred, @med_met, 'MODERATE', 'Corticosteroid có thể làm tăng đường huyết.', 'Có thể cần điều chỉnh thuốc kiểm soát đường huyết.', @now, @now, 0, 0),
(@med_ator, @med_ome, 'MINOR', 'Tương tác ít ý nghĩa lâm sàng, theo dõi triệu chứng cơ.', 'Hiếm gặp đau cơ.', @now, @now, 0, 0);

INSERT IGNORE INTO medication_batches
(medication_id, batch_number, quantity_in_stock, manufacture_date, expiry_date, import_price, sell_price, supplier, note, created_at, updated_at, version, deleted)
VALUES
(@med_para, 'PARA-2026-01', 1200, DATE_SUB(@today, INTERVAL 4 MONTH), DATE_ADD(@today, INTERVAL 20 MONTH), 700, 1200, 'Công ty Dược Sài Gòn', NULL, @now, @now, 0, 0),
(@med_los, 'LOS-2026-02', 500, DATE_SUB(@today, INTERVAL 3 MONTH), DATE_ADD(@today, INTERVAL 21 MONTH), 2200, 3200, 'Nhà phân phối Minh Châu', NULL, @now, @now, 0, 0),
(@med_met, 'MET-2026-02', 650, DATE_SUB(@today, INTERVAL 3 MONTH), DATE_ADD(@today, INTERVAL 21 MONTH), 900, 1600, 'Nhà phân phối Minh Châu', NULL, @now, @now, 0, 0),
(@med_ome, 'OME-2026-03', 720, DATE_SUB(@today, INTERVAL 2 MONTH), DATE_ADD(@today, INTERVAL 22 MONTH), 1000, 1800, 'Công ty Dược An Khang', NULL, @now, @now, 0, 0),
(@med_amox, 'AMOX-2026-01', 300, DATE_SUB(@today, INTERVAL 5 MONTH), DATE_ADD(@today, INTERVAL 18 MONTH), 1800, 2500, 'Công ty Dược An Khang', 'Theo dõi dị ứng penicillin.', @now, @now, 0, 0),
(@med_cet, 'CET-2026-04', 480, DATE_SUB(@today, INTERVAL 1 MONTH), DATE_ADD(@today, INTERVAL 23 MONTH), 850, 1500, 'Công ty Dược Sài Gòn', NULL, @now, @now, 0, 0);

SELECT id INTO @batch_para FROM medication_batches WHERE medication_id = @med_para AND batch_number = 'PARA-2026-01';
SELECT id INTO @batch_los FROM medication_batches WHERE medication_id = @med_los AND batch_number = 'LOS-2026-02';
SELECT id INTO @batch_met FROM medication_batches WHERE medication_id = @med_met AND batch_number = 'MET-2026-02';
SELECT id INTO @batch_ome FROM medication_batches WHERE medication_id = @med_ome AND batch_number = 'OME-2026-03';

INSERT INTO inventory_transactions
(batch_id, transaction_type, quantity, reason, reference_type, reference_id, performed_by_user_id, performed_at, created_at, updated_at, version, deleted)
VALUES
(@batch_para, 'IMPORT', 1200, 'Nhập kho ban đầu cho dữ liệu test.', 'PURCHASE_ORDER', NULL, @u_pharmacist, TIMESTAMP(@today, '08:00:00'), @now, @now, 0, 0),
(@batch_los, 'IMPORT', 500, 'Nhập kho ban đầu cho dữ liệu test.', 'PURCHASE_ORDER', NULL, @u_pharmacist, TIMESTAMP(@today, '08:10:00'), @now, @now, 0, 0),
(@batch_met, 'IMPORT', 650, 'Nhập kho ban đầu cho dữ liệu test.', 'PURCHASE_ORDER', NULL, @u_pharmacist, TIMESTAMP(@today, '08:15:00'), @now, @now, 0, 0),
(@batch_ome, 'IMPORT', 720, 'Nhập kho ban đầu cho dữ liệu test.', 'PURCHASE_ORDER', NULL, @u_pharmacist, TIMESTAMP(@today, '08:20:00'), @now, @now, 0, 0),
(@batch_para, 'ADJUSTMENT', -20, 'Kiểm kê lệch do bao bì hỏng.', 'STOCKTAKE', NULL, @u_pharmacist, TIMESTAMP(@today, '09:00:00'), @now, @now, 0, 0);

INSERT IGNORE INTO icd10_codes
(code, name_vn, name_en, category, is_active, created_at, updated_at, version, deleted)
VALUES
('I10', 'Tăng huyết áp vô căn', 'Essential hypertension', 'CARDIO', 1, @now, @now, 0, 0),
('E11', 'Đái tháo đường type 2', 'Type 2 diabetes mellitus', 'ENDOCRINE', 1, @now, @now, 0, 0),
('J06.9', 'Nhiễm trùng hô hấp trên cấp, không đặc hiệu', 'Acute upper respiratory infection, unspecified', 'RESPIRATORY', 1, @now, @now, 0, 0),
('K29.7', 'Viêm dạ dày, không xác định', 'Gastritis, unspecified', 'DIGESTIVE', 1, @now, @now, 0, 0),
('L20.9', 'Viêm da cơ địa, không xác định', 'Atopic dermatitis, unspecified', 'DERMA', 1, @now, @now, 0, 0),
('R07.4', 'Đau ngực, không xác định', 'Chest pain, unspecified', 'SYMPTOM', 1, @now, @now, 0, 0),
('R50.9', 'Sốt, không xác định', 'Fever, unspecified', 'SYMPTOM', 1, @now, @now, 0, 0),
('H52.1', 'Cận thị', 'Myopia', 'EYE', 1, @now, @now, 0, 0),
('Z00.0', 'Khám sức khỏe tổng quát', 'General medical examination', 'CHECKUP', 1, @now, @now, 0, 0),
('E78.5', 'Rối loạn lipid máu, không xác định', 'Hyperlipidemia, unspecified', 'ENDOCRINE', 1, @now, @now, 0, 0);

SELECT id INTO @icd_i10 FROM icd10_codes WHERE code = 'I10';
SELECT id INTO @icd_e11 FROM icd10_codes WHERE code = 'E11';
SELECT id INTO @icd_j06 FROM icd10_codes WHERE code = 'J06.9';
SELECT id INTO @icd_k29 FROM icd10_codes WHERE code = 'K29.7';
SELECT id INTO @icd_r07 FROM icd10_codes WHERE code = 'R07.4';
SELECT id INTO @icd_e78 FROM icd10_codes WHERE code = 'E78.5';

-- -----------------------------------------------------------------------------
-- 5) Appointments, encounters, diagnoses
-- -----------------------------------------------------------------------------
INSERT INTO appointments
(code, status, branch_id, specialty_id, doctor_id, visit_date, session, source_type, arrival_status, reception_queue_no, arrived_by, arrived_at, queue_no, slot_minutes, eta_start, eta_end, patient_full_name, patient_phone, patient_email, patient_dob, patient_gender, patient_note, reason_for_visit, visit_type, triage_priority, triage_note, insurance_note, emergency_contact_name, emergency_contact_phone, height_cm, weight_kg, temperature_c, pulse, systolic_bp, diastolic_bp, respiratory_rate, spo2, intake_completed_by, intake_completed_at, confirmed_by, confirmed_at, cancelled_by, cancelled_at, cancel_reason, processing_by, processing_started_at, processing_expires_at, checked_in_by, checked_in_at, no_show_marked_by, no_show_marked_at, follow_up_pending, completed_at, rescheduled_from_appointment_id, rescheduled_by, patient_id, rescheduled_at, created_at, updated_at, version, deleted)
VALUES
(CONCAT('APT-', DATE_FORMAT(@yesterday, '%Y%m%d'), '-001'), 'COMPLETED', @branch_q1, @sp_cardio, @doc_minh, @yesterday, 'PM', 'STAFF_BOOKING', 'ARRIVED', 12, @u_reception, TIMESTAMP(@yesterday, '13:45:00'), 5, 30, '14:00:00', '14:30:00', 'Võ Thanh Tùng', '0909876543', 'tung.vo@example.com', '1995-07-22', 'MALE', NULL, 'Đau ngực nhẹ sau vận động, hồi hộp.', 'FOLLOW_UP', 'NORMAL', 'Không khó thở khi nghỉ.', 'BHYT hợp lệ', 'Võ Minh Tâm', '0909000005', 172, 72, 36.7, 82, 132, 84, 18, 98, @u_reception, TIMESTAMP(@yesterday, '13:50:00'), @u_reception, TIMESTAMP(@yesterday, '09:00:00'), NULL, NULL, NULL, NULL, NULL, NULL, @u_reception, TIMESTAMP(@yesterday, '13:55:00'), NULL, NULL, 1, TIMESTAMP(@yesterday, '15:20:00'), NULL, NULL, @p_tung, NULL, TIMESTAMP(@yesterday, '08:30:00'), TIMESTAMP(@yesterday, '15:20:00'), 0, 0),
(CONCAT('APT-', DATE_FORMAT(@today, '%Y%m%d'), '-001'), 'CONFIRMED', @branch_q1, @sp_cardio, @doc_minh, @today, 'AM', 'PUBLIC_BOOKING', 'NOT_ARRIVED', NULL, NULL, NULL, 1, 30, '08:00:00', '08:30:00', 'Nguyễn Thị Mai', '0912345678', 'mai.nguyen@example.com', '1990-05-15', 'FEMALE', 'Bệnh nhân muốn nhắc lịch qua email.', 'Đau ngực thoáng qua, kiểm tra huyết áp.', 'FIRST_VISIT', 'NORMAL', NULL, 'BHYT hợp lệ', 'Nguyễn Văn An', '0912000001', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, @u_reception, TIMESTAMP(@today, '07:10:00'), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, @p_mai, NULL, TIMESTAMP(@today, '06:30:00'), @now, 0, 0),
(CONCAT('APT-', DATE_FORMAT(@today, '%Y%m%d'), '-002'), 'CHECKED_IN', @branch_q1, @sp_derma, @doc_huong, @today, 'AM', 'PUBLIC_BOOKING', 'ARRIVED', 3, @u_reception, TIMESTAMP(@today, '08:10:00'), 2, 20, '08:30:00', '08:50:00', 'Trần Văn Bình', '0987654321', 'binh.tran@example.com', '1985-08-20', 'MALE', NULL, 'Ngứa da kéo dài vùng cẳng tay.', 'FIRST_VISIT', 'NORMAL', 'Sinh hiệu ổn.', NULL, 'Trần Thị Thu', '0987000002', 168, 70, 36.8, 78, 124, 80, 18, 99, @u_reception, TIMESTAMP(@today, '08:15:00'), @u_reception, TIMESTAMP(@today, '07:20:00'), NULL, NULL, NULL, NULL, NULL, NULL, @u_reception, TIMESTAMP(@today, '08:18:00'), NULL, NULL, 0, NULL, NULL, NULL, @p_binh, NULL, TIMESTAMP(@today, '06:45:00'), @now, 0, 0),
(CONCAT('APT-', DATE_FORMAT(@today, '%Y%m%d'), '-003'), 'CHECKED_IN', @branch_q7, @sp_ped, @doc_nam, @today, 'AM', 'WALK_IN', 'ARRIVED', 5, @u_reception, TIMESTAMP(@today, '09:05:00'), 4, 25, '09:15:00', '09:40:00', 'Phạm Minh Đức', '0918765432', 'duc.pham@example.com', '2020-03-10', 'MALE', 'Bệnh nhi đi cùng mẹ.', 'Sốt cao, ho khan từ tối qua.', 'FIRST_VISIT', 'URGENT', 'Sốt 38.8 độ, SpO2 ổn.', NULL, 'Phạm Thu Hà', '0918000004', 108, 18.5, 38.8, 110, 100, 65, 24, 98, @u_reception, TIMESTAMP(@today, '09:08:00'), @u_reception, TIMESTAMP(@today, '09:02:00'), NULL, NULL, NULL, NULL, NULL, NULL, @u_reception, TIMESTAMP(@today, '09:10:00'), NULL, NULL, 0, NULL, NULL, NULL, @p_duc, NULL, TIMESTAMP(@today, '08:58:00'), @now, 0, 0),
(CONCAT('APT-', DATE_FORMAT(@tomorrow, '%Y%m%d'), '-001'), 'REQUESTED', @branch_q7, @sp_obgyn, @doc_tuan, @tomorrow, 'AM', 'PUBLIC_BOOKING', 'NOT_ARRIVED', NULL, NULL, NULL, 1, 30, '08:00:00', '08:30:00', 'Đặng Thị Lan Anh', '0932123456', 'lananh.dang@example.com', '1988-11-08', 'FEMALE', 'Theo dõi thai kỳ.', 'Khám thai định kỳ.', 'FOLLOW_UP', 'NORMAL', NULL, 'BHYT hợp lệ', 'Đặng Văn Hòa', '0932000006', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, NULL, NULL, NULL, @p_lananh, NULL, TIMESTAMP(@today, '10:00:00'), @now, 0, 0),
(CONCAT('APT-', DATE_FORMAT(@today, '%Y%m%d'), '-004'), 'NO_SHOW', @branch_td, @sp_eye, @doc_lan, @today, 'PM', 'STAFF_BOOKING', 'NOT_ARRIVED', NULL, NULL, NULL, 1, 20, '13:30:00', '13:50:00', 'Bùi Gia Hân', '0923456789', 'han.bui@example.com', '2016-09-18', 'FEMALE', NULL, 'Đo thị lực định kỳ.', 'FOLLOW_UP', 'NORMAL', NULL, NULL, 'Bùi Quốc Dũng', '0923000007', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, @u_reception, TIMESTAMP(@today, '07:30:00'), NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, @u_reception, TIMESTAMP(@today, '14:00:00'), 1, NULL, NULL, NULL, @p_han, NULL, TIMESTAMP(@today, '07:10:00'), @now, 0, 0),
(CONCAT('APT-', DATE_FORMAT(@today, '%Y%m%d'), '-005'), 'CANCELLED', @branch_q1, @sp_general, @doc_hai, @today, 'PM', 'PUBLIC_BOOKING', 'NOT_ARRIVED', NULL, NULL, NULL, 3, 30, '14:30:00', '15:00:00', 'Hoàng Minh Quân', '0965432109', 'quan.hoang@example.com', '1969-02-14', 'MALE', NULL, 'Tái khám huyết áp.', 'FOLLOW_UP', 'NORMAL', NULL, 'BHYT hợp lệ', 'Hoàng Thị Hoa', '0965000008', NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, @u_reception, TIMESTAMP(@today, '07:40:00'), @u_reception, TIMESTAMP(@today, '11:05:00'), 'Bệnh nhân bận việc cá nhân, sẽ đặt lại lịch.', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 1, NULL, NULL, NULL, @p_quan, NULL, TIMESTAMP(@today, '07:15:00'), @now, 0, 0);

SELECT id INTO @appt_completed FROM appointments WHERE code = CONCAT('APT-', DATE_FORMAT(@yesterday, '%Y%m%d'), '-001');
SELECT id INTO @appt_mai FROM appointments WHERE code = CONCAT('APT-', DATE_FORMAT(@today, '%Y%m%d'), '-001');
SELECT id INTO @appt_binh FROM appointments WHERE code = CONCAT('APT-', DATE_FORMAT(@today, '%Y%m%d'), '-002');
SELECT id INTO @appt_duc FROM appointments WHERE code = CONCAT('APT-', DATE_FORMAT(@today, '%Y%m%d'), '-003');
SELECT id INTO @appt_lananh FROM appointments WHERE code = CONCAT('APT-', DATE_FORMAT(@tomorrow, '%Y%m%d'), '-001');
SELECT id INTO @appt_noshow FROM appointments WHERE code = CONCAT('APT-', DATE_FORMAT(@today, '%Y%m%d'), '-004');
SELECT id INTO @appt_cancelled FROM appointments WHERE code = CONCAT('APT-', DATE_FORMAT(@today, '%Y%m%d'), '-005');

INSERT INTO appointment_status_histories
(appointment_id, from_status, to_status, changed_by, changed_at, note, created_at, updated_at, version, deleted)
VALUES
(@appt_completed, NULL, 'REQUESTED', @u_reception, TIMESTAMP(@yesterday, '08:30:00'), 'Tạo lịch hẹn từ quầy.', @now, @now, 0, 0),
(@appt_completed, 'REQUESTED', 'CONFIRMED', @u_reception, TIMESTAMP(@yesterday, '09:00:00'), 'Đã xác nhận.', @now, @now, 0, 0),
(@appt_completed, 'CONFIRMED', 'CHECKED_IN', @u_reception, TIMESTAMP(@yesterday, '13:55:00'), 'Bệnh nhân đã check-in.', @now, @now, 0, 0),
(@appt_completed, 'CHECKED_IN', 'COMPLETED', @u_dr_minh, TIMESTAMP(@yesterday, '15:20:00'), 'Hoàn tất khám.', @now, @now, 0, 0),
(@appt_mai, NULL, 'REQUESTED', @u_patient_mai, TIMESTAMP(@today, '06:30:00'), 'Đặt lịch online.', @now, @now, 0, 0),
(@appt_mai, 'REQUESTED', 'CONFIRMED', @u_reception, TIMESTAMP(@today, '07:10:00'), 'Xác nhận lịch hẹn.', @now, @now, 0, 0),
(@appt_binh, 'CONFIRMED', 'CHECKED_IN', @u_reception, TIMESTAMP(@today, '08:18:00'), 'Bệnh nhân đã đến.', @now, @now, 0, 0),
(@appt_duc, NULL, 'CHECKED_IN', @u_reception, TIMESTAMP(@today, '09:10:00'), 'Khám walk-in.', @now, @now, 0, 0),
(@appt_noshow, 'CONFIRMED', 'NO_SHOW', @u_reception, TIMESTAMP(@today, '14:00:00'), 'Không đến sau thời gian chờ.', @now, @now, 0, 0),
(@appt_cancelled, 'CONFIRMED', 'CANCELLED', @u_reception, TIMESTAMP(@today, '11:05:00'), 'Bệnh nhân yêu cầu hủy.', @now, @now, 0, 0);

INSERT INTO appointment_call_logs (appointment_id, staff_id, call_time, outcome, note)
VALUES
(@appt_mai, @u_reception, TIMESTAMP(@today, '07:05:00'), 'CONFIRMED', 'Gọi xác nhận lịch hẹn sáng nay.'),
(@appt_cancelled, @u_reception, TIMESTAMP(@today, '11:03:00'), 'CANCELLED', 'Bệnh nhân xin hủy và đặt lại sau.'),
(@appt_noshow, @u_reception, TIMESTAMP(@today, '13:55:00'), 'NO_ANSWER', 'Không nghe máy lần 1.');

INSERT INTO encounters
(code, appointment_id, branch_id, specialty_id, doctor_id, patient_id, patient_full_name_snapshot, patient_phone_snapshot, patient_email_snapshot, patient_dob_snapshot, patient_gender_snapshot, intake_reason_for_visit, visit_type, triage_priority, triage_note, insurance_note, emergency_contact_name, emergency_contact_phone, intake_completed_at, intake_completed_by_name, chief_complaint, clinical_note, preliminary_diagnosis, final_diagnosis, conclusion, height_cm, weight_kg, temperature_c, pulse, systolic_bp, diastolic_bp, respiratory_rate, spo2, allergy_snapshot, chronic_disease_snapshot, past_medical_history, physical_examination, treatment_plan, follow_up_date, follow_up_note, status, started_at, completed_at, reopened_count, created_at, updated_at, version, deleted)
VALUES
(CONCAT('ENC-', DATE_FORMAT(@yesterday, '%Y%m%d'), '-001'), @appt_completed, @branch_q1, @sp_cardio, @doc_minh, @p_tung, 'Võ Thanh Tùng', '0909876543', 'tung.vo@example.com', '1995-07-22', 'MALE', 'Đau ngực nhẹ sau vận động, hồi hộp.', 'FOLLOW_UP', 'NORMAL', 'Sinh hiệu ổn.', 'BHYT hợp lệ', 'Võ Minh Tâm', '0909000005', TIMESTAMP(@yesterday, '13:50:00'), 'Nguyễn Minh Tú', 'Đau ngực thoáng qua, hồi hộp.', 'Khám tim phổi chưa ghi nhận bất thường rõ. ECG nhịp xoang.', 'Đau ngực không đặc hiệu, theo dõi tăng huyết áp.', 'Tăng huyết áp vô căn, rối loạn lipid máu.', 'Điều trị nội khoa, tái khám sau 2 tuần hoặc khi đau ngực tăng.', 172, 72, 36.7, 82, 132, 84, 18, 98, NULL, NULL, 'Không ghi nhận bệnh sử nặng.', 'Tim đều, phổi thông khí tốt.', 'Dùng thuốc theo toa, xét nghiệm lipid máu.', DATE_ADD(@today, INTERVAL 13 DAY), 'Mang theo kết quả xét nghiệm khi tái khám.', 'COMPLETED', TIMESTAMP(@yesterday, '14:05:00'), TIMESTAMP(@yesterday, '15:20:00'), 0, @now, @now, 0, 0),
(CONCAT('ENC-', DATE_FORMAT(@today, '%Y%m%d'), '-001'), @appt_binh, @branch_q1, @sp_derma, @doc_huong, @p_binh, 'Trần Văn Bình', '0987654321', 'binh.tran@example.com', '1985-08-20', 'MALE', 'Ngứa da kéo dài vùng cẳng tay.', 'FIRST_VISIT', 'NORMAL', 'Sinh hiệu ổn.', NULL, 'Trần Thị Thu', '0987000002', TIMESTAMP(@today, '08:15:00'), 'Nguyễn Minh Tú', 'Ngứa và đỏ da vùng cẳng tay.', 'Tổn thương dạng mảng đỏ, ranh giới không rõ.', 'Viêm da cơ địa.', NULL, NULL, 168, 70, 36.8, 78, 124, 80, 18, 99, NULL, 'Viêm dạ dày mạn tính.', NULL, 'Da vùng cẳng tay đỏ nhẹ, không bội nhiễm.', 'Kê thuốc bôi, hẹn kiểm tra sau 7 ngày.', DATE_ADD(@today, INTERVAL 7 DAY), 'Tái khám nếu lan rộng hoặc chảy dịch.', 'IN_PROGRESS', TIMESTAMP(@today, '08:35:00'), NULL, 0, @now, @now, 0, 0),
(CONCAT('ENC-', DATE_FORMAT(@today, '%Y%m%d'), '-002'), @appt_duc, @branch_q7, @sp_ped, @doc_nam, @p_duc, 'Phạm Minh Đức', '0918765432', 'duc.pham@example.com', '2020-03-10', 'MALE', 'Sốt cao, ho khan từ tối qua.', 'FIRST_VISIT', 'URGENT', 'Sốt 38.8 độ, SpO2 ổn.', NULL, 'Phạm Thu Hà', '0918000004', TIMESTAMP(@today, '09:08:00'), 'Nguyễn Minh Tú', 'Sốt và ho khan.', 'Họng đỏ nhẹ, phổi chưa nghe ran.', 'Nhiễm trùng hô hấp trên cấp.', NULL, NULL, 108, 18.5, 38.8, 110, 100, 65, 24, 98, NULL, NULL, NULL, 'Họng đỏ, không khó thở.', 'Hạ sốt, theo dõi, làm xét nghiệm công thức máu nếu sốt kéo dài.', DATE_ADD(@today, INTERVAL 3 DAY), 'Tái khám nếu sốt trên 39 độ hoặc khó thở.', 'WAITING_RESULTS', TIMESTAMP(@today, '09:20:00'), NULL, 0, @now, @now, 0, 0);

SELECT id INTO @enc_completed FROM encounters WHERE code = CONCAT('ENC-', DATE_FORMAT(@yesterday, '%Y%m%d'), '-001');
SELECT id INTO @enc_binh FROM encounters WHERE code = CONCAT('ENC-', DATE_FORMAT(@today, '%Y%m%d'), '-001');
SELECT id INTO @enc_duc FROM encounters WHERE code = CONCAT('ENC-', DATE_FORMAT(@today, '%Y%m%d'), '-002');

INSERT INTO encounter_diagnoses (encounter_id, icd10_code_id, diagnosis_type, note, display_order)
VALUES
(@enc_completed, @icd_i10, 'FINAL', 'Huyết áp tại phòng khám cao nhẹ, cần theo dõi tại nhà.', 1),
(@enc_completed, @icd_e78, 'FINAL', 'Khuyến nghị xét nghiệm lipid máu.', 2),
(@enc_binh, @icd_k29, 'PRELIMINARY', 'Bệnh sử viêm dạ dày, lưu ý khi kê thuốc uống.', 1),
(@enc_duc, @icd_j06, 'PRELIMINARY', 'Nhiễm trùng hô hấp trên cấp.', 1),
(@enc_duc, @icd_r07, 'PRELIMINARY', 'Theo dõi nếu trẻ đau ngực/khó thở.', 2);

-- -----------------------------------------------------------------------------
-- 6) Service orders, results, invoices, payment flows
-- -----------------------------------------------------------------------------
INSERT INTO service_orders
(code, encounter_id, ordered_by_doctor_id, branch_id, status, payment_status, estimated_total_amount, ordered_at, paid_at, cancelled_at, note, created_at, updated_at, version, deleted)
VALUES
(CONCAT('SO-', DATE_FORMAT(@yesterday, '%Y%m%d'), '-001'), @enc_completed, @u_dr_minh, @branch_q1, 'COMPLETED', 'PAID', 830000, TIMESTAMP(@yesterday, '14:20:00'), TIMESTAMP(@yesterday, '14:35:00'), NULL, 'Dịch vụ cận lâm sàng tim mạch.', @now, @now, 0, 0),
(CONCAT('SO-', DATE_FORMAT(@today, '%Y%m%d'), '-001'), @enc_binh, @u_dr_huong, @branch_q1, 'PAID', 'PAID', 180000, TIMESTAMP(@today, '08:50:00'), TIMESTAMP(@today, '09:05:00'), NULL, 'Xét nghiệm hỗ trợ trước kê thuốc.', @now, @now, 0, 0),
(CONCAT('SO-', DATE_FORMAT(@today, '%Y%m%d'), '-002'), @enc_duc, @u_dr_nam, @branch_q7, 'IN_PROGRESS', 'PAID', 180000, TIMESTAMP(@today, '09:35:00'), TIMESTAMP(@today, '09:45:00'), NULL, 'Công thức máu cho bệnh nhi sốt.', @now, @now, 0, 0);

SELECT id INTO @so_completed FROM service_orders WHERE code = CONCAT('SO-', DATE_FORMAT(@yesterday, '%Y%m%d'), '-001');
SELECT id INTO @so_binh FROM service_orders WHERE code = CONCAT('SO-', DATE_FORMAT(@today, '%Y%m%d'), '-001');
SELECT id INTO @so_duc FROM service_orders WHERE code = CONCAT('SO-', DATE_FORMAT(@today, '%Y%m%d'), '-002');

INSERT INTO service_order_items
(service_order_id, medical_service_id, service_code_snapshot, service_name_vn_snapshot, service_name_en_snapshot, price_snapshot, quantity, line_total_amount, status, result_status, assigned_department_code, queue_no, queued_at, started_at, completed_at, cancelled_at, note, created_at, updated_at, version, deleted)
VALUES
(@so_completed, @svc_ecg, 'FUNC-ECG', 'Điện tâm đồ', 'Electrocardiogram', 150000, 1, 150000, 'DONE', 'VERIFIED', 'FUNCTIONAL', 2, TIMESTAMP(@yesterday, '14:25:00'), TIMESTAMP(@yesterday, '14:40:00'), TIMESTAMP(@yesterday, '14:55:00'), NULL, NULL, @now, @now, 0, 0),
(@so_completed, @svc_echo, 'IMG-ECHO', 'Siêu âm tim Doppler', 'Doppler echocardiography', 500000, 1, 500000, 'DONE', 'VERIFIED', 'IMAGING', 3, TIMESTAMP(@yesterday, '14:25:00'), TIMESTAMP(@yesterday, '14:55:00'), TIMESTAMP(@yesterday, '15:15:00'), NULL, NULL, @now, @now, 0, 0),
(@so_completed, @svc_cbc, 'LAB-CBC', 'Công thức máu toàn phần', 'Complete blood count', 180000, 1, 180000, 'DONE', 'VERIFIED', 'LAB', 8, TIMESTAMP(@yesterday, '14:25:00'), TIMESTAMP(@yesterday, '14:30:00'), TIMESTAMP(@yesterday, '15:00:00'), NULL, NULL, @now, @now, 0, 0),
(@so_binh, @svc_cbc, 'LAB-CBC', 'Công thức máu toàn phần', 'Complete blood count', 180000, 1, 180000, 'WAITING_EXECUTION', 'DRAFT', 'LAB', 12, TIMESTAMP(@today, '09:05:00'), NULL, NULL, NULL, 'Chờ lấy mẫu.', @now, @now, 0, 0),
(@so_duc, @svc_cbc, 'LAB-CBC', 'Công thức máu toàn phần', 'Complete blood count', 180000, 1, 180000, 'IN_PROGRESS', 'DRAFT', 'LAB', 13, TIMESTAMP(@today, '09:48:00'), TIMESTAMP(@today, '09:55:00'), NULL, NULL, 'Đang xử lý mẫu.', @now, @now, 0, 0);

SELECT id INTO @soi_ecg FROM service_order_items WHERE service_order_id = @so_completed AND medical_service_id = @svc_ecg;
SELECT id INTO @soi_echo FROM service_order_items WHERE service_order_id = @so_completed AND medical_service_id = @svc_echo;
SELECT id INTO @soi_cbc_completed FROM service_order_items WHERE service_order_id = @so_completed AND medical_service_id = @svc_cbc;
SELECT id INTO @soi_cbc_binh FROM service_order_items WHERE service_order_id = @so_binh AND medical_service_id = @svc_cbc;
SELECT id INTO @soi_cbc_duc FROM service_order_items WHERE service_order_id = @so_duc AND medical_service_id = @svc_cbc;

INSERT INTO service_results
(service_order_item_id, result_text_vn, result_text_en, result_data_json, attachment_url, attachment_mime_type, template_code, template_schema_json, field_values_json, conclusion_text, impression_text, attachment_urls_json, report_title, report_pdf_path, report_pdf_status, report_pdf_error_message, report_pdf_generated_at, performed_by_user_id, verified_by_user_id, performed_at, verified_at, status, created_at, updated_at, version, deleted)
VALUES
(@soi_ecg, 'Nhịp xoang, tần số 78 lần/phút. Không ghi nhận ST chênh rõ.', 'Sinus rhythm, HR 78 bpm. No significant ST changes.', '{"heartRate":78,"rhythm":"Sinus"}', '/files/results/ecg-demo.pdf', 'application/pdf', 'IMAGING_REPORT', NULL, NULL, 'Điện tâm đồ trong giới hạn cho phép.', 'No acute ECG abnormality.', '["/files/results/ecg-demo.pdf"]', 'Kết quả điện tâm đồ', '/reports/service-results/ecg-demo.pdf', 'COMPLETED', NULL, TIMESTAMP(@yesterday, '15:00:00'), @u_service_ops, @u_dr_minh, TIMESTAMP(@yesterday, '14:55:00'), TIMESTAMP(@yesterday, '15:05:00'), 'VERIFIED', @now, @now, 0, 0),
(@soi_echo, 'Chức năng thất trái bảo tồn, EF 62%. Không tràn dịch màng tim.', 'Preserved LV function, EF 62%. No pericardial effusion.', '{"EF":"62%","pericardialEffusion":false}', '/files/results/echo-demo.pdf', 'application/pdf', 'IMAGING_REPORT', NULL, NULL, 'Siêu âm tim chưa ghi nhận bất thường nặng.', 'Preserved systolic function.', '["/files/results/echo-demo.pdf"]', 'Kết quả siêu âm tim', '/reports/service-results/echo-demo.pdf', 'COMPLETED', NULL, TIMESTAMP(@yesterday, '15:18:00'), @u_service_ops, @u_dr_minh, TIMESTAMP(@yesterday, '15:15:00'), TIMESTAMP(@yesterday, '15:18:00'), 'VERIFIED', @now, @now, 0, 0),
(@soi_cbc_completed, 'Bạch cầu trong giới hạn, Hb 145 g/L, tiểu cầu 245 G/L.', 'WBC normal, Hb 145 g/L, platelet 245 G/L.', '{"WBC":"7.2 G/L","HGB":"145 g/L","PLT":"245 G/L"}', NULL, NULL, 'LAB_TABLE', '{"fields":["WBC","HGB","PLT"]}', '{"WBC":"7.2","HGB":"145","PLT":"245"}', 'Công thức máu trong giới hạn tham chiếu.', NULL, NULL, 'Kết quả công thức máu', '/reports/service-results/cbc-demo.pdf', 'COMPLETED', NULL, TIMESTAMP(@yesterday, '15:03:00'), @u_service_ops, @u_dr_minh, TIMESTAMP(@yesterday, '15:00:00'), TIMESTAMP(@yesterday, '15:03:00'), 'VERIFIED', @now, @now, 0, 0),
(@soi_cbc_binh, NULL, NULL, NULL, NULL, NULL, 'LAB_TABLE', '{"fields":["WBC","HGB","PLT"]}', NULL, NULL, NULL, NULL, 'Kết quả công thức máu', NULL, 'PENDING', NULL, NULL, NULL, NULL, NULL, NULL, 'DRAFT', @now, @now, 0, 0),
(@soi_cbc_duc, NULL, NULL, NULL, NULL, NULL, 'LAB_TABLE', '{"fields":["WBC","HGB","PLT"]}', NULL, NULL, NULL, NULL, 'Kết quả công thức máu', NULL, 'PENDING', NULL, NULL, @u_service_ops, NULL, TIMESTAMP(@today, '09:55:00'), NULL, 'DRAFT', @now, @now, 0, 0);

SELECT id INTO @sr_ecg FROM service_results WHERE service_order_item_id = @soi_ecg;
SELECT id INTO @sr_echo FROM service_results WHERE service_order_item_id = @soi_echo;
SELECT id INTO @sr_cbc_completed FROM service_results WHERE service_order_item_id = @soi_cbc_completed;
SELECT id INTO @sr_cbc_binh FROM service_results WHERE service_order_item_id = @soi_cbc_binh;
SELECT id INTO @sr_cbc_duc FROM service_results WHERE service_order_item_id = @soi_cbc_duc;

INSERT INTO service_result_status_histories
(service_result_id, from_status, to_status, changed_by, changed_at, note, created_at, updated_at, version, deleted)
VALUES
(@sr_ecg, 'DRAFT', 'COMPLETED', @u_service_ops, TIMESTAMP(@yesterday, '15:00:00'), 'Hoàn tất nhập kết quả.', @now, @now, 0, 0),
(@sr_ecg, 'COMPLETED', 'VERIFIED', @u_dr_minh, TIMESTAMP(@yesterday, '15:05:00'), 'Bác sĩ xác nhận.', @now, @now, 0, 0),
(@sr_echo, 'DRAFT', 'VERIFIED', @u_dr_minh, TIMESTAMP(@yesterday, '15:18:00'), 'Kết quả đã xác nhận.', @now, @now, 0, 0),
(@sr_cbc_completed, 'DRAFT', 'VERIFIED', @u_dr_minh, TIMESTAMP(@yesterday, '15:03:00'), 'Kết quả xét nghiệm đã xác nhận.', @now, @now, 0, 0),
(@sr_cbc_duc, NULL, 'DRAFT', @u_service_ops, TIMESTAMP(@today, '09:55:00'), 'Đang xử lý mẫu.', @now, @now, 0, 0);

INSERT INTO invoices
(code, service_order_id, cashier_id, subtotal_amount, discount_amount, tax_amount, total_amount, payment_method, payment_status, payment_reference, transfer_content, payment_detected_at, payment_review_reason, vnp_txn_ref, vnp_payment_url, paid_at, note, created_at, updated_at, version, deleted)
VALUES
(CONCAT('INV-', DATE_FORMAT(@yesterday, '%Y%m%d'), '-001'), @so_completed, @u_cashier, 830000, 0, 0, 830000, 'CASH', 'PAID', CONCAT('PMT', DATE_FORMAT(@yesterday, '%Y%m%d'), '001'), NULL, NULL, NULL, NULL, NULL, TIMESTAMP(@yesterday, '14:35:00'), 'Thanh toán tiền mặt tại quầy.', @now, @now, 0, 0),
(CONCAT('INV-', DATE_FORMAT(@today, '%Y%m%d'), '-001'), @so_binh, @u_cashier, 180000, 0, 0, 180000, 'BANK_TRANSFER', 'PAID', CONCAT('PMT', DATE_FORMAT(@today, '%Y%m%d'), '001'), CONCAT('THANH TOAN HOA DON ', DATE_FORMAT(@today, '%Y%m%d'), '001'), TIMESTAMP(@today, '09:05:30'), NULL, NULL, NULL, TIMESTAMP(@today, '09:06:00'), 'Đã khớp giao dịch chuyển khoản.', @now, @now, 0, 0),
(CONCAT('INV-', DATE_FORMAT(@today, '%Y%m%d'), '-002'), @so_duc, @u_cashier, 180000, 0, 0, 180000, 'VNPAY', 'PAID', CONCAT('PMT', DATE_FORMAT(@today, '%Y%m%d'), '002'), NULL, TIMESTAMP(@today, '09:45:30'), NULL, CONCAT('VNP', DATE_FORMAT(@today, '%Y%m%d'), '002'), 'https://pay.vnpay.vn/mock/primecare', TIMESTAMP(@today, '09:46:00'), 'Thanh toán VNPAY thành công.', @now, @now, 0, 0);

SELECT id INTO @inv_completed FROM invoices WHERE code = CONCAT('INV-', DATE_FORMAT(@yesterday, '%Y%m%d'), '-001');
SELECT id INTO @inv_binh FROM invoices WHERE code = CONCAT('INV-', DATE_FORMAT(@today, '%Y%m%d'), '-001');
SELECT id INTO @inv_duc FROM invoices WHERE code = CONCAT('INV-', DATE_FORMAT(@today, '%Y%m%d'), '-002');

INSERT INTO invoice_items
(invoice_id, reference_type, reference_id, name_snapshot, unit_price, quantity, tax_rate, subtotal_amount, tax_amount, total_amount, created_at, updated_at, version, deleted)
VALUES
(@inv_completed, 'CLINICAL_SERVICE', @soi_ecg, 'Điện tâm đồ', 150000, 1, 0.00, 150000, 0, 150000, @now, @now, 0, 0),
(@inv_completed, 'CLINICAL_SERVICE', @soi_echo, 'Siêu âm tim Doppler', 500000, 1, 0.00, 500000, 0, 500000, @now, @now, 0, 0),
(@inv_completed, 'CLINICAL_SERVICE', @soi_cbc_completed, 'Công thức máu toàn phần', 180000, 1, 0.00, 180000, 0, 180000, @now, @now, 0, 0),
(@inv_binh, 'CLINICAL_SERVICE', @soi_cbc_binh, 'Công thức máu toàn phần', 180000, 1, 0.00, 180000, 0, 180000, @now, @now, 0, 0),
(@inv_duc, 'CLINICAL_SERVICE', @soi_cbc_duc, 'Công thức máu toàn phần', 180000, 1, 0.00, 180000, 0, 180000, @now, @now, 0, 0);

INSERT INTO invoice_status_histories
(invoice_id, from_status, to_status, changed_by, changed_at, note, created_at, updated_at, version, deleted)
VALUES
(@inv_completed, 'UNPAID', 'PAID', @u_cashier, TIMESTAMP(@yesterday, '14:35:00'), 'Thanh toán tiền mặt.', @now, @now, 0, 0),
(@inv_binh, 'UNPAID', 'PAID', @u_cashier, TIMESTAMP(@today, '09:06:00'), 'Khớp chuyển khoản tự động.', @now, @now, 0, 0),
(@inv_duc, 'UNPAID', 'PAID', @u_cashier, TIMESTAMP(@today, '09:46:00'), 'VNPAY callback thành công.', @now, @now, 0, 0);

INSERT INTO payment_intents
(invoice_id, provider, payment_reference, transfer_content, bank_code, bank_account_no, bank_account_name, expected_amount, qr_payload, status, expires_at, detected_at, confirmed_at, matched_transaction_ref, review_reason, note, created_at, updated_at, version, deleted)
VALUES
(@inv_binh, 'VIETQR', CONCAT('PMT', DATE_FORMAT(@today, '%Y%m%d'), '001'), CONCAT('THANH TOAN HOA DON ', DATE_FORMAT(@today, '%Y%m%d'), '001'), 'VCB', '1020304050', 'CONG TY PRIMECARE', 180000, '00020101021238540010A00000072701240006970436011010203040500208QRIBFTTA530370454061800005802VN6304ABCD', 'CONFIRMED', TIMESTAMP(@today, '09:35:00'), TIMESTAMP(@today, '09:05:30'), TIMESTAMP(@today, '09:06:00'), CONCAT('BANK-', DATE_FORMAT(@today, '%Y%m%d'), '-001'), NULL, 'Seed payment intent đã thanh toán.', @now, @now, 0, 0),
(@inv_duc, 'MANUAL_BANK_TRANSFER', CONCAT('PMT', DATE_FORMAT(@today, '%Y%m%d'), '002'), CONCAT('THANH TOAN HOA DON ', DATE_FORMAT(@today, '%Y%m%d'), '002'), 'VCB', '1020304050', 'CONG TY PRIMECARE', 180000, NULL, 'CONFIRMED', TIMESTAMP(@today, '10:15:00'), TIMESTAMP(@today, '09:45:30'), TIMESTAMP(@today, '09:46:00'), CONCAT('VNP-', DATE_FORMAT(@today, '%Y%m%d'), '-002'), NULL, 'Seed payment intent VNPAY.', @now, @now, 0, 0);

INSERT INTO bank_transactions
(provider, transaction_ref, amount, transfer_content, bank_account_no, bank_code, transaction_time, status, matched_invoice_id, matched_at, review_reason, raw_payload, created_at, updated_at, version, deleted)
VALUES
('VIETQR_WEBHOOK', CONCAT('BANK-', DATE_FORMAT(@today, '%Y%m%d'), '-001'), 180000, CONCAT('THANH TOAN HOA DON ', DATE_FORMAT(@today, '%Y%m%d'), '001'), '1020304050', 'VCB', TIMESTAMP(@today, '09:05:30'), 'MATCHED', @inv_binh, TIMESTAMP(@today, '09:06:00'), NULL, '{"source":"seed","matched":true}', @now, @now, 0, 0),
('VIETQR_WEBHOOK', CONCAT('BANK-', DATE_FORMAT(@today, '%Y%m%d'), '-REVIEW'), 250000, 'NOI DUNG KHONG KHOP', '1020304050', 'VCB', TIMESTAMP(@today, '10:10:00'), 'REVIEW', NULL, NULL, 'Không tìm thấy mã hóa đơn trong nội dung chuyển khoản.', '{"source":"seed","matched":false}', @now, @now, 0, 0)
ON DUPLICATE KEY UPDATE status = VALUES(status), matched_invoice_id = VALUES(matched_invoice_id), updated_at = @now;

INSERT INTO refund_records
(invoice_id, refund_amount, reason, approved_by_user_id, refunded_at, created_at, updated_at, version, deleted)
VALUES
(@inv_completed, 50000, 'Hoàn tiền phần chênh lệch do điều chỉnh dịch vụ khuyến mãi.', @u_ops, TIMESTAMP(@today, '10:30:00'), @now, @now, 0, 0);

-- -----------------------------------------------------------------------------
-- 7) Prescriptions and pharmacy flow
-- -----------------------------------------------------------------------------
INSERT INTO prescriptions
(code, encounter_id, doctor_user_id, issued_date, general_note, status, created_at, updated_at, version, deleted)
VALUES
(CONCAT('RX-', DATE_FORMAT(@yesterday, '%Y%m%d'), '-001'), @enc_completed, @u_dr_minh, @yesterday, 'Uống thuốc đúng hướng dẫn, tự theo dõi huyết áp tại nhà.', 'ISSUED', @now, @now, 0, 0),
(CONCAT('RX-', DATE_FORMAT(@today, '%Y%m%d'), '-001'), @enc_binh, @u_dr_huong, @today, 'Bôi thuốc vùng tổn thương, tránh gãi và tránh xà phòng mạnh.', 'DRAFT', @now, @now, 0, 0);

SELECT id INTO @rx_completed FROM prescriptions WHERE code = CONCAT('RX-', DATE_FORMAT(@yesterday, '%Y%m%d'), '-001');
SELECT id INTO @rx_binh FROM prescriptions WHERE code = CONCAT('RX-', DATE_FORMAT(@today, '%Y%m%d'), '-001');

INSERT INTO prescription_items
(prescription_id, medication_id, medication_code_snapshot, medication_name_snapshot, strength_snapshot, dosage_form_snapshot, unit_snapshot, quantity, dose, frequency, duration_days, route, instruction)
VALUES
(@rx_completed, @med_los, 'MED-LOS50', 'Losartan 50mg', '50mg', 'Viên nén', 'Viên', 30, '1 viên', '1 lần/ngày', 30, 'Uống', 'Uống buổi sáng sau ăn.'),
(@rx_completed, @med_ator, 'MED-ATOR20', 'Atorvastatin 20mg', '20mg', 'Viên nén', 'Viên', 30, '1 viên', '1 lần/ngày', 30, 'Uống', 'Uống buổi tối.'),
(@rx_completed, @med_para, 'MED-PARA500', 'Paracetamol 500mg', '500mg', 'Viên nén', 'Viên', 10, '1 viên', 'Khi đau', 5, 'Uống', 'Chỉ dùng khi đau, tối đa 4 viên/ngày.'),
(@rx_binh, @med_cet, 'MED-CET10', 'Cetirizine 10mg', '10mg', 'Viên nén', 'Viên', 7, '1 viên', '1 lần/ngày', 7, 'Uống', 'Uống buổi tối nếu ngứa nhiều.');

INSERT INTO inventory_transactions
(batch_id, transaction_type, quantity, reason, reference_type, reference_id, performed_by_user_id, performed_at, created_at, updated_at, version, deleted)
VALUES
(@batch_los, 'DISPENSED', -30, 'Xuất thuốc theo toa.', 'PRESCRIPTION', @rx_completed, @u_pharmacist, TIMESTAMP(@yesterday, '15:30:00'), @now, @now, 0, 0),
(@batch_para, 'DISPENSED', -10, 'Xuất thuốc theo toa.', 'PRESCRIPTION', @rx_completed, @u_pharmacist, TIMESTAMP(@yesterday, '15:30:00'), @now, @now, 0, 0);

-- -----------------------------------------------------------------------------
-- 8) Notifications, tokens, files and PDF jobs
-- -----------------------------------------------------------------------------
INSERT INTO notifications
(channel, template_code, entity_type, entity_id, appointment_id, to_phone, to_email, content, status, provider, provider_message_id, error_message, scheduled_at, last_attempt_at, attempt_count, sent_at, created_at, updated_at, version, deleted)
VALUES
('EMAIL', 'APPOINTMENT_CONFIRMED', 'APPOINTMENT', @appt_mai, @appt_mai, NULL, 'mai.nguyen@example.com', 'Lịch hẹn của quý khách tại PrimeCare đã được xác nhận.', 'SENT', 'MOCK_MAIL', 'mail-seed-001', NULL, NULL, TIMESTAMP(@today, '07:11:00'), 1, TIMESTAMP(@today, '07:11:00'), @now, @now, 0, 0),
('SMS', 'APPOINTMENT_REMINDER', 'APPOINTMENT', @appt_mai, @appt_mai, '0912345678', NULL, 'PrimeCare nhắc lịch khám của quý khách trong hôm nay.', 'PENDING', 'MOCK_SMS', NULL, NULL, TIMESTAMP(@today, '07:30:00'), NULL, 0, NULL, @now, @now, 0, 0),
('EMAIL', 'RESULT_READY', 'SERVICE_RESULT', @sr_cbc_completed, @appt_completed, NULL, 'tung.vo@example.com', 'Kết quả xét nghiệm của quý khách đã sẵn sàng.', 'DELIVERED', 'MOCK_MAIL', 'mail-seed-002', NULL, NULL, TIMESTAMP(@yesterday, '15:10:00'), 1, TIMESTAMP(@yesterday, '15:10:00'), @now, @now, 0, 0),
('EMAIL', 'INVOICE_PAID', 'INVOICE', @inv_binh, @appt_binh, NULL, 'binh.tran@example.com', 'Hóa đơn của quý khách đã thanh toán thành công.', 'SENT', 'MOCK_MAIL', 'mail-seed-003', NULL, NULL, TIMESTAMP(@today, '09:07:00'), 1, TIMESTAMP(@today, '09:07:00'), @now, @now, 0, 0),
('SMS', 'OTP_PUBLIC_LOOKUP', 'OTP', @appt_lananh, @appt_lananh, '0932123456', NULL, 'Ma OTP tra cuu lich hen PrimeCare cua quy khach la 123456.', 'FAILED', 'MOCK_SMS', NULL, 'Simulated SMS failure for test.', NULL, TIMESTAMP(@today, '10:05:00'), 1, NULL, @now, @now, 0, 0);

SELECT id INTO @notif_confirm FROM notifications WHERE provider_message_id = 'mail-seed-001';
SELECT id INTO @notif_result FROM notifications WHERE provider_message_id = 'mail-seed-002';
SELECT id INTO @notif_invoice FROM notifications WHERE provider_message_id = 'mail-seed-003';
SELECT id INTO @notif_failed FROM notifications WHERE template_code = 'OTP_PUBLIC_LOOKUP' AND to_phone = '0932123456' ORDER BY id DESC LIMIT 1;

INSERT INTO notification_attempts
(notification_id, attempt_no, status, provider_message_id, error_message, response_excerpt, attempted_at, created_at, updated_at, version, deleted)
VALUES
(@notif_confirm, 1, 'SENT', 'mail-seed-001', NULL, '250 OK mock mail accepted', TIMESTAMP(@today, '07:11:00'), @now, @now, 0, 0),
(@notif_result, 1, 'DELIVERED', 'mail-seed-002', NULL, 'delivered mock webhook', TIMESTAMP(@yesterday, '15:10:00'), @now, @now, 0, 0),
(@notif_invoice, 1, 'SENT', 'mail-seed-003', NULL, '250 OK mock mail accepted', TIMESTAMP(@today, '09:07:00'), @now, @now, 0, 0),
(@notif_failed, 1, 'FAILED', NULL, 'Simulated SMS failure for test.', 'mock provider timeout', TIMESTAMP(@today, '10:05:00'), @now, @now, 0, 0);

INSERT INTO files
(owner_type, owner_id, file_name, mime_type, file_size, storage_provider, storage_path, created_by, created_at)
VALUES
('SERVICE_RESULT', @sr_ecg, 'ecg-demo.pdf', 'application/pdf', 245760, 'LOCAL', '/tmp/primecare-storage/results/ecg-demo.pdf', @u_service_ops, TIMESTAMP(@yesterday, '15:00:00')),
('SERVICE_RESULT', @sr_echo, 'echo-demo.pdf', 'application/pdf', 524288, 'LOCAL', '/tmp/primecare-storage/results/echo-demo.pdf', @u_service_ops, TIMESTAMP(@yesterday, '15:18:00')),
('PATIENT', @p_mai, 'identity-mai-demo.jpg', 'image/jpeg', 185120, 'LOCAL', '/tmp/primecare-storage/patients/identity-mai-demo.jpg', @u_reception, TIMESTAMP(@today, '07:00:00')),
('INVOICE', @inv_completed, 'invoice-demo.pdf', 'application/pdf', 210000, 'LOCAL', '/tmp/primecare-storage/invoices/invoice-demo.pdf', @u_cashier, TIMESTAMP(@yesterday, '14:40:00'));

INSERT INTO appointment_pdf_jobs
(appointment_id, status, file_path, error_message, created_at, updated_at, version, deleted)
VALUES
(@appt_mai, 'COMPLETED', '/tmp/primecare-storage/appointments/appointment-mai.pdf', NULL, @now, @now, 0, 0)
ON DUPLICATE KEY UPDATE status = VALUES(status), file_path = VALUES(file_path), updated_at = @now;

INSERT INTO invoice_pdf_jobs
(invoice_id, requested_by, status, file_path, error_message, created_at, updated_at, version, deleted)
VALUES
(@inv_completed, @u_cashier, 'COMPLETED', '/tmp/primecare-storage/invoices/invoice-completed.pdf', NULL, @now, @now, 0, 0),
(@inv_binh, @u_cashier, 'PENDING', NULL, NULL, @now, @now, 0, 0);

INSERT INTO prescription_pdf_jobs
(prescription_id, requested_by, status, file_path, error_message, created_at, updated_at, version, deleted)
VALUES
(@rx_completed, @u_dr_minh, 'COMPLETED', '/tmp/primecare-storage/prescriptions/rx-completed.pdf', NULL, @now, @now, 0, 0),
(@rx_binh, @u_dr_huong, 'PENDING', NULL, NULL, @now, @now, 0, 0);

INSERT INTO invoice_pdf_jobs
(invoice_id, requested_by, status, file_path, error_message, created_at, updated_at, version, deleted)
VALUES
(@inv_duc, @u_cashier, 'PROCESSING', NULL, NULL, @now, @now, 0, 0);

-- Runtime auth/demo tokens. These are intentionally non-sensitive fake values.
INSERT IGNORE INTO credential_setup_tokens
(user_id, token_hash, purpose, delivery_channel, delivery_target, requested_by_user_id, expires_at, used_at, revoked, revoked_at, note, created_at, updated_at, version, deleted)
VALUES
(@u_service_ops, SHA2('seed-setup-token-service-ops', 256), 'ACCOUNT_SETUP', 'EMAIL', 'service.ops@primecare.vn', @u_sysadmin, DATE_ADD(@now, INTERVAL 30 MINUTE), NULL, 0, NULL, 'Seed token for UI testing only.', @now, @now, 0, 0);

INSERT IGNORE INTO refresh_tokens
(user_id, token, expires_at, revoked, created_at, revoked_at, user_agent, ip_address, family_id, replaced_by_token)
VALUES
(@u_patient_mai, 'seed-refresh-token-patient-mai', DATE_ADD(@now, INTERVAL 14 DAY), 0, @now, NULL, 'PrimeCare Seed Browser', '127.0.0.1', 'seed-family-mai', NULL),
(@u_sysadmin, 'seed-refresh-token-revoked-admin', DATE_ADD(@now, INTERVAL 14 DAY), 1, @now, @now, 'PrimeCare Seed Browser', '127.0.0.1', 'seed-family-admin', 'seed-refresh-token-new-admin');

INSERT IGNORE INTO access_token_blacklist
(jti, user_id, expires_at, revoked_at, reason)
VALUES
('seedblacklistedjti0000000000000001', @u_sysadmin, DATE_ADD(@now, INTERVAL 15 MINUTE), @now, 'Seed revoked token for blacklist testing');

INSERT INTO public_lookup_access_tokens
(token, lookup_type, reference_id, reference_code, expires_at, consumed_at, created_at, updated_at, version, deleted)
VALUES
('seed-public-lookup-token-appointment-mai', 'APPOINTMENT', @appt_mai, CONCAT('APT-', DATE_FORMAT(@today, '%Y%m%d'), '-001'), DATE_ADD(@now, INTERVAL 20 MINUTE), NULL, @now, @now, 0, 0)
ON DUPLICATE KEY UPDATE expires_at = VALUES(expires_at), consumed_at = NULL, updated_at = @now;

INSERT INTO public_lookup_otps
(lookup_type, reference_id, reference_code, target_email, otp_code, expires_at, consumed_at, attempt_count, created_at, updated_at, version, deleted)
VALUES
('APPOINTMENT', @appt_lananh, CONCAT('APT-', DATE_FORMAT(@tomorrow, '%Y%m%d'), '-001'), 'lananh.dang@example.com', SHA2('123456', 256), DATE_ADD(@now, INTERVAL 10 MINUTE), NULL, 1, @now, @now, 0, 0);

INSERT INTO rate_limit_events (key_type, key_value, event_type, created_at)
VALUES
('IP', '127.0.0.1', 'LOGIN_ATTEMPT', DATE_SUB(@now, INTERVAL 5 MINUTE)),
('PHONE', '0912345678', 'OTP_REQUEST', DATE_SUB(@now, INTERVAL 3 MINUTE)),
('IP', '127.0.0.1', 'PUBLIC_LOOKUP', DATE_SUB(@now, INTERVAL 2 MINUTE));

-- -----------------------------------------------------------------------------
-- 9) CMS, leave requests, audit, contact submissions
-- -----------------------------------------------------------------------------
INSERT INTO faqs
(category, status, question_vn, question_en, answer_vn, answer_en, created_at, updated_at, version, deleted)
VALUES
('booking', 'PUBLISHED', 'Tôi có thể đặt lịch khám online không?', 'Can I book an appointment online?', 'Có. Bạn chọn chi nhánh, chuyên khoa, bác sĩ, ngày khám và gửi yêu cầu đặt lịch trên website.', 'Yes. Select branch, specialty, doctor and date, then submit the booking request online.', @now, @now, 0, 0),
('payment', 'PUBLISHED', 'PrimeCare hỗ trợ phương thức thanh toán nào?', 'Which payment methods are supported?', 'PrimeCare hỗ trợ tiền mặt, chuyển khoản, thẻ và VNPAY tùy từng dịch vụ.', 'PrimeCare supports cash, bank transfer, card and VNPAY depending on service.', @now, @now, 0, 0),
('result', 'PUBLISHED', 'Tôi tra cứu kết quả xét nghiệm ở đâu?', 'Where can I view my test results?', 'Bạn có thể đăng nhập cổng bệnh nhân hoặc dùng mã tra cứu được gửi qua email/SMS.', 'You can sign in to the patient portal or use the lookup code sent by email/SMS.', @now, @now, 0, 0);

INSERT INTO articles
(title_vn, title_en, slug_vn, slug_en, summary_vn, summary_en, content_vn, content_en, thumbnail_url, status, published_at, created_by, created_at, updated_at, version, deleted)
VALUES
('5 lưu ý khi khám sức khỏe tổng quát', '5 tips for general health checkups', '5-luu-y-khi-kham-suc-khoe-tong-quat', '5-tips-for-general-health-checkups', 'Chuẩn bị đúng giúp buổi khám sức khỏe diễn ra nhanh và chính xác hơn.', 'Proper preparation makes checkups faster and more accurate.', '<p>Nên nhịn ăn theo hướng dẫn, mang theo hồ sơ bệnh án cũ và danh sách thuốc đang dùng.</p>', '<p>Follow fasting instructions, bring prior medical records and your medication list.</p>', NULL, 'PUBLISHED', TIMESTAMP(@today, '07:00:00'), @u_ops, @now, @now, 0, 0),
('Dấu hiệu cần khám tim mạch sớm', 'When to see a cardiologist', 'dau-hieu-can-kham-tim-mach-som', 'when-to-see-a-cardiologist', 'Đau ngực, khó thở khi gắng sức hoặc hồi hộp kéo dài là các dấu hiệu cần chú ý.', 'Chest pain, exertional dyspnea or persistent palpitation should be evaluated.', '<p>Nếu triệu chứng xuất hiện lặp lại, người bệnh nên đặt lịch khám chuyên khoa tim mạch.</p>', '<p>If symptoms recur, patients should book a cardiology consultation.</p>', NULL, 'PUBLISHED', TIMESTAMP(@today, '07:30:00'), @u_ops, @now, @now, 0, 0)
ON DUPLICATE KEY UPDATE title_vn = VALUES(title_vn), status = VALUES(status), updated_at = @now;

INSERT INTO doctor_leave_requests
(doctor_id, start_date, end_date, start_session, end_session, reason, status, reviewed_by, reviewed_at, review_note)
VALUES
(@doc_minh, DATE_ADD(@today, INTERVAL 10 DAY), DATE_ADD(@today, INTERVAL 12 DAY), 'AM', 'PM', 'Tham dự hội nghị tim mạch.', 'APPROVED', @u_ops, TIMESTAMP(@today, '08:30:00'), 'Đã bố trí bác sĩ thay thế.'),
(@doc_huong, DATE_ADD(@today, INTERVAL 5 DAY), DATE_ADD(@today, INTERVAL 5 DAY), 'PM', 'PM', 'Việc gia đình.', 'PENDING', NULL, NULL, NULL),
(@doc_nam, DATE_SUB(@today, INTERVAL 2 DAY), DATE_SUB(@today, INTERVAL 2 DAY), 'AM', 'PM', 'Nghỉ cá nhân.', 'REJECTED', @u_ops, TIMESTAMP(@yesterday, '16:30:00'), 'Ngày này lịch nhi khoa đã kín.');

INSERT INTO public_contact_submissions
(reference_code, full_name, email, phone, message, source_page, requester_ip, user_agent, status, emailed_at, email_error, created_at, updated_at)
VALUES
(CONCAT('CONTACT-', DATE_FORMAT(@today, '%Y%m%d'), '-001'), 'Nguyễn Khách Test', 'guest@example.com', '0909999999', 'Tôi muốn được tư vấn gói khám tổng quát cho gia đình.', '/contact', '127.0.0.1', 'PrimeCare Seed Browser', 'RECEIVED', NULL, NULL, @now, @now),
(CONCAT('CONTACT-', DATE_FORMAT(@today, '%Y%m%d'), '-002'), 'Công ty ABC', 'hr@abc.example', '0283999888', 'Vui lòng gửi báo giá khám sức khỏe doanh nghiệp.', '/contact', '127.0.0.1', 'PrimeCare Seed Browser', 'EMAILED', TIMESTAMP(@today, '09:30:00'), NULL, @now, @now)
ON DUPLICATE KEY UPDATE status = VALUES(status), updated_at = @now;

INSERT INTO audit_logs
(actor_id, actor_role, action, entity, entity_id, before_json, after_json, ip_address, user_agent, created_at)
VALUES
(@u_reception, 'STAFF', 'CREATE', 'Appointment', @appt_mai, NULL, JSON_OBJECT('id', @appt_mai, 'status', 'REQUESTED'), '127.0.0.1', 'PrimeCare Seed Browser', TIMESTAMP(@today, '06:30:00')),
(@u_reception, 'STAFF', 'UPDATE', 'Appointment', @appt_mai, JSON_OBJECT('status', 'REQUESTED'), JSON_OBJECT('status', 'CONFIRMED'), '127.0.0.1', 'PrimeCare Seed Browser', TIMESTAMP(@today, '07:10:00')),
(@u_dr_minh, 'DOCTOR', 'CREATE', 'Encounter', @enc_completed, NULL, JSON_OBJECT('id', @enc_completed, 'status', 'COMPLETED'), '127.0.0.1', 'PrimeCare Seed Browser', TIMESTAMP(@yesterday, '15:20:00')),
(@u_cashier, 'CASHIER', 'UPDATE', 'Invoice', @inv_binh, JSON_OBJECT('paymentStatus', 'UNPAID'), JSON_OBJECT('paymentStatus', 'PAID'), '127.0.0.1', 'PrimeCare Seed Browser', TIMESTAMP(@today, '09:06:00')),
(@u_ops, 'OPERATIONS_ADMIN', 'CREATE', 'Article', NULL, NULL, JSON_OBJECT('slug', '5-luu-y-khi-kham-suc-khoe-tong-quat'), '127.0.0.1', 'PrimeCare Seed Browser', TIMESTAMP(@today, '07:00:00'));

-- -----------------------------------------------------------------------------
-- 10) Final consistency touches
-- -----------------------------------------------------------------------------
UPDATE medication_batches SET quantity_in_stock = quantity_in_stock - 30 WHERE id = @batch_los;
UPDATE medication_batches SET quantity_in_stock = quantity_in_stock - 30 WHERE id = @batch_para;

UPDATE encounters SET status = 'COMPLETED', completed_at = TIMESTAMP(@yesterday, '15:20:00') WHERE id = @enc_completed;
UPDATE service_orders SET status = 'COMPLETED', payment_status = 'PAID', paid_at = TIMESTAMP(@yesterday, '14:35:00') WHERE id = @so_completed;
UPDATE service_orders SET status = 'PAID', payment_status = 'PAID', paid_at = TIMESTAMP(@today, '09:05:00') WHERE id = @so_binh;
UPDATE service_orders SET status = 'IN_PROGRESS', payment_status = 'PAID', paid_at = TIMESTAMP(@today, '09:45:00') WHERE id = @so_duc;

-- Quick reference:
--   All seeded accounts use password: password
--   sysadmin@primecare.vn        SYSTEM_ADMIN
--   ops@primecare.vn             OPERATIONS_ADMIN
--   reception@primecare.vn       STAFF / reception
--   cashier@primecare.vn         CASHIER
--   pharmacist@primecare.vn      PHARMACIST
--   dr.minh@primecare.vn         DOCTOR / cardiology
--   dr.huong@primecare.vn        DOCTOR / dermatology
--   mai.nguyen@example.com       PATIENT
--   binh.tran@example.com        PATIENT
