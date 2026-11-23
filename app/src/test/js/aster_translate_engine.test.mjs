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


import { test } from "node:test";
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { fileURLToPath } from "node:url";
import vm from "node:vm";

const script_path = fileURLToPath(
  new URL("../../full/assets/bergamot/aster_translate.js", import.meta.url),
);

const source = readFileSync(script_path, "utf8");

function load_engine({ fail = false } = {}) {
  const events = { detect: [], status: [], result: [] };
  const translated_inputs = [];
  const console_calls = [];
  const fake_module = {
    setWorkerUrlResolver: () => {},
    TranslatorBacking: class {
      constructor(options) {
        this.options = options;
      }
    },
    BatchTranslator: class {
      constructor(options, backing) {
        this.backing = backing;
      }
      async translate({ text }) {
        if (fail) throw new Error("model missing for " + text);
        translated_inputs.push(text);
        return { target: { text: "[" + text + "]" } };
      }
    },
  };
  const window = {
    AsterTranslateBridge: {
      on_detect: (json) => events.detect.push(JSON.parse(json)),
      on_status: (json) => events.status.push(JSON.parse(json)),
      on_result: (json) => events.result.push(JSON.parse(json)),
    },
  };
  const spy_console = new Proxy({}, {
    get: () => (...args) => console_calls.push(args),
  });
  const patched = source.replace('await import("./translator.js")', "await __fake_import()");
  assert.notEqual(patched, source);
  vm.runInNewContext(patched, {
    window,
    console: spy_console,
    __fake_import: async () => fake_module,
  });
  return { api: window.__aster_translate, events, translated_inputs, console_calls };
}

const GERMAN = "Guten Tag, ich schreibe Ihnen wegen der Rechnung vom letzten Monat und bitte um eine kurze Antwort.";

test("detection echoes the request id", () => {
  const { api, events } = load_engine();

  api.detect_text(GERMAN, "en", "s1:1");

  assert.equal(events.detect.at(-1).id, "s1:1");
  assert.equal(events.detect.at(-1).language, "de");
});

test("detection without an id still works", () => {
  const { api, events } = load_engine();

  api.detect_text(GERMAN, "en");

  assert.equal(events.detect.at(-1).id, undefined);
  assert.equal(events.detect.at(-1).detected, true);
});

test("translation echoes the request id on every event", async () => {
  const { api, events } = load_engine();

  await api.translate_texts(JSON.stringify(["Hallo Welt.", "Bis bald."]), "de", "en", "s2:7");

  assert.ok(events.status.filter((event) => event.state !== "ready").every((event) => event.id === "s2:7"));
  assert.equal(events.result.length, 1);
  assert.equal(events.result[0].id, "s2:7");
  assert.equal(events.result[0].segments.length, 2);
  assert.equal(events.status.at(-1).state, "translated");
});

test("a long message is translated in chunks with progress", async () => {
  const { api, events, translated_inputs } = load_engine();
  const segments = Array.from({ length: 120 }, (_, index) => "Satz Nummer " + index + ".");

  await api.translate_texts(JSON.stringify(segments), "de", "en", "s3:1");

  const progress = events.status.filter((event) => event.state === "progress");
  assert.ok(progress.length >= 2);
  assert.equal(progress.at(-1).done, progress.at(-1).total);
  assert.equal(events.result[0].segments.length, 120);
  assert.equal(translated_inputs.length >= 120, true);
});

test("an engine failure reports a generic error without the text", async () => {
  const { api, events } = load_engine({ fail: true });

  await api.translate_texts(JSON.stringify(["Vertraulicher Inhalt."]), "de", "en", "s4:1");

  const error = events.status.at(-1);
  assert.equal(error.state, "error");
  assert.equal(error.id, "s4:1");
  assert.equal(JSON.stringify(error).includes("Vertraulicher"), false);
});

test("the script never calls console logging", () => {
  assert.equal(/console\s*\.\s*(log|info|warn|error|debug)/.test(source), false);
});
