package br.com.pimentech.controlemateriais.dto;

public record EntregaItemInput(
        long pedidoItemId,
        double quantidadeRecebida,
        double quantidadeAceita,
        boolean materialCorreto,
        String motivoRecusa
) {
}
