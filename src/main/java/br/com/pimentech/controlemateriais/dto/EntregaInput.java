package br.com.pimentech.controlemateriais.dto;

import java.time.LocalDate;
import java.util.List;

public record EntregaInput(
        long pedidoId,
        LocalDate dataPrevista,
        LocalDate dataRecebimento,
        String notaFiscal,
        String observacao,
        List<EntregaItemInput> itens
) {
}
