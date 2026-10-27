package br.edu.api.model;

public record Tag(String nome) {
    public Tag {
        if (nome == null || nome.isBlank()) {
            throw new RegraDeNegocioException("Informe a tag do evento.");
        }
        nome = nome.strip();
    }
}
