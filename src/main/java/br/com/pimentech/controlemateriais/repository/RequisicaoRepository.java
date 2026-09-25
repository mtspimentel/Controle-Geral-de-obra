package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.model.Requisicao;

import java.util.List;
import java.util.Optional;

public interface RequisicaoRepository {

    List<Requisicao> findByObraId(long obraId);

    Optional<Requisicao> findById(long id);

    long insert(Requisicao requisicao);

    void update(Requisicao requisicao);
}
