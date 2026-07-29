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
package org.astermail.android.util

import java.security.SecureRandom

private val alias_adjectives = listOf(
    "airy", "alert", "alpine", "amber", "ample", "ancient", "arctic", "arid", "artful", "ashen", "auburn", "autumn",
    "azure", "balmy", "beaming", "beige", "blithe", "blush", "bold", "boreal", "brave", "breezy", "bright", "brisk",
    "bronze", "calm", "candid", "carmine", "casual", "cedar", "celestial", "cherry", "chestnut", "chipper", "civic",
    "classic", "clear", "clever", "cobalt", "copper", "coral", "cosmic", "cozy", "cream", "crimson", "crisp",
    "crystal", "curious", "cyan", "daring", "dawn", "deft", "dewy", "digital", "distant", "dreamy", "dusky",
    "dusty", "eager", "early", "earthy", "eastern", "ebony", "elated", "electric", "elegant", "emerald", "endless",
    "epic", "eternal", "fabled", "fair", "faithful", "fancy", "fertile", "fiery", "fleet", "fluent", "foggy",
    "fond", "formal", "frosty", "gallant", "garnet", "gentle", "giant", "gilded", "ginger", "glacial", "gleaming",
    "glossy", "golden", "graceful", "grand", "granite", "grassy", "hardy", "harmonic", "hasty", "hazel", "hazy",
    "hearty", "heroic", "hidden", "high", "hollow", "honest", "humble", "icy", "ideal", "immense", "indigo",
    "inland", "ivory", "jade", "jolly", "jovial", "joyful", "keen", "kindly", "lavender", "lemon", "lilac", "lime",
    "lively", "lofty", "lone", "loyal", "lucid", "lucky", "lunar", "lush", "magenta", "magnetic", "maple", "maroon",
    "mauve", "mellow", "merry", "mighty", "mild", "mint", "misty", "mocha", "modern", "modest", "moonlit", "mossy",
    "mystic", "native", "navy", "neat", "nimble", "noble", "nordic", "northern", "novel", "oaken", "oceanic",
    "ochre", "olive", "opal", "opaline", "orbital", "ornate", "pacific", "patient", "peach", "pearl", "pewter",
    "placid", "playful", "plucky", "plum", "polar", "polished", "prime", "pristine", "prompt", "proud", "pure",
    "purple", "quaint", "quantum", "quick", "quiet", "radiant", "rapid", "rare", "ready", "regal", "remote",
    "restful", "rich", "rising", "roaming", "robust", "rocky", "rosy", "ruby", "rugged", "russet", "rustic",
    "saffron", "sage", "salmon", "sandy", "sapphire", "savvy", "scarlet", "scenic", "secret", "sepia", "serene",
    "shady", "sharp", "sheer", "shining", "sienna", "silent", "silken", "silver", "simple", "sincere", "slate",
    "sleek", "slender", "smooth", "snowy", "solar", "solemn", "solid", "soothing", "southern", "sparkling",
    "spirited", "splendid", "spry", "stable", "starry", "steady", "stellar", "still", "stoic", "stormy", "stout",
    "sturdy", "subtle", "sunlit", "sunny", "supple", "swift", "tawny", "teal", "tender", "thermal", "thrifty",
    "tidal", "tidy", "timely", "topaz", "tranquil", "true", "turquoise", "twilight", "ultra", "umber", "upbeat",
    "urban", "valiant", "vast", "velvet", "verdant", "vermilion", "vibrant", "vigilant", "vintage", "violet",
    "viridian", "vital", "vivid", "wandering", "warm", "watchful", "western", "whimsical", "wild", "willing",
    "windy", "winter", "wise", "wistful", "witty", "wooded", "woven", "young", "zealous", "zesty",
)

