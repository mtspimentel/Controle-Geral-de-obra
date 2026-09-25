package br.com.pimentech.controlemateriais.service;

import br.com.pimentech.controlemateriais.model.CronogramaItem;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class CronogramaPropostaRioClaro {
    public static final double TOTAL = 14_369_168.31;
    private static final double[] TOTAIS_MENSAIS = {
            361636.44, 235760.56, 299077.35, 312547.09, 306654.38, 910139.14,
            844570.52, 712663.32, 792198.09, 921919.61, 801162.63, 811939.72,
            824518.94, 812017.72, 765609.40, 702740.81, 700582.58, 699184.97,
            677022.93, 656049.13, 445346.13, 275918.24, 251475.67, 248432.93
    };
    private static final double[] ACUMULADOS = {
            361636.44, 597397.00, 896474.35, 1209021.44, 1515675.82, 2425814.96,
            3270385.48, 3983048.80, 4775246.89, 5697166.50, 6498329.13, 7310268.85,
            8134787.79, 8946805.51, 9712414.92, 10415155.73, 11115738.30, 11814923.27,
            12491946.20, 13147995.33, 13593341.46, 13869259.70, 14120735.38, 14369168.31
    };

    private CronogramaPropostaRioClaro() { }

    public static double totalMensal(int mes) { return TOTAIS_MENSAIS[mes - 1]; }
    public static double acumulado(int mes) { return ACUMULADOS[mes - 1]; }

    public static List<CronogramaItem> itens(long obraId) {
        InputStream input = CronogramaPropostaRioClaro.class.getResourceAsStream("/cronograma/cronograma-rio-claro-7pct.csv");
        if (input == null) throw new IllegalStateException("A proposta do cronograma não foi incluída na aplicação.");
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            reader.readLine();
            List<CronogramaItem> itens = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                String[] cells = line.split(";", -1);
                if (cells.length != 28) throw new IllegalStateException("Linha inválida na proposta: " + line);
                List<Double> months = new ArrayList<>();
                for (int month = 2; month < 26; month++) months.add(Double.parseDouble(cells[month]));
                int order = itens.size() + 1;
                itens.add(new CronogramaItem(null, obraId, order, cells[0], cells[1], "UN", 1,
                        Double.parseDouble(cells[27]), null, null, 0, null, true,
                        Instant.now(), Instant.now(), months, List.of(), Double.parseDouble(cells[26]),
                        Double.parseDouble(cells[27])));
            }
            if (itens.size() != 27 || Math.abs(itens.stream().mapToDouble(CronogramaItem::totalPrevisto).sum() - TOTAL) > 0.001)
                throw new IllegalStateException("O total da proposta não confere com o PDF de referência.");
            return List.copyOf(itens);
        } catch (IOException exception) {
            throw new IllegalStateException("Não foi possível ler a proposta do cronograma.", exception);
        }
    }
}
