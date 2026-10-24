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


PAUSE_CUT_THRESHOLD = 1.0
POST_SPEECH_HOLD = .2


def tokens(text):
    plain = ''.join(c for c in unicodedata.normalize('NFKD', text.casefold())
                    if not unicodedata.combining(c))
    return re.findall(r'[a-z0-9]+', plain)


def align(clips, words, audio_duration, fps=30,
          pause_cut_threshold=PAUSE_CUT_THRESHOLD):
    if not math.isfinite(pause_cut_threshold) or not .1 <= pause_cut_threshold <= 30:
        raise ValueError('A pausa mínima deve ficar entre 0,1 e 30 segundos.')
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
        partial = False
        if ratio < .65:
            # A speaker may shorten one scene and continue at the next. Accept only
            # a contiguous spoken prefix with a clear, later anchor in the next scene.
            contiguous_prefix = (len(pairs) >= 4 and pairs[0][0] == start and
                                 all(a[0] + 1 == b[0] and a[1] + 1 == b[1]
                                     for a, b in zip(pairs, pairs[1:])))
            next_start, next_end = ranges[i + 1] if i + 1 < len(ranges) else (0, 0)
            next_pairs = [(j, mapping[j]) for j in range(next_start, next_end) if j in mapping]
            next_anchors = [pair for pair in next_pairs if pair[0] < next_start + 8]
            clear_next_scene = (len(next_anchors) >= min(3, next_end - next_start) and
                                next_anchors[0][1] > pairs[-1][1] and
                                0 < times[next_anchors[0][1]][0] - times[pairs[-1][1]][1] <= 2.5)
            partial = contiguous_prefix and ratio >= .25 and clear_next_scene
        if ((ratio < .65 and not partial) or len(anchors) < min(3, end - start) or
                (anchors[0][0] > start + 1 and (ratio < .85 or anchors[0][0] > start + 4))):
            raise ValueError(f'Cena {i+1}: correspondência insuficiente; revisar antes de salvar.')
        first = anchors[0][1]
        # A short improvised lead-in may replace the first few words of a scene.
        # Backfill at most two heard words only when the rest matches strongly.
        leading = anchors[0][0] - start
        if leading and first > 0 and (leading == 1 or ratio >= .85):
            first -= min(leading, 2)
        scenes.append({'scene': i+1, 'text': clips[i]['text'], 'coverage': ratio, 'partial': partial,
                       'speechStart': times[first][0], 'speechEnd': times[pairs[-1][1]][1]})
    boundaries = [0]
    for previous, following in zip(scenes, scenes[1:]):
        # Word-level estimates can cross a scene edge by a fraction of a second.
        # A bounded overlap is resolved at the midpoint; larger conflicts are unsafe.
        if following['speechStart'] < previous['speechEnd'] - .75:
            raise ValueError('Tempos de fala conflitantes; revisar a transcrição.')
        gap = following['speechStart'] - previous['speechEnd']
        if gap >= pause_cut_threshold:
            # Only a text-anchored scene boundary can consume a long pause.
            # Switch images soon after the previous speech ends, so the next
            # scene is already visible while the speaker prepares its line.
            cut = previous['speechEnd'] + min(POST_SPEECH_HOLD, gap / 3)
            following['cutMode'] = 'after_speech'
        else:
            cut = (previous['speechEnd'] + following['speechStart']) / 2
            following['cutMode'] = 'midpoint'
        following['precedingPause'] = max(0, gap)
        boundaries.append(round(cut * fps))
    boundaries.append(math.ceil(audio_duration * fps))
    for i, scene in enumerate(scenes):
        if boundaries[i+1] - boundaries[i] < 3:
            raise ValueError('Uma cena ficou com menos de 0,1 segundo; revisar.')
        scene.update({'start': boundaries[i] / fps, 'end': boundaries[i+1] / fps,
                      'duration': (boundaries[i+1] - boundaries[i]) / fps})
    if scenes[-1]['speechEnd'] > audio_duration + .1:
        raise ValueError('Transcrição termina além da duração do áudio.')
    return {'coverage': coverage, 'audioDuration': audio_duration,
            'leadingSilence': max(0, scenes[0]['speechStart']),
            'trailingSilence': max(0, audio_duration - scenes[-1]['speechEnd']),
            'timelineDuration': boundaries[-1] / fps, 'scenes': scenes}


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('project', type=Path)
    parser.add_argument('--apply', action='store_true')
    parser.add_argument('--pause-cut-threshold', type=float, default=PAUSE_CUT_THRESHOLD,
                        help='Pausa mínima entre cenas para cortar após a fala anterior (segundos).')
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
    report = align(project['clips'], words, duration, pause_cut_threshold=args.pause_cut_threshold)
    report['pauseCutThreshold'] = args.pause_cut_threshold
    report['audioSha256'] = transcript['audioSha256']
    (folder / 'alinhamento.json').write_text(json.dumps(report, ensure_ascii=False, indent=2) + '\n')
    rows = ['# Sincronização com a narração', '', f'Correspondência: {report["coverage"]:.1%}.',
            f'Pausa mínima para corte após a fala: {args.pause_cut_threshold:.2f}s.', '',
            '| Cena | Entrada | Duração | Início da fala | Pausa anterior | Tipo de corte | Correspondência |',
            '|---|---|---|---|---|---|---|']
    for scene in report['scenes']:
        rows.append(f'| {scene["scene"]:02} | {scene["start"]:.3f}s | {scene["duration"]:.3f}s | '
                    f'{scene["speechStart"]:.3f}s | {scene.get("precedingPause", 0):.3f}s | '
                    f'{"após fala" if scene.get("cutMode") == "after_speech" else "meio da pausa" if scene.get("cutMode") == "midpoint" else "início"} | '
                    f'{scene["coverage"]:.1%} |')
        if scene['partial']:
            rows.append('|  |  |  |  |  | Fala encurtada; transição mantida pela próxima cena. |  |')
    if report['leadingSilence'] >= 1 or report['trailingSilence'] >= 1:
        rows.extend(['', f'Atenção: há {report["leadingSilence"]:.2f}s antes da primeira fala e '
                     f'{report["trailingSilence"]:.2f}s após a última. Esses silêncios continuam '
                     'no áudio e nas cenas inicial/final; corte-os no arquivo de narração e '
                     'sincronize novamente se não fizerem parte do vídeo.'])
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
