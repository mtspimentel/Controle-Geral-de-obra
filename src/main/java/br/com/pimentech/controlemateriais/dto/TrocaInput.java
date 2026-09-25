package br.com.pimentech.controlemateriais.dto;

import java.time.LocalDate;

public record TrocaInput(
        long pedidoItemId,
        long fornecedorId,
        LocalDate dataSolicitacao,
        double quantidade,
        String motivo,
        LocalDate previsaoTroca,
        String observacao
) {
}
