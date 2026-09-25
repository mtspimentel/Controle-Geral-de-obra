package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.model.Pedido;
import br.com.pimentech.controlemateriais.model.PedidoStatus;

import java.util.List;
import java.util.Optional;

public interface PedidoRepository {

    List<Pedido> findByObraId(long obraId);

    Optional<Pedido> findById(long id);

    long insert(Pedido pedido);

    int proximoNumero();

    void definirFornecedor(long pedidoId, long fornecedorId);

    void atualizarStatus(long pedidoId, PedidoStatus status);

    void excluir(long pedidoId);
}
