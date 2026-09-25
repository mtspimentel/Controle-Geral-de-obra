package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.model.CronogramaItem;
import br.com.pimentech.controlemateriais.model.Medicao;
import br.com.pimentech.controlemateriais.model.ProducaoTarefa;
import br.com.pimentech.controlemateriais.model.OrcamentoItem;

import java.util.List;

public interface PlanejamentoRepository {
    List<CronogramaItem> listarCronograma(long obraId);
    long inserirCronograma(CronogramaItem item);
    void atualizarCronograma(CronogramaItem item);
    void importarProposta(long obraId, List<CronogramaItem> itens);
    void salvarExecucao(long obraId, long itemId, int mes, double valor);
    List<ProducaoTarefa> listarProducao(long obraId);
    long salvarProducao(ProducaoTarefa producao);
    void excluirProducao(long obraId, long producaoId);
    List<OrcamentoItem> listarOrcamento(long obraId);
    long inserirOrcamento(OrcamentoItem item);
    void atualizarOrcamento(OrcamentoItem item);
    List<Medicao> listarMedicoes(long obraId);
    long inserirMedicao(Medicao medicao);
    void atualizarMedicao(Medicao medicao);
}
