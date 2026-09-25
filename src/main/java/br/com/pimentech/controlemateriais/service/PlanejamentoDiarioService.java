package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.AreaObra;
import br.com.pimentech.controlemateriais.model.ElementoProducao;
import br.com.pimentech.controlemateriais.model.ElementoServico;
import br.com.pimentech.controlemateriais.model.Material;
import br.com.pimentech.controlemateriais.model.MaterialTipo;
import br.com.pimentech.controlemateriais.model.OcorrenciaServico;
import br.com.pimentech.controlemateriais.model.PendenciaDia;
import br.com.pimentech.controlemateriais.model.ProducaoDiaria;
import br.com.pimentech.controlemateriais.model.ResumoServicoDia;
import br.com.pimentech.controlemateriais.model.ServicoArea;
import br.com.pimentech.controlemateriais.model.SituacaoServico;
import br.com.pimentech.controlemateriais.model.TipoAreaObra;
import br.com.pimentech.controlemateriais.model.TipoOcorrenciaServico;
import br.com.pimentech.controlemateriais.model.VisaoPlanejamentoDia;
import br.com.pimentech.controlemateriais.repository.MaterialRepository;
import br.com.pimentech.controlemateriais.repository.PlanejamentoDiarioRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class PlanejamentoDiarioService {
    private static final double EPS = 0.000001;
    private final PlanejamentoDiarioRepository repository;
    private final MaterialRepository materialRepository;

    public PlanejamentoDiarioService(PlanejamentoDiarioRepository repository, MaterialRepository materialRepository) {
        this.repository = repository;
        this.materialRepository = materialRepository;
    }

    public List<AreaObra> listarAreas(long obraId) { return repository.listarAreas(obraId); }
    public List<ServicoArea> listarServicos(long obraId) { return repository.listarServicos(obraId); }
    public List<ProducaoDiaria> listarProducoes(long obraId) { return repository.listarProducoes(obraId); }
    public List<ElementoServico> listarElementos(long obraId) { return repository.listarElementos(obraId); }
    public List<ElementoProducao> listarElementosProducao(long obraId) { return repository.listarElementosProducao(obraId); }
    public List<OcorrenciaServico> listarOcorrencias(long obraId) { return repository.listarOcorrencias(obraId); }

    public AreaObra salvarArea(long obraId, Long id, Long paiId, TipoAreaObra tipo, String nome) {
        requireWork(obraId);
        if (tipo == null) throw new ValidationException("Escolha o tipo de área.");
        requireText(nome, "Informe o nome da área.");
        List<AreaObra> areas = listarAreas(obraId);
        if (id != null && areas.stream().noneMatch(area -> id.equals(area.id()))) throw new ValidationException("Área não encontrada nesta obra.");
        if (paiId != null) {
            Map<Long, AreaObra> byId = new HashMap<>();
            for (AreaObra area : areas) byId.put(area.id(), area);
            Long cursor = paiId;
            while (cursor != null) {
                if (cursor.equals(id)) throw new ValidationException("Uma área não pode ser filha dela mesma.");
                AreaObra parent = byId.get(cursor);
                if (parent == null) throw new ValidationException("A área superior não pertence à obra ativa.");
                cursor = parent.areaPaiId();
            }
        }
        if (areas.stream().anyMatch(area -> !Objects.equals(id, area.id()) && Objects.equals(paiId, area.areaPaiId())
                && area.tipo() == tipo && area.nome().equalsIgnoreCase(nome.trim())))
            throw new ValidationException("Já existe uma área com este nome no mesmo local.");
        long savedId = repository.salvarArea(new AreaObra(id, obraId, paiId, tipo, nome.trim(), true));
        return listarAreas(obraId).stream().filter(area -> area.id() == savedId).findFirst().orElseThrow();
    }

    public ServicoArea salvarServico(long obraId, Long id, long areaId, String descricao, String unidade,
                                    double quantidadePrevista, LocalDate inicio, LocalDate fim, Double metaDiaria) {
        requireWork(obraId);
        if (listarAreas(obraId).stream().noneMatch(area -> area.id() == areaId)) throw new ValidationException("Selecione uma área da obra.");
        requireText(descricao, "Descreva o serviço.");
        requireText(unidade, "Informe a unidade de medida.");
        if (!Double.isFinite(quantidadePrevista) || quantidadePrevista <= 0) throw new ValidationException("A quantidade prevista deve ser maior que zero.");
        if (metaDiaria != null && (!Double.isFinite(metaDiaria) || metaDiaria <= 0)) throw new ValidationException("A meta diária deve ser maior que zero.");
        if (inicio != null && fim != null && fim.isBefore(inicio)) throw new ValidationException("O término previsto não pode ser anterior ao início.");
        ServicoArea previous = id == null ? null : listarServicos(obraId).stream().filter(value -> id.equals(value.id()))
                .findFirst().orElseThrow(() -> new ValidationException("Serviço não encontrado nesta obra."));
        if (previous != null && !previous.unidade().equalsIgnoreCase(unidade.trim())
                && listarProducoes(obraId).stream().anyMatch(row -> row.servicoId() == id))
            throw new ValidationException("A unidade não pode mudar após haver produção registrada. Corrija os lançamentos primeiro.");
        long savedId = repository.salvarServico(new ServicoArea(id, obraId, areaId, descricao.trim(), unidade.trim(),
                quantidadePrevista, inicio, fim, metaDiaria, true));
        return listarServicos(obraId).stream().filter(value -> value.id() == savedId).findFirst().orElseThrow();
    }

    public ResultadoLancamento salvarDia(long obraId, Long id, long servicoId, LocalDate data, double quantidade,
                                         int trabalhadores, Double horasPorTrabalhador, String equipe, String observacao,
                                         TipoOcorrenciaServico tipoOcorrencia, String descricaoOcorrencia, Long materialId) {
        List<Long> existing = id == null ? List.of() : listarElementosProducao(obraId).stream()
                .filter(item -> item.producaoId() == id).map(ElementoProducao::elementoId).toList();
        return salvarDia(obraId, id, servicoId, data, quantidade, trabalhadores, horasPorTrabalhador,
                equipe, observacao, tipoOcorrencia, descricaoOcorrencia, materialId, existing);
    }

    public ResultadoLancamento salvarDia(long obraId, Long id, long servicoId, LocalDate data, double quantidade,
                                         int trabalhadores, Double horasPorTrabalhador, String equipe, String observacao,
                                         TipoOcorrenciaServico tipoOcorrencia, String descricaoOcorrencia, Long materialId,
                                         List<Long> elementoIds) {
        requireWork(obraId);
        ServicoArea servico = requireServico(obraId, servicoId);
        if (data == null) throw new ValidationException("Informe a data da produção.");
        if (!Double.isFinite(quantidade) || quantidade < 0) throw new ValidationException("A quantidade executada não pode ser negativa.");
        if (trabalhadores < 0 || (quantidade > EPS && trabalhadores == 0))
            throw new ValidationException("Informe o número de trabalhadores da produção.");
        if (horasPorTrabalhador != null && (!Double.isFinite(horasPorTrabalhador) || horasPorTrabalhador <= 0
                || horasPorTrabalhador > 24 || trabalhadores == 0))
            throw new ValidationException("Informe horas por trabalhador entre 0 e 24, com equipe maior que zero.");
        List<ProducaoDiaria> history = listarProducoes(obraId);
        if (id != null && history.stream().noneMatch(row -> id.equals(row.id()))) throw new ValidationException("Lançamento não encontrado nesta obra.");
        if (history.stream().anyMatch(row -> row.servicoId() == servicoId && row.data().equals(data) && !Objects.equals(row.id(), id)))
            throw new ValidationException("Já existe produção deste serviço nesta data. Selecione o lançamento no histórico para corrigir.");
        if (elementoIds == null) throw new ValidationException("Selecione os elementos atendidos.");
        if (elementoIds.stream().distinct().count() != elementoIds.size()) throw new ValidationException("Não repita elementos no lançamento.");
        var validElements = listarElementos(obraId).stream().filter(e -> e.servicoId() == servicoId)
                .map(ElementoServico::id).toList();
        if (!validElements.containsAll(elementoIds)) throw new ValidationException("Os elementos devem pertencer ao serviço e à obra ativa.");
        if (quantidade > EPS && !validElements.isEmpty() && elementoIds.isEmpty())
            throw new ValidationException("Selecione os elementos ou trechos atendidos nesta produção.");
        OcorrenciaServico occurrence = tipoOcorrencia == null ? null : validatedOccurrence(null, obraId, servicoId, data,
                tipoOcorrencia, descricaoOcorrencia, materialId, null);
        ProducaoDiaria production = new ProducaoDiaria(id, obraId, servicoId, data, quantidade, trabalhadores,
                horasPorTrabalhador, emptyToNull(equipe), emptyToNull(observacao));
        long savedId = repository.salvarDia(production, occurrence, elementoIds);
        double accumulated = listarProducoes(obraId).stream().filter(row -> row.servicoId() == servicoId)
                .mapToDouble(ProducaoDiaria::quantidade).sum();
        ProducaoDiaria saved = listarProducoes(obraId).stream().filter(row -> row.id() == savedId).findFirst().orElseThrow();
        return new ResultadoLancamento(saved, accumulated, accumulated > servico.quantidadePrevista() + EPS);
    }

    public OcorrenciaServico salvarOcorrencia(long obraId, Long id, long servicoId, LocalDate data,
                                              TipoOcorrenciaServico tipo, String descricao, Long materialId, LocalDate resolvidaEm) {
        requireWork(obraId);
        if (id != null && listarOcorrencias(obraId).stream().noneMatch(item -> id.equals(item.id())))
            throw new ValidationException("Ocorrência não encontrada nesta obra.");
        OcorrenciaServico occurrence = validatedOccurrence(id, obraId, servicoId, data, tipo, descricao, materialId, resolvidaEm);
        long savedId = repository.salvarOcorrencia(occurrence);
        return listarOcorrencias(obraId).stream().filter(item -> item.id() == savedId).findFirst().orElseThrow();
    }

    public void resolverOcorrencia(long obraId, long ocorrenciaId, LocalDate dataResolucao) {
        OcorrenciaServico occurrence = listarOcorrencias(obraId).stream().filter(item -> item.id() == ocorrenciaId)
                .findFirst().orElseThrow(() -> new ValidationException("Ocorrência não encontrada nesta obra."));
        if (dataResolucao != null && dataResolucao.isBefore(occurrence.data()))
            throw new ValidationException("A resolução não pode ser anterior à ocorrência.");
        repository.resolverOcorrencia(obraId, ocorrenciaId, dataResolucao);
    }

    public VisaoPlanejamentoDia visao(long obraId, LocalDate data) {
        requireWork(obraId);
        if (data == null) throw new ValidationException("Selecione a data da consulta.");
        List<AreaObra> areas = listarAreas(obraId);
        List<ServicoArea> services = listarServicos(obraId);
        List<ProducaoDiaria> production = listarProducoes(obraId);
        Map<Long, java.util.Set<Long>> productionScopes = new HashMap<>();
        for (ElementoProducao item : listarElementosProducao(obraId))
            productionScopes.computeIfAbsent(item.producaoId(), ignored -> new java.util.HashSet<>()).add(item.elementoId());
        List<OcorrenciaServico> occurrences = listarOcorrencias(obraId);
        Map<Long, AreaObra> byArea = new HashMap<>();
        for (AreaObra area : areas) byArea.put(area.id(), area);
        List<ResumoServicoDia> rows = new ArrayList<>();
        List<PendenciaDia> pending = new ArrayList<>();
        Map<String, Double> byUnit = new LinkedHashMap<>();
        int fronts = 0, workers = 0, occurrencesToday = 0;
        for (ServicoArea service : services) {
            List<ProducaoDiaria> serviceProduction = production.stream().filter(row -> row.servicoId() == service.id()).toList();
            List<OcorrenciaServico> serviceOccurrences = occurrences.stream().filter(row -> row.servicoId() == service.id()).toList();
            ResumoServicoDia row = calcular(service, byArea.get(service.areaId()), data, serviceProduction,
                    serviceOccurrences, productionScopes);
            rows.add(row);
            ProducaoDiaria day = serviceProduction.stream().filter(item -> item.data().equals(data)).findFirst().orElse(null);
            if (day != null) {
                workers += day.trabalhadores();
                if (day.quantidade() > EPS) {
                    fronts++;
                    byUnit.merge(service.unidade(), day.quantidade(), Double::sum);
                }
            }
            occurrencesToday += serviceOccurrences.stream().filter(item -> item.data().equals(data)).count();
            String areaName = row.area() == null ? "Área" : row.area().toString();
            if (row.atrasado()) pending.add(new PendenciaDia(service.id(), areaName, service.descricao(), "Prazo previsto vencido: revisar programação"));
            if (row.semAvancoRegistrado()) pending.add(new PendenciaDia(service.id(), areaName, service.descricao(), "Dia registrado sem avanço: verificar a frente"));
            else if (row.semLancamento()) pending.add(new PendenciaDia(service.id(), areaName, service.descricao(), "Sem lançamento nesta data: confirmar produção"));
            for (OcorrenciaServico occurrence : serviceOccurrences) {
                if (occurrence.abertaEm(data)) pending.add(new PendenciaDia(service.id(), areaName, service.descricao(),
                        occurrence.tipo().label() + ": " + occurrence.descricao()));
            }
        }
        return new VisaoPlanejamentoDia(data, List.copyOf(rows), List.copyOf(pending), Map.copyOf(byUnit),
                fronts, workers, occurrencesToday);
    }

    public static ResumoServicoDia calcular(ServicoArea servico, AreaObra area, LocalDate data,
                                             List<ProducaoDiaria> history, List<OcorrenciaServico> occurrences) {
        return calcular(servico, area, data, history, occurrences, Map.of());
    }

    public static ResumoServicoDia calcular(ServicoArea servico, AreaObra area, LocalDate data,
                                             List<ProducaoDiaria> history, List<OcorrenciaServico> occurrences,
                                             Map<Long, java.util.Set<Long>> productionScopes) {
        List<ProducaoDiaria> until = history.stream().filter(row -> !row.data().isAfter(data)).toList();
        ProducaoDiaria day = until.stream().filter(row -> row.data().equals(data)).findFirst().orElse(null);
        double accumulated = until.stream().mapToDouble(ProducaoDiaria::quantidade).sum();
        double doneToday = day == null ? 0 : day.quantidade();
        double remaining = servico.quantidadePrevista() - accumulated;
        boolean completed = remaining <= EPS;
        boolean stopped = occurrences.stream().anyMatch(item -> item.tipo() == TipoOcorrenciaServico.PARALISACAO && item.abertaEm(data));
        SituacaoServico status = completed ? SituacaoServico.CONCLUIDO : stopped ? SituacaoServico.PARALISADO
                : until.stream().anyMatch(row -> row.quantidade() > EPS) ? SituacaoServico.EM_ANDAMENTO : SituacaoServico.NAO_INICIADO;
        boolean late = !completed && servico.fimPrevisto() != null && data.isAfter(servico.fimPrevisto());
        boolean inPlannedWindow = servico.inicioPrevisto() != null && !data.isBefore(servico.inicioPrevisto())
                && (servico.fimPrevisto() == null || !data.isAfter(servico.fimPrevisto()));
        boolean zeroRecorded = day != null && day.quantidade() <= EPS && !completed;
        boolean missingRecord = day == null && inPlannedWindow && !completed;
        Double perPerson = day == null || day.trabalhadores() <= 0 ? null : doneToday / day.trabalhadores();
        Double perHour = day == null || day.homemHoras() == null || day.homemHoras() <= 0 ? null : doneToday / day.homemHoras();
        Double vsGoal = day == null || servico.metaDiaria() == null ? null : percentDifference(doneToday, servico.metaDiaria());
        java.util.Set<Long> currentScope = scopeOf(productionScopes, day);
        ProducaoDiaria previousPerson = until.stream().filter(row -> row.data().isBefore(data) && row.trabalhadores() > 0
                        && scopeOf(productionScopes, row).equals(currentScope))
                .max(Comparator.comparing(ProducaoDiaria::data)).orElse(null);
        ProducaoDiaria previousHour = until.stream().filter(row -> row.data().isBefore(data)
                        && row.homemHoras() != null && row.homemHoras() > 0
                        && scopeOf(productionScopes, row).equals(currentScope))
                .max(Comparator.comparing(ProducaoDiaria::data)).orElse(null);
        Double vsPerson = perPerson == null || previousPerson == null ? null
                : percentDifference(perPerson, previousPerson.quantidade() / previousPerson.trabalhadores());
        Double vsHour = perHour == null || previousHour == null ? null
                : percentDifference(perHour, previousHour.quantidade() / previousHour.homemHoras());
        return new ResumoServicoDia(servico, area, data, doneToday, accumulated, remaining,
                100d * accumulated / servico.quantidadePrevista(), status, late, zeroRecorded, missingRecord,
                perPerson, perHour, vsGoal, vsPerson, vsHour);
    }

    public static Double percentDifference(double actual, double reference) {
        return reference <= EPS ? null : (actual - reference) * 100d / reference;
    }
    private static java.util.Set<Long> scopeOf(Map<Long, java.util.Set<Long>> scopes, ProducaoDiaria production) {
        return production == null || production.id() == null ? java.util.Set.of()
                : scopes.getOrDefault(production.id(), java.util.Set.of());
    }

    private OcorrenciaServico validatedOccurrence(Long id, long obraId, long serviceId, LocalDate date,
                                                  TipoOcorrenciaServico type, String description, Long materialId,
                                                  LocalDate resolved) {
        requireServico(obraId, serviceId);
        if (date == null) throw new ValidationException("Informe a data da ocorrência.");
        if (type == null) throw new ValidationException("Escolha o tipo da ocorrência.");
        requireText(description, "Descreva a ocorrência; a causa não será presumida.");
        if (resolved != null && resolved.isBefore(date)) throw new ValidationException("A resolução não pode ser anterior à ocorrência.");
        if (materialId != null) {
            if (type != TipoOcorrenciaServico.FALTA_MATERIAL) throw new ValidationException("O material só pode ser vinculado a uma falta de material.");
            Material material = materialRepository.findById(materialId)
                    .orElseThrow(() -> new ValidationException("Material não encontrado."));
            if (!Objects.equals(material.obraId(), obraId) || !material.ativo() || material.tipo() != MaterialTipo.MATERIAL)
                throw new ValidationException("Selecione um material ativo desta obra.");
        }
        return new OcorrenciaServico(id, obraId, serviceId, date, type, description.trim(), materialId, resolved);
    }

    private ServicoArea requireServico(long obraId, long serviceId) {
        return listarServicos(obraId).stream().filter(item -> item.id() == serviceId).findFirst()
                .orElseThrow(() -> new ValidationException("Selecione um serviço da obra ativa."));
    }
    private void requireWork(long obraId) { if (obraId <= 0) throw new ValidationException("Selecione uma obra ativa."); }
    private void requireText(String text, String message) { if (text == null || text.isBlank()) throw new ValidationException(message); }
    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public record ResultadoLancamento(ProducaoDiaria producao, double acumulado, boolean ultrapassouPrevisto) { }
}
