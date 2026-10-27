package br.edu.api.service;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Uma tag nula preserva os vínculos; vazia remove o vínculo; preenchida substitui pelo evento informado. */
public record AlteracaoAcao(String nome, BigDecimal valor, LocalDate data, String nomeDoador, String tagEvento) { }
