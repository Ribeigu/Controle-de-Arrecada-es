package br.edu.api.model;

public enum UnidadeMedida {
    KG("kg"), UNIDADE("unidade");

    private final String descricao;

    UnidadeMedida(String descricao) { this.descricao = descricao; }

    @Override
    public String toString() { return descricao; }
}
