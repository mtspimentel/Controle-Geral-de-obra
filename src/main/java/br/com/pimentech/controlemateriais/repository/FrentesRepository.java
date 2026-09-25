package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.model.*;
import java.util.List;

public interface FrentesRepository {
    List<DisciplinaObra> disciplinas(long obraId);
    void criarDisciplinasPadrao(long obraId, List<String> nomes);
    long criarDisciplina(long obraId, String nome);
    void atualizarDetalhesServico(long obraId, long servicoId, long disciplinaId, String equipe, String observacoes,
                                  List<String> novosElementos);
    List<VinculoFrente> vinculos(long obraId);
    List<TrechoVinculo> trechos(long obraId);
    List<LiberacaoTrecho> liberacoes(long obraId);
    long criarVinculo(VinculoFrente vinculo, List<TrechoVinculo> trechos);
    long registrarLiberacao(LiberacaoTrecho liberacao);
}
