# Planejamento Rio Claro

## Modelo do Diario de Obra (RDO)

O diario foi organizado seguindo o modelo tecnico de RDO usado como referencia: identificacao da obra e da data, condicao climatica, efetivo por funcao, equipamentos e ferramentas, atividades executadas, observacoes e intercorrencias.

Em `Diarios registrados`, selecione um registro para editar ou abrir o documento de impressao. O arquivo e gerado em formato A4, com cabecalho, tabelas de efetivo e equipamentos, campos de observacoes e linhas para assinatura do engenheiro responsavel e do fiscal da obra.

Os arquivos novos sao salvos em `exports/rdo_...html`.

Cada obra possui um RDO por data. O botao `Novo RDO` seleciona automaticamente a proxima data livre; para alterar um registro existente, use `Editar RDO selecionado`.

Na lista de diarios, use `Abrir RDO` para abrir o arquivo no navegador e `Imprimir RDO` para enviar diretamente para a impressora quando o sistema oferecer esse recurso. Se a impressao direta nao estiver disponivel, o RDO sera aberto para usar `Ctrl+P`.

O cabecalho do RDO segue a referencia visual da Integral construtora. O campo `NOME DA OBRA` pode ser clicado e preenchido no navegador antes da impressao.

Sistema desktop offline em JavaFX para planejar obras, controlar materiais e operar o almoxarifado em um único fluxo.

O projeto reúne as funções de planejamento de obra e de controle de almoxarifado. O cadastro de materiais e o saldo ficam centralizados; entradas, saídas definitivas, retiradas para uso, devoluções e histórico usam a mesma base SQLite.

## Tecnologias

- Java 21
- JavaFX
- SQLite com JDBC
- Maven
- JUnit

## Executar

```powershell
mvn javafx:run
```

O banco local é criado automaticamente em `data/obra.db`. Essa pasta é ignorada pelo Git para preservar os dados locais de cada instalação.

No menu `Controle`, selecione a obra ativa para consultar saldos, filtrar materiais por tipo, cadastrar ou editar materiais, registrar movimentações e acompanhar retiradas pendentes. O recebimento aceito de uma entrega também gera uma entrada no histórico do almoxarifado.

A área `Planejamento` está reservada para o futuro cronograma de obra. O `Diário de obra` permite registrar atividades, efetivo e funções, clima, observações e intercorrências por data e por obra.

Na tela `Obras`, selecione uma linha e use `Editar obra selecionada` — ou dê duplo clique na obra — para alterar seus dados, datas e status.

Na aba `Editar / imprimir`, um diário selecionado pode ser editado ou exportado como documento HTML técnico em `exports/diario_obra_...html`. O arquivo abre no navegador, permite revisão e impressão, e inclui campos de assinatura do engenheiro responsável e do fiscal da obra.
