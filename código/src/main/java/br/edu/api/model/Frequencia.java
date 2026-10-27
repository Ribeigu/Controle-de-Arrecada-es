package br.edu.api.model;

import java.time.LocalDate;

public enum Frequencia {
    DIARIA("Diária"), SEMANAL("Semanal"), MENSAL("Mensal"), ANUAL("Anual");

    private final String descricao;

    Frequencia(String descricao) {
        this.descricao = descricao;
    }

    public LocalDate calcularData(LocalDate referencia, long numero) {
        // Calcular sempre a partir da referência evita transformar 31/jan em 28/mar.
        return switch (this) {
            case DIARIA -> referencia.plusDays(numero);
            case SEMANAL -> referencia.plusWeeks(numero);
            case MENSAL -> referencia.plusMonths(numero);
            case ANUAL -> referencia.plusYears(numero);
        };
    }

    @Override
    public String toString() { return descricao; }
}
