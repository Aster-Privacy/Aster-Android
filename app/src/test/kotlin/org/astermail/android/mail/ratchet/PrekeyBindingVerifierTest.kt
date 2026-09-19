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

package org.astermail.android.mail.ratchet

import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrekeyBindingVerifierTest {

    private val alice_public = """-----BEGIN PGP PUBLIC KEY BLOCK-----

xjMEaq6IWRYJKwYBBAHaRw8BAQdAkJUqjVKsRCZhzRWVfregi55vIutiYNaL
cZvSZ/Yubq3NG2FsaWNlIDxhbGljZUBhc3Rlcm1haWwub3JnPsLAEwQTFgoA
hQWCaq6IWQMLCQcJEPUijgWpr9FDRRQAAAAAABwAIHNhbHRAbm90YXRpb25z
Lm9wZW5wZ3Bqcy5vcmf+95ApBWzP1iUQ5TqZo1JwI+AJrOg6ESl3pnSkyaBW
wQUVCggODAQWAAIBAhkBApsDAh4BFiEE0CJfAxGpe0iCXEW19SKOBamv0UMA
AIBZAQD/sVybEsRkpHtQgTEjWHDxPCKHHNb2rk1sPdn65SmbpQD+MzOjtRtB
ILwcXd3P8GRZu3YHO3QdceIBSMCUL/XRPg3OOARqrohZEgorBgEEAZdVAQUB
AQdADXdgHyDUEM2JL334PR51Nw/3OS/Q5+QKK3ZuSzZkThsDAQgHwr4EGBYK
AHAFgmquiFkJEPUijgWpr9FDRRQAAAAAABwAIHNhbHRAbm90YXRpb25zLm9w
ZW5wZ3Bqcy5vcmcbJiG8dmbqLK+lHLArbscsWGMpzcd7KD5aYkpXdpMTmAKb
DBYhBNAiXwMRqXtIglxFtfUijgWpr9FDAADnQAD/bfqDiGuawxnDcfGMCFMm
WqnPyuE1TzgA17iApPdj1Z4A/1ik7Zm7/slKjUjXVatTA32qlXdNT56ax8p8
rbivoagO
=FCov
-----END PGP PUBLIC KEY BLOCK-----
"""

    private val bob_public = """-----BEGIN PGP PUBLIC KEY BLOCK-----

xjMEaq6IWRYJKwYBBAHaRw8BAQdAE1/X0pSyfr4/HTQcrx1eHqof2Kti8WQ/
2f0oF/Pm3nbNF2JvYiA8Ym9iQGFzdGVybWFpbC5vcmc+wsATBBMWCgCFBYJq
rohZAwsJBwkQubxm5t12kdBFFAAAAAAAHAAgc2FsdEBub3RhdGlvbnMub3Bl
bnBncGpzLm9yZ3gpcvdslI5+x84KB05ckRC7Ba+xPRv7vgwIsDF4bFdpBRUK
CA4MBBYAAgECGQECmwMCHgEWIQSVp+9QFGZgxnfZ2zi5vGbm3XaR0AAAAeYA
+QFy5jwd+v8dF/pioFOnntJgEgA3zpJmJF83fUsAlFUgAQClf82HmHo1S2eE
Nb8uz2cWZewKMcpWhJ5tQSbbOGpEAc44BGquiFkSCisGAQQBl1UBBQEBB0Bm
KupT2OAIUUhsZDmzm0Gku1q/1K9HnTEqhsklOjh2IgMBCAfCvgQYFgoAcAWC
aq6IWQkQubxm5t12kdBFFAAAAAAAHAAgc2FsdEBub3RhdGlvbnMub3BlbnBn
cGpzLm9yZ8UvfWSiw6Qz1V5ORrunEBe+cb8+BansxTvM2sQnLf80ApsMFiEE
lafvUBRmYMZ32ds4ubxm5t12kdAAAHT6AP9jk1n819yj7e13PYLtAeLt1vFd
gRKzSyjwC70dx0oKjgEArFHdfXOpF3N8dKZ+VbVNDbxDd7Mp5meU2ARKw0x1
/ww=
=uIrV
-----END PGP PUBLIC KEY BLOCK-----
"""

    private val ik = "BAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQEBAQ="
    private val spk = "BwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwcHBwc="
    private val pq = "CQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQkJCQk="
    private val v1_signature = "LS0tLS1CRUdJTiBQR1AgU0lHTkVEIE1FU1NBR0UtLS0tLQpIYXNoOiBTSEE1MTIKCmFzdGVyLXJhdGNoZXQtcHJla2V5LXYxOkJBUUVCQVFFQkFRRUJBUUVCQVFFQkFRRUJBUUVCQVFFQkFRRUJBUUVCQVFFQkFRRUJBUUVCQVFFQkFRRUJBUUVCQVFFQkFRRUJBUUVCQVFFQkFRRUJBUT0uQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjPQotLS0tLUJFR0lOIFBHUCBTSUdOQVRVUkUtLS0tLQoKd3JzRUFSWUtBRzBGZ21xdWlGa0pFUFVpamdXcHI5RkRSUlFBQUFBQUFCd0FJSE5oYkhSQWJtOTBZWFJwCmIyNXpMbTl3Wlc1d1ozQnFjeTV2Y21jSTMvZ2JvbmhCaTNMQW5USDJvWnJ0dERtMjRSMmh5UTRrdWVVeQpwL2UwWkJZaEJOQWlYd01ScVh0SWdseEZ0ZlVpamdXcHI5RkRBQUIxYkFFQThQcGpDanlpWHZZMWxGMSsKZzJXd0FadUNQeHZxbCtvTG1valI5V3ExWFFFQkFLdEx4Y3JjZFFsWWZjV0VkYXg1SkMxMHB5TXdkaFZWCmFPMW9vSzJYT29vSQo9d0J5KwotLS0tLUVORCBQR1AgU0lHTkFUVVJFLS0tLS0K"
    private val v2_signature = "LS0tLS1CRUdJTiBQR1AgU0lHTkVEIE1FU1NBR0UtLS0tLQpIYXNoOiBTSEE1MTIKCmFzdGVyLXJhdGNoZXQtcHJla2V5LXYyOkJBUUVCQVFFQkFRRUJBUUVCQVFFQkFRRUJBUUVCQVFFQkFRRUJBUUVCQVFFQkFRRUJBUUVCQVFFQkFRRUJBUUVCQVFFQkFRRUJBUUVCQVFFQkFRRUJBUT0uQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjSEJ3Y0hCd2NIQndjPS5DUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrSkNRa0pDUWtKQ1FrPQotLS0tLUJFR0lOIFBHUCBTSUdOQVRVUkUtLS0tLQoKd3JzRUFSWUtBRzBGZ21xdWlGa0pFUFVpamdXcHI5RkRSUlFBQUFBQUFCd0FJSE5oYkhSQWJtOTBZWFJwCmIyNXpMbTl3Wlc1d1ozQnFjeTV2Y21lbTFMTFJ1d3FPRms1akM0emJKYmFwaStRRUltU3d1Q3kyWTdZZwowQ0lZOFJZaEJOQWlYd01ScVh0SWdseEZ0ZlVpamdXcHI5RkRBQUFuV1FEK1BkcU4ra1UzZUh2UGUrMVIKUDRNaFdIQk1NZ1h5RUNQeHovS0loRTQrbGVFQSt3UUZLV1dnOCtyVHZVT2lvd2FLZnR4anBNVkVvOU1VCkRlS2dudGp5YjVnQwo9eXowdAotLS0tLUVORCBQR1AgU0lHTkFUVVJFLS0tLS0K"
    private val legacy_signature = "AwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwM="

    private fun armored(field: String): String = String(Base64.getDecoder().decode(field), Charsets.UTF_8)

    private fun rewrap(armored_text: String): String =
        Base64.getEncoder().encodeToString(armored_text.toByteArray(Charsets.UTF_8))

    private fun other_key(): String =
        Base64.getEncoder().encodeToString(ByteArray(65) { 5 })

    @Test
    fun web_v1_signature_verifies_against_the_owner_key() {
        assertEquals(
            PrekeyBindingResult.VERIFIED,
            PrekeyBindingVerifier.verify(v1_signature, alice_public, ik, spk),
        )
    }

    @Test
    fun web_v1_signature_verifies_when_the_bundle_also_advertises_a_pq_key() {
        assertEquals(
            PrekeyBindingResult.VERIFIED,
            PrekeyBindingVerifier.verify(v1_signature, alice_public, ik, spk, pq),
        )
    }

    @Test
    fun web_v2_signature_verifies_with_the_pq_identity_key() {
        assertEquals(
            PrekeyBindingResult.VERIFIED,
            PrekeyBindingVerifier.verify(v2_signature, alice_public, ik, spk, pq),
        )
    }

    @Test
    fun base64_wrapped_signatures_are_detected_as_pgp() {
        assertTrue(PrekeyBindingVerifier.is_pgp_signature(v1_signature))
        assertTrue(PrekeyBindingVerifier.is_pgp_signature(v2_signature))
        assertTrue(PrekeyBindingVerifier.is_pgp_signature(armored(v1_signature)))
    }

    @Test
    fun legacy_hash_and_blank_signatures_stay_unsigned_legacy() {
        assertFalse(PrekeyBindingVerifier.is_pgp_signature(legacy_signature))
        assertFalse(PrekeyBindingVerifier.is_pgp_signature(""))
        assertEquals(
            PrekeyBindingResult.UNSIGNED_LEGACY,
            PrekeyBindingVerifier.verify(legacy_signature, alice_public, ik, spk),
        )
        assertEquals(
            PrekeyBindingResult.UNSIGNED_LEGACY,
            PrekeyBindingVerifier.verify("", alice_public, ik, spk),
        )
    }

    @Test
    fun raw_armored_signature_still_verifies() {
        assertEquals(
            PrekeyBindingResult.VERIFIED,
            PrekeyBindingVerifier.verify(armored(v1_signature), alice_public, ik, spk),
        )
    }

    @Test
    fun url_safe_or_unpadded_key_encoding_is_not_treated_as_tampering() {
        val url_safe_ik = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(Base64.getDecoder().decode(ik))
        assertEquals(
            PrekeyBindingResult.VERIFIED,
            PrekeyBindingVerifier.verify(v1_signature, alice_public, url_safe_ik, spk),
        )
    }

    @Test
    fun swapped_signed_prekey_is_invalid() {
        assertEquals(
            PrekeyBindingResult.INVALID,
            PrekeyBindingVerifier.verify(v1_signature, alice_public, ik, other_key()),
        )
    }

    @Test
    fun swapped_identity_key_is_invalid() {
        assertEquals(
            PrekeyBindingResult.INVALID,
            PrekeyBindingVerifier.verify(v2_signature, alice_public, other_key(), spk, pq),
        )
    }

    @Test
    fun swapped_or_stripped_pq_key_under_a_v2_signature_is_invalid() {
        assertEquals(
            PrekeyBindingResult.INVALID,
            PrekeyBindingVerifier.verify(v2_signature, alice_public, ik, spk, other_key()),
        )
        assertEquals(
            PrekeyBindingResult.INVALID,
            PrekeyBindingVerifier.verify(v2_signature, alice_public, ik, spk, null),
        )
    }

    @Test
    fun edited_cleartext_fails_the_signature_check() {
        val text = armored(v1_signature)
        val tampered = text.replace("aster-ratchet-prekey-v1:" + ik, "aster-ratchet-prekey-v1:" + other_key())
        assertTrue(tampered != text)
        assertEquals(
            PrekeyBindingResult.INVALID,
            PrekeyBindingVerifier.verify(rewrap(tampered), alice_public, other_key(), spk),
        )
    }

    @Test
    fun signature_from_a_key_the_recipient_does_not_publish_is_unverifiable_not_invalid() {
        assertEquals(
            PrekeyBindingResult.UNVERIFIABLE,
            PrekeyBindingVerifier.verify(v1_signature, bob_public, ik, spk),
        )
    }

    @Test
    fun missing_or_garbage_public_key_is_unverifiable_not_invalid() {
        assertEquals(
            PrekeyBindingResult.UNVERIFIABLE,
            PrekeyBindingVerifier.verify(v1_signature, null, ik, spk),
        )
        assertEquals(
            PrekeyBindingResult.UNVERIFIABLE,
            PrekeyBindingVerifier.verify(v1_signature, "not a key", ik, spk),
        )
    }

    @Test
    fun truncated_signature_block_is_unverifiable_not_invalid() {
        val text = armored(v1_signature)
        val truncated = text.substring(0, text.indexOf("-----BEGIN PGP SIGNATURE-----") + 40)
        assertEquals(
            PrekeyBindingResult.UNVERIFIABLE,
            PrekeyBindingVerifier.verify(rewrap(truncated), alice_public, ik, spk),
        )
    }
}
