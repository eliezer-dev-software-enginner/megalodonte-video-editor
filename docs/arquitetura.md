# Arquitetura e evolução

## Etapa inicial — 8 de outubro de 2026

A base foi copiada do `megalodonte-cliente`. Código, recursos, wrapper Gradle e scripts de distribuição foram preservados; metadados Git, caches, outputs de compilação e arquivos locais da IDE não foram copiados. Nome do projeto, identificação do pacote e UUID do instalador foram alterados. Os testes de login da cópia foram substituídos por testes do editor; os exemplos de login e cadastro permanecem disponíveis.

A UI é orientada a dados: organização dos painéis, textos, botões, estados e referências de eventos são definidos em JSONC. O bootstrap e o registro por convenção continuam sendo responsabilidade do `megalodonte-script-app`. Não foi necessário criar um launcher no aplicativo.

`Editor` controla as ações. `VideoProject` não depende de JavaFX e contém o roteiro e a lista ordenada de cenas (`text`, `image`, `duration`). A seleção da UI é um índice; mover cenas preserva os respectivos textos, imagens e durações. A duração total e os intervalos da timeline são derivados dessa lista, sem gaps.

## Extensões do framework

O `megalodonte-jsonc-ui` recebeu três componentes declarativos:

- `TextArea`: texto multilinha, com `bind.value` string em duas vias, placeholder, width/height e validações de entrada.
- `ImagePreview`: `bind.source` string contendo caminho local; preserva proporção e mostra placeholder quando a imagem não existe ou não é legível.
- `ImageSequence`: `bind.items` string contendo um array JSON de `{label, source, duration}`; `bind.selected` integer com índice da seleção; `events.onSelect` dispara após seleção por mouse ou Enter/Espaço. Width/height controlam o viewport. Os itens precisam ter duração finita positiva.

Os componentes são criados pelo renderer, sem montagem da árvore de UI no script cliente. Assinaturas e listeners são descartados com a sessão. O contrato runtime e o JSON Schema foram ampliados juntos. Não há carregamento de imagens remotas nesses componentes.

## Persistência v1

O manifesto inclui `version: 1`, `script` e `clips`, em ordem. As imagens são copiadas para `<nome-do-manifesto>.assets/` e referenciadas relativamente à pasta do manifesto. Salvar usa um arquivo temporário e substituição atômica quando suportada. Cada cópia recebe um nome único, para não sobrescrever imagens do manifesto anterior em caso de falha. Assets antigos não são removidos automaticamente; salvamentos sucessivos podem aumentar o tamanho da pasta.

Ao abrir, versões incompatíveis, durações inválidas e caminhos de imagens fora da pasta do projeto são rejeitados. Arquivos ausentes não impedem a abertura; a UI informa o problema. Importação e persistência são síncronas nesta primeira versão, portanto lotes muito grandes podem bloquear a UI temporariamente. Os assets são incorporados ao projeto ao salvar; antes disso, a sessão usa os caminhos originais. Não há autosave nem proteção no fechamento da janela: salve antes de fechar.

## Integrações posteriores

A geração de imagens e a narração estão fora do escopo desta etapa. Ao integrar provedores, manter credenciais fora do manifesto e da view. Processar o roteiro em cenas com identidade estável, armazenar resultados no diretório de assets e aplicar os resultados pela ordem original, independentemente da ordem em que as requisições terminem. Usar execução assíncrona e atualizar os estados na thread JavaFX. Adicionar status por cena, retentativa e cancelamento antes de habilitar geração em lote.

Narração precisará de um asset de áudio no modelo e de uma decisão explícita sobre sincronização das durações. Reprodução e exportação de vídeo exigirão um motor de mídia, a ser escolhido em outra etapa.

## Validação desta etapa

Compilação, `installDist` e 20 testes passaram: quatro no editor, dez no JSONC UI e seis no script-app. A UI foi renderizada em 1320 × 820 e 1180 × 780, com verificação de limites da janela. Os testes exercitam validação do roteiro vazio, criação das cenas, reordenação, duração válida/inválida, remoção até a timeline vazia, bindings de texto e carregamento de imagem local. Capturas ficam em `build/preview/editor-1320.png` e `editor-1180.png`. A persistência foi verificada após remover a imagem original. Instaladores MSI/DEB e APIs externas não foram executados nesta etapa.
