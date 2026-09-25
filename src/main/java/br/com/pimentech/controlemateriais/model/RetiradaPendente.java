package br.com.pimentech.controlemateriais.model;

public record RetiradaPendente(
        String referencia,
        long materialId,
        String codigoMaterial,
        String descricaoMaterial,
        String unidade,
        double quantidade,
        String retirante,
        String servico
) {
    @Override
    public String toString() {
        return codigoMaterial + " - " + descricaoMaterial + " | " + quantidade + " " + unidade
                + " | " + retirante + " | " + servico;
    }
}
