package br.com.pimentech.controlemateriais.model;

public record RequisicaoItem(
        Long id,
        Long materialId,
        String materialCodigo,
        String materialDescricao,
        String unidade,
        double quantidade,
        String observacao
) {
    public RequisicaoItem(Long id, Long materialId, String materialDescricao, String unidade,
                          double quantidade, String observacao) {
        this(id, materialId, null, materialDescricao, unidade, quantidade, observacao);
    }
}
