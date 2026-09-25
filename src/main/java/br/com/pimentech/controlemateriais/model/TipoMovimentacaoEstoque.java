package br.com.pimentech.controlemateriais.model;

public enum TipoMovimentacaoEstoque {
    ENTRADA("Entrada"),
    SAIDA("Saída definitiva"),
    RETIRADA("Retirada para uso"),
    DEVOLUCAO("Devolução");

    private final String label;

    TipoMovimentacaoEstoque(String label) {
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
