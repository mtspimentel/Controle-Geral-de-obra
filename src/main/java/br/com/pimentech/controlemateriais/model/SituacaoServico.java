package br.com.pimentech.controlemateriais.model;

public enum SituacaoServico {
    NAO_INICIADO("Não iniciado"), EM_ANDAMENTO("Em andamento"), PARALISADO("Paralisado"), CONCLUIDO("Concluído");

    private final String label;
    SituacaoServico(String label) { this.label = label; }
    public String label() { return label; }
    @Override public String toString() { return label; }
}
