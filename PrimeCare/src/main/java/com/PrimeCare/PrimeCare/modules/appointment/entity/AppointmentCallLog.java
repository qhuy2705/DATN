package com.PrimeCare.PrimeCare.modules.appointment.entity;

import com.PrimeCare.PrimeCare.modules.auth.entity.User;
import com.PrimeCare.PrimeCare.shared.enums.CallOutcome;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name="appointment_call_logs",
        indexes = @Index(name="idx_call_ap", columnList="appointment_id,call_time")
)
public class AppointmentCallLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="appointment_id", nullable=false)
    private Appointment appointment;

    @ManyToOne(fetch=FetchType.LAZY, optional=false)
    @JoinColumn(name="staff_id", nullable=false)
    private User staff;

    @Column(name="call_time", nullable=false)
    private LocalDateTime callTime;

    @Enumerated(EnumType.STRING)
    @Column(name="outcome", nullable=false, length=32)
    private CallOutcome outcome;

    @Column(name="note", length=500)
    private String note;

    @PrePersist
    void prePersist() {
        if (callTime == null) callTime = LocalDateTime.now();
    }
}
