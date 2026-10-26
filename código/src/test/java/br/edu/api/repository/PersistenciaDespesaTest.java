package br.edu.api.repository;

import br.edu.api.model.Frequencia;
import br.edu.api.model.Instituicao;
import br.edu.api.model.LimiteRepeticao;
import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.model.Repeticao;
import br.edu.api.model.Tag;
import br.edu.api.service.AutorizacaoEventos;
import br.edu.api.service.DespesaService;
import br.edu.api.service.RegistroDespesa;
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
import static org.junit.jupiter.api.Assertions.*;

class PersistenciaDespesaTest {
    @TempDir Path pasta;
    private BancoDeDados banco;
    private JdbcDespesaRepository repositorio;
    private DespesaService servico;
    private final LocalDate hoje = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void prepararBancoIsolado() {
        banco = new BancoDeDados(pasta);
        banco.inicializar();
        repositorio = new JdbcDespesaRepository(banco);
        servico = servicoEm(hoje, AutorizacaoEventos.semSessao());
    }

    @Test
    void reabreArquivoLocalEPreservaDespesa() {
        assertEquals(1, servico.registrar(pedido("Energia", "125.50", hoje, null, "")));
        BancoDeDados reaberto = new BancoDeDados(pasta);
        reaberto.inicializar();
        var despesas = new JdbcDespesaRepository(reaberto).listarPorMes(YearMonth.from(hoje));
        assertEquals(1, despesas.size());
        assertEquals(new BigDecimal("125.50"), despesas.get(0).getValor());
        assertEquals(hoje, despesas.get(0).getData());
    }

    @Test
    void agrupaSomenteNaApresentacaoEPreservaLancamentosIndividuais() throws SQLException {
        servico.registrar(pedido("Água", "10.10", hoje, null, ""));
        servico.registrar(pedido("Água", "15.20", hoje.plusDays(1), null, ""));
        var grupos = servico.listarPorMes(YearMonth.from(hoje));
        assertEquals(1, grupos.size());
        assertEquals(2, grupos.get(0).lancamentos().size());
        assertEquals(new BigDecimal("25.30"), grupos.get(0).total());
        assertEquals(2, contar("despesa"));
        assertNotEquals(grupos.get(0).lancamentos().get(0).getId(), grupos.get(0).lancamentos().get(1).getId());
    }

    @Test
    void filtraMesSemMisturarOcorrenciasDoMesSeguinte() {
        servico.registrar(pedido("Água", "10.00", LocalDate.of(2026, 10, 31), null, ""));
        servico.registrar(pedido("Água", "20.00", LocalDate.of(2026, 11, 1), null, ""));
        assertEquals(new BigDecimal("10.00"), servico.listarPorMes(YearMonth.of(2026, 10)).get(0).total());
        assertEquals(new BigDecimal("20.00"), servico.listarPorMes(YearMonth.of(2026, 11)).get(0).total());
    }

    @Test
    void gravaNomeComAspasComoDadoUsandoConsultaParametrizada() {
        String nome = "Lanche d'Oeste'); DROP TABLE despesa; --";
        servico.registrar(pedido(nome, "10.00", hoje, null, ""));
        servico.registrar(pedido("Segunda despesa", "5.00", hoje, null, ""));
        assertEquals(nome, servico.listarPorMes(YearMonth.from(hoje)).get(0).nome());
        assertEquals(2, repositorio.listarPorMes(YearMonth.from(hoje)).size());
    }

