package br.com.pimentech.controlemateriais.model;

import java.time.LocalDate;

public record ResumoServicoDia(ServicoArea servico, AreaObra area, LocalDate data,
                              double feitoNoDia, double acumulado, double saldo, double percentual,
                              SituacaoServico situacao, boolean atrasado, boolean semAvancoRegistrado,
                              boolean semLancamento, Double produtividadePessoaDia, Double produtividadeHomemHora,
                              Double comparacaoMetaPercentual, Double comparacaoAnteriorPessoaPercentual,
                              Double comparacaoAnteriorHoraPercentual) {
    public boolean ultrapassouPrevisto() { return saldo < -0.000001; }
}
