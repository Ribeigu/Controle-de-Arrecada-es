package br.edu.api.model;

public enum LimiteRepeticao {
    QUANTIDADE("Por quantidade"), DATA_LIMITE("Até uma data"), PARA_SEMPRE("Para sempre");

    private final String descricao;

    LimiteRepeticao(String descricao) { this.descricao = descricao; }

    @Override
    public String toString() { return descricao; }
}
