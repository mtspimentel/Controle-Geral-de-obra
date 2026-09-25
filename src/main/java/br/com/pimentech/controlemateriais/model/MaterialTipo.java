package br.com.pimentech.controlemateriais.model;

public enum MaterialTipo {
    MATERIAL("Material"),
    EQUIPAMENTO("Equipamento"),
    FERRAMENTA("Ferramenta"),
    OUTRO("Outro");

    private final String label;

    MaterialTipo(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    @Override
    public String toString() {
        return label;
    }
}
