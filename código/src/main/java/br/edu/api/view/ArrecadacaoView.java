package br.edu.api.view;

import br.edu.api.controller.EntradaArrecadacao;
import br.edu.api.controller.EntradaAlteracaoAcao;
import br.edu.api.model.Arrecadacao;
import br.edu.api.model.Frequencia;
import br.edu.api.model.LimiteRepeticao;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
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

/** Formulário do UC02 e listagem das entradas recebidas ou previstas no mês. */
public final class ArrecadacaoView extends JPanel {
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
    private final JTextField nome = campo("nome", 24);
    private final JTextField valor = campo("valor", 12);
    private final JTextField data = campo("data", 12);
    private final JTextField nomeDoador = campo("nomeDoador", 24);
    private final JCheckBox repetir = new JCheckBox("Repetir esta arrecadação");
    private final JComboBox<Frequencia> frequencia = new JComboBox<>(Frequencia.values());
    private final JTextField dataEntrada = campo("dataEntrada", 12);
    private final JComboBox<LimiteRepeticao> limite = new JComboBox<>(LimiteRepeticao.values());
    private final JTextField quantidade = campo("quantidade", 8);
    private final JTextField dataLimite = campo("dataLimite", 12);
    private final JTextField tagEvento = campo("tagEvento", 16);
    private final JButton salvar = new JButton("Registrar arrecadação");
    private final JButton limpar = new JButton("Limpar");
    private final JButton atualizar = new JButton("Atualizar");
    private final JButton editar = new JButton("Editar lançamento selecionado");
    private final JButton cancelarEdicao = new JButton("Cancelar edição");
    private final JPanel formulario = new JPanel(new GridBagLayout());
    private final JSpinner mes;
    private final JSpinner ano;
    private final DefaultTableModel lancamentos = new DefaultTableModel(
            new String[]{"Data", "Arrecadação", "Doador", "Valor (R$)", "Tag do evento"}, 0) {
        @Override public boolean isCellEditable(int linha, int coluna) { return false; }
    };
    private final JTable tabela = new JTable(lancamentos);
    private final JLabel totalMes = new JLabel("Total de arrecadações do mês: R$ 0,00");
    private final JLabel situacao = new JLabel("Carregando arrecadações...");
    private final LocalDate hoje;
    private boolean ocupada;
    private boolean alterandoMes;
    private boolean editando;
    private List<Arrecadacao> arrecadacoesAtuais = List.of();

    public ArrecadacaoView(LocalDate hoje) {
        super(new BorderLayout(24, 16));
        this.hoje = hoje;
        mes = new JSpinner(new SpinnerNumberModel(hoje.getMonthValue(), 1, 12, 1));
        ano = new JSpinner(new SpinnerNumberModel(hoje.getYear(), 1, 9999, 1));
        ano.setEditor(new JSpinner.NumberEditor(ano, "0000"));
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        JLabel titulo = new JLabel("Arrecadação por Instituição");
        titulo.setFont(titulo.getFont().deriveFont(Font.BOLD, 23));
        add(titulo, BorderLayout.NORTH);
        add(montarFormulario(), BorderLayout.WEST);
        add(montarAcompanhamento(), BorderLayout.CENTER);
        add(situacao, BorderLayout.SOUTH);

        frequencia.setName("frequencia");
        limite.setName("limite");
        repetir.addActionListener(evento -> {
            if (repetir.isSelected()) { dataEntrada.setText(data.getText()); }
            atualizarCamposRepeticao();
        });
        limite.addActionListener(evento -> atualizarCamposRepeticao());
        limpar.addActionListener(evento -> limparFormulario());
        data.addActionListener(evento -> dataEntrada.setText(data.getText()));
        tabela.getSelectionModel().addListSelectionListener(evento -> atualizarBotaoEditar());
        limparFormulario();
    }

