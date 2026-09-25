package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.exception.ValidationException;
import br.com.pimentech.controlemateriais.model.Pedido;
import br.com.pimentech.controlemateriais.model.Requisicao;
import br.com.pimentech.controlemateriais.util.DateFormats;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;

public final class FolhaPedidoService {

    private static final int BLANK_ROWS = 19;
    private final Path exportsDirectory;

    public FolhaPedidoService(Path rootDirectory) {
        this.exportsDirectory = rootDirectory.resolve("exports");
    }

    public Path gerar(Requisicao requisicao) {
        if (requisicao == null) throw new ValidationException("Selecione uma requisição para gerar a folha");
        List<SheetItem> items = requisicao.itens().stream()
                .map(item -> new SheetItem(item.materialCodigo(), item.materialDescricao(), item.unidade(), item.quantidade(), null))
                .toList();
        return write("folha_requisicao_" + safeFilePart(requisicao.numero()) + ".html",
                requisicao.numero(), "A preencher pelo almoxarifado", requisicao.obraNome(), requisicao.solicitante(),
                "PRIORIDADE", requisicao.prioridade().name(), requisicao.dataRequisicao(), items, requisicao.observacao());
    }

    public Path gerar(Pedido pedido) {
        if (pedido == null) throw new ValidationException("Selecione um pedido para gerar a folha");
        List<SheetItem> items = pedido.itens().stream()
                .map(item -> new SheetItem(item.materialCodigo(), item.materialDescricao(), item.unidade(), item.quantidadeSolicitada(), item.quantidadeRecebida()))
                .toList();
        return write("folha_" + safeFilePart(pedido.numero()) + ".html",
                pedido.requisicaoId() == null ? pedido.numero() : "REQ-" + pedido.requisicaoId(),
                pedido.numero(), pedido.obraNome(), "Almoxarifado", "FORNECEDOR",
                pedido.fornecedorNome() == null ? "A definir" : pedido.fornecedorNome(),
                pedido.dataPedido(), items, pedido.observacao());
    }

