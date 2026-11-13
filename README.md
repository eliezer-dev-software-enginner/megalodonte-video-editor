# Megalodonte Video Editor

Editor desktop para montar vídeos 16:9 a partir de um roteiro, imagens e áudio locais, com prévia e exportação em MP4. A tela é declarada em `src/main/resources/views/editor.view.jsonc`; o script `Editor.java` coordena as ações e `VideoProject.java` mantém o modelo e a persistência. Login e cadastro da base foram preservados como exemplos, mas não fazem parte do fluxo do editor.

## Executar

Requer JDK 25 e ambiente gráfico com suporte a JavaFX.

```bash
bash gradlew run
```

No Windows, execute `./run.ps1` no PowerShell. O build procura `../../framework/megalodonte-declarative/megalodonte-script-app` e o inclui como composite build quando essa pasta existe. Sem ela, a dependência `megalodonte:megalodonte-script-app:0.1.0` precisa estar disponível no Maven local ou em um repositório configurado. As dependências do próprio script app, incluindo componentes JSONC UI, também precisam estar resolvíveis. Este repositório não distribui esses artefatos.

### Criar um vídeo inteiramente pelo terminal (Linux)

Execute `./video-cli` na pasta do editor. O assistente pede o nome, o roteiro, a pasta opcional de avatares, imagens locais ou geração de imagens pela OpenAI, efeito visual e aplicação de sons. Cole o roteiro em várias linhas, separando cenas com uma linha vazia, e encerre com uma linha contendo apenas `::fim` (também aceita `::Fim` ou `::FIM`, com espaços ao redor). As marcações `#nome-do-avatar.png` funcionam como no editor; informe a pasta que contém esses PNGs. Depois de usar uma pasta, o CLI lembra seu caminho e, na próxima execução interativa, pergunta se você quer reaproveitá-la. Responda **n** para informar outra; uma pasta removida não é reaproveitada. A opção `--avatar-dir` escolhe a pasta diretamente, sem fazer essa pergunta. Para usar imagens próprias, coloque uma por cena em uma pasta, com nomes em ordem (`001.png`, `002.png` etc.). Se deixar essa pasta vazia, o CLI gera imagens pela OpenAI usando a chave salva nas Configurações do editor ou `OPENAI_API_KEY`; essa chamada pode ter custo.

O CLI usa o Ressoa Linux Port para narrar cada cena em português brasileiro ou inglês americano, conforme a escolha interativa ou `--language pt-BR|en-US`. Forneça o roteiro já no idioma desejado: o editor não traduz o texto. O Ressoa recebe os códigos `pt` ou `en`; o sotaque efetivo depende do motor e da voz escolhida. A preferência de idioma também pode ser alterada em **Configurações → Geral** no editor. O editor desktop não sintetiza narração por Ressoa. Com o Ressoa já ativo em `http://127.0.0.1:8768`, o CLI encontra os motores compatíveis e suas vozes. Se não estiver ativo, informe a pasta da instalação, que deve conter `run-linux.sh`, ou defina `RESSONA_HOME`; o CLI inicia o serviço e o encerra ao terminar. O efeito visual inicial é `smooth`. O primeiro motor sugerido é `supertonic3` quando instalado. As escolhas de motor e voz são lembradas nas próximas execuções; instale-os previamente no Ressoa. Para uma voz clonada do OmniVoice, informe também a transcrição exata do áudio de referência (`--reference-text`). FFmpeg e FFprobe são configuráveis em **Configurações → Ferramentas**.

Cada fala é gerada separadamente, sem pausa adicionada pelo Ressoa. Esses trabalhos internos não enviam notificações do Ressoa nem criam cópias soltas em Downloads. O FFprobe mede a duração de cada WAV, o FFmpeg junta a narração, e a duração da cena acompanha o áudio correspondente. Se optar por sons, o CLI pede a pasta de origem (que precisa conter arquivos `.txt` descrevendo os sons) e a cobertura de efeitos sonoros. O padrão da cobertura é **25%**, com opções de 0%, 25%, 50%, 75% e 100%; a música de fundo não entra nesse percentual. A pasta configurada no editor é sugerida, mas você pode escolher outra para esta execução. Os caminhos das ferramentas continuam vindo das Configurações do desktop. Ao final, grava um projeto editável em `projects/<nome>/<nome>.mvideo.json` e o MP4 em `videos/<nome>.mp4`, ambos relativos à pasta do editor. Nomes já existentes recebem um sufixo numérico; o CLI não substitui um vídeo ou projeto anterior. O processo mostra o andamento no terminal. Se falhar depois de começar, o projeto parcial permanece na pasta de projetos para inspeção.

