package br.com.pimentech.controlemateriais.model;

import java.time.Instant;
import java.time.LocalDate;

public record DiarioObra(
        Long id,
        long obraId,
        LocalDate data,
        String atividades,
        String efetivo,
        String equipamentos,
        String clima,
        String observacoes,
        String intercorrencias,
        Instant createdAt,
        Instant updatedAt
) {
}
