package br.edu.api.view;

import br.edu.api.controller.EntradaDespesa;
import br.edu.api.controller.EntradaAlteracaoAcao;
import br.edu.api.model.Despesa;
import br.edu.api.model.Frequencia;
import br.edu.api.model.LimiteRepeticao;
import br.edu.api.service.GrupoDespesa;
import java.awt.BorderLayout;
import java.awt.Color;
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
import java.util.function.IntConsumer;
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

/** Tela simples de cadastro à esquerda e acompanhamento mensal à direita. */
public final class DespesaView extends JPanel {
    private static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private final NumberFormat moeda = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("pt-BR"));
    private final JTextField nome = campo("nome", 24);
    private final JTextField valor = campo("valor", 12);
    private final JTextField data = campo("data", 12);
    private final JCheckBox repetir = new JCheckBox("Repetir esta despesa");
    private final JComboBox<Frequencia> frequencia = new JComboBox<>(Frequencia.values());
    private final JTextField dataCobranca = campo("dataCobranca", 12);
    private final JComboBox<LimiteRepeticao> limite = new JComboBox<>(LimiteRepeticao.values());
    private final JTextField quantidade = campo("quantidade", 8);
    private final JTextField dataLimite = campo("dataLimite", 12);
    private final JTextField tagEvento = campo("tagEvento", 16);
    private final JButton salvar = new JButton("Registrar despesa");
    private final JButton limpar = new JButton("Limpar");
    private final JButton atualizar = new JButton("Atualizar");
    private final JButton editar = new JButton("Editar lançamento selecionado");
    private final JButton cancelarEdicao = new JButton("Cancelar edição");
    private final JPanel formulario = new JPanel(new GridBagLayout());
    private final JSpinner mes;
    private final JSpinner ano;
    private final DefaultTableModel grupos = tabela("Despesa", "Lançamentos", "Total (R$)");
    private final DefaultTableModel lancamentos = tabela("Data", "Valor (R$)", "Tag do evento");
    private final JTable tabelaGrupos = new JTable(grupos);
    private final JTable tabelaLancamentos = new JTable(lancamentos);
    private final JLabel detalhes = new JLabel("Selecione uma despesa para ver os lançamentos.");
    private final JLabel totalMes = new JLabel("Total de despesas do mês: R$ 0,00");
    private final JLabel situacao = new JLabel("Carregando despesas...");
    private final LocalDate hoje;
    private boolean ocupada;
    private boolean alterandoMes;
    private boolean editando;
    private List<Despesa> lancamentosAtuais = List.of();

    public DespesaView(LocalDate hoje) {
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
        situacao.setBorder(BorderFactory.createEmptyBorder(4, 0, 0, 0));
        add(situacao, BorderLayout.SOUTH);

        repetir.addActionListener(evento -> {
            if (repetir.isSelected()) { dataCobranca.setText(data.getText()); }
            atualizarCamposRepeticao();
        });
        limite.addActionListener(evento -> atualizarCamposRepeticao());
        limpar.addActionListener(evento -> limparFormulario());
        data.addActionListener(evento -> dataCobranca.setText(data.getText()));
        tabelaLancamentos.setName("lancamentosDespesas");
        tabelaGrupos.setName("gruposDespesas");
        tabelaLancamentos.getSelectionModel().addListSelectionListener(evento -> atualizarBotaoEditar());
        limparFormulario();
    }

    private JPanel montarFormulario() {
        JPanel painel = formulario;
        painel.setPreferredSize(new Dimension(360, 590));
        painel.setBorder(BorderFactory.createTitledBorder("Nova despesa"));
        int linha = 0;
        linha = adicionarCampo(painel, linha, "Nome *", nome);
        linha = adicionarCampo(painel, linha, "Valor em reais *", valor);
        linha = adicionarTexto(painel, linha, "Use duas casas decimais. Ex.: 125.50");
        linha = adicionarCampo(painel, linha, "Data da despesa * (DD/MM/AAAA)", data);
        linha = adicionarComponente(painel, linha, repetir);
        linha = adicionarCampo(painel, linha, "Frequência", frequencia);
        linha = adicionarCampo(painel, linha, "Data base da cobrança (DD/MM/AAAA)", dataCobranca);
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
        painel.setBorder(BorderFactory.createTitledBorder("Despesas do mês"));
        JPanel filtros = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 4));
        filtros.add(new JLabel("Mês"));
        filtros.add(mes);
        filtros.add(new JLabel("Ano"));
        filtros.add(ano);
        filtros.add(atualizar);
        painel.add(filtros, BorderLayout.NORTH);

        configurarTabela(tabelaGrupos);
        configurarTabela(tabelaLancamentos);
        JPanel tabelas = new JPanel(new java.awt.GridLayout(2, 1, 0, 16));
        JPanel resumo = new JPanel(new BorderLayout(0, 6));
        resumo.add(new JLabel("Despesas com o mesmo nome aparecem juntas."), BorderLayout.NORTH);
        resumo.add(rolagemComCabecalho(tabelaGrupos), BorderLayout.CENTER);
        tabelas.add(resumo);
        JPanel lista = new JPanel(new BorderLayout(0, 6));
        lista.add(detalhes, BorderLayout.NORTH);
        lista.add(rolagemComCabecalho(tabelaLancamentos), BorderLayout.CENTER);
        tabelas.add(lista);
        painel.add(tabelas, BorderLayout.CENTER);
        totalMes.setFont(totalMes.getFont().deriveFont(Font.BOLD, 14));
        JPanel rodape = new JPanel(new BorderLayout(0, 8));
        rodape.add(totalMes, BorderLayout.NORTH);
        rodape.add(editar, BorderLayout.SOUTH);
        painel.add(rodape, BorderLayout.SOUTH);
        return painel;
    }

    public EntradaDespesa lerFormulario() {
        return new EntradaDespesa(nome.getText(), valor.getText(), data.getText(), repetir.isSelected(),
                (Frequencia) frequencia.getSelectedItem(), dataCobranca.getText(),
                (LimiteRepeticao) limite.getSelectedItem(), quantidade.getText(), dataLimite.getText(), tagEvento.getText());
    }

    public EntradaAlteracaoAcao lerAlteracao() {
        return new EntradaAlteracaoAcao(nome.getText(), valor.getText(), data.getText(), null, null);
    }

    public Despesa lancamentoSelecionado() {
        int linha = tabelaLancamentos.getSelectedRow();
        return linha >= 0 && linha < lancamentosAtuais.size() ? lancamentosAtuais.get(linha) : null;
    }

    public void iniciarEdicao(Despesa despesa) {
        editando = true;
        formulario.setBorder(BorderFactory.createTitledBorder("Editar despesa"));
        nome.setText(despesa.getNome());
        valor.setText(despesa.getValor().toPlainString());
        data.setText(despesa.getData().format(FORMATO_DATA));
        tagEvento.setText(despesa.getTags().stream().map(tag -> tag.nome()).sorted()
                .collect(java.util.stream.Collectors.joining(", ")));
        repetir.setSelected(despesa.getRepeticao() != null);
        if (despesa.getRepeticao() != null) {
            var repeticao = despesa.getRepeticao();
            frequencia.setSelectedItem(repeticao.frequencia());
            dataCobranca.setText(repeticao.dataReferencia().format(FORMATO_DATA));
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

    public void mostrarGrupos(List<GrupoDespesa> despesas) {
        lancamentosAtuais = List.of();
        grupos.setRowCount(0);
        lancamentos.setRowCount(0);
        detalhes.setText("Selecione uma despesa para ver os lançamentos.");
        java.math.BigDecimal total = new java.math.BigDecimal("0.00");
        for (GrupoDespesa grupo : despesas) {
            grupos.addRow(new Object[]{grupo.nome(), grupo.lancamentos().size(), moeda.format(grupo.total())});
            total = total.add(grupo.total());
        }
        totalMes.setText("Total de despesas do mês: " + moeda.format(total));
        if (!despesas.isEmpty()) { tabelaGrupos.setRowSelectionInterval(0, 0); }
        situacao.setText(despesas.isEmpty() ? "Nenhuma despesa cadastrada neste mês." : "Despesas atualizadas.");
    }

    public void mostrarLancamentos(GrupoDespesa grupo) {
        lancamentosAtuais = grupo == null ? List.of() : grupo.lancamentos();
        lancamentos.setRowCount(0);
        if (grupo == null) { return; }
        detalhes.setText("Lançamentos de " + grupo.nome());
        for (Despesa despesa : grupo.lancamentos()) {
            String tags = despesa.getTags().stream().map(tag -> tag.nome()).sorted()
                    .collect(java.util.stream.Collectors.joining(", "));
            lancamentos.addRow(new Object[]{despesa.getData().format(FORMATO_DATA), moeda.format(despesa.getValor()), tags});
        }
    }

    public void limparFormulario() {
        editando = false;
        formulario.setBorder(BorderFactory.createTitledBorder("Nova despesa"));
        salvar.setText("Registrar despesa");
        limpar.setVisible(true);
        cancelarEdicao.setVisible(false);
        nome.setText("");
        valor.setText("");
        data.setText(hoje.format(FORMATO_DATA));
        repetir.setSelected(false);
        frequencia.setSelectedItem(Frequencia.MENSAL);
        dataCobranca.setText(hoje.format(FORMATO_DATA));
        limite.setSelectedItem(LimiteRepeticao.QUANTIDADE);
        quantidade.setText("1");
        dataLimite.setText("");
        tagEvento.setText("");
        atualizarCamposRepeticao();
        atualizarBotaoEditar();
        nome.requestFocusInWindow();
    }

    public void definirOcupada(boolean valor, String mensagem) {
        ocupada = valor;
        nome.setEnabled(!valor);
        this.valor.setEnabled(!valor);
        data.setEnabled(!valor);
        salvar.setEnabled(!valor);
        limpar.setEnabled(!valor);
        atualizar.setEnabled(!valor);
        mes.setEnabled(!valor);
        ano.setEnabled(!valor);
        cancelarEdicao.setEnabled(!valor);
        tabelaGrupos.setEnabled(!valor);
        tabelaLancamentos.setEnabled(!valor);
        atualizarCamposRepeticao();
        atualizarBotaoEditar();
        if (mensagem != null) { situacao.setText(mensagem); }
    }

    private void atualizarCamposRepeticao() {
        repetir.setEnabled(!ocupada && !editando);
        boolean habilitar = repetir.isSelected() && !ocupada && !editando;
        frequencia.setEnabled(habilitar);
        dataCobranca.setEnabled(habilitar);
        limite.setEnabled(habilitar);
        quantidade.setEnabled(habilitar && limite.getSelectedItem() == LimiteRepeticao.QUANTIDADE);
        dataLimite.setEnabled(habilitar && limite.getSelectedItem() == LimiteRepeticao.DATA_LIMITE);
    }

    public void aoSalvar(Runnable acao) { salvar.addActionListener(evento -> acao.run()); }
    public void aoEditar(Runnable acao) { editar.addActionListener(evento -> acao.run()); }
    public void aoCancelarEdicao(Runnable acao) { cancelarEdicao.addActionListener(evento -> acao.run()); }
    private void atualizarBotaoEditar() { editar.setEnabled(!ocupada && !editando && lancamentoSelecionado() != null); }
    public void mostrarAlteracaoConcluida() {
        JOptionPane.showMessageDialog(this, "Despesa alterada.", "Alteração concluída", JOptionPane.INFORMATION_MESSAGE);
    }
    public void aoAtualizar(Runnable acao) {
        atualizar.addActionListener(evento -> acao.run());
        mes.addChangeListener(evento -> { if (!alterandoMes) { acao.run(); } });
        ano.addChangeListener(evento -> { if (!alterandoMes) { acao.run(); } });
    }
    public void aoSelecionarGrupo(IntConsumer acao) {
        tabelaGrupos.getSelectionModel().addListSelectionListener(evento -> {
            if (!evento.getValueIsAdjusting()) { acao.accept(tabelaGrupos.getSelectedRow()); }
        });
    }
    public void mostrarErro(String mensagem) {
        situacao.setText(mensagem);
        JOptionPane.showMessageDialog(this, mensagem, "Confira os dados", JOptionPane.WARNING_MESSAGE);
    }
    public void mostrarSucesso(int quantidade) {
        String mensagem = quantidade == 1 ? "Despesa registrada." : "Despesa registrada: " + quantidade + " lançamentos gravados.";
        situacao.setText(mensagem);
        JOptionPane.showMessageDialog(this, mensagem, "Registro concluído", JOptionPane.INFORMATION_MESSAGE);
    }

    private static JTextField campo(String nome, int colunas) {
        JTextField campo = new JTextField(colunas);
        campo.setName(nome);
        return campo;
    }
    private static DefaultTableModel tabela(String... colunas) {
        return new DefaultTableModel(colunas, 0) {
            @Override public boolean isCellEditable(int linha, int coluna) { return false; }
        };
    }
    private static void configurarTabela(JTable tabela) {
        tabela.setRowHeight(27);
        tabela.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        tabela.getTableHeader().setReorderingAllowed(false);
        tabela.setFillsViewportHeight(true);
    }
    private static JScrollPane rolagemComCabecalho(JTable tabela) {
        JScrollPane rolagem = new JScrollPane(tabela);
        rolagem.setColumnHeaderView(tabela.getTableHeader());
        return rolagem;
    }
    private int adicionarCampo(JPanel painel, int linha, String texto, java.awt.Component componente) {
        linha = adicionarComponente(painel, linha, new JLabel(texto));
        return adicionarComponente(painel, linha, componente);
    }
    private int adicionarTexto(JPanel painel, int linha, String texto) {
        JLabel ajuda = new JLabel(texto);
        ajuda.setFont(ajuda.getFont().deriveFont(11f));
        ajuda.setForeground(new Color(85, 85, 85));
        return adicionarComponente(painel, linha, ajuda);
    }
    private int adicionarComponente(JPanel painel, int linha, java.awt.Component componente) {
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
