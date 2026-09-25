package br.com.pimentech.controlemateriais.model;

public record PlanejamentoResumo(
        double percentualPlanejado,
        double percentualExecutado,
        double orcamentoTotal,
        double valorMedido
) {
    public double diferenca() {
        return percentualExecutado - percentualPlanejado;
    }

    public double saldoOrcamento() {
        return orcamentoTotal - valorMedido;
    }
}
