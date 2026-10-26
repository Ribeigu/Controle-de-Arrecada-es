package br.edu.api.view;

import br.edu.api.model.Arrecadacao;
import br.edu.api.model.LimiteRepeticao;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ArrecadacaoViewTest {
    @Test
    void habilitaRecorrenciaExibeDoadorELimpaFormularioNoPadraoDoUc1() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                LocalDate hoje = LocalDate.of(2026, 10, 22);
                ArrecadacaoView tela = new ArrecadacaoView(hoje);
                campo(tela, "nomeDoador").setText("Ana");
                assertEquals("Ana", tela.lerFormulario().nomeDoador());
                assertFalse(campo(tela, "quantidade").isEnabled());
                campo(tela, "data").setText("31/10/2026");
                encontrarTipo(tela, JCheckBox.class).doClick();
                assertEquals("31/10/2026", campo(tela, "dataEntrada").getText());
                assertTrue(campo(tela, "quantidade").isEnabled());
                JComboBox<?> limite = (JComboBox<?>) encontrarNome(tela, "limite");
                limite.setSelectedItem(LimiteRepeticao.DATA_LIMITE);
                assertFalse(campo(tela, "quantidade").isEnabled());
                assertTrue(campo(tela, "dataLimite").isEnabled());
                limite.setSelectedItem(LimiteRepeticao.PARA_SEMPRE);
                assertFalse(campo(tela, "quantidade").isEnabled());
                assertFalse(campo(tela, "dataLimite").isEnabled());
                tela.definirOcupada(true, "Registrando arrecadação...");
                assertFalse(campo(tela, "nomeDoador").isEnabled());
                assertFalse(campo(tela, "tagEvento").isEnabled());
                tela.definirOcupada(false, null);
                limpar(tela);
                assertEquals("", campo(tela, "nomeDoador").getText());
                assertFalse(tela.lerFormulario().repetir());
                assertFalse(campo(tela, "tagEvento").isEnabled());

                tela.selecionarMes(YearMonth.of(2027, 2));
                assertEquals(YearMonth.of(2027, 2), tela.mesSelecionado());
                tela.selecionarMes(YearMonth.from(hoje));
                tela.mostrarArrecadacoes(List.of(
                        Arrecadacao.criar(new BigDecimal("500.00"), "Contribuição mensal", hoje, "Ana Souza"),
                        Arrecadacao.criar(new BigDecimal("250.75"), "Doação espontânea", hoje, null)));
                JTable tabela = (JTable) encontrarNome(tela, "arrecadacoes");
                assertEquals(2, tabela.getRowCount());
                assertEquals("Ana Souza", tabela.getValueAt(0, 2));
                assertNull(tabela.getValueAt(1, 2));
                assertFalse(tabela.isCellEditable(0, 0));

                JTabbedPane abas = new JTabbedPane();
                abas.addTab("Despesas", new DespesaView(hoje));
                abas.addTab("Arrecadações", tela);
                abas.setSelectedIndex(1);
                abas.setSize(1132, 820);
                organizar(abas);
                BufferedImage previa = new BufferedImage(abas.getWidth(), abas.getHeight(), BufferedImage.TYPE_INT_RGB);
                var desenho = previa.createGraphics();
                abas.paint(desenho);
                desenho.dispose();
                Path destino = Path.of("target", "previa-uc2.png");
                Files.createDirectories(destino.getParent());
                ImageIO.write(previa, "png", destino.toFile());
            } catch (Exception erro) { throw new RuntimeException(erro); }
        });
    }

    private JTextField campo(Container raiz, String nome) {
        return (JTextField) encontrarNome(raiz, nome);
    }

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

    private void limpar(Container raiz) {
        for (Component componente : raiz.getComponents()) {
            if (componente instanceof JButton botao && botao.getText().equals("Limpar")) {
                botao.doClick();
            } else if (componente instanceof Container filho) { limpar(filho); }
        }
    }

    private void organizar(Container painel) {
        painel.doLayout();
        for (Component componente : painel.getComponents()) {
            if (componente instanceof Container filho) { organizar(filho); }
        }
    }
}
