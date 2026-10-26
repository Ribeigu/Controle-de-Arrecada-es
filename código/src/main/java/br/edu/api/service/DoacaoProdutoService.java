package br.edu.api.service;

import br.edu.api.model.DoacaoDeProduto;
import br.edu.api.model.Instituicao;
import br.edu.api.repository.DoacaoProdutoRepository;
import java.time.YearMonth;
import java.util.List;

public final class DoacaoProdutoService {
    private final Instituicao instituicao;
    private final DoacaoProdutoRepository doacoes;

    public DoacaoProdutoService(Instituicao instituicao, DoacaoProdutoRepository doacoes) {
        this.instituicao = instituicao;
        this.doacoes = doacoes;
    }

    public DoacaoDeProduto registrar(RegistroDoacaoProduto pedido) {
        DoacaoDeProduto doacao = instituicao.informarDoacaoProduto(pedido.nomeProduto(), pedido.unidadeMedida(),
                pedido.quantidade(), pedido.data(), pedido.nomeDoador());
        doacao.definirValidadeOuGarantia(pedido.prazoValidade(), pedido.prazoGarantia());
        doacoes.registrar(doacao);
        return doacao;
    }

    public List<DoacaoDeProduto> listarPorMes(YearMonth mes) {
        return doacoes.listarPorMes(mes);
    }
}
