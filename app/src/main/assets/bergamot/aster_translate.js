//
// Aster Communications Inc.
//
// Copyright (c) 2026 Aster Communications Inc.
//
// This file is part of this project.
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program. If not, see <https://www.gnu.org/licenses/>.
//

const MODEL_BASE = "/models/bergamot/v1";
const WORKER_URL = "/bergamot/translator-worker.js";
const PIVOT_LANGUAGE = "en";
const MODEL_VERSION = "v1";
const ROOT_ID = "m";
const TRANSLATED_MARKER_ATTR = "data-aster-translated";
const MIN_DETECTION_LENGTH = 40;
const MIN_DETECTION_CONFIDENCE = 0.85;
const MIN_STOPWORD_HIT_RATE = 0.04;
const SCRIPT_CONFIDENCE = 0.95;
const SCRIPT_SHARE_THRESHOLD = 0.1;
const MIN_TRANSLATABLE_LENGTH = 2;

const SUPPORTED_LANGUAGES = [
  "ar", "de", "en", "es", "fr", "it", "ja", "ko", "nl", "pl", "pt", "ru", "tr", "zh",
];
const RTL_LANGUAGES = ["ar"];

function bridge() {
  return typeof window !== "undefined" ? window.AsterTranslateBridge : undefined;
}

function post_status(payload) {
  const b = bridge();
  if (b && typeof b.on_status === "function") {
    try { b.on_status(JSON.stringify(payload)); return; } catch (e) { /* fall through */ }
  }
  try { console.log("ASTER_TR_STATUS:" + JSON.stringify(payload)); } catch (e) { /* ignore */ }
}

function post_detect(payload) {
  const b = bridge();
  if (b && typeof b.on_detect === "function") {
    try { b.on_detect(JSON.stringify(payload)); return; } catch (e) { /* fall through */ }
  }
  try { console.log("ASTER_TR_DETECT:" + JSON.stringify(payload)); } catch (e) { /* ignore */ }
}

function normalize_language(value) {
  if (!value) return null;
  const base = String(value).trim().toLowerCase().split(/[-_]/)[0];
  if (!base) return null;
  return SUPPORTED_LANGUAGES.indexOf(base) >= 0 ? base : null;
}

function direction_for(language) {
  return RTL_LANGUAGES.indexOf(language) >= 0 ? "rtl" : "ltr";
}

const QUOTE_LINE = /^\s*(?:>|\|)+.*$/gm;
const SIGNATURE_BLOCK = /\n--\s*\n[\s\S]*$/;
const URL_OR_EMAIL = /(?:https?:\/\/|www\.)\S+|[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}/g;

function strip_for_detection(text) {
  return text
    .replace(QUOTE_LINE, " ")
    .replace(SIGNATURE_BLOCK, " ")
    .replace(URL_OR_EMAIL, " ")
    .replace(/\s+/g, " ")
    .trim();
}

const SCRIPT_TESTS = [
  { language: "ko", pattern: /[가-힯ᄀ-ᇿ㄰-㆏]/g },
  { language: "ja", pattern: /[぀-ゟ゠-ヿ]/g },
  { language: "ar", pattern: /[؀-ۿݐ-ݿﭐ-﷿]/g },
  { language: "ru", pattern: /[Ѐ-ӿ]/g },
  { language: "zh", pattern: /[一-鿿㐀-䶿]/g },
];

function detect_by_script(text) {
  const total = text.replace(/\s/g, "").length;
  if (total === 0) return null;
  for (const test of SCRIPT_TESTS) {
    test.pattern.lastIndex = 0;
    const matches = text.match(test.pattern);
    if (matches && matches.length / total >= SCRIPT_SHARE_THRESHOLD) {
      return { language: test.language, confidence: SCRIPT_CONFIDENCE };
    }
  }
  return null;
}

