package br.com.pimentech.controlemateriais.model;

public record TrechoVinculo(Long id, long vinculoId, String codigo, Long origemElementoId, Long destinoElementoId) { }
