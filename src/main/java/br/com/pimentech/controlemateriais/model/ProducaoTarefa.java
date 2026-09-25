package br.com.pimentech.controlemateriais.model;

import java.time.LocalDate;
import java.util.List;

public record ProducaoTarefa(
        Long id,
        long obraId,
        long cronogramaItemId,
        long diarioObraId,
        LocalDate data,
        double quantidadeExecutada,
        String observacao,
        List<EfetivoAlocado> efetivo
) {
    public ProducaoTarefa { efetivo = efetivo == null ? List.of() : List.copyOf(efetivo); }
    public int pessoas() { return efetivo.stream().mapToInt(EfetivoAlocado::pessoas).sum(); }
    public double horasHomem() { return efetivo.stream().mapToDouble(EfetivoAlocado::horasHomem).sum(); }
    public double pessoasDia() { return horasHomem() / 8d; }
    public double produtividadePorPessoaDia() { return pessoasDia() == 0 ? 0 : quantidadeExecutada / pessoasDia(); }
    public double produtividadePorHoraHomem() { return horasHomem() == 0 ? 0 : quantidadeExecutada / horasHomem(); }
}
