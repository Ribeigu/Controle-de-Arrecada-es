package br.edu.api.repository;

import br.edu.api.model.Acao;
import br.edu.api.model.Arrecadacao;
import br.edu.api.model.Despesa;
import br.edu.api.model.LimiteRepeticao;
import br.edu.api.model.Repeticao;
import br.edu.api.model.Tag;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

/** Separa a edição de um lançamento dos dados usados para gerar ocorrências futuras. */
final class ModeloRepeticaoJdbc {
    private ModeloRepeticaoJdbc() { }

    static void preservar(Connection conexao, Acao original, Repeticao repeticao, int numero) throws SQLException {
        if (repeticao == null || numero != 0 || repeticao.limite() != LimiteRepeticao.PARA_SEMPRE) { return; }
        if (buscar(conexao, repeticao.id()) != null) { return; }
        try (var inserir = conexao.prepareStatement("""
                INSERT INTO modelo_repeticao (repeticao_id, nome, valor, nome_doador) VALUES (?, ?, ?, ?)
                """)) {
            inserir.setObject(1, repeticao.id());
            inserir.setString(2, original.getNome());
            inserir.setBigDecimal(3, original.getValor());
            inserir.setString(4, original instanceof Arrecadacao arrecadacao ? arrecadacao.getNomeDoador() : null);
            inserir.executeUpdate();
        }
        try (var inserir = conexao.prepareStatement("INSERT INTO modelo_repeticao_tag (repeticao_id, tag_nome) VALUES (?, ?)")) {
            for (Tag tag : original.getTags()) {
                inserir.setObject(1, repeticao.id());
                inserir.setString(2, tag.nome());
                inserir.addBatch();
            }
            inserir.executeBatch();
        }
    }

    static Despesa restaurar(Connection conexao, Despesa original) throws SQLException {
        Dados modelo = buscar(conexao, original.getRepeticao().id());
        if (modelo == null) { return original; }
        var despesa = new Despesa(original.getId(), modelo.nome(), modelo.valor(), original.getData(),
                original.getRepeticao(), original.getNumeroOcorrencia());
        modelo.tags().forEach(despesa::associarTag);
        return despesa;
    }

    static Arrecadacao restaurar(Connection conexao, Arrecadacao original) throws SQLException {
        Dados modelo = buscar(conexao, original.getRepeticao().id());
        if (modelo == null) { return original; }
        var arrecadacao = new Arrecadacao(original.getId(), modelo.nome(), modelo.valor(), original.getData(),
                modelo.doador(), original.getRepeticao(), original.getNumeroOcorrencia());
        modelo.tags().forEach(arrecadacao::associarTag);
        return arrecadacao;
    }

    private static Dados buscar(Connection conexao, UUID id) throws SQLException {
        try (var consulta = conexao.prepareStatement("SELECT * FROM modelo_repeticao WHERE repeticao_id = ?")) {
            consulta.setObject(1, id);
            try (var registro = consulta.executeQuery()) {
                if (!registro.next()) { return null; }
                var tags = new LinkedHashSet<Tag>();
                try (var consultaTags = conexao.prepareStatement("SELECT tag_nome FROM modelo_repeticao_tag WHERE repeticao_id = ?")) {
                    consultaTags.setObject(1, id);
                    try (var nomes = consultaTags.executeQuery()) {
                        while (nomes.next()) { tags.add(new Tag(nomes.getString(1))); }
                    }
                }
                return new Dados(registro.getString("nome"), registro.getBigDecimal("valor"),
                        registro.getString("nome_doador"), Set.copyOf(tags));
            }
        }
    }

    private record Dados(String nome, BigDecimal valor, String doador, Set<Tag> tags) { }
}
