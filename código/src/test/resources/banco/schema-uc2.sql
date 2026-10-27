CREATE TABLE IF NOT EXISTS tag (
    nome VARCHAR PRIMARY KEY
);

-- Estrutura de apoio ao vínculo previsto no UC01 e no UC02. O cadastro pertence ao UC04.
CREATE TABLE IF NOT EXISTS evento (
    id UUID PRIMARY KEY,
    nome VARCHAR NOT NULL,
    tag_nome VARCHAR NOT NULL UNIQUE REFERENCES tag(nome),
    status VARCHAR NOT NULL,
    data_inicio DATE NOT NULL,
    data_termino DATE NOT NULL,
    descricao VARCHAR,
    CHECK (data_termino > data_inicio)
);

CREATE TABLE IF NOT EXISTS repeticao (
    id UUID PRIMARY KEY,
    frequencia VARCHAR NOT NULL,
    data_referencia DATE NOT NULL,
    limite VARCHAR NOT NULL,
    quantidade_repeticoes INTEGER,
    data_limite DATE,
    ultimo_mes_atualizado DATE NOT NULL
);

CREATE TABLE IF NOT EXISTS despesa (
    id UUID PRIMARY KEY,
    nome VARCHAR NOT NULL,
    valor DECIMAL(19, 2) NOT NULL CHECK (valor > 0),
    data DATE NOT NULL,
    repeticao_id UUID REFERENCES repeticao(id),
    numero_ocorrencia INTEGER NOT NULL DEFAULT 0,
    UNIQUE (repeticao_id, numero_ocorrencia)
);

CREATE TABLE IF NOT EXISTS despesa_tag (
    despesa_id UUID NOT NULL REFERENCES despesa(id),
    tag_nome VARCHAR NOT NULL REFERENCES tag(nome),
    PRIMARY KEY (despesa_id, tag_nome)
);

CREATE INDEX IF NOT EXISTS idx_despesa_data ON despesa(data);

-- As tabelas do UC02 são acrescentadas sem alterar os registros do UC01.
CREATE TABLE IF NOT EXISTS arrecadacao (
    id UUID PRIMARY KEY,
    nome VARCHAR NOT NULL,
    valor DECIMAL(19, 2) NOT NULL CHECK (valor > 0),
    data DATE NOT NULL,
    nome_doador VARCHAR,
    repeticao_id UUID REFERENCES repeticao(id),
    numero_ocorrencia INTEGER NOT NULL DEFAULT 0,
    UNIQUE (repeticao_id, numero_ocorrencia)
);

CREATE TABLE IF NOT EXISTS arrecadacao_tag (
    arrecadacao_id UUID NOT NULL REFERENCES arrecadacao(id),
    tag_nome VARCHAR NOT NULL REFERENCES tag(nome),
    PRIMARY KEY (arrecadacao_id, tag_nome)
);

CREATE INDEX IF NOT EXISTS idx_arrecadacao_data ON arrecadacao(data);