    private JPanel montarFormulario() {
        JPanel painel = formulario;
        painel.setPreferredSize(new Dimension(360, 650));
        painel.setBorder(BorderFactory.createTitledBorder("Nova arrecadação"));
        int linha = 0;
        linha = adicionarCampo(painel, linha, "Nome *", nome);
        linha = adicionarCampo(painel, linha, "Valor em reais *", valor);
        linha = adicionarTexto(painel, linha, "Use duas casas decimais. Ex.: 125.50");
        linha = adicionarCampo(painel, linha, "Data da arrecadação * (DD/MM/AAAA)", data);
        linha = adicionarCampo(painel, linha, "Nome do doador (opcional)", nomeDoador);
        linha = adicionarComponente(painel, linha, repetir);
        linha = adicionarCampo(painel, linha, "Frequência", frequencia);
        linha = adicionarCampo(painel, linha, "Data base da entrada (DD/MM/AAAA)", dataEntrada);
        linha = adicionarTexto(painel, linha, "A primeira repetição ocorre no intervalo seguinte.");
        linha = adicionarCampo(painel, linha, "Repetir", limite);
        linha = adicionarCampo(painel, linha, "Repetições após o lançamento inicial", quantidade);
        linha = adicionarCampo(painel, linha, "Data limite (DD/MM/AAAA)", dataLimite);
        linha = adicionarCampo(painel, linha, "Tag de evento (opcional)", tagEvento);
        tagEvento.setEnabled(false);
        tagEvento.setToolTipText("O vínculo a eventos exige uma sessão autenticada.");
        linha = adicionarTexto(painel, linha, "Eventos requerem autenticação.");
        JPanel botoes = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 8));
        botoes.add(salvar);
        botoes.add(new JLabel("   "));
        botoes.add(limpar);
        botoes.add(cancelarEdicao);
        linha = adicionarComponente(painel, linha, botoes);
        GridBagConstraints espaco = restricoes(linha);
        espaco.weighty = 1;
        painel.add(new JPanel(), espaco);
        return painel;
    }

    private JPanel montarAcompanhamento() {
        JPanel painel = new JPanel(new BorderLayout(8, 12));
        painel.setBorder(BorderFactory.createTitledBorder("Arrecadações do mês"));
        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filtros.add(new JLabel("Mês"));
        filtros.add(mes);
        filtros.add(new JLabel("Ano"));
        filtros.add(ano);
        filtros.add(atualizar);
        painel.add(filtros, BorderLayout.NORTH);
        tabela.setName("arrecadacoes");
        tabela.setRowHeight(27);
        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.getTableHeader().setReorderingAllowed(false);
        tabela.setFillsViewportHeight(true);
        int[] larguras = {95, 190, 150, 110, 130};
        for (int coluna = 0; coluna < larguras.length; coluna++) {
            tabela.getColumnModel().getColumn(coluna).setPreferredWidth(larguras[coluna]);
        }
        JScrollPane rolagem = new JScrollPane(tabela);
        rolagem.setColumnHeaderView(tabela.getTableHeader());
        painel.add(rolagem, BorderLayout.CENTER);
        totalMes.setFont(totalMes.getFont().deriveFont(Font.BOLD, 14));
        JPanel rodape = new JPanel(new BorderLayout(0, 8));
        rodape.add(totalMes, BorderLayout.NORTH);
        rodape.add(editar, BorderLayout.SOUTH);
        painel.add(rodape, BorderLayout.SOUTH);
        return painel;
    }

    public EntradaArrecadacao lerFormulario() {
        return new EntradaArrecadacao(nome.getText(), valor.getText(), data.getText(), nomeDoador.getText(),
                repetir.isSelected(), (Frequencia) frequencia.getSelectedItem(), dataEntrada.getText(),
                (LimiteRepeticao) limite.getSelectedItem(), quantidade.getText(), dataLimite.getText(), tagEvento.getText());
    }

    public EntradaAlteracaoAcao lerAlteracao() {
        return new EntradaAlteracaoAcao(nome.getText(), valor.getText(), data.getText(), nomeDoador.getText(), null);
    }

    public Arrecadacao lancamentoSelecionado() {
        int linha = tabela.getSelectedRow();
        return linha >= 0 && linha < arrecadacoesAtuais.size() ? arrecadacoesAtuais.get(linha) : null;
    }

    public void iniciarEdicao(Arrecadacao arrecadacao) {
        editando = true;
        formulario.setBorder(BorderFactory.createTitledBorder("Editar arrecadação"));
        nome.setText(arrecadacao.getNome());
        valor.setText(arrecadacao.getValor().toPlainString());
        data.setText(arrecadacao.getData().format(FORMATO_DATA));
        nomeDoador.setText(arrecadacao.getNomeDoador() == null ? "" : arrecadacao.getNomeDoador());
        tagEvento.setText(arrecadacao.getTags().stream().map(tag -> tag.nome()).sorted().collect(Collectors.joining(", ")));
        repetir.setSelected(arrecadacao.getRepeticao() != null);
        if (arrecadacao.getRepeticao() != null) {
            var repeticao = arrecadacao.getRepeticao();
            frequencia.setSelectedItem(repeticao.frequencia());
            dataEntrada.setText(repeticao.dataReferencia().format(FORMATO_DATA));
            limite.setSelectedItem(repeticao.limite());
            quantidade.setText(repeticao.quantidadeRepeticoes() == null ? "" : repeticao.quantidadeRepeticoes().toString());
            dataLimite.setText(repeticao.dataLimite() == null ? "" : repeticao.dataLimite().format(FORMATO_DATA));
        }
        salvar.setText("Salvar alterações");
        limpar.setVisible(false);
        cancelarEdicao.setVisible(true);
        atualizarCamposRepeticao();
        atualizarBotaoEditar();
        situacao.setText("Edite os dados deste lançamento e salve, ou cancele a edição.");
        nome.requestFocusInWindow();
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

    public void mostrarArrecadacoes(List<Arrecadacao> arrecadacoes) {
        arrecadacoesAtuais = List.copyOf(arrecadacoes);
        lancamentos.setRowCount(0);
        BigDecimal total = new BigDecimal("0.00");
        for (Arrecadacao arrecadacao : arrecadacoes) {
            String tags = arrecadacao.getTags().stream().map(tag -> tag.nome()).sorted().collect(Collectors.joining(", "));
            lancamentos.addRow(new Object[]{arrecadacao.getData().format(FORMATO_DATA), arrecadacao.getNome(),
                    arrecadacao.getNomeDoador(), moeda.format(arrecadacao.getValor()), tags});
            total = total.add(arrecadacao.getValor());
        }
        totalMes.setText("Total de arrecadações do mês: " + moeda.format(total));
        situacao.setText(arrecadacoes.isEmpty() ? "Nenhuma arrecadação cadastrada neste mês." : "Arrecadações atualizadas.");
    }

    public void limparFormulario() {
        editando = false;
        formulario.setBorder(BorderFactory.createTitledBorder("Nova arrecadação"));
        salvar.setText("Registrar arrecadação");
        limpar.setVisible(true);
        cancelarEdicao.setVisible(false);
        nome.setText("");
        valor.setText("");
        data.setText(hoje.format(FORMATO_DATA));
        nomeDoador.setText("");
        repetir.setSelected(false);
        frequencia.setSelectedItem(Frequencia.MENSAL);
        dataEntrada.setText(hoje.format(FORMATO_DATA));
        limite.setSelectedItem(LimiteRepeticao.QUANTIDADE);
        quantidade.setText("1");
        dataLimite.setText("");
        tagEvento.setText("");
        atualizarCamposRepeticao();
        atualizarBotaoEditar();
        nome.requestFocusInWindow();
    }

    public void definirOcupada(boolean ocupada, String mensagem) {
        this.ocupada = ocupada;
        for (Component campo : new Component[]{nome, valor, data, nomeDoador, repetir, salvar, limpar, atualizar, mes, ano}) {
            campo.setEnabled(!ocupada);
        }
        cancelarEdicao.setEnabled(!ocupada);
        tabela.setEnabled(!ocupada);
        atualizarCamposRepeticao();
        atualizarBotaoEditar();
        if (mensagem != null) { situacao.setText(mensagem); }
    }

    private void atualizarCamposRepeticao() {
        repetir.setEnabled(!ocupada && !editando);
        boolean habilitar = repetir.isSelected() && !ocupada && !editando;
        frequencia.setEnabled(habilitar);
        dataEntrada.setEnabled(habilitar);
        limite.setEnabled(habilitar);
        quantidade.setEnabled(habilitar && limite.getSelectedItem() == LimiteRepeticao.QUANTIDADE);
        dataLimite.setEnabled(habilitar && limite.getSelectedItem() == LimiteRepeticao.DATA_LIMITE);
    }

    public void aoSalvar(Runnable acao) { salvar.addActionListener(evento -> acao.run()); }
    public void aoEditar(Runnable acao) { editar.addActionListener(evento -> acao.run()); }
    public void aoCancelarEdicao(Runnable acao) { cancelarEdicao.addActionListener(evento -> acao.run()); }
    private void atualizarBotaoEditar() { editar.setEnabled(!ocupada && !editando && lancamentoSelecionado() != null); }
    public void mostrarAlteracaoConcluida() {
        JOptionPane.showMessageDialog(this, "Arrecadação alterada.", "Alteração concluída", JOptionPane.INFORMATION_MESSAGE);
    }
    public void aoAtualizar(Runnable acao) {
        atualizar.addActionListener(evento -> acao.run());
        mes.addChangeListener(evento -> { if (!alterandoMes) { acao.run(); } });
        ano.addChangeListener(evento -> { if (!alterandoMes) { acao.run(); } });
    }
    public void mostrarErro(String mensagem) {
        situacao.setText(mensagem);
        JOptionPane.showMessageDialog(this, mensagem, "Confira os dados", JOptionPane.WARNING_MESSAGE);
    }
    public void mostrarSucesso(int quantidade) {
        String mensagem = quantidade == 1 ? "Arrecadação registrada."
                : "Arrecadação registrada: " + quantidade + " lançamentos gravados.";
        situacao.setText(mensagem);
        JOptionPane.showMessageDialog(this, mensagem, "Registro concluído", JOptionPane.INFORMATION_MESSAGE);
    }

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
