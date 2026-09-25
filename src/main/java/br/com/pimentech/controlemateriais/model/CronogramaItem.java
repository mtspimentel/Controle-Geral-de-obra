package br.com.pimentech.controlemateriais.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public record CronogramaItem(
        Long id,
        long obraId,
        int ordem,
        String codigo,
        String descricao,
        String unidade,
        double quantidade,
        double pesoPercentual,
        LocalDate inicioPrevisto,
        LocalDate fimPrevisto,
        double percentualExecutado,
        String observacao,
        boolean ativo,
        Instant createdAt,
        Instant updatedAt,
        List<Double> valoresMensais,
        List<Double> valoresExecutados,
        Double totalReferencia,
        Double percentualReferencia,
        boolean metaFisicaDefinida
) {
    public static final int MESES = 24;

    public CronogramaItem {
        List<Double> values = new ArrayList<>();
        if (valoresMensais != null) values.addAll(valoresMensais);
        while (values.size() < MESES) values.add(0d);
        if (values.size() > MESES) values = new ArrayList<>(values.subList(0, MESES));
        valoresMensais = List.copyOf(values);
        values = new ArrayList<>();
        if (valoresExecutados != null) values.addAll(valoresExecutados);
        while (values.size() < MESES) values.add(0d);
        if (values.size() > MESES) values = new ArrayList<>(values.subList(0, MESES));
        valoresExecutados = List.copyOf(values);
    }

    public CronogramaItem(Long id, long obraId, int ordem, String codigo, String descricao, String unidade,
                          double quantidade, double pesoPercentual, LocalDate inicioPrevisto, LocalDate fimPrevisto,
                          double percentualExecutado, String observacao, boolean ativo, Instant createdAt,
                          Instant updatedAt, List<Double> valoresMensais) {
        this(id, obraId, ordem, codigo, descricao, unidade, quantidade, pesoPercentual, inicioPrevisto,
                fimPrevisto, percentualExecutado, observacao, ativo, createdAt, updatedAt,
                valoresMensais, List.of(), null, null);
    }

    public CronogramaItem(Long id, long obraId, int ordem, String codigo, String descricao, String unidade,
                          double quantidade, double pesoPercentual, LocalDate inicioPrevisto, LocalDate fimPrevisto,
                          double percentualExecutado, String observacao, boolean ativo, Instant createdAt,
                          Instant updatedAt, List<Double> valoresMensais, List<Double> valoresExecutados,
                          Double totalReferencia, Double percentualReferencia) {
        this(id, obraId, ordem, codigo, descricao, unidade, quantidade, pesoPercentual, inicioPrevisto,
                fimPrevisto, percentualExecutado, observacao, ativo, createdAt, updatedAt, valoresMensais,
                valoresExecutados, totalReferencia, percentualReferencia, totalReferencia == null && quantidade > 0);
    }

    public double valorMensal(int mes) {
        return mes < 1 || mes > MESES ? 0 : valoresMensais.get(mes - 1);
    }

    public double totalPrevisto() {
        return totalReferencia == null ? valoresMensais.stream().mapToDouble(Double::doubleValue).sum() : totalReferencia;
    }

    public double valorExecutado(int mes) {
        return mes < 1 || mes > MESES ? 0 : valoresExecutados.get(mes - 1);
    }

    public double totalExecutado() {
        return valoresExecutados.stream().mapToDouble(Double::doubleValue).sum();
    }

    @Override
    public String toString() {
        return (codigo == null || codigo.isBlank() ? "" : codigo + " - ") + descricao;
    }
}
