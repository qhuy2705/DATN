package com.PrimeCare.PrimeCare.modules.appointment.dto.request;

import com.PrimeCare.PrimeCare.shared.enums.BranchSessionType;
import com.PrimeCare.PrimeCare.shared.enums.Gender;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class CreateAppointmentRequest {

    @NotNull(message = "branchId không được để trống")
    private Long branchId;

    @NotNull(message = "specialtyId không được để trống")
    private Long specialtyId;

    @NotNull(message = "doctorId không được để trống")
    private Long doctorId;

    @NotNull(message = "visitDate không được để trống")
    @FutureOrPresent(message = "Ngày khám phải từ hôm nay trở đi")
    private LocalDate visitDate;

    @NotNull(message = "session không được để trống")
    private BranchSessionType session;

    @NotNull(message = "slotStart không được để trống")
    private LocalTime slotStart;

    @NotBlank(message = "Họ tên bệnh nhân không được để trống")
    @Size(max = 255, message = "Họ tên bệnh nhân tối đa 255 ký tự")
    private String patientFullName;

    @NotBlank(message = "Số điện thoại không được để trống")
    @Pattern(
            regexp = "^(\\+84|84|0)(3|5|7|8|9)\\d{8}$",
            message = "Số điện thoại không đúng định dạng Việt Nam"
    )
    private String patientPhone;

    @Size(max = 255, message = "Email tối đa 255 ký tự")
    @Email(message = "Email không đúng định dạng")
    private String patientEmail;

    @PastOrPresent(message = "Ngày sinh không được ở tương lai")
    private LocalDate patientDob;

    private Gender patientGender;

    @Size(max = 1000, message = "Ghi chú tối đa 1000 ký tự")
    private String patientNote;

    @Size(max = 1000, message = "Lý do khám tối đa 1000 ký tự")
    private String reasonForVisit;

    @Size(max = 32, message = "Loại lượt khám tối đa 32 ký tự")
    private String visitType;
}