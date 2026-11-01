"""Texts for the generated cases (tests/sync/generate.py, tests/anki/generate.py): words and phrases by script."""

# (text, kind, {answer language code: the line shown}); a header line has " — " before the meaning.
TEXTS = {
    "de": [
        ("Haus", "word", {"ru": "das Haus (Hauses, pl. Häuser) — дом", "en": "das Haus (Hauses, pl. Häuser) — house, home"}),
        ("Häuser", "word", {"ru": "дома", "en": "houses"}),
        ("Straße", "word", {"ru": "die Straße (pl. Straßen) — улица", "en": "street"}),
        ("STRASSE", "word", {"ru": "улица", "en": "street"}),
        ("GROẞ", "word", {"ru": "большой", "en": "big"}),
        ("Käse", "word", {"ru": "der Käse — сыр", "en": "cheese"}),
        ("Mädchen", "word", {"ru": "девочка", "en": "girl"}),
        ("Übung", "word", {"ru": "упражнение", "en": "exercise"}),
        ("größer", "word", {"ru": "больше", "en": "bigger"}),
        ("Fuß", "word", {"ru": "нога, ступня", "en": "foot"}),
        ("Öl", "word", {"ru": "масло", "en": "oil"}),
        ("aufmachen", "word", {"ru": "открывать", "en": "to open"}),
        ("Ich mache die Tür zu!", "phrase", {"ru": "Я закрываю дверь!", "en": "I close the door!"}),
        ("Wie geht's?", "phrase", {"ru": "Как дела?", "en": "How are you?"}),
        ("Grüß Gott", "phrase", {"ru": "Здравствуйте", "en": "Hello"}),
    ],
    "ru": [
        ("дом", "word", {"en": "house", "de": "Haus"}),
        ("ёжик", "word", {"en": "hedgehog", "de": "Igel"}),
        ("съесть", "word", {"en": "to eat up", "de": "aufessen"}),
        ("Ещё раз", "phrase", {"en": "Once more", "de": "Noch einmal"}),
        ("Добрый вечер!", "phrase", {"en": "Good evening!", "de": "Guten Abend!"}),
        ("їжак", "word", {"en": "hedgehog", "ru": "ёж"}),
        ("ґанок", "word", {"en": "porch", "ru": "крыльцо"}),
        ("Добрий вечір", "phrase", {"en": "Good evening", "ru": "Добрый вечер"}),
    ],
    "fr": [
        ("café", "word", {"ru": "кофе", "en": "coffee"}),
        ("élève", "word", {"ru": "ученик", "en": "pupil"}),
        ("cœur", "word", {"ru": "сердце", "en": "heart"}),
        ("garçon", "word", {"ru": "мальчик", "en": "boy"}),
        ("l’été", "word", {"ru": "лето", "en": "the summer"}),
        ("aujourd'hui", "word", {"ru": "сегодня", "en": "today"}),
        ("« Ça va ? »", "phrase", {"ru": "«Как дела?»", "en": "“How are you?”"}),
    ],
    "es": [
        ("niño", "word", {"ru": "ребёнок", "en": "child"}),
        ("corazón", "word", {"ru": "сердце", "en": "heart"}),
        ("pingüino", "word", {"ru": "пингвин", "en": "penguin"}),
        ("¿Qué tal?", "phrase", {"ru": "Как дела?", "en": "How are you?"}),
        ("¡Hola, amigo!", "phrase", {"ru": "Привет, друг!", "en": "Hi, friend!"}),
    ],
    "pl": [
        ("źdźbło", "word", {"ru": "стебель", "en": "blade of grass"}),
        ("łódź", "word", {"ru": "лодка", "en": "boat"}),
        ("Gdańsk", "word", {"ru": "Гданьск", "en": "Gdansk"}),
        ("Zażółć gęślą jaźń", "phrase", {"ru": "панграмма", "en": "a pangram"}),
    ],
    "pt": [
        ("coração", "word", {"ru": "сердце", "en": "heart"}),
        ("não", "word", {"ru": "нет", "en": "no"}),
        ("maçã", "word", {"ru": "яблоко", "en": "apple"}),
        ("Obrigado!", "phrase", {"ru": "Спасибо!", "en": "Thank you!"}),
    ],
    "it": [
        ("perché", "word", {"ru": "почему", "en": "why"}),
        ("città", "word", {"ru": "город", "en": "city"}),
        ("più", "word", {"ru": "больше", "en": "more"}),
        ("Com'è bello!", "phrase", {"ru": "Как красиво!", "en": "How beautiful!"}),
    ],
    "cjk": [
        ("日本語", "word", {"ru": "японский язык", "en": "Japanese"}),
        ("漢字", "word", {"ru": "иероглифы", "en": "kanji"}),
        ("東京", "word", {"ru": "Токио", "en": "Tokyo"}),
        ("ひらがな", "word", {"ru": "хирагана", "en": "hiragana"}),
        ("カタカナ", "word", {"ru": "катакана", "en": "katakana"}),
        ("ｶﾀｶﾅ", "word", {"ru": "катакана (полуширинная)", "en": "half-width katakana"}),
        ("你好", "word", {"ru": "привет", "en": "hello"}),
        ("我爱你。", "phrase", {"ru": "Я тебя люблю.", "en": "I love you."}),
        ("한국어", "word", {"ru": "корейский язык", "en": "Korean"}),
        ("사랑해요", "phrase", {"ru": "Я люблю тебя", "en": "I love you"}),
        ("𠮷野家", "word", {"ru": "Ёсиноя", "en": "Yoshinoya"}),
    ],
    "rtl": [
        ("مرحبا", "word", {"ru": "привет", "en": "hello"}),
        ("كتاب", "word", {"ru": "книга", "en": "book"}),
        ("السلام عليكم", "phrase", {"ru": "Мир вам", "en": "Peace be upon you"}),
        ("שלום", "word", {"ru": "мир, привет", "en": "peace, hello"}),
        ("שָׁלוֹם", "word", {"ru": "мир (с огласовками)", "en": "peace (pointed)"}),
        ("תודה רבה", "phrase", {"ru": "Большое спасибо", "en": "Thank you very much"}),
    ],
    "emoji": [
        ("😀", "word", {"ru": "улыбка", "en": "grin"}),
        ("👍🏽", "word", {"ru": "класс (тон кожи)", "en": "thumbs up (skin tone)"}),
        ("👨‍👩‍👧", "word", {"ru": "семья", "en": "family"}),
        ("🇩🇪", "word", {"ru": "флаг Германии", "en": "flag of Germany"}),
        ("Haus 🏠", "phrase", {"ru": "дом 🏠", "en": "house 🏠"}),
    ],
    "other": [
        ("ΟΔΟΣ", "word", {"ru": "улица", "en": "street"}),
        ("οδός", "word", {"ru": "улица", "en": "street"}),
        ("İstanbul", "word", {"ru": "Стамбул", "en": "Istanbul"}),
        ("ılık", "word", {"ru": "тёплый", "en": "lukewarm"}),
        ("Ǆemal", "word", {"ru": "Джемаль", "en": "Djemal"}),
    ],
}

