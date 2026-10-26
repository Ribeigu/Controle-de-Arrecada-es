package br.edu.api.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public final class Arrecadacao extends Acao {
    private final UUID id;
    private final String nomeDoador;
    private final int numeroOcorrencia;
    private Repeticao repeticao;

    public Arrecadacao(UUID id, String nome, BigDecimal valor, LocalDate data, String nomeDoador,
                       Repeticao repeticao, int numeroOcorrencia) {
        super(nome, valor, data);
        this.id = id;
        this.nomeDoador = nomeDoador == null || nomeDoador.isBlank() ? null : nomeDoador.strip();
        this.repeticao = repeticao;
        this.numeroOcorrencia = numeroOcorrencia;
    }

    public static Arrecadacao criar(BigDecimal valor, String nome, LocalDate data, String nomeDoador) {
        return new Arrecadacao(UUID.randomUUID(), nome, valor, data, nomeDoador, null, 0);
    }

    public void configurarRepeticao(Repeticao repeticao) {
        this.repeticao = repeticao;
    }

    public Arrecadacao registrarAlteracao(String nome, BigDecimal valor, LocalDate data, String nomeDoador) {
        return new Arrecadacao(id, nome, valor, data, nomeDoador, repeticao, numeroOcorrencia);
    }

    public Arrecadacao criarOcorrencia(int numero, LocalDate data) {
        Arrecadacao nova = new Arrecadacao(UUID.randomUUID(), getNome(), getValor(), data,
                nomeDoador, repeticao, numero);
        getTags().forEach(nova::associarTag);
        return nova;
    }

    public UUID getId() { return id; }
    public String getNomeDoador() { return nomeDoador; }
    public Repeticao getRepeticao() { return repeticao; }
    public int getNumeroOcorrencia() { return numeroOcorrencia; }
}
