package br.edu.api.service;

import br.edu.api.model.Despesa;
import java.math.BigDecimal;
import java.util.List;

/** Agrupamento de apresentação: os lançamentos continuam separados no banco. */
public record GrupoDespesa(String nome, List<Despesa> lancamentos) {
    public GrupoDespesa {
        lancamentos = List.copyOf(lancamentos);
    }

    public BigDecimal total() {
        return lancamentos.stream().map(Despesa::getValor)
                .reduce(new BigDecimal("0.00"), BigDecimal::add);
    }
}
