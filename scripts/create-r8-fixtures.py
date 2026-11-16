#!/usr/bin/env python3
"""Create deterministic, entirely synthetic CBZs for an isolated Leaf benchmark install."""
import argparse
from pathlib import Path
import struct
import zipfile
import zlib


def page(index):
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data))
    width, height = 640, 960
    color = bytes((40 + index * 15, 80 + index * 10, 120 + index * 8))
    rows = b''.join(b'\0' + (b'\xff\xff\xff' if y % 240 < 8 else color) * width for y in range(height))
    return (b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', width, height, 8, 2, 0, 0, 0))
            + chunk(b'IDAT', zlib.compress(rows)) + chunk(b'IEND', b''))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('output', type=Path)
    parser.add_argument('--count', type=int, default=24, choices=range(1, 101))
    args = parser.parse_args()
    args.output.mkdir(parents=True, exist_ok=True)
    pages = [page(i) for i in range(8)]
    for number in range(1, args.count + 1):
        title = f'Leaf R8 Fixture {number:02}'
        path = args.output / (title + '.cbz')
        with path.open('xb') as target, zipfile.ZipFile(target, 'w', zipfile.ZIP_DEFLATED) as archive:
            for i, image in enumerate(pages):
                archive.writestr(zipfile.ZipInfo(f'page{i + 1}.png', (2026, 1, 1, 0, 0, 0)), image)
            archive.writestr(zipfile.ZipInfo('ComicInfo.xml', (2026, 1, 1, 0, 0, 0)),
                f'<ComicInfo><Title>{title}</Title><Series>{title}</Series><Number>1</Number></ComicInfo>')
    print(f'Created {args.count} synthetic CBZs in {args.output}')
