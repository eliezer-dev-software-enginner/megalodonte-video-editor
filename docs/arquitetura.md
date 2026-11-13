# Arquitetura e evolução

## Navegação das Configurações

A janela de Configurações usa uma barra lateral com Geral, Efeitos visuais, Ferramentas, Avatar, Prompt base e Imagens do projeto. A seção ativa aparece no painel à direita; as demais ficam ocultas e não ocupam espaço. Os estados dos campos continuam vivos durante a navegação e o botão Salvar configurações grava tudo de uma vez. A estrutura e os controles permanecem declarados em `settings.view.jsonc`.

Na seção Geral, **Cobertura de efeitos sonoros** permite 0%, 25%, 50%, 75% ou 100% das cenas. O botão **Aplicar sons** prioriza as combinações cena/arquivo com maior relevância de tags e limita a quantidade de cenas com efeitos ao teto desse percentual; cenas que já têm efeitos não são apagadas ao reduzir a preferência. A música de fundo é selecionada independentemente desse limite. Preferências antigas usam 100%, reproduzindo a cobertura anterior.

A seleção divide a timeline em faixas distribuídas do início ao fim, em vez de concentrar todos os sons nas cenas com maior pontuação. Em cada faixa, prefere a correspondência de tags da cena; quando ela não existe, usa as tags relacionadas ao roteiro completo como alternativa. O mesmo arquivo pode tocar em cenas diferentes, com uma penalidade leve para evitar repetições consecutivas. Efeitos já presentes ocupam sua faixa e não são duplicados. `SoundProjectStore` copia uma fonte apenas uma vez por aplicação e reutiliza o caminho do asset nas entradas da timeline. Uma simulação de leitura do projeto local `video-4`, sem gravá-lo, encontrou 25 novos efeitos antes dos 260 segundos e o primeiro em 0,4 segundo.

Na prévia, `AudioPreview` prepara apenas efeitos próximos da posição de reprodução (3 segundos de antecedência) e libera os players ao passar do fim. Isso evita abrir dezenas de `MediaPlayer` para o mesmo arquivo ao carregar um projeto. Para efeitos novos, `SoundLevel` mede os primeiros 5 segundos com FFmpeg e escolhe um volume por fonte para aproximar a média de -24 dB, limitado a 25–100%; sem FFmpeg ou com áudio ilegível, usa 50%. O projeto local `video-4` tinha 99 efeitos automáticos a 7%, então seus volumes foram atualizados após uma cópia do manifesto; nenhuma mídia original foi alterada.

## Efeitos visuais e restauração

As Configurações guardam o efeito padrão para novas cenas (`none`, `smooth`, `particles` ou `shake`) e a intensidade visual entre 0,25 e 3. O manifesto do projeto guarda o efeito de cada cena e a intensidade usada nele; projetos antigos sem esse campo usam intensidade 1. A prévia e o FFmpeg usam a mesma intensidade. Ao abrir um projeto, a seleção do efeito é reconstruída das cenas, mesmo que o padrão atual nas Configurações seja outro. Aplicar um efeito a todas as cenas salva automaticamente o projeto já aberto; em um projeto novo, é preciso usar Salvar projeto. No instante inicial de uma cena, o movimento suave ainda está em seu quadro inicial; reproduza a prévia para observá-lo.

## Idioma da narração

A preferência global de idioma da narração é compartilhada entre o editor e o CLI. Preferências antigas de idioma são migradas; a detecção automática legada passa a português. A escolha afeta a síntese do CLI, não traduz o roteiro nem altera o idioma da interface.

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


## Reprodução, scroll e formato horizontal — 8 de outubro de 2026

A view recebeu controles de Play/Pausar, retorno ao início e contador. Um `AnimationTimer` usa tempo monotônico para avançar o playhead e localizar a cena pelos intervalos cumulativos. A pausa preserva a posição; o fim da sequência pausa no último frame e o próximo Play reinicia. Mudanças estruturais ou de duração interrompem a reprodução e reiniciam o relógio. O timer é interrompido no descarte da tela. A reprodução é uma prévia visual da sequência de imagens, sem áudio ou codificação de vídeo.

