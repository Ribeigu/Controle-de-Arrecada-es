package br.edu.api.controller;

import br.edu.api.model.Frequencia;
import br.edu.api.model.LimiteRepeticao;
import br.edu.api.service.RegistroDespesa;

/** Converte os textos da tela sem colocar regras de interface no domínio. */
public record EntradaDespesa(String nome, String valor, String data, boolean repetir,
                             Frequencia frequencia, String dataCobranca, LimiteRepeticao limite,
                             String quantidade, String dataLimite, String tagEvento) {
    public RegistroDespesa paraRegistro() {
        var dataDespesa = LeituraFormulario.lerData(data, "data da despesa");
        var valorDespesa = LeituraFormulario.lerValor(valor);
        var repeticao = repetir ? LeituraFormulario.lerRepeticao(frequencia, dataCobranca,
                "data base da cobrança", limite, quantidade, dataLimite) : null;
        return new RegistroDespesa(nome, valorDespesa, dataDespesa, repeticao, tagEvento);
    }
}
