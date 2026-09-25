package br.com.pimentech.controlemateriais.model;

import java.time.Instant;

public record OrcamentoItem(
        Long id,
        long obraId,
        String codigo,
        String descricao,
        String unidade,
        double quantidade,
        double valorUnitario,
        String observacao,
        boolean ativo,
        Instant createdAt,
        Instant updatedAt
) {
    public double total() {
        return quantidade * valorUnitario;
    }

    @Override
    public String toString() {
        return (codigo == null || codigo.isBlank() ? "" : codigo + " - ") + descricao;
    }
}
