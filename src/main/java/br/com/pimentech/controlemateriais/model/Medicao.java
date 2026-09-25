package br.com.pimentech.controlemateriais.model;

import java.time.Instant;
import java.time.LocalDate;

public record Medicao(
        Long id,
        long obraId,
        Long cronogramaItemId,
        Long orcamentoItemId,
        String numero,
        LocalDate data,
        String descricao,
        double quantidade,
        double percentualFisico,
        double valorMedido,
        String observacao,
        Instant createdAt,
        Instant updatedAt
) {
}