Para usar arquivos e opções sem responder ao assistente, por exemplo:

```bash
./video-cli --name meu-video --script-file /caminho/roteiro.txt \
  --images-dir /caminho/imagens --avatar-dir /caminho/avatares \
  --language pt-BR --effect smooth --sounds s --sounds-dir /caminho/sons \
  --sound-effect-percent 25 --ressona-home /caminho/ressona-linux-port \
  --engine supertonic3 --voice supertonic3-M1
```

Use `./video-cli --help` para ver todas as opções. `--dry-run` valida roteiro, marcações de avatar e imagens sem gerar áudio, chamar a OpenAI ou exportar. O CLI e o editor compartilham o mesmo formato de projeto; o projeto produzido no terminal pode ser aberto e ajustado na interface gráfica.

Em **Configurações → Geral**, escolha a biblioteca global de imagens geradas. No CLI, informe `--image-library /caminho/para/imagens` ou responda à pergunta sobre a biblioteca; a escolha fica disponível também no editor. O padrão é `Pictures/Megalodonte Video Editor` na pasta pessoal. O editor guarda arquivos com nomes derivados da cena e mantém uma cópia nos assets de cada projeto. O reaproveitamento automático exige texto de cena igual, modelo e parâmetros iguais e contexto idêntico ou suficientemente semelhante. As imagens novas recebem arquivos de metadados com o texto e o contexto para essa comparação local. A busca é conservadora: não interpreta visualmente imagens arbitrárias já existentes na pasta nem garante encontrar todas as imagens adequadas.

## Fluxo atual

1. Cole o roteiro no painel à direita, separando as cenas com uma linha em branco.
2. Clique em **Criar cenas do roteiro**. A separação é local e determinística; não utiliza IA.
3. **Importar imagens** aceita PNG, JPEG e GIF. Os arquivos são ordenados lexicograficamente pelo nome: use `001`, `002`, `003` para indicar a ordem. O lote preenche as cenas sem imagem; arquivos excedentes criam novas cenas. A ordem fica visível na timeline.
4. Selecione uma cena para ver sua imagem, alterar a duração, mover à esquerda/direita, trocar a imagem ou remover a cena. O intervalo inicial é de cinco segundos. Os botões Mover ←/Mover → movem a cena selecionada na sequência.
5. **Salvar projeto** grava um manifesto JSON e uma pasta de imagens ao lado dele. **Abrir projeto** restaura o roteiro, as cenas, a ordem e as durações. Mantenha o manifesto e sua pasta `.assets` juntos para transportar o projeto.

O formato é fixo: **YouTube horizontal 16:9**. A prévia mantém um quadro 16:9, com barras pretas quando a imagem tem outra proporção. O formato também é gravado no manifesto do projeto.

**▶ Play** reproduz a sequência respeitando a duração de cada cena. O mesmo botão pausa; **↺ Início** interrompe e retorna ao começo. O contador mostra o tempo atual e o total. Ao terminar, Play reinicia a sequência. Selecionar uma cena posiciona a reprodução no início dela.

O seletor **Efeito em todas as imagens** aplica a escolha a cada cena: **Movimento suave** cria um zoom e deslocamento lentos, **Partículas** adiciona pontos flutuantes, **Tremor leve** move discretamente o enquadramento, e **Nenhum** remove esses efeitos. Em um projeto já salvo, a escolha é gravada automaticamente e reaparece ao reabrir o editor. Em um projeto novo, clique em **Salvar projeto**. O movimento suave começa no quadro inicial: use **▶ Play** para vê-lo na prévia.

A timeline tem miniaturas, intervalos cumulativos e rolagem horizontal. A roda do mouse e o gesto horizontal do trackpad rolam a timeline; a barra pode ser arrastada. Os painéis central e direito usam o scroll v2 do Megalodonte. A janela pode ser redimensionada ou maximizada, e os painéis rolam quando o conteúdo ultrapassa a área disponível. Imagens ausentes mostram uma prévia vazia e podem ser substituídas. A prévia e a exportação usam imagens, narração e música locais. A geração de imagens já pode usar a OpenAI. A narração continua sendo anexada como arquivo local. O roteiro editado não altera cenas existentes automaticamente; recriar as cenas pede confirmação.

