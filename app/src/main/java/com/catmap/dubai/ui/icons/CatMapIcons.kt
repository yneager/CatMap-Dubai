package com.catmap.dubai.ui.icons

import androidx.annotation.DrawableRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.unit.dp
import com.catmap.dubai.R
import com.catmap.dubai.data.MapLayer
import com.catmap.dubai.data.model.AnimalType
import com.catmap.dubai.data.model.PostCategory
import com.catmap.dubai.gamification.Badge

/** One registry for original CatMap artwork, including bitmap consumers. */
object CatMapIcons {
    val CAT = R.drawable.ic_cm_cat
    val DOG = R.drawable.ic_cm_dog
    val BIRD = R.drawable.ic_cm_bird
    val RABBIT = R.drawable.ic_cm_rabbit
    val PAW = R.drawable.ic_cm_paw
    val LOST = R.drawable.ic_cm_lost
    val FOUND = R.drawable.ic_cm_found
    val SPOTTED = R.drawable.ic_cm_spotted
    val HELP = R.drawable.ic_cm_help
    val STREET = R.drawable.ic_cm_street
    val ADOPTION = R.drawable.ic_cm_adoption
    val NOTE = R.drawable.ic_cm_note
    val STATION = R.drawable.ic_cm_station
    val SITTER = R.drawable.ic_cm_sitter
    val RESCUE = R.drawable.ic_cm_rescue
    val AGE = R.drawable.ic_cm_age
    val COLOUR = R.drawable.ic_cm_colour
    val BREED = R.drawable.ic_cm_breed
    val SEX = R.drawable.ic_cm_sex
    val SIZE = R.drawable.ic_cm_size
    val MICROCHIP = R.drawable.ic_cm_microchip
    val UPDATE = R.drawable.ic_cm_update
    val LOCK = R.drawable.ic_cm_lock
    val BELL = R.drawable.ic_cm_bell
    val PIN = R.drawable.ic_cm_pin
    val CAMERA = R.drawable.ic_cm_camera
    val CALENDAR = R.drawable.ic_cm_calendar
    val SOUQ = R.drawable.ic_cm_souq
    val ABRA = R.drawable.ic_cm_abra
    val BOAT = R.drawable.ic_cm_boat
    val CUP = R.drawable.ic_cm_cup
    val HANDS = R.drawable.ic_cm_hands
    val MOON = R.drawable.ic_cm_moon
    val NIGHT = R.drawable.ic_cm_night
    val TREE = R.drawable.ic_cm_tree
    val WATER = R.drawable.ic_cm_water
    val SCISSORS = R.drawable.ic_cm_scissors
    val PEARL = R.drawable.ic_cm_pearl
    val FALCON = R.drawable.ic_cm_falcon
    val CAMEL = R.drawable.ic_cm_camel
    val VERIFIED = R.drawable.ic_cm_verified
    val CHART = R.drawable.ic_cm_chart
    val BADGE_PAW = R.drawable.ic_cm_badge_paw
    val BADGE_SOUQ = R.drawable.ic_cm_badge_souq
    val BADGE_ABRA = R.drawable.ic_cm_badge_abra
    val BADGE_BOAT = R.drawable.ic_cm_badge_boat
    val BADGE_CUP = R.drawable.ic_cm_badge_cup
    val BADGE_HANDS = R.drawable.ic_cm_badge_hands
    val BADGE_MOON = R.drawable.ic_cm_badge_moon
    val BADGE_NIGHT = R.drawable.ic_cm_badge_night
    val BADGE_TREE = R.drawable.ic_cm_badge_tree
    val BADGE_WATER = R.drawable.ic_cm_badge_water
    val BADGE_SCISSORS = R.drawable.ic_cm_badge_scissors
    val BADGE_PEARL = R.drawable.ic_cm_badge_pearl
    val BADGE_CAMERA = R.drawable.ic_cm_badge_camera
    val BADGE_LOST = R.drawable.ic_cm_badge_lost
    val BADGE_ADOPTION = R.drawable.ic_cm_badge_adoption
    val BADGE_FALCON = R.drawable.ic_cm_badge_falcon
    val BADGE_STREET = R.drawable.ic_cm_badge_street
    val BADGE_CAMEL = R.drawable.ic_cm_badge_camel
    val all = listOf("cat" to CAT,"dog" to DOG,"bird" to BIRD,"rabbit" to RABBIT,"paw" to PAW,"lost" to LOST,"found" to FOUND,"spotted" to SPOTTED,"help" to HELP,"street" to STREET,"adoption" to ADOPTION,"note" to NOTE,"station" to STATION,"sitter" to SITTER,"rescue" to RESCUE,"age" to AGE,"colour" to COLOUR,"breed" to BREED,"sex" to SEX,"size" to SIZE,"microchip" to MICROCHIP,"update" to UPDATE,"lock" to LOCK,"bell" to BELL,"pin" to PIN,"camera" to CAMERA,"calendar" to CALENDAR,"souq" to SOUQ,"abra" to ABRA,"boat" to BOAT,"cup" to CUP,"hands" to HANDS,"moon" to MOON,"night" to NIGHT,"tree" to TREE,"water" to WATER,"scissors" to SCISSORS,"pearl" to PEARL,"falcon" to FALCON,"camel" to CAMEL,"verified" to VERIFIED,"chart" to CHART,"badge_paw" to BADGE_PAW,"badge_souq" to BADGE_SOUQ,"badge_abra" to BADGE_ABRA,"badge_boat" to BADGE_BOAT,"badge_cup" to BADGE_CUP,"badge_hands" to BADGE_HANDS,"badge_moon" to BADGE_MOON,"badge_night" to BADGE_NIGHT,"badge_tree" to BADGE_TREE,"badge_water" to BADGE_WATER,"badge_scissors" to BADGE_SCISSORS,"badge_pearl" to BADGE_PEARL,"badge_camera" to BADGE_CAMERA,"badge_lost" to BADGE_LOST,"badge_adoption" to BADGE_ADOPTION,"badge_falcon" to BADGE_FALCON,"badge_street" to BADGE_STREET,"badge_camel" to BADGE_CAMEL)
    fun animal(type: AnimalType) = when (type) {
        AnimalType.CAT -> CAT; AnimalType.DOG -> DOG; AnimalType.BIRD -> BIRD
        AnimalType.RABBIT -> RABBIT; AnimalType.OTHER -> PAW
    }
    fun category(category: PostCategory) = when(category) {
        PostCategory.LOST -> LOST; PostCategory.FOUND -> FOUND; PostCategory.SPOTTED -> SPOTTED
        PostCategory.NEEDS_HELP -> HELP; PostCategory.STREET -> STREET; PostCategory.ADOPTION -> ADOPTION; PostCategory.OTHER -> NOTE
    }
    fun layer(layer: MapLayer) = when(layer) {
        MapLayer.LOST -> LOST; MapLayer.FOUND -> FOUND; MapLayer.SPOTTED -> SPOTTED
        MapLayer.NEEDS_HELP -> HELP; MapLayer.STREET_CATS -> CAT; MapLayer.ADOPTION -> ADOPTION; MapLayer.SITTING -> SITTER
    }
    fun badge(badge: Badge) = badge.icon
}

@Composable
fun CatMapIcon(@DrawableRes icon: Int, modifier: Modifier = Modifier, contentDescription: String? = null, tint: Color = LocalContentColor.current) {
    val artwork = icon in listOf(CatMapIcons.CAT, CatMapIcons.DOG, CatMapIcons.BIRD, CatMapIcons.RABBIT, CatMapIcons.PAW) || CatMapIcons.all.any { it.first.startsWith("badge_") && it.second == icon }
    Icon(painterResource(icon), contentDescription, modifier.size(24.dp), tint = if (artwork) Color.Unspecified else tint)
}

@Composable
fun animalNames(types: List<String>): String {
    val resources = LocalResources.current
    return types.joinToString(", ") { key -> resources.getString(AnimalType.entries.firstOrNull { it.key == key }?.label ?: R.string.animal_other) }
}

@Composable
fun catMapVector(icon:Int):ImageVector = ImageVector.vectorResource(icon)

@Composable
fun IconLabel(@DrawableRes icon: Int, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        CatMapIcon(icon)
        Text(text)
    }
}
