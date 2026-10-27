package br.edu.api.controller;

import br.edu.api.model.RegraDeNegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class EntradaAlteracaoAcaoTest {
    @Test
    void leNovosDadosComDoadorOpcionalEPreservaVinculosQuandoATagNaoEEditavel() {
        var pedido = new EntradaAlteracaoAcao("Contribuição corrigida", "125.50", "01/11/2026", "Ana", null).paraAlteracao();
        assertEquals("Contribuição corrigida", pedido.nome());
        assertEquals(new BigDecimal("125.50"), pedido.valor());
        assertEquals(LocalDate.of(2026, 11, 1), pedido.data());
        assertEquals("Ana", pedido.nomeDoador());
        assertNull(pedido.tagEvento());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "1,50", "1.234", "1e3"})
    void exigeOFormatoMonetarioDosCadastros(String valor) {
        assertThrows(RegraDeNegocioException.class,
                () -> new EntradaAlteracaoAcao("Nome", valor, "22/10/2026", "", null).paraAlteracao());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "31/02/2026", "2026-10-22"})
    void recusaDataMalformadaOuInexistente(String data) {
        assertThrows(RegraDeNegocioException.class,
                () -> new EntradaAlteracaoAcao("Nome", "1.50", data, "", null).paraAlteracao());
    }
}
