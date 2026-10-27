package br.edu.api.controller;

import br.edu.api.model.Frequencia;
import br.edu.api.model.LimiteRepeticao;
import br.edu.api.model.RegraDeNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class EntradaDespesaTest {
    @ParameterizedTest
    @ValueSource(strings = {"31/02/2026", "29/02/2027", "2026-10-22", "", "1/1/2026"})
    void rejeitaDatasInexistentesOuForaDoFormato(String data) {
        assertThrows(RegraDeNegocioException.class, () -> entrada("10.00", data).paraRegistro());
    }

    @ParameterizedTest
    @ValueSource(strings = {"10,50", "1.000,00", "10.001", "dez", "", "-1.00"})
    void exigeFormatoMonetarioDocumentado(String valor) {
        assertThrows(RegraDeNegocioException.class, () -> entrada(valor, "22/10/2026").paraRegistro());
    }

    @Test
    void converteDatasEValoresSemDependerDaConfiguracaoRegionalDoComputador() {
        var registro = entrada("125.50", "29/02/2028").paraRegistro();
        assertEquals(LocalDate.of(2028, 2, 29), registro.data());
        assertEquals(new BigDecimal("125.50"), registro.valor());
        assertNull(registro.repeticao());
    }

    @Test
    void leDataDeCobrancaSeparadamenteDaDataDoLancamento() {
        var entrada = new EntradaDespesa("Água", "20.00", "22/10/2026", true,
                Frequencia.MENSAL, "31/10/2026", LimiteRepeticao.QUANTIDADE, "3", "", "");
        var repeticao = entrada.paraRegistro().repeticao();
        assertEquals(LocalDate.of(2026, 10, 31), repeticao.dataReferencia());
        assertEquals(3, repeticao.quantidadeRepeticoes());
    }

    private EntradaDespesa entrada(String valor, String data) {
        return new EntradaDespesa("Energia", valor, data, false, null, "", null, "", "", "");
    }
}
