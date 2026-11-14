package com.neilturner.musicfun.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Shapes

/**
 * Material 3 Expressive shape scale: larger, softer corners than the TV defaults
 * (4/8/12/16/28dp). The increased radii give surfaces and cards the bold, rounded look of
 * expressive, while buttons remain pill-shaped.
 */
val ExpressiveShapes = Shapes(
	extraSmall = RoundedCornerShape(8.dp),
	small = RoundedCornerShape(12.dp),
	medium = RoundedCornerShape(16.dp),
	large = RoundedCornerShape(20.dp),
	extraLarge = RoundedCornerShape(28.dp),
)