package br.com.pimentech.controlemateriais.model;

public record DisciplinaObra(Long id, long obraId, String nome) {
    @Override public String toString() { return nome; }
}
