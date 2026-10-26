package br.edu.api.service;

import br.edu.api.model.UnidadeMedida;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistroDoacaoProduto(String nomeProduto, UnidadeMedida unidadeMedida, BigDecimal quantidade,
                                    LocalDate data, String nomeDoador, LocalDate prazoValidade, LocalDate prazoGarantia) { }
