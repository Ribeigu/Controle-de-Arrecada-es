package br.edu.api.repository;

import br.edu.api.model.Arrecadacao;
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

public final class JdbcArrecadacaoRepository implements ArrecadacaoRepository {
    private final BancoDeDados banco;

    public JdbcArrecadacaoRepository(BancoDeDados banco) {
        this.banco = banco;
    }

    @Override
    public int registrar(Arrecadacao arrecadacao, LocalDate hoje) {
        return TransacaoJdbc.executar(banco, conexao -> {
            conferirEventos(conexao, arrecadacao);
            if (arrecadacao.getRepeticao() != null) {
                RepeticaoJdbc.inserir(conexao, arrecadacao.getRepeticao(), hoje);
            }
            inserirArrecadacao(conexao, arrecadacao);
            return 1 + inserirOcorrencias(conexao, arrecadacao, 0, hoje.plusYears(5));
        });
    }

    @Override
    public List<Arrecadacao> listarPorMes(YearMonth mes) {
        String sql = """
                SELECT a.*, t.tag_nome
                FROM arrecadacao a
                LEFT JOIN arrecadacao_tag t ON t.arrecadacao_id = a.id
                WHERE a.data >= ? AND a.data < ?
                ORDER BY a.data, a.nome, a.id
                """;
        try (Connection conexao = banco.abrirConexao();
             PreparedStatement consulta = conexao.prepareStatement(sql)) {
            consulta.setObject(1, mes.atDay(1));
            consulta.setObject(2, mes.plusMonths(1).atDay(1));
            var arrecadacoes = new LinkedHashMap<UUID, Arrecadacao>();
            try (ResultSet registros = consulta.executeQuery()) {
                while (registros.next()) {
                    UUID id = registros.getObject("id", UUID.class);
                    Arrecadacao arrecadacao = arrecadacoes.get(id);
                    if (arrecadacao == null) {
                        UUID repeticaoId = registros.getObject("repeticao_id", UUID.class);
                        Repeticao repeticao = repeticaoId == null ? null : RepeticaoJdbc.buscar(conexao, repeticaoId);
                        arrecadacao = lerArrecadacao(registros, repeticao);
                        arrecadacoes.put(id, arrecadacao);
                    }
                    String tag = registros.getString("tag_nome");
                    if (tag != null) { arrecadacao.associarTag(new Tag(tag)); }
                }
            }
            return List.copyOf(arrecadacoes.values());
        } catch (SQLException erro) {
            throw new PersistenciaException("Não foi possível consultar as arrecadações.", erro);
        }
    }

    @Override
    public Optional<Arrecadacao> buscarPorId(UUID id) {
        try (var conexao = banco.abrirConexao(); var consulta = conexao.prepareStatement("SELECT * FROM arrecadacao WHERE id = ?")) {
            consulta.setObject(1, id);
            try (var registro = consulta.executeQuery()) {
                if (!registro.next()) { return Optional.empty(); }
                UUID repeticaoId = registro.getObject("repeticao_id", UUID.class);
                Arrecadacao arrecadacao = lerArrecadacao(registro, repeticaoId == null ? null : RepeticaoJdbc.buscar(conexao, repeticaoId));
                try (var consultaTags = conexao.prepareStatement("SELECT tag_nome FROM arrecadacao_tag WHERE arrecadacao_id = ?")) {
                    consultaTags.setObject(1, id);
                    try (var tags = consultaTags.executeQuery()) {
                        while (tags.next()) { arrecadacao.associarTag(new Tag(tags.getString(1))); }
                    }
                }
                return Optional.of(arrecadacao);
            }
        } catch (SQLException erro) {
            throw new PersistenciaException("Não foi possível consultar a arrecadação selecionada.", erro);
        }
    }

