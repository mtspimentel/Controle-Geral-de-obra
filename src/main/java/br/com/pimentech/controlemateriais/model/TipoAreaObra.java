package br.com.pimentech.controlemateriais.model;

public enum TipoAreaObra {
    BLOCO("Bloco"), PAVIMENTO("Pavimento"), SETOR("Setor"), AMBIENTE("Ambiente"), OUTRA("Outra área");

    private final String label;
    TipoAreaObra(String label) { this.label = label; }
    public String label() { return label; }
    @Override public String toString() { return label; }
}
