package br.com.pimentech.controlemateriais.dto;

public record EntregaResumo(long id, String pedido, String fornecedor, String dataRecebimento,
                            String notaFiscal, String status, int itens) {
}
