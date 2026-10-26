package br.edu.api.repository;

import br.edu.api.model.Despesa;
import br.edu.api.model.Arrecadacao;
import br.edu.api.model.Frequencia;
import br.edu.api.model.Instituicao;
import br.edu.api.model.LimiteRepeticao;
import br.edu.api.model.Repeticao;
import br.edu.api.model.UnidadeMedida;
import br.edu.api.service.DoacaoProdutoService;
import br.edu.api.service.RegistroDoacaoProduto;
import br.edu.api.service.AlteracaoAcao;
import br.edu.api.service.DespesaService;
import br.edu.api.service.ArrecadacaoService;
import br.edu.api.service.AutorizacaoEventos;
import br.edu.api.service.RegistroArrecadacao;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class AtualizacaoBancoTest {
    @TempDir Path pasta;

    @Test
    void atualizaBancoDoUc3EPreservaOsTresCadastrosAoEditarOriginalPermanente() throws Exception {
        BancoDeDados banco = new BancoDeDados(pasta);
        try (var arquivo = getClass().getResourceAsStream("/banco/schema-uc3.sql"); var conexao = banco.abrirConexao()) {
            assertNotNull(arquivo);
            for (String sql : new String(arquivo.readAllBytes(), StandardCharsets.UTF_8).split(";")) {
                if (!sql.isBlank()) {
                    try (var comando = conexao.prepareStatement(sql)) { comando.execute(); }
                }
            }
        }
        LocalDate hoje = LocalDate.of(2026, 10, 22);
        var despesas = new JdbcDespesaRepository(banco);
        var arrecadacoes = new JdbcArrecadacaoRepository(banco);
        var produtos = new JdbcDoacaoProdutoRepository(banco);
        var despesa = Despesa.criar(new BigDecimal("100.00"), "Energia", hoje);
        despesa.configurarRepeticao(Repeticao.criar(Frequencia.MENSAL, hoje, LimiteRepeticao.PARA_SEMPRE, null, null));
        despesas.registrar(despesa, hoje);
        var entrada = Arrecadacao.criar(new BigDecimal("500.00"), "Contribuição", hoje, "Ana");
        arrecadacoes.registrar(entrada, hoje);
        var produto = br.edu.api.model.DoacaoDeProduto.criar("Arroz", UnidadeMedida.KG, BigDecimal.TEN, hoje, null);
        produtos.registrar(produto);

        banco.inicializar();
        ZoneId fuso = ZoneId.of("America/Sao_Paulo");
        var instituicao = new Instituicao(Clock.fixed(hoje.atStartOfDay(fuso).toInstant(), fuso));
        new DespesaService(instituicao, despesas, AutorizacaoEventos.semSessao()).alterar(despesa.getId(),
                new AlteracaoAcao("Energia corrigida", new BigDecimal("150.00"), hoje, null, null));
        banco.inicializar();
        assertEquals("Energia corrigida", despesas.buscarPorId(despesa.getId()).orElseThrow().getNome());
        assertEquals("Ana", arrecadacoes.buscarPorId(entrada.getId()).orElseThrow().getNomeDoador());
        assertEquals(produto.getId(), produtos.listarPorMes(YearMonth.from(hoje)).get(0).getId());
        assertEquals(1, despesas.atualizarRepeticoesPermanentes(LocalDate.of(2026, 11, 22)));
        assertEquals(new BigDecimal("100.00"), despesas.listarPorMes(YearMonth.of(2031, 11)).get(0).getValor());
    }

    @Test
    void ampliaBancoDoUc2PreservandoDespesasArrecadacoesEDoadores() throws Exception {
        BancoDeDados banco = new BancoDeDados(pasta);
        try (var arquivo = getClass().getResourceAsStream("/banco/schema-uc2.sql");
             var conexao = banco.abrirConexao()) {
            assertNotNull(arquivo);
            String esquemaAnterior = new String(arquivo.readAllBytes(), StandardCharsets.UTF_8);
            for (String sql : esquemaAnterior.split(";")) {
                if (!sql.isBlank()) {
                    try (var comando = conexao.prepareStatement(sql)) { comando.execute(); }
                }
            }
        }
        LocalDate hoje = LocalDate.of(2026, 10, 22);
        var despesas = new JdbcDespesaRepository(banco);
        var arrecadacoes = new JdbcArrecadacaoRepository(banco);
        var despesa = Despesa.criar(new BigDecimal("100.00"), "Energia", hoje);
        var arrecadacao = Arrecadacao.criar(new BigDecimal("500.00"), "Contribuição", hoje, "Ana");
        despesa.configurarRepeticao(Repeticao.criar(Frequencia.MENSAL, hoje, LimiteRepeticao.QUANTIDADE, 1, null));
        arrecadacao.configurarRepeticao(Repeticao.criar(Frequencia.MENSAL, hoje, LimiteRepeticao.QUANTIDADE, 1, null));
        despesas.registrar(despesa, hoje);
        arrecadacoes.registrar(arrecadacao, hoje);

        banco.inicializar();
        ZoneId fuso = ZoneId.of("America/Sao_Paulo");
        Clock relogio = Clock.fixed(hoje.atStartOfDay(fuso).toInstant(), fuso);
        var produtos = new DoacaoProdutoService(new Instituicao(relogio), new JdbcDoacaoProdutoRepository(banco));
        var doacao = produtos.registrar(new RegistroDoacaoProduto("Arroz", UnidadeMedida.KG,
                new BigDecimal("2.5"), hoje, null, hoje.plusYears(1), null));
        banco.inicializar();
        assertEquals(doacao.getId(), produtos.listarPorMes(YearMonth.from(hoje)).get(0).getId());
        assertEquals(despesa.getId(), despesas.listarPorMes(YearMonth.from(hoje)).get(0).getId());
        assertEquals(arrecadacao.getId(), arrecadacoes.listarPorMes(YearMonth.from(hoje)).get(0).getId());
        var proximoMes = YearMonth.from(hoje.plusMonths(1));
        assertEquals(despesa.getRepeticao().id(), despesas.listarPorMes(proximoMes).get(0).getRepeticao().id());
        assertEquals(arrecadacao.getRepeticao().id(), arrecadacoes.listarPorMes(proximoMes).get(0).getRepeticao().id());
        assertEquals("Ana", arrecadacoes.listarPorMes(proximoMes).get(0).getNomeDoador());
        assertEquals(new BigDecimal("500.00"), arrecadacoes.listarPorMes(proximoMes).get(0).getValor());
    }

    @Test
    void ampliaBancoDoUc1SemPerderDespesasOuRecorrencias() throws Exception {
        BancoDeDados banco = new BancoDeDados(pasta);
        // Reproduz um arquivo criado pela versão anterior, sem as tabelas do UC02.
        try (var arquivo = getClass().getResourceAsStream("/banco/schema-uc1.sql");
             var conexao = banco.abrirConexao()) {
            assertNotNull(arquivo);
            String esquemaAnterior = new String(arquivo.readAllBytes(), StandardCharsets.UTF_8);
            for (String sql : esquemaAnterior.split(";")) {
                if (!sql.isBlank()) {
                    try (var comando = conexao.prepareStatement(sql)) { comando.execute(); }
                }
            }
        }
        LocalDate hoje = LocalDate.of(2026, 10, 22);
        LocalDate referencia = LocalDate.of(2026, 10, 31);
        JdbcDespesaRepository despesas = new JdbcDespesaRepository(banco);
        Despesa original = Despesa.criar(new BigDecimal("125.50"), "Energia", referencia);
        original.configurarRepeticao(Repeticao.criar(Frequencia.MENSAL, referencia,
                LimiteRepeticao.PARA_SEMPRE, null, null));
        despesas.registrar(original, hoje);

        banco.inicializar();
        banco.inicializar(); // A inicialização também deve funcionar nas próximas aberturas.
        Despesa preservada = despesas.listarPorMes(YearMonth.from(hoje)).get(0);
        assertEquals(original.getId(), preservada.getId());
        assertEquals(new BigDecimal("125.50"), preservada.getValor());
        assertEquals(original.getRepeticao().id(), preservada.getRepeticao().id());

        ZoneId fuso = ZoneId.of("America/Sao_Paulo");
        Clock relogio = Clock.fixed(hoje.atStartOfDay(fuso).toInstant(), fuso);
        var arrecadacoes = new ArrecadacaoService(new Instituicao(relogio),
                new JdbcArrecadacaoRepository(banco), AutorizacaoEventos.semSessao());
        arrecadacoes.registrar(new RegistroArrecadacao("Contribuição", new BigDecimal("500.00"),
                hoje, "Ana", null, ""));
        assertEquals("Ana", arrecadacoes.listarPorMes(YearMonth.from(hoje)).get(0).getNomeDoador());
        assertEquals(1, despesas.atualizarRepeticoesPermanentes(LocalDate.of(2026, 11, 1)));
        assertEquals(original.getId(), despesas.listarPorMes(YearMonth.from(hoje)).get(0).getId());
    }
}
