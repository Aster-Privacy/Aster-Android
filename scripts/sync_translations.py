#!/usr/bin/env python3
import argparse
import re
import subprocess
import sys
import xml.etree.ElementTree as ET
from collections import Counter, defaultdict
from pathlib import Path

repo_root = Path(__file__).resolve().parent.parent
res_root = repo_root / "app" / "src" / "main" / "res"

locale_dirs = {
    "es": "values-es",
    "fr": "values-fr",
    "de": "values-de",
    "it": "values-it",
    "pt": "values-pt",
    "zh-CN": "values-zh-rCN",
    "ja": "values-ja",
    "ko": "values-ko",
    "ar": "values-ar",
    "ru": "values-ru",
    "nl": "values-nl",
    "pl": "values-pl",
    "tr": "values-tr",
    "hi": "values-hi",
}

short_word_limit = 2
plural_suffixes = ("zero", "one", "two", "few", "many", "other")
quantity_order = {q: i for i, q in enumerate(plural_suffixes)}

web_placeholder = re.compile(r"\{\{\s*([A-Za-z0-9_]+)\s*\}\}|\{([A-Za-z0-9_]+)\}")
android_placeholder = re.compile(r"%(?:(\d+)\$)?([-#+0,(]*\d*(?:\.\d+)?)([sdfxXc])")

entry_pattern = re.compile(
    r"<(string|plurals)\s+name=\"([^\"]+)\"([^>]*?)(?:/>|>(.*?)</\1>)",
    re.S,
)
item_pattern = re.compile(r"<item\s+quantity=\"([a-z]+)\"\s*>(.*?)</item>", re.S)


class sync_error(Exception):
    pass


def js_tokens(source):
    i = 0
    n = len(source)
    while i < n:
        c = source[i]
        if c.isspace():
            i += 1
        elif source.startswith("//", i):
            j = source.find("\n", i)
            i = n if j < 0 else j + 1
        elif source.startswith("/*", i):
            j = source.find("*/", i + 2)
            if j < 0:
                raise sync_error("unterminated comment in web translations")
            i = j + 2
        elif c in "{}:,+;":
            yield (c, c)
            i += 1
        elif c in "\"'`":
            quote = c
            i += 1
            out = []
            while True:
                if i >= n:
                    raise sync_error("unterminated string in web translations")
                ch = source[i]
                if ch == "\\":
                    nxt = source[i + 1]
                    i += 2
                    if nxt == "n":
                        out.append("\n")
                    elif nxt == "t":
                        out.append("\t")
                    elif nxt == "r":
                        out.append("\r")
                    elif nxt == "u":
                        if source[i] == "{":
                            j = source.index("}", i)
                            out.append(chr(int(source[i + 1:j], 16)))
                            i = j + 1
                        else:
                            out.append(chr(int(source[i:i + 4], 16)))
                            i += 4
                    elif nxt != "\n":
                        out.append(nxt)
                elif ch == quote:
                    i += 1
                    break
                elif quote == "`" and source.startswith("${", i):
                    raise sync_error("template interpolation in web translations is not supported")
                else:
                    out.append(ch)
                    i += 1
            yield ("str", "".join(out))
        elif c.isalnum() or c in "_$":
            j = i
            while j < n and (source[j].isalnum() or source[j] in "_$"):
                j += 1
            yield ("id", source[i:j])
            i = j
        else:
            raise sync_error(f"unexpected character {c!r} in web translations")


def parse_web_module(source):
    start = source.find("export const")
    brace = source.find("= {", start)
    if start < 0 or brace < 0:
        raise sync_error("web translation module has no exported object")
    tokens = list(js_tokens(source[brace + 2:]))
    pos = 0

    def peek():
        return tokens[pos] if pos < len(tokens) else (None, None)

    def take(kind=None):
        nonlocal pos
        tok = tokens[pos]
        if kind and tok[0] != kind:
            raise sync_error(f"expected {kind} in web translations, found {tok[1]!r}")
        pos += 1
        return tok

    def value():
        kind = peek()[0]
        if kind == "{":
            return obj()
        if kind == "str":
            parts = [take("str")[1]]
            while peek()[0] == "+":
                take("+")
                parts.append(take("str")[1])
            return "".join(parts)
        raise sync_error(f"unsupported value in web translations: {peek()[1]!r}")

    def obj():
        take("{")
        out = {}
        while peek()[0] != "}":
            kind, key = take()
            if kind not in ("id", "str"):
                raise sync_error(f"bad key in web translations: {key!r}")
            take(":")
            out[key] = value()
            if peek()[0] == ",":
                take(",")
        take("}")
        return out

    return obj()


