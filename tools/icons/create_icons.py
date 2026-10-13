"""Original CatMap vector artwork. Regenerate drawables and the registry from this source."""
from pathlib import Path
import json, re

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'app/src/main/res/drawable'
# Each path was drawn on a 24-unit grid; no downloaded artwork or fonts.
ART = {
 'cat': 'M4,9 L3,3 L9,6 Q12,5 15,6 L21,3 L20,9 Q23,20 12,21 Q1,20 4,9 M8,11 L8,12 M16,11 L16,12 M10,15 L12,17 L14,15 M12,17 L12,19',
 'dog': 'M6,7 Q4,2 2,7 L2,14 Q5,16 7,11 M18,7 Q20,2 22,7 L22,14 Q19,16 17,11 M7,6 Q12,3 17,6 L18,15 Q18,21 12,21 Q6,21 6,15 Z M9,11 L9,12 M15,11 L15,12 M10,16 L12,18 L14,16',
 'bird': 'M5,15 Q3,9 8,7 Q10,2 16,5 Q19,6 19,10 L22,12 L18,13 Q16,20 8,19 L3,21 L5,15 Z M7,11 Q9,17 14,13 M16,8 L16,8.2 M10,19 L10,22 M14,18 L14,22',
 'rabbit': 'M8,10 Q3,1 7,1 Q10,2 11,9 M13,9 Q14,1 18,1 Q22,2 16,10 M6,11 Q3,21 12,22 Q21,21 18,11 Q12,7 6,11 Z M9,14 L9,15 M15,14 L15,15 M10,18 L12,19 L14,18',
 'paw': 'M7,12 Q12,8 17,12 L20,18 Q21,22 16,21 L12,19 L8,21 Q3,22 4,18 Z M3,8 Q1,4 4,4 Q7,4 6,8 Q5,11 3,8 Z M9,7 Q6,2 9,1 Q12,1 12,5 Q12,8 9,7 Z M15,7 Q12,2 15,1 Q18,1 18,5 Q18,8 15,7 Z M21,8 Q18,4 21,4 Q24,4 23,8 Q22,11 21,8 Z',
 'lost': 'M10,3 A7,7 0,1 0,10,17 A7,7 0,1 0,10,3 M15,15 L22,22 M10,7 L10,11 M10,14 L10,14.2',
 'found': 'M12,22 Q4,14 4,9.5 Q4,2 12,2 Q20,2 20,9.5 Q20,14 12,22 Z M8.5,9.5 L11,12 L15.5,7.5',
 'spotted': 'M2,12 Q12,1 22,12 Q12,23 2,12 Z M12,8 A4,4 0,1 0,12,16 A4,4 0,1 0,12,8',
 'help': 'M9,2 L15,2 L15,9 L22,9 L22,15 L15,15 L15,22 L9,22 L9,15 L2,15 L2,9 L9,9 Z',
 'street': 'M2,22 L2,10 L9,10 L9,22 M9,22 L9,2 L16,2 L16,22 M16,22 L16,7 L22,7 L22,22 M5,14 L6,14 M5,18 L6,18 M12,6 L13,6 M12,10 L13,10 M12,14 L13,14 M19,11 L20,11 M19,15 L20,15 M1,22 L23,22',
 'adoption': 'M12,21 Q1,13 2,7 Q3,1 9,4 L12,7 L15,4 Q21,1 22,7 Q23,13 12,21 Z',
 'note': 'M5,2 L15,2 L20,7 L20,22 L4,22 L4,2 Z M14,2 L14,8 L20,8 M8,12 L16,12 M8,16 L16,16 M8,19 L13,19',
 'station': 'M2,12 L22,12 Q21,22 12,22 Q3,22 2,12 Z M7,8 Q5,6 7,3 M12,8 Q10,6 12,3 M17,8 Q15,6 17,3',
 'sitter': 'M2,11 L12,2 L22,11 M5,10 L5,22 L19,22 L19,10 M9,22 L9,15 L15,15 L15,22',
 'rescue': 'M2,8 L15,8 L15,19 L2,19 Z M15,12 L19,12 L22,16 L22,19 L15,19 M6,19 A2,2 0,1 0,6,23 A2,2 0,1 0,6,19 M19,19 A2,2 0,1 0,19,23 A2,2 0,1 0,19,19 M7,11 L7,16 M4.5,13.5 L9.5,13.5 M6,5 L10,5',
 'age': 'M4,11 L20,11 L20,22 L4,22 Z M3,16 Q6,19 9,16 Q12,19 15,16 Q18,19 21,16 M8,11 L8,6 M16,11 L16,6 M8,2 Q5,5 8,6 Q11,5 8,2 M16,2 Q13,5 16,6 Q19,5 16,2',
 'colour': 'M12,2 Q22,2 22,12 Q22,16 17,16 L14,16 Q12,16 13,19 Q14,22 10,22 Q2,22 2,12 Q2,2 12,2 Z M7,7 L7,7.2 M12,5 L12,5.2 M17,8 L17,8.2 M6,13 L6,13.2',
 'breed': 'M12,2 A6.5,6.5 0,1 0,12,15 A6.5,6.5 0,1 0,12,2 M12,5.5 A3,3 0,1 0,12,11.5 A3,3 0,1 0,12,5.5 M8.5,14 L7,22 L10,20 L12,22 M15.5,14 L17,22 L14,20 L12,22',
 'sex': 'M7,3 A4,4 0,1 0,7,11 A4,4 0,1 0,7,3 M7,11 L7,21 M3,17 L11,17 M16,10 A4,4 0,1 0,16,18 A4,4 0,1 0,16,10 M19,11 L23,6 M19,6 L23,6 L23,10',
 'size': 'M3,5 L21,5 L21,19 L3,19 Z M7,5 L7,10 M11,5 L11,8 M15,5 L15,10 M19,5 L19,8',
 'microchip': 'M7,7 L17,7 L17,17 L7,17 Z M10,10 L14,10 L14,14 L10,14 Z M9,2 L9,7 M15,2 L15,7 M9,17 L9,22 M15,17 L15,22 M2,9 L7,9 M2,15 L7,15 M17,9 L22,9 M17,15 L22,15',
 'update': 'M8,14 Q7,5 17,2 Q22,12 13,16 Z M8,14 L4,14 L2,19 L9,18 M13,16 L14,21 L19,19 L17,14 M15,6 A2,2 0,1 0,15,10 A2,2 0,1 0,15,6 M5,19 L2,22',
 'lock': 'M6,10 L18,10 L18,22 L6,22 Z M8,10 L8,6 Q8,1 12,1 Q16,1 16,6 L16,10 M12,15 L12,18',
 'bell': 'M5,16 L5,10 Q5,3 12,3 Q19,3 19,10 L19,16 L22,19 L2,19 Z M9,22 L15,22 M12,1 L12,3',
 'pin': 'M12,22 Q2,12 5,6 Q8,0 14,2 Q23,4 19,13 Z M12,6 A3,3 0,1 0,12,12 A3,3 0,1 0,12,6',
 'camera': 'M3,7 L7,7 L9,3 L15,3 L17,7 L21,7 L21,21 L3,21 Z M12,10 A4,4 0,1 0,12,18 A4,4 0,1 0,12,10',
 'calendar': 'M3,5 L21,5 L21,22 L3,22 Z M3,10 L21,10 M7,2 L7,7 M17,2 L17,7 M7,14 L9,14 M14,14 L17,14 M7,18 L9,18',
 'souq': 'M8,3 L16,3 L14,7 Q21,11 18,20 L6,20 Q3,11 10,7 Z M6,13 L18,13 M9,2 L15,2',
 'abra': 'M2,15 L22,15 L18,21 L6,21 Z M12,3 L12,15 M11,3 L4,13 L11,13 M14,5 L20,13 L14,13 M2,23 Q5,21 8,23 Q11,21 14,23 Q17,21 22,23',
 'boat': 'M2,16 L22,16 L19,21 L5,21 Z M6,16 L8,8 L16,8 L19,16 M11,3 L11,8 M8,12 L16,12',
 'cup': 'M3,8 L17,8 L17,15 Q16,20 10,20 Q4,20 3,15 Z M17,9 L21,9 Q24,14 17,16 M2,23 L19,23 M7,5 Q5,3 7,1 M13,5 Q11,3 13,1',
 'hands': 'M12,11 Q7,8 7,5.5 Q7,2.5 9.7,2.5 Q11.2,2.5 12,4 Q12.8,2.5 14.3,2.5 Q17,2.5 17,5.5 Q17,8 12,11 Z M2,14 L5,14 L5,22 L2,22 Z M5,15 L9,15 Q11,15 12,17 L15,17 Q17,17 17,19 L10,19 M17,18 L21,15.5 Q23,15 22.5,17 L18,21.5 Q17,22 15,22 L5,22',
 'moon': 'M16,2 Q4,8 13,19 Q17,22 22,18 Q17,25 7,21 Q-1,17 3,8 Q6,1 16,2 Z',
 'night': 'M2,22 L2,13 L8,13 L8,22 M10,22 L10,8 L16,8 L16,22 M18,22 L18,16 L22,16 L22,22 M3,4 L5,4 M4,3 L4,5 M19,3 L22,3 M20.5,1.5 L20.5,4.5 M13,2 L13,4',
 'tree': 'M12,2 Q4,1 5,7 Q0,10 5,15 Q7,18 12,16 Q17,18 20,14 Q24,9 18,7 Q20,1 12,2 Z M12,11 L12,23 M8,23 L16,23 M12,16 L16,12',
 'water': 'M12,2 Q3,12 3,16 Q3,23 12,23 Q21,23 21,16 Q21,12 12,2 Z M7,16 Q7,20 11,20',
 'scissors': 'M6,12 A4,4 0,1 0,6,20 A4,4 0,1 0,6,12 M18,12 A4,4 0,1 0,18,20 A4,4 0,1 0,18,12 M8,14 L20,2 M16,14 L4,2',
 'pearl': 'M2,13 L22,13 Q19,22 12,22 Q5,22 2,13 Z M7,13 L9,18 M12,13 L12,19 M17,13 L15,18 M12,4 A3.5,3.5 0,1 0,12,11 A3.5,3.5 0,1 0,12,4 M19,3 L19,7 M17,5 L21,5',
 'falcon': 'M9,4 Q13,2 15,5 L16,10 Q17,15 15,18 L13,21 L11,21 L12,18 Q8,16 8,11 L8,8 L6,8 L7,6 Q8,4 9,4 Z M10.5,6 L10.5,6.2 M9.5,7.5 L9.5,10 M10.5,11 Q11.5,15 15,17 M4,21 L20,21',
 'camel': 'M3,13 Q5,7 9,8 Q11,4 14,8 Q16,10 17,10 L18,5 Q19,3 21,4 L23,5.5 L21,7 L20,7 L19,13 Q18,15 16,15 L6,15 Q3,15 3,13 Z M6,15 L6,22 M9,15 L9,22 M14,15 L14,22 M17,14.5 L17,22 M3,13 L2,16',
 'verified': 'M12,2 L21,6 L20,15 Q18,20 12,23 Q6,20 4,15 L3,6 Z M7,12 L11,16 L17,8',
 'chart': 'M3,2 L3,22 L23,22 M7,18 L7,13 L10,13 L10,18 Z M13,18 L13,9 L16,9 L16,18 Z M19,18 L19,4 L22,4 L22,18 Z',
}
BADGES = ['paw','souq','abra','boat','cup','hands','moon','night','tree','water','scissors','pearl','camera','lost','adoption','falcon','street','camel']
ART.update({f'badge_{name}': ART[name] for name in BADGES})
for name, path in ART.items():
    badge = name.startswith('badge_')
    fill = '#FFDBC8' if name in ['cat','dog','rabbit','paw','bird'] else '#00000000'
    outer = '<path android:pathData="M12,1 A11,11 0,1 0,12,23 A11,11 0,1 0,12,1" android:fillColor="#FFDEA3" android:strokeColor="#7A5900" android:strokeWidth="2" />' if badge else ''
    group = '<group android:scaleX="0.65" android:scaleY="0.65" android:translateX="4.2" android:translateY="4.2">' if badge else '<group>'
    xml = f'''<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android" android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    {outer}{group}
    <path android:pathData="{path}" android:fillColor="{fill}" android:strokeColor="#A9531A" android:strokeWidth="2" android:strokeLineCap="round" android:strokeLineJoin="round" />
    </group>
</vector>
'''
    (OUT / f'ic_cm_{name}.xml').write_text(xml, encoding='utf-8')

registry = '''package com.catmap.dubai.ui.icons

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
'''
registry += '\n'.join(f'    val {name.upper()} = R.drawable.ic_cm_{name}' for name in ART)
registry += '\n    val all = listOf(' + ','.join(f'"{name}" to {name.upper()}' for name in ART) + ')\n'
registry += '''    fun animal(type: AnimalType) = when (type) {
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
'''
dest = ROOT / 'app/src/main/java/com/catmap/dubai/ui/icons/CatMapIcons.kt'
dest.parent.mkdir(parents=True, exist_ok=True)
dest.write_text(registry, encoding='utf-8')
print(f'Wrote {len(ART)} original icons and registry')
