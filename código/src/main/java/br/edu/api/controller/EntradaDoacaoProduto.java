package br.edu.api.controller;

import br.edu.api.model.UnidadeMedida;
import br.edu.api.service.RegistroDoacaoProduto;

public record EntradaDoacaoProduto(String nomeProduto, UnidadeMedida unidadeMedida, String quantidade,
                                   String data, String nomeDoador, String prazoValidade, String prazoGarantia) {
    public RegistroDoacaoProduto paraRegistro() {
        return new RegistroDoacaoProduto(nomeProduto, unidadeMedida,
                LeituraFormulario.lerQuantidadeProduto(quantidade),
                LeituraFormulario.lerData(data, "data da doação"), nomeDoador,
                LeituraFormulario.lerDataOpcional(prazoValidade, "data de validade"),
                LeituraFormulario.lerDataOpcional(prazoGarantia, "data de fim da garantia"));
    }
}
