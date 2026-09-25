package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.dto.EntregaResumo;
import br.com.pimentech.controlemateriais.dto.TrocaResumo;
import br.com.pimentech.controlemateriais.repository.AcompanhamentoRepository;

import java.util.List;

public final class AcompanhamentoService {

    private final AcompanhamentoRepository repository;

    public AcompanhamentoService(AcompanhamentoRepository repository) {
        this.repository = repository;
    }

    public List<EntregaResumo> entregas(long obraId) {
        return repository.entregasPorObra(obraId);
    }

    public List<TrocaResumo> trocas(long obraId) {
        return repository.trocasPorObra(obraId);
    }
}
