package br.edu.api.repository;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

/** Abre conexões locais. Não inicia servidor nem depende de acesso à internet. */
public final class BancoDeDados {
    private final String url;

    public BancoDeDados(Path diretorioDados) {
        try {
            Path pasta = diretorioDados.toAbsolutePath().normalize();
            Files.createDirectories(pasta);
            url = "jdbc:h2:file:" + pasta.resolve("api").toString().replace('\\', '/')
                    + ";DB_CLOSE_ON_EXIT=FALSE";
        } catch (IOException erro) {
            throw new PersistenciaException("Não foi possível preparar a pasta dos dados.", erro);
        }
    }

    public Connection abrirConexao() throws SQLException {
        return DriverManager.getConnection(url, "sa", "");
    }

    public void inicializar() {
        try (var arquivo = BancoDeDados.class.getResourceAsStream("/banco/schema.sql")) {
            if (arquivo == null) {
                throw new IOException("O esquema do banco não foi incluído na aplicação.");
            }
            String esquema = new String(arquivo.readAllBytes(), StandardCharsets.UTF_8);
            try (Connection conexao = abrirConexao()) {
                for (String comando : esquema.split(";")) {
                    if (!comando.isBlank()) {
                        try (var consulta = conexao.prepareStatement(comando)) {
                            consulta.execute();
                        }
                    }
                }
            }
        } catch (IOException | SQLException erro) {
            throw new PersistenciaException("Não foi possível inicializar o banco local.", erro);
        }
    }
}
