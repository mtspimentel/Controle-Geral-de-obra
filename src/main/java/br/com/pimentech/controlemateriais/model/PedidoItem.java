package br.com.pimentech.controlemateriais.model;

public record PedidoItem(
        Long id,
        Long materialId,
        String materialDescricao,
        String unidade,
        double quantidadeSolicitada,
        double quantidadeComprada,
        double quantidadeRecebida,
        double quantidadeAceita,
        double quantidadePendente,
        double custoUnitario,
        String status,
        String motivoPendencia
) {
}
