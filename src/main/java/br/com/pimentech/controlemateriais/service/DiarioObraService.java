package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.DiarioObra;
import br.com.pimentech.controlemateriais.repository.DiarioObraRepository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class DiarioObraService {

    private final DiarioObraRepository repository;

    public DiarioObraService(DiarioObraRepository repository) {
        this.repository = repository;
    }

    public List<DiarioObra> listar(long obraId) {
        return repository.findByObraId(obraId);
    }

    public DiarioObra salvar(long obraId, Long id, LocalDate data, String atividades, String efetivo,
                             String equipamentos, String clima, String observacoes, String intercorrencias) {
        if (obraId <= 0) throw new ValidationException("Selecione uma obra válida.");
        if (data == null) throw new ValidationException("Informe a data do diário.");
        requireText(atividades, "Descreva as atividades realizadas.");

        DiarioObra sameDay = repository.findByObraIdAndData(obraId, data.toString()).orElse(null);
        if (id == null && sameDay != null) {
            throw new ValidationException("Já existe um RDO para esta data. Informe outra data ou selecione o registro para editar.");
        }
        if (id != null && sameDay != null && !id.equals(sameDay.id())) {
            throw new ValidationException("Já existe um diário registrado para esta data.");
        }
        Instant now = Instant.now();
        DiarioObra diario = new DiarioObra(id != null ? id : sameDay == null ? null : sameDay.id(), obraId, data,
                atividades.trim(), empty(efetivo), empty(equipamentos), empty(clima), empty(observacoes), empty(intercorrencias),
                sameDay == null ? now : sameDay.createdAt(), now);
        if (diario.id() == null) {
            repository.insert(diario);
            return repository.findByObraIdAndData(obraId, data.toString()).orElseThrow(
                    () -> new ValidationException("O diário salvo não foi encontrado."));
        }
        repository.update(diario);
        return repository.findByObraIdAndData(obraId, data.toString()).orElseThrow(
                () -> new ValidationException("O diário atualizado não foi encontrado."));
    }

    private void requireText(String value, String message) {
        if (value == null || value.isBlank()) throw new ValidationException(message);
    }

    private String empty(String value) {
        return value == null ? "" : value.trim();
    }
}
