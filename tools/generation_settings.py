"""Show only the base prompt and sound library; API credentials are never printed."""
import argparse
import json
import os
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--settings', type=Path)
    args = parser.parse_args()
    if args.settings:
        path = args.settings
    elif os.name == 'nt' and os.getenv('APPDATA'):
        path = Path(os.environ['APPDATA']) / 'megalodonte-video-editor/editor-settings.json'
    else:
        configured = os.getenv('XDG_CONFIG_HOME', '')
        root = Path(configured) if configured and Path(configured).is_absolute() else Path.home() / '.config'
        path = root / 'megalodonte-video-editor/editor-settings.json'
    root = Path(__file__).resolve().parents[1]
    settings = json.loads(path.read_text()) if path.is_file() else {}
    result = {'soundsDirectory': settings.get('soundsDirectory', '/home/eliezer/Desktop/backup/sounds'),
              'basePrompt': settings.get('basePrompt', (root / 'docs/prompts/gerar-video.md').read_text())}
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
