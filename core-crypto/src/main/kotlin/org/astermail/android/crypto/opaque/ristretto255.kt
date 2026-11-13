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
package org.astermail.android.crypto.opaque

import java.math.BigInteger

internal const val RISTRETTO_ELEMENT_LEN = 32
internal const val RISTRETTO_SCALAR_LEN = 32
internal const val RISTRETTO_UNIFORM_LEN = 64

internal class RistrettoElement internal constructor(
    internal val x: BigInteger,
    internal val y: BigInteger,
    internal val z: BigInteger,
    internal val t: BigInteger,
)

internal object Ristretto255 {
    private val two: BigInteger = BigInteger.valueOf(2)
    val field_prime: BigInteger = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.valueOf(19))
    val group_order: BigInteger =
        BigInteger.ONE.shiftLeft(252).add(BigInteger("27742317777372353535851937790883648493"))

    private val curve_d: BigInteger = field_prime.subtract(BigInteger.valueOf(121665))
        .multiply(BigInteger.valueOf(121666).modInverse(field_prime)).mod(field_prime)
    private val curve_two_d: BigInteger = curve_d.shiftLeft(1).mod(field_prime)
    private val sqrt_m1: BigInteger =
        two.modPow(field_prime.subtract(BigInteger.ONE).shiftRight(2), field_prime)
    private val sqrt_exponent: BigInteger = field_prime.subtract(BigInteger.valueOf(5)).shiftRight(3)
    private val one_minus_d_sq: BigInteger =
        BigInteger.ONE.subtract(curve_d.multiply(curve_d)).mod(field_prime)
    private val d_minus_one_sq: BigInteger =
        curve_d.subtract(BigInteger.ONE).modPow(two, field_prime)
    private val sqrt_ad_minus_one: BigInteger =
        BigInteger("25063068953384623474111414158702152701244531502492656460079210482610430750235")
    private val invsqrt_a_minus_d: BigInteger =
        BigInteger("54469307008909316920995813868745141605393597292927456921205312896311721017578")

    val base_point: RistrettoElement = run {
        val x = BigInteger("15112221349535400772501151409588531511454012693041857206046113283949847762202")
        val y = BigInteger("46316835694926478169428394003475163141307993866256225615783033603165251855960")
        RistrettoElement(x, y, BigInteger.ONE, mul(x, y))
    }

    private val identity: RistrettoElement =
        RistrettoElement(BigInteger.ZERO, BigInteger.ONE, BigInteger.ONE, BigInteger.ZERO)

    init {
        val minus_one = field_prime.subtract(BigInteger.ONE)
        check(mul(sqrt_m1, sqrt_m1) == minus_one)
        check(mul(sqrt_ad_minus_one, sqrt_ad_minus_one) == sub(neg(curve_d), BigInteger.ONE))
        check(
            mul(mul(invsqrt_a_minus_d, invsqrt_a_minus_d), sub(minus_one, curve_d)) == BigInteger.ONE,
        )
    }

    private fun add(a: BigInteger, b: BigInteger): BigInteger = a.add(b).mod(field_prime)

    private fun sub(a: BigInteger, b: BigInteger): BigInteger = a.subtract(b).mod(field_prime)

    private fun mul(a: BigInteger, b: BigInteger): BigInteger = a.multiply(b).mod(field_prime)

    private fun neg(a: BigInteger): BigInteger = a.negate().mod(field_prime)

    private fun is_negative(a: BigInteger): Boolean = a.testBit(0)

    private fun abs(a: BigInteger): BigInteger = if (is_negative(a)) neg(a) else a

    private class SqrtRatio(val was_square: Boolean, val root: BigInteger)

    private fun sqrt_ratio_m1(u: BigInteger, v: BigInteger): SqrtRatio {
        val v3 = mul(mul(v, v), v)
        val v7 = mul(mul(v3, v3), v)
        var r = mul(mul(u, v3), mul(u, v7).modPow(sqrt_exponent, field_prime))
        val check = mul(v, mul(r, r))
        val minus_u = neg(u)
        val correct_sign = check == u
        val flipped_sign = check == minus_u
        val flipped_sign_i = check == mul(minus_u, sqrt_m1)
        if (flipped_sign || flipped_sign_i) r = mul(r, sqrt_m1)
        return SqrtRatio(correct_sign || flipped_sign, abs(r))
    }

    fun le_to_integer(bytes: ByteArray): BigInteger = BigInteger(1, bytes.reversedArray())

    fun integer_to_le(value: BigInteger, length: Int): ByteArray {
        val be = value.toByteArray()
        val out = ByteArray(length)
        var index = 0
        while (index < length && index < be.size) {
            out[index] = be[be.size - 1 - index]
            index++
        }
        return out
    }

    fun decode(bytes: ByteArray): RistrettoElement? {
        if (bytes.size != RISTRETTO_ELEMENT_LEN) return null
        val s = le_to_integer(bytes)
        if (s >= field_prime || is_negative(s)) return null
        val ss = mul(s, s)
        val u1 = sub(BigInteger.ONE, ss)
        val u2 = add(BigInteger.ONE, ss)
        val u2_sqr = mul(u2, u2)
        val v = sub(neg(mul(curve_d, mul(u1, u1))), u2_sqr)
        val ratio = sqrt_ratio_m1(BigInteger.ONE, mul(v, u2_sqr))
        val den_x = mul(ratio.root, u2)
        val den_y = mul(mul(ratio.root, den_x), v)
        val x = abs(mul(mul(two, s), den_x))
        val y = mul(u1, den_y)
        val t = mul(x, y)
        if (!ratio.was_square || is_negative(t) || y.signum() == 0) return null
        return RistrettoElement(x, y, BigInteger.ONE, t)
    }

    fun encode(element: RistrettoElement): ByteArray {
        val u1 = mul(add(element.z, element.y), sub(element.z, element.y))
        val u2 = mul(element.x, element.y)
        val invsqrt = sqrt_ratio_m1(BigInteger.ONE, mul(u1, mul(u2, u2))).root
        val den1 = mul(invsqrt, u1)
        val den2 = mul(invsqrt, u2)
        val z_inv = mul(mul(den1, den2), element.t)
        val rotate = is_negative(mul(element.t, z_inv))
        val x = if (rotate) mul(element.y, sqrt_m1) else element.x
        var y = if (rotate) mul(element.x, sqrt_m1) else element.y
        val den_inv = if (rotate) mul(den1, invsqrt_a_minus_d) else den2
        if (is_negative(mul(x, z_inv))) y = neg(y)
        return integer_to_le(abs(mul(den_inv, sub(element.z, y))), RISTRETTO_ELEMENT_LEN)
    }

    fun is_identity(element: RistrettoElement): Boolean = encode(element).all { it == 0.toByte() }

    private fun map_to_element(t: BigInteger): RistrettoElement {
        val r = mul(sqrt_m1, mul(t, t))
        val u = mul(add(r, BigInteger.ONE), one_minus_d_sq)
        val v = mul(sub(neg(BigInteger.ONE), mul(r, curve_d)), add(r, curve_d))
        val ratio = sqrt_ratio_m1(u, v)
        val s = if (ratio.was_square) ratio.root else neg(abs(mul(ratio.root, t)))
        val c = if (ratio.was_square) neg(BigInteger.ONE) else r
        val n = sub(mul(mul(c, sub(r, BigInteger.ONE)), d_minus_one_sq), v)
        val w0 = mul(mul(two, s), v)
        val w1 = mul(n, sqrt_ad_minus_one)
        val w2 = sub(BigInteger.ONE, mul(s, s))
        val w3 = add(BigInteger.ONE, mul(s, s))
        return RistrettoElement(mul(w0, w3), mul(w2, w1), mul(w1, w3), mul(w0, w2))
    }

    fun from_uniform_bytes(bytes: ByteArray): RistrettoElement {
        require(bytes.size == RISTRETTO_UNIFORM_LEN)
        val half = RISTRETTO_UNIFORM_LEN / 2
        val first = bytes.copyOfRange(0, half)
        val second = bytes.copyOfRange(half, RISTRETTO_UNIFORM_LEN)
        first[half - 1] = (first[half - 1].toInt() and 0x7f).toByte()
        second[half - 1] = (second[half - 1].toInt() and 0x7f).toByte()
        return add(
            map_to_element(le_to_integer(first).mod(field_prime)),
            map_to_element(le_to_integer(second).mod(field_prime)),
        )
    }

    fun add(p: RistrettoElement, q: RistrettoElement): RistrettoElement {
        val a = mul(sub(p.y, p.x), sub(q.y, q.x))
        val b = mul(add(p.y, p.x), add(q.y, q.x))
        val c = mul(mul(p.t, curve_two_d), q.t)
        val d = mul(mul(two, p.z), q.z)
        val e = sub(b, a)
        val f = sub(d, c)
        val g = add(d, c)
        val h = add(b, a)
        return RistrettoElement(mul(e, f), mul(g, h), mul(f, g), mul(e, h))
    }

    fun multiply(element: RistrettoElement, scalar: BigInteger): RistrettoElement {
        val k = scalar.mod(group_order)
        var result = identity
        for (bit in k.bitLength() - 1 downTo 0) {
            result = add(result, result)
            if (k.testBit(bit)) result = add(result, element)
        }
        return result
    }

    fun scalar_from_wide_bytes(bytes: ByteArray): BigInteger = le_to_integer(bytes).mod(group_order)

    fun scalar_to_bytes(scalar: BigInteger): ByteArray =
        integer_to_le(scalar.mod(group_order), RISTRETTO_SCALAR_LEN)
}