    private Path write(String fileName, String protocol, String orderNumber, String sector, String requester,
                       String extraLabel, String extraValue, java.time.LocalDate date, List<SheetItem> items,
                       String observation) {
        try {
            Files.createDirectories(exportsDirectory);
            Path file = exportsDirectory.resolve(fileName);
            StringBuilder html = new StringBuilder();
            html.append("""
                    <!doctype html>
                    <html lang="pt-BR"><head><meta charset="UTF-8">
                    <title>Solicitação de fornecimento - %s</title>
                    <style>
                    @page{size:A4 portrait;margin:9mm}
                    *{box-sizing:border-box}
                    body{margin:0;background:#fff;color:#111;font-family:Arial,Helvetica,sans-serif;font-size:9px}
                    .sheet{width:100%%;border:1px solid #222}
                    table{width:100%%;border-collapse:collapse;table-layout:fixed}
                    td,th{border:1px solid #222;padding:4px;text-align:center;vertical-align:middle}
                    .brand{font-size:18px;font-weight:800;letter-spacing:-1px;line-height:1.05;text-align:left;padding:12px 10px;width:29%%}
                    .brand small{display:block;font-size:7px;letter-spacing:1px;margin-top:5px}
                    .label{display:block;font-size:7px;font-weight:700;margin-bottom:4px}
                    .value{font-size:11px;min-height:13px;display:block}
                    .left{text-align:left}.title{font-weight:700;font-size:10px;padding:7px}
                    .items th{font-size:8px;height:28px}.items td{height:27px;font-size:9px}
                    .items .item{width:6%%}.items .code{width:18%%}.items .description{width:39%%}
                    .items .unit{width:10%%}.items .requested{width:13%%}.items .supplied{width:14%%}
                    .obs-label{text-align:left;font-weight:700;width:12%%}.obs{height:25px;text-align:left}
                    .yellow{height:14px;background:#fff500}
                    .signatures{margin-top:18px}.signatures td{height:43px}.signature-space{height:33px}
                    .signature-label{font-weight:700;font-size:8px}.line{border-top:1px solid #222;margin:12px 12px 0;padding-top:3px;font-size:8px}
                    .muted{font-size:8px}
                    </style></head><body>
                    <div class="sheet">
                    <table class="header">
                    <tr><td class="brand" rowspan="2">INTEGRAL<br>CONSTRUTORA<small>CONTROLE DE MATERIAIS</small></td>
                    <td><span class="label">Nº PROTOCOLO (PREENCHIMENTO DO SETOR)</span><span class="value">%s</span></td>
                    <td><span class="label">Nº PEDIDO (CMG) (USO DO ALMOXARIFADO)</span><span class="value">%s</span></td></tr>
                    <tr><td colspan="2"><span class="label">SETOR / OBRA REQUISITANTE</span><span class="value">%s</span></td></tr>
                    <tr><td><span class="label">SOLICITANTE</span><span class="value">%s</span></td>
                    <td><span class="label">%s</span><span class="value">%s</span></td>
                    <td><span class="label">DATA</span><span class="value">%s</span></td></tr>
                    </table>
                    <table><tr><td class="title">SOLICITO FORNECIMENTO DO(S) SEGUINTE(S) MATERIAL(IS)</td></tr></table>
                    <table class="items"><thead><tr><th class="item">ITEM</th><th class="code">CÓDIGO DO MATERIAL</th><th class="description">DESCRIÇÃO</th><th class="unit">UNIDADE</th><th class="requested">QUANT. SOLICITADA</th><th class="supplied">QUANT. FORNECIDA</th></tr></thead><tbody>
                    """.formatted(
                    escape(protocol), escape(protocol), escape(orderNumber), escape(sector), escape(requester),
                    escape(extraLabel), escape(extraValue), DateFormats.format(date)));

            for (int index = 0; index < BLANK_ROWS; index++) {
                SheetItem item = index < items.size() ? items.get(index) : null;
                html.append("<tr><td>").append(index + 1).append("</td><td>")
                        .append(item == null ? "" : escape(valueOrDash(item.code())))
                        .append("</td><td class='left'>")
                        .append(item == null ? "" : escape(item.description()))
                        .append("</td><td>")
                        .append(item == null ? "" : escape(item.unit()))
                        .append("</td><td>")
                        .append(item == null ? "" : number(item.requested()))
                        .append("</td><td>")
                        .append(item == null || item.supplied() == null ? "" : number(item.supplied()))
                        .append("</td></tr>");
            }
            html.append("</tbody></table>");
            html.append("<table><tr><td class='obs-label'>OBS:</td><td class='obs' colspan='5'>")
                    .append(observation == null ? "" : escape(observation)).append("</td></tr>")
                    .append("<tr><td class='yellow' colspan='6'>&nbsp;</td></tr></table>");
            html.append("""
                    <table class="signatures">
                    <tr><td colspan="3" class="signature-label">SOLICITANTE</td><td colspan="3" class="signature-label">DESPACHADO POR<br><span class="muted">(PREENCHIMENTO DO ALMOXARIFADO)</span></td></tr>
                    <tr><td colspan="3"><div class="signature-space"></div><div class="line">DATA / ASS./CARIMBO</div></td><td colspan="3"><div class="signature-space"></div><div class="line">DATA / ASS./CARIMBO</div></td></tr>
                    <tr><td colspan="3" class="signature-label">AUTORIZADO POR: Engenheiro responsável</td><td colspan="3" class="signature-label">RECEBIDO POR:</td></tr>
                    <tr><td colspan="3"><div class="signature-space"></div><div class="line">DATA / ASS./CARIMBO</div></td><td colspan="3"><div class="signature-space"></div><div class="line">DATA / ASS./CARIMBO</div></td></tr>
                    </table></div></body></html>
                    """);
            Files.writeString(file, html.toString(), StandardCharsets.UTF_8);
            return file;
        } catch (IOException exception) {
            throw new ValidationException("Não foi possível gerar a folha: " + exception.getMessage());
        }
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String number(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%.2f", value);
    }

    private String safeFilePart(String value) {
        return value == null || value.isBlank() ? "documento" : value.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    private String escape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private record SheetItem(String code, String description, String unit, double requested, Double supplied) {
    }
}