## Restaurar o último projeto salvo

Após salvar com sucesso, o editor guarda o caminho absoluto do projeto e o restaura na próxima inicialização, incluindo roteiro, cenas, imagens, áudios e volumes. Abrir outro projeto sem salvá-lo não altera essa preferência. Alterações não salvas continuam sem autosave.

**Novo projeto** pede um nome e uma pasta de destino. O editor cria imediatamente uma subpasta com esse nome e, dentro dela, o manifesto `nome.mvideo.json` vazio. O projeto fica aberto e pronto para editar; uma pasta existente nunca é substituída.

O botão **☾ Tema lua / ☀ Tema sol** no topo alterna a aparência do editor imediatamente. A escolha é salva nas preferências locais e restaurada na próxima abertura; o tema Lua usa a mesma paleta escura do editor Android.

Abaixo do campo **Roteiro**, **Copiar sem avatarização** copia o texto para a área de transferência sem linhas de marcação como `#falando_boca-aberta.png`. O roteiro do projeto permanece intacto; as falas e a separação entre cenas são preservadas na cópia.
Ao usar **Gerar e anexar imagens**, essas linhas também são excluídas do contexto enviado à geração. Elas continuam salvas no roteiro do projeto para orientar a exibição do avatar.
Ao remover ou trocar uma marcação de avatar no roteiro de um projeto já aberto, a prévia atualiza o avatar da cena correspondente sem recriar a timeline, desde que a quantidade e o texto das cenas continuem os mesmos. Salve o projeto para persistir a mudança. Ao reabrir um projeto com roteiro e avatares divergentes, o editor também reconcilia as marcações válidas antes de mostrar a prévia.

**Fechar projeto** limpa o roteiro, a timeline e os áudios da sessão, e remove a preferência de restauração para que o editor abra vazio na próxima inicialização. Se houver alterações não salvas, o editor pede confirmação antes de descartá-las. O arquivo do projeto e seus assets permanecem no disco.

Ao abrir o projeto, o editor registra a versão do manifesto. Se outra janela ou processo alterar o arquivo no disco, Salvar projeto e o salvamento durante a geração de imagens recusam sobrescrevê-lo e pedem para reabrir a versão atual. Isso evita perder imagens, narração e tempos sincronizados por salvar uma janela defasada.

A preferência fica em `$XDG_CONFIG_HOME/megalodonte-video-editor/last-project.properties` ou `~/.config/megalodonte-video-editor/last-project.properties` no Linux, e em `%APPDATA%/megalodonte-video-editor/last-project.properties` no Windows. Se o projeto tiver sido removido, movido ou estiver inválido, o editor abre vazio e informa a falha. Assets ausentes são informados após a restauração.

## Seletores de arquivos e últimas pastas

Os sete fluxos usam um contrato único de seleção: abrir projeto, salvar projeto, importar várias imagens, trocar uma imagem, anexar narração, adicionar música e exportar vídeo. Salvamentos usam **Save**; seleções de entrada usam **Open**. Apenas a importação de imagens aceita múltiplos arquivos. Os filtros aceitam extensões em maiúsculas/minúsculas. Salvamentos acrescentam `.json` ou `.mp4` quando necessário e confirmam substituição do destino final.

O editor lembra as últimas pastas entre reinicializações, em `file-dialogs.properties`, na mesma pasta de configurações de `last-project.properties`. Imagens importadas e troca de imagem compartilham a pasta; narração, música, projetos e exportação têm históricos separados. Abrir e salvar projetos compartilham a pasta de projetos. Pastas removidas usam a pasta do projeto atual ou a pasta pessoal como alternativa. Cancelar não troca arquivos do projeto nem atualiza a pasta lembrada.

## Áudio e exportação

No painel central, use **Anexar narração** e **Adicionar música de fundo** para escolher arquivos locais. Há um botão para remover cada faixa. Os volumes são independentes, de 0 a 100%; narração começa em 100% e música em 15%. Zero silencia a faixa. **Aplicar volumes** atualiza a prévia e a exportação. Play, Salvar projeto e Exportar vídeo também validam e aplicam os valores digitados.

