package br.com.pimentech.controlemateriais.model;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record VisaoPlanejamentoDia(LocalDate data, List<ResumoServicoDia> servicos,
                                  List<PendenciaDia> pendencias, Map<String, Double> producaoPorUnidade,
                                  int frentesComAvanco, int alocacoesTrabalhadores, int ocorrenciasNoDia) { }
