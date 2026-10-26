package br.edu.api.repository;

import br.edu.api.model.Frequencia;
import br.edu.api.model.LimiteRepeticao;
import br.edu.api.model.Repeticao;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;

/** Persistência da configuração compartilhada pelos dois tipos de lançamento. */
final class RepeticaoJdbc {
    private RepeticaoJdbc() { }

    static void inserir(Connection conexao, Repeticao repeticao, LocalDate hoje) throws SQLException {
        try (var inserir = conexao.prepareStatement("""
                INSERT INTO repeticao (id, frequencia, data_referencia, limite,
                    quantidade_repeticoes, data_limite, ultimo_mes_atualizado)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            inserir.setObject(1, repeticao.id());
            inserir.setString(2, repeticao.frequencia().name());
            inserir.setObject(3, repeticao.dataReferencia());
            inserir.setString(4, repeticao.limite().name());
            inserir.setObject(5, repeticao.quantidadeRepeticoes());
            inserir.setObject(6, repeticao.dataLimite());
            inserir.setObject(7, YearMonth.from(hoje).atDay(1));
            inserir.executeUpdate();
        }
    }

    static Repeticao buscar(Connection conexao, UUID id) throws SQLException {
        try (var consulta = conexao.prepareStatement("SELECT * FROM repeticao WHERE id = ?")) {
            consulta.setObject(1, id);
            try (var registro = consulta.executeQuery()) {
                if (!registro.next()) { throw new SQLException("Configuração de repetição não encontrada."); }
                return new Repeticao(id, Frequencia.valueOf(registro.getString("frequencia")),
                        registro.getObject("data_referencia", LocalDate.class),
                        LimiteRepeticao.valueOf(registro.getString("limite")),
                        registro.getObject("quantidade_repeticoes", Integer.class),
                        registro.getObject("data_limite", LocalDate.class));
            }
        }
    }

    static void bloquear(Connection conexao, UUID id) throws SQLException {
        try (var bloqueio = conexao.prepareStatement("SELECT id FROM repeticao WHERE id = ? FOR UPDATE")) {
            bloqueio.setObject(1, id);
            try (var registro = bloqueio.executeQuery()) {
                if (!registro.next()) { throw new SQLException("Configuração de repetição não encontrada."); }
            }
        }
    }

    static void marcarAtualizada(Connection conexao, UUID id, LocalDate mes) throws SQLException {
        try (var atualizar = conexao.prepareStatement("UPDATE repeticao SET ultimo_mes_atualizado = ? WHERE id = ?")) {
            atualizar.setObject(1, mes);
            atualizar.setObject(2, id);
            atualizar.executeUpdate();
        }
    }
}
