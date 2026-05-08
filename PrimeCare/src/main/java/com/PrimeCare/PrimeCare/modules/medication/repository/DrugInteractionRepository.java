package com.PrimeCare.PrimeCare.modules.medication.repository;

import com.PrimeCare.PrimeCare.modules.medication.entity.DrugInteraction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface DrugInteractionRepository extends JpaRepository<DrugInteraction, Long> {

    @Query("SELECT di FROM DrugInteraction di WHERE " +
            "(di.medicationA.id = :medId OR di.medicationB.id = :medId) " +
            "AND di.deleted = false")
    List<DrugInteraction> findByMedicationId(@Param("medId") Long medicationId);

    @Query("SELECT di FROM DrugInteraction di WHERE " +
            "((di.medicationA.id = :medAId AND di.medicationB.id = :medBId) OR " +
            "(di.medicationA.id = :medBId AND di.medicationB.id = :medAId)) " +
            "AND di.deleted = false")
    List<DrugInteraction> findByMedicationPair(@Param("medAId") Long medAId, @Param("medBId") Long medBId);

    @Query("SELECT di FROM DrugInteraction di WHERE " +
            "(di.medicationA.id IN :medIds OR di.medicationB.id IN :medIds) " +
            "AND di.deleted = false")
    List<DrugInteraction> findByMedicationIds(@Param("medIds") List<Long> medicationIds);
}
