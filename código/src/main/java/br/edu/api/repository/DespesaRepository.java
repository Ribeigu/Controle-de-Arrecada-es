package br.edu.api.repository;

import br.edu.api.model.Despesa;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DespesaRepository {
    int registrar(Despesa despesa, LocalDate hoje);
    List<Despesa> listarPorMes(YearMonth mes);
    Optional<Despesa> buscarPorId(UUID id);
    void alterar(Despesa original, Despesa alterada);
    int atualizarRepeticoesPermanentes(LocalDate hoje);
}