    @Test
    void dadosInvalidosNaoGeramRegistros() throws SQLException {
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido(" ", "10.00", hoje, null, "")));
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido("Água", "0.00", hoje, null, "")));
        assertThrows(RegraDeNegocioException.class,
                () -> servico.registrar(pedido("Água", "10.00", hoje.minusMonths(2).minusDays(1), null, "")));
        assertEquals(0, contar("despesa"));
    }

    @Test
    void quantidadeRepresentaRepeticoesAlemDoLancamentoInicial() throws SQLException {
        Repeticao repeticao = Repeticao.criar(Frequencia.MENSAL, hoje, LimiteRepeticao.QUANTIDADE, 3, null);
        assertEquals(4, servico.registrar(pedido("Internet", "99.90", hoje, repeticao, "")));
        assertEquals(4, contar("despesa"));
        var janeiro = repositorio.listarPorMes(YearMonth.of(2027, 1));
        assertEquals(LocalDate.of(2027, 1, 22), janeiro.get(0).getData());
        assertEquals(new BigDecimal("99.90"), janeiro.get(0).getValor());
        assertEquals(repeticao.id(), janeiro.get(0).getRepeticao().id());
    }

    @Test
    void geraOcorrenciasAteDataLimiteInclusive() throws SQLException {
        Repeticao repeticao = Repeticao.criar(Frequencia.DIARIA, hoje,
                LimiteRepeticao.DATA_LIMITE, null, hoje.plusDays(2));
        assertEquals(3, servico.registrar(pedido("Transporte", "8.00", hoje, repeticao, "")));
        assertEquals(3, contar("despesa"));
    }

    @Test
    void falhaDuranteRepeticoesDesfazTambemADespesaInicialEConfiguracao() throws SQLException {
        try (var conexao = banco.abrirConexao(); var restricao = conexao.prepareStatement(
                "ALTER TABLE despesa ADD CONSTRAINT limite_do_teste CHECK (data < DATE '2026-11-01')")) {
            restricao.execute();
        }
        Repeticao repeticao = Repeticao.criar(Frequencia.MENSAL, hoje, LimiteRepeticao.QUANTIDADE, 2, null);
        assertThrows(PersistenciaException.class,
                () -> servico.registrar(pedido("Internet", "20.00", hoje, repeticao, "")));
        assertEquals(0, contar("despesa"));
        assertEquals(0, contar("repeticao"));
    }

    @Test
    void atualizaQuintoAnoNoMesSeguinteSemDuplicarEAcompanhaMesesEmQueFicouFechado() throws SQLException {
        LocalDate referencia = LocalDate.of(2026, 10, 31);
        Repeticao repeticao = Repeticao.criar(Frequencia.MENSAL, referencia, LimiteRepeticao.PARA_SEMPRE, null, null);
        servico.registrar(pedido("Aluguel", "100.00", referencia, repeticao, ""));
        assertEquals(60, contar("despesa"));
        assertEquals(0, servico.atualizarRepeticoesPermanentes());

        DespesaService novembro = servicoEm(LocalDate.of(2026, 11, 1), AutorizacaoEventos.semSessao());
        assertEquals(1, novembro.atualizarRepeticoesPermanentes());
        assertEquals(0, novembro.atualizarRepeticoesPermanentes());
        assertEquals(61, contar("despesa"));
        assertEquals(LocalDate.of(2031, 10, 31), repositorio.listarPorMes(YearMonth.of(2031, 10)).get(0).getData());

        assertEquals(3, servicoEm(LocalDate.of(2027, 2, 1), AutorizacaoEventos.semSessao()).atualizarRepeticoesPermanentes());
        assertEquals(64, contar("despesa"));
    }

    @Test
    void duasAtualizacoesSimultaneasNaoDuplicamRepeticoes() throws Exception {
        Repeticao repeticao = Repeticao.criar(Frequencia.MENSAL, hoje, LimiteRepeticao.PARA_SEMPRE, null, null);
        servico.registrar(pedido("Aluguel", "100.00", hoje, repeticao, ""));
        int antes = contar("despesa");
        var executor = Executors.newFixedThreadPool(2);
        try {
            var resultados = executor.invokeAll(List.of(
                    () -> repositorio.atualizarRepeticoesPermanentes(LocalDate.of(2026, 11, 22)),
                    () -> repositorio.atualizarRepeticoesPermanentes(LocalDate.of(2026, 11, 22))));
            assertEquals(1, (int) resultados.get(0).get() + (int) resultados.get(1).get());
            assertEquals(antes + 1, contar("despesa"));
        } finally { executor.shutdownNow(); }
    }

    @Test
    void bloqueiaVinculoAEventoSemAutenticacao() throws SQLException {
        assertThrows(RegraDeNegocioException.class,
                () -> servico.registrar(pedido("Material", "20.00", hoje, null, "campanha")));
        assertEquals(0, contar("despesa"));
    }

    @Test
    void vinculaTagDeEventoExistenteQuandoRecebeAutorizacaoDaSessao() throws SQLException {
        cadastrarEventoDeTeste("campanha");
        // Simula a autorização fornecida por um futuro módulo de autenticação.
        DespesaService autenticado = servicoEm(hoje, () -> { });
        Repeticao repeticao = Repeticao.criar(Frequencia.DIARIA, hoje, LimiteRepeticao.QUANTIDADE, 2, null);
        autenticado.registrar(pedido("Material", "20.00", hoje, repeticao, "campanha"));
        assertEquals(3, contar("despesa_tag"));
        for (var despesa : repositorio.listarPorMes(YearMonth.from(hoje))) {
            assertTrue(despesa.getTags().contains(new Tag("campanha")));
        }
    }

    @Test
    void tagInexistenteNaoCriaEventoNemDeixaRegistroParcial() throws SQLException {
        assertThrows(RegraDeNegocioException.class, () -> servicoEm(hoje, () -> { })
                .registrar(pedido("Material", "20.00", hoje, null, "inexistente")));
        assertEquals(0, contar("despesa"));
        assertEquals(0, contar("evento"));
    }

    @Test
    void bancoImpoeIntegridadeReferencialDasTags() throws SQLException {
        servico.registrar(pedido("Material", "20.00", hoje, null, ""));
        UUID id = repositorio.listarPorMes(YearMonth.from(hoje)).get(0).getId();
        try (var conexao = banco.abrirConexao(); var inserir = conexao.prepareStatement(
                "INSERT INTO despesa_tag (despesa_id, tag_nome) VALUES (?, ?)")) {
            inserir.setObject(1, id);
            inserir.setString(2, "tag que não existe");
            assertThrows(SQLException.class, inserir::executeUpdate);
        }
    }

    private DespesaService servicoEm(LocalDate data, AutorizacaoEventos autorizacao) {
        ZoneId fuso = ZoneId.of("America/Sao_Paulo");
        Clock relogio = Clock.fixed(data.atStartOfDay(fuso).toInstant(), fuso);
        return new DespesaService(new Instituicao(relogio), repositorio, autorizacao);
    }

    private RegistroDespesa pedido(String nome, String valor, LocalDate data, Repeticao repeticao, String tag) {
        return new RegistroDespesa(nome, new BigDecimal(valor), data, repeticao, tag);
    }

    private int contar(String tabela) throws SQLException {
        // Os nomes são constantes do teste, nunca uma entrada do usuário.
        try (var conexao = banco.abrirConexao(); var consulta = conexao.prepareStatement("SELECT COUNT(*) FROM " + tabela);
             var registro = consulta.executeQuery()) {
            registro.next();
            return registro.getInt(1);
        }
    }

    private void cadastrarEventoDeTeste(String tag) throws SQLException {
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
