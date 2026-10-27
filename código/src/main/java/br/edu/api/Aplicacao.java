package br.edu.api;

import br.edu.api.controller.DespesaController;
import br.edu.api.controller.ArrecadacaoController;
import br.edu.api.controller.DoacaoProdutoController;
import br.edu.api.model.Instituicao;
import br.edu.api.repository.BancoDeDados;
import br.edu.api.repository.JdbcDespesaRepository;
import br.edu.api.repository.JdbcArrecadacaoRepository;
import br.edu.api.repository.JdbcDoacaoProdutoRepository;
import br.edu.api.service.AutorizacaoEventos;
import br.edu.api.service.DespesaService;
import br.edu.api.service.ArrecadacaoService;
import br.edu.api.service.DoacaoProdutoService;
import br.edu.api.view.DespesaView;
import br.edu.api.view.ArrecadacaoView;
import br.edu.api.view.DoacaoProdutoView;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.JFrame;
import javax.swing.JTabbedPane;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

public final class Aplicacao {
    private static final Logger LOG = Logger.getLogger(Aplicacao.class.getName());

    private Aplicacao() { }

    public static void main(String[] argumentos) {
        try {
            BancoDeDados banco = new BancoDeDados(diretorioDados());
            banco.inicializar();
            Instituicao instituicao = new Instituicao(Clock.systemDefaultZone());
            DespesaService servico = new DespesaService(instituicao,
                    new JdbcDespesaRepository(banco), AutorizacaoEventos.semSessao());
            ArrecadacaoService arrecadacoes = new ArrecadacaoService(instituicao,
                    new JdbcArrecadacaoRepository(banco), AutorizacaoEventos.semSessao());
            DoacaoProdutoService doacoes = new DoacaoProdutoService(instituicao, new JdbcDoacaoProdutoRepository(banco));
            SwingUtilities.invokeLater(() -> abrirJanela(instituicao, servico, arrecadacoes, doacoes));
        } catch (Exception erro) {
            LOG.log(Level.SEVERE, "Falha ao iniciar a aplicação", erro);
            SwingUtilities.invokeLater(() -> JOptionPane.showMessageDialog(null,
                    "Não foi possível abrir o banco local. Confira a permissão de gravação na pasta dos dados.",
                    "Não foi possível iniciar", JOptionPane.ERROR_MESSAGE));
        }
    }

    public static Path diretorioDados() throws Exception {
        String personalizado = System.getProperty("api.dados");
        if (personalizado != null && !personalizado.isBlank()) { return Path.of(personalizado); }
        Path origem = Path.of(Aplicacao.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        return Files.isRegularFile(origem) ? origem.getParent().resolve("dados") : Path.of("dados");
    }

    private static void abrirJanela(Instituicao instituicao, DespesaService servico,
                                    ArrecadacaoService arrecadacoes, DoacaoProdutoService doacoes) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception erro) {
            LOG.log(Level.FINE, "Usando o tema padrão do Swing", erro);
        }
        DespesaView tela = new DespesaView(instituicao.hoje());
        DespesaController controle = new DespesaController(tela, servico, instituicao);
        ArrecadacaoView telaArrecadacoes = new ArrecadacaoView(instituicao.hoje());
        ArrecadacaoController controleArrecadacoes = new ArrecadacaoController(telaArrecadacoes, arrecadacoes, instituicao);
        DoacaoProdutoView telaDoacoes = new DoacaoProdutoView(instituicao.hoje());
        DoacaoProdutoController controleDoacoes = new DoacaoProdutoController(telaDoacoes, doacoes);
        JTabbedPane abas = new JTabbedPane();
        abas.addTab("Despesas", tela);
        abas.addTab("Arrecadações", telaArrecadacoes);
        abas.addTab("Doações de produtos", telaDoacoes);
        JFrame janela = new JFrame("API — Registros da instituição");
        janela.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        janela.setContentPane(abas);
        janela.setMinimumSize(new Dimension(1100, 820));
        janela.setSize(1180, 860);
        janela.setLocationRelativeTo(null);
        janela.addWindowListener(new WindowAdapter() {
            @Override public void windowClosing(WindowEvent evento) {
                controle.encerrar();
                controleArrecadacoes.encerrar();
            }
        });
        janela.setVisible(true);
        controle.iniciar();
        controleArrecadacoes.iniciar();
        controleDoacoes.iniciar();
    }
}
