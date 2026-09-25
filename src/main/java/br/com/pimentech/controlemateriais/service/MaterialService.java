package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.Material;
import br.com.pimentech.controlemateriais.model.MaterialTipo;
import br.com.pimentech.controlemateriais.repository.MaterialRepository;

import java.time.Instant;
import java.util.List;

public final class MaterialService {

    private final MaterialRepository repository;

    public MaterialService(MaterialRepository repository) {
        this.repository = repository;
    }

    public List<Material> listar(long obraId) {
        return repository.findByObraId(obraId);
    }

    public List<Material> pesquisar(long obraId, String termo) {
        return repository.searchByObraId(obraId, termo);
    }

    public Material criar(long obraId, String codigo, String descricao, String unidade, String categoria,
                          String especificacao, double estoqueMinimo, double consumoMedioDiario,
                          int prazoMedioEntregaDias, double estoqueAtual, String observacao) {
        return criar(obraId, MaterialTipo.MATERIAL, codigo, descricao, unidade, categoria, especificacao,
                estoqueMinimo, consumoMedioDiario, prazoMedioEntregaDias, 0, estoqueAtual, observacao);
    }

    public Material criar(long obraId, MaterialTipo tipo, String codigo, String descricao, String unidade, String categoria,
                          String especificacao, double estoqueMinimo, double consumoMedioDiario,
                          int prazoMedioEntregaDias, int diasLocado, double estoqueAtual, String observacao) {
        validate(obraId, tipo, codigo, descricao, unidade, estoqueMinimo, consumoMedioDiario, prazoMedioEntregaDias, diasLocado, estoqueAtual);
        Instant now = Instant.now();
        Material material = new Material(null, obraId, tipo, codigo.trim().toUpperCase(), descricao.trim(), unidade.trim().toUpperCase(),
                emptyToNull(categoria), emptyToNull(especificacao), estoqueMinimo, consumoMedioDiario,
                prazoMedioEntregaDias, diasLocado, emptyToNull(observacao), true, estoqueAtual, now, now);
        long id = repository.insert(material);
        return repository.findById(id).orElseThrow(() -> new ValidationException("O material salvo não foi encontrado"));
    }

    public void atualizar(Material material) {
        if (material == null || material.id() == null) {
            throw new ValidationException("Selecione um material válido");
        }
        validate(material.obraId(), material.tipo(), material.codigo(), material.descricao(), material.unidade(), material.estoqueMinimo(),
                material.consumoMedioDiario(), material.prazoMedioEntregaDias(), material.diasLocado(), material.estoqueAtual());
        repository.update(new Material(material.id(), material.obraId(), material.tipo(), material.codigo().trim().toUpperCase(),
                material.descricao().trim(), material.unidade().trim().toUpperCase(), emptyToNull(material.categoria()),
                emptyToNull(material.especificacao()), material.estoqueMinimo(), material.consumoMedioDiario(),
                material.prazoMedioEntregaDias(), material.diasLocado(), emptyToNull(material.observacao()), material.ativo(), material.estoqueAtual(),
                material.createdAt(), Instant.now()));
    }

    private void validate(long obraId, MaterialTipo tipo, String codigo, String descricao, String unidade, double estoqueMinimo,
                          double consumoMedioDiario, int prazoMedioEntregaDias, int diasLocado, double estoqueAtual) {
        if (obraId <= 0) {
            throw new ValidationException("Selecione a obra do material");
        }
        if (tipo == null) throw new ValidationException("Selecione o tipo do cadastro");
        if (codigo == null || codigo.isBlank()) {
            throw new ValidationException("Informe o código do material");
        }
        if (descricao == null || descricao.isBlank()) {
            throw new ValidationException("Informe a descrição do material");
        }
        if (unidade == null || unidade.isBlank()) {
            throw new ValidationException("Informe a unidade do material");
        }
        if (estoqueMinimo < 0 || consumoMedioDiario < 0 || prazoMedioEntregaDias < 0 || diasLocado < 0 || estoqueAtual < 0) {
            throw new ValidationException("Quantidades, consumo e prazo não podem ser negativos");
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