_LONG = "Es war einmal ein kleines Haus am Rande eines großen, dunklen Waldes, und niemand wusste, wer darin wohnte. "

# Whitespace at the edges, soft hyphens, NFD, escapes, control characters, very long texts: each its own text.
EDGE = [
    (" Haus", "word", {"ru": "дом (пробел слева)"}),
    ("Haus ", "word", {"ru": "дом (пробел справа)"}),
    ("\tHaus\n", "word", {"ru": "дом (табуляция, перевод строки)"}),
    ("Haus\u00a0", "word", {"ru": "дом (неразрывный пробел)"}),
    ("\u3000日本\u3000", "word", {"ru": "Япония (идеографический пробел)"}),
    ("Ha\u00adus", "word", {"ru": "дом (мягкий перенос)"}),
    ("Ka\u0308se", "word", {"ru": "сыр (NFD)"}),
    ("Ich mache die Tu\u0308r zu!", "phrase", {"ru": "Я закрываю дверь! (NFD)"}),
    ('Er sagte: "Komm!"', "phrase", {"ru": 'Он сказал: "Иди!"'}),
    ("C:\\Pfad\\Datei", "phrase", {"ru": "путь \\ с обратной косой"}),
    ("Zeile eins\nZeile zwei", "phrase", {"ru": "строка один\nстрока два"}),
    ("Tab\there", "phrase", {"ru": "табуляция\tвнутри"}),
    ("\u0001steuer\u001f", "word", {"ru": "управляющие символы"}),
    ("nul\u0000inside", "word", {"ru": "NUL внутри"}),
    ("del\u007fchar", "word", {"ru": "DEL внутри"}),
    ("Zeile\u2028Trenner", "phrase", {"ru": "разделитель строк U+2028"}),
    ("a/b </script> & <b>", "phrase", {"ru": "<разметка> & «кавычки»"}),
    ("\u200bHaus", "word", {"ru": "дом (пробел нулевой ширины)"}),
    ("Hau\u00df", "word", {"ru": "ß"}),
    (_LONG * 10, "phrase", {"ru": "очень длинный отрывок"}),
    ("x" * 1000, "word", {"ru": "длинное слово"}),
]

# A form → its base form (history.seen's base).
BASES = {"Häuser": "Haus", "größer": "groß", "aufmachen": "machen", "élève": "élever", "съесть": "есть"}

# The answer language codes and their English names (ai.put keeps the name: docs/formats.md).
NAMES = {"ru": "Russian", "en": "English", "de": "German", "uk": "Ukrainian", "fr": "French", "es": "Spanish",
         "pl": "Polish", "pt": "Portuguese", "it": "Italian", "ja": "Japanese", "zh": "Chinese", "ko": "Korean",
         "ar": "Arabic", "he": "Hebrew", "el": "Greek", "tr": "Turkish"}


def pool(groups):
    """(text, kind, meanings) of these groups ("edge" too), each text once."""
    out, seen = [], set()
    for g in groups:
        for t in EDGE if g == "edge" else TEXTS[g]:
            if t[0] not in seen:
                seen.add(t[0])
                out.append(t)
    return out