const STOPWORDS = {
  en: ["the", "and", "you", "your", "for", "with", "this", "that", "have", "from", "please", "will", "not", "are", "was", "been", "our", "we", "can", "if"],
  de: ["und", "der", "die", "das", "sie", "ist", "nicht", "mit", "für", "ein", "eine", "auf", "wir", "ihre", "haben", "bitte", "wird", "auch", "dass", "von"],
  es: ["que", "los", "las", "una", "por", "para", "con", "del", "está", "como", "más", "pero", "este", "esta", "sus", "puede", "hemos", "gracias", "ser", "todo", "hola", "puedes", "tus", "tenemos", "nunca", "además", "muy", "también", "desde", "hasta", "cuando", "donde", "porque", "estamos", "somos", "tienen", "saludo", "usted"],
  fr: ["les", "des", "une", "vous", "pour", "avec", "dans", "sur", "est", "pas", "votre", "nous", "que", "qui", "plus", "être", "cette", "sont", "merci", "par"],
  it: ["che", "non", "per", "con", "una", "sono", "questo", "come", "della", "dei", "alla", "più", "tuo", "vostro", "grazie", "essere", "anche", "nella", "delle", "gli"],
  nl: ["een", "het", "van", "niet", "voor", "met", "aan", "zijn", "wij", "uw", "dat", "dit", "maar", "ook", "worden", "heeft", "kunt", "bedankt", "onze", "naar"],
  pl: ["nie", "jest", "się", "oraz", "dla", "przez", "jako", "tego", "które", "wszystkie", "twoje", "proszę", "można", "będzie", "jeśli", "aby", "przy", "tym", "już"],
  pt: ["que", "não", "para", "com", "uma", "por", "seu", "sua", "está", "como", "mais", "você", "isso", "pelo", "obrigado", "ser", "nós", "são", "das", "dos", "esta", "este", "todo", "pode"],
  tr: ["için", "bir", "ile", "olan", "daha", "veya", "bu", "ve", "size", "olarak", "sonra", "kadar", "üzere", "değil", "gibi", "lütfen", "sizin", "her", "çok", "var"],
};

const DISCRIMINATIVE_STOPWORDS = (function () {
  const owners = new Map();
  for (const code of Object.keys(STOPWORDS)) {
    const language = normalize_language(code);
    if (!language) continue;
    for (const word of STOPWORDS[code]) {
      owners.set(word, owners.has(word) ? null : language);
    }
  }
  const discriminative = new Map();
  owners.forEach((language, word) => {
    if (language) discriminative.set(word, language);
  });
  return discriminative;
})();

const TOKEN_SPLIT = /[^\p{L}]+/u;

function detect_by_stopwords(text) {
  const tokens = text.toLowerCase().split(TOKEN_SPLIT).filter(Boolean);
  if (tokens.length < 8) return null;
  const scores = new Map();
  for (const token of tokens) {
    const language = DISCRIMINATIVE_STOPWORDS.get(token);
    if (language) scores.set(language, (scores.get(language) || 0) + 1);
  }
  let top_language = null;
  let top_hits = 0;
  let second_hits = 0;
  scores.forEach((hits, language) => {
    if (hits > top_hits) {
      second_hits = top_hits;
      top_hits = hits;
      top_language = language;
    } else if (hits > second_hits) {
      second_hits = hits;
    }
  });
  if (!top_language || top_hits / tokens.length < MIN_STOPWORD_HIT_RATE) return null;
  return { language: top_language, confidence: top_hits / (top_hits + second_hits) };
}

function detect_language(text) {
  const stripped = strip_for_detection(text);
  if (stripped.length < MIN_DETECTION_LENGTH) return null;
  return detect_by_script(stripped) || detect_by_stopwords(stripped);
}

