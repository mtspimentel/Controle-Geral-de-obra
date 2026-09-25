package br.com.pimentech.controlemateriais.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record Pedido(
        Long id,
        String numero,
        Long obraId,
        String obraNome,
        Long requisicaoId,
        Long fornecedorId,
        String fornecedorNome,
        LocalDate dataPedido,
        LocalDate dataPrevistaEntrega,
        LocalDate dataRecebimentoCompleto,
        PedidoStatus status,
        String observacao,
        List<PedidoItem> itens,
        Instant createdAt,
        Instant updatedAt
) {
}
