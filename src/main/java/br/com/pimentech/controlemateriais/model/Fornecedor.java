package br.com.pimentech.controlemateriais.model;

import java.time.Instant;

public record Fornecedor(
        Long id,
        String nome,
        String cnpj,
        String telefone,
        String email,
        String contato,
        String observacao,
        boolean ativo,
        Instant createdAt,
        Instant updatedAt
) {
}
