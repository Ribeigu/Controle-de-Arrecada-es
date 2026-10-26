package br.edu.api.controller;

import br.edu.api.model.Frequencia;
import br.edu.api.model.LimiteRepeticao;
import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.model.Repeticao;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;

/** Conversões usadas pelos formulários de registros financeiros e doações físicas. */
final class LeituraFormulario {
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter
            .ofPattern("dd/MM/uuuu").withResolverStyle(ResolverStyle.STRICT);

    private LeituraFormulario() { }

    static BigDecimal lerValor(String texto) {
        if (texto == null || !texto.strip().matches("[0-9]+\\.[0-9]{2}")) {
            throw new RegraDeNegocioException("Informe o valor no formato 125.50, com duas casas decimais.");
        }
        return new BigDecimal(texto.strip());
    }

    static LocalDate lerData(String texto, String campo) {
        try {
            return LocalDate.parse(texto == null ? "" : texto.strip(), FORMATO_DATA);
        } catch (DateTimeParseException erro) {
            throw new RegraDeNegocioException("Informe uma " + campo + " válida no formato DD/MM/AAAA.");
        }
    }

    static BigDecimal lerQuantidadeProduto(String texto) {
        if (texto == null || !texto.strip().matches("[0-9]+([.,][0-9]+)?")) {
            throw new RegraDeNegocioException("Informe uma quantidade válida. Exemplos: 5 ou 1,25.");
        }
        return new BigDecimal(texto.strip().replace(',', '.'));
    }

    static LocalDate lerDataOpcional(String texto, String campo) {
        return texto == null || texto.isBlank() ? null : lerData(texto, campo);
    }

    static Repeticao lerRepeticao(Frequencia frequencia, String dataReferencia, String nomeCampoReferencia,
                                  LimiteRepeticao limite, String quantidade, String dataLimite) {
        LocalDate referencia = lerData(dataReferencia, nomeCampoReferencia);
        Integer numero = limite == LimiteRepeticao.QUANTIDADE ? lerQuantidade(quantidade) : null;
        LocalDate fim = limite == LimiteRepeticao.DATA_LIMITE ? lerData(dataLimite, "data limite") : null;
        return Repeticao.criar(frequencia, referencia, limite, numero, fim);
    }

    private static Integer lerQuantidade(String texto) {
        try {
            return Integer.valueOf(texto.strip());
        } catch (NumberFormatException | NullPointerException erro) {
            throw new RegraDeNegocioException("Informe uma quantidade inteira de repetições maior que zero.");
        }
    }
}
