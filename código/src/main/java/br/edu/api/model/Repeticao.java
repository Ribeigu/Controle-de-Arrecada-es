package br.edu.api.model;

import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;

public record Repeticao(UUID id, Frequencia frequencia, LocalDate dataReferencia,
                        LimiteRepeticao limite, Integer quantidadeRepeticoes, LocalDate dataLimite) {
    public Repeticao {
        Objects.requireNonNull(id, "Identificador da repetição");
        if (frequencia == null || dataReferencia == null || limite == null) {
            throw new RegraDeNegocioException("Informe a frequência, a data de referência e o limite da repetição.");
        }
        if (limite == LimiteRepeticao.QUANTIDADE
                && (quantidadeRepeticoes == null || quantidadeRepeticoes < 1)) {
            throw new RegraDeNegocioException("Informe uma quantidade de repetições maior que zero.");
        }
        if (limite == LimiteRepeticao.DATA_LIMITE
                && (dataLimite == null || !dataLimite.isAfter(dataReferencia))) {
            throw new RegraDeNegocioException("A data limite deve ser posterior à data de referência.");
        }
        if (limite != LimiteRepeticao.QUANTIDADE) { quantidadeRepeticoes = null; }
        if (limite != LimiteRepeticao.DATA_LIMITE) { dataLimite = null; }
    }

    public static Repeticao criar(Frequencia frequencia, LocalDate dataReferencia,
                                  LimiteRepeticao limite, Integer quantidade, LocalDate dataLimite) {
        return new Repeticao(UUID.randomUUID(), frequencia, dataReferencia, limite, quantidade, dataLimite);
    }

    /** O lançamento original é a ocorrência zero; as repetições começam em um. */
    public void paraCadaOcorrenciaApos(int ultimaOcorrencia, LocalDate horizonte,
                                      BiConsumer<Integer, LocalDate> receber) {
        long numero = (long) ultimaOcorrencia + 1;
        while (numero <= Integer.MAX_VALUE) {
            if (limite == LimiteRepeticao.QUANTIDADE && numero > quantidadeRepeticoes) {
                return;
            }
            LocalDate data = frequencia.calcularData(dataReferencia, numero);
            LocalDate fim = limite == LimiteRepeticao.DATA_LIMITE ? dataLimite : horizonte;
            if (limite != LimiteRepeticao.QUANTIDADE && data.isAfter(fim)) {
                return;
            }
            receber.accept((int) numero, data);
            numero++;
        }
    }
}
