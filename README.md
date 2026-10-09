# Megalodonte Video Editor

Editor desktop inicial baseado no código do `megalodonte-cliente`, sem copiar o `.git`. A tela é declarada em `src/main/resources/views/editor.view.jsonc`; o script `Editor.java` coordena as ações e `VideoProject.java` mantém o modelo e a persistência. Login e cadastro da base foram preservados como exemplos, mas não fazem parte do fluxo do editor.

## Executar

Requer JDK 25 e ambiente gráfico com suporte a JavaFX.

```bash
bash gradlew run
```

No Windows: `./run.ps1`. Os projetos irmãos `megalodonte-script-app` e `megalodonte-jsonc-ui` e os módulos do ecossistema são usados automaticamente via composite builds. Os novos componentes precisam desta versão local do JSONC UI; para usar fora do workspace, publique antes as bibliotecas atualizadas em Maven.

## Fluxo atual

1. Cole o roteiro no painel à direita, separando as cenas com uma linha em branco.
2. Clique em **Criar cenas do roteiro**. A separação é local e determinística; não utiliza IA.
3. **Importar imagens** aceita PNG, JPEG e GIF. Os arquivos são ordenados lexicograficamente pelo nome: use `001`, `002`, `003` para indicar a ordem. O lote preenche as cenas sem imagem; arquivos excedentes criam novas cenas. A ordem fica visível na timeline.
4. Selecione uma cena para ver sua imagem, alterar a duração, mover à esquerda/direita, trocar a imagem ou remover a cena. O intervalo inicial é de cinco segundos. Os botões Mover ←/Mover → movem a cena selecionada na sequência.
5. **Salvar projeto** grava um manifesto JSON e uma pasta de imagens ao lado dele. **Abrir projeto** restaura o roteiro, as cenas, a ordem e as durações. Mantenha o manifesto e sua pasta `.assets` juntos para transportar o projeto.

O formato é fixo: **YouTube horizontal 16:9**. A prévia mantém um quadro 16:9, com barras pretas quando a imagem tem outra proporção. O formato também é gravado no manifesto do projeto.

**▶ Play** reproduz a sequência respeitando a duração de cada cena. O mesmo botão pausa; **↺ Início** interrompe e retorna ao começo. O contador mostra o tempo atual e o total. Ao terminar, Play reinicia a sequência. Selecionar uma cena posiciona a reprodução no início dela.

A timeline tem miniaturas, intervalos cumulativos e rolagem horizontal. A roda do mouse e o gesto horizontal do trackpad rolam a timeline; a barra pode ser arrastada. Os painéis central e direito usam o scroll v2 do Megalodonte. A janela pode ser redimensionada ou maximizada, e os painéis rolam quando o conteúdo ultrapassa a área disponível. Imagens ausentes mostram uma prévia vazia e podem ser substituídas. A prévia e a exportação usam imagens, narração e música locais. Não há chamadas a APIs de geração de imagens ou narração. O roteiro editado não altera cenas existentes automaticamente; recriar as cenas pede confirmação.

## Restaurar o último projeto salvo

Após salvar com sucesso, o editor guarda o caminho absoluto do projeto e o restaura na próxima inicialização, incluindo roteiro, cenas, imagens, áudios e volumes. Abrir outro projeto sem salvá-lo não altera essa preferência. Alterações não salvas continuam sem autosave.

A preferência fica em `$XDG_CONFIG_HOME/megalodonte-video-editor/last-project.properties` ou `~/.config/megalodonte-video-editor/last-project.properties` no Linux, e em `%APPDATA%/megalodonte-video-editor/last-project.properties` no Windows. Se o projeto tiver sido removido, movido ou estiver inválido, o editor abre vazio e informa a falha. Assets ausentes são informados após a restauração.

## Seletores de arquivos e últimas pastas

Os sete fluxos usam um contrato único de seleção: abrir projeto, salvar projeto, importar várias imagens, trocar uma imagem, anexar narração, adicionar música e exportar vídeo. Salvamentos usam **Save**; seleções de entrada usam **Open**. Apenas a importação de imagens aceita múltiplos arquivos. Os filtros aceitam extensões em maiúsculas/minúsculas. Salvamentos acrescentam `.json` ou `.mp4` quando necessário e confirmam substituição do destino final.

O editor lembra as últimas pastas entre reinicializações, em `file-dialogs.properties`, na mesma pasta de configurações de `last-project.properties`. Imagens importadas e troca de imagem compartilham a pasta; narração, música, projetos e exportação têm históricos separados. Abrir e salvar projetos compartilham a pasta de projetos. Pastas removidas usam a pasta do projeto atual ou a pasta pessoal como alternativa. Cancelar não troca arquivos do projeto nem atualiza a pasta lembrada.

## Áudio e exportação

No painel central, use **Anexar narração** e **Adicionar música de fundo** para escolher arquivos locais. Há um botão para remover cada faixa. Os volumes são independentes, de 0 a 100%; narração começa em 100% e música em 15%. Zero silencia a faixa. **Aplicar volumes** atualiza a prévia e a exportação. Play, Salvar projeto e Exportar vídeo também validam e aplicam os valores digitados.

Os áudios e seus volumes são incluídos no projeto salvo, junto às imagens. Recriar as cenas preserva as faixas. Play reproduz o áudio a partir da posição atual; música repete até o fim do vídeo e a narração fica em silêncio quando termina. A prévia usa JavaFX Media: formatos e codecs disponíveis dependem do sistema. Falhas da prévia são informadas e não impedem exportar com FFmpeg.

**Exportar vídeo** gera MP4 horizontal em **1920 × 1080, 30 fps**, com vídeo H.264 e áudio AAC estéreo, quando há faixas anexadas. Todas as cenas precisam de uma imagem. Imagens são encaixadas sem distorção, com barras pretas; a duração segue a timeline, com precisão de um frame por cena. Narração é cortada no fim do vídeo ou completada com silêncio; música é repetida e cortada no fim. Os volumes aplicados são usados na mistura.

A exportação requer **FFmpeg instalado e disponível como `ffmpeg` no PATH**. Já está disponível no ambiente utilizado para desenvolver este projeto; não é incorporado ao instalador. FFprobe é usado apenas nos testes. Exportar funciona em segundo plano e permite **Cancelar exportação**. O arquivo final só é substituído quando a renderização termina com sucesso. A exportação usa uma cópia do estado do projeto no momento do clique, portanto alterações posteriores não afetam o vídeo em andamento. Fechar o editor cancela a exportação.

## Verificação e distribuição

```bash
bash gradlew test :megalodonte-jsonc-ui:test :megalodonte-script-app:test
bash gradlew installDist
```

Os testes do editor verificam separação do roteiro, portabilidade das imagens e áudios, volumes, leitura de projetos inválidos, exportação real e ações da UI, gerando capturas em `build/preview/`. Os testes JavaFX exigem ambiente gráfico. Os testes de exportação usam FFmpeg/FFprobe e são ignorados quando FFmpeg não está instalado. A distribuição fica em `build/install/megalodonte-video-editor` e requer Java 25.

Detalhes e decisões: [arquitetura](docs/arquitetura.md).
