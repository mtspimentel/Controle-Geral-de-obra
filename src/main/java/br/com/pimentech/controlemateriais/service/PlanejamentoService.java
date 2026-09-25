package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.CronogramaItem;
import br.com.pimentech.controlemateriais.model.DiarioObra;
import br.com.pimentech.controlemateriais.model.EfetivoAlocado;
import br.com.pimentech.controlemateriais.model.Medicao;
import br.com.pimentech.controlemateriais.model.OrcamentoItem;
import br.com.pimentech.controlemateriais.model.PlanejamentoResumo;
import br.com.pimentech.controlemateriais.model.ProducaoTarefa;
import br.com.pimentech.controlemateriais.repository.PlanejamentoRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class PlanejamentoService {
    private final PlanejamentoRepository repository;
    private final DiarioObraService diarioObraService;
    private static final Pattern EFETIVO = Pattern.compile("^(\\d+)\\s*[xX-]\\s*(.+)$");

    public PlanejamentoService(PlanejamentoRepository repository) {
        this(repository, null);
    }

    public PlanejamentoService(PlanejamentoRepository repository, DiarioObraService diarioObraService) {
        this.repository = repository;
        this.diarioObraService = diarioObraService;
    }

    public List<CronogramaItem> listarCronograma(long obraId) { return repository.listarCronograma(obraId); }
    public List<OrcamentoItem> listarOrcamento(long obraId) { return repository.listarOrcamento(obraId); }
    public List<Medicao> listarMedicoes(long obraId) { return repository.listarMedicoes(obraId); }
    public List<ProducaoTarefa> listarProducao(long obraId) { return repository.listarProducao(obraId); }

    public Map<String, Integer> efetivoDoDiario(long obraId, long diarioId) {
        return parseEfetivo(requireDiario(obraId, diarioId).efetivo());
    }

    public static Map<String, Integer> parseEfetivo(String raw) {
        Map<String, Integer> people = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) return people;
        for (String part : raw.split("[;\\n]+")) {
            Matcher matcher = EFETIVO.matcher(part.trim());
            if (!matcher.matches()) continue;
            String role = matcher.group(2).trim();
            if (!role.isEmpty()) people.merge(role, Integer.parseInt(matcher.group(1)), Integer::sum);
        }
        return people;
    }

    public ProducaoTarefa salvarProducao(long obraId, Long id, long tarefaId, long diarioId,
                                         double quantidade, String observacao, List<EfetivoAlocado> efetivo) {
        if (obraId <= 0) throw new ValidationException("Selecione uma obra válida.");
        CronogramaItem task = listarCronograma(obraId).stream().filter(item -> item.id() == tarefaId).findFirst()
                .orElseThrow(() -> new ValidationException("Selecione uma tarefa válida desta obra."));
        DiarioObra diario = requireDiario(obraId, diarioId);
        if (!Double.isFinite(quantidade) || quantidade < 0) throw new ValidationException("Informe uma quantidade executada válida.");
        if (efetivo == null || efetivo.isEmpty()) throw new ValidationException("Vincule ao menos uma função do efetivo do RDO.");
        Map<String, Integer> capacity = parseEfetivo(diario.efetivo());
        Set<String> usedRoles = new HashSet<>();
        List<ProducaoTarefa> existing = listarProducao(obraId);
        if (id != null && existing.stream().noneMatch(row -> id.equals(row.id())))
            throw new ValidationException("Lançamento de produção não encontrado nesta obra.");
        if (id == null && existing.stream().anyMatch(row -> row.cronogramaItemId() == tarefaId && row.diarioObraId() == diarioId))
            throw new ValidationException("Já existe produção desta tarefa para este RDO. Edite o apontamento existente.");
        for (EfetivoAlocado allocation : efetivo) {
            if (allocation.funcao() == null || allocation.funcao().isBlank() || allocation.pessoas() <= 0
                    || !Double.isFinite(allocation.horasPorPessoa()) || allocation.horasPorPessoa() <= 0 || allocation.horasPorPessoa() > 24)
                throw new ValidationException("Revise função, número de pessoas e horas por pessoa.");
            String key = allocation.funcao().trim().toLowerCase(java.util.Locale.ROOT);
            if (!usedRoles.add(key)) throw new ValidationException("A função " + allocation.funcao() + " foi adicionada duas vezes.");
            String sourceRole = capacity.keySet().stream().filter(role -> role.equalsIgnoreCase(allocation.funcao().trim()))
                    .findFirst().orElseThrow(() -> new ValidationException("A função " + allocation.funcao() + " não consta no RDO escolhido."));
            if (allocation.pessoas() > capacity.get(sourceRole))
                throw new ValidationException("O RDO registra apenas " + capacity.get(sourceRole) + " pessoa(s) na função " + sourceRole + ".");
            double alreadyAllocated = existing.stream().filter(row -> row.diarioObraId() == diarioId && !java.util.Objects.equals(row.id(), id))
                    .flatMap(row -> row.efetivo().stream()).filter(row -> row.funcao().equalsIgnoreCase(sourceRole))
                    .mapToDouble(EfetivoAlocado::horasHomem).sum();
            double availableHours = capacity.get(sourceRole) * 8d - alreadyAllocated;
            if (allocation.horasHomem() > availableHours + 0.000001)
                throw new ValidationException("Efetivo insuficiente no RDO para " + sourceRole + ". Restam "
                        + Math.max(0, availableHours) + " homem-horas (jornada de referência: 8 h/pessoa)." );
        }
        ProducaoTarefa production = new ProducaoTarefa(id, obraId, task.id(), diario.id(), diario.data(),
                quantidade, observacao == null ? null : observacao.trim(), efetivo);
        long savedId = repository.salvarProducao(production);
        return listarProducao(obraId).stream().filter(row -> row.id() == savedId).findFirst().orElseThrow();
    }

    public void excluirProducao(long obraId, long id) { repository.excluirProducao(obraId, id); }

    public static double progressoFisico(CronogramaItem task, List<ProducaoTarefa> production) {
        double done = production.stream().filter(row -> row.cronogramaItemId() == task.id()).mapToDouble(ProducaoTarefa::quantidadeExecutada).sum();
        return done > 0 && metaFisicaInformada(task) ? Math.min(100, done * 100d / task.quantidade()) : task.percentualExecutado();
    }

    public static boolean metaFisicaInformada(CronogramaItem task) {
        return task.quantidade() > 0 && task.metaFisicaDefinida();
    }

    private DiarioObra requireDiario(long obraId, long diarioId) {
        if (diarioObraService == null) throw new ValidationException("O Diário de Obra não está disponível para o cronograma.");
        return diarioObraService.listar(obraId).stream().filter(value -> value.id() == diarioId).findFirst()
                .orElseThrow(() -> new ValidationException("Selecione um RDO da obra para vincular o efetivo."));
    }

    public void importarPropostaRioClaro(long obraId) {
        if (obraId <= 0) throw new ValidationException("Selecione uma obra válida.");
        repository.importarProposta(obraId, CronogramaPropostaRioClaro.itens(obraId));
    }

    public void salvarExecucao(long obraId, long itemId, int mes, double valor) {
        if (obraId <= 0 || itemId <= 0) throw new ValidationException("Selecione um serviço da obra.");
        if (mes < 1 || mes > CronogramaItem.MESES) throw new ValidationException("Selecione um mês entre 1 e 24.");
        if (!Double.isFinite(valor) || valor < 0) throw new ValidationException("Informe um valor executado válido.");
        repository.salvarExecucao(obraId, itemId, mes, valor);
    }

    public CronogramaItem salvarCronograma(long obraId, Long id, int ordem, String codigo, String descricao, String unidade,
                                           double quantidade, double peso, LocalDate inicio, LocalDate fim, double executado, String observacao) {
        if (obraId <= 0) throw new ValidationException("Selecione uma obra válida.");
        requireText(descricao, "Informe a descrição da etapa.");
        requireText(unidade, "Informe a unidade da etapa.");
        if (quantidade < 0) throw new ValidationException("A quantidade da etapa não pode ser negativa.");
        if (peso < 0 || peso > 100) throw new ValidationException("O peso deve estar entre 0 e 100%.");
        if (executado < 0 || executado > 100) throw new ValidationException("O avanço deve estar entre 0 e 100%.");
        if (inicio != null && fim != null && fim.isBefore(inicio)) throw new ValidationException("O fim previsto não pode ser anterior ao início.");
        Instant now = Instant.now();
        CronogramaItem item = new CronogramaItem(id, obraId, Math.max(0, ordem), emptyToNull(codigo), descricao.trim(), unidade.trim(),
                quantidade, peso, inicio, fim, executado, emptyToNull(observacao), true, now, now, List.of());
        long savedId = id == null ? repository.inserirCronograma(item) : id;
        if (id != null) repository.atualizarCronograma(item);
        return repository.listarCronograma(obraId).stream().filter(value -> savedId == value.id()).findFirst().orElse(item);
    }

    public CronogramaItem salvarCronograma(long obraId, Long id, int ordem, String codigo, String descricao, String unidade,
                                           double quantidade, double peso, LocalDate inicio, LocalDate fim, double executado, String observacao,
                                           List<Double> valoresMensais) {
        if (obraId <= 0) throw new ValidationException("Selecione uma obra válida.");
        requireText(descricao, "Informe a descrição da etapa.");
        requireText(unidade, "Informe a unidade da etapa.");
        if (quantidade < 0) throw new ValidationException("A quantidade da etapa não pode ser negativa.");
        if (peso < 0 || peso > 100) throw new ValidationException("O peso deve estar entre 0 e 100%.");
        if (executado < 0 || executado > 100) throw new ValidationException("O avanço deve estar entre 0 e 100%.");
        if (inicio != null && fim != null && fim.isBefore(inicio)) throw new ValidationException("O fim previsto não pode ser anterior ao início.");
        List<Double> monthly = valoresMensais == null ? List.of() : valoresMensais;
        if (monthly.stream().anyMatch(value -> value == null || !Double.isFinite(value) || value < 0)) throw new ValidationException("Os valores mensais devem ser números positivos.");
        Instant now = Instant.now();
        CronogramaItem item = new CronogramaItem(id, obraId, Math.max(0, ordem), emptyToNull(codigo), descricao.trim(), unidade.trim(),
                quantidade, peso, inicio, fim, executado, emptyToNull(observacao), true, now, now, monthly);
        long savedId = id == null ? repository.inserirCronograma(item) : id;
        if (id != null) repository.atualizarCronograma(item);
        return repository.listarCronograma(obraId).stream().filter(value -> savedId == value.id()).findFirst().orElse(item);
    }

    public OrcamentoItem salvarOrcamento(long obraId, Long id, String codigo, String descricao, String unidade,
                                         double quantidade, double valorUnitario, String observacao) {
        if (obraId <= 0) throw new ValidationException("Selecione uma obra válida.");
        requireText(descricao, "Informe a descrição do item do orçamento.");
        requireText(unidade, "Informe a unidade do item do orçamento.");
        if (quantidade < 0 || valorUnitario < 0) throw new ValidationException("Quantidade e valor unitário não podem ser negativos.");
        Instant now = Instant.now();
        OrcamentoItem item = new OrcamentoItem(id, obraId, emptyToNull(codigo), descricao.trim(), unidade.trim(), quantidade, valorUnitario,
                emptyToNull(observacao), true, now, now);
        if (id == null) repository.inserirOrcamento(item); else repository.atualizarOrcamento(item);
        return item;
    }

    public Medicao salvarMedicao(long obraId, Long id, Long cronogramaItemId, Long orcamentoItemId, String numero, LocalDate data,
                                 String descricao, double quantidade, double percentualFisico, double valorMedido, String observacao) {
        if (obraId <= 0) throw new ValidationException("Selecione uma obra válida.");
        if (data == null) throw new ValidationException("Informe a data da medição.");
        requireText(descricao, "Informe a descrição da medição.");
        if (quantidade < 0 || valorMedido < 0) throw new ValidationException("Quantidade e valor medido não podem ser negativos.");
        if (percentualFisico < 0 || percentualFisico > 100) throw new ValidationException("O percentual físico deve estar entre 0 e 100%.");
        Instant now = Instant.now();
        Medicao medicao = new Medicao(id, obraId, cronogramaItemId, orcamentoItemId, emptyToNull(numero), data, descricao.trim(),
                quantidade, percentualFisico, valorMedido, emptyToNull(observacao), now, now);
        Long previousScheduleId = id == null ? null : repository.listarMedicoes(obraId).stream()
                .filter(value -> id.equals(value.id())).map(Medicao::cronogramaItemId).findFirst().orElse(null);
        if (id == null) repository.inserirMedicao(medicao); else repository.atualizarMedicao(medicao);
        atualizarAvancoMedido(obraId, previousScheduleId);
        atualizarAvancoMedido(obraId, cronogramaItemId);
        return medicao;
    }

    public PlanejamentoResumo resumo(long obraId, LocalDate dataBase) {
        return resumo(obraId, dataBase, null);
    }

    public PlanejamentoResumo resumo(long obraId, LocalDate dataBase, LocalDate inicioObra) {
        List<CronogramaItem> schedule = listarCronograma(obraId);
        List<ProducaoTarefa> production = listarProducao(obraId);
        double totalMoney = schedule.stream().mapToDouble(CronogramaItem::totalPrevisto).sum();
        double totalWeight = schedule.stream().mapToDouble(CronogramaItem::pesoPercentual).sum();
        double planned = 0;
        double actual = 0;
        for (CronogramaItem item : schedule) {
            double weight = totalMoney > 0 ? item.totalPrevisto() / totalMoney
                    : totalWeight > 0 ? item.pesoPercentual() / totalWeight : 1d / Math.max(1, schedule.size());
            planned += (totalMoney > 0 && inicioObra != null
                    ? plannedMonthlyPercent(item, inicioObra, dataBase)
                    : plannedPercent(item, dataBase)) * weight;
            actual += progressoFisico(item, production) * weight;
        }
        double budget = listarOrcamento(obraId).stream().mapToDouble(OrcamentoItem::total).sum();
        double measured = listarMedicoes(obraId).stream().mapToDouble(Medicao::valorMedido).sum();
        return new PlanejamentoResumo(planned, actual, budget, measured);
    }

    public PlanejamentoResumo resumoTarefas(long obraId, LocalDate dataBase, LocalDate inicioObra) {
        List<CronogramaItem> tasks = listarCronograma(obraId);
        List<ProducaoTarefa> production = listarProducao(obraId);
        double total = tasks.stream().mapToDouble(CronogramaItem::totalPrevisto).sum();
        double planned = 0, actual = 0;
        for (CronogramaItem task : tasks) {
            double weight = total > 0 ? task.totalPrevisto() / total : 1d / Math.max(1, tasks.size());
            LocalDate start = inicioTarefa(task, inicioObra);
            LocalDate finish = fimTarefa(task, inicioObra);
            planned += plannedPercent(start, finish, dataBase) * weight;
            actual += progressoFisico(task, production) * weight;
        }
        return new PlanejamentoResumo(planned, actual,
                listarOrcamento(obraId).stream().mapToDouble(OrcamentoItem::total).sum(),
                listarMedicoes(obraId).stream().mapToDouble(Medicao::valorMedido).sum());
    }

    public static LocalDate inicioTarefa(CronogramaItem item, LocalDate inicioObra) {
        if (item.inicioPrevisto() != null) return item.inicioPrevisto();
        if (item.totalReferencia() == null || inicioObra == null) return null;
        for (int index = 0; index < CronogramaItem.MESES; index++) {
            if (item.valorMensal(index + 1) > 0) return inicioObra.withDayOfMonth(1).plusMonths(index);
        }
        return null;
    }

    public static LocalDate fimTarefa(CronogramaItem item, LocalDate inicioObra) {
        if (item.fimPrevisto() != null) return item.fimPrevisto();
        if (item.totalReferencia() == null || inicioObra == null) return null;
        for (int index = CronogramaItem.MESES - 1; index >= 0; index--) {
            if (item.valorMensal(index + 1) > 0) return inicioObra.withDayOfMonth(1).plusMonths(index + 1).minusDays(1);
        }
        return null;
    }

    public static double plannedPercent(LocalDate start, LocalDate finish, LocalDate date) {
        if (start == null || finish == null || date == null) return 0;
        if (date.isBefore(start)) return 0;
        if (!date.isBefore(finish)) return 100;
        long total = Math.max(1, ChronoUnit.DAYS.between(start, finish) + 1);
        long elapsed = ChronoUnit.DAYS.between(start, date) + 1;
        return Math.min(100, elapsed * 100d / total);
    }

    public static double plannedMonthlyPercent(CronogramaItem item, LocalDate inicioObra, LocalDate dataBase) {
        if (item.totalPrevisto() <= 0 || inicioObra == null || dataBase == null || dataBase.isBefore(inicioObra)) return 0;
        long elapsedMonths = ChronoUnit.MONTHS.between(
                inicioObra.withDayOfMonth(1), dataBase.withDayOfMonth(1));
        int monthsToInclude = (int) Math.max(0, Math.min(CronogramaItem.MESES, elapsedMonths + 1));
        double planned = 0;
        for (int month = 1; month <= monthsToInclude; month++) planned += item.valorMensal(month);
        return Math.min(100, planned * 100d / item.totalPrevisto());
    }

    public static double plannedPercent(CronogramaItem item, LocalDate date) {
        if (item.inicioPrevisto() == null || item.fimPrevisto() == null || date == null) return 0;
        if (!date.isAfter(item.inicioPrevisto())) return 0;
        if (!date.isBefore(item.fimPrevisto())) return 100;
        long total = Math.max(1, ChronoUnit.DAYS.between(item.inicioPrevisto(), item.fimPrevisto()));
        long elapsed = ChronoUnit.DAYS.between(item.inicioPrevisto(), date);
        return Math.max(0, Math.min(100, elapsed * 100d / total));
    }

    private void atualizarAvancoMedido(long obraId, Long cronogramaItemId) {
        if (cronogramaItemId == null) return;
        CronogramaItem item = listarCronograma(obraId).stream().filter(value -> cronogramaItemId.equals(value.id())).findFirst().orElse(null);
        if (item == null || item.quantidade() <= 0) return;
        double measuredQuantity = listarMedicoes(obraId).stream()
                .filter(value -> cronogramaItemId.equals(value.cronogramaItemId()))
                .mapToDouble(Medicao::quantidade).sum();
        double progress = Math.min(100, measuredQuantity * 100d / item.quantidade());
        CronogramaItem updated = new CronogramaItem(item.id(), item.obraId(), item.ordem(), item.codigo(), item.descricao(), item.unidade(),
                item.quantidade(), item.pesoPercentual(), item.inicioPrevisto(), item.fimPrevisto(), progress, item.observacao(),
                item.ativo(), item.createdAt(), Instant.now(), item.valoresMensais(), item.valoresExecutados(),
                item.totalReferencia(), item.percentualReferencia(), item.metaFisicaDefinida());
        repository.atualizarCronograma(updated);
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new ValidationException(message);
    }

    private String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
