package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.EstoqueMovimentacao;
import br.com.pimentech.controlemateriais.model.Material;
import br.com.pimentech.controlemateriais.model.RetiradaPendente;
import br.com.pimentech.controlemateriais.model.TipoMovimentacaoEstoque;
import br.com.pimentech.controlemateriais.repository.EstoqueRepository;
import br.com.pimentech.controlemateriais.repository.MaterialRepository;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class AlmoxarifadoService {

    private final EstoqueRepository estoqueRepository;
    private final MaterialRepository materialRepository;

    public AlmoxarifadoService(EstoqueRepository estoqueRepository, MaterialRepository materialRepository) {
        this.estoqueRepository = estoqueRepository;
        this.materialRepository = materialRepository;
    }

    public List<EstoqueMovimentacao> listarMovimentacoes(long obraId) {
        return estoqueRepository.findMovimentacoes(obraId);
    }

    public List<RetiradaPendente> listarRetiradasPendentes(long obraId) {
        Map<String, EstoqueMovimentacao> retiradas = new LinkedHashMap<>();
        Map<String, Double> devolvido = new LinkedHashMap<>();
        for (EstoqueMovimentacao item : listarMovimentacoes(obraId)) {
            if (item.tipo() == TipoMovimentacaoEstoque.RETIRADA && hasText(item.referencia())) {
                retiradas.put(item.referencia(), item);
                devolvido.putIfAbsent(item.referencia(), 0D);
            } else if (item.tipo() == TipoMovimentacaoEstoque.DEVOLUCAO && hasText(item.referencia())) {
                devolvido.merge(item.referencia(), item.quantidade(), Double::sum);
            }
        }
        return retiradas.entrySet().stream().map(entry -> {
            EstoqueMovimentacao retirada = entry.getValue();
            double saldo = retirada.quantidade() - devolvido.getOrDefault(entry.getKey(), 0D);
            return new RetiradaPendente(entry.getKey(), retirada.materialId(), retirada.codigoMaterial(),
                    retirada.descricaoMaterial(), retirada.unidade(), saldo, retirada.retirante(), retirada.servico(),
                    retirada.empresaLocataria());
        }).filter(item -> item.quantidade() > 0).toList();
    }

    public void registrarEntrada(long obraId, long materialId, double quantidade, String responsavel, String observacao) {
        registrar(obraId, materialId, quantidade, TipoMovimentacaoEstoque.ENTRADA, responsavel, "", "", "", observacao, "", quantidade);
    }

    public void registrarSaida(long obraId, long materialId, double quantidade, String retirante, String servico,
                               String responsavel, String observacao) {
        registrar(obraId, materialId, quantidade, TipoMovimentacaoEstoque.SAIDA, responsavel, retirante, servico, "", observacao, "", -quantidade);
    }

    public void registrarRetirada(long obraId, long materialId, double quantidade, String retirante, String servico,
                                  String responsavel, String observacao) {
        registrarRetirada(obraId, materialId, quantidade, retirante, servico, responsavel, "", observacao);
    }

    public void registrarRetirada(long obraId, long materialId, double quantidade, String retirante, String servico,
                                  String responsavel, String empresaLocataria, String observacao) {
        registrar(obraId, materialId, quantidade, TipoMovimentacaoEstoque.RETIRADA, responsavel, retirante, servico,
                empresaLocataria, observacao, UUID.randomUUID().toString(), -quantidade);
    }

    public void registrarDevolucao(long obraId, String referencia, double quantidade, String conferente, String observacao) {
        requireText(referencia, "Selecione uma retirada pendente");
        requireText(conferente, "Informe o conferente");
        RetiradaPendente pendente = listarRetiradasPendentes(obraId).stream()
                .filter(item -> item.referencia().equals(referencia))
                .findFirst()
                .orElseThrow(() -> new ValidationException("Essa retirada já foi devolvida ou não existe."));
        requirePositive(quantidade);
        if (quantidade > pendente.quantidade()) {
            throw new ValidationException("A devolução não pode ser maior que o saldo retirado: " + number(pendente.quantidade()));
        }
        registrar(obraId, pendente.materialId(), quantidade, TipoMovimentacaoEstoque.DEVOLUCAO, conferente,
                pendente.retirante(), pendente.servico(), pendente.empresaLocataria(), observacao, referencia, quantidade);
    }

    private void registrar(long obraId, long materialId, double quantidade, TipoMovimentacaoEstoque tipo,
                           String responsavel, String retirante, String servico, String empresaLocataria, String observacao,
                           String referencia, double variacaoSaldo) {
        requirePositive(quantidade);
        requireText(responsavel, tipo == TipoMovimentacaoEstoque.DEVOLUCAO ? "Informe o conferente" : "Informe o responsável");
        if (tipo == TipoMovimentacaoEstoque.SAIDA || tipo == TipoMovimentacaoEstoque.RETIRADA) {
            requireText(retirante, "Informe o retirante");
            requireText(servico, "Informe o serviço");
        }
        Material material = materialRepository.findById(materialId)
                .orElseThrow(() -> new ValidationException("Material não encontrado."));
        if (material.obraId() != obraId || !material.ativo()) {
            throw new ValidationException("O material não pertence à obra ativa.");
        }
        if (tipo == TipoMovimentacaoEstoque.RETIRADA && material.tipo() == br.com.pimentech.controlemateriais.model.MaterialTipo.EQUIPAMENTO) {
            if (Math.abs(quantidade - 1D) > 0.0000001D) {
                throw new ValidationException("A retirada de um equipamento deve usar quantidade 1 e o código do equipamento selecionado.");
            }
            requireText(empresaLocataria, "Informe a empresa locatária do equipamento");
        }
        EstoqueMovimentacao movimentacao = new EstoqueMovimentacao(null, obraId, materialId, material.codigo(),
                material.descricao(), material.unidade(), LocalDateTime.now(), tipo, quantidade,
                responsavel.trim(), empty(retirante), empty(servico), empty(empresaLocataria), empty(observacao), empty(referencia));
        estoqueRepository.registrar(movimentacao, variacaoSaldo);
    }

    private void requirePositive(double value) {
        if (!Double.isFinite(value) || value <= 0) throw new ValidationException("A quantidade deve ser maior que zero.");
    }

    private void requireText(String value, String message) {
        if (!hasText(value)) throw new ValidationException(message);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String empty(String value) {
        return value == null ? "" : value.trim();
    }

    private String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format("%.2f", value);
    }
}
