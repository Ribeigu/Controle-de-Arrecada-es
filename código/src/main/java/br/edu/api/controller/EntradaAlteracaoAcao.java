package br.edu.api.controller;

import br.edu.api.service.AlteracaoAcao;

/** O UC07 usa as mesmas conversões de valor e data dos cadastros financeiros. */
public record EntradaAlteracaoAcao(String nome, String valor, String data, String nomeDoador, String tagEvento) {
    public AlteracaoAcao paraAlteracao() {
        return new AlteracaoAcao(nome, LeituraFormulario.lerValor(valor),
                LeituraFormulario.lerData(data, "data do lançamento"), nomeDoador, tagEvento);
    }
}
