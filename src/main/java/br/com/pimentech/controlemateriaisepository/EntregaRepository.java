package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.dto.EntregaInput;

public interface EntregaRepository {

    long registrar(EntregaInput input);
}
