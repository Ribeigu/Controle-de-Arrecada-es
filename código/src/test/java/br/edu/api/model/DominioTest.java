package br.edu.api.model;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class DominioTest {
    private final Instituicao instituicao = new Instituicao(Clock.fixed(
            Instant.parse("2026-10-22T15:00:00Z"), ZoneId.of("America/Sao_Paulo")));

    @ParameterizedTest
    @CsvSource({"2026-08-21,false", "2026-08-22,true", "2026-10-22,true", "2031-10-22,true", "2031-10-23,false"})
    void aplicaJanelaConfirmadaPorIgorComLimitesInclusivos(String data, boolean aceita) {
        assertEquals(aceita, instituicao.verificarJanelaTempo(LocalDate.parse(data)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.001"})
    void rejeitaValorInvalidoSemArredondar(String valor) {
        assertThrows(RegraDeNegocioException.class,
                () -> Despesa.criar(new BigDecimal(valor), "Energia", instituicao.hoje()));
    }

    @Test
    void preservaPrecisaoMonetaria() {
        Despesa despesa = Despesa.criar(new BigDecimal("125.10"), " Energia ", instituicao.hoje());
        assertEquals("Energia", despesa.getNome());
        assertEquals(new BigDecimal("125.10"), despesa.getValor());
    }

    @Test
    void rejeitaNomeEmBranco() {
        assertThrows(RegraDeNegocioException.class,
                () -> Despesa.criar(BigDecimal.ONE, "  ", instituicao.hoje()));
    }

    @ParameterizedTest
    @CsvSource({"29,2027-02-28,2027-03-29", "30,2027-02-28,2027-03-30", "31,2027-02-28,2027-03-31"})
    void ajustaMesCurtoSemPerderODiaOriginal(int dia, String fevereiro, String marco) {
        Repeticao repeticao = Repeticao.criar(Frequencia.MENSAL, LocalDate.of(2027, 1, dia),
                LimiteRepeticao.QUANTIDADE, 2, null);
        assertEquals(List.of(LocalDate.parse(fevereiro), LocalDate.parse(marco)), datas(repeticao, null));
    }

    @Test
    void reconheceFevereiroBissexto() {
        Repeticao repeticao = Repeticao.criar(Frequencia.MENSAL, LocalDate.of(2028, 1, 31),
                LimiteRepeticao.QUANTIDADE, 2, null);
        assertEquals(List.of(LocalDate.of(2028, 2, 29), LocalDate.of(2028, 3, 31)), datas(repeticao, null));
    }

    @Test
    void repeticaoAnualVoltaAoDia29NoProximoAnoBissexto() {
        Repeticao repeticao = Repeticao.criar(Frequencia.ANUAL, LocalDate.of(2024, 2, 29),
                LimiteRepeticao.QUANTIDADE, 4, null);
        assertEquals(LocalDate.of(2025, 2, 28), datas(repeticao, null).get(0));
        assertEquals(LocalDate.of(2028, 2, 29), datas(repeticao, null).get(3));
    }

    @Test
    void incluiOcorrenciaExatamenteNaDataLimite() {
        Repeticao repeticao = Repeticao.criar(Frequencia.SEMANAL, LocalDate.of(2026, 10, 22),
                LimiteRepeticao.DATA_LIMITE, null, LocalDate.of(2026, 11, 5));
        assertEquals(List.of(LocalDate.of(2026, 10, 29), LocalDate.of(2026, 11, 5)), datas(repeticao, null));
    }

    @Test
    void paraSempreRespeitaHorizonteERetomaSemRepetirOcorrencias() {
        Repeticao repeticao = Repeticao.criar(Frequencia.DIARIA, LocalDate.of(2026, 10, 22),
                LimiteRepeticao.PARA_SEMPRE, null, null);
        assertEquals(3, datas(repeticao, LocalDate.of(2026, 10, 25)).size());
        var novas = new ArrayList<LocalDate>();
        repeticao.paraCadaOcorrenciaApos(3, LocalDate.of(2026, 10, 27), (numero, data) -> novas.add(data));
        assertEquals(List.of(LocalDate.of(2026, 10, 26), LocalDate.of(2026, 10, 27)), novas);
    }

    @Test
    void exigeLimiteValidoParaRepeticao() {
        assertThrows(RegraDeNegocioException.class, () -> Repeticao.criar(Frequencia.MENSAL,
                instituicao.hoje(), LimiteRepeticao.QUANTIDADE, 0, null));
        assertThrows(RegraDeNegocioException.class, () -> Repeticao.criar(Frequencia.MENSAL,
                instituicao.hoje(), LimiteRepeticao.DATA_LIMITE, null, instituicao.hoje()));
    }

    private List<LocalDate> datas(Repeticao repeticao, LocalDate horizonte) {
        var datas = new ArrayList<LocalDate>();
        repeticao.paraCadaOcorrenciaApos(0, horizonte, (numero, data) -> datas.add(data));
        return datas;
    }
}