    @Override
    public void alterar(Arrecadacao original, Arrecadacao alterada) {
        EdicaoAcaoJdbc.alterar(banco, EdicaoAcaoJdbc.Tipo.ARRECADACAO, original.getId(), original, alterada,
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
                      AND EXISTS (SELECT 1 FROM arrecadacao a
                                  WHERE a.repeticao_id = r.id AND a.numero_ocorrencia = 0)
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
                Arrecadacao original = buscarArrecadacaoOriginal(conexao, repeticao);
                int ultimaOcorrencia = buscarUltimaOcorrencia(conexao, id);
                quantidade += inserirOcorrencias(conexao, original, ultimaOcorrencia, hoje.plusYears(5));
                RepeticaoJdbc.marcarAtualizada(conexao, id, primeiroDiaMes);
            }
            return quantidade;
        });
    }

    private int inserirOcorrencias(Connection conexao, Arrecadacao original, int ultima, LocalDate horizonte) {
        if (original.getRepeticao() == null) { return 0; }
        int[] quantidade = {0};
        original.getRepeticao().paraCadaOcorrenciaApos(ultima, horizonte, (numero, data) -> {
            inserirArrecadacao(conexao, original.criarOcorrencia(numero, data));
            quantidade[0]++;
        });
        return quantidade[0];
    }

    private void conferirEventos(Connection conexao, Arrecadacao arrecadacao) throws SQLException {
        try (var consulta = conexao.prepareStatement("SELECT id FROM evento WHERE tag_nome = ?")) {
            for (Tag tag : arrecadacao.getTags()) {
                consulta.setString(1, tag.nome());
                try (var evento = consulta.executeQuery()) {
                    if (!evento.next()) {
                        throw new RegraDeNegocioException("Não foi encontrado um evento com a tag informada.");
                    }
                }
            }
        }
    }

    private void inserirArrecadacao(Connection conexao, Arrecadacao arrecadacao) {
        try (var inserir = conexao.prepareStatement("""
                INSERT INTO arrecadacao (id, nome, valor, data, repeticao_id, numero_ocorrencia, nome_doador)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            inserir.setObject(1, arrecadacao.getId());
            inserir.setString(2, arrecadacao.getNome());
            inserir.setBigDecimal(3, arrecadacao.getValor());
            inserir.setObject(4, arrecadacao.getData());
            inserir.setObject(5, arrecadacao.getRepeticao() == null ? null : arrecadacao.getRepeticao().id());
            inserir.setInt(6, arrecadacao.getNumeroOcorrencia());
            inserir.setString(7, arrecadacao.getNomeDoador());
            inserir.executeUpdate();
            try (var associar = conexao.prepareStatement("INSERT INTO arrecadacao_tag (arrecadacao_id, tag_nome) VALUES (?, ?)")) {
                for (Tag tag : arrecadacao.getTags()) {
                    associar.setObject(1, arrecadacao.getId());
                    associar.setString(2, tag.nome());
                    associar.addBatch();
                }
                associar.executeBatch();
            }
        } catch (SQLException erro) {
            throw new PersistenciaException("Não foi possível gravar a arrecadação.", erro);
        }
    }

    private Arrecadacao buscarArrecadacaoOriginal(Connection conexao, Repeticao repeticao) throws SQLException {
        try (var consulta = conexao.prepareStatement("SELECT * FROM arrecadacao WHERE repeticao_id = ? AND numero_ocorrencia = 0")) {
            consulta.setObject(1, repeticao.id());
            try (var registro = consulta.executeQuery()) {
                if (!registro.next()) { throw new SQLException("Arrecadação original não encontrada."); }
                Arrecadacao arrecadacao = lerArrecadacao(registro, repeticao);
                try (var tags = conexao.prepareStatement("SELECT tag_nome FROM arrecadacao_tag WHERE arrecadacao_id = ?")) {
                    tags.setObject(1, arrecadacao.getId());
                    try (var nomes = tags.executeQuery()) {
                        while (nomes.next()) { arrecadacao.associarTag(new Tag(nomes.getString(1))); }
                    }
                }
                return ModeloRepeticaoJdbc.restaurar(conexao, arrecadacao);
            }
        }
    }

    private int buscarUltimaOcorrencia(Connection conexao, UUID repeticaoId) throws SQLException {
        try (var consulta = conexao.prepareStatement("SELECT MAX(numero_ocorrencia) FROM arrecadacao WHERE repeticao_id = ?")) {
            consulta.setObject(1, repeticaoId);
            try (var registro = consulta.executeQuery()) {
                registro.next();
                return registro.getInt(1);
            }
        }
    }

    private Arrecadacao lerArrecadacao(ResultSet registro, Repeticao repeticao) throws SQLException {
        return new Arrecadacao(registro.getObject("id", UUID.class), registro.getString("nome"),
                registro.getBigDecimal("valor"), registro.getObject("data", LocalDate.class),
                registro.getString("nome_doador"), repeticao, registro.getInt("numero_ocorrencia"));
    }

}
