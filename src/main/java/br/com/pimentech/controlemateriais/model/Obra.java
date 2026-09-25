package br.com.pimentech.controlemateriais.model;

import java.time.Instant;
import java.time.LocalDate;

public record Obra(
        Long id,
        String nome,
        String codigo,
        String endereco,
        String responsavel,
        LocalDate dataInicio,
        LocalDate previsaoTermino,
        ObraStatus status,
        boolean ativo,
        Instant createdAt,
        Instant updatedAt
) {
}
