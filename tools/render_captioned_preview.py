"""Rebuild documentary captions using the current scene times, preserving encoded audio."""
import argparse
import json
from pathlib import Path
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('project', type=Path)
    parser.add_argument('input_video', type=Path)
    parser.add_argument('output_video', type=Path)
    parser.add_argument('--font', type=Path, default=Path('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf'))
    parser.add_argument('--ffmpeg', default='ffmpeg', help='Executável FFmpeg; padrão: buscar no PATH.')
    args = parser.parse_args()
    project = json.loads(args.project.read_text())
    captions = args.project.parent / 'textos-visuais'
    if not args.font.is_file():
        raise SystemExit('Informe uma fonte TTF existente com --font.')
    with tempfile.TemporaryDirectory(prefix='megalodonte-captions-') as temporary:
        work = Path(temporary)
        shutil.copy2(args.font, work / 'font.ttf')
        font = work / 'font.ttf'
        filters = []
        cursor = 0
        for i, clip in enumerate(project['clips']):
            source = captions / f'titulo-{i+1:02}.txt'
            if not source.is_file():
                raise SystemExit(f'Cartela ausente: cena {i+1}.')
            target = work / f'title-{i+1:02}.txt'
            shutil.copy2(source, target)
            duration = clip['duration']
            start = cursor + min(.4, duration/4)
            end = min(cursor+duration-.1, start+4.6)
            filters.append(f"drawtext=fontfile={font}:textfile={target}:fontcolor=white:fontsize=38:x=72:y=72:"
                           f"box=1:boxcolor=0x101827@0.72:boxborderw=18:enable='between(t,{start:.3f},{end:.3f})'")
            cursor += duration
        label = work / 'label.txt'
        label.write_text('ILUSTRAÇÕES GERADAS POR IA')
        filters.append(f'drawtext=fontfile={font}:textfile={label}:fontcolor=white@0.8:fontsize=20:'
                       'x=72:y=h-60:box=1:boxcolor=black@0.35:boxborderw=8')
        subprocess.run([args.ffmpeg, '-hide_banner', '-loglevel', 'error', '-nostdin', '-n', '-filter_threads', '1',
                        '-i', str(args.input_video), '-vf', ','.join(filters), '-map', '0:v:0', '-map', '0:a?',
                        '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '20', '-threads', '2',
                        '-pix_fmt', 'yuv420p', '-c:a', 'copy', '-movflags', '+faststart', str(args.output_video)], check=True)
    print('Vídeo com cartelas sincronizadas e áudio preservado: ' + str(args.output_video))


if __name__ == '__main__':
    main()
