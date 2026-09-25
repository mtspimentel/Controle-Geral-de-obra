package br.com.pimentech.controlemateriais.model;

public record EfetivoAlocado(String funcao, int pessoas, double horasPorPessoa) {
    public double horasHomem() { return pessoas * horasPorPessoa; }
}
