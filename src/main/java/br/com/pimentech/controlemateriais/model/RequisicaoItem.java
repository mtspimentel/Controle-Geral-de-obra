package br.com.pimentech.controlemateriais.model;

public record RequisicaoItem(
        Long id,
        Long materialId,
        String materialDescricao,
        String unidade,
        double quantidade,
        String observacao
) {
}
