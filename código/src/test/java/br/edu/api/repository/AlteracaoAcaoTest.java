package br.edu.api.repository;

import br.edu.api.model.Acao;
import br.edu.api.model.Arrecadacao;
import br.edu.api.model.Despesa;
import br.edu.api.model.Frequencia;
import br.edu.api.model.Instituicao;
import br.edu.api.model.LimiteRepeticao;
import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.model.Repeticao;
import br.edu.api.model.Tag;
import br.edu.api.service.AlteracaoAcao;
import br.edu.api.service.ArrecadacaoService;
import br.edu.api.service.AutorizacaoEventos;
import br.edu.api.service.DespesaService;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class AlteracaoAcaoTest {
    @TempDir Path pasta;
    private BancoDeDados banco;
    private JdbcDespesaRepository despesas;
    private JdbcArrecadacaoRepository arrecadacoes;
    private Instituicao instituicao;
    private final LocalDate hoje = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void prepararBancoIsolado() {
        banco = new BancoDeDados(pasta);
        banco.inicializar();
        despesas = new JdbcDespesaRepository(banco);
        arrecadacoes = new JdbcArrecadacaoRepository(banco);
        ZoneId fuso = ZoneId.of("America/Sao_Paulo");
        instituicao = new Instituicao(Clock.fixed(hoje.atStartOfDay(fuso).toInstant(), fuso));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void alteraRegistroExistenteEPersisteNoNovoMesSemCriarOutraIdentidade(boolean arrecadacao) {
        Acao original = registrar(arrecadacao, hoje, null, null);
        LocalDate novaData = hoje.plusMonths(1).withDayOfMonth(1);
        alterar(arrecadacao, original, new AlteracaoAcao("  Nome d'Ávila corrigido  ", new BigDecimal("125.50"), novaData, "  Bia  ", null), false);
        var reaberto = new BancoDeDados(pasta);
        reaberto.inicializar();
        Acao salva = arrecadacao ? new JdbcArrecadacaoRepository(reaberto).buscarPorId(id(original)).orElseThrow()
                : new JdbcDespesaRepository(reaberto).buscarPorId(id(original)).orElseThrow();
        assertEquals(id(original), id(salva));
        assertEquals("Nome d'Ávila corrigido", salva.getNome());
        assertEquals(new BigDecimal("125.50"), salva.getValor());
        assertEquals(novaData, salva.getData());
        assertTrue(listar(arrecadacao, YearMonth.from(hoje)).isEmpty());
        assertEquals(1, listar(arrecadacao, YearMonth.from(novaData)).size());
        if (salva instanceof Arrecadacao entrada) { assertEquals("Bia", entrada.getNomeDoador()); }
    }

    @Test
    void permiteRemoverONomeDoDoadorSemExcluirAArrecadacao() {
        var original = registrar(true, hoje, null, null);
        alterar(true, original, new AlteracaoAcao(original.getNome(), original.getValor(), hoje, " ", null), false);
        assertNull(((Arrecadacao) buscar(true, id(original))).getNomeDoador());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void recusaDadosInvalidosSemAlterarOriginalEPermiteCorrigirDepois(boolean arrecadacao) {
        var original = registrar(arrecadacao, hoje, null, null);
        var invalidos = List.of(
                new AlteracaoAcao(" ", BigDecimal.ONE, hoje, "Ana", null),
                new AlteracaoAcao("Nome", BigDecimal.ZERO, hoje, "Ana", null),
                new AlteracaoAcao("Nome", new BigDecimal("-1"), hoje, "Ana", null),
                new AlteracaoAcao("Nome", new BigDecimal("1.001"), hoje, "Ana", null),
                new AlteracaoAcao("Nome", new BigDecimal("100000000000000000"), hoje, "Ana", null),
                new AlteracaoAcao("Nome", null, hoje, "Ana", null),
                new AlteracaoAcao("Nome", BigDecimal.ONE, null, "Ana", null),
                new AlteracaoAcao("Nome", BigDecimal.ONE, hoje.minusMonths(2).minusDays(1), "Ana", null),
                new AlteracaoAcao("Nome", BigDecimal.ONE, hoje.plusYears(5).plusDays(1), "Ana", null));
        for (var pedido : invalidos) {
            assertThrows(RegraDeNegocioException.class, () -> alterar(arrecadacao, original, pedido, false));
            assertEquals(original.getNome(), buscar(arrecadacao, id(original)).getNome());
            assertEquals(original.getValor(), buscar(arrecadacao, id(original)).getValor());
        }
        alterar(arrecadacao, original, pedido("Corrigido", hoje), false);
        assertEquals("Corrigido", buscar(arrecadacao, id(original)).getNome());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exigeQueADataAtualEASubstitutaEstejamNaJanelaInclusiva(boolean arrecadacao) {
        LocalDate inicio = hoje.minusMonths(2);
        LocalDate fim = hoje.plusYears(5);
        var original = registrar(arrecadacao, inicio, null, null);
        var alterada = alterar(arrecadacao, original, pedido("No limite futuro", fim), false);
        alterar(arrecadacao, alterada, pedido("No limite passado", inicio), false);
        for (LocalDate fora : List.of(inicio.minusDays(1), fim.plusDays(1))) {
            var antiga = registrar(arrecadacao, fora, null, null);
            assertThrows(RegraDeNegocioException.class, () -> alterar(arrecadacao, antiga, pedido("Mover para hoje", hoje), false));
            assertEquals(fora, buscar(arrecadacao, id(antiga)).getData());
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void editaApenasAOcorrenciaSelecionadaPreservandoConfiguracaoEOutrosLancamentos(boolean arrecadacao) {
        var repeticao = Repeticao.criar(Frequencia.DIARIA, hoje, LimiteRepeticao.QUANTIDADE, 2, null);
        registrar(arrecadacao, hoje, repeticao, null);
        var antes = listar(arrecadacao, YearMonth.from(hoje));
        Acao selecionada = antes.get(1);
        alterar(arrecadacao, selecionada, pedido("Ocorrência corrigida", hoje.plusMonths(1)), false);
        assertEquals(antes.get(0).getNome(), buscar(arrecadacao, id(antes.get(0))).getNome());
        assertEquals(antes.get(2).getValor(), buscar(arrecadacao, id(antes.get(2))).getValor());
        Acao salva = buscar(arrecadacao, id(selecionada));
        assertEquals(repeticao.id(), repeticao(salva).id());
        assertEquals(1, numero(salva));
        assertEquals(2, listar(arrecadacao, YearMonth.from(hoje)).size());
        assertEquals(1, listar(arrecadacao, YearMonth.from(hoje.plusMonths(1))).size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void editarOriginalPermanenteNaoMudaOsDadosDasOcorrenciasFuturas(boolean arrecadacao) throws SQLException {
        cadastrarEvento("campanha");
        var referencia = hoje.withDayOfMonth(1);
        var repeticao = Repeticao.criar(Frequencia.MENSAL, referencia, LimiteRepeticao.PARA_SEMPRE, null, null);
        var original = registrar(arrecadacao, referencia, repeticao, "campanha");
        var alterada = alterar(arrecadacao, original, new AlteracaoAcao("Corrigido", new BigDecimal("2.00"), hoje, "Bia", ""), true);
        alterar(arrecadacao, alterada, pedido("Segunda correção", hoje.plusDays(1)), false);
        var proximoMes = hoje.plusMonths(1).withDayOfMonth(1);
        int geradas = arrecadacao ? arrecadacoes.atualizarRepeticoesPermanentes(proximoMes)
                : despesas.atualizarRepeticoesPermanentes(proximoMes);
        assertEquals(1, geradas);
        var futura = listar(arrecadacao, YearMonth.from(proximoMes.plusYears(5))).get(0);
        assertEquals(original.getNome(), futura.getNome());
        assertEquals(original.getValor(), futura.getValor());
        assertEquals(Set.of(new Tag("campanha")), futura.getTags());
        assertEquals(proximoMes.plusYears(5), futura.getData());
        if (futura instanceof Arrecadacao entrada) { assertEquals("Ana", entrada.getNomeDoador()); }
        assertEquals("Segunda correção", buscar(arrecadacao, id(original)).getNome());
        assertTrue(buscar(arrecadacao, id(original)).getTags().isEmpty());
        assertEquals(repeticao, repeticao(buscar(arrecadacao, id(original))));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exigeAutenticacaoParaEditarAcaoVinculadaOuMudarSeuVinculo(boolean arrecadacao) throws SQLException {
        cadastrarEvento("campanha");
        var vinculada = registrar(arrecadacao, hoje, null, "campanha");
        for (String tag : new String[]{null, "", "campanha"}) {
            assertThrows(RegraDeNegocioException.class, () -> alterar(arrecadacao, vinculada,
                    new AlteracaoAcao("Nome", BigDecimal.ONE, hoje, "Bia", tag), false));
        }
        var livre = registrar(arrecadacao, hoje, null, null);
        assertThrows(RegraDeNegocioException.class, () -> alterar(arrecadacao, livre,
                new AlteracaoAcao("Nome", BigDecimal.ONE, hoje, "Bia", "campanha"), false));
        assertEquals(Set.of(new Tag("campanha")), buscar(arrecadacao, id(vinculada)).getTags());
        assertTrue(buscar(arrecadacao, id(livre)).getTags().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservaSubstituiOuRemoveTagAutorizadaERecusaEventoInexistente(boolean arrecadacao) throws SQLException {
        cadastrarEvento("antigo");
        cadastrarEvento("novo");
        var original = registrar(arrecadacao, hoje, null, "antigo");
        var mantida = alterar(arrecadacao, original, pedido("Nome corrigido", hoje), true);
        assertEquals(Set.of(new Tag("antigo")), buscar(arrecadacao, id(original)).getTags());
        assertThrows(RegraDeNegocioException.class, () -> alterar(arrecadacao, mantida,
                new AlteracaoAcao("Não gravar", BigDecimal.ONE, hoje, null, "inexistente"), true));
        assertEquals("Nome corrigido", buscar(arrecadacao, id(original)).getNome());
        var substituida = alterar(arrecadacao, mantida, new AlteracaoAcao("Novo", BigDecimal.ONE, hoje, null, "novo"), true);
        assertEquals(Set.of(new Tag("novo")), buscar(arrecadacao, id(original)).getTags());
        alterar(arrecadacao, substituida, new AlteracaoAcao("Sem evento", BigDecimal.ONE, hoje, null, ""), true);
        assertTrue(buscar(arrecadacao, id(original)).getTags().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void falhaNaGravacaoDesfazDadosTagsEModeloDaRecorrencia(boolean arrecadacao) throws SQLException {
        cadastrarEvento("campanha");
        var repeticao = Repeticao.criar(Frequencia.MENSAL, hoje, LimiteRepeticao.PARA_SEMPRE, null, null);
        var original = registrar(arrecadacao, hoje, repeticao, "campanha");
        String tabela = arrecadacao ? "arrecadacao" : "despesa";
        try (var conexao = banco.abrirConexao(); var restricao = conexao.prepareStatement(
                "ALTER TABLE " + tabela + " ADD CONSTRAINT valor_teste CHECK (valor > 2)")) { restricao.execute(); }
        assertThrows(PersistenciaException.class, () -> alterar(arrecadacao, original,
                new AlteracaoAcao("Não gravar", BigDecimal.ONE, hoje, null, ""), true));
        assertEquals(original.getNome(), buscar(arrecadacao, id(original)).getNome());
        assertEquals(original.getTags(), buscar(arrecadacao, id(original)).getTags());
        try (var conexao = banco.abrirConexao(); var consulta = conexao.prepareStatement("SELECT COUNT(*) FROM modelo_repeticao");
             var registro = consulta.executeQuery()) {
            registro.next();
            assertEquals(0, registro.getInt(1));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void naoSobrescreveAlteracoesFeitasDepoisDaSelecao(boolean arrecadacao) {
        var selecionada = registrar(arrecadacao, hoje, null, null);
        alterar(arrecadacao, selecionada, pedido("Alterado em outra janela", hoje), false);
        assertThrows(RegraDeNegocioException.class,
                () -> alterar(arrecadacao, selecionada, pedido("Seleção desatualizada", hoje), false));
        assertEquals("Alterado em outra janela", buscar(arrecadacao, id(selecionada)).getNome());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void falhaAoTrocarVinculoRestauraDadosEVinculoAnterior(boolean arrecadacao) throws SQLException {
        cadastrarEvento("antigo");
        cadastrarEvento("novo");
        var repeticao = Repeticao.criar(Frequencia.MENSAL, hoje, LimiteRepeticao.PARA_SEMPRE, null, null);
        var original = registrar(arrecadacao, hoje, repeticao, "antigo");
        String tabelaTags = arrecadacao ? "arrecadacao_tag" : "despesa_tag";
        try (var conexao = banco.abrirConexao(); var restricao = conexao.prepareStatement(
                "ALTER TABLE " + tabelaTags + " ADD CONSTRAINT tag_teste CHECK (tag_nome <> 'novo')")) { restricao.execute(); }
        assertThrows(PersistenciaException.class, () -> alterar(arrecadacao, original,
                new AlteracaoAcao("Não gravar", new BigDecimal("20.00"), hoje.plusDays(1), "Bia", "novo"), true));
        Acao preservada = buscar(arrecadacao, id(original));
        assertEquals(original.getNome(), preservada.getNome());
        assertEquals(original.getValor(), preservada.getValor());
        assertEquals(original.getData(), preservada.getData());
        assertEquals(original.getTags(), preservada.getTags());
        if (preservada instanceof Arrecadacao entrada) { assertEquals("Ana", entrada.getNomeDoador()); }
    }

    @Test
    void reagruparDespesaCorrigidaPreservaOutraDoMesmoNomeEADoacaoFisica() {
        var primeira = (Despesa) registrar(false, hoje, null, null);
        var segunda = (Despesa) registrar(false, hoje, null, null);
        var produtos = new JdbcDoacaoProdutoRepository(banco);
        var doacao = br.edu.api.model.DoacaoDeProduto.criar("Arroz", br.edu.api.model.UnidadeMedida.KG,
                new BigDecimal("2.5"), hoje, null);
        produtos.registrar(doacao);
        var servico = new DespesaService(instituicao, despesas, AutorizacaoEventos.semSessao());
        assertEquals(new BigDecimal("20.00"), servico.listarPorMes(YearMonth.from(hoje)).get(0).total());
        servico.alterar(primeira, pedido("Água", hoje));
        var grupos = servico.listarPorMes(YearMonth.from(hoje));
        assertEquals(2, grupos.size());
        assertEquals(new BigDecimal("30.00"), grupos.stream().map(grupo -> grupo.total()).reduce(BigDecimal.ZERO, BigDecimal::add));
        assertEquals("Energia", despesas.buscarPorId(segunda.getId()).orElseThrow().getNome());
        assertEquals(doacao.getId(), produtos.listarPorMes(YearMonth.from(hoje)).get(0).getId());
        assertEquals(new BigDecimal("2.500000"), produtos.listarPorMes(YearMonth.from(hoje)).get(0).getQuantidade());
    }

    @Test
    void informaQuandoOIdentificadorNaoExisteSemCriarRegistros() {
        assertThrows(RegraDeNegocioException.class, () -> new DespesaService(instituicao, despesas, AutorizacaoEventos.semSessao())
                .alterar(UUID.randomUUID(), pedido("Nome", hoje)));
        assertThrows(RegraDeNegocioException.class, () -> new ArrecadacaoService(instituicao, arrecadacoes, AutorizacaoEventos.semSessao())
                .alterar(UUID.randomUUID(), pedido("Nome", hoje)));
        assertTrue(despesas.listarPorMes(YearMonth.from(hoje)).isEmpty());
        assertTrue(arrecadacoes.listarPorMes(YearMonth.from(hoje)).isEmpty());
    }

    private Acao registrar(boolean arrecadacao, LocalDate data, Repeticao repeticao, String tag) {
        if (arrecadacao) {
            var acao = Arrecadacao.criar(new BigDecimal("10.00"), "Contribuição", data, "Ana");
            acao.configurarRepeticao(repeticao);
            if (tag != null) { acao.associarTag(new Tag(tag)); }
            arrecadacoes.registrar(acao, hoje);
            return acao;
        }
        var acao = Despesa.criar(new BigDecimal("10.00"), "Energia", data);
        acao.configurarRepeticao(repeticao);
        if (tag != null) { acao.associarTag(new Tag(tag)); }
        despesas.registrar(acao, hoje);
        return acao;
    }

    private Acao alterar(boolean arrecadacao, Acao original, AlteracaoAcao pedido, boolean autorizada) {
        AutorizacaoEventos autorizacao = autorizada ? () -> { } : AutorizacaoEventos.semSessao();
        return arrecadacao ? new ArrecadacaoService(instituicao, arrecadacoes, autorizacao).alterar((Arrecadacao) original, pedido)
                : new DespesaService(instituicao, despesas, autorizacao).alterar((Despesa) original, pedido);
    }

    private Acao buscar(boolean arrecadacao, UUID id) {
        return arrecadacao ? arrecadacoes.buscarPorId(id).orElseThrow() : despesas.buscarPorId(id).orElseThrow();
    }
    private List<? extends Acao> listar(boolean arrecadacao, YearMonth mes) {
        return arrecadacao ? arrecadacoes.listarPorMes(mes) : despesas.listarPorMes(mes);
    }
    private UUID id(Acao acao) { return acao instanceof Despesa despesa ? despesa.getId() : ((Arrecadacao) acao).getId(); }
    private Repeticao repeticao(Acao acao) { return acao instanceof Despesa despesa ? despesa.getRepeticao() : ((Arrecadacao) acao).getRepeticao(); }
    private int numero(Acao acao) { return acao instanceof Despesa despesa ? despesa.getNumeroOcorrencia() : ((Arrecadacao) acao).getNumeroOcorrencia(); }
    private AlteracaoAcao pedido(String nome, LocalDate data) { return new AlteracaoAcao(nome, new BigDecimal("20.00"), data, "Ana", null); }

    private void cadastrarEvento(String tag) throws SQLException {
        try (var conexao = banco.abrirConexao(); var inserirTag = conexao.prepareStatement("INSERT INTO tag (nome) VALUES (?)");
             var inserirEvento = conexao.prepareStatement("""
                     INSERT INTO evento (id, nome, tag_nome, status, data_inicio, data_termino)
                     VALUES (?, 'Evento de teste', ?, 'planejado', ?, ?)
                     """)) {
            inserirTag.setString(1, tag);
            inserirTag.executeUpdate();
            inserirEvento.setObject(1, UUID.randomUUID());
            inserirEvento.setString(2, tag);
            inserirEvento.setObject(3, hoje);
            inserirEvento.setObject(4, hoje.plusMonths(1));
            inserirEvento.executeUpdate();
        }
    }
}
