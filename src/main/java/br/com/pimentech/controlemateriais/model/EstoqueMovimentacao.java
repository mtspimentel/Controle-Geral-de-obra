package br.com.pimentech.controlemateriais.model;

import java.time.LocalDateTime;

public record EstoqueMovimentacao(
        Long id,
        long obraId,
        long materialId,
        String codigoMaterial,
        String descricaoMaterial,
        String unidade,
        LocalDateTime data,
        TipoMovimentacaoEstoque tipo,
        double quantidade,
        String responsavel,
        String retirante,
        String servico,
        String empresaLocataria,
        String observacao,
        String referencia
) {
}