def flatten(tree, prefix=""):
    out = {}
    for key, val in tree.items():
        full = f"{prefix}{key}"
        if isinstance(val, dict):
            out.update(flatten(val, full + "."))
        else:
            out[full] = val
    return out


def load_web(web_root, ref, code):
    rel = f"src/lib/i18n/translations/{code}.ts"
    if ref:
        result = subprocess.run(
            ["git", "-C", str(web_root), "show", f"{ref}:{rel}"],
            capture_output=True,
            encoding="utf-8",
        )
        if result.returncode != 0:
            raise sync_error(result.stderr.strip() or f"cannot read {rel} at {ref}")
        source = result.stdout
    else:
        path = Path(web_root) / rel
        if not path.is_file():
            raise sync_error(f"missing web translation file {path}")
        source = path.read_text(encoding="utf-8")
    return flatten(parse_web_module(source))


def unescape_android(raw, strip=True):
    try:
        text = "".join(ET.fromstring(f"<r>{raw}</r>").itertext())
    except ET.ParseError:
        text = raw
    out = []
    i = 0
    quoted = False
    while i < len(text):
        c = text[i]
        if c == "\\" and i + 1 < len(text):
            nxt = text[i + 1]
            if nxt == "n":
                out.append("\n")
            elif nxt == "t":
                out.append("\t")
            elif nxt == "u":
                out.append(chr(int(text[i + 2:i + 6], 16)))
                i += 4
            else:
                out.append(nxt)
            i += 2
            continue
        if c == "\"":
            quoted = not quoted
            i += 1
            continue
        if c.isspace() and not quoted:
            if out and out[-1] == " ":
                i += 1
                continue
            out.append(" ")
            i += 1
            continue
        out.append(c)
        i += 1
    joined = "".join(out)
    return joined.strip() if strip else joined


def escape_android(text):
    out = []
    for i, c in enumerate(text):
        if c == "\\":
            out.append("\\\\")
        elif c == "'":
            out.append("\\'")
        elif c == "\"":
            out.append("\\\"")
        elif c == "\n":
            out.append("\\n")
        elif c == "\t":
            out.append("\\t")
        elif c == "&":
            out.append("&amp;")
        elif c == "<":
            out.append("&lt;")
        elif c == ">":
            out.append("&gt;")
        elif c in "@?" and i == 0:
            out.append("\\" + c)
        else:
            out.append(c)
    result = "".join(out)
    if result != result.strip() or "  " in result:
        result = f"\"{result}\""
    return result


class resource:
    def __init__(self, kind, name, attrs, raw, items, path=None, span=None):
        self.path = path
        self.span = span
        self.kind = kind
        self.name = name
        self.attrs = attrs
        self.raw = raw
        self.items = items

    @property
    def translatable(self):
        return "translatable=\"false\"" not in self.attrs

    def text(self):
        return unescape_android(self.raw)

    def item_text(self, quantity):
        return unescape_android(self.items[quantity])


def parse_resource_file(path):
    with open(path, encoding="utf-8", newline="") as handle:
        source = handle.read()
    out = []
    for m in entry_pattern.finditer(source):
        kind, name, attrs, body = m.groups()
        body = (body or "").strip()
        items = {}
        if kind == "plurals":
            items = {q: v.strip() for q, v in item_pattern.findall(body)}
        out.append(resource(kind, name, attrs, body, items, Path(path), m.span()))
    return out


def parse_resource_dir(directory):
    files = {}
    for path in sorted(Path(directory).glob("*.xml")):
        entries = parse_resource_file(path)
        if entries:
            files[path.name] = entries
    return files


def normalize(text, pattern):
    text = pattern.sub("\u0000", text)
    text = text.replace("’", "'").replace("…", "...")
    return re.sub(r"\s+", " ", text).strip()


def normalize_android(text):
    return normalize(text.replace("%%", "%"), android_placeholder)


def normalize_web(text):
    return normalize(text, web_placeholder)


def android_args(text):
    args = []
    sequential = 0
    for m in android_placeholder.finditer(text.replace("%%", "")):
        index, flags, conversion = m.groups()
        if index:
            position = int(index)
        else:
            sequential += 1
            position = sequential
        args.append((position, flags, conversion, index is not None))
    return args


def web_names(text):
    return [a or b for a, b in web_placeholder.findall(text)]


def is_formatted(english_raw_text):
    return "%" in english_raw_text


