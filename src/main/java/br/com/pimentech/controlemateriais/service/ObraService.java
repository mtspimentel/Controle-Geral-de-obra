package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.Obra;
import br.com.pimentech.controlemateriais.model.ObraStatus;
import br.com.pimentech.controlemateriais.repository.ObraRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class ObraService {

    private final ObraRepository repository;

    public ObraService(ObraRepository repository) {
        this.repository = repository;
    }

    public List<Obra> listar() {
        return repository.findAll();
    }

    public Obra obraAtiva() {
        return repository.findActive().orElse(null);
    }

    public Obra criar(String nome, String codigo, String endereco, String responsavel,
                      LocalDate dataInicio, LocalDate previsaoTermino, ObraStatus status) {
        validate(nome, codigo, dataInicio, previsaoTermino);
        Instant now = Instant.now();
        Obra obra = new Obra(null, nome.trim(), codigo.trim().toUpperCase(), emptyToNull(endereco),
                emptyToNull(responsavel), dataInicio, previsaoTermino,
                status == null ? ObraStatus.ATIVA : status, true, now, now);
        long id = repository.insert(obra);
        return repository.findById(id).orElseThrow(() -> new ValidationException("A obra salva não foi encontrada"));
    }

    public void atualizar(Obra obra) {
        if (obra == null || obra.id() == null) {
            throw new ValidationException("Selecione uma obra válida");
        }
        validate(obra.nome(), obra.codigo(), obra.dataInicio(), obra.previsaoTermino());
        repository.update(new Obra(obra.id(), obra.nome().trim(), obra.codigo().trim().toUpperCase(),
                emptyToNull(obra.endereco()), emptyToNull(obra.responsavel()), obra.dataInicio(),
                obra.previsaoTermino(), obra.status(), obra.ativo(), obra.createdAt(), Instant.now()));
    }

    private void validate(String nome, String codigo, LocalDate dataInicio, LocalDate previsaoTermino) {
        if (nome == null || nome.isBlank()) {
            throw new ValidationException("Informe o nome da obra");
        }
        if (codigo == null || codigo.isBlank()) {
            throw new ValidationException("Informe o código da obra");
        }
        if (dataInicio != null && previsaoTermino != null && previsaoTermino.isBefore(dataInicio)) {
            throw new ValidationException("A previsão de término não pode ser anterior ao início");
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
