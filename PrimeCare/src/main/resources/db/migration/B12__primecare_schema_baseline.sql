-- PrimeCare baseline schema at version 12
-- Place this file as src/main/resources/db/migration/B12__primecare_schema_baseline.sql
-- Use for NEW empty environments only. Existing environments that already applied V1..V12 will ignore this baseline migration.

CREATE TABLE IF NOT EXISTS `appointment_pdf_jobs` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `appointment_id` BIGINT NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `file_path` VARCHAR(1000),
  `error_message` TEXT,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_appointment_pdf_jobs_1` UNIQUE (`appointment_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `branches` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `code` VARCHAR(32) NOT NULL,
  `name_vn` VARCHAR(255) NOT NULL,
  `name_en` VARCHAR(255),
  `address_vn` VARCHAR(500) NOT NULL,
  `address_en` VARCHAR(500),
  `description_vn` LONGTEXT,
  `description_en` LONGTEXT,
  `phone` VARCHAR(32),
  `email` VARCHAR(255),
  `lat` DECIMAL(10,7),
  `lng` DECIMAL(10,7),
  `status` VARCHAR(16) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_branches_1` UNIQUE (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `admin_profiles` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `full_name` VARCHAR(255) NOT NULL,
  `branch_id` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_admin_profiles_branch_id_1` FOREIGN KEY (`branch_id`) REFERENCES `branches`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `branch_sessions` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `branch_id` BIGINT NOT NULL,
  `session` VARCHAR(8) NOT NULL,
  `start_time` TIME NOT NULL,
  `end_time` TIME NOT NULL,
  `capacity_override` INT,
  `status` VARCHAR(16) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_branch_session` UNIQUE (`branch_id`, `session`),
  CONSTRAINT `fk_branch_sessions_branch_id_1` FOREIGN KEY (`branch_id`) REFERENCES `branches`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `department_queue_counters` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `department_code` VARCHAR(64) NOT NULL,
  `queue_date` DATE NOT NULL,
  `next_queue_no` INT NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_department_queue_counter` UNIQUE (`department_code`, `queue_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `doctor_profiles` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `full_name` VARCHAR(255) NOT NULL,
  `branch_id` BIGINT NOT NULL,
  `display_title_vn` VARCHAR(150),
  `display_title_en` VARCHAR(150),
  `bio_vn` LONGTEXT,
  `bio_en` LONGTEXT,
  `expertise_vn` LONGTEXT,
  `expertise_en` LONGTEXT,
  `education_vn` LONGTEXT,
  `education_en` LONGTEXT,
  `achievements_vn` LONGTEXT,
  `achievements_en` LONGTEXT,
  `years_exp` INT,
  `avatar_url` VARCHAR(500),
  `slot_minutes_override` INT,
  `status` VARCHAR(16) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_doctor_profiles_branch_id_1` FOREIGN KEY (`branch_id`) REFERENCES `branches`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `doctor_work_schedules` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `doctor_id` BIGINT NOT NULL,
  `work_date` DATE NOT NULL,
  `session` VARCHAR(10) NOT NULL,
  `note` VARCHAR(255),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_doctor_work_schedules_doctor_id_1` FOREIGN KEY (`doctor_id`) REFERENCES `doctor_profiles`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `faqs` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `category` VARCHAR(100),
  `status` VARCHAR(16) NOT NULL,
  `question_vn` VARCHAR(500) NOT NULL,
  `question_en` VARCHAR(500),
  `answer_vn` LONGTEXT NOT NULL,
  `answer_en` LONGTEXT,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_faq_cat` ON `faqs` (`category`, `status`);

CREATE TABLE IF NOT EXISTS `icd10_codes` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `code` VARCHAR(16) NOT NULL,
  `name_vn` VARCHAR(500) NOT NULL,
  `name_en` VARCHAR(500),
  `category` VARCHAR(64),
  `is_active` TINYINT(1) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_icd10_codes_1` UNIQUE (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_icd10_code` ON `icd10_codes` (`code`);
CREATE INDEX `idx_icd10_name_vn` ON `icd10_codes` (`name_vn`);
CREATE INDEX `idx_icd10_category` ON `icd10_codes` (`category`);

CREATE TABLE IF NOT EXISTS `medical_services` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `code` VARCHAR(64) NOT NULL,
  `name_vn` VARCHAR(255) NOT NULL,
  `name_en` VARCHAR(255),
  `description_vn` TEXT,
  `description_en` TEXT,
  `service_type` VARCHAR(32) NOT NULL,
  `department_code` VARCHAR(64),
  `base_price` BIGINT NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `public_visible` TINYINT(1) NOT NULL,
  `display_order` INT,
  `thumbnail_url` VARCHAR(500),
  `default_turnaround_minutes` INT,
  `requires_file_result` TINYINT(1) NOT NULL,
  `requires_numeric_result` TINYINT(1) NOT NULL,
  `result_template_code` VARCHAR(32),
  `result_template_schema_json` TEXT,
  `result_report_title` VARCHAR(255),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_medical_services_1` UNIQUE (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `medications` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `code` VARCHAR(64) NOT NULL,
  `name` VARCHAR(255) NOT NULL,
  `generic_name` VARCHAR(255),
  `strength` VARCHAR(128),
  `dosage_form` VARCHAR(128),
  `unit` VARCHAR(64),
  `manufacturer` VARCHAR(255),
  `indication_note` TEXT,
  `contraindication_note` TEXT,
  `category` VARCHAR(64),
  `route_of_administration` VARCHAR(64),
  `storage_conditions` VARCHAR(128),
  `requires_prescription` TINYINT(1) NOT NULL,
  `max_daily_dose` VARCHAR(128),
  `unit_price` BIGINT,
  `status` VARCHAR(16) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_medications_1` UNIQUE (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `drug_interactions` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `medication_a_id` BIGINT NOT NULL,
  `medication_b_id` BIGINT NOT NULL,
  `severity` VARCHAR(32) NOT NULL,
  `description` TEXT,
  `clinical_effect` TEXT,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_drug_interaction_pair` UNIQUE (`medication_a_id`, `medication_b_id`),
  CONSTRAINT `fk_drug_interactions_medication_a_id_1` FOREIGN KEY (`medication_a_id`) REFERENCES `medications`(`id`),
  CONSTRAINT `fk_drug_interactions_medication_b_id_2` FOREIGN KEY (`medication_b_id`) REFERENCES `medications`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `medication_batches` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `medication_id` BIGINT NOT NULL,
  `batch_number` VARCHAR(64) NOT NULL,
  `quantity_in_stock` INT NOT NULL,
  `manufacture_date` DATE,
  `expiry_date` DATE,
  `import_price` BIGINT,
  `sell_price` BIGINT,
  `supplier` VARCHAR(255),
  `note` TEXT,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_batch_number` UNIQUE (`medication_id`, `batch_number`),
  CONSTRAINT `fk_medication_batches_medication_id_1` FOREIGN KEY (`medication_id`) REFERENCES `medications`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `patients` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `code` VARCHAR(32) NOT NULL,
  `full_name` VARCHAR(255) NOT NULL,
  `phone` VARCHAR(32) NOT NULL,
  `email` VARCHAR(255),
  `avatar_url` VARCHAR(1000),
  `dob` DATE,
  `gender` VARCHAR(16),
  `address` VARCHAR(500),
  `identity_number` VARCHAR(32),
  `insurance_number` VARCHAR(64),
  `insurance_expiry_date` DATE,
  `insurance_registered_hospital` VARCHAR(500),
  `blood_type` VARCHAR(16),
  `ethnicity` VARCHAR(64),
  `nationality` VARCHAR(64),
  `occupation` VARCHAR(255),
  `province` VARCHAR(128),
  `district` VARCHAR(128),
  `ward` VARCHAR(128),
  `emergency_contact_name` VARCHAR(255),
  `emergency_contact_phone` VARCHAR(32),
  `allergy_note` TEXT,
  `chronic_disease_note` TEXT,
  `note` TEXT,
  `status` VARCHAR(16) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_patient_code` UNIQUE (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_patient_phone` ON `patients` (`phone`);
CREATE INDEX `idx_patient_identity_number` ON `patients` (`identity_number`);
CREATE INDEX `idx_patient_insurance_number` ON `patients` (`insurance_number`);

CREATE TABLE IF NOT EXISTS `public_contact_submissions` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `reference_code` VARCHAR(40) NOT NULL,
  `full_name` VARCHAR(255) NOT NULL,
  `email` VARCHAR(255),
  `phone` VARCHAR(32),
  `message` TEXT NOT NULL,
  `source_page` VARCHAR(255),
  `requester_ip` VARCHAR(64),
  `user_agent` VARCHAR(500),
  `status` VARCHAR(32) NOT NULL,
  `emailed_at` DATETIME(6),
  `email_error` VARCHAR(500),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_public_contact_submissions_1` UNIQUE (`reference_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_public_contact_created_at` ON `public_contact_submissions` (`created_at`);
CREATE INDEX `idx_public_contact_status` ON `public_contact_submissions` (`status`);

CREATE TABLE IF NOT EXISTS `public_lookup_access_tokens` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `token` VARCHAR(128) NOT NULL,
  `lookup_type` VARCHAR(32) NOT NULL,
  `reference_id` BIGINT NOT NULL,
  `reference_code` VARCHAR(64) NOT NULL,
  `expires_at` DATETIME(6) NOT NULL,
  `consumed_at` DATETIME(6),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_public_lookup_access_tokens_1` UNIQUE (`token`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_public_lookup_access_token` ON `public_lookup_access_tokens` (`token`);
CREATE INDEX `idx_public_lookup_access_ref` ON `public_lookup_access_tokens` (`lookup_type`, `reference_id`);

CREATE TABLE IF NOT EXISTS `public_lookup_otps` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `lookup_type` VARCHAR(32) NOT NULL,
  `reference_id` BIGINT NOT NULL,
  `reference_code` VARCHAR(64) NOT NULL,
  `target_email` VARCHAR(255) NOT NULL,
  `otp_code` VARCHAR(255) NOT NULL,
  `expires_at` DATETIME(6) NOT NULL,
  `consumed_at` DATETIME(6),
  `attempt_count` INT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_public_lookup_otp_ref` ON `public_lookup_otps` (`lookup_type`, `reference_code`, `created_at`);
CREATE INDEX `idx_public_lookup_otp_email` ON `public_lookup_otps` (`target_email`);

CREATE TABLE IF NOT EXISTS `rate_limit_events` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `key_type` VARCHAR(16) NOT NULL,
  `key_value` VARCHAR(64) NOT NULL,
  `event_type` VARCHAR(64) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_rl` ON `rate_limit_events` (`key_type`, `key_value`, `event_type`, `created_at`);

CREATE TABLE IF NOT EXISTS `reception_queue_counters` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `branch_id` BIGINT NOT NULL,
  `queue_date` DATE NOT NULL,
  `next_queue_no` INT NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_reception_queue_counter` UNIQUE (`branch_id`, `queue_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `specialties` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `code` VARCHAR(32) NOT NULL,
  `name_vn` VARCHAR(255) NOT NULL,
  `name_en` VARCHAR(255),
  `description_vn` LONGTEXT,
  `description_en` LONGTEXT,
  `icon_url` VARCHAR(500),
  `status` VARCHAR(16) NOT NULL,
  `default_slot_minutes` INT NOT NULL,
  `buffer_every_n` INT NOT NULL,
  `buffer_minutes` INT NOT NULL,
  `max_per_session` INT,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_specialties_1` UNIQUE (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `branch_specialties` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `branch_id` BIGINT NOT NULL,
  `specialty_id` BIGINT NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `display_order` INT,
  `consultation_fee` BIGINT,
  `slot_minutes_override` INT,
  `note` VARCHAR(500),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_branch_specialty_branch_specialty` UNIQUE (`branch_id`, `specialty_id`),
  CONSTRAINT `fk_branch_specialties_branch_id_1` FOREIGN KEY (`branch_id`) REFERENCES `branches`(`id`),
  CONSTRAINT `fk_branch_specialties_specialty_id_2` FOREIGN KEY (`specialty_id`) REFERENCES `specialties`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `doctor_specialties` (
  `doctor_id` BIGINT NOT NULL,
  `specialty_id` BIGINT NOT NULL,
  PRIMARY KEY (`doctor_id`, `specialty_id`),
  CONSTRAINT `fk_doctor_specialties_doctor_id_1` FOREIGN KEY (`doctor_id`) REFERENCES `doctor_profiles`(`id`),
  CONSTRAINT `fk_doctor_specialties_specialty_id_2` FOREIGN KEY (`specialty_id`) REFERENCES `specialties`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `staff_profiles` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `full_name` VARCHAR(255) NOT NULL,
  `branch_id` BIGINT NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_staff_profiles_branch_id_1` FOREIGN KEY (`branch_id`) REFERENCES `branches`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `users` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `email` VARCHAR(255),
  `phone` VARCHAR(32),
  `password_hash` VARCHAR(255) NOT NULL,
  `role` VARCHAR(16) NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `last_login_at` DATETIME(6),
  `doctor_profile_id` BIGINT,
  `staff_profile_id` BIGINT,
  `admin_profile_id` BIGINT,
  `patient_id` BIGINT,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_users_email` UNIQUE (`email`),
  CONSTRAINT `uq_users_phone` UNIQUE (`phone`),
  CONSTRAINT `uq_users_doctor_profile` UNIQUE (`doctor_profile_id`),
  CONSTRAINT `uq_users_staff_profile` UNIQUE (`staff_profile_id`),
  CONSTRAINT `uq_users_patient` UNIQUE (`patient_id`),
  CONSTRAINT `fk_users_doctor_profile_id_1` FOREIGN KEY (`doctor_profile_id`) REFERENCES `doctor_profiles`(`id`),
  CONSTRAINT `fk_users_staff_profile_id_2` FOREIGN KEY (`staff_profile_id`) REFERENCES `staff_profiles`(`id`),
  CONSTRAINT `fk_users_admin_profile_id_3` FOREIGN KEY (`admin_profile_id`) REFERENCES `admin_profiles`(`id`),
  CONSTRAINT `fk_users_patient_id_4` FOREIGN KEY (`patient_id`) REFERENCES `patients`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `access_token_blacklist` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `jti` VARCHAR(64) NOT NULL,
  `user_id` BIGINT NOT NULL,
  `expires_at` DATETIME(6) NOT NULL,
  `revoked_at` DATETIME(6) NOT NULL,
  `reason` VARCHAR(100),
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_access_token_blacklist_1` UNIQUE (`jti`),
  CONSTRAINT `fk_access_token_blacklist_user_id_1` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `appointments` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `code` VARCHAR(64) NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `branch_id` BIGINT NOT NULL,
  `specialty_id` BIGINT NOT NULL,
  `doctor_id` BIGINT NOT NULL,
  `visit_date` DATE NOT NULL,
  `session` VARCHAR(8) NOT NULL,
  `source_type` VARCHAR(32) NOT NULL,
  `arrival_status` VARCHAR(16) NOT NULL,
  `reception_queue_no` INT,
  `arrived_by` BIGINT,
  `arrived_at` DATETIME(6),
  `queue_no` INT,
  `slot_minutes` INT,
  `eta_start` TIME,
  `eta_end` TIME,
  `patient_full_name` VARCHAR(255) NOT NULL,
  `patient_phone` VARCHAR(32) NOT NULL,
  `patient_email` VARCHAR(255),
  `patient_dob` DATE,
  `patient_gender` VARCHAR(16),
  `patient_note` TEXT,
  `reason_for_visit` TEXT,
  `visit_type` VARCHAR(32),
  `triage_priority` VARCHAR(32),
  `triage_note` TEXT,
  `insurance_note` VARCHAR(500),
  `emergency_contact_name` VARCHAR(255),
  `emergency_contact_phone` VARCHAR(32),
  `height_cm` DOUBLE,
  `weight_kg` DOUBLE,
  `temperature_c` DOUBLE,
  `pulse` INT,
  `systolic_bp` INT,
  `diastolic_bp` INT,
  `respiratory_rate` INT,
  `spo2` INT,
  `intake_completed_by` BIGINT,
  `intake_completed_at` DATETIME(6),
  `confirmed_by` BIGINT,
  `confirmed_at` DATETIME(6),
  `cancelled_by` BIGINT,
  `cancelled_at` DATETIME(6),
  `cancel_reason` VARCHAR(500),
  `processing_by` BIGINT,
  `processing_started_at` DATETIME(6),
  `processing_expires_at` DATETIME(6),
  `checked_in_by` BIGINT,
  `checked_in_at` DATETIME(6),
  `no_show_marked_by` BIGINT,
  `no_show_marked_at` DATETIME(6),
  `follow_up_pending` TINYINT(1),
  `completed_at` DATETIME(6),
  `rescheduled_from_appointment_id` BIGINT,
  `rescheduled_by` BIGINT,
  `patient_id` BIGINT,
  `rescheduled_at` DATETIME(6),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_appointments_1` UNIQUE (`code`),
  CONSTRAINT `fk_appointments_branch_id_1` FOREIGN KEY (`branch_id`) REFERENCES `branches`(`id`),
  CONSTRAINT `fk_appointments_specialty_id_2` FOREIGN KEY (`specialty_id`) REFERENCES `specialties`(`id`),
  CONSTRAINT `fk_appointments_doctor_id_3` FOREIGN KEY (`doctor_id`) REFERENCES `doctor_profiles`(`id`),
  CONSTRAINT `fk_appointments_arrived_by_4` FOREIGN KEY (`arrived_by`) REFERENCES `users`(`id`),
  CONSTRAINT `fk_appointments_intake_completed_by_5` FOREIGN KEY (`intake_completed_by`) REFERENCES `users`(`id`),
  CONSTRAINT `fk_appointments_confirmed_by_6` FOREIGN KEY (`confirmed_by`) REFERENCES `users`(`id`),
  CONSTRAINT `fk_appointments_cancelled_by_7` FOREIGN KEY (`cancelled_by`) REFERENCES `users`(`id`),
  CONSTRAINT `fk_appointments_processing_by_8` FOREIGN KEY (`processing_by`) REFERENCES `users`(`id`),
  CONSTRAINT `fk_appointments_checked_in_by_9` FOREIGN KEY (`checked_in_by`) REFERENCES `users`(`id`),
  CONSTRAINT `fk_appointments_no_show_marked_by_10` FOREIGN KEY (`no_show_marked_by`) REFERENCES `users`(`id`),
  CONSTRAINT `fk_appointments_rescheduled_from_appointment_id_11` FOREIGN KEY (`rescheduled_from_appointment_id`) REFERENCES `appointments`(`id`),
  CONSTRAINT `fk_appointments_rescheduled_by_12` FOREIGN KEY (`rescheduled_by`) REFERENCES `users`(`id`),
  CONSTRAINT `fk_appointments_patient_id_13` FOREIGN KEY (`patient_id`) REFERENCES `patients`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `appointment_call_logs` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `appointment_id` BIGINT NOT NULL,
  `staff_id` BIGINT NOT NULL,
  `call_time` DATETIME(6) NOT NULL,
  `outcome` VARCHAR(32) NOT NULL,
  `note` VARCHAR(500),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_appointment_call_logs_appointment_id_1` FOREIGN KEY (`appointment_id`) REFERENCES `appointments`(`id`),
  CONSTRAINT `fk_appointment_call_logs_staff_id_2` FOREIGN KEY (`staff_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_call_ap` ON `appointment_call_logs` (`appointment_id`, `call_time`);

CREATE TABLE IF NOT EXISTS `appointment_status_histories` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `appointment_id` BIGINT NOT NULL,
  `from_status` VARCHAR(32),
  `to_status` VARCHAR(32) NOT NULL,
  `changed_by` BIGINT,
  `changed_at` DATETIME(6) NOT NULL,
  `note` VARCHAR(500),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_appointment_status_histories_appointment_id_1` FOREIGN KEY (`appointment_id`) REFERENCES `appointments`(`id`),
  CONSTRAINT `fk_appointment_status_histories_changed_by_2` FOREIGN KEY (`changed_by`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_ap_status_history_appt` ON `appointment_status_histories` (`appointment_id`, `changed_at`);

CREATE TABLE IF NOT EXISTS `articles` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `title_vn` VARCHAR(255) NOT NULL,
  `title_en` VARCHAR(255),
  `slug_vn` VARCHAR(255) NOT NULL,
  `slug_en` VARCHAR(255),
  `summary_vn` LONGTEXT,
  `summary_en` LONGTEXT,
  `content_vn` longtext NOT NULL,
  `content_en` longtext,
  `thumbnail_url` VARCHAR(500),
  `status` VARCHAR(16) NOT NULL,
  `published_at` DATETIME(6),
  `created_by` BIGINT,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_article_slug_vn` UNIQUE (`slug_vn`),
  CONSTRAINT `uq_article_slug_en` UNIQUE (`slug_en`),
  CONSTRAINT `fk_articles_created_by_1` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_art_pub` ON `articles` (`status`, `published_at`);

CREATE TABLE IF NOT EXISTS `audit_logs` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `actor_id` BIGINT,
  `actor_role` VARCHAR(32),
  `action` VARCHAR(64) NOT NULL,
  `entity` VARCHAR(64) NOT NULL,
  `entity_id` BIGINT,
  `before_json` json,
  `after_json` json,
  `ip_address` VARCHAR(64),
  `user_agent` VARCHAR(255),
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_audit_logs_actor_id_1` FOREIGN KEY (`actor_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_audit_entity` ON `audit_logs` (`entity`, `entity_id`, `created_at`);

CREATE TABLE IF NOT EXISTS `credential_setup_tokens` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `token_hash` VARCHAR(128) NOT NULL,
  `purpose` VARCHAR(32) NOT NULL,
  `delivery_channel` VARCHAR(16),
  `delivery_target` VARCHAR(255),
  `requested_by_user_id` BIGINT,
  `expires_at` DATETIME(6) NOT NULL,
  `used_at` DATETIME(6),
  `revoked` TINYINT(1) NOT NULL,
  `revoked_at` DATETIME(6),
  `note` VARCHAR(255),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_credential_setup_tokens_1` UNIQUE (`token_hash`),
  CONSTRAINT `fk_credential_setup_tokens_user_id_1` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`),
  CONSTRAINT `fk_credential_setup_tokens_requested_by_user_id_2` FOREIGN KEY (`requested_by_user_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_credential_setup_user` ON `credential_setup_tokens` (`user_id`, `purpose`, `expires_at`);
CREATE INDEX `idx_credential_setup_expires` ON `credential_setup_tokens` (`expires_at`);

CREATE TABLE IF NOT EXISTS `doctor_leave_requests` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `doctor_id` BIGINT NOT NULL,
  `start_date` DATE NOT NULL,
  `end_date` DATE NOT NULL,
  `start_session` VARCHAR(10) NOT NULL,
  `end_session` VARCHAR(10) NOT NULL,
  `reason` VARCHAR(500),
  `status` VARCHAR(20) NOT NULL,
  `reviewed_by` BIGINT,
  `reviewed_at` DATETIME(6),
  `review_note` VARCHAR(500),
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_doctor_leave_requests_doctor_id_1` FOREIGN KEY (`doctor_id`) REFERENCES `doctor_profiles`(`id`),
  CONSTRAINT `fk_doctor_leave_requests_reviewed_by_2` FOREIGN KEY (`reviewed_by`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `encounters` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `code` VARCHAR(64) NOT NULL,
  `appointment_id` BIGINT NOT NULL,
  `branch_id` BIGINT NOT NULL,
  `specialty_id` BIGINT NOT NULL,
  `doctor_id` BIGINT NOT NULL,
  `patient_id` BIGINT,
  `patient_full_name_snapshot` VARCHAR(255) NOT NULL,
  `patient_phone_snapshot` VARCHAR(32),
  `patient_email_snapshot` VARCHAR(255),
  `patient_dob_snapshot` DATE,
  `patient_gender_snapshot` VARCHAR(16),
  `intake_reason_for_visit` TEXT,
  `visit_type` VARCHAR(32),
  `triage_priority` VARCHAR(32),
  `triage_note` TEXT,
  `insurance_note` VARCHAR(500),
  `emergency_contact_name` VARCHAR(255),
  `emergency_contact_phone` VARCHAR(32),
  `intake_completed_at` DATETIME(6),
  `intake_completed_by_name` VARCHAR(255),
  `chief_complaint` TEXT,
  `clinical_note` TEXT,
  `preliminary_diagnosis` TEXT,
  `final_diagnosis` TEXT,
  `conclusion` TEXT,
  `height_cm` DOUBLE,
  `weight_kg` DOUBLE,
  `temperature_c` DOUBLE,
  `pulse` INT,
  `systolic_bp` INT,
  `diastolic_bp` INT,
  `respiratory_rate` INT,
  `spo2` INT,
  `allergy_snapshot` TEXT,
  `chronic_disease_snapshot` TEXT,
  `past_medical_history` TEXT,
  `physical_examination` TEXT,
  `treatment_plan` TEXT,
  `follow_up_date` DATE,
  `follow_up_note` TEXT,
  `status` VARCHAR(32) NOT NULL,
  `started_at` DATETIME(6),
  `completed_at` DATETIME(6),
  `reopened_count` INT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_encounter_appointment` UNIQUE (`appointment_id`),
  CONSTRAINT `uq_encounters_2` UNIQUE (`code`),
  CONSTRAINT `fk_encounters_appointment_id_1` FOREIGN KEY (`appointment_id`) REFERENCES `appointments`(`id`),
  CONSTRAINT `fk_encounters_branch_id_2` FOREIGN KEY (`branch_id`) REFERENCES `branches`(`id`),
  CONSTRAINT `fk_encounters_specialty_id_3` FOREIGN KEY (`specialty_id`) REFERENCES `specialties`(`id`),
  CONSTRAINT `fk_encounters_doctor_id_4` FOREIGN KEY (`doctor_id`) REFERENCES `doctor_profiles`(`id`),
  CONSTRAINT `fk_encounters_patient_id_5` FOREIGN KEY (`patient_id`) REFERENCES `patients`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `encounter_diagnoses` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `encounter_id` BIGINT NOT NULL,
  `icd10_code_id` BIGINT NOT NULL,
  `diagnosis_type` VARCHAR(16) NOT NULL,
  `note` TEXT,
  `display_order` INT NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_encounter_diagnoses_encounter_id_1` FOREIGN KEY (`encounter_id`) REFERENCES `encounters`(`id`),
  CONSTRAINT `fk_encounter_diagnoses_icd10_code_id_2` FOREIGN KEY (`icd10_code_id`) REFERENCES `icd10_codes`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_enc_diag_encounter` ON `encounter_diagnoses` (`encounter_id`);
CREATE INDEX `idx_enc_diag_icd10` ON `encounter_diagnoses` (`icd10_code_id`);

CREATE TABLE IF NOT EXISTS `encounter_reopen_logs` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `encounter_id` BIGINT NOT NULL,
  `reopened_by_user_id` BIGINT NOT NULL,
  `reason` TEXT NOT NULL,
  `previous_status` VARCHAR(32) NOT NULL,
  `reopened_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_encounter_reopen_logs_encounter_id_1` FOREIGN KEY (`encounter_id`) REFERENCES `encounters`(`id`),
  CONSTRAINT `fk_encounter_reopen_logs_reopened_by_user_id_2` FOREIGN KEY (`reopened_by_user_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `files` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `owner_type` VARCHAR(32) NOT NULL,
  `owner_id` BIGINT NOT NULL,
  `file_name` VARCHAR(255) NOT NULL,
  `mime_type` VARCHAR(100) NOT NULL,
  `file_size` BIGINT NOT NULL,
  `storage_provider` VARCHAR(16) NOT NULL,
  `storage_path` VARCHAR(500) NOT NULL,
  `created_by` BIGINT,
  `created_at` DATETIME(6) NOT NULL,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_files_created_by_1` FOREIGN KEY (`created_by`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_files_owner` ON `files` (`owner_type`, `owner_id`);

CREATE TABLE IF NOT EXISTS `inventory_transactions` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `batch_id` BIGINT NOT NULL,
  `transaction_type` VARCHAR(32) NOT NULL,
  `quantity` INT NOT NULL,
  `reason` TEXT,
  `reference_type` VARCHAR(32),
  `reference_id` BIGINT,
  `performed_by_user_id` BIGINT,
  `performed_at` DATETIME(6) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_inventory_transactions_batch_id_1` FOREIGN KEY (`batch_id`) REFERENCES `medication_batches`(`id`),
  CONSTRAINT `fk_inventory_transactions_performed_by_user_id_2` FOREIGN KEY (`performed_by_user_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `invoice_pdf_jobs` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `invoice_id` BIGINT NOT NULL,
  `requested_by` BIGINT NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `file_path` VARCHAR(1000),
  `error_message` TEXT,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_invoice_pdf_jobs_requested_by_1` FOREIGN KEY (`requested_by`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `notification_preferences` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `allow_email` TINYINT(1) NOT NULL,
  `allow_sms` TINYINT(1) NOT NULL,
  `appointment_reminders` TINYINT(1) NOT NULL,
  `result_ready_alerts` TINYINT(1) NOT NULL,
  `invoice_updates` TINYINT(1) NOT NULL,
  `security_alerts` TINYINT(1) NOT NULL,
  `preferred_channel` VARCHAR(16),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_notification_preferences_user` UNIQUE (`user_id`),
  CONSTRAINT `fk_notification_preferences_user_id_1` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `notifications` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `channel` VARCHAR(16) NOT NULL,
  `template_code` VARCHAR(64) NOT NULL,
  `entity_type` VARCHAR(32),
  `entity_id` BIGINT,
  `appointment_id` BIGINT,
  `to_phone` VARCHAR(32),
  `to_email` VARCHAR(255),
  `content` LONGTEXT NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `provider` VARCHAR(64),
  `provider_message_id` VARCHAR(128),
  `error_message` VARCHAR(255),
  `scheduled_at` DATETIME(6),
  `last_attempt_at` DATETIME(6),
  `attempt_count` INT NOT NULL,
  `sent_at` DATETIME(6),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_notifications_appointment_id_1` FOREIGN KEY (`appointment_id`) REFERENCES `appointments`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_notif_status` ON `notifications` (`status`, `created_at`);
CREATE INDEX `idx_notif_ap` ON `notifications` (`appointment_id`);
CREATE INDEX `idx_notif_entity` ON `notifications` (`entity_type`, `entity_id`);

CREATE TABLE IF NOT EXISTS `notification_attempts` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `notification_id` BIGINT NOT NULL,
  `attempt_no` INT NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `provider_message_id` VARCHAR(128),
  `error_message` VARCHAR(500),
  `response_excerpt` VARCHAR(1000),
  `attempted_at` DATETIME(6) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_notification_attempts_notification_id_1` FOREIGN KEY (`notification_id`) REFERENCES `notifications`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_notification_attempt_notification` ON `notification_attempts` (`notification_id`, `attempt_no`);

CREATE TABLE IF NOT EXISTS `patient_allergies` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `patient_id` BIGINT NOT NULL,
  `allergen_name` VARCHAR(255) NOT NULL,
  `allergy_type` VARCHAR(32) NOT NULL,
  `severity` VARCHAR(32) NOT NULL,
  `reaction` TEXT,
  `noted_by` BIGINT,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_patient_allergies_patient_id_1` FOREIGN KEY (`patient_id`) REFERENCES `patients`(`id`),
  CONSTRAINT `fk_patient_allergies_noted_by_2` FOREIGN KEY (`noted_by`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `prescription_pdf_jobs` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `prescription_id` BIGINT NOT NULL,
  `requested_by` BIGINT NOT NULL,
  `status` VARCHAR(16) NOT NULL,
  `file_path` VARCHAR(1000),
  `error_message` TEXT,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_prescription_pdf_jobs_requested_by_1` FOREIGN KEY (`requested_by`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `prescriptions` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `code` VARCHAR(64) NOT NULL,
  `encounter_id` BIGINT NOT NULL,
  `doctor_user_id` BIGINT NOT NULL,
  `issued_date` DATE NOT NULL,
  `general_note` TEXT,
  `status` VARCHAR(16) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_prescriptions_1` UNIQUE (`code`),
  CONSTRAINT `fk_prescriptions_encounter_id_1` FOREIGN KEY (`encounter_id`) REFERENCES `encounters`(`id`),
  CONSTRAINT `fk_prescriptions_doctor_user_id_2` FOREIGN KEY (`doctor_user_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `prescription_items` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `prescription_id` BIGINT NOT NULL,
  `medication_id` BIGINT NOT NULL,
  `medication_code_snapshot` VARCHAR(64) NOT NULL,
  `medication_name_snapshot` VARCHAR(255) NOT NULL,
  `strength_snapshot` VARCHAR(128),
  `dosage_form_snapshot` VARCHAR(128),
  `unit_snapshot` VARCHAR(64),
  `quantity` INT NOT NULL,
  `dose` VARCHAR(128),
  `frequency` VARCHAR(128),
  `duration_days` INT,
  `route` VARCHAR(128),
  `instruction` TEXT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_prescription_items_prescription_id_1` FOREIGN KEY (`prescription_id`) REFERENCES `prescriptions`(`id`),
  CONSTRAINT `fk_prescription_items_medication_id_2` FOREIGN KEY (`medication_id`) REFERENCES `medications`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `refresh_tokens` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `user_id` BIGINT NOT NULL,
  `token` VARCHAR(500) NOT NULL,
  `expires_at` DATETIME(6) NOT NULL,
  `revoked` TINYINT(1) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `revoked_at` DATETIME(6),
  `user_agent` VARCHAR(255),
  `ip_address` VARCHAR(64),
  `family_id` VARCHAR(64),
  `replaced_by_token` VARCHAR(500),
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_refresh_tokens_1` UNIQUE (`token`),
  CONSTRAINT `fk_refresh_tokens_user_id_1` FOREIGN KEY (`user_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `service_orders` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `code` VARCHAR(64) NOT NULL,
  `encounter_id` BIGINT NOT NULL,
  `ordered_by_doctor_id` BIGINT NOT NULL,
  `branch_id` BIGINT NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `payment_status` VARCHAR(16) NOT NULL,
  `estimated_total_amount` BIGINT,
  `ordered_at` DATETIME(6),
  `paid_at` DATETIME(6),
  `cancelled_at` DATETIME(6),
  `note` VARCHAR(500),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_service_orders_1` UNIQUE (`code`),
  CONSTRAINT `fk_service_orders_encounter_id_1` FOREIGN KEY (`encounter_id`) REFERENCES `encounters`(`id`),
  CONSTRAINT `fk_service_orders_ordered_by_doctor_id_2` FOREIGN KEY (`ordered_by_doctor_id`) REFERENCES `users`(`id`),
  CONSTRAINT `fk_service_orders_branch_id_3` FOREIGN KEY (`branch_id`) REFERENCES `branches`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `invoices` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `code` VARCHAR(64) NOT NULL,
  `service_order_id` BIGINT NOT NULL,
  `cashier_id` BIGINT,
  `subtotal_amount` BIGINT NOT NULL,
  `discount_amount` BIGINT NOT NULL,
  `tax_amount` BIGINT NOT NULL,
  `total_amount` BIGINT NOT NULL,
  `payment_method` VARCHAR(16),
  `payment_status` VARCHAR(32) NOT NULL,
  `payment_reference` VARCHAR(32),
  `transfer_content` VARCHAR(64),
  `payment_detected_at` DATETIME(6),
  `payment_review_reason` VARCHAR(255),
  `vnp_txn_ref` VARCHAR(64),
  `vnp_payment_url` VARCHAR(2000),
  `paid_at` DATETIME(6),
  `note` VARCHAR(500),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_invoice_code` UNIQUE (`code`),
  CONSTRAINT `uq_invoice_service_order` UNIQUE (`service_order_id`),
  CONSTRAINT `fk_invoices_service_order_id_1` FOREIGN KEY (`service_order_id`) REFERENCES `service_orders`(`id`),
  CONSTRAINT `fk_invoices_cashier_id_2` FOREIGN KEY (`cashier_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `bank_transactions` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `provider` VARCHAR(32) NOT NULL,
  `transaction_ref` VARCHAR(128) NOT NULL,
  `amount` BIGINT NOT NULL,
  `transfer_content` VARCHAR(255),
  `bank_account_no` VARCHAR(64),
  `bank_code` VARCHAR(32),
  `transaction_time` DATETIME(6),
  `status` VARCHAR(24) NOT NULL,
  `matched_invoice_id` BIGINT,
  `matched_at` DATETIME(6),
  `review_reason` VARCHAR(255),
  `raw_payload` TEXT,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_bank_transaction_ref` UNIQUE (`transaction_ref`),
  CONSTRAINT `fk_bank_transactions_matched_invoice_id_1` FOREIGN KEY (`matched_invoice_id`) REFERENCES `invoices`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `invoice_items` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `invoice_id` BIGINT NOT NULL,
  `reference_type` VARCHAR(32) NOT NULL,
  `reference_id` BIGINT,
  `name_snapshot` VARCHAR(255) NOT NULL,
  `unit_price` BIGINT NOT NULL,
  `quantity` INT NOT NULL,
  `tax_rate` DECIMAL(5,2),
  `subtotal_amount` BIGINT NOT NULL,
  `tax_amount` BIGINT NOT NULL,
  `total_amount` BIGINT NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_invoice_items_invoice_id_1` FOREIGN KEY (`invoice_id`) REFERENCES `invoices`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `invoice_status_histories` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `invoice_id` BIGINT NOT NULL,
  `from_status` VARCHAR(16),
  `to_status` VARCHAR(16) NOT NULL,
  `changed_by` BIGINT,
  `changed_at` DATETIME(6) NOT NULL,
  `note` VARCHAR(500),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_invoice_status_histories_invoice_id_1` FOREIGN KEY (`invoice_id`) REFERENCES `invoices`(`id`),
  CONSTRAINT `fk_invoice_status_histories_changed_by_2` FOREIGN KEY (`changed_by`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_invoice_status_history_invoice` ON `invoice_status_histories` (`invoice_id`, `changed_at`);

CREATE TABLE IF NOT EXISTS `payment_intents` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `invoice_id` BIGINT NOT NULL,
  `provider` VARCHAR(32) NOT NULL,
  `payment_reference` VARCHAR(32) NOT NULL,
  `transfer_content` VARCHAR(64) NOT NULL,
  `bank_code` VARCHAR(32),
  `bank_account_no` VARCHAR(64),
  `bank_account_name` VARCHAR(255),
  `expected_amount` BIGINT NOT NULL,
  `qr_payload` TEXT,
  `status` VARCHAR(24) NOT NULL,
  `expires_at` DATETIME(6),
  `detected_at` DATETIME(6),
  `confirmed_at` DATETIME(6),
  `matched_transaction_ref` VARCHAR(128),
  `review_reason` VARCHAR(255),
  `note` VARCHAR(500),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_payment_intent_invoice` UNIQUE (`invoice_id`),
  CONSTRAINT `uq_payment_intent_reference` UNIQUE (`payment_reference`),
  CONSTRAINT `fk_payment_intents_invoice_id_1` FOREIGN KEY (`invoice_id`) REFERENCES `invoices`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `refund_records` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `invoice_id` BIGINT NOT NULL,
  `refund_amount` BIGINT NOT NULL,
  `reason` TEXT NOT NULL,
  `approved_by_user_id` BIGINT,
  `refunded_at` DATETIME(6) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_refund_records_invoice_id_1` FOREIGN KEY (`invoice_id`) REFERENCES `invoices`(`id`),
  CONSTRAINT `fk_refund_records_approved_by_user_id_2` FOREIGN KEY (`approved_by_user_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `service_order_items` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `service_order_id` BIGINT NOT NULL,
  `medical_service_id` BIGINT NOT NULL,
  `service_code_snapshot` VARCHAR(64) NOT NULL,
  `service_name_vn_snapshot` VARCHAR(255) NOT NULL,
  `service_name_en_snapshot` VARCHAR(255),
  `price_snapshot` BIGINT NOT NULL,
  `quantity` INT NOT NULL,
  `line_total_amount` BIGINT NOT NULL,
  `status` VARCHAR(32) NOT NULL,
  `result_status` VARCHAR(16) NOT NULL,
  `assigned_department_code` VARCHAR(64),
  `queue_no` INT,
  `queued_at` DATETIME(6),
  `started_at` DATETIME(6),
  `completed_at` DATETIME(6),
  `cancelled_at` DATETIME(6),
  `note` VARCHAR(500),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_service_order_items_service_order_id_1` FOREIGN KEY (`service_order_id`) REFERENCES `service_orders`(`id`),
  CONSTRAINT `fk_service_order_items_medical_service_id_2` FOREIGN KEY (`medical_service_id`) REFERENCES `medical_services`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `service_results` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `service_order_item_id` BIGINT NOT NULL,
  `result_text_vn` TEXT,
  `result_text_en` TEXT,
  `result_data_json` TEXT,
  `attachment_url` VARCHAR(1000),
  `attachment_mime_type` VARCHAR(128),
  `template_code` VARCHAR(32),
  `template_schema_json` TEXT,
  `field_values_json` TEXT,
  `conclusion_text` TEXT,
  `impression_text` TEXT,
  `attachment_urls_json` TEXT,
  `report_title` VARCHAR(255),
  `report_pdf_path` VARCHAR(1000),
  `report_pdf_status` VARCHAR(16),
  `report_pdf_error_message` TEXT,
  `report_pdf_generated_at` DATETIME(6),
  `performed_by_user_id` BIGINT,
  `verified_by_user_id` BIGINT,
  `performed_at` DATETIME(6),
  `verified_at` DATETIME(6),
  `status` VARCHAR(16) NOT NULL,
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `uq_service_results_1` UNIQUE (`service_order_item_id`),
  CONSTRAINT `fk_service_results_service_order_item_id_1` FOREIGN KEY (`service_order_item_id`) REFERENCES `service_order_items`(`id`),
  CONSTRAINT `fk_service_results_performed_by_user_id_2` FOREIGN KEY (`performed_by_user_id`) REFERENCES `users`(`id`),
  CONSTRAINT `fk_service_results_verified_by_user_id_3` FOREIGN KEY (`verified_by_user_id`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS `service_result_status_histories` (
  `id` BIGINT AUTO_INCREMENT NOT NULL,
  `service_result_id` BIGINT NOT NULL,
  `from_status` VARCHAR(16),
  `to_status` VARCHAR(16) NOT NULL,
  `changed_by` BIGINT,
  `changed_at` DATETIME(6) NOT NULL,
  `note` VARCHAR(500),
  `created_at` DATETIME(6) NOT NULL,
  `updated_at` DATETIME(6) NOT NULL,
  `version` BIGINT,
  `deleted` TINYINT(1) NOT NULL,
  `deleted_at` DATETIME(6),
  `deleted_by` BIGINT,
  PRIMARY KEY (`id`),
  CONSTRAINT `fk_service_result_status_histories_service_result_id_1` FOREIGN KEY (`service_result_id`) REFERENCES `service_results`(`id`),
  CONSTRAINT `fk_service_result_status_histories_changed_by_2` FOREIGN KEY (`changed_by`) REFERENCES `users`(`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
CREATE INDEX `idx_service_result_status_history_result` ON `service_result_status_histories` (`service_result_id`, `changed_at`);
