package com.PrimeCare.PrimeCare.modules.dashboard.dto.query;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AllArgsConstructor
public class DashboardAggregateRow {
    private final Long id;
    private final String code;
    private final String name;
    private final Long value;
}