O projeto agora grava `aspectRatio: "16:9"`. Manifestos anteriores sem esse campo continuam abrindo como 16:9; proporções diferentes são rejeitadas. O quadro da prévia é centralizado e medido em 16:9 em qualquer tamanho de janela. Imagens de outras proporções são encaixadas com barras pretas, sem distorção.

A timeline deixou de criar `ScrollPane`. Usa `megalodonte.components.v2.HorizontalScroll`, adicionado à biblioteca components como variante horizontal do scroll v2. O componente usa panes recortados, faixa arrastável, roda do mouse, gesto horizontal e teclas de seta/Home/End. Seleções durante a reprodução revelam a cena atual. Mudanças na lista recalculam a largura do conteúdo.

O renderer JSONC recebeu `Scroll`, com exatamente um filho e width/height/fillWidth/fillHeight; ele usa o scroll vertical v2 existente. Os painéis central e direito são declarados com esse componente. O conteúdo do scroll vertical foi marcado como unmanaged para preservar as dimensões calculadas pelo viewport. `getContent`, `getScrollY` e `scrollTo` permitem acesso e navegação programática.

O launcher compartilhado agora configura explicitamente janela redimensionável e limites máximos livres. O editor usa mínimo de 920 × 600, permitindo maximização também em monitores com altura disponível inferior ao mínimo anterior de 780. Os conteúdos maiores são acessíveis por scroll. A configuração e o layout foram testados, incluindo quadro 16:9, rolagem de lotes longos e controles de reprodução.


## Faixas locais de áudio e exportação MP4 — 8 de outubro de 2026

`VideoProject` recebeu `AudioTrack` para narração e música, com caminho local e volume linear (0 a 1). A UI recebe porcentagens de 0 a 100, aceita vírgula decimal e rejeita NaN, infinito e valores fora do intervalo. Os padrões são 1,0 para narração e 0,15 para música. Zero significa mudo. O manifesto v1 ganhou `audio.narration` e `audio.music`; projetos anteriores sem esses campos mantêm os padrões. Caminhos dos áudios seguem as mesmas regras das imagens e os arquivos são copiados para a pasta de assets ao salvar. A recriação de cenas conserva as faixas.

`AudioPreview` usa dois MediaPlayers, controlados pelo playhead da sequência. Volume, play, pause e seek são independentes por faixa. A música repete e a narração toca uma vez. Players são descartados ao trocar áudio, abrir outro projeto ou fechar a sessão. O projeto adicionou o módulo JavaFX Media ao Gradle e ao manifesto de empacotamento. Compatibilidade de prévia depende dos codecs locais; exportar não depende dos MediaPlayers.

`VideoExporter` recebe um snapshot independente da UI. Converte a primeira imagem de cada arquivo para PNG temporário, renderiza cada cena para H.264/yuv420p, concatena os segmentos e combina as faixas em AAC estéreo. O perfil é fixo: 1920 × 1080, 30 fps e 16:9. Durações são quantizadas ao frame mais próximo (mínimo de um frame), e o resultado é limitado à duração total da timeline. As imagens usam scale/pad com proporção preservada. Música é repetida na entrada FFmpeg; narração é completada com silêncio ou cortada. Volume não é normalizado automaticamente pelo mix; um limitador evita saturação da soma.

O FFmpeg é uma dependência executável externa no PATH. Comandos usam listas de argumentos de ProcessBuilder, sem shell; nomes com espaço, acentos e apóstrofos são aceitos. Um diretório temporário ao lado da saída contém segmentos, logs e resultado. O arquivo final só é substituído após sucesso, de forma atômica quando disponível. Falha ou cancelamento preserva uma saída anterior. O exporter impede a escolha de um asset como saída, e a UI impede usar o manifesto do projeto. O worker é separado da thread JavaFX, publica progresso por runLater e pode ser cancelado; o fechamento da sessão interrompe o processo.

