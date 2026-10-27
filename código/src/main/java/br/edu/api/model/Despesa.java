package br.edu.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class Despesa extends Acao {
    private final UUID id;
    private final int numeroOcorrencia;
    private Repeticao repeticao;

    public Despesa(UUID id, String nome, BigDecimal valor, LocalDate data,
                   Repeticao repeticao, int numeroOcorrencia) {
        super(nome, valor, data);
        this.id = id;
        this.repeticao = repeticao;
        this.numeroOcorrencia = numeroOcorrencia;
    }

    public static Despesa criar(BigDecimal valor, String nome, LocalDate data) {
        return new Despesa(UUID.randomUUID(), nome, valor, data, null, 0);
    }

    public void configurarRepeticao(Repeticao repeticao) {
        this.repeticao = repeticao;
    }

    public Despesa registrarAlteracao(String nome, BigDecimal valor, LocalDate data) {
        return new Despesa(id, nome, valor, data, repeticao, numeroOcorrencia);
    }

    public Despesa criarOcorrencia(int numero, LocalDate data) {
        Despesa nova = new Despesa(UUID.randomUUID(), getNome(), getValor(), data, repeticao, numero);
        getTags().forEach(nova::associarTag);
        return nova;
    }

    public UUID getId() { return id; }
    public Repeticao getRepeticao() { return repeticao; }
    public int getNumeroOcorrencia() { return numeroOcorrencia; }
}
