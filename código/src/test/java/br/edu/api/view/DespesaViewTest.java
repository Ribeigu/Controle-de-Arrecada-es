package br.edu.api.view;

import br.edu.api.model.Despesa;
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
import javax.swing.JCheckBox;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DespesaViewTest {
    @Test
    void telaRenderizaERepeticaoHabilitaSomenteCamposAplicaveis() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            try {
                LocalDate hoje = LocalDate.of(2026, 10, 22);
                DespesaView tela = new DespesaView(hoje);
                assertFalse(encontrar(tela, "quantidade").isEnabled());
                JCheckBox repetir = encontrarRepeticao(tela);
                repetir.doClick();
                assertTrue(encontrar(tela, "quantidade").isEnabled());
                assertFalse(encontrar(tela, "dataLimite").isEnabled());
                assertFalse(encontrar(tela, "tagEvento").isEnabled());
                tela.definirOcupada(true, "Registrando despesa...");
                assertFalse(encontrar(tela, "quantidade").isEnabled());
                tela.definirOcupada(false, null);
                tela.limparFormulario();

                tela.mostrarGrupos(List.of(
                        new GrupoDespesa("Energia", List.of(Despesa.criar(new BigDecimal("125.50"), "Energia", hoje))),
                        new GrupoDespesa("Material de limpeza", List.of(Despesa.criar(new BigDecimal("38.90"), "Material de limpeza", hoje)))));
                tela.mostrarLancamentos(new GrupoDespesa("Energia", List.of(Despesa.criar(new BigDecimal("125.50"), "Energia", hoje))));
                tela.setSize(1132, 820);
                organizar(tela);
                BufferedImage previa = new BufferedImage(tela.getWidth(), tela.getHeight(), BufferedImage.TYPE_INT_RGB);
                var desenho = previa.createGraphics();
                tela.paint(desenho);
                desenho.dispose();
                Path destino = Path.of("target", "previa-uc1.png");
                Files.createDirectories(destino.getParent());
                ImageIO.write(previa, "png", destino.toFile());
            } catch (Exception erro) {
                throw new RuntimeException(erro);
            }
        });
    }

    private JTextField encontrar(Container raiz, String nome) {
        for (Component componente : raiz.getComponents()) {
            if (componente instanceof JTextField campo && nome.equals(campo.getName())) { return campo; }
            if (componente instanceof Container filho) {
                JTextField encontrado = encontrar(filho, nome);
                if (encontrado != null) { return encontrado; }
            }
        }
        return null;
    }

    private JCheckBox encontrarRepeticao(Container raiz) {
        for (Component componente : raiz.getComponents()) {
            if (componente instanceof JCheckBox caixa) { return caixa; }
            if (componente instanceof Container filho) {
                JCheckBox encontrada = encontrarRepeticao(filho);
                if (encontrada != null) { return encontrada; }
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