Os testes de integração geram MP4 real, inspecionam resolução/codecs/fps, decodificam frames para conferir a ordem das cenas e medem áudio PCM para confirmar música em 15%, repetição após a narração e mute. Também verificam exportação sem áudio, cancelamento, preservação de arquivo existente e limpeza dos temporários. FFmpeg/FFprobe são necessários para esses testes.

Validação final: 24 testes passaram (oito do editor, dez do JSONC UI, seis do script-app), sem testes de exportação ignorados. A distribuição installDist foi gerada com JavaFX Media. Os instaladores específicos de sistema operacional não foram gerados nesta etapa.


## Diálogo de exportação — 9 de outubro de 2026

A exportação usa `FileChooser.showSaveDialog` nativo do JavaFX. A tentativa anterior de substituir o diálogo Linux por Swing foi removida após avaliação do usuário. O filtro MP4, o nome inicial, cancelamento, extensão automática e confirmação de substituição são preservados; o sistema operacional define a aparência e a tradução dos botões.


## Restauração do último projeto salvo — 9 de outubro de 2026

`LastProjectStore` persiste o caminho absoluto do último manifesto salvo em uma preferência por usuário, separada dos assets portáveis. A gravação usa arquivo temporário e substituição atômica quando disponível. O registro só é atualizado depois de `VideoProject.save` terminar com sucesso; uma falha ao guardar a preferência não desfaz o salvamento nem perde o caminho atual da sessão. Abrir um projeto manualmente não atualiza a preferência.

No onMount, o editor tenta carregar o último projeto sem confirmação e aplica o mesmo fluxo de abertura manual: roteiro, cenas, seleção inicial, áudios, volumes e caminho para salvamentos seguintes. Projeto ausente ou inválido resulta em sessão vazia com mensagem; assets ausentes não impedem a restauração. Não há autosave. A propriedade JVM `megalodonte.editor.settingsDir` permite isolar preferências nos testes. Testes verificam persistência entre instâncias, caminhos com espaços/acentos, preservação da preferência em falhas e restauração da UI, incluindo salvar novamente sem abrir um chooser.


## Varredura dos seletores e persistência de pastas — 9 de outubro de 2026

A revisão cobriu os sete fluxos de FileChooser do editor. `FileDialogs` centraliza modo, título, filtro, nome inicial e quantidade de arquivos. Todas as operações usam o seletor nativo JavaFX: `showSaveDialog` para salvar projeto e exportar vídeo, `showOpenMultipleDialog` para importar imagens e `showOpenDialog` para as demais entradas. Filtros incluem todas as combinações de maiúsculas/minúsculas.

A prévia pausa antes de abrir o seletor. O FileChooser recebe a janela JavaFX principal como proprietária. Alertas de confirmação também recebem essa janela como proprietária.

`FileSelections` valida seleção independentemente do filtro: arquivo regular, legível, não vazio e extensão aceita; saída normalizada com extensão, pasta existente e gravável e destino que não seja diretório. Projeto e vídeo não podem sobrescrever assets; vídeo também não pode substituir o manifesto. A primeira gravação de projeto confirma a substituição de arquivo existente; o caminho atual só é alterado após sucesso. Falhas de salvamento conservam o destino atual. A abertura considera também faixas de áudio ao pedir confirmação de descarte. Imagens são decodificadas antes de modificar a timeline; falhas de troca são apresentadas pela UI.

`FileDialogFolders` grava as pastas escolhidas em `file-dialogs.properties`, no diretório de preferências do usuário. Categorias: images (importar/trocar), narration, music, projects (abrir/salvar) e video. O registro é atualizado apenas quando a seleção retornou arquivos e passou pela validação comum; cancelamento não muda a pasta. Guarda-se a pasta navegada, inclusive quando o arquivo é um link simbólico. Configuração ausente, corrompida ou pasta removida permite fallback para pasta do projeto ou home. Gravações são atômicas quando suportadas; falhas da preferência não impedem usar o arquivo escolhido.

Validação: 14 testes do editor passaram, incluindo exportação real, configuração dos sete tipos de diálogo, persistência independente de pastas entre instâncias, extensão automática, entradas vazias/inválidas e proteção de assets. A distribuição foi atualizada.


