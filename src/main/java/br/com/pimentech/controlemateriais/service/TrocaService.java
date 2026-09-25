package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.dto.TrocaInput;
import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.repository.TrocaRepository;

import java.time.LocalDate;

public final class TrocaService {

    private final TrocaRepository repository;

    public TrocaService(TrocaRepository repository) {
        this.repository = repository;
    }

    public long solicitar(TrocaInput input) {
        if (input == null || input.pedidoItemId() <= 0) throw new ValidationException("Selecione um item de pedido válido");
        return repository.solicitar(input);
    }

    public void receber(long trocaId, LocalDate dataRecebimento, String observacao) {
        if (trocaId <= 0 || dataRecebimento == null) throw new ValidationException("Informe a troca e a data de recebimento");
        repository.receber(trocaId, dataRecebimento, observacao);
    }
}
