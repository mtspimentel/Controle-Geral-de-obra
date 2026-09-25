package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class PlanejamentoDiarioMigration implements Migration {
    @Override public int version() { return 14; }
    @Override public String description() { return "Áreas, serviços, produção diária e ocorrências da obra"; }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE areas_obra (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL REFERENCES obras(id) ON DELETE RESTRICT,
                        area_pai_id INTEGER REFERENCES areas_obra(id) ON DELETE RESTRICT,
                        tipo TEXT NOT NULL CHECK (tipo IN ('BLOCO','PAVIMENTO','SETOR','AMBIENTE','OUTRA')),
                        nome TEXT NOT NULL CHECK (length(trim(nome)) > 0),
                        ativo INTEGER NOT NULL DEFAULT 1 CHECK (ativo IN (0,1)),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate("CREATE UNIQUE INDEX uq_areas_obra_nome ON areas_obra(obra_id, ifnull(area_pai_id,0), tipo, lower(nome)) WHERE ativo = 1");
            statement.executeUpdate("CREATE INDEX idx_areas_obra_obra ON areas_obra(obra_id, ativo)");
            statement.executeUpdate("""
                    CREATE TABLE servicos_area (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL REFERENCES obras(id) ON DELETE RESTRICT,
                        area_id INTEGER NOT NULL REFERENCES areas_obra(id) ON DELETE RESTRICT,
                        descricao TEXT NOT NULL CHECK (length(trim(descricao)) > 0),
                        unidade TEXT NOT NULL CHECK (length(trim(unidade)) > 0),
                        quantidade_prevista REAL NOT NULL CHECK (quantidade_prevista > 0),
                        inicio_previsto TEXT,
                        fim_previsto TEXT,
                        meta_diaria REAL CHECK (meta_diaria > 0),
                        ativo INTEGER NOT NULL DEFAULT 1 CHECK (ativo IN (0,1)),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate("CREATE INDEX idx_servicos_area_obra_area ON servicos_area(obra_id, area_id, ativo)");
            statement.executeUpdate("""
                    CREATE TABLE producao_diaria_servico (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL REFERENCES obras(id) ON DELETE RESTRICT,
                        servico_id INTEGER NOT NULL REFERENCES servicos_area(id) ON DELETE RESTRICT,
                        data_producao TEXT NOT NULL,
                        quantidade REAL NOT NULL CHECK (quantidade >= 0),
                        trabalhadores INTEGER NOT NULL CHECK (trabalhadores >= 0),
                        horas_por_trabalhador REAL CHECK (horas_por_trabalhador > 0 AND horas_por_trabalhador <= 24),
                        equipe TEXT,
                        observacao TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        UNIQUE (servico_id, data_producao)
                    )
                    """);
            statement.executeUpdate("CREATE INDEX idx_producao_diaria_obra_data ON producao_diaria_servico(obra_id, data_producao)");
            statement.executeUpdate("""
                    CREATE TABLE ocorrencias_servico (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL REFERENCES obras(id) ON DELETE RESTRICT,
                        servico_id INTEGER NOT NULL REFERENCES servicos_area(id) ON DELETE RESTRICT,
                        data_ocorrencia TEXT NOT NULL,
                        tipo TEXT NOT NULL CHECK (tipo IN ('FALTA_MATERIAL','CHUVA','ATRASO_LIBERACAO','RETRABALHO','PARALISACAO','OUTRA')),
                        descricao TEXT NOT NULL CHECK (length(trim(descricao)) > 0),
                        material_id INTEGER REFERENCES materiais(id) ON DELETE RESTRICT,
                        resolvida_em TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            statement.executeUpdate("CREATE INDEX idx_ocorrencias_obra_data ON ocorrencias_servico(obra_id, data_ocorrencia)");
            statement.executeUpdate("CREATE INDEX idx_ocorrencias_servico_abertas ON ocorrencias_servico(servico_id, resolvida_em)");
        }
    }
}