const URL_PATTERN = "(?:https?:\\/\\/|www\\.)[^\\s<>()\\[\\]]+";
const EMAIL_PATTERN = "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}";
const BASE64_PATTERN = "[A-Za-z0-9+/]{24,}={0,2}";
const CURRENCY_PATTERN = "(?:[$\\u20ac\\u00a3\\u00a5\\u20bd\\u20a9\\u20b9]\\s?\\d[\\d.,]*|\\d[\\d.,]*\\s?(?:USD|EUR|GBP|JPY|CHF|CAD|AUD|SEK|NOK|DKK|PLN|RUB|TRY|CNY|KRW))";
const ISO_DATE_PATTERN = "\\d{4}-\\d{2}-\\d{2}(?:[T ]\\d{2}:\\d{2}(?::\\d{2})?)?";
const NUMERIC_DATE_PATTERN = "\\d{1,2}[\\/.\\-]\\d{1,2}[\\/.\\-]\\d{2,4}";
const TIME_PATTERN = "\\d{1,2}:\\d{2}(?::\\d{2})?\\s?(?:[AaPp]\\.?[Mm]\\.?)?";
const PHONE_PATTERN = "\\+?\\d[\\d\\s().-]{7,}\\d";
const ORDER_ID_PATTERN = "#?\\b[A-Z0-9]{2,}(?:-[A-Z0-9]{2,}){1,}\\b";
const TRACKING_PATTERN = "\\b(?=[A-Z0-9]*\\d)[A-Z0-9]{8,}\\b";
const CODE_PATTERN = "\\b\\d{4,10}\\b";

const PROTECTED_PATTERN = new RegExp([
  URL_PATTERN, EMAIL_PATTERN, ISO_DATE_PATTERN, NUMERIC_DATE_PATTERN, CURRENCY_PATTERN,
  TIME_PATTERN, PHONE_PATTERN, BASE64_PATTERN, ORDER_ID_PATTERN, TRACKING_PATTERN, CODE_PATTERN,
].join("|"), "g");

const TOKEN_PREFIX = "ZQX";
const TOKEN_SUFFIX = "QZX";
const TOKEN_PATTERN = /ZQX\s*([A-Za-z]{1,4})\s*QZX/gi;

function index_to_letters(index) {
  let value = index + 1;
  let out = "";
  while (value > 0) {
    const remainder = (value - 1) % 26;
    out = String.fromCharCode(65 + remainder) + out;
    value = Math.floor((value - 1) / 26);
  }
  return out;
}

function letters_to_index(letters) {
  let value = 0;
  for (const character of letters.toUpperCase()) {
    value = value * 26 + (character.charCodeAt(0) - 64);
  }
  return value - 1;
}

function token_for(index) {
  return TOKEN_PREFIX + index_to_letters(index) + TOKEN_SUFFIX;
}

function protect_entities(text) {
  const entities = [];
  const masked = text.replace(PROTECTED_PATTERN, (match) => {
    const index = entities.length;
    entities.push(match);
    return token_for(index);
  });
  return { masked, entities };
}

function restore_entities(masked, entities) {
  const seen = new Set();
  const text = masked.replace(TOKEN_PATTERN, (match, letters) => {
    const index = letters_to_index(letters);
    if (!Number.isInteger(index) || index < 0 || index >= entities.length) return match;
    seen.add(index);
    return entities[index];
  });
  return { text, missing: entities.length - seen.size };
}

function get_sentence_segmenter(locale) {
  if (typeof Intl === "undefined" || typeof Intl.Segmenter !== "function") return null;
  try {
    return new Intl.Segmenter(locale, { granularity: "sentence" });
  } catch (e) {
    try {
      return new Intl.Segmenter(undefined, { granularity: "sentence" });
    } catch (e2) {
      return null;
    }
  }
}

function segment_sentences(text, locale) {
  if (!text.trim()) return [];
  const segmenter = get_sentence_segmenter(locale);
  if (!segmenter) return [text];
  const parts = [];
  for (const entry of segmenter.segment(text)) {
    if (entry.segment.trim()) parts.push(entry.segment);
  }
  return parts.length > 0 ? parts : [text];
}

