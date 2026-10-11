"""Import calendar facts and app-selected daily references; no lesson prose is bundled."""
import datetime as dt
import hashlib
import json
import re
from pathlib import Path
import urllib.request
from bs4 import BeautifulSoup

PROJECT = Path(__file__).resolve().parents[1]
WORK = PROJECT / 'work'
ASSETS = PROJECT / 'app/src/main/assets'
catalog = json.loads((ASSETS / 'scriptures/catalog.json').read_text(encoding='utf8'))
by_id = {b['id']: b for b in catalog}
def normal(s):
    return re.sub(r'\s+', ' ', s.replace('\u00a0', ' ').replace('\ufffd', '-').replace('â€“', '-').replace('â€”', '-')).strip()
by_name = {normal(b['name']): b for b in catalog}
def assignments(title):
    groups, current = [], None
    for segment in normal(title).split(';'):
        segment = segment.strip()
        shared = re.fullmatch(r'(\d)\s*(?:and|-)\s*(\d)\s+(John|Peter|Timothy|Thessalonians)', segment)
        if shared:
            for n in range(int(shared[1]), int(shared[2]) + 1):
                b = by_name[f'{n} {shared[3]}']
                groups.append({'bookId': b['id'], 'chapters': list(range(1, len(b['verseCounts']) + 1))})
            current = None
            continue
        found = next((name for name in sorted(by_name, key=len, reverse=True)
                      if segment == name or segment.startswith(name + ' ')), None)
        if found:
            current = by_name[found]
            segment = segment[len(found):].strip()
        if current is None:
            return []
        if not segment:
            numbers = list(range(1, len(current['verseCounts']) + 1))
        else:
            numbers = []
            for part in segment.split(','):
                match = re.fullmatch(r'(\d+)(?:-(\d+))?', part.strip())
                if not match:
                    raise ValueError((title, segment))
                start = int(match[1])
                end_text = match[2]
                end = int(end_text) if end_text else start
                # The publisher abbreviates ranges such as 102-3 and 116-19.
                if end < start and end_text:
                    end = int(str(start)[:-len(end_text)] + end_text)
                numbers += list(range(start, end + 1))
        existing = next((g for g in groups if g['bookId'] == current['id']), None)
        if existing:
            existing['chapters'] += numbers
        else:
            groups.append({'bookId': current['id'], 'chapters': numbers})
    return groups

def main():
    WORK.mkdir(parents=True, exist_ok=True)
    selections = json.loads((PROJECT / 'tools/daily_selections.json').read_text())
    lessons, anchors, sources = [], [], []
    for year, testament in [(2026, 'old'), (2027, 'new')]:
        url = f'https://www.churchofjesuschrist.org/study/manual/come-follow-me-for-home-and-church-{testament}-testament-{year}?lang=eng'
        cached = WORK / f'cfm-{year}.html'
        if not cached.exists():
            request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0'})
            cached.write_bytes(urllib.request.urlopen(request, timeout=20).read())
        rows = BeautifulSoup(cached.read_bytes(), 'html.parser').select('li[data-date-start]')
        assert len(rows) == len(selections[str(year)]) == 52
        sources.append({'year': year, 'url': url, 'sha256': hashlib.sha256(cached.read_bytes()).hexdigest()})
        for row, selected in zip(rows, selections[str(year)]):
            title = normal(row.select_one('.title').get_text(' ', strip=True)).replace('-', 'â€“')
            groups = assignments(title)
            highlights = []
            for encoded in selected.split(';'):
                book, chapter, verse = encoded.split(':')
                reference = {'bookId': book, 'chapter': int(chapter), 'verse': int(verse)}
                b = by_id[book]
                assert 1 <= reference['chapter'] <= len(b['verseCounts']), encoded
                assert 1 <= reference['verse'] <= b['verseCounts'][reference['chapter'] - 1], encoded
                if groups:
                    assert any(g['bookId'] == book and reference['chapter'] in g['chapters'] for g in groups), (title, encoded)
                highlights.append(reference)
                if reference not in anchors:
                    anchors.append(reference)
            supporting = not groups
            if supporting:
                for ref in highlights:
                    group = next((g for g in groups if g['bookId'] == ref['bookId']), None)
                    if not group:
                        group = {'bookId': ref['bookId'], 'chapters': []}
                        groups.append(group)
                    if ref['chapter'] not in group['chapters']:
                        group['chapters'].append(ref['chapter'])
            start, end = row['data-date-start'], row['data-date-end']
            assert (dt.date.fromisoformat(end) - dt.date.fromisoformat(start)).days == 6
            lessons.append({'id': f'{year}-{len([l for l in lessons if l["year"] == year]) + 1:02}',
                            'year': year, 'start': start, 'end': end, 'title': title,
                            'url': 'https://www.churchofjesuschrist.org' + row.select_one('a')['href'],
                            'supporting': supporting, 'groups': groups, 'highlights': highlights})
    for previous, current in zip(lessons, lessons[1:]):
        assert dt.date.fromisoformat(previous['end']) + dt.timedelta(days=1) == dt.date.fromisoformat(current['start'])
    target = ASSETS / 'lessons'
    target.mkdir(exist_ok=True)
    (target / 'calendar.json').write_text(json.dumps(lessons, ensure_ascii=False, separators=(',', ':')), encoding='utf8')
    (target / 'highlights.json').write_text(json.dumps(anchors, separators=(',', ':')), encoding='utf8')
    (PROJECT / 'LESSON-SOURCE.json').write_text(json.dumps({'sources': sources,
        'description': 'Published calendar dates and scripture assignments only. No official lesson prose bundled.',
        'dailySelection': 'App-curated references; weekday modulo curated pool size; no official daily-verse claim.',
        'lessons': len(lessons), 'uniqueCuratedReferences': len(anchors)}, indent=2) + '\n', encoding='utf8')
    print(f'Imported {len(lessons)} weekly schedules and {len(anchors)} validated curated references. No lesson prose bundled.')

if __name__ == '__main__':
    main()
