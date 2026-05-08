package com.PrimeCare.PrimeCare.shared.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Sai tài khoản hoặc mật khẩu"),
    AUTH_ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Tài khoản đang bị khóa"),
    AUTH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Token không hợp lệ"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token đã hết hạn"),
    AUTH_REFRESH_MISSING(HttpStatus.UNAUTHORIZED, "Thiếu refresh token"),
    AUTH_REFRESH_REVOKED(HttpStatus.UNAUTHORIZED, "Refresh token đã bị thu hồi"),
    AUTH_REFRESH_EXPIRED(HttpStatus.UNAUTHORIZED, "Refresh token đã hết hạn"),
    PUBLIC_LOOKUP_OTP_INVALID(HttpStatus.BAD_REQUEST, "Mã OTP không đúng. Vui lòng kiểm tra lại."),
    PUBLIC_LOOKUP_OTP_EXPIRED(HttpStatus.BAD_REQUEST, "Mã OTP đã hết hạn. Vui lòng gửi lại mã mới."),
    PUBLIC_LOOKUP_OTP_LOCKED(HttpStatus.TOO_MANY_REQUESTS, "Mã OTP đã bị khóa do nhập sai quá nhiều lần. Vui lòng gửi lại mã mới."),
    PUBLIC_LOOKUP_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Token tra cứu không hợp lệ."),
    PUBLIC_LOOKUP_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "Token tra cứu đã hết hạn."),
    AUTH_EMAIL_EXISTS(HttpStatus.CONFLICT, "Email đã tồn tại"),
    AUTH_PHONE_EXISTS(HttpStatus.CONFLICT, "Số điện thoại đã tồn tại"),
    AUTH_SETUP_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Liên kết thiết lập mật khẩu không hợp lệ"),
    AUTH_SETUP_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "Liên kết thiết lập mật khẩu đã hết hạn"),
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống"),

    BRANCH_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy cơ sở"),
    BRANCH_CODE_EXISTS(HttpStatus.CONFLICT, "Branch code đã tồn tại"),

    DOCTOR_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bác sĩ"),
    STAFF_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy nhân viên"),
    DOCTOR_NOT_IN_SPECIALTY(HttpStatus.BAD_REQUEST, "Bác sĩ không thuộc chuyên khoa đã chọn"),
    SPECIALTY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy chuyên khoa"),
    APPOINTMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy lịch hẹn"),
    APPOINTMENT_SLOT_NOT_AVAILABLE(HttpStatus.BAD_REQUEST, "Khung giờ đã được đặt hoặc không khả dụng"),
    APPOINTMENT_INVALID_DATE(HttpStatus.BAD_REQUEST, "Ngày khám không hợp lệ"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "Yêu cầu không hợp lệ"),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Bạn chưa đăng nhập hoặc phiên đăng nhập đã hết hạn"),
    ACCESS_DENIED(HttpStatus.FORBIDDEN, "Bạn không có quyền thực hiện thao tác này"),
    PROFILE_ALREADY_HAS_ACCOUNT(HttpStatus.CONFLICT, "Hồ sơ đã có tài khoản"),
    DOCTOR_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ bác sĩ"),
    STAFF_PROFILE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy hồ sơ nhân viên"),
    DOCTOR_LEAVE_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu nghỉ phép của bác sĩ"),
    DOCTOR_LEAVE_HAS_APPOINTMENT_CONFLICT(HttpStatus.BAD_REQUEST, "Yêu cầu nghỉ phép của bác sĩ xung đột với các cuộc hẹn hiện tại"),
    APPOINTMENT_ALREADY_CLAIMED(HttpStatus.CONFLICT, "Lịch hẹn đang được xử lý bởi nhân viên khác."),
    APPOINTMENT_CLAIM_CONFLICT(HttpStatus.CONFLICT, "Không thể nhận xử lý lịch hẹn lúc này. Vui lòng tải lại dữ liệu."),
    APPOINTMENT_CLAIM_REQUIRED(HttpStatus.BAD_REQUEST, "Bạn không giữ quyền xử lý lịch hẹn này."),
    APPOINTMENT_CHECKIN_TOKEN_INVALID(HttpStatus.BAD_REQUEST, "Mã QR check-in không hợp lệ."),
    APPOINTMENT_CHECKIN_TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "Mã QR check-in đã hết hạn."),
    APPOINTMENT_INVALID_STATUS(HttpStatus.BAD_REQUEST, "Trạng thái lịch hẹn không hợp lệ"),
    APPOINTMENT_TOO_EARLY_FOR_NO_SHOW(HttpStatus.BAD_REQUEST, "Chưa đủ thời gian để đánh dấu bệnh nhân không đến"),

    PATIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bệnh nhân"),
    PATIENT_CODE_EXISTS(HttpStatus.CONFLICT, "Mã bệnh nhân đã tồn tại"),
    PATIENT_ACCOUNT_ALREADY_EXISTS(HttpStatus.CONFLICT, "Bệnh nhân đã có tài khoản"),
    PATIENT_REGISTRATION_MISMATCH(HttpStatus.BAD_REQUEST, "Thông tin đăng ký không khớp với hồ sơ hiện có"),
    PATIENT_ACCOUNT_NOT_LINKED(HttpStatus.BAD_REQUEST, "Tài khoản chưa liên kết với hồ sơ bệnh nhân"),
    PATIENT_SELF_SERVICE_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Hiện không thể tự phục vụ cho lịch hẹn này"),

    MEDICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thuốc"),
    PRESCRIPTION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy đơn thuốc"),
    PRESCRIPTION_INVALID_STATUS(HttpStatus.BAD_REQUEST, "Trạng thái đơn thuốc không hợp lệ"),
    PRESCRIPTION_ALLERGY_WARNING(HttpStatus.BAD_REQUEST, "Cảnh báo: Bệnh nhân có tiền sử dị ứng với thuốc này"),
    PRESCRIPTION_DRUG_INTERACTION(HttpStatus.BAD_REQUEST, "Cảnh báo: Tương tác thuốc nguy hiểm"),
    PRESCRIPTION_INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "Không đủ tồn kho để kê đơn"),

    ENCOUNTER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy lần khám"),
    ENCOUNTER_INVALID_STATUS(HttpStatus.BAD_REQUEST, "Trạng thái lần khám không hợp lệ"),
    ENCOUNTER_ALREADY_EXISTS(HttpStatus.CONFLICT, "Lần khám đã được tạo cho lịch hẹn này"),
    ENCOUNTER_REOPEN_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Không thể mở lại lần khám này"),
    MEDICAL_SERVICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy dịch vụ"),
    SERVICE_ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy phiếu chỉ định"),
    SERVICE_ORDER_ITEM_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy mục dịch vụ"),
    SERVICE_ORDER_INVALID_STATUS(HttpStatus.BAD_REQUEST, "Trạng thái phiếu chỉ định không hợp lệ"),
    INVOICE_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy hóa đơn"),
    INVOICE_INVALID_STATUS(HttpStatus.BAD_REQUEST, "Trạng thái hóa đơn không hợp lệ"),
    REFUND_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "Không thể hoàn tiền cho hóa đơn này"),
    SERVICE_RESULT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy kết quả dịch vụ"),
    INTERNAL_NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo"),
    INVENTORY_INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "Không đủ tồn kho"),
    MEDICATION_BATCH_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy lô thuốc"),
    PUBLIC_CONTACT_SUBMISSION_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy yêu cầu liên hệ"),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Bạn đang thao tác quá nhanh, vui lòng thử lại sau 1 phút.");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }
}
