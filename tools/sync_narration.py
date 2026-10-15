"""Align existing scene text to local Whisper word timestamps before changing durations."""
import argparse
from difflib import SequenceMatcher
import hashlib
import json
import math
from pathlib import Path
import re
import shutil
import subprocess
import tempfile
import unicodedata


def tokens(text):
    plain = ''.join(c for c in unicodedata.normalize('NFKD', text.casefold())
                    if not unicodedata.combining(c))
    return re.findall(r'[a-z0-9]+', plain)


def align(clips, words, audio_duration, fps=30):
    expected = []
    ranges = []
    for clip in clips:
        start = len(expected)
        expected.extend(tokens(clip['text']))
        ranges.append((start, len(expected)))
    heard = []
    times = []
    for word in words:
        for token in tokens(word['text']):
            heard.append(token)
            times.append((float(word['start']), float(word['end'])))
    if not expected or not heard:
        raise ValueError('Roteiro ou transcrição sem palavras.')
    matching = SequenceMatcher(None, expected, heard, autojunk=False)
    mapping = {block.a + k: block.b + k for block in matching.get_matching_blocks()
               for k in range(block.size)}
    coverage = len(mapping) / len(expected)
    if coverage < .75:
        raise ValueError(f'Áudio diverge do roteiro: cobertura de apenas {coverage:.1%}.')
    scenes = []
    for i, (start, end) in enumerate(ranges):
        pairs = [(j, mapping[j]) for j in range(start, end) if j in mapping]
        ratio = len(pairs) / max(1, end - start)
        anchors = [(j, mapping[j]) for j in range(start, min(end, start + 8)) if j in mapping]
        if ratio < .65 or len(anchors) < min(3, end - start) or (anchors[0][0] > start + 1 and (ratio < .85 or anchors[0][0] > start + 4)):
            raise ValueError(f'Cena {i+1}: correspondência insuficiente; revisar antes de salvar.')
        first = anchors[0][1]
        # A short improvised lead-in may replace the first few words of a scene.
        # Backfill at most two heard words only when the rest matches strongly.
        leading = anchors[0][0] - start
        if leading and first > 0 and (leading == 1 or ratio >= .85):
            first -= min(leading, 2)
        scenes.append({'scene': i+1, 'text': clips[i]['text'], 'coverage': ratio,
                       'speechStart': times[first][0], 'speechEnd': times[pairs[-1][1]][1]})
    boundaries = [0]
    for previous, following in zip(scenes, scenes[1:]):
        if following['speechStart'] < previous['speechEnd'] - .2:
            raise ValueError('Tempos de fala conflitantes; revisar a transcrição.')
        midpoint = (previous['speechEnd'] + following['speechStart']) / 2
        boundaries.append(round(midpoint * fps))
    boundaries.append(math.ceil(audio_duration * fps))
    for i, scene in enumerate(scenes):
        if boundaries[i+1] - boundaries[i] < 3:
            raise ValueError('Uma cena ficou com menos de 0,1 segundo; revisar.')
        scene.update({'start': boundaries[i] / fps, 'end': boundaries[i+1] / fps,
                      'duration': (boundaries[i+1] - boundaries[i]) / fps})
    if scenes[-1]['speechEnd'] > audio_duration + .1:
        raise ValueError('Transcrição termina além da duração do áudio.')
    return {'coverage': coverage, 'audioDuration': audio_duration,
            'timelineDuration': boundaries[-1] / fps, 'scenes': scenes}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('project', type=Path)
    parser.add_argument('--apply', action='store_true')
    args = parser.parse_args()
    manifest = args.project.resolve()
    original = manifest.read_bytes()
    project = json.loads(original)
    audio = (manifest.parent / project['audio']['narration']['file']).resolve()
    folder = manifest.parent / 'sincronizacao'
    transcript = json.loads((folder / 'transcricao.json').read_text())
    if hashlib.sha256(audio.read_bytes()).hexdigest() != transcript['audioSha256']:
        raise SystemExit('O áudio foi alterado após a transcrição. Transcreva novamente.')
    duration = float(subprocess.check_output(['ffprobe', '-v', 'error', '-show_entries',
                                             'format=duration', '-of', 'csv=p=0', str(audio)], text=True))
    words = [word for segment in transcript['segments'] for word in segment['words']]
    report = align(project['clips'], words, duration)
    report['audioSha256'] = transcript['audioSha256']
    (folder / 'alinhamento.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    rows = ['# Sincronização com a narração', '', f'Correspondência: {report["coverage"]:.1%}.', '',
            '| Cena | Entrada | Duração | Início da fala | Correspondência |', '|---|---|---|---|---|']
    for scene in report['scenes']:
        rows.append(f'| {scene["scene"]:02} | {scene["start"]:.3f}s | {scene["duration"]:.3f}s | '
                    f'{scene["speechStart"]:.3f}s | {scene["coverage"]:.1%} |')
    (folder / 'relatorio.md').write_text('\n'.join(rows) + '\n')
    print(f'Cobertura {report["coverage"]:.1%}; áudio {duration:.3f}s; timeline {report["timelineDuration"]:.3f}s.')
    for scene in report['scenes']:
        print(f'Cena {scene["scene"]:02}: {scene["start"]:.3f}–{scene["end"]:.3f}s; correspondência {scene["coverage"]:.1%}')
    if args.apply:
        for clip, scene in zip(project['clips'], report['scenes']):
            clip['duration'] = scene['duration']
        if manifest.read_bytes() != original:
            raise SystemExit('O projeto mudou durante a sincronização. Reexecute sem sobrescrever alterações.')
        backup = manifest.with_name(manifest.name.replace('.mvideo.json', '') + '.antes-sincronizacao.mvideo.json')
        if not backup.exists():
            shutil.copy2(manifest, backup)
        handle, name = tempfile.mkstemp(prefix='.sync-', suffix='.tmp', dir=manifest.parent)
        import os
        with os.fdopen(handle, 'w') as output:
            output.write(json.dumps(project, ensure_ascii=False, indent=2) + '\n')
        Path(name).chmod(manifest.stat().st_mode & 0o777)
        Path(name).replace(manifest)
        print('Projeto atualizado. Áudio, imagens e roteiro preservados.')


if __name__ == '__main__':
    main()