Os áudios e seus volumes são incluídos no projeto salvo, junto às imagens. Recriar as cenas preserva as faixas. Play reproduz o áudio a partir da posição atual; música repete até o fim do vídeo e a narração fica em silêncio quando termina. A prévia usa JavaFX Media: formatos e codecs disponíveis dependem do sistema. Falhas da prévia são informadas e não impedem exportar com FFmpeg.

No painel direito, **Aplicar sons** usa a pasta definida em **Configurações → Geral**. Crie as cenas e salve o projeto antes de clicar. Nessa mesma seção, escolha a **Cobertura de efeitos sonoros**: 0%, 25%, 50%, 75% ou 100% das cenas. Por exemplo, em um vídeo com quatro cenas, 25% permite um efeito em uma cena. O editor distribui os efeitos do início ao fim do vídeo e prioriza sons cujas tags combinam com cada cena; se não houver correspondência direta, considera o tema do roteiro inteiro. Um mesmo arquivo pode ser reutilizado em cenas diferentes. Se a biblioteca não tiver sons compatíveis, o resultado pode ficar abaixo do percentual escolhido. A música de fundo não entra nessa conta e pode ser adicionada mesmo em 0%. Reduzir a cobertura depois não remove efeitos já anexados.

A prévia prepara os efeitos pouco antes de sua entrada, mesmo quando o mesmo arquivo é usado em muitas cenas. Ao usar **Aplicar sons**, novos efeitos recebem volume ajustado ao nível do arquivo de origem; sons naturalmente baixos deixam de receber o mesmo volume de um efeito já alto. O volume de cada efeito ainda pode ser ajustado manualmente na timeline.

A biblioteca precisa conter arquivos de áudio descritos em arquivos TXT na mesma pasta de cada áudio: cada entrada usa um cabeçalho `[nome-do-arquivo.ext]` seguido de tags `#` (por exemplo, `#musica #chuva` para uma trilha ou `#efeito #chuva` para um efeito). Uma tag descritiva deve corresponder a palavras do roteiro para que o som seja escolhido. O editor seleciona uma trilha ambiente correspondente e a repete até o fim do vídeo em volume baixo; também coloca efeitos correspondentes nas cenas e os exibe na timeline de efeitos. A ação preserva música existente e não duplica os efeitos ao clicar novamente. Novos arquivos de som são copiados para os assets do projeto; imagens e narração existentes não são recopiadas.

**Exportar vídeo** gera MP4 horizontal em **1920 × 1080, 30 fps**, com vídeo H.264 e áudio AAC estéreo, quando há faixas anexadas. Todas as cenas precisam de uma imagem. Imagens são encaixadas sem distorção, com barras pretas; a duração segue a timeline, com precisão de um frame por cena. Narração é cortada no fim do vídeo ou completada com silêncio; música é repetida e cortada no fim. Os volumes aplicados são usados na mistura.

A exportação requer **FFmpeg** e a medição/sincronização da narração requer **FFprobe**. Em **Configurações → Ferramentas**, informe os executáveis caso não estejam no `PATH`; campos vazios usam a detecção automática. O FFmpeg também é usado para medir os efeitos sonoros. Essas ferramentas não são incorporadas à distribuição. Exportar funciona em segundo plano e permite **Cancelar exportação**. O arquivo final só é substituído quando a renderização termina com sucesso. A exportação usa uma cópia do estado do projeto no momento do clique, portanto alterações posteriores não afetam o vídeo em andamento. Fechar o editor cancela a exportação.

## Verificação e distribuição

```bash
bash gradlew test
bash gradlew installDist
```

Os testes do editor verificam separação do roteiro, portabilidade das imagens e áudios, volumes, leitura de projetos inválidos, exportação real e ações da UI, gerando capturas em `build/preview/`. Os testes JavaFX exigem ambiente gráfico. Os testes de exportação são ignorados quando `ffmpeg` não está disponível; se `ffmpeg` estiver disponível, também é necessário ter `ffprobe` no PATH para esses testes. A distribuição fica em `build/install/megalodonte-video-editor` e requer Java 25.

Detalhes e decisões: [arquitetura](docs/arquitetura.md).


