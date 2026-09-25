package br.com.pimentech.controlemateriais.database;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class SchemaMigration implements Migration {

    @Override
    public int version() {
        return 1;
    }

    @Override
    public String description() {
        return "Estrutura inicial de suprimentos da obra";
    }

    @Override
    public void apply(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS obras (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        nome TEXT NOT NULL,
                        codigo TEXT NOT NULL UNIQUE,
                        endereco TEXT,
                        responsavel TEXT,
                        data_inicio TEXT,
                        previsao_termino TEXT,
                        status TEXT NOT NULL DEFAULT 'ATIVA'
                            CHECK (status IN ('PLANEJADA', 'ATIVA', 'CONCLUIDA', 'CANCELADA')),
                        ativo INTEGER NOT NULL DEFAULT 1 CHECK (ativo IN (0, 1)),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS fornecedores (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        nome TEXT NOT NULL,
                        cnpj TEXT UNIQUE,
                        telefone TEXT,
                        email TEXT,
                        contato TEXT,
                        observacao TEXT,
                        ativo INTEGER NOT NULL DEFAULT 1 CHECK (ativo IN (0, 1)),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS materiais (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL,
                        codigo TEXT NOT NULL,
                        descricao TEXT NOT NULL,
                        unidade TEXT NOT NULL,
                        categoria TEXT,
                        especificacao TEXT,
                        estoque_minimo REAL NOT NULL DEFAULT 0 CHECK (estoque_minimo >= 0),
                        consumo_medio_diario REAL NOT NULL DEFAULT 0 CHECK (consumo_medio_diario >= 0),
                        prazo_medio_entrega_dias INTEGER NOT NULL DEFAULT 0 CHECK (prazo_medio_entrega_dias >= 0),
                        observacao TEXT,
                        ativo INTEGER NOT NULL DEFAULT 1 CHECK (ativo IN (0, 1)),
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (obra_id) REFERENCES obras (id) ON DELETE RESTRICT,
                        UNIQUE (obra_id, codigo)
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS estoque (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        obra_id INTEGER NOT NULL,
                        material_id INTEGER NOT NULL,
                        quantidade_atual REAL NOT NULL DEFAULT 0 CHECK (quantidade_atual >= 0),
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (obra_id) REFERENCES obras (id) ON DELETE RESTRICT,
                        FOREIGN KEY (material_id) REFERENCES materiais (id) ON DELETE RESTRICT,
                        UNIQUE (obra_id, material_id)
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS requisicoes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        numero TEXT NOT NULL UNIQUE,
                        obra_id INTEGER NOT NULL,
                        solicitante TEXT NOT NULL,
                        data_requisicao TEXT NOT NULL,
                        prioridade TEXT NOT NULL DEFAULT 'NORMAL'
                            CHECK (prioridade IN ('NORMAL', 'ALTA', 'URGENTE')),
                        status TEXT NOT NULL DEFAULT 'RASCUNHO'
                            CHECK (status IN ('RASCUNHO', 'ENVIADA', 'EM_COTACAO', 'APROVADA', 'CONVERTIDA', 'CANCELADA')),
                        observacao TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (obra_id) REFERENCES obras (id) ON DELETE RESTRICT
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS requisicao_itens (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        requisicao_id INTEGER NOT NULL,
                        material_id INTEGER NOT NULL,
                        quantidade REAL NOT NULL CHECK (quantidade > 0),
                        unidade TEXT NOT NULL,
                        observacao TEXT,
                        FOREIGN KEY (requisicao_id) REFERENCES requisicoes (id) ON DELETE CASCADE,
                        FOREIGN KEY (material_id) REFERENCES materiais (id) ON DELETE RESTRICT,
                        UNIQUE (requisicao_id, material_id)
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cotacoes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        requisicao_id INTEGER NOT NULL,
                        fornecedor_id INTEGER NOT NULL,
                        data_cotacao TEXT NOT NULL,
                        validade TEXT,
                        status TEXT NOT NULL DEFAULT 'RECEBIDA'
                            CHECK (status IN ('SOLICITADA', 'RECEBIDA', 'SELECIONADA', 'RECUSADA')),
                        observacao TEXT,
                        FOREIGN KEY (requisicao_id) REFERENCES requisicoes (id) ON DELETE CASCADE,
                        FOREIGN KEY (fornecedor_id) REFERENCES fornecedores (id) ON DELETE RESTRICT
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS cotacao_itens (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        cotacao_id INTEGER NOT NULL,
                        requisicao_item_id INTEGER NOT NULL,
                        quantidade_atendida REAL NOT NULL DEFAULT 0 CHECK (quantidade_atendida >= 0),
                        preco_unitario REAL NOT NULL DEFAULT 0 CHECK (preco_unitario >= 0),
                        prazo_entrega_dias INTEGER NOT NULL DEFAULT 0 CHECK (prazo_entrega_dias >= 0),
                        atende INTEGER NOT NULL DEFAULT 0 CHECK (atende IN (0, 1)),
                        observacao TEXT,
                        FOREIGN KEY (cotacao_id) REFERENCES cotacoes (id) ON DELETE CASCADE,
                        FOREIGN KEY (requisicao_item_id) REFERENCES requisicao_itens (id) ON DELETE RESTRICT,
                        UNIQUE (cotacao_id, requisicao_item_id)
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mapas_cotacao (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        requisicao_id INTEGER NOT NULL UNIQUE,
                        obra_id INTEGER NOT NULL,
                        data_criacao TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'EM_ANALISE'
                            CHECK (status IN ('EM_ANALISE', 'APROVADO', 'REJEITADO')),
                        aprovado_em TEXT,
                        observacao TEXT,
                        FOREIGN KEY (requisicao_id) REFERENCES requisicoes (id) ON DELETE CASCADE,
                        FOREIGN KEY (obra_id) REFERENCES obras (id) ON DELETE RESTRICT
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS mapa_cotacao_itens (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        mapa_id INTEGER NOT NULL,
                        requisicao_item_id INTEGER NOT NULL,
                        cotacao_id INTEGER,
                        fornecedor_id INTEGER,
                        quantidade_aprovada REAL NOT NULL DEFAULT 0 CHECK (quantidade_aprovada >= 0),
                        preco_aprovado REAL NOT NULL DEFAULT 0 CHECK (preco_aprovado >= 0),
                        FOREIGN KEY (mapa_id) REFERENCES mapas_cotacao (id) ON DELETE CASCADE,
                        FOREIGN KEY (requisicao_item_id) REFERENCES requisicao_itens (id) ON DELETE RESTRICT,
                        FOREIGN KEY (cotacao_id) REFERENCES cotacoes (id) ON DELETE RESTRICT,
                        FOREIGN KEY (fornecedor_id) REFERENCES fornecedores (id) ON DELETE RESTRICT
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pedidos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        numero TEXT NOT NULL UNIQUE,
                        obra_id INTEGER NOT NULL,
                        requisicao_id INTEGER,
                        fornecedor_id INTEGER NOT NULL,
                        data_pedido TEXT NOT NULL,
                        data_prevista_entrega TEXT,
                        data_recebimento_completo TEXT,
                        status TEXT NOT NULL DEFAULT 'COMPRADO'
                            CHECK (status IN ('SOLICITADO', 'COTADO', 'EM_COTACAO', 'APROVADO', 'COMPRADO', 'AGUARDANDO_ENTREGA', 'ENTREGA_PARCIAL', 'COM_PENDENCIA', 'EM_TROCA', 'ENTREGA_COMPLETA', 'RECEBIDO', 'CONCLUIDO', 'CANCELADO')),
                        observacao TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (obra_id) REFERENCES obras (id) ON DELETE RESTRICT,
                        FOREIGN KEY (requisicao_id) REFERENCES requisicoes (id) ON DELETE SET NULL,
                        FOREIGN KEY (fornecedor_id) REFERENCES fornecedores (id) ON DELETE RESTRICT
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS pedido_itens (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        pedido_id INTEGER NOT NULL,
                        material_id INTEGER NOT NULL,
                        quantidade_solicitada REAL NOT NULL CHECK (quantidade_solicitada > 0),
                        quantidade_comprada REAL NOT NULL DEFAULT 0 CHECK (quantidade_comprada >= 0),
                        quantidade_recebida REAL NOT NULL DEFAULT 0 CHECK (quantidade_recebida >= 0),
                        quantidade_aceita REAL NOT NULL DEFAULT 0 CHECK (quantidade_aceita >= 0),
                        quantidade_pendente REAL NOT NULL DEFAULT 0 CHECK (quantidade_pendente >= 0),
                        unidade TEXT NOT NULL,
                        custo_unitario REAL NOT NULL DEFAULT 0 CHECK (custo_unitario >= 0),
                        status TEXT NOT NULL DEFAULT 'PENDENTE',
                        FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE CASCADE,
                        FOREIGN KEY (material_id) REFERENCES materiais (id) ON DELETE RESTRICT
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS entregas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        pedido_id INTEGER NOT NULL,
                        data_prevista TEXT,
                        data_recebimento TEXT NOT NULL,
                        nota_fiscal TEXT,
                        status TEXT NOT NULL DEFAULT 'RECEBIDA'
                            CHECK (status IN ('RECEBIDA', 'PARCIAL', 'RECUSADA')),
                        observacao TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE CASCADE
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS entrega_itens (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        entrega_id INTEGER NOT NULL,
                        pedido_item_id INTEGER NOT NULL,
                        quantidade_esperada REAL NOT NULL DEFAULT 0 CHECK (quantidade_esperada >= 0),
                        quantidade_recebida REAL NOT NULL DEFAULT 0 CHECK (quantidade_recebida >= 0),
                        quantidade_aceita REAL NOT NULL DEFAULT 0 CHECK (quantidade_aceita >= 0),
                        quantidade_recusada REAL NOT NULL DEFAULT 0 CHECK (quantidade_recusada >= 0),
                        material_correto INTEGER NOT NULL DEFAULT 1 CHECK (material_correto IN (0, 1)),
                        motivo_recusa TEXT,
                        observacao TEXT,
                        FOREIGN KEY (entrega_id) REFERENCES entregas (id) ON DELETE CASCADE,
                        FOREIGN KEY (pedido_item_id) REFERENCES pedido_itens (id) ON DELETE RESTRICT
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS trocas (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        pedido_item_id INTEGER NOT NULL,
                        fornecedor_id INTEGER NOT NULL,
                        entrega_item_id INTEGER,
                        data_solicitacao TEXT NOT NULL,
                        quantidade REAL NOT NULL CHECK (quantidade > 0),
                        motivo TEXT NOT NULL,
                        status TEXT NOT NULL DEFAULT 'SOLICITADA'
                            CHECK (status IN ('SOLICITADA', 'AGUARDANDO_FORNECEDOR', 'ENVIADA', 'EM_TRANSPORTE', 'RECEBIDA', 'CANCELADA')),
                        previsao_troca TEXT,
                        data_envio TEXT,
                        data_recebimento TEXT,
                        observacao TEXT,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (pedido_item_id) REFERENCES pedido_itens (id) ON DELETE RESTRICT,
                        FOREIGN KEY (fornecedor_id) REFERENCES fornecedores (id) ON DELETE RESTRICT,
                        FOREIGN KEY (entrega_item_id) REFERENCES entrega_itens (id) ON DELETE SET NULL
                    )
                    """);

            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS historico_pedidos (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        pedido_id INTEGER NOT NULL,
                        data_evento TEXT NOT NULL,
                        tipo TEXT NOT NULL,
                        descricao TEXT NOT NULL,
                        created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        FOREIGN KEY (pedido_id) REFERENCES pedidos (id) ON DELETE CASCADE
                    )
                    """);

            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_materiais_obra ON materiais (obra_id, ativo)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pedidos_obra_status ON pedidos (obra_id, status)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pedidos_fornecedor ON pedidos (fornecedor_id, status)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_pedidos_previsao ON pedidos (data_prevista_entrega)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_entregas_pedido ON entregas (pedido_id, data_recebimento)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_trocas_status ON trocas (status)");
            statement.executeUpdate("CREATE INDEX IF NOT EXISTS idx_historico_pedido ON historico_pedidos (pedido_id, data_evento)");
        }
    }
}
