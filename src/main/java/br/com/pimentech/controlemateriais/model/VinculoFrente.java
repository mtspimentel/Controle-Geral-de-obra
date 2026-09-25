package br.com.pimentech.controlemateriais.model;

public record VinculoFrente(Long id, long obraId, long origemServicoId, long destinoServicoId, long areaId) { }
