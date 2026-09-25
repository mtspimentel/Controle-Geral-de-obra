package br.com.pimentech.controlemateriais.repository;

import br.com.pimentech.controlemateriais.model.EstoqueMovimentacao;

import java.util.List;

public interface EstoqueRepository {

    List<EstoqueMovimentacao> findMovimentacoes(long obraId);

    void registrar(EstoqueMovimentacao movimentacao, double variacaoSaldo);
}