const EXCLUDED_TAGS = new Set(["SCRIPT", "STYLE", "NOSCRIPT", "TEXTAREA", "PRE", "CODE", "KBD", "SAMP", "VAR", "TT"]);
const EXCLUDED_SELECTOR = [".aster-quoted-content", ".aster-quote-toggle", ".aster-forwarded-collapse", ".remote-content-banner", "[translate='no']", ".notranslate"].join(",");
const NON_LINGUISTIC = /^[\s\d\p{P}\p{S}]*$/u;
const node_originals = new WeakMap();

function is_excluded(node) {
  let element = node.parentElement;
  while (element) {
    if (EXCLUDED_TAGS.has(element.tagName)) return true;
    if (element.getAttribute("translate") === "no") return true;
    element = element.parentElement;
  }
  return false;
}

function is_translatable_text(value) {
  const trimmed = value.trim();
  if (trimmed.length < MIN_TRANSLATABLE_LENGTH) return false;
  return !NON_LINGUISTIC.test(trimmed);
}

function collect_translatable_nodes(root) {
  const doc = root.ownerDocument;
  if (!doc) return [];
  const excluded_roots = new Set(Array.prototype.slice.call(root.querySelectorAll(EXCLUDED_SELECTOR)));
  const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const nodes = [];
  for (let current = walker.nextNode(); current !== null; current = walker.nextNode()) {
    const node = current;
    if (!is_translatable_text(node.data)) continue;
    if (is_excluded(node)) continue;
    let inside_excluded = false;
    for (let element = node.parentElement; element && element !== root; element = element.parentElement) {
      if (excluded_roots.has(element)) { inside_excluded = true; break; }
    }
    if (inside_excluded) continue;
    nodes.push(node);
  }
  return nodes;
}

function apply_translations(nodes, translations) {
  if (nodes.length !== translations.length) return 0;
  let swapped = 0;
  for (let index = 0; index < nodes.length; index += 1) {
    const node = nodes[index];
    const translated = translations[index];
    if (!translated || !translated.trim()) continue;
    const original = node_originals.get(node) || node.data;
    if (translated === original) continue;
    if (!node_originals.has(node)) node_originals.set(node, node.data);
    node.textContent = translated;
    swapped += 1;
  }
  return swapped;
}

function restore_originals(root) {
  const doc = root.ownerDocument;
  if (!doc) return 0;
  const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  let restored = 0;
  for (let current = walker.nextNode(); current !== null; current = walker.nextNode()) {
    const node = current;
    const original = node_originals.get(node);
    if (original === undefined) continue;
    if (node.data !== original) { node.textContent = original; restored += 1; }
    node_originals.delete(node);
  }
  root.removeAttribute(TRANSLATED_MARKER_ATTR);
  return restored;
}

function mark_translated(root, language) {
  root.setAttribute(TRANSLATED_MARKER_ATTR, language);
  root.setAttribute("dir", direction_for(language));
  root.setAttribute("lang", language);
}

let engine_promise = null;

async function get_translator() {
  if (!engine_promise) {
    engine_promise = (async () => {
      const mod = await import("./translator.js");
      mod.setWorkerUrlResolver(() => WORKER_URL);

      class SelfHostedBacking extends mod.TranslatorBacking {
        constructor(base) {
          super({ registryUrl: base + "/registry.json", pivotLanguage: PIVOT_LANGUAGE, cacheSize: 0 });
          this.__base = base;
        }
        async loadModelRegistery() {
          const entries = await super.loadModelRegistery();
          for (const entry of entries) {
            const files = entry.files;
            for (const part of Object.keys(files)) {
              const file = files[part];
              if (file && typeof file.name === "string") {
                const bare = file.name
                  .replace(/^https?:\/\/[^/]+/i, "")
                  .replace(/^\/+/, "");
                file.name = this.__base + "/" + bare;
              }
            }
          }
          return entries;
        }
      }

      const backing = new SelfHostedBacking(MODEL_BASE);
      const translator = new mod.BatchTranslator({ workers: 1, batchSize: 8, downloadTimeout: 0 }, backing);
      return translator;
    })();
  }
  return engine_promise;
}

