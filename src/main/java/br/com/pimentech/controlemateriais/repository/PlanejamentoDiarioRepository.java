package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.model.AreaObra;
import br.com.pimentech.controlemateriais.model.ElementoProducao;
import br.com.pimentech.controlemateriais.model.ElementoServico;
import br.com.pimentech.controlemateriais.model.OcorrenciaServico;
import br.com.pimentech.controlemateriais.model.ProducaoDiaria;
import br.com.pimentech.controlemateriais.model.ServicoArea;

import java.time.LocalDate;
import java.util.List;

public interface PlanejamentoDiarioRepository {
    List<AreaObra> listarAreas(long obraId);
    long salvarArea(AreaObra area);
    List<ServicoArea> listarServicos(long obraId);
    long salvarServico(ServicoArea servico);
    List<ProducaoDiaria> listarProducoes(long obraId);
    List<ElementoServico> listarElementos(long obraId);
    List<ElementoProducao> listarElementosProducao(long obraId);
    List<OcorrenciaServico> listarOcorrencias(long obraId);
    default long salvarDia(ProducaoDiaria producao, OcorrenciaServico ocorrencia) {
        return salvarDia(producao, ocorrencia, List.of());
    }
    long salvarDia(ProducaoDiaria producao, OcorrenciaServico ocorrencia, List<Long> elementos);
    long salvarOcorrencia(OcorrenciaServico ocorrencia);
    void resolverOcorrencia(long obraId, long ocorrenciaId, LocalDate dataResolucao);
}
