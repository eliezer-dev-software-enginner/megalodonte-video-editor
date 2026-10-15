# Vídeo 1 — aplicativo de ponto

Roteiro original preservado em `roteiro-original.txt` e no manifesto. 28 cenas visuais agrupam os 111 parágrafos, sem reescrever as falas. Primeira cena usa a fotografia real de Eliezer em composição horizontal 16:9. As demais imagens são ilustrativas e geradas com OpenAI. Música lofi em 12%. Narração ainda não anexada; durações estimadas por contagem de palavras a 150 palavras/minuto. Após anexar a locução, sincronizar as cenas antes da exportação final.

Direção visual corrigida: a foto real do criador aparece apenas na abertura. As outras cenas mostram objetos, interfaces e ambientes sem pessoas ou rostos. A versão anterior está preservada em `versao-com-pessoas/`.

## Narração sincronizada (14/10/2026)

A narração anexada dura 707,58 s. A timeline foi ajustada para 707,60 s em 30 fps usando Whisper local. Correspondência global de 95,0%; os tempos por cena estão em `sincronizacao/relatorio.md`. A cena 27 teve 68,7% de correspondência porque a fala prevista sobre organizar os dados no Firebase foi omitida; a imagem segue adequada à chamada para o próximo vídeo. A primeira fala começa em 3,09 s e a última termina em 704,66 s. O lofi está em 12% e se repete porque sua duração é de 179,18 s. Antes de qualquer novo salvamento pelo editor, reabra este manifesto para carregar a narração, as imagens e os tempos atualizados.

## Partículas visuais

As cenas 1 (foto de Eliezer), 16 (Firebase), 21 (ciclos de desenvolvimento) e 28 (encerramento) usam `visualEffect: "particles"` no manifesto. O efeito produz pontos luminosos discretos em movimento na prévia e na exportação; na abertura eles ficam nas laterais para preservar o retrato. O metadado é separado da fala narrada. Reabra o projeto no editor antes de salvá-lo se ele já estava aberto durante esta alteração.

## Prompt próprio do projeto

`prompt-base.md` reúne as regras de produção desta série, incluindo o uso da foto real de Eliezer, `animateImages: true` para zoom e fades e os efeitos pontuais `particles` e `shake`. O projeto salvo já tem zoom/fades e partículas nas cenas indicadas acima; shakes ficam para uma escolha editorial por cena.

## Efeitos sonoros e restauração do manifesto

Após uma janela antiga salvar o manifesto em 15/10, ele voltou a 674,8 s, perdeu a narração e manteve só 13 referências de imagem. Esse arquivo foi preservado como `video-01.antes-restaurar-som.mvideo.json`. O manifesto atual foi recuperado da versão sincronizada: 28 imagens, narração, 707,6 s e partículas visuais. Reabra o projeto na versão atualizada do editor antes de qualquer salvamento.

A biblioteca de sons atual é majoritariamente industrial, sombria ou de ficção científica, pouco adequada a este vídeo. O candidato `whooshing-by-120-bpm` foi descartado após a leitura da descrição do autor: é uma sequência de sintetizadores pensada para horror/paranormal/ficção científica. `audio.effects` permanece vazio por decisão editorial; a narração e o lofi em 12% foram preservados. Quando houver efeitos adequados, eles podem ser anexados individualmente na timeline.
