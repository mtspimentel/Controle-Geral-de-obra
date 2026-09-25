package br.com.pimentech.controlemateriais.model;

import java.time.LocalDate;

public record LiberacaoTrecho(Long id, long trechoId, LocalDate data, boolean liberado,
                             String responsavel, String observacao) { }
