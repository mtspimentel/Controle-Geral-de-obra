package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.*;
import br.com.pimentech.controlemateriais.repository.FrentesRepository;

import java.time.LocalDate;
import java.util.*;

/** Regras de localização, disciplina e liberação manual por trecho. */
public final class FrentesService {
    private static final List<String> PADRAO = List.of("Fundação", "Estrutura", "Alvenaria", "Hidráulica",
            "Elétrica", "Gases Medicinais", "Revestimento", "Forro", "Pintura");
    private final FrentesRepository repository;
    private final PlanejamentoDiarioService diario;

    public FrentesService(FrentesRepository repository, PlanejamentoDiarioService diario) {
        this.repository = repository; this.diario = diario;
    }
    public List<DisciplinaObra> disciplinas(long obraId) {
        requireWork(obraId);
        repository.criarDisciplinasPadrao(obraId, PADRAO);
        return repository.disciplinas(obraId);
    }
    public DisciplinaObra criarDisciplina(long obraId, String nome) {
        requireWork(obraId); requireText(nome, "Informe a disciplina.");
        String cleaned = nome.trim();
        if (disciplinas(obraId).stream().anyMatch(d -> d.nome().equalsIgnoreCase(cleaned)))
            throw new ValidationException("Esta disciplina já existe na obra.");
        long id = repository.criarDisciplina(obraId, cleaned);
        return repository.disciplinas(obraId).stream().filter(d -> d.id() == id).findFirst().orElseThrow();
    }
    public ServicoArea salvarServico(long obraId, Long id, long areaId, long disciplinaId, String descricao,
                                     String unidade, double quantidade, LocalDate inicio, LocalDate fim, Double meta,
                                     String equipe, String observacoes, List<String> novosElementos) {
        requireWork(obraId);
        if (disciplinas(obraId).stream().noneMatch(d -> d.id() == disciplinaId))
            throw new ValidationException("Selecione uma disciplina desta obra.");
        if (novosElementos == null) throw new ValidationException("Informe os elementos do serviço.");
        List<String> codes = novosElementos.stream().map(String::trim).filter(s -> !s.isBlank()).toList();
        if (codes.size() != novosElementos.size() || codes.stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().count() != codes.size())
            throw new ValidationException("Os elementos devem ter identificações únicas e não vazias.");
        if (id != null) {
            ServicoArea old = diario.listarServicos(obraId).stream().filter(s -> s.id().equals(id)).findFirst()
                    .orElseThrow(() -> new ValidationException("Serviço não encontrado nesta obra."));
            boolean linked = repository.vinculos(obraId).stream().anyMatch(v -> v.origemServicoId() == id || v.destinoServicoId() == id);
            if (old.areaId() != areaId && (linked || diario.listarProducoes(obraId).stream().anyMatch(p -> p.servicoId() == id)))
                throw new ValidationException("A área não pode mudar após haver produção ou dependência registrada.");
            if (linked && !codes.isEmpty())
                throw new ValidationException("Cadastre os elementos antes de criar dependências. Para novo trecho, crie outro serviço.");
            Set<String> existing = new HashSet<>();
            for (ElementoServico e : diario.listarElementos(obraId)) if (e.servicoId() == id) existing.add(e.codigo().toLowerCase(Locale.ROOT));
            if (codes.stream().anyMatch(s -> existing.contains(s.toLowerCase(Locale.ROOT))))
                throw new ValidationException("Um dos elementos já está cadastrado neste serviço.");
        }
        ServicoArea saved = diario.salvarServico(obraId, id, areaId, descricao, unidade, quantidade, inicio, fim, meta);
        repository.atualizarDetalhesServico(obraId, saved.id(), disciplinaId, emptyToNull(equipe), emptyToNull(observacoes), codes);
        return diario.listarServicos(obraId).stream().filter(s -> s.id().equals(saved.id())).findFirst().orElseThrow();
    }
    public List<VinculoFrente> vinculos(long obraId) { return repository.vinculos(obraId); }
    public List<TrechoVinculo> trechos(long obraId) { return repository.trechos(obraId); }
    public List<LiberacaoTrecho> historico(long obraId) { return repository.liberacoes(obraId); }

    /** A escolha do destino não depende da área selecionada nos filtros de consulta. */
    public List<ServicoArea> destinosPossiveis(long obraId, long origemId) {
        requireService(obraId, origemId);
        return diario.listarServicos(obraId).stream().filter(s -> s.id() != origemId).toList();
    }

