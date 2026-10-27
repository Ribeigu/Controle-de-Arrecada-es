package br.edu.api.repository;

import br.edu.api.model.Acao;
import br.edu.api.model.Arrecadacao;
import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.model.Repeticao;
import br.edu.api.model.Tag;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Objects;
import java.util.UUID;

/** Atualiza os dados e os vínculos de um lançamento na mesma transação. */
final class EdicaoAcaoJdbc {
    // Os identificadores SQL vêm apenas destas constantes, nunca dos campos da tela.
    enum Tipo { DESPESA("despesa"), ARRECADACAO("arrecadacao");
        final String tabela;
        Tipo(String tabela) { this.tabela = tabela; }
        String tabelaTags() { return tabela + "_tag"; }
        String colunaId() { return tabela + "_id"; }
    }

    private EdicaoAcaoJdbc() { }

    static void alterar(BancoDeDados banco, Tipo tipo, UUID id, Acao original, Acao alterada,
                         Repeticao repeticao, int numero) {
        TransacaoJdbc.executar(banco, conexao -> {
            // Mesma ordem de bloqueios da geração mensal: configuração antes do lançamento.
            if (repeticao != null) { RepeticaoJdbc.bloquear(conexao, repeticao.id()); }
            conferirVersao(conexao, tipo, id, original);
            conferirEventos(conexao, alterada);
            ModeloRepeticaoJdbc.preservar(conexao, original, repeticao, numero);
            String doador = tipo == Tipo.ARRECADACAO ? ", nome_doador = ?" : "";
            try (var atualizar = conexao.prepareStatement(
                    "UPDATE " + tipo.tabela + " SET nome = ?, valor = ?, data = ?" + doador + " WHERE id = ?")) {
                atualizar.setString(1, alterada.getNome());
                atualizar.setBigDecimal(2, alterada.getValor());
                atualizar.setObject(3, alterada.getData());
                int parametroId = 4;
                if (alterada instanceof Arrecadacao arrecadacao) {
                    atualizar.setString(4, arrecadacao.getNomeDoador());
                    parametroId = 5;
                }
                atualizar.setObject(parametroId, id);
                if (atualizar.executeUpdate() != 1) { throw conflito(); }
            }
            try (var remover = conexao.prepareStatement("DELETE FROM " + tipo.tabelaTags() + " WHERE " + tipo.colunaId() + " = ?")) {
                remover.setObject(1, id);
                remover.executeUpdate();
            }
            try (var inserir = conexao.prepareStatement(
                    "INSERT INTO " + tipo.tabelaTags() + " (" + tipo.colunaId() + ", tag_nome) VALUES (?, ?)")) {
                for (Tag tag : alterada.getTags()) {
                    inserir.setObject(1, id);
                    inserir.setString(2, tag.nome());
                    inserir.addBatch();
                }
                inserir.executeBatch();
            }
            return null;
        });
    }

    private static void conferirVersao(Connection conexao, Tipo tipo, UUID id, Acao original) throws SQLException {
        try (var consulta = conexao.prepareStatement("SELECT * FROM " + tipo.tabela + " WHERE id = ? FOR UPDATE")) {
            consulta.setObject(1, id);
            try (var registro = consulta.executeQuery()) {
                if (!registro.next()
                        || !original.getNome().equals(registro.getString("nome"))
                        || original.getValor().compareTo(registro.getBigDecimal("valor")) != 0
                        || !original.getData().equals(registro.getObject("data", LocalDate.class))) { throw conflito(); }
                if (original instanceof Arrecadacao arrecadacao
                        && !Objects.equals(arrecadacao.getNomeDoador(), registro.getString("nome_doador"))) { throw conflito(); }
            }
        }
        var tags = new HashSet<Tag>();
        try (var consulta = conexao.prepareStatement("SELECT tag_nome FROM " + tipo.tabelaTags() + " WHERE " + tipo.colunaId() + " = ?")) {
            consulta.setObject(1, id);
            try (var registros = consulta.executeQuery()) {
                while (registros.next()) { tags.add(new Tag(registros.getString(1))); }
            }
        }
        if (!tags.equals(original.getTags())) { throw conflito(); }
    }

    private static void conferirEventos(Connection conexao, Acao alterada) throws SQLException {
        try (var consulta = conexao.prepareStatement("SELECT id FROM evento WHERE tag_nome = ?")) {
            for (Tag tag : alterada.getTags()) {
                consulta.setString(1, tag.nome());
                try (var evento = consulta.executeQuery()) {
                    if (!evento.next()) {
                        throw new RegraDeNegocioException("Não foi encontrado um evento com a tag informada.");
                    }
                }
            }
        }
    }

    private static RegraDeNegocioException conflito() {
        return new RegraDeNegocioException("O lançamento foi alterado ou removido por outra operação. Atualize a consulta e selecione-o novamente.");
    }
}
