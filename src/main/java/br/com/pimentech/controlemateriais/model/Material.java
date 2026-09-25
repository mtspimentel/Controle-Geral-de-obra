package br.com.pimentech.controlemateriais.model;

import java.time.Instant;

public record Material(
        Long id,
        Long obraId,
        MaterialTipo tipo,
        String codigo,
        String descricao,
        String unidade,
        String categoria,
        String especificacao,
        double estoqueMinimo,
        double consumoMedioDiario,
        int prazoMedioEntregaDias,
        int diasLocado,
        String observacao,
        boolean ativo,
        double estoqueAtual,
        Instant createdAt,
        Instant updatedAt
) {
}