    /** Os elementos do destino definem o alcance da liberação; cada um começa bloqueado. */
    public List<String> trechosDisponiveis(long obraId, long origemId, long destinoId) {
        ServicoArea origem = requireService(obraId, origemId), destino = requireService(obraId, destinoId);
        if (origem.id().equals(destino.id())) throw new ValidationException("Escolha serviços distintos.");
        List<ElementoServico> all = diario.listarElementos(obraId);
        List<String> source = all.stream().filter(e -> e.servicoId() == origemId).map(ElementoServico::codigo).toList();
        List<String> target = all.stream().filter(e -> e.servicoId() == destinoId).map(ElementoServico::codigo).toList();
        if (!target.isEmpty()) return target;
        if (!source.isEmpty()) return source;
        return List.of("Área inteira do destino");
    }
    public VinculoFrente criarVinculo(long obraId, long origemId, long destinoId, List<String> codigos) {
        ServicoArea origem = requireService(obraId, origemId);
        requireService(obraId, destinoId);
        List<String> available = trechosDisponiveis(obraId, origemId, destinoId);
        if (available.isEmpty()) throw new ValidationException("Informe o local ou os elementos do serviço de destino.");
        if (codigos == null || codigos.isEmpty()) throw new ValidationException("Escolha os trechos abrangidos pelo vínculo.");
        if (codigos.stream().map(s -> s.toLowerCase(Locale.ROOT)).distinct().count() != codigos.size()
                || codigos.stream().anyMatch(s -> available.stream().noneMatch(a -> a.equalsIgnoreCase(s))))
            throw new ValidationException("Há trecho repetido ou não cadastrado nos serviços.");
        if (codigos.size() != available.size())
            throw new ValidationException("Inclua todos os trechos correspondentes. A liberação parcial é confirmada depois, por trecho.");
        List<VinculoFrente> existing = vinculos(obraId);
        if (existing.stream().anyMatch(v -> v.origemServicoId() == origemId && v.destinoServicoId() == destinoId))
            throw new ValidationException("Este vínculo já existe. Consulte seus trechos na aba Frentes.");
        if (hasPath(existing, destinoId, origemId, new HashSet<>()))
            throw new ValidationException("Este vínculo criaria uma dependência circular.");
        List<ElementoServico> elements = diario.listarElementos(obraId);
        List<TrechoVinculo> scopes = codigos.stream().map(code -> new TrechoVinculo(null, 0, code,
                findElement(elements, origemId, code), findElement(elements, destinoId, code))).toList();
        long id = repository.criarVinculo(new VinculoFrente(null, obraId, origemId, destinoId, origem.areaId()), scopes);
        return vinculos(obraId).stream().filter(v -> v.id() == id).findFirst().orElseThrow();
    }
    public LiberacaoTrecho registrarLiberacao(long obraId, long trechoId, LocalDate data, boolean liberar,
                                              String responsavel, String observacao) {
        requireWork(obraId); requireText(responsavel, "Informe quem confirmou a situação da frente.");
        requireText(observacao, "Informe a observação da liberação ou do bloqueio.");
        if (data == null) throw new ValidationException("Informe a data da confirmação.");
        if (trechos(obraId).stream().noneMatch(t -> t.id() == trechoId))
            throw new ValidationException("Trecho não pertence à obra ativa.");
        long id = repository.registrarLiberacao(new LiberacaoTrecho(null, trechoId, data, liberar,
                responsavel.trim(), observacao.trim()));
        return historico(obraId).stream().filter(h -> h.id() == id).findFirst().orElseThrow();
    }
    public List<ResumoVinculo> resumo(long obraId, LocalDate data) {
        if (data == null) throw new ValidationException("Selecione uma data.");
        List<TrechoVinculo> scopes = trechos(obraId);
        List<LiberacaoTrecho> events = historico(obraId);
        List<ResumoVinculo> result = new ArrayList<>();
        for (VinculoFrente link : vinculos(obraId)) {
            List<EstadoTrecho> states = scopes.stream().filter(s -> s.vinculoId() == link.id()).map(s -> {
                LiberacaoTrecho latest = events.stream().filter(e -> e.trechoId() == s.id() && !e.data().isAfter(data))
                        .max(Comparator.comparing(LiberacaoTrecho::data).thenComparing(LiberacaoTrecho::id)).orElse(null);
                return new EstadoTrecho(s, latest != null && latest.liberado(), latest);
            }).toList();
            result.add(new ResumoVinculo(link, estado(states), states));
        }
        return result;
    }
    public SituacaoFrente estadoEntrada(long obraId, long servicoId, LocalDate data) {
        List<EstadoTrecho> incoming = resumo(obraId, data).stream()
                .filter(r -> r.vinculo().destinoServicoId() == servicoId).flatMap(r -> r.trechos().stream()).toList();
        return incoming.isEmpty() ? null : estado(incoming);
    }
    private static SituacaoFrente estado(List<EstadoTrecho> states) {
        long released = states.stream().filter(EstadoTrecho::liberado).count();
        return released == 0 ? SituacaoFrente.BLOQUEADO : released == states.size()
                ? SituacaoFrente.LIBERADO : SituacaoFrente.PARCIAL;
    }
    private static Long findElement(List<ElementoServico> elements, long serviceId, String code) {
        return elements.stream().filter(e -> e.servicoId() == serviceId && e.codigo().equalsIgnoreCase(code))
                .map(ElementoServico::id).findFirst().orElse(null);
    }
    private static boolean hasPath(List<VinculoFrente> graph, long start, long target, Set<Long> seen) {
        if (start == target) return true;
        if (!seen.add(start)) return false;
        return graph.stream().filter(v -> v.origemServicoId() == start)
                .anyMatch(v -> hasPath(graph, v.destinoServicoId(), target, seen));
    }
    private ServicoArea requireService(long obraId, long id) {
        return diario.listarServicos(obraId).stream().filter(s -> s.id() == id).findFirst()
                .orElseThrow(() -> new ValidationException("Serviço não encontrado na obra ativa."));
    }
    private static void requireWork(long id) { if (id <= 0) throw new ValidationException("Selecione uma obra ativa."); }
    private static void requireText(String value, String message) { if (value == null || value.isBlank()) throw new ValidationException(message); }
    private static String emptyToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    public record EstadoTrecho(TrechoVinculo trecho, boolean liberado, LiberacaoTrecho ultimaConfirmacao) { }
    public record ResumoVinculo(VinculoFrente vinculo, SituacaoFrente situacao, List<EstadoTrecho> trechos) { }
}
