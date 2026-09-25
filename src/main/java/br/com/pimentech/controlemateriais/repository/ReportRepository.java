package br.com.pimentech.controlemateriais.repository;

import java.nio.file.Path;
import java.time.LocalDate;

public interface ReportRepository {

    void exportPedidos(long obraId, LocalDate inicio, LocalDate fim, Path destino);

    void exportPlanejamento(long obraId, Path destino);
}
