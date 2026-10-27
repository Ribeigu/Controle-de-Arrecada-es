package br.edu.api.controller;

import br.edu.api.model.Instituicao;
import br.edu.api.model.Despesa;
import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.service.DespesaService;
import br.edu.api.service.GrupoDespesa;
import br.edu.api.service.RegistroDespesa;
import br.edu.api.service.AlteracaoAcao;
import br.edu.api.view.DespesaView;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.swing.SwingWorker;
import javax.swing.Timer;

public final class DespesaController {
    private static final Logger LOG = Logger.getLogger(DespesaController.class.getName());
    private final DespesaView tela;
    private final DespesaService servico;
    private final Instituicao instituicao;
    private final Timer verificarMudancaMes;
    private List<GrupoDespesa> gruposAtuais = List.of();
    private YearMonth ultimoMesAtualizado;
    private boolean trabalhando;
    private Despesa despesaEmEdicao;

    public DespesaController(DespesaView tela, DespesaService servico, Instituicao instituicao) {
        this.tela = tela;
        this.servico = servico;
        this.instituicao = instituicao;
        tela.aoSalvar(this::registrar);
        tela.aoAtualizar(this::atualizarLista);
        tela.aoEditar(this::editarSelecionada);
        tela.aoCancelarEdicao(() -> {
            despesaEmEdicao = null;
            tela.limparFormulario();
        });
        tela.aoSelecionarGrupo(indice -> tela.mostrarLancamentos(
                indice >= 0 && indice < gruposAtuais.size() ? gruposAtuais.get(indice) : null));
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
        if (despesaEmEdicao != null) {
            salvarAlteracao();
            return;
        }
        RegistroDespesa pedido;
        try {
            pedido = tela.lerFormulario().paraRegistro();
        } catch (RegraDeNegocioException erro) {
            tela.mostrarErro(erro.getMessage());
            return;
        }
        executar("Registrando despesa...", () -> servico.registrar(pedido), quantidade -> {
            tela.limparFormulario();
            tela.selecionarMes(YearMonth.from(pedido.data()));
            tela.mostrarSucesso(quantidade);
            // Consultar depois de confirmar evita apresentar uma falha de leitura como falha de gravação.
            atualizarLista();
        });
    }

    private void editarSelecionada() {
        if (trabalhando || despesaEmEdicao != null) { return; }
        Despesa selecionada = tela.lancamentoSelecionado();
        if (selecionada == null) {
            tela.mostrarErro("Selecione um lançamento individual na lista de despesas.");
            return;
        }
        despesaEmEdicao = selecionada;
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
        Despesa original = despesaEmEdicao;
        executar("Salvando alterações da despesa...", () -> servico.alterar(original, pedido), alterada -> {
            despesaEmEdicao = null;
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
        executar("Atualizando despesas...", () -> {
            servico.atualizarRepeticoesPermanentes();
            return servico.listarPorMes(mes);
        }, grupos -> {
            ultimoMesAtualizado = mesAtual;
            gruposAtuais = grupos;
            tela.mostrarGrupos(grupos);
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
                        LOG.log(Level.SEVERE, "Falha na operação de despesas", causa);
                        tela.mostrarErro("Não foi possível concluir a operação. Confira o acesso ao arquivo de dados e tente novamente.");
                    }
                    return;
                }
                aoConcluir.accept(resultado);
            }
        }.execute();
    }
}
