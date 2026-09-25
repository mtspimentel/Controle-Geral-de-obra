package br.com.pimentech.controlemateriais.model;

public enum TipoOcorrenciaServico {
    FALTA_MATERIAL("Falta de material"), CHUVA("Chuva"), ATRASO_LIBERACAO("Frente liberada com atraso"),
    RETRABALHO("Retrabalho"), PARALISACAO("Paralisação"), OUTRA("Outra ocorrência");

    private final String label;
    TipoOcorrenciaServico(String label) { this.label = label; }
    public String label() { return label; }
    @Override public String toString() { return label; }
}
