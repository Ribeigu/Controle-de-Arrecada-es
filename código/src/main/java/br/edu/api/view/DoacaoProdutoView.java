package br.edu.api.view;

import br.edu.api.controller.EntradaDoacaoProduto;
import br.edu.api.model.DoacaoDeProduto;
import br.edu.api.model.UnidadeMedida;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SpinnerNumberModel;
import javax.swing.table.DefaultTableModel;

/** Cadastro e consulta mensal das doações físicas do UC03. */
public final class DoacaoProdutoView extends JPanel {
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private final NumberFormat formatoQuantidade = NumberFormat.getNumberInstance(Locale.forLanguageTag("pt-BR"));
    private final JTextField nomeProduto = campo("nomeProduto", 24);
    private final JComboBox<UnidadeMedida> unidadeMedida = new JComboBox<>(UnidadeMedida.values());
    private final JTextField quantidade = campo("quantidade", 12);
    private final JTextField data = campo("data", 12);
    private final JTextField nomeDoador = campo("nomeDoador", 24);
    private final JTextField prazoValidade = campo("prazoValidade", 12);
    private final JTextField prazoGarantia = campo("prazoGarantia", 12);
    private final JButton salvar = new JButton("Registrar doação");
    private final JButton limpar = new JButton("Limpar");
    private final JButton atualizar = new JButton("Atualizar");
    private final JSpinner mes;
    private final JSpinner ano;
    private final DefaultTableModel registros = new DefaultTableModel(
            new String[]{"Data", "Produto", "Quantidade", "Unidade", "Doador", "Validade", "Fim da garantia"}, 0) {
        @Override public boolean isCellEditable(int linha, int coluna) { return false; }
    };
    private final JTable tabela = new JTable(registros);
    private final JLabel resumoMes = new JLabel("Nenhuma doação neste mês.");
    private final JLabel situacao = new JLabel("Carregando doações de produtos...");
    private final LocalDate hoje;
    private boolean alterandoMes;

