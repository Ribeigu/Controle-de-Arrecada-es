package br.edu.api.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/** Doação física: sua quantidade não representa um valor financeiro. */
public final class DoacaoDeProduto {
    private final UUID id;
    private final String nomeProduto;
    private final UnidadeMedida unidadeMedida;
    private final BigDecimal quantidade;
    private final LocalDate data;
    private final String nomeDoador;
    private LocalDate prazoValidade;
    private LocalDate prazoGarantia;

    public DoacaoDeProduto(UUID id, String nomeProduto, UnidadeMedida unidadeMedida,
                           BigDecimal quantidade, LocalDate data, String nomeDoador,
                           LocalDate prazoValidade, LocalDate prazoGarantia) {
        this.id = Objects.requireNonNull(id, "Identificador da doação");
        if (nomeProduto == null || nomeProduto.isBlank()) {
            throw new RegraDeNegocioException("Informe o nome do produto.");
        }
        if (unidadeMedida == null) {
            throw new RegraDeNegocioException("Selecione a unidade de medida do produto.");
        }
        if (quantidade == null || quantidade.signum() <= 0) {
            throw new RegraDeNegocioException("A quantidade deve ser maior que zero.");
        }
        if (data == null) {
            throw new RegraDeNegocioException("Informe a data da doação.");
        }
        try {
            this.quantidade = quantidade.setScale(6, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException erro) {
            throw new RegraDeNegocioException("A quantidade admite até seis casas decimais.");
        }
        if (this.quantidade.precision() > 19) {
            throw new RegraDeNegocioException("A quantidade excede a capacidade de armazenamento.");
        }
        this.nomeProduto = nomeProduto.strip();
        this.unidadeMedida = unidadeMedida;
        this.data = data;
        this.nomeDoador = nomeDoador == null || nomeDoador.isBlank() ? null : nomeDoador.strip();
        definirValidadeOuGarantia(prazoValidade, prazoGarantia);
    }

    public static DoacaoDeProduto criar(String nome, UnidadeMedida unidade, BigDecimal quantidade,
                                        LocalDate data, String nomeDoador) {
        return new DoacaoDeProduto(UUID.randomUUID(), nome, unidade, quantidade, data, nomeDoador, null, null);
    }

    /** Os prazos são independentes e opcionais, conforme os atributos recebidos. */
    public void definirValidadeOuGarantia(LocalDate prazoValidade, LocalDate prazoGarantia) {
        this.prazoValidade = prazoValidade;
        this.prazoGarantia = prazoGarantia;
    }

    public UUID getId() { return id; }
    public String getNomeProduto() { return nomeProduto; }
    public UnidadeMedida getUnidadeMedida() { return unidadeMedida; }
    public BigDecimal getQuantidade() { return quantidade; }
    public LocalDate getData() { return data; }
    public String getNomeDoador() { return nomeDoador; }
    public LocalDate getPrazoValidade() { return prazoValidade; }
    public LocalDate getPrazoGarantia() { return prazoGarantia; }
}