## Integração com OpenAI Images — 9 de outubro de 2026

`OpenAiImageClient` usa HttpClient do JDK para POST /v1/images/generations, com Authorization Bearer, modelo explícito, n=1, size, quality e output_format=png. Não envia response_format, pois os modelos GPT Image devolvem base64 por padrão. A resposta precisa conter uma imagem decodificável; só então é convertida para PNG e gravada. Timeout é de seis minutos por geração. Redirecionamentos e retentativas automáticas estão desabilitados. Erros não imprimem a chave, headers de autorização, corpo base64 ou corpo bruto de erro da API. `java.net.http` foi acrescentado ao empacotamento.

`ImageGenerationJob` trabalha com uma cópia do projeto e processa sequencialmente as cenas pendentes. Antes de chamar a API, consulta a biblioteca global por correspondência exata de cena, parâmetros de geração e contexto idêntico ou com alta sobreposição de palavras. Os nomes combinam palavras da cena e um identificador derivado do conteúdo. Cada imagem gerada tem metadados locais para comparação e é copiada aos assets do projeto. O script aplica os resultados por índice na thread JavaFX, aguarda a aplicação/salvamento antes de seguir e impede alterações estruturais durante o lote. Falhas ou cancelamento mantêm os resultados já anexados. A autenticação vem de OPENAI_API_KEY ou da chave definida nas Configurações. A chave fica apenas nas preferências locais; o manifesto nunca a recebe. Ao abrir um projeto legado com `metadata.openaiApiKey`, o editor migra a chave se as preferências não tiverem uma e remove o campo legado do arquivo.

`ImageIntegrationSmoke` é uma tarefa opt-in com entrada de roteiro e destino novo. Os testes comuns usam um HttpServer local para verificar headers, payload, ordem, decoding, persistência, cancelamento e erros HTTP sem custos de API. `OpenAiGeneratedProjectUiTest` é opt-in e apenas renderiza imagens já geradas: não solicita novas imagens. A propriedade initialProject permite abrir um artefato de integração no editor sem alterar preferências pessoais.

Teste real concluído: duas chamadas à OpenAI usando gpt-image-2, qualidade low e PNG 1536 × 864. As duas cenas do roteiro curto foram salvas e abertas na UI real do editor, com verificação da proporção 16:9, da ordem na timeline e da prévia de cada cena. O projeto está em projects/openai-real-20261009 e não depende de credenciais para visualizar ou exportar os arquivos já gerados. O campo legado com a chave foi removido desse manifesto na migração para as preferências locais.

## Movimento de imagens — produção documental

O manifesto aceita `animateImages` (false em projetos antigos). Quando true, o exporter aplica aproximação central suave de 5% por cena e fades curtos, sem mudar sua duração. A opção é preservada em snapshots e salvamentos. A montagem de três minutos sobre Google e energia nuclear usa esse modo; roteiro limpo, fontes e prompt editorial foram salvos junto ao projeto local. A sincronização definitiva depende da narração externa.

## Duração das cenas e narração

O CLI sintetiza e mede cada áudio de cena separadamente, atribui sua duração à cena e concatena as narrações. O exportador arredonda os limites acumulados aos quadros de 30 fps, evitando o acúmulo do erro de arredondamento individual. Ao anexar uma narração única no editor, `WhisperSync` executa os scripts locais de transcrição e alinhamento em segundo plano, salva os tempos das cenas e atualiza a timeline. O botão de repetição serve para recuperar uma falha; o alinhador rejeita fala divergente em vez de aplicar tempos incorretos. O modelo padrão é `small`, com idioma da preferência geral, busca 5, quatro threads e VAD ativo. Arquivos de projetos e caches anteriores não são removidos.

## Configurações e prompt base — 12 de outubro de 2026