## Gerar imagens com OpenAI

1. Cole um roteiro curto, separando as cenas por uma linha em branco, e clique em **Criar cenas do roteiro**.
2. Salve o projeto para definir onde os assets serão gravados.
3. Abra **Configurações** e informe a chave no campo oculto **OpenAI API key**, ou inicie o editor com `OPENAI_API_KEY` configurada.
4. Clique em **Gerar e anexar imagens**. O editor processa todas as cenas sem imagem em sequência; cada PNG recebido é anexado à cena correspondente e o manifesto é atualizado logo após cada resultado. Com **Notificar no Telegram** ativado em **Configurações → Geral**, o editor avisa ao concluir ou falhar, identificando o projeto. O cancelamento não envia notificação.

O botão processa somente cenas sem imagem, então pode ser usado novamente após uma falha ou cancelamento sem repetir cenas já concluídas. Quando não há correspondência na biblioteca global, cada nova imagem exige uma chamada à API e pode gerar cobrança. O roteiro é separado localmente; o texto de cada cena e um trecho de contexto são enviados ao endpoint de imagens. Por padrão, o editor pede uma composição cinematográfica genérica, sem textos ou logotipos. Para outro estilo, abra **Configurações → Imagens do projeto** e escreva as instruções do projeto, por exemplo:

```text
Ilustração vetorial flat para vídeo de treinamento corporativo, fundo claro, paleta azul e ciano.
Telas e documentos sem texto legível; nenhum logotipo ou marca real.
```

