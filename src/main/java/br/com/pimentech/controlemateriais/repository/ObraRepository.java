package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.model.Obra;

import java.util.List;
import java.util.Optional;

public interface ObraRepository {

    Optional<Obra> findById(long id);

    Optional<Obra> findActive();

    List<Obra> findAll();

    long insert(Obra obra);

    void update(Obra obra);
}
