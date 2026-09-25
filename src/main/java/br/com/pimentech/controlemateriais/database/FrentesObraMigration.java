package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

/** Acrescenta disciplinas, elementos e liberações sem reescrever registros da versão 14. */
public final class FrentesObraMigration implements Migration {
    @Override public int version() { return 15; }
    @Override public String description() { return "Disciplinas, elementos e liberação explícita de frentes"; }

    @Override public void apply(Connection connection) throws SQLException {
        try (Statement sql = connection.createStatement()) {
            sql.executeUpdate("""
                    CREATE TABLE disciplinas_obra (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL REFERENCES obras(id) ON DELETE RESTRICT,
                        nome TEXT NOT NULL CHECK (length(trim(nome)) > 0),
                        ativo INTEGER NOT NULL DEFAULT 1 CHECK (ativo IN (0,1))
                    )
                    """);
            sql.executeUpdate("CREATE UNIQUE INDEX uq_disciplina_obra ON disciplinas_obra(obra_id, lower(nome)) WHERE ativo = 1");
            sql.executeUpdate("ALTER TABLE servicos_area ADD COLUMN disciplina_id INTEGER REFERENCES disciplinas_obra(id) ON DELETE RESTRICT");
            sql.executeUpdate("ALTER TABLE servicos_area ADD COLUMN equipe_responsavel TEXT");
            sql.executeUpdate("ALTER TABLE servicos_area ADD COLUMN observacoes TEXT");
            sql.executeUpdate("""
                    CREATE TABLE elementos_servico (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL REFERENCES obras(id) ON DELETE RESTRICT,
                        servico_id INTEGER NOT NULL REFERENCES servicos_area(id) ON DELETE RESTRICT,
                        codigo TEXT NOT NULL CHECK (length(trim(codigo)) > 0),
                        ativo INTEGER NOT NULL DEFAULT 1 CHECK (ativo IN (0,1))
                    )
                    """);
            sql.executeUpdate("CREATE UNIQUE INDEX uq_elemento_servico ON elementos_servico(servico_id, lower(codigo)) WHERE ativo = 1");
            sql.executeUpdate("CREATE INDEX idx_elemento_servico_obra ON elementos_servico(obra_id, servico_id)");
            sql.executeUpdate("""
                    CREATE TRIGGER ck_elemento_servico_obra BEFORE INSERT ON elementos_servico
                    WHEN NOT EXISTS (SELECT 1 FROM servicos_area s WHERE s.id = NEW.servico_id
                        AND s.obra_id = NEW.obra_id AND s.ativo = 1)
                    BEGIN SELECT RAISE(ABORT, 'Elemento e serviço devem pertencer à mesma obra'); END
                    """);
            sql.executeUpdate("""
                    CREATE TRIGGER ck_disciplina_servico_obra BEFORE UPDATE OF disciplina_id ON servicos_area
                    WHEN NEW.disciplina_id IS NOT NULL AND NOT EXISTS (
                        SELECT 1 FROM disciplinas_obra d WHERE d.id = NEW.disciplina_id
                        AND d.obra_id = NEW.obra_id AND d.ativo = 1)
                    BEGIN SELECT RAISE(ABORT, 'Disciplina não pertence à obra do serviço'); END
                    """);
            sql.executeUpdate("""
                    CREATE TABLE producao_elementos (
                        producao_id INTEGER NOT NULL REFERENCES producao_diaria_servico(id) ON DELETE CASCADE,
                        elemento_id INTEGER NOT NULL REFERENCES elementos_servico(id) ON DELETE RESTRICT,
                        PRIMARY KEY (producao_id, elemento_id)
                    )
                    """);
            sql.executeUpdate("""
                    CREATE TABLE vinculos_frente (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL REFERENCES obras(id) ON DELETE RESTRICT,
                        origem_servico_id INTEGER NOT NULL REFERENCES servicos_area(id) ON DELETE RESTRICT,
                        destino_servico_id INTEGER NOT NULL REFERENCES servicos_area(id) ON DELETE RESTRICT,
                        area_id INTEGER NOT NULL REFERENCES areas_obra(id) ON DELETE RESTRICT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        CHECK (origem_servico_id <> destino_servico_id),
                        UNIQUE (origem_servico_id, destino_servico_id)
                    )
                    """);
            sql.executeUpdate("CREATE INDEX idx_vinculos_frente_obra ON vinculos_frente(obra_id, area_id)");
            sql.executeUpdate("""
                    CREATE TRIGGER ck_area_servico_vinculado BEFORE UPDATE OF area_id ON servicos_area
                    WHEN NEW.area_id <> OLD.area_id AND EXISTS (
                        SELECT 1 FROM vinculos_frente v WHERE v.origem_servico_id = OLD.id OR v.destino_servico_id = OLD.id)
                    BEGIN SELECT RAISE(ABORT, 'Serviço vinculado não pode mudar de área'); END
                    """);
            sql.executeUpdate("""
                    CREATE TABLE vinculo_trechos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        vinculo_id INTEGER NOT NULL REFERENCES vinculos_frente(id) ON DELETE RESTRICT,
                        codigo TEXT NOT NULL CHECK (length(trim(codigo)) > 0),
                        origem_elemento_id INTEGER REFERENCES elementos_servico(id) ON DELETE RESTRICT,
                        destino_elemento_id INTEGER REFERENCES elementos_servico(id) ON DELETE RESTRICT,
                        UNIQUE (vinculo_id, codigo)
                    )
                    """);
            sql.executeUpdate("""
                    CREATE TABLE liberacoes_trecho (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        trecho_id INTEGER NOT NULL REFERENCES vinculo_trechos(id) ON DELETE RESTRICT,
                        data_liberacao TEXT NOT NULL,
                        situacao TEXT NOT NULL CHECK (situacao IN ('LIBERADO','BLOQUEADO')),
                        responsavel TEXT NOT NULL CHECK (length(trim(responsavel)) > 0),
                        observacao TEXT NOT NULL CHECK (length(trim(observacao)) > 0),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);
            sql.executeUpdate("CREATE INDEX idx_liberacoes_trecho_data ON liberacoes_trecho(trecho_id, data_liberacao, id)");
            sql.executeUpdate("""
                    CREATE TRIGGER ck_producao_elemento_escopo BEFORE INSERT ON producao_elementos
                    WHEN NOT EXISTS (
                        SELECT 1 FROM producao_diaria_servico p JOIN elementos_servico e
                        ON e.servico_id = p.servico_id AND e.obra_id = p.obra_id
                        WHERE p.id = NEW.producao_id AND e.id = NEW.elemento_id AND e.ativo = 1
                    ) BEGIN SELECT RAISE(ABORT, 'Elemento não pertence ao lançamento'); END
                    """);
            sql.executeUpdate("""
                    CREATE TRIGGER ck_vinculo_escopo BEFORE INSERT ON vinculos_frente
                    WHEN NOT EXISTS (
                        SELECT 1 FROM servicos_area o JOIN servicos_area d
                        ON d.area_id = o.area_id AND d.obra_id = o.obra_id
                        WHERE o.id = NEW.origem_servico_id AND d.id = NEW.destino_servico_id
                        AND o.obra_id = NEW.obra_id AND o.area_id = NEW.area_id
                    ) BEGIN SELECT RAISE(ABORT, 'Vínculo deve usar serviços da mesma área e obra'); END
                    """);
            sql.executeUpdate("""
                    CREATE TRIGGER ck_trecho_escopo BEFORE INSERT ON vinculo_trechos
                    WHEN NOT EXISTS (
                        SELECT 1 FROM vinculos_frente v WHERE v.id = NEW.vinculo_id
                        AND (NEW.origem_elemento_id IS NULL OR EXISTS (
                            SELECT 1 FROM elementos_servico e WHERE e.id = NEW.origem_elemento_id
                            AND e.servico_id = v.origem_servico_id AND e.obra_id = v.obra_id
                            AND lower(e.codigo) = lower(NEW.codigo) AND e.ativo = 1))
                        AND (NEW.destino_elemento_id IS NULL OR EXISTS (
                            SELECT 1 FROM elementos_servico e WHERE e.id = NEW.destino_elemento_id
                            AND e.servico_id = v.destino_servico_id AND e.obra_id = v.obra_id
                            AND lower(e.codigo) = lower(NEW.codigo) AND e.ativo = 1))
                    ) BEGIN SELECT RAISE(ABORT, 'Trecho não pertence aos serviços do vínculo'); END
                    """);
        }
    }
}
