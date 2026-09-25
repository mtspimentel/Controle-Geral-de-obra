package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.repository.ReportRepository;

import java.nio.file.Path;
import java.time.LocalDate;

public final class ReportService {

    private final ReportRepository repository;

    public ReportService(ReportRepository repository) {
        this.repository = repository;
    }

    public void exportPedidos(long obraId, LocalDate inicio, LocalDate fim, Path destino) {
        if (obraId <= 0 || inicio == null || fim == null || fim.isBefore(inicio)) {
            throw new ValidationException("Informe um período válido para o relatório");
        }
        repository.exportPedidos(obraId, inicio, fim, destino);
    }

    public void exportPlanejamento(long obraId, Path destino) {
        if (obraId <= 0) throw new ValidationException("Selecione uma obra ativa");
        repository.exportPlanejamento(obraId, destino);
    }
}
