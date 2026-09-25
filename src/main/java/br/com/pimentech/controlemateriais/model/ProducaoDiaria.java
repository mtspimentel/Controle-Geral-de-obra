package br.com.pimentech.controlemateriais.model;

import java.time.LocalDate;

public record ProducaoDiaria(Long id, long obraId, long servicoId, LocalDate data, double quantidade,
                             int trabalhadores, Double horasPorTrabalhador, String equipe, String observacao) {
    public Double homemHoras() { return horasPorTrabalhador == null ? null : trabalhadores * horasPorTrabalhador; }
}
