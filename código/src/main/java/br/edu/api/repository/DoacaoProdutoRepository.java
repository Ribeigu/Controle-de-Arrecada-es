package br.edu.api.repository;

import br.edu.api.model.DoacaoDeProduto;
import java.time.YearMonth;
import java.util.List;

public interface DoacaoProdutoRepository {
    void registrar(DoacaoDeProduto doacao);
    List<DoacaoDeProduto> listarPorMes(YearMonth mes);
}
