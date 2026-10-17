#!/usr/bin/env python3
"""Catch duplicate value definitions within each Android source set/configuration."""

from collections import defaultdict
from pathlib import Path
import sys
import xml.etree.ElementTree as ET


def duplicates(roots):
    problems = []
    for root in roots:
        for folder in sorted(root.glob('values*')):
            if not folder.is_dir():
                continue
            definitions = defaultdict(list)
            for path in sorted(folder.glob('*.xml')):
                for item in ET.parse(path).getroot():
                    name = item.get('name')
                    # Metadata, ID declarations and styleable composition are not unique values.
                    if not name or item.tag in ('public', 'java-symbol', 'overlayable', 'declare-styleable'):
                        continue
                    kind = item.get('type') if item.tag == 'item' else item.tag
                    if kind == 'id':
                        continue
                    if kind in ('string-array', 'integer-array'):
                        kind = 'array'
                    definitions[(kind, name)].append(path)
            for (kind, name), paths in sorted(definitions.items()):
                if len(paths) > 1:
                    problems.append(f'{folder}: duplicate {kind}/{name}: '+', '.join(str(p) for p in paths))
    return problems


if __name__ == '__main__':
    project = Path(__file__).resolve().parent.parent
    roots = [project / value for value in (
        'smarttubetv/src/main/res', 'smarttubetv/src/stmobile/res', 'common/src/main/res')]
    problems = duplicates(roots)
    if problems:
        print('\n'.join(problems), file=sys.stderr)
        sys.exit(1)
    print('Android value resources: no duplicate definitions within source sets/configurations.')
