"""Check the entire bundled library without downloading anything."""
import json
from pathlib import Path

project = Path(__file__).resolve().parents[1]
assets = project / 'app/src/main/assets/scriptures'
catalog = json.loads((assets / 'catalog.json').read_text(encoding='utf8'))
source = json.loads((project / 'CONTENT-SOURCE.json').read_text(encoding='utf8'))
assert len(catalog) == source['books'] == 87
assert len({b['id'] for b in catalog}) == len(catalog)
assert {b['volume'] for b in catalog} == {
    'Old Testament', 'New Testament', 'Book of Mormon', 'Doctrine and Covenants', 'Pearl of Great Price'}
chapter_count = verse_count = 0
for book in catalog:
    chapters = json.loads((assets / (book['id'] + '.json')).read_text(encoding='utf8'))
    assert len(chapters) == len(book['verseCounts']), book['name']
    for chapter, count in zip(chapters, book['verseCounts']):
        assert len(chapter) == count and count > 0, book['name']
        assert all(isinstance(text, str) and text.strip() for text in chapter), book['name']
        verse_count += count
        chapter_count += 1
assert chapter_count == source['chapters'] == 1582
assert verse_count == source['verses'] == 41995
def verse(book, chapter, number):
    return json.loads((assets / (book + '.json')).read_text(encoding='utf8'))[chapter - 1][number - 1]
assert verse('alma', 32, 21).startswith('And now as I said concerning faith')
assert verse('ps', 46, 10).startswith('Be still, and know that I am God')
assert verse('a-of-f', 1, 13).startswith('We believe in being honest')
assert next(b for b in catalog if b['id'] == 'dc')['verseCounts'].__len__() == 138
print(f'Validated {len(catalog)} books, {chapter_count} chapters, {verse_count} nonempty verses and reference boundaries.')
