package br.edu.api.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

public abstract class Acao {
    private final String nome;
    private final BigDecimal valor;
    private final LocalDate data;
    private final Set<Tag> tags = new LinkedHashSet<>();

    protected Acao(String nome, BigDecimal valor, LocalDate data) {
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("Informe o nome do lançamento.");
        }
        if (valor == null || valor.signum() <= 0) {
            throw new RegraDeNegocioException("O valor deve ser maior que zero.");
        }
        if (data == null) {
            throw new RegraDeNegocioException("Informe a data do lançamento.");
        }
        try {
            this.valor = valor.setScale(2, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException erro) {
            throw new RegraDeNegocioException("O valor deve ter no máximo duas casas decimais.");
        }
        if (this.valor.precision() > 19) {
            throw new RegraDeNegocioException("O valor excede a capacidade de armazenamento monetário.");
        }
        this.nome = nome.strip();
        this.data = data;
    }

    public String getNome() { return nome; }
    public BigDecimal getValor() { return valor; }
    public LocalDate getData() { return data; }
    public Set<Tag> getTags() { return Set.copyOf(tags); }

    public void associarTag(Tag tag) {
        if (tag != null) {
            tags.add(tag);
        }
    }
}