`EditorSettings` persiste chave, diretório de sons, prompt base e pausa mínima para troca de cena em JSON UTF-8, separado dos projetos. A escrita usa arquivo temporário, substituição atômica e permissões POSIX 600. A configuração ausente usa o prompt embarcado e a biblioteca `/home/eliezer/Desktop/backup/sounds`. Uma chave local não vazia prevalece sobre credenciais de projetos antigos; quando está vazia, a abertura de um projeto legado migra a chave do manifesto para as preferências locais e remove o campo do projeto. Salvamentos futuros não escrevem credenciais no projeto.

O prompt foi separado em dois papéis: [roteirista conversacional](prompts/roteirista-conversacional.md) cuida da conversa, pesquisa e falas; [produtor do projeto](prompts/configurar-projeto-video.md) recebe o roteiro final e configura imagens, efeitos e música no editor. O segundo é o padrão embarcado e substitui o prompt salvo anteriormente nas preferências locais.

`SettingsWindow` usa um Stage JavaFX com dono e modalidade, enquanto campos, botões, textos e scroll vêm de `settings.view.jsonc` e de uma UiSession independente. Salvar valida a pasta, o prompt e a pausa mínima antes de persistir; cancelar não publica alterações. A sessão e seus listeners são descartados ao fechar, inclusive no encerramento do editor. O campo de chave foi removido do cabeçalho principal. O seletor de pasta é um DirectoryChooser nativo.

O prompt padrão foi derivado integralmente do anexo Canal Dark de Curiosidades, preservando a identidade documental, a verificação dos fatos, a duração padrão de 6 a 10 minutos, a formatação por cena e as chamadas do canal. Novas seções definem o início da produção e a direção sonora. O editor dispõe de narração, música e efeitos individuais em `audio.effects`; a direção sonora deve anexar cada efeito separadamente. Não foi criado um gerador automático de sons no botão de imagens.

`tools/generation_settings.py` expõe somente prompt e diretório, sem imprimir a chave. Testes verificam persistência, cancelamento, reinicialização, validação de pasta, arquivo malformado, proteção de permissões e abertura da janela pela ação declarativa do editor.

## Efeitos individuais

`VideoProject.SoundEffect` guarda arquivo, início absoluto, recorte inicial, duração, volume e fades. Persistência copia cada asset; snapshot copia os efeitos sem compartilhar objetos. A ausência de `audio.effects` representa uma lista vazia em projetos antigos.

O botão **Aplicar sons** lê os catálogos `TXT.txt` ao lado dos arquivos. Cada seção usa `[nome-exato-do-arquivo.ext]`, uma descrição livre e hashtags; `#trilha`/`#music` classifica música, `#efeito`/`#sound-effect` classifica efeitos, e as demais tags descrevem contexto (por exemplo, `#calmo #relaxante`). O editor cruza as tags descritivas com palavras normalizadas do roteiro e de cada cena. Não há nomes de arquivos nem temas de cenas codificados no seletor; usuários podem adicionar arquivos e tags próprios. Arquivos sem entrada catalogada ou sem tag de categoria são ignorados. A trilha recebe volume 8% e toca durante o vídeo; efeitos relacionados são colocados nas cenas correspondentes, no máximo um por cena e sem duplicar o mesmo arquivo. `SoundProjectStore` incorpora apenas os novos arquivos e elementos de `audio.effects`, preservando imagens e narração sem cópias redundantes. A operação é idempotente e mantém uma cópia do manifesto antes da primeira aplicação.

`SoundSequence` foi adicionado ao módulo megalodonte-jsonc-ui com bindings `items`/`selected` e evento `onSelect`. Usa HorizontalScroll Megalodonte, régua em segundos, blocos selecionáveis por mouse/teclado e linhas adicionais para sobreposições. Os controles de edição permanecem no JSONC do editor.

AudioPreview mantém players independentes, agenda entradas/saídas pelo relógio do vídeo e atualiza envelopes de volume. VideoExporter aplica recorte, volume, fades e atraso por amostra antes da mixagem com limitador. O arquivo original termina naturalmente, sem repetição automática dos efeitos. As entradas são absolutas; mudanças estruturais nas cenas exigem revisão dos tempos.

## Prompt de imagens por projeto — 13 de novembro de 2026

