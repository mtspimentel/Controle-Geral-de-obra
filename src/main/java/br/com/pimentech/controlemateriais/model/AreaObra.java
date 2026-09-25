package br.com.pimentech.controlemateriais.model;

public record AreaObra(Long id, long obraId, Long areaPaiId, TipoAreaObra tipo, String nome, boolean ativo) {
    @Override public String toString() { return tipo.label() + " • " + nome; }
}
