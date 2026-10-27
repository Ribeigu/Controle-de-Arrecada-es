package br.edu.api.service;

import br.edu.api.model.RegraDeNegocioException;

/** Porta de integração com a sessão autenticada exigida pelo RNF1.2. */
@FunctionalInterface
public interface AutorizacaoEventos {
    void exigirUsuarioAutenticado();

    static AutorizacaoEventos semSessao() {
        return () -> {
            throw new RegraDeNegocioException("O vínculo a um evento exige autenticação prévia.");
        };
    }
}
