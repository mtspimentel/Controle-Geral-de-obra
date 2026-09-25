package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.dto.EntregaResumo;
import br.com.pimentech.controlemateriais.dto.TrocaResumo;

import java.util.List;

public interface AcompanhamentoRepository {

    List<EntregaResumo> entregasPorObra(long obraId);

    List<TrocaResumo> trocasPorObra(long obraId);
}
