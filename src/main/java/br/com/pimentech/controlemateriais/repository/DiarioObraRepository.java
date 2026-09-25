package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.model.DiarioObra;

import java.util.List;
import java.util.Optional;

public interface DiarioObraRepository {

    List<DiarioObra> findByObraId(long obraId);

    Optional<DiarioObra> findByObraIdAndData(long obraId, String data);

    long insert(DiarioObra diario);

    void update(DiarioObra diario);
}
