package br.com.pimentech.controlemateriais.model;

public enum SituacaoFrente {
    BLOQUEADO("Bloqueado"), PARCIAL("Liberado parcialmente"), LIBERADO("Liberado");
    private final String label;
    SituacaoFrente(String label) { this.label = label; }
    public String label() { return label; }
    @Override public String toString() { return label; }
}
