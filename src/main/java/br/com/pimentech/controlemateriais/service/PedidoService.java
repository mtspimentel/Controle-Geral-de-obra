package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.Pedido;
import br.com.pimentech.controlemateriais.model.PedidoItem;
import br.com.pimentech.controlemateriais.model.PedidoStatus;
import br.com.pimentech.controlemateriais.model.Fornecedor;
import br.com.pimentech.controlemateriais.model.Requisicao;
import br.com.pimentech.controlemateriais.model.RequisicaoItem;
import br.com.pimentech.controlemateriais.repository.PedidoRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PedidoService {

    private final PedidoRepository repository;

    public PedidoService(PedidoRepository repository) {
        this.repository = repository;
    }

    public List<Pedido> listar(long obraId) {
        return repository.findByObraId(obraId);
    }

    public void definirFornecedor(Pedido pedido, Fornecedor fornecedor) {
        if (pedido == null || pedido.id() == null) throw new ValidationException("Selecione um pedido válido");
        if (fornecedor == null || fornecedor.id() == null) throw new ValidationException("Selecione um fornecedor");
        repository.definirFornecedor(pedido.id(), fornecedor.id());
    }

    public void alterarStatus(Pedido pedido, PedidoStatus status) {
        if (pedido == null || pedido.id() == null) throw new ValidationException("Selecione um pedido válido");
        if (status == null) throw new ValidationException("Selecione um status");
        repository.atualizarStatus(pedido.id(), status);
    }

    public void excluir(Pedido pedido) {
        if (pedido == null || pedido.id() == null) throw new ValidationException("Selecione um pedido válido");
        repository.excluir(pedido.id());
    }

    public Pedido criarAPartirDaRequisicao(Requisicao requisicao, Long fornecedorId,
                                           String fornecedorNome, LocalDate dataPedido,
                                           LocalDate dataPrevista, String observacao) {
        if (requisicao == null || requisicao.id() == null) throw new ValidationException("Selecione uma requisição válida");
        if (dataPedido == null) throw new ValidationException("Informe a data do pedido");
        if (dataPrevista != null && dataPrevista.isBefore(dataPedido)) throw new ValidationException("A previsão não pode ser anterior à data do pedido");
        if (requisicao.itens() == null || requisicao.itens().isEmpty()) throw new ValidationException("A requisição não possui itens");
        Instant now = Instant.now();
        List<PedidoItem> itens = requisicao.itens().stream().map(this::toPedidoItem).toList();
        Pedido pedido = new Pedido(null, String.format("PED-%03d", repository.proximoNumero()),
                requisicao.obraId(), requisicao.obraNome(), requisicao.id(), fornecedorId, fornecedorNome,
                dataPedido, dataPrevista, null, PedidoStatus.SOLICITADO, emptyToNull(observacao), itens, now, now);
        long id = repository.insert(pedido);
        return repository.findById(id).orElseThrow(() -> new ValidationException("O pedido salvo não foi encontrado"));
    }

    private PedidoItem toPedidoItem(RequisicaoItem item) {
        return new PedidoItem(null, item.materialId(), item.materialCodigo(), item.materialDescricao(), item.unidade(), item.quantidade(),
                item.quantidade(), 0, 0, item.quantidade(), 0, "PENDENTE", null);
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
