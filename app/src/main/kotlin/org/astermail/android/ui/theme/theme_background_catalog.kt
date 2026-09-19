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


package org.astermail.android.ui.theme

import androidx.compose.ui.graphics.Color
import org.astermail.android.R
import org.astermail.android.design.ColorThemeId

val theme_backgrounds = listOf(
    ThemeBackground("southern_ring", R.drawable.theme_bg_southern_ring, ThemeCategory.space, ColorThemeId.orange, Color(0xFF221617), "NASA, ESA, CSA, STScI, public domain"),
    ThemeBackground("whirlpool", R.drawable.theme_bg_whirlpool, ThemeCategory.space, ColorThemeId.indigo, Color(0xFF221F1C), "NASA, ESA, public domain"),
    ThemeBackground("ring_nebula", R.drawable.theme_bg_ring_nebula, ThemeCategory.space, ColorThemeId.aster_blue, Color(0xFF221F22), "NASA, ESA, CSA, public domain"),
    ThemeBackground("sombrero", R.drawable.theme_bg_sombrero, ThemeCategory.space, ColorThemeId.amber, Color(0xFF222020), "NASA, ESA, public domain"),
    ThemeBackground("butterfly", R.drawable.theme_bg_butterfly, ThemeCategory.space, ColorThemeId.rose, Color(0xFF221A17), "NASA, ESA, public domain"),
    ThemeBackground("andromeda", R.drawable.theme_bg_andromeda, ThemeCategory.space, ColorThemeId.purple, Color(0xFF211D22), "Luc Viatour, CC BY-SA 4.0"),
    ThemeBackground("helix", R.drawable.theme_bg_helix, ThemeCategory.space, ColorThemeId.orange, Color(0xFF221514), "NASA, ESA, public domain"),
    ThemeBackground("pinwheel", R.drawable.theme_bg_pinwheel, ThemeCategory.space, ColorThemeId.indigo, Color(0xFF221F1F), "ESA/Hubble, NASA, CC BY 4.0"),
    ThemeBackground("cartwheel", R.drawable.theme_bg_cartwheel, ThemeCategory.space, ColorThemeId.rose, Color(0xFF221417), "NASA, ESA, CSA, STScI, public domain"),
    ThemeBackground("westerlund2", R.drawable.theme_bg_westerlund2, ThemeCategory.space, ColorThemeId.pink, Color(0xFF22181A), "NASA, ESA, Hubble Heritage, public domain"),
    ThemeBackground("cosmic_cliffs", R.drawable.theme_bg_cosmic_cliffs, ThemeCategory.space, ColorThemeId.orange, Color(0xFF1D1922), "NASA, ESA, CSA, STScI, public domain"),
    ThemeBackground("pleiades", R.drawable.theme_bg_pleiades, ThemeCategory.space, ColorThemeId.aster_blue, Color(0xFF0E1722), "Luc Viatour, CC BY-SA 4.0"),
    ThemeBackground("deep_field", R.drawable.theme_bg_deep_field, ThemeCategory.space, ColorThemeId.amber, Color(0xFF221C17), "NASA, ESA, public domain"),
    ThemeBackground("stephans_quintet", R.drawable.theme_bg_stephans_quintet, ThemeCategory.space, ColorThemeId.amber, Color(0xFF221B16), "NASA, ESA, public domain"),
    ThemeBackground("crab", R.drawable.theme_bg_crab, ThemeCategory.space, ColorThemeId.teal, Color(0xFF20221D), "NASA, ESA, public domain"),
    ThemeBackground("dumbbell", R.drawable.theme_bg_dumbbell, ThemeCategory.space, ColorThemeId.cyan, Color(0xFF141922), "Luc Viatour, CC BY-SA 4.0"),
    ThemeBackground("westerlund1", R.drawable.theme_bg_westerlund1, ThemeCategory.space, ColorThemeId.aster_blue, Color(0xFF221D1F), "ESA/Webb, NASA & CSA, M. Zamani (ESA/Webb), M. G. Guarcello (INAF-OAPA) and the EWOCS team, CC BY 4.0"),
    ThemeBackground("tucanae", R.drawable.theme_bg_tucanae, ThemeCategory.space, ColorThemeId.aster_blue, Color(0xFF202022), "ESO/M.-R. Cioni/VISTA Magellanic Cloud survey, Cambridge Astronomical Survey Unit, CC BY 4.0"),
    ThemeBackground("ngc6440", R.drawable.theme_bg_ngc6440, ThemeCategory.space, ColorThemeId.amber, Color(0xFF222120), "ESA/Webb, NASA & CSA, P. Freire, M. Cadelano, C. Pallanca, CC BY 4.0"),
    ThemeBackground("webb_deep", R.drawable.theme_bg_webb_deep, ThemeCategory.space, ColorThemeId.orange, Color(0xFF222021), "NASA, ESA, CSA, STScI, public domain"),
    ThemeBackground("ngc6397", R.drawable.theme_bg_ngc6397, ThemeCategory.space, ColorThemeId.aster_blue, Color(0xFF221A17), "NASA, ESA, T. Brown, S. Casertano, J. Anderson (STScI), public domain"),
    ThemeBackground("backlit_saturn", R.drawable.theme_bg_backlit_saturn, ThemeCategory.planets, ColorThemeId.amber, Color(0xFF221E1A), "NASA, JPL, SSI, public domain"),
    ThemeBackground("saturn_eclipse", R.drawable.theme_bg_saturn_eclipse, ThemeCategory.planets, ColorThemeId.aster_blue, Color(0xFF141722), "NASA, JPL, SSI, public domain"),
    ThemeBackground("blue_marble", R.drawable.theme_bg_blue_marble, ThemeCategory.planets, ColorThemeId.aster_blue, Color(0xFF191C22), "NASA, Apollo 17, public domain"),
    ThemeBackground("totality", R.drawable.theme_bg_totality, ThemeCategory.planets, ColorThemeId.cyan, Color(0xFF1B1E22), "NASA, public domain"),
    ThemeBackground("jupiter", R.drawable.theme_bg_jupiter, ThemeCategory.planets, ColorThemeId.orange, Color(0xFF1E1F22), "NASA, ESA, Jupiter ERS Team, CC BY 4.0"),
    ThemeBackground("moon", R.drawable.theme_bg_moon, ThemeCategory.planets, ColorThemeId.slate, Color(0xFF222120), "Gregory H. Revera, CC BY-SA 3.0"),
    ThemeBackground("earthrise", R.drawable.theme_bg_earthrise, ThemeCategory.planets, ColorThemeId.aster_blue, Color(0xFF212220), "NASA, Bill Anders, public domain"),
    ThemeBackground("saturn", R.drawable.theme_bg_saturn, ThemeCategory.planets, ColorThemeId.amber, Color(0xFF221F16), "NASA, JPL, SSI, public domain"),
    ThemeBackground("neptune", R.drawable.theme_bg_neptune, ThemeCategory.planets, ColorThemeId.aster_blue, Color(0xFF070C22), "NASA, public domain"),
    ThemeBackground("mars", R.drawable.theme_bg_mars, ThemeCategory.planets, ColorThemeId.orange, Color(0xFF22140F), "ESA, MPS, CC BY-SA 3.0 IGO"),
    ThemeBackground("io", R.drawable.theme_bg_io, ThemeCategory.planets, ColorThemeId.amber, Color(0xFF221F0E), "NASA, JPL, public domain"),
    ThemeBackground("jupiter_pole", R.drawable.theme_bg_jupiter_pole, ThemeCategory.planets, ColorThemeId.slate, Color(0xFF22211F), "NASA, JPL, SwRI, MSSS, public domain"),
    ThemeBackground("pluto", R.drawable.theme_bg_pluto, ThemeCategory.planets, ColorThemeId.rose, Color(0xFF221F1D), "NASA, JHUAPL, SwRI, public domain"),
    ThemeBackground("uranus", R.drawable.theme_bg_uranus, ThemeCategory.planets, ColorThemeId.cyan, Color(0xFF1B1A22), "NASA, ESA, CSA, STScI, CC BY 4.0"),
    ThemeBackground("europa", R.drawable.theme_bg_europa, ThemeCategory.planets, ColorThemeId.orange, Color(0xFF221D15), "NASA, JPL, DLR, public domain"),
    ThemeBackground("ganymede", R.drawable.theme_bg_ganymede, ThemeCategory.planets, ColorThemeId.slate, Color(0xFF22201D), "NASA, Kevin M. Gill, CC BY 2.0"),
    ThemeBackground("seven_sisters", R.drawable.theme_bg_seven_sisters, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF0E1722), "Lviatour, CC BY-SA 4.0"),
    ThemeBackground("cygnus_field", R.drawable.theme_bg_cygnus_field, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF151822), "A. Fujii, Public domain"),
    ThemeBackground("witch_head", R.drawable.theme_bg_witch_head, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF151622), "NASA/STScI Digitized Sky Survey/Noel Carboni, Public domain"),
    ThemeBackground("rho_ophiuchi", R.drawable.theme_bg_rho_ophiuchi, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF1B1D22), "Judy Schmidt from Fresh Meadows, NY, USA, CC BY 2.0"),
    ThemeBackground("iris_nebula", R.drawable.theme_bg_iris_nebula, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF131722), "T.A. Rector/University of Alaska Anchorage, H. Schweiker/WIYN and NOIRLab/NSF/AURA, CC BY 4.0"),
    ThemeBackground("pencil_field", R.drawable.theme_bg_pencil_field, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF151822), "ESO/Digitized Sky Survey 2, Davide De Martin, CC BY 4.0"),
    ThemeBackground("orion_belt", R.drawable.theme_bg_orion_belt, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF1E1D22), "ESO and Digitized Sky Survey 2, CC BY 4.0"),
    ThemeBackground("gum_15", R.drawable.theme_bg_gum_15, ThemeCategory.night_sky, ColorThemeId.pink, Color(0xFF201822), "ESO/Digitized Sky Survey 2, Davide De Martin, CC BY 4.0"),
    ThemeBackground("taurus_dust", R.drawable.theme_bg_taurus_dust, ThemeCategory.night_sky, ColorThemeId.slate, Color(0xFF1E1D22), "Denis Paryshev, CC BY-SA 4.0"),
    ThemeBackground("rho_field", R.drawable.theme_bg_rho_field, ThemeCategory.night_sky, ColorThemeId.pink, Color(0xFF221F1E), "ESO/DSS 2, CC BY 4.0"),
    ThemeBackground("southern_milky_way", R.drawable.theme_bg_southern_milky_way, ThemeCategory.night_sky, ColorThemeId.slate, Color(0xFF1C1222), "A. Fujii, Public domain"),
    ThemeBackground("carina_star_cloud", R.drawable.theme_bg_carina_star_cloud, ThemeCategory.night_sky, ColorThemeId.slate, Color(0xFF1F1922), "A. Fujii, Public domain"),
    ThemeBackground("monoceros_field", R.drawable.theme_bg_monoceros_field, ThemeCategory.night_sky, ColorThemeId.slate, Color(0xFF1C2022), "ESO/Digitized Sky Survey 2, Davide De Martin, CC BY 4.0"),
    ThemeBackground("small_magellanic", R.drawable.theme_bg_small_magellanic, ThemeCategory.night_sky, ColorThemeId.slate, Color(0xFF222020), "NOIRLab/NSF/AURA/P. Horálek (Institute of Physics in Opava), CC BY 4.0"),
    ThemeBackground("lagoon_blue", R.drawable.theme_bg_lagoon_blue, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF181D22), "Dylan O'Donnell, deography.com, CC0"),
    ThemeBackground("lmc_field", R.drawable.theme_bg_lmc_field, ThemeCategory.night_sky, ColorThemeId.slate, Color(0xFF221816), "ESO/Digitized Sky Survey 2, Davide De Martin, CC BY 4.0"),
    ThemeBackground("pudong", R.drawable.theme_bg_pudong, ThemeCategory.cities, ColorThemeId.pink, Color(0xFF151022), "King of Hearts, CC BY-SA 4.0"),
    ThemeBackground("midtown_manhattan", R.drawable.theme_bg_midtown_manhattan, ThemeCategory.cities, ColorThemeId.aster_blue, Color(0xFF1D1D22), "King of Hearts, CC BY-SA 4.0"),
    ThemeBackground("tokyo_tower", R.drawable.theme_bg_tokyo_tower, ThemeCategory.cities, ColorThemeId.teal, Color(0xFF192022), "David Kernan, CC BY 4.0"),
    ThemeBackground("helix_bridge", R.drawable.theme_bg_helix_bridge, ThemeCategory.cities, ColorThemeId.orange, Color(0xFF221819), "Diego Delso, CC BY-SA 4.0"),
    ThemeBackground("copenhagen_lakes", R.drawable.theme_bg_copenhagen_lakes, ThemeCategory.cities, ColorThemeId.aster_blue, Color(0xFF131722), "Kristoffer Trolle from Copenhagen, Denmark, CC BY 2.0"),
    ThemeBackground("ferry_building", R.drawable.theme_bg_ferry_building, ThemeCategory.cities, ColorThemeId.rose, Color(0xFF221E1E), "Dllu, CC BY-SA 4.0"),
    ThemeBackground("berlin_bode", R.drawable.theme_bg_berlin_bode, ThemeCategory.cities, ColorThemeId.cyan, Color(0xFF161E22), "Diego Delso, CC BY-SA 4.0"),
    ThemeBackground("manhattan_moon", R.drawable.theme_bg_manhattan_moon, ThemeCategory.cities, ColorThemeId.orange, Color(0xFF221D1A), "Rhododendrites, CC BY-SA 4.0"),
    ThemeBackground("dotonbori", R.drawable.theme_bg_dotonbori, ThemeCategory.cities, ColorThemeId.pink, Color(0xFF211E22), "Martin Falbisoner, CC BY-SA 4.0"),
    ThemeBackground("salzburg_night", R.drawable.theme_bg_salzburg_night, ThemeCategory.cities, ColorThemeId.amber, Color(0xFF221D12), "Max Dawncat, CC BY 2.0"),
    ThemeBackground("iss_new_york", R.drawable.theme_bg_iss_new_york, ThemeCategory.cities, ColorThemeId.orange, Color(0xFF221C1E), "NASA, Public domain"),
    ThemeBackground("chicago_orbit", R.drawable.theme_bg_chicago_orbit, ThemeCategory.cities, ColorThemeId.amber, Color(0xFF221C0E), "NASA, Public domain"),
    ThemeBackground("iss_south_africa", R.drawable.theme_bg_iss_south_africa, ThemeCategory.cities, ColorThemeId.orange, Color(0xFF22160E), "NASA, Public domain"),
    ThemeBackground("victoria_harbour", R.drawable.theme_bg_victoria_harbour, ThemeCategory.cities, ColorThemeId.amber, Color(0xFF221C12), "Wilfredor, CC0"),
    ThemeBackground("frankfurt_skyline", R.drawable.theme_bg_frankfurt_skyline, ThemeCategory.cities, ColorThemeId.orange, Color(0xFF221A12), "Jörg Braukmann, CC BY-SA 4.0"),
    ThemeBackground("darling_harbour", R.drawable.theme_bg_darling_harbour, ThemeCategory.cities, ColorThemeId.amber, Color(0xFF221D15), "Dietmar Rabich, CC BY-SA 4.0"),
    ThemeBackground("etna_eruption", R.drawable.theme_bg_etna_eruption, ThemeCategory.landscapes, ColorThemeId.orange, Color(0xFF220600), "gnuckx, CC BY 2.0"),
    ThemeBackground("riffelsee", R.drawable.theme_bg_riffelsee, ThemeCategory.landscapes, ColorThemeId.orange, Color(0xFF202221), "Deralpinbergsteiger, CC BY-SA 4.0"),
    ThemeBackground("tre_cime", R.drawable.theme_bg_tre_cime, ThemeCategory.landscapes, ColorThemeId.aster_blue, Color(0xFF151922), "Giorgia Hofer/IAU OAE, CC BY 4.0"),
    ThemeBackground("fournaise_lava", R.drawable.theme_bg_fournaise_lava, ThemeCategory.landscapes, ColorThemeId.rose, Color(0xFF220602), "Rémih, CC BY-SA 4.0"),
    ThemeBackground("everest_alpenglow", R.drawable.theme_bg_everest_alpenglow, ThemeCategory.landscapes, ColorThemeId.indigo, Color(0xFF151122), "Nir B. Gurung, CC BY-SA 4.0"),
    ThemeBackground("serra_capivara", R.drawable.theme_bg_serra_capivara, ThemeCategory.landscapes, ColorThemeId.rose, Color(0xFF221D22), "Thiagomarcelcampi, CC BY-SA 4.0"),
    ThemeBackground("fimmvorduhals", R.drawable.theme_bg_fimmvorduhals, ThemeCategory.landscapes, ColorThemeId.pink, Color(0xFF22161E), "Boaworm, CC BY 3.0"),
    ThemeBackground("paranal_sky", R.drawable.theme_bg_paranal_sky, ThemeCategory.landscapes, ColorThemeId.orange, Color(0xFF221D1F), "ESO/A. Ghizzi Panizza, CC BY 4.0"),
    ThemeBackground("nanda_khat", R.drawable.theme_bg_nanda_khat, ThemeCategory.landscapes, ColorThemeId.orange, Color(0xFF1C2022), "Harshit SR, CC BY-SA 4.0"),
    ThemeBackground("sunset_crater", R.drawable.theme_bg_sunset_crater, ThemeCategory.landscapes, ColorThemeId.indigo, Color(0xFF141322), "Coconino National Forest, CC0"),
    ThemeBackground("elbrus_camp", R.drawable.theme_bg_elbrus_camp, ThemeCategory.landscapes, ColorThemeId.rose, Color(0xFF1A1A22), "oliwok, CC BY 4.0"),
    ThemeBackground("lava_coast", R.drawable.theme_bg_lava_coast, ThemeCategory.landscapes, ColorThemeId.orange, Color(0xFF221915), "Rennett Stowe from USA, CC BY 2.0"),
    ThemeBackground("pedra_azul", R.drawable.theme_bg_pedra_azul, ThemeCategory.landscapes, ColorThemeId.orange, Color(0xFF22211F), "EduardoMSNeves, CC BY-SA 4.0"),
    ThemeBackground("damaraland_sky", R.drawable.theme_bg_damaraland_sky, ThemeCategory.landscapes, ColorThemeId.aster_blue, Color(0xFF051022), "Giles Laurent, CC BY-SA 4.0"),
    ThemeBackground("oeschinensee", R.drawable.theme_bg_oeschinensee, ThemeCategory.landscapes, ColorThemeId.aster_blue, Color(0xFF131B22), "Giles Laurent, CC BY-SA 4.0"),
    ThemeBackground("lone_tree_galaxy", R.drawable.theme_bg_lone_tree_galaxy, ThemeCategory.landscapes, ColorThemeId.slate, Color(0xFF141922), "Tomás Andonie, CC BY 4.0"),
    ThemeBackground("stellisee", R.drawable.theme_bg_stellisee, ThemeCategory.water, ColorThemeId.teal, Color(0xFF051722), "Giles Laurent, CC BY-SA 4.0"),
    ThemeBackground("moon_fjord", R.drawable.theme_bg_moon_fjord, ThemeCategory.water, ColorThemeId.cyan, Color(0xFF101A22), "W.carter, CC0"),
    ThemeBackground("glowing_tide", R.drawable.theme_bg_glowing_tide, ThemeCategory.water, ColorThemeId.aster_blue, Color(0xFF060F22), "Panamitsu, CC BY-SA 4.0"),
    ThemeBackground("guilin_pagodas", R.drawable.theme_bg_guilin_pagodas, ThemeCategory.water, ColorThemeId.orange, Color(0xFF221A10), "King of Hearts, CC BY-SA 4.0"),
    ThemeBackground("reine", R.drawable.theme_bg_reine, ThemeCategory.water, ColorThemeId.aster_blue, Color(0xFF0C1722), "Christoph Strässler, CC BY-SA 2.0"),
    ThemeBackground("summit_lake", R.drawable.theme_bg_summit_lake, ThemeCategory.water, ColorThemeId.orange, Color(0xFF221B16), "ForestWander, CC BY-SA 3.0 us"),
    ThemeBackground("llangorse_crannog", R.drawable.theme_bg_llangorse_crannog, ThemeCategory.water, ColorThemeId.aster_blue, Color(0xFF121622), "TheGasmanDruid, CC BY-SA 4.0"),
    ThemeBackground("svolvaer_harbor", R.drawable.theme_bg_svolvaer_harbor, ThemeCategory.water, ColorThemeId.aster_blue, Color(0xFF0A1522), "Christoph Strässler, CC BY-SA 2.0"),
    ThemeBackground("noctilucent_bay", R.drawable.theme_bg_noctilucent_bay, ThemeCategory.water, ColorThemeId.aster_blue, Color(0xFF0B1522), "Matthias Süßen, CC BY-SA 4.0"),
    ThemeBackground("reflection_lake", R.drawable.theme_bg_reflection_lake, ThemeCategory.water, ColorThemeId.teal, Color(0xFF1C2221), "LassenNPS, Public domain"),
    ThemeBackground("lake_mcdonald", R.drawable.theme_bg_lake_mcdonald, ThemeCategory.water, ColorThemeId.orange, Color(0xFF221F1A), "GlacierNPS, Public domain"),
    ThemeBackground("brofjorden_clouds", R.drawable.theme_bg_brofjorden_clouds, ThemeCategory.water, ColorThemeId.cyan, Color(0xFF161D22), "W.carter, CC0"),
    ThemeBackground("tiorati", R.drawable.theme_bg_tiorati, ThemeCategory.water, ColorThemeId.orange, Color(0xFF151B22), "Juliancolton, CC BY-SA 4.0"),
    ThemeBackground("kollerod_beach", R.drawable.theme_bg_kollerod_beach, ThemeCategory.water, ColorThemeId.cyan, Color(0xFF041522), "W.carter, CC0"),
    ThemeBackground("ogwen", R.drawable.theme_bg_ogwen, ThemeCategory.water, ColorThemeId.amber, Color(0xFF221E1B), "John Badham, CC BY-SA 4.0"),
    ThemeBackground("moon_path", R.drawable.theme_bg_moon_path, ThemeCategory.water, ColorThemeId.aster_blue, Color(0xFF071422), "W.carter, CC0"),
)
