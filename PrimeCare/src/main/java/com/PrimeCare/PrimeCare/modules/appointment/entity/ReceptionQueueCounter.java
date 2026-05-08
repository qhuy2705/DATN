package com.PrimeCare.PrimeCare.modules.appointment.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "reception_queue_counters",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_reception_queue_counter",
                columnNames = {"branch_id", "queue_date"}
        )
)
public class ReceptionQueueCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "branch_id", nullable = false)
    private Long branchId;

    @Column(name = "queue_date", nullable = false)
    private LocalDate queueDate;

    @Column(name = "next_queue_no", nullable = false)
    private Integer nextQueueNo;
}