Esse prompt substitui somente as instruções padrão: o contexto do roteiro e o texto da cena continuam sendo acrescentados automaticamente. Ele fica salvo no manifesto como `imagePrompt` e vale apenas para esse projeto; deixe vazio ou use **Usar prompt padrão** para voltar ao comportamento normal. Imagens já anexadas não são refeitas, e imagens da biblioteca geradas com outras instruções não são reaproveitadas. Os padrões são `gpt-image-2`, `1536x864` (16:9), qualidade `low` e PNG. São configuráveis via `OPENAI_IMAGE_MODEL`, `OPENAI_IMAGE_SIZE` e `OPENAI_IMAGE_QUALITY`; as combinações precisam ser suportadas pelo modelo escolhido. Contrato verificado na [API oficial de geração de imagens](https://developers.openai.com/api/reference/resources/images/methods/generate).

### Texto breve sobre as imagens

Para mostrar um texto curto sobre cada cena sem regerar imagens, abra **Configurações → Imagens do projeto** e escolha **Mostrar texto breve sobre a imagem de cada cena**. O texto aparece numa caixa escura no canto inferior esquerdo, na prévia e no MP4 exportado, por cima dos efeitos visuais (o movimento suave e o tremor não deslocam o texto). Com a opção desativada, nada é desenhado.

Para editar o texto de uma cena, selecione-a, preencha **Texto breve** no painel da cena (até 90 caracteres, no máximo duas linhas na tela) e clique em **Aplicar texto**. Se o campo ficar vazio, o editor usa uma sugestão local tirada da fala: a primeira frase ou, se ela for longa, sua primeira oração. A dica abaixo do campo mostra a sugestão em uso. Salve o projeto para persistir; os textos ficam em `clips[].caption` e a opção em `sceneCaptions`. Recriar as cenas mantém o texto das cenas cuja fala não mudou.

A API usa a chave informada e pode gerar cobrança na conta correspondente. A chave informada em Configurações é salva somente nas preferências locais do aplicativo, com permissões restritas, e nunca entra no manifesto do projeto. Ao abrir um projeto antigo que ainda contenha `metadata.openaiApiKey`, o editor migra a chave para as preferências locais quando elas não têm uma chave definida e remove o campo do manifesto sem alterar as cenas nem os assets. Uma chave local já configurada prevalece. A variável de ambiente continua como alternativa quando o campo está vazio. Não há retentativas automáticas: se a API falhar, as cenas já prontas permanecem anexadas; clicar novamente solicita somente as pendentes. Cancelar interrompe o lote e conserva as imagens anexadas. Uma solicitação já enviada pode ter sido processada pela API antes do cancelamento.

A geração ocorre em segundo plano. Alterações estruturais ficam bloqueadas até o término ou cancelamento. PNGs recebidos em base64 são validados e gravados em `<projeto>.assets/generated/`; o salvamento normal incorpora os arquivos ao manifesto portátil.

### Teste real, executado explicitamente

Exemplos de roteiro: `examples/roteiro-curto.txt` (duas cenas) e `examples/roteiro-uma-cena.txt`.

Com uma chave no ambiente:

```bash
bash gradlew imageIntegrationSmoke
```

Essa tarefa chama a API real; não faz parte dos testes comuns. Para um destino persistente novo:

```bash
bash gradlew imageIntegrationSmoke -PscriptFile=examples/roteiro-uma-cena.txt -PprojectOutput=projects/meu-teste/projeto.mvideo.json
```

Os arquivos em `projects/` são locais e ignorados pelo Git; um clone novo não contém o projeto usado na validação de 9 de outubro de 2026. Depois de gerar um projeto com a tarefa acima, abra o manifesto pelo botão **Abrir projeto** ou execute, ajustando o caminho ao arquivo criado:

```bash
bash gradlew run -PinitialProject=projects/meu-teste/projeto.mvideo.json
```

`initialProject` abre o arquivo uma vez, sem trocar a preferência do último projeto salvo até que você salve. A pasta `projects/` é ignorada pelo Git para preservar os artefatos locais sem versionar imagens grandes.

Para renderizar e verificar um projeto gerado, sem novas chamadas à API:

```bash
bash gradlew test --tests '*OpenAiGeneratedProjectUiTest' -PliveProject=projects/meu-teste/projeto.mvideo.json
```

Os testes normais usam um servidor HTTP local com PNGs de teste e não consomem a API. Capturas da integração real ficam em `build/preview/openai-live-scene-1.png` e `openai-live-scene-2.png`.

### Movimento suave na exportação

Use **Efeito em todas as imagens → Movimento suave** para adicionar zoom e deslocamento discretos às cenas. O efeito escolhido e a intensidade configurada são salvos com o projeto e usados na exportação. Projetos antigos que usam `animateImages: true` continuam compatíveis; a duração continua definida na timeline.

Para uma ênfase pontual, projetos que tenham `visualEffect: "shake"` em uma cena exibem um tremor leve na prévia e na exportação; `visualEffect: "particles"` mostra pontos luminosos flutuantes. O campo é opcional e fica separado da narração.

### Narração e duração das cenas

O CLI gera e mede separadamente a narração de cada cena, definindo sua duração automaticamente. Na exportação, os limites acumulados são arredondados aos quadros para evitar desvio progressivo.

Ao anexar uma gravação única no editor, a transcrição local com Whisper começa automaticamente em segundo plano e ajusta as durações das cenas conforme o roteiro. O andamento aparece logo abaixo de **Anexar narração**; se houver falha, use **Repetir sincronização** depois de corrigir a causa indicada. As cenas criadas a partir do roteiro começam com 5 segundos cada, um valor provisório até a sincronização. Confira as transições na prévia. A exportação avisa quando a narração ultrapassa significativamente a duração total das cenas.

Para usar a sincronização neste computador, instale o modelo local uma vez: `python3 -m venv .local-asr/venv` e `.local-asr/venv/bin/python -m pip install -r tools/requirements-asr.txt`. O modelo `small` é baixado no primeiro uso e guardado em `.local-asr/models`; o áudio não é enviado a uma API. O idioma é definido em **Configurações → Geral** e compartilhado com o CLI. A distribuição inclui os scripts; o ambiente Python e o cache ficam na instalação local. Projetos e arquivos anteriores permanecem intactos.

### Configurações e prompt base

O botão **Configurações** abre uma janela com um menu lateral à esquerda. Escolha **Geral** para a chave OpenAI, pastas de imagens e sons, idioma da narração do CLI, cobertura de efeitos e **Notificar no Telegram**; **Efeitos visuais** para o efeito padrão e sua intensidade; **Ferramentas** para FFmpeg e FFprobe; **Avatar** para a pasta de avatares do projeto; **Prompt base** para as instruções da geração; ou **Imagens do projeto** para o prompt de imagens do projeto aberto. Os valores permanecem ao trocar de seção. A notificação informa a conclusão ou falha da geração de imagens e exportação; cancelamentos não enviam mensagem. Configure `MEGALODONTE_TELEGRAM_BOT_TOKEN` e `MEGALODONTE_TELEGRAM_CHAT_ID` em `~/.config/megalodonte/megalodonte-video-editor/.env` (Linux) ou `%APPDATA%\Megalodonte\megalodonte-video-editor\.env` (Windows). O arquivo legado `~/.config/megalodonte/telegram.properties` também é aceito. **Salvar configurações** grava as preferências gerais em `editor-settings.json`; a pasta de avatar é salva no projeto. No Linux, o arquivo de preferências tem permissões 600 e a chave OpenAI não entra no arquivo do projeto.

Em **Avatar**, digite o caminho de uma pasta existente ou use **Selecionar pasta…**. Por exemplo, `~/Imagens/meu-avatar` pode conter o avatar mestre e suas variações. Cada projeto guarda seu próprio caminho; ao abrir outro projeto, a seção mostra a pasta associada a ele. Deixe o campo vazio quando não houver avatar. No roteiro, inicie uma cena com o nome exato do PNG precedido por `#`, por exemplo `#falando_boca-aberta.png`, seguido pela fala na linha seguinte. O marcador não é narrado. O editor procura a imagem inclusive nas subpastas, exibe o avatar sobre o fundo na prévia e o incorpora ao MP4 exportado. O editor guarda o caminho externo, portanto selecione novamente uma pasta válida se mover o projeto para outro computador.

Para ajustar o visual, escolha um efeito padrão e uma intensidade em **Efeitos visuais**. O valor 1 mantém a intensidade normal; valores menores suavizam e maiores intensificam o movimento ou as partículas. O efeito padrão vale para cenas novas. O efeito escolhido nas cenas e a intensidade do projeto são recuperados ao reabrir o projeto, e a exportação usa essa intensidade.

A biblioteca de sons vem configurada com um caminho local do ambiente de desenvolvimento, que pode não existir em outro computador. Antes de usar **Aplicar sons**, selecione em **Configurações** uma pasta de sons existente e legível. Os prompts foram separados por função. Use [roteirista-conversacional.md](docs/prompts/roteirista-conversacional.md) em qualquer agente que converse sobre tema e duração, pesquise e escreva a narração. O prompt editável nas Configurações agora é [configurar-projeto-video.md](docs/prompts/configurar-projeto-video.md): ele recebe o roteiro pronto e orienta a configuração de cenas, imagens, música e efeitos individuais na timeline, sem reescrever as falas. O padrão embarcado fica em `src/main/resources/prompts/base-video.md`. Alterações feitas na janela são preservadas entre reinicializações. **Restaurar prompt padrão** apenas repõe o campo; ainda é necessário salvar para persistir.

O agente de produção pode executar `python3 tools/generation_settings.py` para consultar a pasta e o prompt sem imprimir a chave. Os sons devem ser planejados por cena e anexados individualmente em `audio.effects`, preservando a narração e a música existente. O prompt e a biblioteca orientam o agente de produção; o botão de geração de imagens continua solicitando somente imagens, sem disparar produção automática de áudio.

Para continuar um projeto com regras próprias, passe `--project projects/<pasta>/<arquivo>.mvideo.json`. Quando existir `prompt-base.md` ao lado do manifesto, esse prompt substitui o global na saída do comando; a biblioteca de sons continua vindo das Configurações. Projetos locais podem ter prompts próprios e configurar fades (`animateImages: true`) e uso pontual de `shake`.

### Efeitos sonoros na timeline

A faixa **TIMELINE · EFEITOS SONOROS** mostra cada arquivo na sua entrada em segundos, com duração e volume. **Adicionar som…** usa o seletor nativo e lembra a pasta de efeitos; na primeira importação parte da biblioteca configurada. Selecione um bloco, edite entrada, duração, volume, recorte inicial e fades e clique em **Aplicar efeito**. **Remover som** remove somente esse clipe. Salve o projeto para persistir as alterações.

A prévia agenda os efeitos e seus fades; a exportação mistura os clipes com narração e música. Efeitos sobrepostos aparecem em linhas diferentes. A duração do vídeo continua sendo definida pelas imagens; sons são limitados ao fim do vídeo e do arquivo. As entradas são absolutas: depois de reorganizar ou alterar a duração das cenas, revise os tempos dos efeitos. Projetos antigos continuam compatíveis.
