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

A timeline tem miniaturas, intervalos cumulativos e rolagem horizontal. Imagens ausentes mostram uma prévia vazia e podem ser substituídas. Esta versão não reproduz nem exporta vídeo, não tem faixa de áudio e não chama APIs de geração de imagens ou narração. O roteiro editado não altera cenas existentes automaticamente; recriar as cenas pede confirmação.

## Verificação e distribuição

```bash
bash gradlew test :megalodonte-jsonc-ui:test :megalodonte-script-app:test
bash gradlew installDist
```

Os testes do editor verificam separação do roteiro, portabilidade dos assets, leitura de projetos inválidos e ações da UI, gerando capturas em `build/preview/`. Os testes JavaFX exigem ambiente gráfico. A distribuição fica em `build/install/megalodonte-video-editor` e requer Java 25.

Detalhes e decisões: [arquitetura](docs/arquitetura.md).
