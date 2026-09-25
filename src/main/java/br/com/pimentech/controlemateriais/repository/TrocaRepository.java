package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.dto.TrocaInput;

public interface TrocaRepository {

    long solicitar(TrocaInput input);

    void receber(long trocaId, java.time.LocalDate dataRecebimento, String observacao);
}
