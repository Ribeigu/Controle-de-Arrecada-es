package br.edu.api.controller;

import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.model.UnidadeMedida;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class EntradaDoacaoProdutoTest {
    @ParameterizedTest
    @CsvSource({"5, 5", "'1,25', 1.25", "1.25, 1.25", "0.000001, 0.000001"})
    void leQuantidadeInteiraOuDecimalSemExigirCamposOpcionais(String texto, String esperado) {
        var registro = entrada(texto, "22/10/2026", "", "").paraRegistro();
        assertEquals(0, new BigDecimal(esperado).compareTo(registro.quantidade()));
        assertEquals(UnidadeMedida.KG, registro.unidadeMedida());
        assertEquals(LocalDate.of(2026, 10, 22), registro.data());
        assertNull(registro.prazoValidade());
        assertNull(registro.prazoGarantia());
    }

    @Test
    void leDoadorEAsDuasDatasOpcionais() {
        var entrada = new EntradaDoacaoProduto("Arroz", UnidadeMedida.KG, "2,5", "22/10/2026",
                "Ana Souza", "31/12/2027", "22/10/2028");
        var registro = entrada.paraRegistro();
        assertEquals("Ana Souza", registro.nomeDoador());
        assertEquals(LocalDate.of(2027, 12, 31), registro.prazoValidade());
        assertEquals(LocalDate.of(2028, 10, 22), registro.prazoGarantia());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void aceitaDatasOpcionaisNaoPreenchidas(String texto) {
        var registro = entrada("1", "22/10/2026", texto, texto).paraRegistro();
        assertNull(registro.prazoValidade());
        assertNull(registro.prazoGarantia());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"1.000,25", "1,000.25", "-1", "1e3", "abc"})
    void recusaQuantidadeMalformada(String texto) {
        assertThrows(RegraDeNegocioException.class, () -> entrada(texto, "22/10/2026", "", "").paraRegistro());
    }

    @ParameterizedTest
    @ValueSource(strings = {"31/02/2026", "2026-10-22", ""})
    void recusaDataObrigatoriaInvalida(String data) {
        assertThrows(RegraDeNegocioException.class, () -> entrada("1", data, "", "").paraRegistro());
    }

    @Test
    void identificaQualDataOpcionalPrecisaSerCorrigida() {
        var validade = assertThrows(RegraDeNegocioException.class,
                () -> entrada("1", "22/10/2026", "31/02/2027", "").paraRegistro());
        var garantia = assertThrows(RegraDeNegocioException.class,
                () -> entrada("1", "22/10/2026", "", "31/02/2027").paraRegistro());
        assertTrue(validade.getMessage().contains("validade"));
        assertTrue(garantia.getMessage().contains("garantia"));
    }

    private EntradaDoacaoProduto entrada(String quantidade, String data, String validade, String garantia) {
        return new EntradaDoacaoProduto("Arroz", UnidadeMedida.KG, quantidade, data, "", validade, garantia);
    }
}
