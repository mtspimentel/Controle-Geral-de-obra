package br.com.pimentech.controlemateriais.dto;

public record TrocaResumo(long id, String pedido, String material, String fornecedor, double quantidade,
                          String dataSolicitacao, String previsao, String dataRecebimento,
                          String status, String motivo) {
}
