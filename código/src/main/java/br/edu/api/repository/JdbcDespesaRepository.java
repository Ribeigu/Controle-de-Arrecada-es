package br.edu.api.repository;

import br.edu.api.model.Despesa;
import br.edu.api.model.LimiteRepeticao;
import br.edu.api.model.RegraDeNegocioException;
import br.edu.api.model.Repeticao;
import br.edu.api.model.Tag;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class JdbcDespesaRepository implements DespesaRepository {
    private final BancoDeDados banco;

    public JdbcDespesaRepository(BancoDeDados banco) {
        this.banco = banco;
    }

    @Override
    public int registrar(Despesa despesa, LocalDate hoje) {
        return TransacaoJdbc.executar(banco, conexao -> {
            conferirEventos(conexao, despesa);
            if (despesa.getRepeticao() != null) {
                RepeticaoJdbc.inserir(conexao, despesa.getRepeticao(), hoje);
            }
            inserirDespesa(conexao, despesa);
            return 1 + inserirOcorrencias(conexao, despesa, 0, hoje.plusYears(5));
        });
    }

    @Override
    public List<Despesa> listarPorMes(YearMonth mes) {
        String sql = """
                SELECT d.*, t.tag_nome
                FROM despesa d
                LEFT JOIN despesa_tag t ON t.despesa_id = d.id
                WHERE d.data >= ? AND d.data < ?
                ORDER BY d.data, d.nome, d.id
                """;
        try (Connection conexao = banco.abrirConexao();
             PreparedStatement consulta = conexao.prepareStatement(sql)) {
            consulta.setObject(1, mes.atDay(1));
            consulta.setObject(2, mes.plusMonths(1).atDay(1));
            var despesas = new LinkedHashMap<UUID, Despesa>();
            try (ResultSet registros = consulta.executeQuery()) {
                while (registros.next()) {
                    UUID id = registros.getObject("id", UUID.class);
                    Despesa despesa = despesas.get(id);
                    if (despesa == null) {
                        UUID repeticaoId = registros.getObject("repeticao_id", UUID.class);
                        Repeticao repeticao = repeticaoId == null ? null : RepeticaoJdbc.buscar(conexao, repeticaoId);
                        despesa = lerDespesa(registros, repeticao);
                        despesas.put(id, despesa);
                    }
                    String tag = registros.getString("tag_nome");
                    if (tag != null) { despesa.associarTag(new Tag(tag)); }
                }
            }
            return List.copyOf(despesas.values());
        } catch (SQLException erro) {
            throw new PersistenciaException("Não foi possível consultar as despesas.", erro);
        }
    }

    @Override
    public Optional<Despesa> buscarPorId(UUID id) {
        try (var conexao = banco.abrirConexao(); var consulta = conexao.prepareStatement("SELECT * FROM despesa WHERE id = ?")) {
            consulta.setObject(1, id);
            try (var registro = consulta.executeQuery()) {
                if (!registro.next()) { return Optional.empty(); }
                UUID repeticaoId = registro.getObject("repeticao_id", UUID.class);
                Despesa despesa = lerDespesa(registro, repeticaoId == null ? null : RepeticaoJdbc.buscar(conexao, repeticaoId));
                try (var consultaTags = conexao.prepareStatement("SELECT tag_nome FROM despesa_tag WHERE despesa_id = ?")) {
                    consultaTags.setObject(1, id);
                    try (var tags = consultaTags.executeQuery()) {
                        while (tags.next()) { despesa.associarTag(new Tag(tags.getString(1))); }
                    }
                }
                return Optional.of(despesa);
            }
        } catch (SQLException erro) {
            throw new PersistenciaException("Não foi possível consultar a despesa selecionada.", erro);
        }
    }

    @Override
    public void alterar(Despesa original, Despesa alterada) {
        EdicaoAcaoJdbc.alterar(banco, EdicaoAcaoJdbc.Tipo.DESPESA, original.getId(), original, alterada,
                original.getRepeticao(), original.getNumeroOcorrencia());
    }

    @Override
    public int atualizarRepeticoesPermanentes(LocalDate hoje) {
        return TransacaoJdbc.executar(banco, conexao -> {
            LocalDate primeiroDiaMes = YearMonth.from(hoje).atDay(1);
            List<UUID> pendentes = new ArrayList<>();
            try (var consulta = conexao.prepareStatement("""
                    SELECT r.id FROM repeticao r
                    WHERE r.limite = ? AND r.ultimo_mes_atualizado < ?
                      AND EXISTS (SELECT 1 FROM despesa d
                                  WHERE d.repeticao_id = r.id AND d.numero_ocorrencia = 0)
                    ORDER BY r.id
                    """)) {
                consulta.setString(1, LimiteRepeticao.PARA_SEMPRE.name());
                consulta.setObject(2, primeiroDiaMes);
                try (var registros = consulta.executeQuery()) {
                    while (registros.next()) { pendentes.add(registros.getObject(1, UUID.class)); }
                }
            }
            int quantidade = 0;
            for (UUID id : pendentes) {
                // O bloqueio mantém a atualização segura mesmo com duas janelas abertas.
                RepeticaoJdbc.bloquear(conexao, id);
                Repeticao repeticao = RepeticaoJdbc.buscar(conexao, id);
                Despesa original = buscarDespesaOriginal(conexao, repeticao);
                int ultimaOcorrencia = buscarUltimaOcorrencia(conexao, id);
                quantidade += inserirOcorrencias(conexao, original, ultimaOcorrencia, hoje.plusYears(5));
                RepeticaoJdbc.marcarAtualizada(conexao, id, primeiroDiaMes);
            }
            return quantidade;
        });
    }

    private int inserirOcorrencias(Connection conexao, Despesa original, int ultima, LocalDate horizonte) {
        if (original.getRepeticao() == null) { return 0; }
        int[] quantidade = {0};
        original.getRepeticao().paraCadaOcorrenciaApos(ultima, horizonte, (numero, data) -> {
            inserirDespesa(conexao, original.criarOcorrencia(numero, data));
            quantidade[0]++;
        });
        return quantidade[0];
    }

    private void conferirEventos(Connection conexao, Despesa despesa) throws SQLException {
        try (var consulta = conexao.prepareStatement("SELECT id FROM evento WHERE tag_nome = ?")) {
            for (Tag tag : despesa.getTags()) {
                consulta.setString(1, tag.nome());
                try (var evento = consulta.executeQuery()) {
                    if (!evento.next()) {
                        throw new RegraDeNegocioException("Não foi encontrado um evento com a tag informada.");
                    }
                }
            }
        }
    }

    private void inserirDespesa(Connection conexao, Despesa despesa) {
        try (var inserir = conexao.prepareStatement("""
                INSERT INTO despesa (id, nome, valor, data, repeticao_id, numero_ocorrencia)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            inserir.setObject(1, despesa.getId());
            inserir.setString(2, despesa.getNome());
            inserir.setBigDecimal(3, despesa.getValor());
            inserir.setObject(4, despesa.getData());
            inserir.setObject(5, despesa.getRepeticao() == null ? null : despesa.getRepeticao().id());
            inserir.setInt(6, despesa.getNumeroOcorrencia());
            inserir.executeUpdate();
            try (var associar = conexao.prepareStatement("INSERT INTO despesa_tag (despesa_id, tag_nome) VALUES (?, ?)")) {
                for (Tag tag : despesa.getTags()) {
                    associar.setObject(1, despesa.getId());
                    associar.setString(2, tag.nome());
                    associar.addBatch();
                }
                associar.executeBatch();
            }
        } catch (SQLException erro) {
            throw new PersistenciaException("Não foi possível gravar a despesa.", erro);
        }
    }

    private Despesa buscarDespesaOriginal(Connection conexao, Repeticao repeticao) throws SQLException {
        try (var consulta = conexao.prepareStatement("SELECT * FROM despesa WHERE repeticao_id = ? AND numero_ocorrencia = 0")) {
            consulta.setObject(1, repeticao.id());
            try (var registro = consulta.executeQuery()) {
                if (!registro.next()) { throw new SQLException("Despesa original não encontrada."); }
                Despesa despesa = lerDespesa(registro, repeticao);
                try (var tags = conexao.prepareStatement("SELECT tag_nome FROM despesa_tag WHERE despesa_id = ?")) {
                    tags.setObject(1, despesa.getId());
                    try (var nomes = tags.executeQuery()) {
                        while (nomes.next()) { despesa.associarTag(new Tag(nomes.getString(1))); }
                    }
                }
                return ModeloRepeticaoJdbc.restaurar(conexao, despesa);
            }
        }
    }

    private int buscarUltimaOcorrencia(Connection conexao, UUID repeticaoId) throws SQLException {
        try (var consulta = conexao.prepareStatement("SELECT MAX(numero_ocorrencia) FROM despesa WHERE repeticao_id = ?")) {
            consulta.setObject(1, repeticaoId);
            try (var registro = consulta.executeQuery()) {
                registro.next();
                return registro.getInt(1);
            }
        }
    }

    private Despesa lerDespesa(ResultSet registro, Repeticao repeticao) throws SQLException {
        return new Despesa(registro.getObject("id", UUID.class), registro.getString("nome"),
                registro.getBigDecimal("valor"), registro.getObject("data", LocalDate.class),
                repeticao, registro.getInt("numero_ocorrencia"));
    }

}
