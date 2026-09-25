package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.dto.DashboardSnapshot;
import br.com.pimentech.controlemateriais.repository.DashboardRepository;

import java.time.LocalDate;

public final class DashboardService {

    private final DashboardRepository repository;

    public DashboardService(DashboardRepository repository) {
        this.repository = repository;
    }

    public DashboardSnapshot carregar(long obraId) {
        return repository.snapshot(obraId, LocalDate.now());
    }
}
