package br.com.pimentech.controlemateriais.model;

public record ElementoServico(Long id, long obraId, long servicoId, String codigo) {
    @Override public String toString() { return codigo; }
}
