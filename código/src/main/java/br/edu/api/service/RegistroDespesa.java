package br.edu.api.service;

import br.edu.api.model.Repeticao;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistroDespesa(String nome, BigDecimal valor, LocalDate data,
                              Repeticao repeticao, String tagEvento) { }
