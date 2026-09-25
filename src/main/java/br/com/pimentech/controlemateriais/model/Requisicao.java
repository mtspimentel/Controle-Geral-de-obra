package br.com.pimentech.controlemateriais.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record Requisicao(
        Long id,
        String numero,
        Long obraId,
        String obraNome,
        String solicitante,
        LocalDate dataRequisicao,
        Prioridade prioridade,
        RequisicaoStatus status,
        String observacao,
        List<RequisicaoItem> itens,
        Instant createdAt,
        Instant updatedAt
) {
}
