package br.com.pimentech.controlemateriais.model;

import java.time.LocalDate;

public record ServicoArea(Long id, long obraId, long areaId, String descricao, String unidade,
                          double quantidadePrevista, LocalDate inicioPrevisto, LocalDate fimPrevisto,
                          Double metaDiaria, boolean ativo, Long disciplinaId,
                          String equipeResponsavel, String observacoes) {
    public ServicoArea(Long id, long obraId, long areaId, String descricao, String unidade,
                       double quantidadePrevista, LocalDate inicioPrevisto, LocalDate fimPrevisto,
                       Double metaDiaria, boolean ativo) {
        this(id, obraId, areaId, descricao, unidade, quantidadePrevista, inicioPrevisto, fimPrevisto,
                metaDiaria, ativo, null, null, null);
    }
    @Override public String toString() { return descricao + " (" + unidade + ")"; }
}
