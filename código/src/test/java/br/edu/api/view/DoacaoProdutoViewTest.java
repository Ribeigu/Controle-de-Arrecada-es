package br.edu.api.view;

import br.edu.api.model.DoacaoDeProduto;
import br.edu.api.model.UnidadeMedida;
import java.awt.Component;
import java.awt.Container;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.imageio.ImageIO;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DoacaoProdutoViewTest {
    @Test
    void permitePreencherLimparEConsultarDoacoesComPrazosOpcionais() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                LocalDate hoje = LocalDate.of(2026, 10, 22);
                var tela = new DoacaoProdutoView(hoje);
                assertEquals("22/10/2026", tela.lerFormulario().data());
                assertEquals("", tela.lerFormulario().prazoValidade());
                assertEquals("", tela.lerFormulario().prazoGarantia());
                campo(tela, "nomeProduto").setText("Arroz");
                campo(tela, "quantidade").setText("2,5");
                campo(tela, "nomeDoador").setText("Ana");
                campo(tela, "prazoValidade").setText("31/12/2027");
                campo(tela, "prazoGarantia").setText("22/10/2028");
                JComboBox<?> unidade = (JComboBox<?>) encontrarNome(tela, "unidadeMedida");
                unidade.setSelectedItem(UnidadeMedida.KG);
                assertEquals(UnidadeMedida.KG, tela.lerFormulario().unidadeMedida());
                assertEquals("Ana", tela.lerFormulario().nomeDoador());
                assertEquals(LocalDate.of(2027, 12, 31), tela.lerFormulario().paraRegistro().prazoValidade());
                tela.definirOcupada(true, "Registrando...");
                assertFalse(campo(tela, "nomeProduto").isEnabled());
                assertFalse(campo(tela, "prazoValidade").isEnabled());
                assertFalse(unidade.isEnabled());
                tela.definirOcupada(false, null);
                limpar(tela);
                assertEquals("", tela.lerFormulario().nomeProduto());
                assertEquals("", tela.lerFormulario().quantidade());
                assertEquals("", tela.lerFormulario().nomeDoador());
                assertEquals("", tela.lerFormulario().prazoValidade());
                assertEquals("", tela.lerFormulario().prazoGarantia());
                assertEquals(UnidadeMedida.UNIDADE, tela.lerFormulario().unidadeMedida());

                var atualizacoes = new AtomicInteger();
                tela.aoAtualizar(atualizacoes::incrementAndGet);
                tela.selecionarMes(YearMonth.of(2027, 2));
                assertEquals(YearMonth.of(2027, 2), tela.mesSelecionado());
                assertEquals(0, atualizacoes.get());
                tela.selecionarMes(YearMonth.from(hoje));
                var alimento = DoacaoDeProduto.criar("Arroz", UnidadeMedida.KG, new BigDecimal("2.5"), hoje, "Ana Souza");
                alimento.definirValidadeOuGarantia(LocalDate.of(2027, 12, 31), null);
                var equipamento = DoacaoDeProduto.criar("Computador", UnidadeMedida.UNIDADE, BigDecimal.ONE, hoje, null);
                equipamento.definirValidadeOuGarantia(null, LocalDate.of(2028, 10, 22));
                tela.mostrarDoacoes(List.of(alimento, equipamento));
                JTable tabela = (JTable) encontrarNome(tela, "doacoesProdutos");
                assertEquals(2, tabela.getRowCount());
                assertEquals(7, tabela.getColumnCount());
                assertEquals("2,5", tabela.getValueAt(0, 2));
                assertEquals("kg", tabela.getValueAt(0, 3));
                assertEquals("Ana Souza", tabela.getValueAt(0, 4));
                assertEquals("31/12/2027", tabela.getValueAt(0, 5));
                assertEquals("", tabela.getValueAt(0, 6));
                assertNull(tabela.getValueAt(1, 4));
                assertEquals("", tabela.getValueAt(1, 5));
                assertEquals("22/10/2028", tabela.getValueAt(1, 6));
                assertFalse(tabela.isCellEditable(0, 0));

                var abas = new JTabbedPane();
                abas.addTab("Despesas", new DespesaView(hoje));
                abas.addTab("Arrecadações", new ArrecadacaoView(hoje));
                abas.addTab("Doações de produtos", tela);
                abas.setSelectedIndex(2);
                abas.setSize(1132, 820);
                organizar(abas);
                var previa = new BufferedImage(abas.getWidth(), abas.getHeight(), BufferedImage.TYPE_INT_RGB);
                var desenho = previa.createGraphics();
                desenho.setColor(abas.getBackground());
                desenho.fillRect(0, 0, previa.getWidth(), previa.getHeight());
                abas.paint(desenho);
                desenho.dispose();
                Path destino = Path.of("target", "previa-uc3.png");
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
