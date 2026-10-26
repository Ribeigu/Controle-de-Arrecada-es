package br.edu.api.model;

/** Uma orientação que pode ser apresentada diretamente ao usuário. */
public class RegraDeNegocioException extends IllegalArgumentException {
    public RegraDeNegocioException(String mensagem) {
        super(mensagem);
    }
}