    public DoacaoProdutoView(LocalDate hoje) {
        super(new BorderLayout(24, 16));
        this.hoje = hoje;
        formatoQuantidade.setMaximumFractionDigits(6);
        mes = new JSpinner(new SpinnerNumberModel(hoje.getMonthValue(), 1, 12, 1));
        ano = new JSpinner(new SpinnerNumberModel(hoje.getYear(), 1, 9999, 1));
        ano.setEditor(new JSpinner.NumberEditor(ano, "0000"));
        unidadeMedida.setName("unidadeMedida");
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        JLabel titulo = new JLabel("Arrecadação por Instituição");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 23));
        add(titulo, BorderLayout.NORTH);
        add(montarFormulario(), BorderLayout.WEST);
        add(montarAcompanhamento(), BorderLayout.CENTER);
        add(situacao, BorderLayout.SOUTH);
        limpar.addActionListener(evento -> limparFormulario());
        limparFormulario();
    }

    private JPanel montarFormulario() {
        JPanel painel = new JPanel(new GridBagLayout());
        painel.setPreferredSize(new Dimension(360, 590));
        painel.setBorder(BorderFactory.createTitledBorder("Nova doação de produto"));
        int linha = 0;
        linha = adicionarCampo(painel, linha, "Nome do produto *", nomeProduto);
        linha = adicionarCampo(painel, linha, "Unidade de medida *", unidadeMedida);
        linha = adicionarCampo(painel, linha, "Quantidade *", quantidade);
        linha = adicionarTexto(painel, linha, "Ex.: 5 ou 1,25. Até seis casas decimais.");
        linha = adicionarCampo(painel, linha, "Data da doação * (DD/MM/AAAA)", data);
        linha = adicionarCampo(painel, linha, "Nome do doador (opcional)", nomeDoador);
        linha = adicionarCampo(painel, linha, "Data de validade (opcional)", prazoValidade);
        linha = adicionarCampo(painel, linha, "Data de fim da garantia (opcional)", prazoGarantia);
        linha = adicionarTexto(painel, linha, "Preencha as datas opcionais em DD/MM/AAAA.");
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
        botoes.add(salvar);
        botoes.add(new JLabel("   "));
        botoes.add(limpar);
        linha = adicionarComponente(painel, linha, botoes);
        GridBagConstraints espaco = restricoes(linha);
        espaco.weighty = 1;
        painel.add(new JPanel(), espaco);
        return painel;
    }

    private JPanel montarAcompanhamento() {
        JPanel painel = new JPanel(new BorderLayout(8, 12));
        painel.setBorder(BorderFactory.createTitledBorder("Doações de produtos do mês"));
        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filtros.add(new JLabel("Mês"));
        filtros.add(mes);
        filtros.add(new JLabel("Ano"));
        filtros.add(ano);
        filtros.add(atualizar);
        painel.add(filtros, BorderLayout.NORTH);
        tabela.setName("doacoesProdutos");
        tabela.setRowHeight(27);
        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
        tabela.getTableHeader().setReorderingAllowed(false);
        tabela.setFillsViewportHeight(true);
        int[] larguras = {95, 180, 105, 75, 150, 100, 120};
        for (int coluna = 0; coluna < larguras.length; coluna++) {
            tabela.getColumnModel().getColumn(coluna).setPreferredWidth(larguras[coluna]);
        }
        JScrollPane rolagem = new JScrollPane(tabela);
        rolagem.setColumnHeaderView(tabela.getTableHeader());
        painel.add(rolagem, BorderLayout.CENTER);
        resumoMes.setFont(resumoMes.getFont().deriveFont(Font.BOLD, 14));
        painel.add(resumoMes, BorderLayout.SOUTH);
        return painel;
    }

    public EntradaDoacaoProduto lerFormulario() {
        return new EntradaDoacaoProduto(nomeProduto.getText(), (UnidadeMedida) unidadeMedida.getSelectedItem(),
                quantidade.getText(), data.getText(), nomeDoador.getText(), prazoValidade.getText(), prazoGarantia.getText());
    }

    public YearMonth mesSelecionado() {
        return YearMonth.of((int) ano.getValue(), (int) mes.getValue());
    }

    public void selecionarMes(YearMonth periodo) {
        alterandoMes = true;
        try {
            mes.setValue(periodo.getMonthValue());
            ano.setValue(periodo.getYear());
        } finally { alterandoMes = false; }
    }

    public void mostrarDoacoes(List<DoacaoDeProduto> doacoes) {
        registros.setRowCount(0);
        for (DoacaoDeProduto doacao : doacoes) {
            registros.addRow(new Object[]{formatarData(doacao.getData()), doacao.getNomeProduto(),
                    formatoQuantidade.format(doacao.getQuantidade()), doacao.getUnidadeMedida().toString(),
                    doacao.getNomeDoador(), formatarData(doacao.getPrazoValidade()), formatarData(doacao.getPrazoGarantia())});
        }
        resumoMes.setText(doacoes.size() == 1 ? "1 doação neste mês." : doacoes.size() + " doações neste mês.");
        situacao.setText(doacoes.isEmpty() ? "Nenhuma doação de produto cadastrada neste mês." : "Doações de produtos atualizadas.");
    }

    public void limparFormulario() {
        nomeProduto.setText("");
        unidadeMedida.setSelectedItem(UnidadeMedida.UNIDADE);
        quantidade.setText("");
        data.setText(hoje.format(FORMATO_DATA));
        nomeDoador.setText("");
        prazoValidade.setText("");
        prazoGarantia.setText("");
        nomeProduto.requestFocusInWindow();
    }

    public void definirOcupada(boolean ocupada, String mensagem) {
        for (Component campo : new Component[]{nomeProduto, unidadeMedida, quantidade, data, nomeDoador,
                prazoValidade, prazoGarantia, salvar, limpar, atualizar, mes, ano}) {
            campo.setEnabled(!ocupada);
        }
        if (mensagem != null) { situacao.setText(mensagem); }
    }

    public void aoSalvar(Runnable acao) { salvar.addActionListener(evento -> acao.run()); }
    public void aoAtualizar(Runnable acao) {
        atualizar.addActionListener(evento -> acao.run());
        mes.addChangeListener(evento -> { if (!alterandoMes) { acao.run(); } });
        ano.addChangeListener(evento -> { if (!alterandoMes) { acao.run(); } });
    }
    public void mostrarErro(String mensagem) {
        situacao.setText(mensagem);
        JOptionPane.showMessageDialog(this, mensagem, "Confira os dados", JOptionPane.WARNING_MESSAGE);
    }
    public void mostrarSucesso() {
        situacao.setText("Doação de produto registrada.");
        JOptionPane.showMessageDialog(this, "Doação de produto registrada.", "Registro concluído", JOptionPane.INFORMATION_MESSAGE);
    }

    private static String formatarData(LocalDate data) { return data == null ? "" : data.format(FORMATO_DATA); }
    private static JTextField campo(String nome, int colunas) {
        JTextField campo = new JTextField(colunas);
        campo.setName(nome);
        return campo;
    }
    private int adicionarCampo(JPanel painel, int linha, String texto, Component componente) {
        linha = adicionarComponente(painel, linha, new JLabel(texto));
        return adicionarComponente(painel, linha, componente);
    }
    private int adicionarTexto(JPanel painel, int linha, String texto) {
        JLabel ajuda = new JLabel(texto);
        ajuda.setFont(ajuda.getFont().deriveFont(11f));
        ajuda.setForeground(new Color(85, 85, 85));
        return adicionarComponente(painel, linha, ajuda);
    }
    private int adicionarComponente(JPanel painel, int linha, Component componente) {
        painel.add(componente, restricoes(linha));
        return linha + 1;
    }
    private GridBagConstraints restricoes(int linha) {
        GridBagConstraints posicao = new GridBagConstraints();
        posicao.gridx = 0;
        posicao.gridy = linha;
        posicao.fill = GridBagConstraints.HORIZONTAL;
        posicao.weightx = 1;
        posicao.anchor = GridBagConstraints.NORTHWEST;
        posicao.insets = new Insets(2, 10, 2, 10);
        return posicao;
    }
}
