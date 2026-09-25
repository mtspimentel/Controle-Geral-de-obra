package br.com.pimentech.controlemateriais.dto;

import java.util.List;

public record DashboardSnapshot(
        int pedidosEmAndamento,
        int pedidosAtrasados,
        int entregasParciais,
        int pedidosComPendencia,
        int materiaisEmTroca,
        int pedidosConcluidos,
        List<String> alertas,
        List<ProximaEntrega> proximasEntregas
) {
    public record ProximaEntrega(String pedido, String fornecedor, String previsao, String status) {
    }
}
