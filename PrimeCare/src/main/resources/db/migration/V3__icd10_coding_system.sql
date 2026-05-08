-- ============================================================
-- V3: ICD-10 Coding System
-- Creates icd10_codes and encounter_diagnoses tables
-- Seeds common ICD-10 codes used in Vietnamese clinics
-- ============================================================

CREATE TABLE icd10_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    code VARCHAR(16) NOT NULL,
    name_vn VARCHAR(500) NOT NULL,
    name_en VARCHAR(500),
    category VARCHAR(64),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    version BIGINT DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at DATETIME NULL,
    deleted_by BIGINT NULL,
    UNIQUE KEY uq_icd10_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_icd10_code ON icd10_codes(code);
CREATE INDEX idx_icd10_name_vn ON icd10_codes(name_vn(100));
CREATE INDEX idx_icd10_category ON icd10_codes(category);

CREATE TABLE encounter_diagnoses (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    encounter_id BIGINT NOT NULL,
    icd10_code_id BIGINT NOT NULL,
    diagnosis_type VARCHAR(16) NOT NULL,
    note TEXT,
    display_order INT NOT NULL DEFAULT 0,
    CONSTRAINT fk_enc_diag_encounter FOREIGN KEY (encounter_id) REFERENCES encounters(id),
    CONSTRAINT fk_enc_diag_icd10 FOREIGN KEY (icd10_code_id) REFERENCES icd10_codes(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE INDEX idx_enc_diag_encounter ON encounter_diagnoses(encounter_id);
CREATE INDEX idx_enc_diag_icd10 ON encounter_diagnoses(icd10_code_id);

-- ============================================================
-- Seed common ICD-10 codes (Vietnamese clinic essentials)
-- ============================================================

INSERT INTO icd10_codes (code, name_vn, name_en, category) VALUES
-- Bệnh nhiễm trùng
('A09', 'Tiêu chảy và viêm dạ dày ruột do nhiễm trùng', 'Infectious gastroenteritis and colitis', 'INFECTIOUS'),
('A15', 'Lao phổi', 'Respiratory tuberculosis', 'INFECTIOUS'),
('B34.9', 'Nhiễm virus không xác định', 'Viral infection, unspecified', 'INFECTIOUS'),

-- Bệnh nội tiết / chuyển hóa
('E10', 'Đái tháo đường type 1', 'Type 1 diabetes mellitus', 'ENDOCRINE'),
('E11', 'Đái tháo đường type 2', 'Type 2 diabetes mellitus', 'ENDOCRINE'),
('E11.9', 'Đái tháo đường type 2 không biến chứng', 'Type 2 diabetes mellitus without complications', 'ENDOCRINE'),
('E78.0', 'Tăng cholesterol máu đơn thuần', 'Pure hypercholesterolemia', 'ENDOCRINE'),
('E78.5', 'Rối loạn lipid máu không xác định', 'Dyslipidemia, unspecified', 'ENDOCRINE'),

-- Bệnh tâm thần / thần kinh
('F32', 'Rối loạn trầm cảm', 'Major depressive disorder, single episode', 'MENTAL'),
('F41.0', 'Rối loạn hoảng sợ', 'Panic disorder', 'MENTAL'),
('F41.1', 'Rối loạn lo âu lan tỏa', 'Generalized anxiety disorder', 'MENTAL'),
('G43', 'Đau nửa đầu (migraine)', 'Migraine', 'NEUROLOGICAL'),
('G47.0', 'Mất ngủ', 'Insomnia', 'NEUROLOGICAL'),

-- Bệnh mắt / tai
('H10.9', 'Viêm kết mạc không xác định', 'Conjunctivitis, unspecified', 'OPHTHALMOLOGY'),
('H66.9', 'Viêm tai giữa không xác định', 'Otitis media, unspecified', 'ENT'),

-- Tim mạch
('I10', 'Tăng huyết áp nguyên phát', 'Essential (primary) hypertension', 'CARDIOVASCULAR'),
('I11.9', 'Bệnh tim do tăng huyết áp không suy tim', 'Hypertensive heart disease without heart failure', 'CARDIOVASCULAR'),
('I20.9', 'Đau thắt ngực không xác định', 'Angina pectoris, unspecified', 'CARDIOVASCULAR'),
('I25', 'Bệnh tim thiếu máu cục bộ mạn tính', 'Chronic ischemic heart disease', 'CARDIOVASCULAR'),
('I48', 'Rung nhĩ và cuồng nhĩ', 'Atrial fibrillation and flutter', 'CARDIOVASCULAR'),
('I50', 'Suy tim', 'Heart failure', 'CARDIOVASCULAR'),

-- Hô hấp
('J00', 'Viêm mũi họng cấp (cảm lạnh)', 'Acute nasopharyngitis (common cold)', 'RESPIRATORY'),
('J02.9', 'Viêm họng cấp không xác định', 'Acute pharyngitis, unspecified', 'RESPIRATORY'),
('J03.9', 'Viêm amiđan cấp không xác định', 'Acute tonsillitis, unspecified', 'RESPIRATORY'),
('J06.9', 'Nhiễm trùng đường hô hấp trên cấp không xác định', 'Acute upper respiratory infection, unspecified', 'RESPIRATORY'),
('J18.9', 'Viêm phổi không xác định', 'Pneumonia, unspecified organism', 'RESPIRATORY'),
('J20.9', 'Viêm phế quản cấp không xác định', 'Acute bronchitis, unspecified', 'RESPIRATORY'),
('J30.1', 'Viêm mũi dị ứng do phấn hoa', 'Allergic rhinitis due to pollen', 'RESPIRATORY'),
('J30.4', 'Viêm mũi dị ứng không xác định', 'Allergic rhinitis, unspecified', 'RESPIRATORY'),
('J44.1', 'Bệnh phổi tắc nghẽn mạn tính đợt cấp', 'COPD with acute exacerbation', 'RESPIRATORY'),
('J45', 'Hen phế quản', 'Asthma', 'RESPIRATORY'),

-- Tiêu hóa
('K21', 'Trào ngược dạ dày thực quản (GERD)', 'Gastro-esophageal reflux disease', 'GASTROINTESTINAL'),
('K25', 'Loét dạ dày', 'Gastric ulcer', 'GASTROINTESTINAL'),
('K29.7', 'Viêm dạ dày không xác định', 'Gastritis, unspecified', 'GASTROINTESTINAL'),
('K30', 'Khó tiêu chức năng', 'Functional dyspepsia', 'GASTROINTESTINAL'),
('K35', 'Viêm ruột thừa cấp', 'Acute appendicitis', 'GASTROINTESTINAL'),
('K58', 'Hội chứng ruột kích thích (IBS)', 'Irritable bowel syndrome', 'GASTROINTESTINAL'),
('K76.0', 'Gan nhiễm mỡ', 'Fatty liver', 'GASTROINTESTINAL'),

-- Da liễu
('L20', 'Viêm da cơ địa (eczema)', 'Atopic dermatitis', 'DERMATOLOGY'),
('L23', 'Viêm da tiếp xúc dị ứng', 'Allergic contact dermatitis', 'DERMATOLOGY'),
('L50', 'Mề đay', 'Urticaria', 'DERMATOLOGY'),

-- Cơ xương khớp
('M54.5', 'Đau thắt lưng', 'Low back pain', 'MUSCULOSKELETAL'),
('M79.3', 'Viêm cân gan bàn chân', 'Panniculitis, unspecified', 'MUSCULOSKELETAL'),
('M15', 'Thoái hóa khớp đa khớp', 'Polyarthrosis', 'MUSCULOSKELETAL'),
('M17', 'Thoái hóa khớp gối', 'Gonarthrosis (arthrosis of knee)', 'MUSCULOSKELETAL'),

-- Tiết niệu
('N10', 'Viêm thận bể thận cấp', 'Acute tubulo-interstitial nephritis', 'UROLOGICAL'),
('N30.0', 'Viêm bàng quang cấp', 'Acute cystitis', 'UROLOGICAL'),
('N39.0', 'Nhiễm trùng đường tiết niệu', 'Urinary tract infection', 'UROLOGICAL'),
('N40', 'Phì đại tuyến tiền liệt', 'Benign prostatic hyperplasia', 'UROLOGICAL'),

-- Sản khoa
('O80', 'Đẻ thường một thai', 'Single spontaneous delivery', 'OBSTETRICS'),
('Z34', 'Theo dõi thai bình thường', 'Supervision of normal pregnancy', 'OBSTETRICS'),

-- Triệu chứng chung
('R05', 'Ho', 'Cough', 'SYMPTOMS'),
('R10.4', 'Đau bụng không xác định', 'Unspecified abdominal pain', 'SYMPTOMS'),
('R11', 'Buồn nôn và nôn', 'Nausea and vomiting', 'SYMPTOMS'),
('R42', 'Chóng mặt', 'Dizziness and giddiness', 'SYMPTOMS'),
('R50.9', 'Sốt không xác định', 'Fever, unspecified', 'SYMPTOMS'),
('R51', 'Nhức đầu', 'Headache', 'SYMPTOMS'),
('R53', 'Mệt mỏi', 'Malaise and fatigue', 'SYMPTOMS'),

-- Chấn thương
('S00', 'Chấn thương nông ở đầu', 'Superficial injury of head', 'INJURY'),
('S61', 'Vết thương hở ở cổ tay, bàn tay và ngón tay', 'Open wound of wrist, hand and fingers', 'INJURY'),
('T78.4', 'Dị ứng không xác định', 'Allergy, unspecified', 'ALLERGY'),

-- Khám sức khỏe / Tầm soát
('Z00.0', 'Khám sức khỏe tổng quát', 'General adult medical examination', 'PREVENTIVE'),
('Z01.0', 'Khám mắt và thị lực', 'Examination of eyes and vision', 'PREVENTIVE'),
('Z23', 'Cần tiêm phòng đối với một bệnh nhiễm trùng', 'Need for immunization', 'PREVENTIVE');