async function translate_segments(translator, segments, from, to) {
  const results = [];
  for (const text of segments) {
    if (!text.trim()) { results.push(text); continue; }
    const response = await translator.translate({ from, to, text, html: false });
    results.push(response.target.text);
  }
  return results;
}

function root_element() {
  return typeof document !== "undefined" ? document.getElementById(ROOT_ID) : null;
}

async function run(from, to) {
  const normalized_from = normalize_language(from);
  const normalized_to = normalize_language(to);
  if (!normalized_from || !normalized_to || normalized_from === normalized_to) {
    post_status({ state: "error", reason: "unsupported_pair" });
    return;
  }
  const root = root_element();
  if (!root) { post_status({ state: "error", reason: "no_root" }); return; }
  post_status({ state: "translating", from: normalized_from, to: normalized_to });
  try {
    const nodes = collect_translatable_nodes(root);
    if (nodes.length === 0) { post_status({ state: "empty", from: normalized_from, to: normalized_to }); return; }

    const originals = nodes.map((node) => node_originals.get(node) || node.data);
    const protections = originals.map((text) => protect_entities(text));
    const segmented = protections.map((entry) => segment_sentences(entry.masked, normalized_from));

    const flat = [];
    const owners = [];
    segmented.forEach((segments, node_index) => {
      segments.forEach((segment) => { flat.push(segment); owners.push(node_index); });
    });

    const translator = await get_translator();
    const translated_flat = await translate_segments(translator, flat, normalized_from, normalized_to);
    if (translated_flat.length !== flat.length) { post_status({ state: "error", reason: "count_mismatch" }); return; }

    const per_node_masked = segmented.map(() => "");
    let cursor = 0;
    segmented.forEach((segments, node_index) => {
      const count = segments.length;
      per_node_masked[node_index] = translated_flat.slice(cursor, cursor + count).join("");
      cursor += count;
    });

    const per_node = per_node_masked.map((masked, index) => {
      const restored = restore_entities(masked, protections[index].entities);
      return restored.missing > 0 ? originals[index] : restored.text;
    });

    const swapped = apply_translations(nodes, per_node);
    if (swapped > 0) mark_translated(root, normalized_to);
    if (typeof window.__aster_fit === "function") { try { window.__aster_fit(); } catch (e) { /* ignore */ } }
    post_status({ state: "translated", from: normalized_from, to: normalized_to, swapped });
  } catch (error) {
    post_status({ state: "error", reason: String(error && error.message ? error.message : error) });
  }
}

function show_original() {
  const root = root_element();
  if (!root) return;
  const restored = restore_originals(root);
  if (typeof window.__aster_fit === "function") { try { window.__aster_fit(); } catch (e) { /* ignore */ } }
  post_status({ state: "original", restored });
}

function detect(accepted_csv) {
  const root = root_element();
  if (!root) { post_detect({ detected: false, reason: "no_root" }); return; }
  const accepted = String(accepted_csv || "")
    .split(",")
    .map((code) => normalize_language(code))
    .filter(Boolean);
  const text = root.textContent || "";
  const result = detect_language(text);
  if (!result || result.confidence < MIN_DETECTION_CONFIDENCE || accepted.indexOf(result.language) >= 0) {
    post_detect({ detected: false });
    return;
  }
  post_detect({ detected: true, language: result.language, confidence: result.confidence });
}

window.__aster_translate = {
  run: run,
  show_original: show_original,
  detect: detect,
  model_version: MODEL_VERSION,
  supported: SUPPORTED_LANGUAGES,
};

post_status({ state: "ready" });