def build_arg_map(english_android, english_web):
    args = android_args(english_android)
    names = web_names(english_web)
    if len(args) != len(names):
        return None
    positional = any(a[3] for a in args) or len({a[0] for a in args}) > 1
    mapping = {}
    for (position, flags, conversion, _), name in zip(args, names):
        spec = f"%{position}${flags}{conversion}" if positional else f"%{flags}{conversion}"
        if mapping.get(name, spec) != spec:
            return None
        mapping[name] = spec
    return mapping


def convert_web_value(value, arg_map, formatted):
    if not arg_map and web_placeholder.search(value):
        return None
    text = value.replace("%", "%%") if formatted else value
    failed = False

    def swap(m):
        nonlocal failed
        name = m.group(1) or m.group(2)
        if name not in arg_map:
            failed = True
            return m.group(0)
        return arg_map[name]

    text = web_placeholder.sub(swap, text)
    if failed:
        return None
    return text


def web_plural_groups(flat):
    groups = defaultdict(dict)
    for key, val in flat.items():
        base, _, suffix = key.rpartition("_")
        if suffix in plural_suffixes and base:
            groups[base][suffix] = val
    return groups


def is_plural_key(key):
    return key.rpartition("_")[2] in plural_suffixes + ("plural",)


def pick(candidates, existing_norm):
    if not candidates:
        return None
    norms = [normalize_android(c) for c in candidates]
    if existing_norm is not None and existing_norm in norms:
        return None
    counts = Counter(norms)
    best = max(counts.values())
    for c, n in zip(candidates, norms):
        if counts[n] == best:
            return c
    return None


class sync_result:
    def __init__(self):
        self.synced = Counter()
        self.kept = Counter()
        self.missing = defaultdict(list)
        self.matched_strings = 0
        self.matched_plurals = 0


def is_short(english):
    return len(android_placeholder.sub("", english).split()) <= short_word_limit


def with_edges(english_raw, text):
    full = unescape_android(english_raw, strip=False)
    lead = full[:len(full) - len(full.lstrip())]
    trail = full[len(full.rstrip()):]
    return f"{lead}{text}{trail}"


def render_string(name, value):
    return f"<string name=\"{name}\">{value}</string>"


def render_plural(name, items):
    body = "".join(
        f"\n        <item quantity=\"{q}\">{items[q]}</item>"
        for q in sorted(items, key=lambda q: quantity_order.get(q, 99))
    )
    return f"<plurals name=\"{name}\">{body}\n    </plurals>"


