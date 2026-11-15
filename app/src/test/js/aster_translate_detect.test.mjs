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

function load_detector() {
  const detections = [];
  const window = {
    AsterTranslateBridge: {
      on_detect: (json) => detections.push(JSON.parse(json)),
      on_status: () => {},
      on_result: () => {},
    },
  };
  vm.runInNewContext(readFileSync(script_path, "utf8"), { window, console });

  return (text, accepted = "en") => {
    window.__aster_translate.detect_text(text, accepted);

    return detections.pop();
  };
}

const detect = load_detector();

const PORTUGUESE_FIRST_MESSAGE =
  "Olá, bom dia. Não consigo aceder à minha conta desde ontem e gostaria de saber o que posso fazer para resolver este problema. Obrigada pela vossa ajuda.";

const PORTUGUESE_SECOND_MESSAGE =
  "Boa tarde. O único email onde me perseguem, neste momento é o institucional, ... Agradeço desde já o vosso esforço. Teresa Silva Teresa";

test("detects both portuguese messages of one thread", () => {
  assert.equal(detect(PORTUGUESE_FIRST_MESSAGE).language, "pt");
  assert.equal(detect(PORTUGUESE_SECOND_MESSAGE).language, "pt");
});

test("detects a short courtesy reply", () => {
  assert.equal(
    detect("Boa tarde. Agradeço desde já o vosso esforço. Teresa Silva").language,
    "pt",
  );
});

test("does not treat words shared by spanish and portuguese as spanish", () => {
  assert.equal(
    detect(
      "Boa tarde, escrevo desde casa porque o sistema não abre neste momento e já tentei várias vezes.",
    ).language,
    "pt",
  );
});

test("detects portuguese written mostly with nasal vowel endings", () => {
  assert.equal(
    detect(
      "As condições da promoção e a duração da campanha mudaram, e as instruções novas seguem em anexo para revisão.",
    ).language,
    "pt",
  );
});

test("detects spanish written mostly with words that use the letter ñ", () => {
  assert.equal(
    detect(
      "Mañana el señor Muñoz enviará el año fiscal al equipo de diseño, sin ningún cambio importante.",
    ).language,
    "es",
  );
});

test("still detects other languages", () => {
  assert.equal(
    detect(
      "Hola, hemos recibido su pedido y le agradecemos su confianza. Puede consultar la factura adjunta para mas detalles.",
    ).language,
    "es",
  );
  assert.equal(
    detect(
      "Guten Tag, wir haben Ihre Bestellung erhalten und werden sie in den nächsten Tagen versenden. Bitte beachten Sie die beigefügte Rechnung.",
    ).language,
    "de",
  );
});

test("leaves an english message signed with a portuguese name alone", () => {
  assert.equal(
    detect(
      "Hi there, thanks for the quick reply. The invoice is attached and I will call you tomorrow.\n\nBest regards,\nJoão Simões",
    ).detected,
    false,
  );
});
