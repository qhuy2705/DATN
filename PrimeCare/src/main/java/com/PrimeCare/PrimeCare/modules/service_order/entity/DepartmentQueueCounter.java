package com.PrimeCare.PrimeCare.modules.service_order.entity;

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
        name = "department_queue_counters",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_department_queue_counter",
                columnNames = {"department_code", "queue_date"}
        )
)
public class DepartmentQueueCounter {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "department_code", nullable = false, length = 64)
    private String departmentCode;

    @Column(name = "queue_date", nullable = false)
    private LocalDate queueDate;

    @Column(name = "next_queue_no", nullable = false)
    private Integer nextQueueNo;
}