"""Import pinned scripture text into small offline book assets (Python stdlib only)."""
import hashlib
import json
from pathlib import Path
import urllib.request

PROJECT = Path(__file__).resolve().parents[1]
WORK = PROJECT / 'work' / 'scripture-source'
ASSETS = PROJECT / 'app/src/main/assets/scriptures'
VOLUMES = [
    ('old-testament', 'Old Testament'),
    ('new-testament', 'New Testament'),
    ('book-of-mormon', 'Book of Mormon'),
    ('doctrine-and-covenants', 'Doctrine and Covenants'),
    ('pearl-of-great-price', 'Pearl of Great Price'),
]

def main():
    WORK.mkdir(parents=True, exist_ok=True)
    ASSETS.mkdir(parents=True, exist_ok=True)
    provenance = PROJECT / 'CONTENT-SOURCE.json'
    if provenance.exists():
        commit = json.loads(provenance.read_text())['commit']
    else:
        request = urllib.request.Request('https://api.github.com/repos/bcbooks/scriptures-json/commits/master',
                                         headers={'User-Agent': 'StillScriptures-content-import'})
        commit = json.load(urllib.request.urlopen(request))['sha']
    catalog, hashes = [], {}
    for slug, volume in VOLUMES:
        filename = slug + '.json'
        source = WORK / (commit + '-' + filename)
        if not source.exists():
            urllib.request.urlretrieve(f'https://raw.githubusercontent.com/bcbooks/scriptures-json/{commit}/{filename}', source)
        hashes[filename] = hashlib.sha256(source.read_bytes()).hexdigest()
        data = json.loads(source.read_text(encoding='utf8'))
        books = data.get('books', [{'book': 'Doctrine and Covenants', 'lds_slug': 'dc', 'chapters': data.get('sections', [])}])
        for book in books:
            book_id = book['lds_slug']
            if '/' in book_id:
                book_id = book_id.rsplit('/', 1)[-1]
            chapters = book['chapters']
            text = []
            for expected, chapter in enumerate(chapters, 1):
                assert chapter.get('chapter', chapter.get('section')) == expected, (book_id, expected)
                verses = chapter['verses']
                assert [v['verse'] for v in verses] == list(range(1, len(verses) + 1)), (book_id, expected)
                assert all(v['text'].strip() for v in verses), (book_id, expected)
                text.append([v['text'] for v in verses])
            catalog.append({'id': book_id, 'name': book['book'], 'volume': volume,
                            'verseCounts': [len(c) for c in text]})
            (ASSETS / (book_id + '.json')).write_text(json.dumps(text, ensure_ascii=False, separators=(',', ':')), encoding='utf8')
    assert len(catalog) == 87, len(catalog)
    assert len({b['id'] for b in catalog}) == len(catalog)
    (ASSETS / 'catalog.json').write_text(json.dumps(catalog, ensure_ascii=False, separators=(',', ':')), encoding='utf8')
    summary = {'repository': 'https://github.com/bcbooks/scriptures-json', 'commit': commit,
               'upstreamLicense': 'Public domain, per upstream README', 'sourceSHA256': hashes,
               'books': len(catalog), 'chapters': sum(len(b['verseCounts']) for b in catalog),
               'verses': sum(sum(b['verseCounts']) for b in catalog),
               'omissions': ['Modern footnotes', 'Modern chapter summaries', 'Official Declarations 1 and 2',
                            'Introductions, facsimiles, title pages and other non-verse front matter', 'JST study aids']}
    provenance.write_text(json.dumps(summary, indent=2) + '\n', encoding='utf8')
    print(json.dumps({k: summary[k] for k in ['commit', 'books', 'chapters', 'verses']}, indent=2))

if __name__ == '__main__':
    main()
