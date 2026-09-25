# Controle Geral de Obra

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

Para equipamentos, registre a entrada usando o código cadastrado. Na `Retirada para uso`, selecione o equipamento pelo código, informe o retirante e a empresa locatária; a devolução deve ser feita pela pendência do mesmo código e mantém o vínculo com a empresa.

## Planejamento e acompanhamento diário

Abra `Planejamento` no menu lateral com uma obra ativa. Os filtros de data, área, disciplina, serviço e situação ficam acima das abas e valem para as visões diárias. A área inclui as subáreas: ao selecionar Bloco A, aparecem também os serviços cadastrados em seus pavimentos e salas.

Em `Áreas, disciplinas e serviços`, cadastre primeiro os locais em hierarquia (por exemplo, Bloco A → Térreo → Setor X → Sala 01). Depois escolha uma das disciplinas disponíveis ou cadastre outra. Para cada serviço, selecione **área e disciplina separadamente**, descreva a atividade e informe unidade, quantidade prevista, datas, meta diária opcional, equipe responsável e observações. Identifique os elementos ou trechos separados por vírgula. Um serviço pode conter vários elementos; para adicionar outros, edite o serviço antes de criar dependências.

Na aba `Visão do dia`, escolha a data, a área e o serviço em `Lançamento rápido`. Informe a quantidade executada, o número de trabalhadores e selecione os elementos atendidos quando o serviço os tiver. Horas por trabalhador, equipe, observações e ocorrência são opcionais. Para uma falta de material, selecione o material já cadastrado na obra, se conhecido. Use `Salvar produção` e repita para outras frentes. A tabela mostra disciplina, feito no dia, acumulado, saldo, percentual, situação da produção, situação da frente e produtividade com unidade. O resumo soma quantidades apenas quando têm a mesma unidade; a lista de pendências aponta prazos vencidos, dias registrados sem avanço, dias planejados sem lançamento e ocorrências ainda abertas.

Em `Frentes e liberações`, clique em `Vincular serviços`. No campo 1, escolha o **serviço que libera**; no campo 2, o **serviço que depende**. A lista do destino mostra todos os demais serviços da obra ativa, mesmo que estejam em outra área ou fora dos filtros da tela. Se ele ainda não existir, use `Cadastrar serviço de destino`. Confira os dois locais e os trechos no campo 3 antes de salvar. Serviços antigos sem disciplina também podem ser vinculados. Os trechos do destino entram bloqueados; selecione o vínculo e o trecho para registrar a liberação ou um novo bloqueio com data, responsável e observação. O vínculo fica `Liberado parcialmente` enquanto apenas parte dos trechos estiver confirmada. A produção concluída nunca muda a liberação automaticamente. A tela do serviço mostra pré-requisitos, trechos liberados e bloqueados e serviços sucessores. O histórico mantém cada confirmação.

Exemplo: cadastre `Área externa` e, dentro dela, `Fundação`. Na disciplina `Fundação`, crie `Cravação das estacas pré-moldadas P41 e P43`, unidade `un`, quantidade prevista `2`, elementos `P41, P43`. Crie a próxima etapa **na mesma área**, também com elementos `P41, P43`. Crie a dependência da cravação para a próxima etapa. Em `Visão do dia`, lance a quantidade executada da P41 marcando apenas `P41`. Em `Frentes e liberações`, selecione o vínculo, escolha `P41` e confirme sua liberação com data, responsável e observação. O vínculo mostrará `Liberado parcialmente`: P41 estará liberada e P43 continuará bloqueada até uma confirmação própria.

Em `Histórico e ocorrências`, selecione a produção e clique em `Corrigir lançamento` para atualizar o registro e os elementos atendidos, recalculando o acumulado. Ocorrências também podem ser cadastradas, editadas e marcadas como resolvidas independentemente da produção. A aba lista as mudanças de situação calculadas a partir dos registros e das ocorrências. Valores negativos são recusados; execução acima do previsto é salva com um aviso para conferência. A produtividade é calculada por trabalhador por dia e, quando houver horas, por homem-hora. A comparação com outro dia exige o mesmo serviço e o mesmo conjunto de elementos atendidos; sem essa base, aparece `sem base comparável`.

O módulo usa a mesma base SQLite local (`data/obra.db`). A versão 15 acrescenta disciplinas, elementos, vínculos e histórico de liberação; a versão 16 permite vincular serviços em áreas diferentes da mesma obra. As migrações preservam os dados anteriores. Serviços antigos permanecem como `Sem disciplina (legado)` até serem editados; o cronograma financeiro antigo não é importado automaticamente, pois não tem vinculação confiável com as novas áreas.

O `Diário de obra` permite registrar atividades, efetivo e funções, clima, observações e intercorrências por data e por obra.

Na tela `Requisições`, selecione uma requisição e use `Gerar folha` para abrir a solicitação de fornecimento em formato A4. O documento usa o cabeçalho da Integral Construtora, mostra os códigos e quantidades solicitadas e reserva campos para fornecimento, observações e assinaturas.

Na tela `Obras`, selecione uma linha e use `Editar obra selecionada` — ou dê duplo clique na obra — para alterar seus dados, datas e status.

Na aba `Editar / imprimir`, um diário selecionado pode ser editado ou exportado como documento HTML técnico em `exports/diario_obra_...html`. O arquivo abre no navegador, permite revisão e impressão, e inclui campos de assinatura do engenheiro responsável e do fiscal da obra.
