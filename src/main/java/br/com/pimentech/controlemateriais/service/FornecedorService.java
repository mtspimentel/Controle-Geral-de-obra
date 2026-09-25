package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.Fornecedor;
import br.com.pimentech.controlemateriais.repository.FornecedorRepository;

import java.time.Instant;
import java.util.List;

public final class FornecedorService {

    private final FornecedorRepository repository;

    public FornecedorService(FornecedorRepository repository) {
        this.repository = repository;
    }

    public List<Fornecedor> listar() {
        return repository.findAll();
    }

    public List<Fornecedor> pesquisar(String termo) {
        return repository.search(termo);
    }

    public Fornecedor criar(String nome, String cnpj, String telefone, String email, String contato, String observacao) {
        validate(nome);
        Instant now = Instant.now();
        Fornecedor fornecedor = new Fornecedor(null, nome.trim(), emptyToNull(cnpj), emptyToNull(telefone),
                emptyToNull(email), emptyToNull(contato), emptyToNull(observacao), true, now, now);
        long id = repository.insert(fornecedor);
        return repository.findById(id).orElseThrow(() -> new ValidationException("O fornecedor salvo não foi encontrado"));
    }

    public void atualizar(Fornecedor fornecedor) {
        if (fornecedor == null || fornecedor.id() == null) {
            throw new ValidationException("Selecione um fornecedor válido");
        }
        validate(fornecedor.nome());
        repository.update(new Fornecedor(fornecedor.id(), fornecedor.nome().trim(), emptyToNull(fornecedor.cnpj()),
                emptyToNull(fornecedor.telefone()), emptyToNull(fornecedor.email()), emptyToNull(fornecedor.contato()),
                emptyToNull(fornecedor.observacao()), fornecedor.ativo(), fornecedor.createdAt(), Instant.now()));
    }

    private void validate(String nome) {
        if (nome == null || nome.isBlank()) {
            throw new ValidationException("Informe o nome do fornecedor");
        }
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
