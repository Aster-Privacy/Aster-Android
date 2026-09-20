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
    ThemeBackground("calm_graphite", R.drawable.theme_bg_calm_graphite, ThemeCategory.calm, ColorThemeId.slate, Color(0xFF141722), "Aster"),
    ThemeBackground("calm_midnight", R.drawable.theme_bg_calm_midnight, ThemeCategory.calm, ColorThemeId.aster_blue, Color(0xFF090F22), "Aster"),
    ThemeBackground("calm_plum", R.drawable.theme_bg_calm_plum, ThemeCategory.calm, ColorThemeId.purple, Color(0xFF130C22), "Aster"),
    ThemeBackground("calm_pine", R.drawable.theme_bg_calm_pine, ThemeCategory.calm, ColorThemeId.teal, Color(0xFF0C2220), "Aster"),
    ThemeBackground("calm_ember", R.drawable.theme_bg_calm_ember, ThemeCategory.calm, ColorThemeId.orange, Color(0xFF221311), "Aster"),
    ThemeBackground("calm_ash", R.drawable.theme_bg_calm_ash, ThemeCategory.calm, ColorThemeId.default, Color(0xFF191B22), "Aster"),
    ThemeBackground("southern_ring", R.drawable.theme_bg_southern_ring, ThemeCategory.space, ColorThemeId.orange, Color(0xFF221617), "NASA, ESA, CSA, STScI, public domain"),
    ThemeBackground("whirlpool", R.drawable.theme_bg_whirlpool, ThemeCategory.space, ColorThemeId.indigo, Color(0xFF221F1C), "NASA, ESA, public domain"),
    ThemeBackground("ring_nebula", R.drawable.theme_bg_ring_nebula, ThemeCategory.space, ColorThemeId.aster_blue, Color(0xFF221F22), "NASA, ESA, CSA, STScI, public domain"),
    ThemeBackground("sombrero", R.drawable.theme_bg_sombrero, ThemeCategory.space, ColorThemeId.amber, Color(0xFF222020), "NASA, ESA, Hubble Heritage Team, public domain"),
    ThemeBackground("butterfly", R.drawable.theme_bg_butterfly, ThemeCategory.space, ColorThemeId.rose, Color(0xFF221A17), "NASA, ESA, Hubble SM4 ERO Team, public domain"),
    ThemeBackground("andromeda", R.drawable.theme_bg_andromeda, ThemeCategory.space, ColorThemeId.purple, Color(0xFF211D22), "Luc Viatour, CC BY-SA 4.0"),
    ThemeBackground("helix", R.drawable.theme_bg_helix, ThemeCategory.space, ColorThemeId.orange, Color(0xFF221514), "NASA, ESA, public domain"),
    ThemeBackground("pinwheel", R.drawable.theme_bg_pinwheel, ThemeCategory.space, ColorThemeId.indigo, Color(0xFF221F1F), "European Space Agency & NASA, CC BY 4.0"),
    ThemeBackground("crab", R.drawable.theme_bg_crab, ThemeCategory.space, ColorThemeId.teal, Color(0xFF20221D), "NASA, ESA, J. Hester and A. Loll, public domain"),
    ThemeBackground("cartwheel", R.drawable.theme_bg_cartwheel, ThemeCategory.space, ColorThemeId.rose, Color(0xFF221417), "NASA, ESA, CSA, STScI, public domain"),
    ThemeBackground("cosmic_cliffs", R.drawable.theme_bg_cosmic_cliffs, ThemeCategory.space, ColorThemeId.orange, Color(0xFF1D1922), "NASA, ESA, CSA, STScI, public domain"),
    ThemeBackground("deep_field", R.drawable.theme_bg_deep_field, ThemeCategory.space, ColorThemeId.amber, Color(0xFF221C17), "NASA, ESA, public domain"),
    ThemeBackground("dumbbell", R.drawable.theme_bg_dumbbell, ThemeCategory.space, ColorThemeId.cyan, Color(0xFF141922), "Luc Viatour, CC BY-SA 4.0"),
    ThemeBackground("ngc6397", R.drawable.theme_bg_ngc6397, ThemeCategory.space, ColorThemeId.aster_blue, Color(0xFF221A17), "NASA, ESA, T. Brown, S. Casertano, J. Anderson (STScI), public domain"),
    ThemeBackground("ngc6440", R.drawable.theme_bg_ngc6440, ThemeCategory.space, ColorThemeId.amber, Color(0xFF222120), "ESA/Webb, NASA & CSA, P. Freire, M. Cadelano, C. Pallanca, CC BY 4.0"),
    ThemeBackground("pleiades", R.drawable.theme_bg_pleiades, ThemeCategory.space, ColorThemeId.aster_blue, Color(0xFF0E1722), "Luc Viatour, CC BY-SA 4.0"),
    ThemeBackground("stephans_quintet", R.drawable.theme_bg_stephans_quintet, ThemeCategory.space, ColorThemeId.amber, Color(0xFF221B16), "NASA, ESA, public domain"),
    ThemeBackground("tucanae", R.drawable.theme_bg_tucanae, ThemeCategory.space, ColorThemeId.aster_blue, Color(0xFF202022), "ESO/M.-R. Cioni/VISTA Magellanic Cloud survey, Cambridge Astronomical Survey Unit, CC BY 4.0"),
    ThemeBackground("webb_deep", R.drawable.theme_bg_webb_deep, ThemeCategory.space, ColorThemeId.orange, Color(0xFF222021), "NASA, ESA, CSA, STScI, public domain"),
    ThemeBackground("westerlund1", R.drawable.theme_bg_westerlund1, ThemeCategory.space, ColorThemeId.aster_blue, Color(0xFF221D1F), "ESA/Webb, NASA & CSA, M. Zamani (ESA/Webb), M. G. Guarcello (INAF-OAPA) and the EWOCS team, CC BY 4.0"),
    ThemeBackground("westerlund2", R.drawable.theme_bg_westerlund2, ThemeCategory.space, ColorThemeId.pink, Color(0xFF22181A), "NASA, ESA, Hubble Heritage, public domain"),
    ThemeBackground("backlit_saturn", R.drawable.theme_bg_backlit_saturn, ThemeCategory.planets, ColorThemeId.amber, Color(0xFF221E1A), "NASA, JPL, Space Science Institute, public domain"),
    ThemeBackground("saturn_eclipse", R.drawable.theme_bg_saturn_eclipse, ThemeCategory.planets, ColorThemeId.aster_blue, Color(0xFF141722), "NASA, JPL-Caltech, Space Science Institute, public domain"),
    ThemeBackground("blue_marble", R.drawable.theme_bg_blue_marble, ThemeCategory.planets, ColorThemeId.aster_blue, Color(0xFF191C22), "NASA, Apollo 17, public domain"),
    ThemeBackground("jupiter", R.drawable.theme_bg_jupiter, ThemeCategory.planets, ColorThemeId.orange, Color(0xFF1E1F22), "NASA, ESA, Jupiter ERS Team, Ricardo Hueso, CC BY 4.0"),
    ThemeBackground("moon", R.drawable.theme_bg_moon, ThemeCategory.planets, ColorThemeId.slate, Color(0xFF222120), "Gregory H. Revera, CC BY-SA 3.0"),
    ThemeBackground("earthrise", R.drawable.theme_bg_earthrise, ThemeCategory.planets, ColorThemeId.aster_blue, Color(0xFF212220), "NASA, Bill Anders, public domain"),
    ThemeBackground("saturn", R.drawable.theme_bg_saturn, ThemeCategory.planets, ColorThemeId.amber, Color(0xFF221F16), "NASA, JPL, Space Science Institute, public domain"),
    ThemeBackground("neptune", R.drawable.theme_bg_neptune, ThemeCategory.planets, ColorThemeId.aster_blue, Color(0xFF070C22), "NASA, public domain"),
    ThemeBackground("mars", R.drawable.theme_bg_mars, ThemeCategory.planets, ColorThemeId.orange, Color(0xFF22140F), "ESA & MPS for OSIRIS Team, CC BY-SA 3.0 IGO"),
    ThemeBackground("pluto", R.drawable.theme_bg_pluto, ThemeCategory.planets, ColorThemeId.rose, Color(0xFF221F1D), "NASA, JHUAPL, SwRI, public domain"),
    ThemeBackground("seven_sisters", R.drawable.theme_bg_seven_sisters, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF0E1722), "Luc Viatour, CC BY-SA 4.0"),
    ThemeBackground("orion_belt", R.drawable.theme_bg_orion_belt, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF1E1D22), "ESO and Digitized Sky Survey 2, CC BY 4.0"),
    ThemeBackground("carina_star_cloud", R.drawable.theme_bg_carina_star_cloud, ThemeCategory.night_sky, ColorThemeId.slate, Color(0xFF1F1922), "A. Fujii, Public domain"),
    ThemeBackground("cygnus_field", R.drawable.theme_bg_cygnus_field, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF151822), "A. Fujii, Public domain"),
    ThemeBackground("gum_15", R.drawable.theme_bg_gum_15, ThemeCategory.night_sky, ColorThemeId.pink, Color(0xFF201822), "ESO/Digitized Sky Survey 2, Davide De Martin, CC BY 4.0"),
    ThemeBackground("iris_nebula", R.drawable.theme_bg_iris_nebula, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF131722), "T.A. Rector/University of Alaska Anchorage, H. Schweiker/WIYN and NOIRLab/NSF/AURA, CC BY 4.0"),
    ThemeBackground("lmc_field", R.drawable.theme_bg_lmc_field, ThemeCategory.night_sky, ColorThemeId.slate, Color(0xFF221816), "ESO/Digitized Sky Survey 2, Davide De Martin, CC BY 4.0"),
    ThemeBackground("monoceros_field", R.drawable.theme_bg_monoceros_field, ThemeCategory.night_sky, ColorThemeId.slate, Color(0xFF1C2022), "ESO/Digitized Sky Survey 2, Davide De Martin, CC BY 4.0"),
    ThemeBackground("pencil_field", R.drawable.theme_bg_pencil_field, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF151822), "ESO/Digitized Sky Survey 2, Davide De Martin, CC BY 4.0"),
    ThemeBackground("rho_field", R.drawable.theme_bg_rho_field, ThemeCategory.night_sky, ColorThemeId.pink, Color(0xFF221F1E), "ESO/DSS 2, CC BY 4.0"),
    ThemeBackground("rho_ophiuchi", R.drawable.theme_bg_rho_ophiuchi, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF1B1D22), "Judy Schmidt from Fresh Meadows, NY, USA, CC BY 2.0"),
    ThemeBackground("small_magellanic", R.drawable.theme_bg_small_magellanic, ThemeCategory.night_sky, ColorThemeId.slate, Color(0xFF222020), "NOIRLab/NSF/AURA/P. Horálek (Institute of Physics in Opava), CC BY 4.0"),
    ThemeBackground("southern_milky_way", R.drawable.theme_bg_southern_milky_way, ThemeCategory.night_sky, ColorThemeId.slate, Color(0xFF1C1222), "A. Fujii, Public domain"),
    ThemeBackground("taurus_dust", R.drawable.theme_bg_taurus_dust, ThemeCategory.night_sky, ColorThemeId.slate, Color(0xFF1E1D22), "Denis Paryshev, CC BY-SA 4.0"),
    ThemeBackground("witch_head", R.drawable.theme_bg_witch_head, ThemeCategory.night_sky, ColorThemeId.aster_blue, Color(0xFF151622), "NASA/STScI Digitized Sky Survey/Noel Carboni, Public domain"),
    ThemeBackground("iss_new_york", R.drawable.theme_bg_iss_new_york, ThemeCategory.cities, ColorThemeId.orange, Color(0xFF221C1E), "NASA, public domain"),
    ThemeBackground("chicago_orbit", R.drawable.theme_bg_chicago_orbit, ThemeCategory.cities, ColorThemeId.amber, Color(0xFF221C0E), "NASA Johnson Space Center, public domain"),
    ThemeBackground("iss_south_africa", R.drawable.theme_bg_iss_south_africa, ThemeCategory.cities, ColorThemeId.orange, Color(0xFF22160E), "NASA, public domain"),
    ThemeBackground("riffelsee", R.drawable.theme_bg_riffelsee, ThemeCategory.landscapes, ColorThemeId.orange, Color(0xFF202221), "Deralpinbergsteiger, CC BY-SA 4.0"),
    ThemeBackground("everest_alpenglow", R.drawable.theme_bg_everest_alpenglow, ThemeCategory.landscapes, ColorThemeId.indigo, Color(0xFF151122), "Nir B. Gurung, CC BY-SA 4.0"),
    ThemeBackground("nanda_khat", R.drawable.theme_bg_nanda_khat, ThemeCategory.landscapes, ColorThemeId.orange, Color(0xFF1C2022), "Harshit SR, CC BY-SA 4.0"),
    ThemeBackground("damaraland_sky", R.drawable.theme_bg_damaraland_sky, ThemeCategory.landscapes, ColorThemeId.aster_blue, Color(0xFF051022), "Giles Laurent, CC BY-SA 4.0"),
    ThemeBackground("oeschinensee", R.drawable.theme_bg_oeschinensee, ThemeCategory.landscapes, ColorThemeId.aster_blue, Color(0xFF131B22), "Giles Laurent, CC BY-SA 4.0"),
    ThemeBackground("lone_tree_galaxy", R.drawable.theme_bg_lone_tree_galaxy, ThemeCategory.landscapes, ColorThemeId.slate, Color(0xFF141922), "Tomás Andonie, CC BY 4.0"),
    ThemeBackground("paranal_sky", R.drawable.theme_bg_paranal_sky, ThemeCategory.landscapes, ColorThemeId.orange, Color(0xFF221D1F), "ESO/A. Ghizzi Panizza, CC BY 4.0"),
    ThemeBackground("stellisee", R.drawable.theme_bg_stellisee, ThemeCategory.water, ColorThemeId.teal, Color(0xFF051722), "Giles Laurent, CC BY-SA 4.0"),
    ThemeBackground("moon_fjord", R.drawable.theme_bg_moon_fjord, ThemeCategory.water, ColorThemeId.cyan, Color(0xFF101A22), "W.carter, CC0"),
    ThemeBackground("noctilucent_bay", R.drawable.theme_bg_noctilucent_bay, ThemeCategory.water, ColorThemeId.aster_blue, Color(0xFF0B1522), "Matthias Süßen, CC BY-SA 4.0"),
    ThemeBackground("reflection_lake", R.drawable.theme_bg_reflection_lake, ThemeCategory.water, ColorThemeId.teal, Color(0xFF1C2221), "Lassen Volcanic National Park, public domain"),
    ThemeBackground("lake_mcdonald", R.drawable.theme_bg_lake_mcdonald, ThemeCategory.water, ColorThemeId.orange, Color(0xFF221F1A), "Glacier National Park, public domain"),
    ThemeBackground("brofjorden_clouds", R.drawable.theme_bg_brofjorden_clouds, ThemeCategory.water, ColorThemeId.cyan, Color(0xFF161D22), "W.carter, CC0"),
    ThemeBackground("tiorati", R.drawable.theme_bg_tiorati, ThemeCategory.water, ColorThemeId.orange, Color(0xFF151B22), "Juliancolton, CC BY-SA 4.0"),
    ThemeBackground("moon_path", R.drawable.theme_bg_moon_path, ThemeCategory.water, ColorThemeId.aster_blue, Color(0xFF071422), "W.carter, CC0"),
)
