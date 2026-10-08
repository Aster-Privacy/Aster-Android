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

package org.astermail.android.settings

val featured_gallery_slugs: List<String> = listOf(
    "blue_marble",
    "jupiter",
    "neptune_full",
    "mars",
    "helix",
    "ring_nebula",
    "cosmic_cliffs",
    "lower_antelope_canyon",
    "everest_alpenglow",
    "pillars_creation_hst",
    "venus_globe",
    "southern_ring",
    "crab",
    "riffelsee",
    "seven_sisters",
    "yellowstone_national_park",
    "aurora_australis_over",
    "great_red_spot",
    "sunset_over_north",
    "matterhorn_wooden_cottages",
    "moon",
    "pluto",
    "cosmic_winter_wonderland",
    "gentle_but_wide",
    "havelock_island_mangrove",
    "vela_supernova_remnant",
    "iris_nebula",
    "milky_way_taganay",
    "autumn_birch_stakrimo",
    "dead_vlei_sossusvlei",
    "neptune",
    "westerlund2",
    "orion_belt",
    "surf_august",
    "multicolored_aurora_borealis",
    "canal_houses_oude",
    "sunflower_field_milky",
    "stellisee",
    "moon_path",
    "lone_tree_front",
    "gum_15",
    "witch_head",
    "northern_lights_curtains",
    "multnomah_falls_october",
    "lower_manhattan",
    "torun_old_town",
    "milky_way_aligned",
    "ultraviolet_cygnus_loop",
    "upper_antelope_canyon",
    "dune",
    "sunrise_point_bryce",
    "malapascua_island_tropical",
    "vista_s_infrared",
    "batok_bromo_volcano",
    "virmalised_aurora_borealis",
    "zagedan_ridge_zagedan",
    "klonglan_waterfall",
    "cartwheel_galaxy_nircam",
    "forrest_gump_point",
    "singapore_sg_marina",
    "snowy_forest_boreal",
    "westerlund1",
    "bryce_canyon_wallstreet",
    "duna_en_sossusvlei",
    "tucanae",
    "rho_ophiuchi",
    "kuantan_waves_breaking",
    "low_sun_filtering",
    "ratargul_trees",
    "water_reflection_canal",
    "three_cliffs_zion",
    "europas_ice_shell",
    "painted_desert_badlands",
    "himalayas_ama_dablam",
    "toronto_skyline_bei",
    "chateau_frontenac_illuminated",
)

val hidden_gallery_slugs: Set<String> = setOf(
    "deep_field",
    "webb_deep",
    "webb_s_first",
    "backlit_saturn",
    "earthrise",
    "nasa_apollo8_dec24",
    "dumbbell",
    "sombrero",
    "saturn_eclipse",
    "stephans_quintet",
    "cone_nebula_ngc",
    "ngc6440",
    "chicago_orbit",
    "iss_south_africa",
    "iss_aurora_borealis",
    "iss_night_earth",
    "manhattan_night_north",
    "lmc_field",
    "monoceros_field",
    "pencil_field",
    "southern_milky_way",
    "lone_tree_galaxy",
    "milky_way_arch",
    "milky_way_zodiacal",
    "starry_night_la",
    "taurus_dust",
    "paranal_beneath_milky",
    "aurora_abisko_near",
    "aurora_australis_iss",
    "magenta_g5_aurora",
    "northern_lights_over",
    "pink_aurora_with",
    "moon_fjord",
    "reflection_lake",
    "lake_mcdonald",
    "brofjorden_clouds",
    "waves_theatre_de",
    "foggy_fanal_forest",
    "morning_fog_some",
    "dulmen_kirchspiel_dernekamp",
    "lawachara_forest",
    "meadow_smoky_mountains",
    "tea_fields_nilgiris",
    "mountains_wadi_shawka",
    "nanda_devi_peak",
    "shivaliks_himalayas_aerial",
    "desert_exploration",
    "sossusvlei_dune_ripples",
)

private val featured_gallery_rank: Map<String, Int> =
    featured_gallery_slugs.withIndex().associate { it.value to it.index }

fun curate_gallery_items(items: List<GalleryItem>): List<GalleryItem> =
    items
        .filterNot { it.slug in hidden_gallery_slugs }
        .sortedBy { featured_gallery_rank[it.slug] ?: Int.MAX_VALUE }
