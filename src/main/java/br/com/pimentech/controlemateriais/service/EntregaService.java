package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.dto.EntregaInput;
import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.repository.EntregaRepository;

public final class EntregaService {

    private final EntregaRepository repository;

    public EntregaService(EntregaRepository repository) {
        this.repository = repository;
    }

    public long registrar(EntregaInput input) {
        if (input == null || input.pedidoId() <= 0) throw new ValidationException("Selecione um pedido válido");
        if (input.dataRecebimento() == null) throw new ValidationException("Informe a data de recebimento");
        return repository.registrar(input);
    }
}
