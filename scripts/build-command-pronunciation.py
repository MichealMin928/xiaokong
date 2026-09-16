"""Generate finite command homophone data; development dependency pypinyin==0.55.0.
Install pypinyin==0.55.0 in a development virtual environment. No pypinyin runtime dependency in Android.
Only whole pronunciations from this finite vocabulary are accepted by the app.
"""
from pathlib import Path
from pypinyin import lazy_pinyin, Style
root=Path(__file__).resolve().parents[1]
assets=root/'app/src/main/assets'
phrases=[line.rsplit('@',1)[-1].strip() for line in (assets/'kws/keywords.txt').read_text().splitlines() if '@' in line]
# Numeric selection and its supported prefix/suffix are separate from command mode.
syllables=set(lazy_pinyin(''.join(phrases)+'一二三四五六七八九十第号点击',style=Style.NORMAL))
rows=[]
for code in range(0x3400,0xa000):
 char=chr(code);reading=lazy_pinyin(char,style=Style.NORMAL)[0]
 if reading in syllables:rows.append(f'{char}\t{reading}\n')
(assets/'speech/command_characters.tsv').write_text(''.join(rows))
(assets/'speech/command_phrases.tsv').write_text(''.join(f'{word}\t{"".join(lazy_pinyin(word,style=Style.NORMAL))}\n' for word in phrases))
print(f'{len(phrases)} phrases, {len(rows)} character readings, {len(syllables)} syllables')
