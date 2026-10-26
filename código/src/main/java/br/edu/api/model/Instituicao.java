package br.edu.api.model;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** Regras da instituição usadas nos registros financeiros e nas doações físicas. */
public final class Instituicao {
    private final Clock relogio;
    private static final DateTimeFormatter DATA_BRASILEIRA = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    public Instituicao(Clock relogio) {
        this.relogio = relogio;
    }

    public LocalDate hoje() { return LocalDate.now(relogio); }
    public LocalDate inicioJanelaTempo() { return hoje().minusMonths(2); }
    public LocalDate fimJanelaTempo() { return hoje().plusYears(5); }

    public boolean verificarJanelaTempo(LocalDate data) {
        return data != null && !data.isBefore(inicioJanelaTempo()) && !data.isAfter(fimJanelaTempo());
    }

    public Despesa informarDespesa(BigDecimal valor, String nome, LocalDate data) {
        Despesa despesa = Despesa.criar(valor, nome, data);
        validarJanelaTempo(data);
        return despesa;
    }

    public Arrecadacao informarArrecadacao(BigDecimal valor, String nome, LocalDate data, String nomeDoador) {
        Arrecadacao arrecadacao = Arrecadacao.criar(valor, nome, data, nomeDoador);
        validarJanelaTempo(data);
        return arrecadacao;
    }

    public DoacaoDeProduto informarDoacaoProduto(String nome, UnidadeMedida unidade, BigDecimal quantidade,
                                                 LocalDate data, String nomeDoador) {
        DoacaoDeProduto doacao = DoacaoDeProduto.criar(nome, unidade, quantidade, data, nomeDoador);
        validarJanelaTempo(data);
        return doacao;
    }

    public Despesa alterarDespesa(Despesa original, String nome, BigDecimal valor, LocalDate data) {
        validarJanelaTempo(original.getData());
        Despesa alterada = original.registrarAlteracao(nome, valor, data);
        validarJanelaTempo(data);
        return alterada;
    }

    public Arrecadacao alterarArrecadacao(Arrecadacao original, String nome, BigDecimal valor,
                                         LocalDate data, String nomeDoador) {
        validarJanelaTempo(original.getData());
        Arrecadacao alterada = original.registrarAlteracao(nome, valor, data, nomeDoador);
        validarJanelaTempo(data);
        return alterada;
    }

    private void validarJanelaTempo(LocalDate data) {
        if (!verificarJanelaTempo(data)) {
            throw new RegraDeNegocioException("A data deve estar entre "
                    + inicioJanelaTempo().format(DATA_BRASILEIRA) + " e "
                    + fimJanelaTempo().format(DATA_BRASILEIRA) + ".");
        }
    }
}
