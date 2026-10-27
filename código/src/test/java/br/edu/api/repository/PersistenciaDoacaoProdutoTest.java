package br.edu.api.repository;

import br.edu.api.model.DoacaoDeProduto;
import br.edu.api.model.Instituicao;
import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.model.UnidadeMedida;
import br.edu.api.service.DoacaoProdutoService;
import br.edu.api.service.RegistroDoacaoProduto;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;

class PersistenciaDoacaoProdutoTest {
    @TempDir Path pasta;
    private BancoDeDados banco;
    private JdbcDoacaoProdutoRepository repositorio;
    private DoacaoProdutoService servico;
    private final LocalDate hoje = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void prepararBancoIsolado() {
        banco = new BancoDeDados(pasta);
        banco.inicializar();
        repositorio = new JdbcDoacaoProdutoRepository(banco);
        ZoneId fuso = ZoneId.of("America/Sao_Paulo");
        Clock relogio = Clock.fixed(hoje.atStartOfDay(fuso).toInstant(), fuso);
        servico = new DoacaoProdutoService(new Instituicao(relogio), repositorio);
    }

    @ParameterizedTest
    @EnumSource(UnidadeMedida.class)
    void reabreArquivoPreservandoQuantidadeExataEDadosComplementares(UnidadeMedida unidade) {
        var original = servico.registrar(new RegistroDoacaoProduto("  Produto doado  ", unidade,
                new BigDecimal("12.345678"), hoje, "  João d'Ávila  ", hoje.plusMonths(3), hoje.plusYears(2)));
        BancoDeDados reaberto = new BancoDeDados(pasta);
        reaberto.inicializar();
        var doacao = new JdbcDoacaoProdutoRepository(reaberto).listarPorMes(YearMonth.from(hoje)).get(0);
        assertEquals(original.getId(), doacao.getId());
        assertEquals("Produto doado", doacao.getNomeProduto());
        assertEquals(unidade, doacao.getUnidadeMedida());
        assertEquals(new BigDecimal("12.345678"), doacao.getQuantidade());
        assertEquals(hoje, doacao.getData());
        assertEquals("João d'Ávila", doacao.getNomeDoador());
        assertEquals(hoje.plusMonths(3), doacao.getPrazoValidade());
        assertEquals(hoje.plusYears(2), doacao.getPrazoGarantia());
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    void naoExigeDoadorValidadeOuGarantia(String doador) {
        servico.registrar(new RegistroDoacaoProduto("Cadeira", UnidadeMedida.UNIDADE,
                BigDecimal.ONE, hoje, doador, null, null));
        var doacao = servico.listarPorMes(YearMonth.from(hoje)).get(0);
        assertNull(doacao.getNomeDoador());
        assertNull(doacao.getPrazoValidade());
        assertNull(doacao.getPrazoGarantia());
    }

    @Test
    void aceitaValidadeEGarantiaSeparadamenteSemAplicarAJanelaDoLancamentoAosPrazos() {
        LocalDate validade = hoje.minusYears(1);
        LocalDate garantia = hoje.plusYears(10);
        servico.registrar(new RegistroDoacaoProduto("Alimento", UnidadeMedida.KG,
                BigDecimal.ONE, hoje, null, validade, null));
        servico.registrar(new RegistroDoacaoProduto("Equipamento", UnidadeMedida.UNIDADE,
                BigDecimal.ONE, hoje, null, null, garantia));
        var doacoes = servico.listarPorMes(YearMonth.from(hoje));
        assertEquals(validade, doacoes.get(0).getPrazoValidade());
        assertNull(doacoes.get(0).getPrazoGarantia());
        assertNull(doacoes.get(1).getPrazoValidade());
        assertEquals(garantia, doacoes.get(1).getPrazoGarantia());
    }

    @Test
    void recusaDadosObrigatoriosInvalidosAntesDeGravar() throws SQLException {
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido(" ", UnidadeMedida.KG, BigDecimal.ONE, hoje)));
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido(null, UnidadeMedida.KG, BigDecimal.ONE, hoje)));
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido("Arroz", null, BigDecimal.ONE, hoje)));
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido("Arroz", UnidadeMedida.KG, null, hoje)));
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido("Arroz", UnidadeMedida.KG, BigDecimal.ONE, null)));
        assertEquals(0, contar("doacao_produto"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.0000001", "10000000000000"})
    void recusaQuantidadeInvalidaOuQueExigiriaArredondamento(String quantidade) throws SQLException {
        assertThrows(RegraDeNegocioException.class,
                () -> servico.registrar(pedido("Arroz", UnidadeMedida.KG, new BigDecimal(quantidade), hoje)));
        assertEquals(0, contar("doacao_produto"));
    }

    @Test
    void aceitaExtremosDaQuantidadeSemPerderPrecisao() {
        servico.registrar(pedido("Mínima", UnidadeMedida.KG, new BigDecimal("0.000001"), hoje));
        servico.registrar(pedido("Máxima", UnidadeMedida.KG, new BigDecimal("9999999999999.999999"), hoje));
        var quantidades = servico.listarPorMes(YearMonth.from(hoje)).stream().map(DoacaoDeProduto::getQuantidade).toList();
        assertTrue(quantidades.contains(new BigDecimal("0.000001")));
        assertTrue(quantidades.contains(new BigDecimal("9999999999999.999999")));
    }

    @Test
    void aplicaJanelaDeDoisMesesAtrasACincoAnosAdianteComLimitesInclusivos() throws SQLException {
        LocalDate inicio = hoje.minusMonths(2);
        LocalDate fim = hoje.plusYears(5);
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido("Arroz", UnidadeMedida.KG,
                BigDecimal.ONE, inicio.minusDays(1))));
        assertThrows(RegraDeNegocioException.class, () -> servico.registrar(pedido("Arroz", UnidadeMedida.KG,
                BigDecimal.ONE, fim.plusDays(1))));
        assertEquals(0, contar("doacao_produto"));
        servico.registrar(pedido("Arroz", UnidadeMedida.KG, BigDecimal.ONE, inicio));
        servico.registrar(pedido("Arroz", UnidadeMedida.KG, BigDecimal.ONE, fim));
        assertEquals(inicio, servico.listarPorMes(YearMonth.from(inicio)).get(0).getData());
        assertEquals(fim, servico.listarPorMes(YearMonth.from(fim)).get(0).getData());
    }

    @Test
    void filtraMesEOrdenaPorDataENomeMantendoDoacoesDistintas() {
        servico.registrar(pedido("Arroz", UnidadeMedida.KG, BigDecimal.ONE, hoje.withDayOfMonth(1)));
        servico.registrar(pedido("Feijão", UnidadeMedida.KG, BigDecimal.ONE, hoje.withDayOfMonth(31)));
        servico.registrar(pedido("Arroz", UnidadeMedida.KG, BigDecimal.TEN, hoje.withDayOfMonth(31)));
        servico.registrar(pedido("Cadeira", UnidadeMedida.UNIDADE, BigDecimal.ONE, hoje.plusMonths(1).withDayOfMonth(1)));
        var outubro = servico.listarPorMes(YearMonth.from(hoje));
        assertEquals(List.of("Arroz", "Arroz", "Feijão"), outubro.stream().map(DoacaoDeProduto::getNomeProduto).toList());
        assertNotEquals(outubro.get(0).getId(), outubro.get(1).getId());
        assertEquals(1, servico.listarPorMes(YearMonth.from(hoje.plusMonths(1))).size());
        assertTrue(servico.listarPorMes(YearMonth.from(hoje.minusMonths(1))).isEmpty());
    }

    @Test
    void nomesComAspasSaoDadosENaoAlteramOsRegistrosFinanceiros() throws SQLException {
        String nome = "Caixa d'água'); DROP TABLE despesa; --";
        String doador = "Ana d'Oeste";
        servico.registrar(new RegistroDoacaoProduto(nome, UnidadeMedida.UNIDADE, BigDecimal.ONE, hoje, doador, null, null));
        var doacao = servico.listarPorMes(YearMonth.from(hoje)).get(0);
        assertEquals(nome, doacao.getNomeProduto());
        assertEquals(doador, doacao.getNomeDoador());
        for (String tabela : List.of("despesa", "arrecadacao", "repeticao", "evento", "tag")) {
            assertEquals(0, contar(tabela));
        }
    }

    @Test
    void falhaDePersistenciaNaoConfirmaNemGravaParcialmente() throws SQLException {
        try (var conexao = banco.abrirConexao(); var comando = conexao.prepareStatement(
                "ALTER TABLE doacao_produto ADD CONSTRAINT limite_teste CHECK (quantidade < 2)")) {
            comando.execute();
        }
        assertThrows(PersistenciaException.class, () -> servico.registrar(pedido("Arroz", UnidadeMedida.KG, BigDecimal.TEN, hoje)));
        assertEquals(0, contar("doacao_produto"));
        servico.registrar(pedido("Arroz", UnidadeMedida.KG, BigDecimal.ONE, hoje));
        assertEquals(1, contar("doacao_produto"));
    }

    @Test
    void bancoTambemProtegeUnidadeEQuantidade() throws SQLException {
        try (var conexao = banco.abrirConexao(); var comando = conexao.prepareStatement("""
                INSERT INTO doacao_produto (id, nome_produto, unidade_medida, quantidade, data)
                VALUES (RANDOM_UUID(), 'Produto', ?, ?, DATE '2026-10-22')
                """)) {
            comando.setString(1, "LITRO");
            comando.setBigDecimal(2, BigDecimal.ONE);
            assertThrows(SQLException.class, comando::executeUpdate);
            comando.setString(1, "KG");
            comando.setBigDecimal(2, BigDecimal.ZERO);
            assertThrows(SQLException.class, comando::executeUpdate);
        }
        assertEquals(0, contar("doacao_produto"));
    }

    private RegistroDoacaoProduto pedido(String nome, UnidadeMedida unidade, BigDecimal quantidade, LocalDate data) {
        return new RegistroDoacaoProduto(nome, unidade, quantidade, data, null, null, null);
    }

    private int contar(String tabela) throws SQLException {
        // Os nomes são constantes do teste, nunca uma entrada do usuário.
        try (var conexao = banco.abrirConexao(); var consulta = conexao.prepareStatement("SELECT COUNT(*) FROM " + tabela);
             var registro = consulta.executeQuery()) {
            registro.next();
            return registro.getInt(1);
        }
    }
}
