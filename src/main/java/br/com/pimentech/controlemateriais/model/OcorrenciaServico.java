package br.com.pimentech.controlemateriais.model;

import java.time.LocalDate;

public record OcorrenciaServico(Long id, long obraId, long servicoId, LocalDate data,
                               TipoOcorrenciaServico tipo, String descricao, Long materialId,
                               LocalDate resolvidaEm) {
    public boolean abertaEm(LocalDate dia) {
        return !data.isAfter(dia) && (resolvidaEm == null || resolvidaEm.isAfter(dia));
    }
}
