package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.model.Fornecedor;

import java.util.List;
import java.util.Optional;

public interface FornecedorRepository {

    Optional<Fornecedor> findById(long id);

    List<Fornecedor> findAll();

    List<Fornecedor> search(String search);

    long insert(Fornecedor fornecedor);

    void update(Fornecedor fornecedor);
}
