package br.edu.api.view;

import br.edu.api.model.Arrecadacao;
import br.edu.api.model.Despesa;
import br.edu.api.model.Frequencia;
import br.edu.api.model.LimiteRepeticao;
import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.model.Repeticao;
import br.edu.api.service.GrupoDespesa;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AlteracaoAcaoViewTest {
    private final LocalDate hoje = LocalDate.of(2026, 10, 22);

    @Test
    void editaUmLancamentoIndividualDoGrupoDeDespesasEPermiteCancelar() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var tela = new DespesaView(hoje);
            var primeira = Despesa.criar(new BigDecimal("10.00"), "Energia", hoje);
            var segunda = Despesa.criar(new BigDecimal("20.00"), "Energia", hoje.plusDays(1));
            segunda.configurarRepeticao(Repeticao.criar(Frequencia.SEMANAL, hoje, LimiteRepeticao.QUANTIDADE, 3, null));
            var grupo = new GrupoDespesa("Energia", List.of(primeira, segunda));
            tela.mostrarGrupos(List.of(grupo));
            tela.mostrarLancamentos(grupo);
            assertFalse(botao(tela, "Editar lançamento selecionado").isEnabled());
            JTable tabela = (JTable) encontrarNome(tela, "lancamentosDespesas");
            tabela.setRowSelectionInterval(1, 1);
            assertEquals(segunda.getId(), tela.lancamentoSelecionado().getId());
            tela.aoEditar(() -> tela.iniciarEdicao(tela.lancamentoSelecionado()));
            tela.aoCancelarEdicao(tela::limparFormulario);
            botao(tela, "Editar lançamento selecionado").doClick();
            assertEquals("Energia", campo(tela, "nome").getText());
            assertEquals("20.00", campo(tela, "valor").getText());
            assertEquals("23/10/2026", campo(tela, "data").getText());
            assertFalse(encontrarTipo(tela, JCheckBox.class).isEnabled());
            assertFalse(campo(tela, "dataCobranca").isEnabled());
            assertEquals("3", campo(tela, "quantidade").getText());
            assertNull(tela.lerAlteracao().tagEvento());
            assertTrue(botao(tela, "Salvar alterações").isEnabled());
            campo(tela, "valor").setText("20,00");
            assertThrows(RegraDeNegocioException.class, () -> tela.lerAlteracao().paraAlteracao());
            assertEquals("20,00", campo(tela, "valor").getText());
            tela.definirOcupada(true, "Salvando...");
            assertFalse(botao(tela, "Cancelar edição").isEnabled());
            assertFalse(botao(tela, "Salvar alterações").isEnabled());
            tela.definirOcupada(false, null);
            botao(tela, "Cancelar edição").doClick();
            assertEquals("", campo(tela, "nome").getText());
            assertTrue(botao(tela, "Registrar despesa").isEnabled());
            assertTrue(encontrarTipo(tela, JCheckBox.class).isEnabled());
            assertFalse(botao(tela, "Cancelar edição").isVisible());
        });
    }

    @Test
    void preencheDadosDaArrecadacaoExibeDoadorEConservaEdicaoDuranteFalha() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var tela = new ArrecadacaoView(hoje);
            var original = Arrecadacao.criar(new BigDecimal("150.00"), "Contribuição", hoje, "Ana Souza");
            tela.mostrarArrecadacoes(List.of(original));
            JTable tabela = (JTable) encontrarNome(tela, "arrecadacoes");
            tabela.setRowSelectionInterval(0, 0);
            tela.aoEditar(() -> tela.iniciarEdicao(tela.lancamentoSelecionado()));
            tela.aoCancelarEdicao(tela::limparFormulario);
            botao(tela, "Editar lançamento selecionado").doClick();
            assertEquals(original.getId(), tela.lancamentoSelecionado().getId());
            assertEquals("150.00", campo(tela, "valor").getText());
            assertEquals("Ana Souza", campo(tela, "nomeDoador").getText());
            assertFalse(campo(tela, "tagEvento").isEnabled());
            assertFalse(encontrarTipo(tela, JCheckBox.class).isEnabled());
            campo(tela, "nomeDoador").setText("");
            assertEquals("", tela.lerAlteracao().paraAlteracao().nomeDoador());
            campo(tela, "nomeDoador").setText("Bia Souza");
            campo(tela, "data").setText("31/02/2026");
            assertThrows(RegraDeNegocioException.class, () -> tela.lerAlteracao().paraAlteracao());
            tela.definirOcupada(true, "Salvando...");
            tela.definirOcupada(false, null);
            assertEquals("Bia Souza", campo(tela, "nomeDoador").getText());
            assertTrue(botao(tela, "Salvar alterações").isEnabled());
            assertFalse(botao(tela, "Editar lançamento selecionado").isEnabled());
            campo(tela, "data").setText("01/11/2026");
            campo(tela, "nome").setText("Contribuição corrigida");
            renderizar(tela);
            botao(tela, "Cancelar edição").doClick();
            assertEquals("", campo(tela, "nomeDoador").getText());
            assertTrue(botao(tela, "Registrar arrecadação").isEnabled());
        });
    }

    private void renderizar(ArrecadacaoView tela) {
        try {
            var abas = new JTabbedPane();
            abas.addTab("Despesas", new DespesaView(hoje));
            abas.addTab("Arrecadações", tela);
            abas.addTab("Doações de produtos", new DoacaoProdutoView(hoje));
            abas.setSelectedIndex(1);
            abas.setSize(1132, 820);
            organizar(abas);
            var imagem = new BufferedImage(abas.getWidth(), abas.getHeight(), BufferedImage.TYPE_INT_RGB);
            var desenho = imagem.createGraphics();
            desenho.setColor(abas.getBackground());
            desenho.fillRect(0, 0, imagem.getWidth(), imagem.getHeight());
            abas.paint(desenho);
            desenho.dispose();
            Path destino = Path.of("target", "previa-uc7.png");
            Files.createDirectories(destino.getParent());
            ImageIO.write(imagem, "png", destino.toFile());
        } catch (Exception erro) { throw new RuntimeException(erro); }
    }

    private JTextField campo(Container raiz, String nome) { return (JTextField) encontrarNome(raiz, nome); }
    private Component encontrarNome(Container raiz, String nome) {
        for (Component componente : raiz.getComponents()) {
            if (nome.equals(componente.getName())) { return componente; }
            if (componente instanceof Container filho) {
                Component encontrado = encontrarNome(filho, nome);
                if (encontrado != null) { return encontrado; }
            }
        }
        return null;
    }
    private JButton botao(Container raiz, String texto) {
        for (Component componente : raiz.getComponents()) {
            if (componente instanceof JButton botao && texto.equals(botao.getText())) { return botao; }
            if (componente instanceof Container filho) {
                JButton encontrado = botao(filho, texto);
                if (encontrado != null) { return encontrado; }
            }
        }
        return null;
    }
    private <T extends Component> T encontrarTipo(Container raiz, Class<T> tipo) {
        for (Component componente : raiz.getComponents()) {
            if (tipo.isInstance(componente)) { return tipo.cast(componente); }
            if (componente instanceof Container filho) {
                T encontrado = encontrarTipo(filho, tipo);
                if (encontrado != null) { return encontrado; }
            }
        }
        return null;
    }
    private void organizar(Container painel) {
        painel.doLayout();
        for (Component componente : painel.getComponents()) {
            if (componente instanceof Container filho) { organizar(filho); }
        }
    }
}
