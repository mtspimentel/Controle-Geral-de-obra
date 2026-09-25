package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.Prioridade;
import br.com.pimentech.controlemateriais.model.Requisicao;
import br.com.pimentech.controlemateriais.model.RequisicaoItem;
import br.com.pimentech.controlemateriais.model.RequisicaoStatus;
import br.com.pimentech.controlemateriais.repository.RequisicaoRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class RequisicaoService {

    private final RequisicaoRepository repository;

    public RequisicaoService(RequisicaoRepository repository) {
        this.repository = repository;
    }

    public List<Requisicao> listar(long obraId) {
        return repository.findByObraId(obraId);
    }

    public void atualizar(Requisicao atual, String solicitante, LocalDate data, Prioridade prioridade,
                          String observacao, List<RequisicaoItem> itens) {
        if (atual == null || atual.id() == null) throw new ValidationException("Selecione uma requisição válida");
        if (atual.status() == RequisicaoStatus.CONVERTIDA) throw new ValidationException("Requisições convertidas em pedido não podem ser editadas");
        if (solicitante == null || solicitante.isBlank()) throw new ValidationException("Informe o solicitante");
        if (data == null) throw new ValidationException("Informe a data da requisição");
        validateItems(itens);
        repository.update(new Requisicao(atual.id(), atual.numero(), atual.obraId(), atual.obraNome(), solicitante.trim(),
                data, prioridade == null ? Prioridade.NORMAL : prioridade, atual.status(), emptyToNull(observacao),
                List.copyOf(itens), atual.createdAt(), Instant.now()));
    }

    public Requisicao criar(long obraId, String obraNome, String solicitante, LocalDate data,
                            Prioridade prioridade, String observacao, List<RequisicaoItem> itens) {
        if (obraId <= 0) throw new ValidationException("Selecione a obra da requisição");
        if (solicitante == null || solicitante.isBlank()) throw new ValidationException("Informe o solicitante");
        if (data == null) throw new ValidationException("Informe a data da requisição");
        validateItems(itens);
        Instant now = Instant.now();
        Requisicao requisicao = new Requisicao(null, "REQ-" + DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(java.time.LocalDateTime.now()),
                obraId, obraNome, solicitante.trim(), data, prioridade == null ? Prioridade.NORMAL : prioridade,
                RequisicaoStatus.ENVIADA, emptyToNull(observacao), List.copyOf(itens), now, now);
        long id = repository.insert(requisicao);
        return repository.findById(id).orElseThrow(() -> new ValidationException("A requisição salva não foi encontrada"));
    }

    private void validateItems(List<RequisicaoItem> itens) {
        if (itens == null || itens.isEmpty()) throw new ValidationException("Adicione pelo menos um material");
        Set<Long> materials = new HashSet<>();
        for (RequisicaoItem item : itens) {
            if (item.materialId() == null || item.materialId() <= 0) throw new ValidationException("Há um material inválido na requisição");
            if (item.quantidade() <= 0) throw new ValidationException("As quantidades devem ser maiores que zero");
            if (!materials.add(item.materialId())) throw new ValidationException("O mesmo material não pode ser repetido na requisição");
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
