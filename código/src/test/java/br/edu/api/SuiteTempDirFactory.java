package br.edu.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.extension.AnnotatedElementContext;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.io.TempDirFactory;

/** Mantém os bancos temporários dos testes dentro do projeto. */
public final class SuiteTempDirFactory implements TempDirFactory {
    @Override
    public Path createTempDirectory(AnnotatedElementContext contexto, ExtensionContext extensao) throws IOException {
        Path base = Files.createDirectories(Path.of("target", "dados-teste"));
        return Files.createTempDirectory(base, "uc1-");
    }
}
