package br.edu.api.repository;

import br.edu.api.model.DoacaoDeProduto;
import br.edu.api.model.UnidadeMedida;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class JdbcDoacaoProdutoRepository implements DoacaoProdutoRepository {
    private final BancoDeDados banco;

    public JdbcDoacaoProdutoRepository(BancoDeDados banco) {
        this.banco = banco;
    }

    @Override
    public void registrar(DoacaoDeProduto doacao) {
        String sql = """
                INSERT INTO doacao_produto (id, nome_produto, unidade_medida, quantidade,
                    data, nome_doador, prazo_validade, prazo_garantia)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (var conexao = banco.abrirConexao(); var inserir = conexao.prepareStatement(sql)) {
            inserir.setObject(1, doacao.getId());
            inserir.setString(2, doacao.getNomeProduto());
            inserir.setString(3, doacao.getUnidadeMedida().name());
            inserir.setBigDecimal(4, doacao.getQuantidade());
            inserir.setObject(5, doacao.getData());
            inserir.setString(6, doacao.getNomeDoador());
            inserir.setObject(7, doacao.getPrazoValidade());
            inserir.setObject(8, doacao.getPrazoGarantia());
            inserir.executeUpdate();
        } catch (SQLException erro) {
            throw new PersistenciaException("Não foi possível gravar a doação de produto.", erro);
        }
    }

    @Override
    public List<DoacaoDeProduto> listarPorMes(YearMonth mes) {
        String sql = """
                SELECT * FROM doacao_produto
                WHERE data >= ? AND data < ?
                ORDER BY data, nome_produto, id
                """;
        try (var conexao = banco.abrirConexao(); var consulta = conexao.prepareStatement(sql)) {
            consulta.setObject(1, mes.atDay(1));
            consulta.setObject(2, mes.plusMonths(1).atDay(1));
            var doacoes = new ArrayList<DoacaoDeProduto>();
            try (var registros = consulta.executeQuery()) {
                while (registros.next()) {
                    doacoes.add(new DoacaoDeProduto(registros.getObject("id", UUID.class),
                            registros.getString("nome_produto"), UnidadeMedida.valueOf(registros.getString("unidade_medida")),
                            registros.getBigDecimal("quantidade"), registros.getObject("data", LocalDate.class),
                            registros.getString("nome_doador"), registros.getObject("prazo_validade", LocalDate.class),
                            registros.getObject("prazo_garantia", LocalDate.class)));
                }
            }
            return List.copyOf(doacoes);
        } catch (SQLException erro) {
            throw new PersistenciaException("Não foi possível consultar as doações de produtos.", erro);
        }
    }
}
