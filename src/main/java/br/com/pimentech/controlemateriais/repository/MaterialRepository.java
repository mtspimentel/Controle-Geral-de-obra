package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.model.Material;

import java.util.List;
import java.util.Optional;

public interface MaterialRepository {

    Optional<Material> findById(long id);

    List<Material> findByObraId(long obraId);

    List<Material> searchByObraId(long obraId, String search);

    long insert(Material material);

    void update(Material material);
}
