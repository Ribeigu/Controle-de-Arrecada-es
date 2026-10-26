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

class EntradaArrecadacaoTest {
    @Test
    void leArrecadacaoSemExigirDoadorOuRecorrencia() {
        var registro = entrada("250.75", "22/10/2026").paraRegistro();
        assertEquals(new BigDecimal("250.75"), registro.valor());
        assertEquals(LocalDate.of(2026, 10, 22), registro.data());
        assertTrue(registro.nomeDoador().isEmpty());
        assertNull(registro.repeticao());
    }

    @Test
    void preservaDoadorELeDataBaseDeEntradaSeparadamente() {
        var entrada = new EntradaArrecadacao("Contribuição", "50.00", "22/10/2026", "Ana Souza",
                true, Frequencia.MENSAL, "31/10/2026", LimiteRepeticao.QUANTIDADE, "3", "", "");
        var registro = entrada.paraRegistro();
        assertEquals("Ana Souza", registro.nomeDoador());
        assertEquals(LocalDate.of(2026, 10, 31), registro.repeticao().dataReferencia());
        assertEquals(3, registro.repeticao().quantidadeRepeticoes());
    }

    @ParameterizedTest
    @ValueSource(strings = {"31/02/2026", "2026-10-22", ""})
    void rejeitaDataInvalidaNaEntrada(String data) {
        assertThrows(RegraDeNegocioException.class, () -> entrada("20.00", data).paraRegistro());
    }

    @ParameterizedTest
    @ValueSource(strings = {"10,50", "10.001", ""})
    void rejeitaValorForaDoFormatoDocumentado(String valor) {
        assertThrows(RegraDeNegocioException.class, () -> entrada(valor, "22/10/2026").paraRegistro());
    }

    @Test
    void leDataLimiteInclusivaSemExigirQuantidade() {
        var entrada = new EntradaArrecadacao("Contribuição", "50.00", "22/10/2026", "",
                true, Frequencia.DIARIA, "22/10/2026", LimiteRepeticao.DATA_LIMITE, "", "24/10/2026", "");
        var repeticao = entrada.paraRegistro().repeticao();
        assertEquals(LocalDate.of(2026, 10, 24), repeticao.dataLimite());
        assertNull(repeticao.quantidadeRepeticoes());
    }

    private EntradaArrecadacao entrada(String valor, String data) {
        return new EntradaArrecadacao("Contribuição", valor, data, "", false, null, "", null, "", "", "");
    }
}