private val alias_nouns = listOf(
    "anchor", "apex", "arbor", "arch", "archer", "arrow", "ash", "aspen", "atlas", "atoll", "aurora", "badger",
    "banner", "basin", "bay", "beacon", "beam", "bear", "beech", "bell", "birch", "bison", "blade", "blossom",
    "bluff", "bolt", "boulder", "bramble", "branch", "breeze", "bridge", "brook", "butte", "cabin", "cactus",
    "cairn", "canal", "candle", "canopy", "canyon", "cape", "cardinal", "cascade", "castle", "cavern", "cedar",
    "chalet", "chamber", "channel", "chapel", "chart", "cherry", "chime", "cinder", "cipher", "circuit", "citadel",
    "cliff", "cloud", "clover", "coast", "comet", "compass", "condor", "copse", "coral", "cottage", "cove", "crag",
    "crane", "crater", "creek", "crest", "crown", "crystal", "cypress", "dale", "dawn", "dell", "delta", "dune",
    "dusk", "eagle", "echo", "eddy", "edge", "elder", "ember", "ermine", "estuary", "fable", "falcon", "fathom",
    "feather", "fern", "field", "finch", "fjord", "flame", "flare", "fleet", "flint", "flora", "flume", "forest",
    "forge", "fountain", "fox", "frost", "galaxy", "gale", "gallery", "garden", "garland", "gate", "gazelle",
    "geyser", "glacier", "glade", "gleam", "glen", "globe", "gorge", "grange", "granite", "grotto", "grove", "gulf",
    "gull", "harbor", "harvest", "haven", "hawk", "haze", "heath", "heron", "hollow", "horizon", "ibis", "inlet",
    "island", "isle", "jasmine", "jetty", "journey", "juniper", "kestrel", "keystone", "knoll", "lagoon", "lake",
    "lantern", "larch", "lark", "laurel", "ledge", "legend", "lichen", "lily", "linden", "lodge", "lotus", "lynx",
    "mallard", "mantle", "maple", "marble", "marina", "marsh", "meadow", "mesa", "meteor", "mirage", "mist", "moor",
    "moss", "mountain", "mulberry", "myrtle", "nebula", "nectar", "needle", "nest", "nexus", "node", "nomad",
    "north", "nova", "oak", "oasis", "ocean", "orbit", "orchard", "orchid", "osprey", "otter", "outpost", "owl",
    "palm", "pasture", "path", "peak", "pearl", "pebble", "pelican", "petal", "pier", "pine", "pinnacle", "plain",
    "plateau", "plaza", "plume", "pond", "poplar", "portal", "prairie", "prism", "puffin", "quarry", "quartz",
    "quill", "rapids", "raven", "ravine", "reef", "ridge", "rill", "rime", "river", "rivulet", "robin", "rock",
    "root", "rose", "rowan", "sable", "saddle", "sail", "sandbar", "sapling", "savanna", "scarp", "sequoia",
    "shade", "shale", "shore", "sierra", "signal", "silo", "sky", "slope", "sparrow", "spire", "spring", "spruce",
    "spur", "star", "station", "steppe", "stone", "stork", "storm", "strand", "stream", "summit", "sundial",
    "sunrise", "sunset", "surf", "swallow", "swan", "tarn", "temple", "terrace", "thicket", "thistle", "thorn",
    "thunder", "tide", "timber", "torch", "tower", "trail", "tundra", "tunnel", "turret", "valley", "vault", "veil",
    "vertex", "vessel", "vista", "vortex", "walnut", "warbler", "wave", "willow", "wind", "wolf", "wood", "wren",
    "yarrow", "zenith", "zephyr",
)

private const val alias_token_alphabet = "abcdefghijklmnopqrstuvwxyz234567"
private const val alias_token_length = 8

private val alias_random = SecureRandom()

private fun uniform_random_index(modulus: Int): Int {
    val limit = Int.MAX_VALUE - (Int.MAX_VALUE % modulus)
    while (true) {
        val value = alias_random.nextInt(Int.MAX_VALUE)
        if (value < limit) return value % modulus
    }
}

private fun generate_alias_token(): String {
    val builder = StringBuilder(alias_token_length)
    repeat(alias_token_length) {
        builder.append(alias_token_alphabet[uniform_random_index(alias_token_alphabet.length)])
    }
    return builder.toString()
}

fun generate_random_local_part(): String {
    val adjective = alias_adjectives[uniform_random_index(alias_adjectives.size)]
    val noun = alias_nouns[uniform_random_index(alias_nouns.size)]
    return "${adjective}.${noun}${generate_alias_token()}"
}
