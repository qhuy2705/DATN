package com.PrimeCare.PrimeCare.modules.publiclookup.service;

import com.PrimeCare.PrimeCare.modules.encounter.entity.Encounter;
import com.PrimeCare.PrimeCare.modules.service_result.entity.ServiceResult;
import com.PrimeCare.PrimeCare.modules.service_result.service.ServiceResultPdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicResultPdfService {

    private final ServiceResultPdfService serviceResultPdfService;

    public byte[] generate(Encounter encounter, List<ServiceResult> results) {
        return serviceResultPdfService.generatePublicEncounterResult(encounter, results);
    }
}
