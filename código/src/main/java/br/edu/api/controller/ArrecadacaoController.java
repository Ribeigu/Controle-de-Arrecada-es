package br.edu.api.controller;

import br.edu.api.model.Instituicao;
import br.edu.api.model.Arrecadacao;
import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.service.ArrecadacaoService;
import br.edu.api.service.RegistroArrecadacao;
import br.edu.api.service.AlteracaoAcao;
import br.edu.api.view.ArrecadacaoView;
import java.time.YearMonth;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import javax.swing.Timer;

public final class ArrecadacaoController {
    private static final Logger LOG = Logger.getLogger(ArrecadacaoController.class.getName());
    private final ArrecadacaoView tela;
    private final ArrecadacaoService servico;
    private final Instituicao instituicao;
    private final Timer verificarMudancaMes;
    private YearMonth ultimoMesAtualizado;
    private boolean trabalhando;
    private Arrecadacao arrecadacaoEmEdicao;

    public ArrecadacaoController(ArrecadacaoView tela, ArrecadacaoService servico, Instituicao instituicao) {
        this.tela = tela;
        this.servico = servico;
        this.instituicao = instituicao;
        tela.aoSalvar(this::registrar);
        tela.aoAtualizar(this::atualizarLista);
        tela.aoEditar(this::editarSelecionada);
        tela.aoCancelarEdicao(() -> {
            arrecadacaoEmEdicao = null;
            tela.limparFormulario();
        });
        verificarMudancaMes = new Timer(60_000, evento -> {
            if (!trabalhando && !YearMonth.from(instituicao.hoje()).equals(ultimoMesAtualizado)) {
                atualizarLista();
            }
        });
    }

    public void iniciar() {
        atualizarLista();
        verificarMudancaMes.start();
    }

    public void encerrar() { verificarMudancaMes.stop(); }

    private void registrar() {
        if (trabalhando) { return; }
        if (arrecadacaoEmEdicao != null) {
            salvarAlteracao();
            return;
        }
        RegistroArrecadacao pedido;
        try {
            pedido = tela.lerFormulario().paraRegistro();
        } catch (RegraDeNegocioException erro) {
            tela.mostrarErro(erro.getMessage());
            return;
        }
        executar("Registrando arrecadação...", () -> servico.registrar(pedido), quantidade -> {
            tela.limparFormulario();
            tela.selecionarMes(YearMonth.from(pedido.data()));
            tela.mostrarSucesso(quantidade);
            atualizarLista();
        });
    }

    private void editarSelecionada() {
        if (trabalhando || arrecadacaoEmEdicao != null) { return; }
        Arrecadacao selecionada = tela.lancamentoSelecionado();
        if (selecionada == null) {
            tela.mostrarErro("Selecione um lançamento na lista de arrecadações.");
            return;
        }
        arrecadacaoEmEdicao = selecionada;
        tela.iniciarEdicao(selecionada);
    }

    private void salvarAlteracao() {
        AlteracaoAcao pedido;
        try {
            pedido = tela.lerAlteracao().paraAlteracao();
        } catch (RegraDeNegocioException erro) {
            tela.mostrarErro(erro.getMessage());
            return;
        }
        Arrecadacao original = arrecadacaoEmEdicao;
        executar("Salvando alterações da arrecadação...", () -> servico.alterar(original, pedido), alterada -> {
            arrecadacaoEmEdicao = null;
            tela.limparFormulario();
            tela.selecionarMes(YearMonth.from(alterada.getData()));
            tela.mostrarAlteracaoConcluida();
            atualizarLista();
        });
    }

    private void atualizarLista() {
        if (trabalhando) { return; }
        YearMonth mes = tela.mesSelecionado();
        YearMonth mesAtual = YearMonth.from(instituicao.hoje());
        executar("Atualizando arrecadações...", () -> {
            servico.atualizarRepeticoesPermanentes();
            return servico.listarPorMes(mes);
        }, arrecadacoes -> {
            ultimoMesAtualizado = mesAtual;
            tela.mostrarArrecadacoes(arrecadacoes);
        });
    }

    private <T> void executar(String mensagem, Callable<T> tarefa, Consumer<T> aoConcluir) {
        trabalhando = true;
        tela.definirOcupada(true, mensagem);
        new SwingWorker<T, Void>() {
            @Override
            protected T doInBackground() throws Exception { return tarefa.call(); }

            @Override
            protected void done() {
                trabalhando = false;
                tela.definirOcupada(false, null);
                T resultado;
                try {
                    resultado = get();
                } catch (InterruptedException erro) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (ExecutionException erro) {
                    Throwable causa = erro.getCause();
                    if (causa instanceof RegraDeNegocioException) {
                        tela.mostrarErro(causa.getMessage());
                    } else {
                        LOG.log(Level.SEVERE, "Falha na operação de arrecadações", causa);
                        tela.mostrarErro("Não foi possível concluir a operação. Confira o acesso ao arquivo de dados e tente novamente.");
                    }
                    return;
                }
                aoConcluir.accept(resultado);
            }
        }.execute();
    }
}
