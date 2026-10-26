package br.edu.api.service;

import br.edu.api.model.Arrecadacao;
import br.edu.api.model.Instituicao;
import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.model.Tag;
import br.edu.api.repository.ArrecadacaoRepository;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

public final class ArrecadacaoService {
    private final Instituicao instituicao;
    private final ArrecadacaoRepository arrecadacoes;
    private final AutorizacaoEventos autorizacaoEventos;

    public ArrecadacaoService(Instituicao instituicao, ArrecadacaoRepository arrecadacoes,
                              AutorizacaoEventos autorizacaoEventos) {
        this.instituicao = instituicao;
        this.arrecadacoes = arrecadacoes;
        this.autorizacaoEventos = autorizacaoEventos;
    }

    public int registrar(RegistroArrecadacao pedido) {
        Arrecadacao arrecadacao = instituicao.informarArrecadacao(
                pedido.valor(), pedido.nome(), pedido.data(), pedido.nomeDoador());
        arrecadacao.configurarRepeticao(pedido.repeticao());
        if (pedido.tagEvento() != null && !pedido.tagEvento().isBlank()) {
            autorizacaoEventos.exigirUsuarioAutenticado();
            arrecadacao.associarTag(new Tag(pedido.tagEvento()));
        }
        try {
            return arrecadacoes.registrar(arrecadacao, instituicao.hoje());
        } catch (DateTimeException erro) {
            throw new RegraDeNegocioException("A repetição ultrapassa o intervalo de datas suportado.");
        }
    }

    public List<Arrecadacao> listarPorMes(YearMonth mes) {
        return arrecadacoes.listarPorMes(mes);
    }

    public Arrecadacao alterar(UUID id, AlteracaoAcao pedido) {
        Arrecadacao original = arrecadacoes.buscarPorId(id).orElseThrow(
                () -> new RegraDeNegocioException("A arrecadação selecionada não foi encontrada. Atualize a consulta."));
        return alterar(original, pedido);
    }

    public Arrecadacao alterar(Arrecadacao original, AlteracaoAcao pedido) {
        Arrecadacao alterada = instituicao.alterarArrecadacao(original, pedido.nome(), pedido.valor(),
                pedido.data(), pedido.nomeDoador());
        if (!original.getTags().isEmpty() || pedido.tagEvento() != null && !pedido.tagEvento().isBlank()) {
            autorizacaoEventos.exigirUsuarioAutenticado();
        }
        if (pedido.tagEvento() == null) {
            original.getTags().forEach(alterada::associarTag);
        } else if (!pedido.tagEvento().isBlank()) {
            alterada.associarTag(new Tag(pedido.tagEvento()));
        }
        arrecadacoes.alterar(original, alterada);
        return alterada;
    }

    public int atualizarRepeticoesPermanentes() {
        return arrecadacoes.atualizarRepeticoesPermanentes(instituicao.hoje());
    }
}
