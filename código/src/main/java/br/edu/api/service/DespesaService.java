package br.edu.api.service;

import br.edu.api.model.Despesa;
import br.edu.api.model.Instituicao;
import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.model.Tag;
import br.edu.api.repository.DespesaRepository;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;

public final class DespesaService {
    private final Instituicao instituicao;
    private final DespesaRepository despesas;
    private final AutorizacaoEventos autorizacaoEventos;

    public DespesaService(Instituicao instituicao, DespesaRepository despesas,
                          AutorizacaoEventos autorizacaoEventos) {
        this.instituicao = instituicao;
        this.despesas = despesas;
        this.autorizacaoEventos = autorizacaoEventos;
    }

    public int registrar(RegistroDespesa pedido) {
        Despesa despesa = instituicao.informarDespesa(pedido.valor(), pedido.nome(), pedido.data());
        despesa.configurarRepeticao(pedido.repeticao());
        if (pedido.tagEvento() != null && !pedido.tagEvento().isBlank()) {
            autorizacaoEventos.exigirUsuarioAutenticado();
            despesa.associarTag(new Tag(pedido.tagEvento()));
        }
        try {
            return despesas.registrar(despesa, instituicao.hoje());
        } catch (DateTimeException erro) {
            throw new RegraDeNegocioException("A repetição ultrapassa o intervalo de datas suportado.");
        }
    }

    public List<GrupoDespesa> listarPorMes(YearMonth mes) {
        var porNome = new TreeMap<String, List<Despesa>>();
        for (Despesa despesa : despesas.listarPorMes(mes)) {
            porNome.computeIfAbsent(despesa.getNome(), nome -> new ArrayList<>()).add(despesa);
        }
        return porNome.entrySet().stream()
                .map(grupo -> new GrupoDespesa(grupo.getKey(), grupo.getValue())).toList();
    }

    public Despesa alterar(UUID id, AlteracaoAcao pedido) {
        Despesa original = despesas.buscarPorId(id).orElseThrow(
                () -> new RegraDeNegocioException("A despesa selecionada não foi encontrada. Atualize a consulta."));
        return alterar(original, pedido);
    }

    public Despesa alterar(Despesa original, AlteracaoAcao pedido) {
        Despesa alterada = instituicao.alterarDespesa(original, pedido.nome(), pedido.valor(), pedido.data());
        if (!original.getTags().isEmpty() || pedido.tagEvento() != null && !pedido.tagEvento().isBlank()) {
            autorizacaoEventos.exigirUsuarioAutenticado();
        }
        if (pedido.tagEvento() == null) {
            original.getTags().forEach(alterada::associarTag);
        } else if (!pedido.tagEvento().isBlank()) {
            alterada.associarTag(new Tag(pedido.tagEvento()));
        }
        despesas.alterar(original, alterada);
        return alterada;
    }

    public int atualizarRepeticoesPermanentes() {
        return despesas.atualizarRepeticoesPermanentes(instituicao.hoje());
    }
}
