package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.Pedido;
import br.com.pimentech.controlemateriais.util.DateFormats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class FolhaPedidoService {

    private final Path exportsDirectory;

    public FolhaPedidoService(Path rootDirectory) {
        this.exportsDirectory = rootDirectory.resolve("exports");
    }

    public Path gerar(Pedido pedido) {
        if (pedido == null) throw new ValidationException("Selecione um pedido para gerar a folha");
        try {
            Files.createDirectories(exportsDirectory);
            Path file = exportsDirectory.resolve("folha_" + pedido.numero() + ".html");
            StringBuilder html = new StringBuilder("""
                    <!doctype html><html lang="pt-BR"><head><meta charset="UTF-8">
                    <title>Folha de pedido</title><style>
                    body{font-family:Arial,sans-serif;margin:35px;color:#172235}h1{margin-bottom:4px}
                    .meta{margin:18px 0;padding:12px;background:#f2f4f7}table{width:100%;border-collapse:collapse}
                    th,td{border:1px solid #b9c2cf;padding:9px;text-align:left}th{background:#e9edf2}
                    .sign{margin-top:70px;display:flex;justify-content:space-around}.line{border-top:1px solid #172235;width:260px;text-align:center;padding-top:8px}
                    </style></head><body>
                    """);
            html.append("<h1>FOLHA DE PEDIDO DE MATERIAL</h1>");
            html.append("<div class='meta'><b>Pedido:</b> ").append(escape(pedido.numero()))
                    .append("<br><b>Obra:</b> ").append(escape(pedido.obraNome()))
                    .append("<br><b>Data:</b> ").append(DateFormats.format(pedido.dataPedido()))
                    .append("<br><b>Previsão:</b> ").append(pedido.dataPrevistaEntrega() == null ? "A definir" : DateFormats.format(pedido.dataPrevistaEntrega()))
                    .append("</div><table><tr><th>Material</th><th>Unidade</th><th>Quantidade</th><th>Observação</th></tr>");
            pedido.itens().forEach(item -> html.append("<tr><td>").append(escape(item.materialDescricao())).append("</td><td>")
                    .append(escape(item.unidade())).append("</td><td>").append(item.quantidadeSolicitada())
                    .append("</td><td></td></tr>"));
            html.append("</table><p><b>Observações:</b> ").append(pedido.observacao() == null ? "" : escape(pedido.observacao()))
                    .append("</p><div class='sign'><div class='line'>Solicitante</div><div class='line'>Engenheiro</div></div></body></html>");
            Files.writeString(file, html.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException exception) {
            throw new ValidationException("Não foi possível gerar a folha do pedido: " + exception.getMessage());
        }
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
