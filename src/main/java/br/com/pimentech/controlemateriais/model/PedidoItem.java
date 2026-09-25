package br.com.pimentech.controlemateriais.model;

public record PedidoItem(
        Long id,
        Long materialId,
        String materialCodigo,
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
    public PedidoItem(Long id, Long materialId, String materialDescricao, String unidade,
                      double quantidadeSolicitada, double quantidadeComprada, double quantidadeRecebida,
                      double quantidadeAceita, double quantidadePendente, double custoUnitario,
                      String status, String motivoPendencia) {
        this(id, materialId, null, materialDescricao, unidade, quantidadeSolicitada, quantidadeComprada,
                quantidadeRecebida, quantidadeAceita, quantidadePendente, custoUnitario, status, motivoPendencia);
    }
}
