package br.edu.api.repository;

import br.edu.api.model.Arrecadacao;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ArrecadacaoRepository {
    int registrar(Arrecadacao arrecadacao, LocalDate hoje);
    List<Arrecadacao> listarPorMes(YearMonth mes);
    Optional<Arrecadacao> buscarPorId(UUID id);
    void alterar(Arrecadacao original, Arrecadacao alterada);
    int atualizarRepeticoesPermanentes(LocalDate hoje);
}