def sync(web_root, ref, write):
    base_files = parse_resource_dir(res_root / "values")
    web_en = load_web(web_root, ref, "en")
    string_index = defaultdict(list)
    for key, val in web_en.items():
        if not is_plural_key(key):
            string_index[normalize_web(val)].append(key)
    en_groups = web_plural_groups(web_en)
    plural_index = defaultdict(list)
    for base_key, forms in en_groups.items():
        if "other" in forms:
            plural_index[normalize_web(forms["other"])].append(base_key)

    result = sync_result()
    string_matches = {}
    plural_matches = {}
    for entries in base_files.values():
        for e in entries:
            if not e.translatable:
                continue
            if e.kind == "string":
                english = e.text()
                keys = string_index.get(normalize_android(english), [])
                maps = []
                for key in keys:
                    arg_map = build_arg_map(english, web_en[key])
                    if arg_map is not None:
                        maps.append((key, arg_map))
                if maps:
                    string_matches[e.name] = (maps, is_formatted(english))
                    result.matched_strings += 1
            elif "other" in e.items:
                other = e.item_text("other")
                keys = plural_index.get(normalize_android(other), [])
                maps = []
                for key in keys:
                    forms = en_groups[key]
                    if "one" in e.items and "one" in forms:
                        if normalize_android(e.item_text("one")) != normalize_web(forms["one"]):
                            continue
                    arg_map = build_arg_map(other, forms["other"])
                    if arg_map is not None:
                        maps.append((key, arg_map))
                if maps:
                    plural_matches[e.name] = (maps, is_formatted(other))
                    result.matched_plurals += 1

    edits = defaultdict(list)
    appends = defaultdict(list)
    for code, directory in locale_dirs.items():
        web_locale = load_web(web_root, ref, code)
        locale_groups = web_plural_groups(web_locale)
        locale_path = res_root / directory
        existing = {}
        if locale_path.is_dir():
            for entries in parse_resource_dir(locale_path).values():
                for e in entries:
                    existing[(e.kind, e.name)] = e
        for file_name, entries in base_files.items():
            for e in entries:
                if not e.translatable:
                    continue
                current = existing.get((e.kind, e.name))
                rendered = None
                if e.kind == "string":
                    value = None
                    if e.name in string_matches:
                        maps, formatted = string_matches[e.name]
                        candidates = []
                        for key, arg_map in maps:
                            translated = web_locale.get(key)
                            if translated is None or translated == web_en[key]:
                                continue
                            converted = convert_web_value(translated, arg_map, formatted)
                            if converted is not None:
                                candidates.append(converted)
                        chosen = None
                        if current is None or not is_short(e.text()) or normalize_android(current.text()) == normalize_android(e.text()):
                            chosen = pick(candidates, normalize_android(current.text()) if current else None)
                        if chosen is not None:
                            value = escape_android(with_edges(e.raw, chosen.strip()))
                            result.synced[code] += 1
                            rendered = render_string(e.name, value)
                    if value is None and current is not None:
                        result.kept[code] += 1
                    elif value is None:
                        result.missing[code].append(f"{e.kind} {e.name}")
                        continue
                else:
                    items = None
                    if e.name in plural_matches:
                        maps, formatted = plural_matches[e.name]
                        needed = set(current.items) if current else {"other"}
                        for key, arg_map in maps:
                            forms = locale_groups.get(key, {})
                            if "other" not in forms or not needed.issubset(forms):
                                continue
                            if forms == en_groups[key]:
                                continue
                            converted = {}
                            for quantity, text in forms.items():
                                c = convert_web_value(text, arg_map, formatted)
                                if c is None:
                                    converted = None
                                    break
                                converted[quantity] = escape_android(c)
                            if converted:
                                same = current is not None and len(converted) == len(current.items) and all(
                                    q in current.items
                                    and normalize_android(unescape_android(v)) == normalize_android(current.item_text(q))
                                    for q, v in converted.items()
                                )
                                if not same:
                                    items = converted
                                    result.synced[code] += 1
                                    rendered = render_plural(e.name, items)
                                break
                    if items is None and current is not None:
                        result.kept[code] += 1
                    elif items is None:
                        result.missing[code].append(f"{e.kind} {e.name}")
                        continue
                if rendered is None:
                    continue
                if current is not None:
                    edits[current.path].append((current.span, rendered))
                else:
                    appends[locale_path / file_name].append(rendered)

    changed = []
    for path in sorted(set(edits) | set(appends)):
        old = None
        if path.is_file():
            with open(path, encoding="utf-8", newline="") as handle:
                old = handle.read()
        eol = "\r\n" if old and "\r\n" in old else "\n"
        content = old if old is not None else "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<resources>\n</resources>\n"
        for (a, b), rendered in sorted(edits.get(path, []), reverse=True):
            content = content[:a] + rendered.replace("\n", eol) + content[b:]
        if path in appends:
            close = content.rindex("</resources>")
            block = "".join(f"    {r}\n" for r in appends[path]).replace("\n", eol)
            content = content[:close] + block + content[close:]
        if content != old:
            changed.append(path)
            if write:
                path.parent.mkdir(parents=True, exist_ok=True)
                with open(path, "w", encoding="utf-8", newline="") as handle:
                    handle.write(content)
    return result, changed


def main():
    parser = argparse.ArgumentParser(
        description=(
            "Sync Android string resources with the Aster Mail web translations. "
            "Android strings whose English text matches a web string take that "
            "string's translation in every locale, with {{name}} placeholders "
            "converted to the Android format arguments. Strings with no web "
            "counterpart keep their existing Android translation. Changed "
            "entries are replaced in place and missing ones are appended."
        ),
    )
    parser.add_argument(
        "--web",
        default=str(repo_root.parent / "Aster-Mail"),
        help="path to the Aster-Mail checkout (default: ../Aster-Mail)",
    )
    parser.add_argument(
        "--ref",
        default="origin/main",
        help="git ref to read web translations from; pass an empty string to read the working tree (default: origin/main)",
    )
    parser.add_argument(
        "--check",
        action="store_true",
        help="report changes without writing, and exit 1 if any locale file is out of date",
    )
    args = parser.parse_args()
    try:
        result, changed = sync(args.web, args.ref or None, write=not args.check)
    except sync_error as error:
        print(f"error: {error}", file=sys.stderr)
        return 2
    print(f"web matches: {result.matched_strings} strings, {result.matched_plurals} plurals")
    for code in locale_dirs:
        print(
            f"{code:6} synced from web {result.synced[code]:5}  "
            f"kept Android {result.kept[code]:5}  missing {len(result.missing[code])}"
        )
    for code, names in result.missing.items():
        for name in names:
            print(f"missing {code}: {name}", file=sys.stderr)
    verb = "out of date" if args.check else "updated"
    print(f"{len(changed)} locale files {verb}")
    if any(result.missing.values()):
        return 1
    if args.check and changed:
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
