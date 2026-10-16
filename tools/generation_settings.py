"""Show only the base prompt and sound library; API credentials are never printed."""
import argparse
import json
import os
from pathlib import Path


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--settings', type=Path)
    parser.add_argument('--project', type=Path, help='Project directory or .mvideo.json file')
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
    project_directory = (args.project if args.project.is_dir() else args.project.parent) if args.project else None
    project_prompt = project_directory / 'prompt-base.md' if project_directory else None
    result = {'soundsDirectory': settings.get('soundsDirectory', '/home/eliezer/Desktop/backup/sounds'),
              'basePrompt': project_prompt.read_text() if project_prompt and project_prompt.is_file()
              else settings.get('basePrompt', (root / 'docs/prompts/configurar-projeto-video.md').read_text())}
    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == '__main__':
    main()
