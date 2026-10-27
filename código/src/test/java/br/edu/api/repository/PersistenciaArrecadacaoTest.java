package br.edu.api.repository;

import br.edu.api.model.Arrecadacao;
import br.edu.api.model.Instituicao;
import br.edu.api.model.Frequencia;
import br.edu.api.model.LimiteRepeticao;
import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.model.Repeticao;
import br.edu.api.model.Tag;
import br.edu.api.service.ArrecadacaoService;
import br.edu.api.service.AutorizacaoEventos;
import br.edu.api.service.RegistroArrecadacao;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PersistenciaArrecadacaoTest {
    @TempDir Path pasta;
    private BancoDeDados banco;
    private JdbcArrecadacaoRepository repositorio;
    private ArrecadacaoService servico;
    private final LocalDate hoje = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void prepararBancoIsolado() {
        banco = new BancoDeDados(pasta);
        banco.inicializar();
        repositorio = new JdbcArrecadacaoRepository(banco);
        servico = servicoEm(hoje, AutorizacaoEventos.semSessao());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void gravaArrecadacaoSemDoador(String nomeDoador) {
        servico.registrar(pedido("Contribuição", "150.50", hoje, nomeDoador, null, ""));
        var registros = repositorio.listarPorMes(YearMonth.from(hoje));
        assertEquals(1, registros.size());
        assertNull(registros.get(0).getNomeDoador());
        assertEquals(new BigDecimal("150.50"), registros.get(0).getValor());
    }

    @Test
    void reabreArquivoLocalEPreservaDoadorDataEValor() {
        servico.registrar(pedido("Contribuição", "250.75", hoje, "  João d'Ávila  ", null, ""));
        BancoDeDados reaberto = new BancoDeDados(pasta);
        reaberto.inicializar();
        var arrecadacao = new JdbcArrecadacaoRepository(reaberto).listarPorMes(YearMonth.from(hoje)).get(0);
        assertEquals("João d'Ávila", arrecadacao.getNomeDoador());
        assertEquals(hoje, arrecadacao.getData());
        assertEquals(new BigDecimal("250.75"), arrecadacao.getValor());
    }

    @Test
    void registrosComMesmoNomeMantemDoadoresEIdentidadesSeparados() {
        servico.registrar(pedido("Contribuição", "10.00", hoje, "Ana", null, ""));
        servico.registrar(pedido("Contribuição", "20.00", hoje.plusDays(1), "Bia", null, ""));
        var registros = servico.listarPorMes(YearMonth.from(hoje));
        assertEquals(2, registros.size());
        assertEquals(List.of("Ana", "Bia"), registros.stream().map(Arrecadacao::getNomeDoador).toList());
        assertNotEquals(registros.get(0).getId(), registros.get(1).getId());
    }

    @Test
    void filtraMesComLimitesCorretos() {
        servico.registrar(pedido("Contribuição", "10.00", LocalDate.of(2026, 10, 31), "Ana", null, ""));
        servico.registrar(pedido("Contribuição", "20.00", LocalDate.of(2026, 11, 1), "Bia", null, ""));
        assertEquals("Ana", servico.listarPorMes(YearMonth.of(2026, 10)).get(0).getNomeDoador());
        assertEquals("Bia", servico.listarPorMes(YearMonth.of(2026, 11)).get(0).getNomeDoador());
    }

    @Test
    void recusaDadosObrigatoriosInvalidosEDatasForaDaJanela() throws SQLException {
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido(" ", "10.00", hoje, null, null, "")));
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido("Doação", "0.00", hoje, null, null, "")));
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido("Doação", "10.00", null, null, null, "")));
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido("Doação", "10.00",
                hoje.minusMonths(2).minusDays(1), null, null, "")));
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido("Doação", "10.00",
                hoje.plusYears(5).plusDays(1), null, null, "")));
        assertEquals(0, contar("arrecadacao"));
    }

    @ParameterizedTest
    @EnumSource(Frequencia.class)
    void geraQuantidadeAdicionalEmCadaFrequenciaEPreservaDoador(Frequencia frequencia) throws SQLException {
        Repeticao repeticao = Repeticao.criar(frequencia, hoje, LimiteRepeticao.QUANTIDADE, 2, null);
        assertEquals(3, servico.registrar(pedido("Contribuição", "40.00", hoje, "Ana", repeticao, "")));
        assertEquals(3, contar("arrecadacao"));
        LocalDate ultimaData = frequencia.calcularData(hoje, 2);
        var ultima = repositorio.listarPorMes(YearMonth.from(ultimaData)).stream()
                .filter(arrecadacao -> arrecadacao.getNumeroOcorrencia() == 2).findFirst().orElseThrow();
        assertEquals(ultimaData, ultima.getData());
        assertEquals("Ana", ultima.getNomeDoador());
        assertEquals(new BigDecimal("40.00"), ultima.getValor());
        assertEquals(repeticao.id(), ultima.getRepeticao().id());
    }

    @Test
    void repeticaoMensalAjustaFevereiroERecuperaODia31EmMarco() {
        LocalDate referencia = LocalDate.of(2027, 1, 31);
        Repeticao repeticao = Repeticao.criar(Frequencia.MENSAL, referencia, LimiteRepeticao.QUANTIDADE, 2, null);
        servico.registrar(pedido("Contribuição", "40.00", referencia, "Ana", repeticao, ""));
        assertEquals(LocalDate.of(2027, 2, 28), repositorio.listarPorMes(YearMonth.of(2027, 2)).get(0).getData());
        assertEquals(LocalDate.of(2027, 3, 31), repositorio.listarPorMes(YearMonth.of(2027, 3)).get(0).getData());
    }

    @Test
    void incluiUltimaOcorrenciaNaDataLimite() throws SQLException {
        Repeticao repeticao = Repeticao.criar(Frequencia.DIARIA, hoje,
                LimiteRepeticao.DATA_LIMITE, null, hoje.plusDays(2));
        assertEquals(3, servico.registrar(pedido("Contribuição", "40.00", hoje, null, repeticao, "")));
        assertEquals(3, contar("arrecadacao"));
        assertEquals(hoje.plusDays(2), repositorio.listarPorMes(YearMonth.from(hoje)).get(2).getData());
    }

    @Test
    void falhaEmUmaRepeticaoDesfazOriginalConfiguracaoEVinculos() throws SQLException {
        cadastrarEvento("campanha");
        try (var conexao = banco.abrirConexao(); var restricao = conexao.prepareStatement(
                "ALTER TABLE arrecadacao ADD CONSTRAINT limite_do_teste CHECK (data < DATE '2026-11-01')")) {
            restricao.execute();
        }
        Repeticao repeticao = Repeticao.criar(Frequencia.MENSAL, hoje, LimiteRepeticao.QUANTIDADE, 2, null);
        assertThrows(PersistenciaException.class, () -> servicoEm(hoje, () -> { })
                .registrar(pedido("Contribuição", "40.00", hoje, "Ana", repeticao, "campanha")));
        assertEquals(0, contar("arrecadacao"));
        assertEquals(0, contar("repeticao"));
        assertEquals(0, contar("arrecadacao_tag"));
        assertEquals(1, contar("evento"));
    }

    @Test
    void atualizaParaSempreRecuperandoMesesFechadosSemDuplicar() throws SQLException {
        LocalDate referencia = LocalDate.of(2026, 10, 31);
        Repeticao repeticao = Repeticao.criar(Frequencia.MENSAL, referencia, LimiteRepeticao.PARA_SEMPRE, null, null);
        servico.registrar(pedido("Contribuição", "40.00", referencia, "Ana", repeticao, ""));
        assertEquals(60, contar("arrecadacao"));
        assertEquals(0, servico.atualizarRepeticoesPermanentes());
        var novembro = servicoEm(LocalDate.of(2026, 11, 1), AutorizacaoEventos.semSessao());
        assertEquals(1, novembro.atualizarRepeticoesPermanentes());
        assertEquals(0, novembro.atualizarRepeticoesPermanentes());
        assertEquals(3, servicoEm(LocalDate.of(2027, 2, 1), AutorizacaoEventos.semSessao()).atualizarRepeticoesPermanentes());
        assertEquals(64, contar("arrecadacao"));
        assertEquals("Ana", repositorio.listarPorMes(YearMonth.of(2032, 1)).get(0).getNomeDoador());
    }

    @Test
    void atualizacoesSimultaneasNaoDuplicamArrecadacoes() throws Exception {
        Repeticao repeticao = Repeticao.criar(Frequencia.MENSAL, hoje, LimiteRepeticao.PARA_SEMPRE, null, null);
        servico.registrar(pedido("Contribuição", "40.00", hoje, "Ana", repeticao, ""));
        int antes = contar("arrecadacao");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var resultados = executor.invokeAll(List.of(
                    () -> repositorio.atualizarRepeticoesPermanentes(LocalDate.of(2026, 11, 22)),
                    () -> repositorio.atualizarRepeticoesPermanentes(LocalDate.of(2026, 11, 22))));
            assertEquals(1, (int) resultados.get(0).get() + (int) resultados.get(1).get());
            assertEquals(antes + 1, contar("arrecadacao"));
        } finally { executor.shutdownNow(); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void despesasEArrecadacoesAtualizamSomenteAsPropriasRecorrencias(boolean arrecadacaoPrimeiro) {
        LocalDate referencia = LocalDate.of(2026, 10, 31);
        Repeticao repeticaoReceita = Repeticao.criar(Frequencia.MENSAL, referencia, LimiteRepeticao.PARA_SEMPRE, null, null);
        servico.registrar(pedido("Contribuição", "40.00", referencia, "Ana", repeticaoReceita, ""));
        var despesas = new JdbcDespesaRepository(banco);
        var despesa = br.edu.api.model.Despesa.criar(new BigDecimal("10.00"), "Energia", referencia);
        despesa.configurarRepeticao(Repeticao.criar(Frequencia.MENSAL, referencia, LimiteRepeticao.PARA_SEMPRE, null, null));
        despesas.registrar(despesa, hoje);
        LocalDate mesSeguinte = LocalDate.of(2026, 11, 1);
        YearMonth mesDoQuintoAno = YearMonth.of(2031, 10);
        if (arrecadacaoPrimeiro) {
            assertEquals(1, repositorio.atualizarRepeticoesPermanentes(mesSeguinte));
            assertTrue(despesas.listarPorMes(mesDoQuintoAno).isEmpty());
            assertEquals(1, despesas.atualizarRepeticoesPermanentes(mesSeguinte));
        } else {
            assertEquals(1, despesas.atualizarRepeticoesPermanentes(mesSeguinte));
            assertTrue(repositorio.listarPorMes(mesDoQuintoAno).isEmpty());
            assertEquals(1, repositorio.atualizarRepeticoesPermanentes(mesSeguinte));
        }
        assertEquals(1, repositorio.listarPorMes(mesDoQuintoAno).size());
        assertEquals(1, despesas.listarPorMes(mesDoQuintoAno).size());
        assertEquals(0, repositorio.atualizarRepeticoesPermanentes(mesSeguinte));
        assertEquals(0, despesas.atualizarRepeticoesPermanentes(mesSeguinte));
    }

    @Test
    void trataNomeEDoadorComAspasComoDados() {
        String doador = "Ana d'Oeste'); DROP TABLE arrecadacao; --";
        servico.registrar(pedido("Contribuição d'Ávila", "10.00", hoje, doador, null, ""));
        assertEquals(doador, repositorio.listarPorMes(YearMonth.from(hoje)).get(0).getNomeDoador());
        servico.registrar(pedido("Outra contribuição", "10.00", hoje, null, null, ""));
        assertEquals(2, repositorio.listarPorMes(YearMonth.from(hoje)).size());
    }

    @Test
    void exigeAutenticacaoParaVincularEvento() throws SQLException {
        cadastrarEvento("campanha");
        assertThrows(RegraDeNegocioException.class,
                () -> servico.registrar(pedido("Contribuição", "10.00", hoje, "Ana", null, "campanha")));
        assertEquals(0, contar("arrecadacao"));
    }

    @Test
    void vinculaEventoExistenteEPreservaTagEDoadorNasRepeticoes() throws SQLException {
        cadastrarEvento("campanha");
        Repeticao repeticao = Repeticao.criar(Frequencia.DIARIA, hoje, LimiteRepeticao.QUANTIDADE, 2, null);
        // Representa a autorização que será fornecida pelo módulo de autenticação.
        servicoEm(hoje, () -> { }).registrar(pedido("Contribuição", "10.00", hoje, "Ana", repeticao, "campanha"));
        assertEquals(3, contar("arrecadacao_tag"));
        for (Arrecadacao arrecadacao : repositorio.listarPorMes(YearMonth.from(hoje))) {
            assertTrue(arrecadacao.getTags().contains(new Tag("campanha")));
            assertEquals("Ana", arrecadacao.getNomeDoador());
        }
    }

    @Test
    void recusaTagInexistenteSemGravarParcialmente() throws SQLException {
        assertThrows(RegraDeNegocioException.class, () -> servicoEm(hoje, () -> { })
                .registrar(pedido("Contribuição", "10.00", hoje, "Ana", null, "não existe")));
        assertEquals(0, contar("arrecadacao"));
        assertEquals(0, contar("evento"));
    }

    private ArrecadacaoService servicoEm(LocalDate data, AutorizacaoEventos autorizacao) {
        ZoneId fuso = ZoneId.of("America/Sao_Paulo");
        Clock relogio = Clock.fixed(data.atStartOfDay(fuso).toInstant(), fuso);
        return new ArrecadacaoService(new Instituicao(relogio), repositorio, autorizacao);
    }

    private RegistroArrecadacao pedido(String nome, String valor, LocalDate data, String doador,
                                       Repeticao repeticao, String tag) {
        return new RegistroArrecadacao(nome, new BigDecimal(valor), data, doador, repeticao, tag);
    }

    private int contar(String tabela) throws SQLException {
        // Os nomes são constantes do teste, nunca uma entrada do usuário.
        try (var conexao = banco.abrirConexao(); var consulta = conexao.prepareStatement("SELECT COUNT(*) FROM " + tabela);
             var registro = consulta.executeQuery()) {
            registro.next();
            return registro.getInt(1);
        }
    }

    private void cadastrarEvento(String tag) throws SQLException {
        try (var conexao = banco.abrirConexao();
             var inserirTag = conexao.prepareStatement("INSERT INTO tag (nome) VALUES (?)");
             var inserirEvento = conexao.prepareStatement("""
                     INSERT INTO evento (id, nome, tag_nome, status, data_inicio, data_termino)
                     VALUES (?, ?, ?, ?, ?, ?)
                     """)) {
            inserirTag.setString(1, tag);
            inserirTag.executeUpdate();
            inserirEvento.setObject(1, UUID.randomUUID());
            inserirEvento.setString(2, "Campanha de teste");
            inserirEvento.setString(3, tag);
            inserirEvento.setString(4, "planejado");
            inserirEvento.setObject(5, hoje);
            inserirEvento.setObject(6, hoje.plusMonths(1));
            inserirEvento.executeUpdate();
        }
    }
}
