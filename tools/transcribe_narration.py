"""Local-only word timestamps for a project's narration; audio never leaves this device."""
import argparse
import hashlib
import json
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('project', type=Path)
    parser.add_argument('--model', default='small')
    parser.add_argument('--language', choices=('pt', 'auto'), default='pt')
    parser.add_argument('--beam-size', type=int, default=5)
    parser.add_argument('--cpu-threads', type=int, default=4)
    parser.add_argument('--vad', choices=('true', 'false'), default='true')
    parser.add_argument('--model-cache', type=Path, default=Path(__file__).resolve().parents[1] / '.local-asr' / 'models')
    args = parser.parse_args()
    if not 1 <= args.beam_size <= 10 or not 1 <= args.cpu_threads <= 16:
        parser.error('beam-size deve ficar entre 1 e 10 e cpu-threads entre 1 e 16')
    manifest = args.project.resolve()
    project = json.loads(manifest.read_text())
    name = project['audio']['narration']['file']
    if not name:
        raise SystemExit('Anexe uma narração antes de transcrever.')
    audio = (manifest.parent / name).resolve()
    if not audio.is_file():
        raise SystemExit('Arquivo de narração ausente.')
    output = manifest.parent / 'sincronizacao'
    output.mkdir(exist_ok=True)
    from faster_whisper import WhisperModel
    cache = args.model_cache.expanduser().resolve()
    print('Carregando Whisper multilingual ' + args.model + ' localmente…', flush=True)
    model = WhisperModel(args.model, device='cpu', compute_type='int8', cpu_threads=args.cpu_threads,
                         download_root=str(cache))
    segments, info = model.transcribe(str(audio), language=None if args.language == 'auto' else args.language,
                                      beam_size=args.beam_size, word_timestamps=True,
                                      vad_filter=args.vad == 'true',
                                      condition_on_previous_text=False, temperature=0)
    records = []
    for segment in segments:
        words = [{'text': word.word, 'start': word.start, 'end': word.end,
                  'probability': word.probability} for word in segment.words or []]
        records.append({'start': segment.start, 'end': segment.end,
                        'text': segment.text, 'words': words})
        print(f'{segment.start:.2f}–{segment.end:.2f}s: {segment.text}', flush=True)
    report = {'model': args.model, 'language': info.language,
              'beamSize': args.beam_size, 'cpuThreads': args.cpu_threads, 'vad': args.vad == 'true',
              'audioSha256': hashlib.sha256(audio.read_bytes()).hexdigest(),
              'segments': records}
    (output / 'transcricao.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    print('Transcrição salva em ' + str(output / 'transcricao.json'), flush=True)


if __name__ == '__main__':
    main()
