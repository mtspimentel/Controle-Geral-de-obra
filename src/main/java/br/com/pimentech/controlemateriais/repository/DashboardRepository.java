package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.dto.DashboardSnapshot;

import java.time.LocalDate;

public interface DashboardRepository {

    DashboardSnapshot snapshot(long obraId, LocalDate hoje);
}
