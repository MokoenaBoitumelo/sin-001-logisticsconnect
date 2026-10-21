import os

ignore_dirs = {'.git', 'node_modules', 'target', 'bin', 'build', '.idea', '__pycache__', '.vscode'}
ignore_exts = {'.png', '.jpg', '.jpeg', '.pdf', '.jar', '.class', '.zip', '.tar', '.gz'}

with open('codebase.md', 'w', encoding='utf-8') as out:
    out.write('# Project Codebase Export\n\n')
    for root, dirs, files in os.walk('.'):
        dirs[:] = [d for d in dirs if d not in ignore_dirs]
        for file in files:
            if file in ['codebase.md', 'make_md.py'] or any(file.endswith(ext) for ext in ignore_exts):
                continue
            filepath = os.path.join(root, file)
            out.write(f'## File: `{filepath}`\n\n```text\n')
            try:
                with open(filepath, 'r', encoding='utf-8', errors='ignore') as f:
                    out.write(f.read())
            except Exception as e:
                out.write(f'// Error reading file: {e}')
            out.write('\n```\n\n')
print('Done! Created codebase.md')
