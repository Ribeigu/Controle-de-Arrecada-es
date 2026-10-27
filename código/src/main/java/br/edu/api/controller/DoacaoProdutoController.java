package br.edu.api.controller;

import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.service.DoacaoProdutoService;
import br.edu.api.service.RegistroDoacaoProduto;
import br.edu.api.view.DoacaoProdutoView;
import java.time.YearMonth;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;

public final class DoacaoProdutoController {
    private static final Logger LOG = Logger.getLogger(DoacaoProdutoController.class.getName());
    private final DoacaoProdutoView tela;
    private final DoacaoProdutoService servico;
    private boolean trabalhando;

    public DoacaoProdutoController(DoacaoProdutoView tela, DoacaoProdutoService servico) {
        this.tela = tela;
        this.servico = servico;
        tela.aoSalvar(this::registrar);
        tela.aoAtualizar(this::atualizarLista);
    }

    public void iniciar() { atualizarLista(); }

    private void registrar() {
        if (trabalhando) { return; }
        RegistroDoacaoProduto pedido;
        try {
            pedido = tela.lerFormulario().paraRegistro();
        } catch (RegraDeNegocioException erro) {
            tela.mostrarErro(erro.getMessage());
            return;
        }
        executar("Registrando doação de produto...", () -> servico.registrar(pedido), doacao -> {
            tela.limparFormulario();
            tela.selecionarMes(YearMonth.from(doacao.getData()));
            tela.mostrarSucesso();
            atualizarLista();
        });
    }

    private void atualizarLista() {
        if (trabalhando) { return; }
        YearMonth mes = tela.mesSelecionado();
        executar("Atualizando doações de produtos...", () -> servico.listarPorMes(mes), tela::mostrarDoacoes);
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
                        LOG.log(Level.SEVERE, "Falha na operação de doações de produtos", causa);
                        tela.mostrarErro("Não foi possível concluir a operação. Confira o acesso ao arquivo de dados e tente novamente.");
                    }
                    return;
                }
                aoConcluir.accept(resultado);
            }
        }.execute();
    }
}
