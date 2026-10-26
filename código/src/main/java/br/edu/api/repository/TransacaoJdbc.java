package br.edu.api.repository;

import java.sql.Connection;
import java.sql.SQLException;

/** Confirma todos os lançamentos juntos ou desfaz a operação inteira. */
final class TransacaoJdbc {
    private TransacaoJdbc() { }

    @FunctionalInterface
    interface Operacao<T> { T executar(Connection conexao) throws SQLException; }

    static <T> T executar(BancoDeDados banco, Operacao<T> operacao) {
        try (Connection conexao = banco.abrirConexao()) {
            conexao.setAutoCommit(false);
            try {
                T resultado = operacao.executar(conexao);
                conexao.commit();
                return resultado;
            } catch (SQLException | RuntimeException erro) {
                try { conexao.rollback(); } catch (SQLException falhaRollback) { erro.addSuppressed(falhaRollback); }
                if (erro instanceof RuntimeException erroDeAplicacao) { throw erroDeAplicacao; }
                throw new PersistenciaException("Não foi possível concluir a operação no banco local.", erro);
            }
        } catch (SQLException erro) {
            throw new PersistenciaException("Não foi possível acessar o banco local.", erro);
        }
    }
}