As instruções fixas de `ImageGenerationJob` (estilo cinematográfico genérico) não servem para todo vídeo; um manual de sistema, por exemplo, precisa de ilustrações didáticas e sem texto. O manifesto passou a aceitar `imagePrompt` (string opcional, até 8000 caracteres). Vazio ou ausente mantém exatamente o prompt anterior, `ImageGenerationJob.DEFAULT_PROMPT`, então projetos existentes geram o mesmo pedido e continuam reaproveitando a biblioteca.

Quando preenchido, o prompt do projeto substitui somente as instruções; o editor sempre acrescenta `Contexto do roteiro` (até 4000 caracteres, sem marcações de avatar) e `Cena N: texto`. Assim, o autor do prompt não precisa conhecer placeholders, e a ordem e a identidade das cenas continuam garantidas pelo código.

A identidade de cache da biblioteca global inclui os 16 primeiros caracteres do SHA-256 do prompt próprio. Decisão: uma imagem gerada sob outras instruções não é reaproveitada, mesmo com cena e contexto iguais; o mesmo prompt próprio reaproveita normalmente. O prompt padrão continua sem esse sufixo para preservar a compatibilidade dos metadados já gravados.

Na interface, a seção **Imagens do projeto** em Configurações edita o campo, mostra o prompt padrão para referência e oferece **Usar prompt padrão** (esvazia o campo). O valor segue o mesmo caminho da pasta de avatar: pertence ao projeto aberto, é salvo no manifesto e não entra em `editor-settings.json`. Recriar cenas, snapshots e a detecção de alterações não salvas preservam o campo. O CLI usa o campo quando o projeto o tiver, mas ainda não tem opção para defini-lo em um projeto novo.

Testes: `OpenAiImageIntegrationTest.projectImagePromptReplacesDefaultInstructionsAndPersists` (prompt enviado, separação de cache, persistência e limite) e `EditorViewTest` (navegação, prompt padrão visível e reabertura da janela com o valor do projeto).

## Texto breve sobre a cena — 13 de novembro de 2026

Pedido: mostrar um texto curto sobre cada imagem sem regerar as imagens já pagas. Pedir o texto ao modelo de imagem foi descartado: exigiria regerar, e modelos de imagem escrevem mal (o prompt do FLU004 proíbe texto justamente por isso). O texto passa a ser uma camada do editor.

O manifesto ganhou `sceneCaptions` (boolean do projeto, falso por padrão) e `clips[].caption` (até 90 caracteres). `SceneCaption.effective` devolve vazio com a opção desligada, o texto da cena quando preenchido ou uma sugestão determinística (`suggest`: primeira frase; se tiver mais de oito palavras, a primeira oração; se ainda for longa, sete palavras e reticências). Nenhuma chamada de rede.

Um único renderizador Java2D (`SceneCaption.render`) gera uma camada ARGB transparente do tamanho do quadro, com caixa escura translúcida, faixa ciano e texto branco em até duas linhas, no canto inferior esquerdo (o avatar ocupa o inferior direito). A prévia (`CaptionPreview`) carrega essa camada em 1920×1080 num `ImageView` ajustado ao quadro 16:9, acima do avatar; a exportação grava o mesmo PNG e o sobrepõe no `filter_complex` depois do filtro da cena e do avatar. Assim prévia e MP4 coincidem e o texto não depende de fontes do FFmpeg nem acompanha zoom, deslocamento ou tremor.

A opção fica em **Configurações → Imagens do projeto**, como `Select` declarativo (o JSONC UI não tem checkbox). `SettingsWindow.ProjectOptions` agrupa as opções que pertencem ao manifesto (pasta de avatar, prompt de imagens e texto breve). O texto por cena é editado no painel da cena (`applyCaption`).

Testes: `SceneCaptionTest` (sugestão, validação, persistência, manifesto antigo e área desenhada), `VideoExporterTest.sceneCaptionIsBurnedAfterVisualEffectsOnlyWhenEnabled` (quadros reais em dois instantes, com movimento suave) e `EditorViewTest` (opção, dica, camada da prévia e edição do texto).

