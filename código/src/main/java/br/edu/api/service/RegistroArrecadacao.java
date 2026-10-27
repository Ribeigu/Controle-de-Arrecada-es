package br.edu.api.service;

import br.edu.api.model.Repeticao;
import java.math.BigDecimal;
import java.time.LocalDate;

public record RegistroArrecadacao(String nome, BigDecimal valor, LocalDate data, String nomeDoador,
                                  Repeticao repeticao, String tagEvento) { }
