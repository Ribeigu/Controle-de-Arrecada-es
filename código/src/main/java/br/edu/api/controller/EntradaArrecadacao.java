package br.edu.api.controller;

import br.edu.api.model.Frequencia;
import br.edu.api.model.LimiteRepeticao;
import br.edu.api.service.RegistroArrecadacao;

public record EntradaArrecadacao(String nome, String valor, String data, String nomeDoador,
                                 boolean repetir, Frequencia frequencia, String dataEntrada,
                                 LimiteRepeticao limite, String quantidade, String dataLimite, String tagEvento) {
    public RegistroArrecadacao paraRegistro() {
        var dataArrecadacao = LeituraFormulario.lerData(data, "data da arrecadação");
        var valorArrecadacao = LeituraFormulario.lerValor(valor);
        var repeticao = repetir ? LeituraFormulario.lerRepeticao(frequencia, dataEntrada,
                "data base da entrada", limite, quantidade, dataLimite) : null;
        return new RegistroArrecadacao(nome, valorArrecadacao, dataArrecadacao, nomeDoador, repeticao, tagEvento);
    }
}
