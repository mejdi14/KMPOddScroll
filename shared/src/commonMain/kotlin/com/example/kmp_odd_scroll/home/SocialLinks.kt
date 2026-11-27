package com.example.kmp_odd_scroll.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import kmpoddscroll.shared.generated.resources.Res
import kmpoddscroll.shared.generated.resources.social_github
import kmpoddscroll.shared.generated.resources.social_linkedin
import kmpoddscroll.shared.generated.resources.social_x
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource

@Composable
internal fun SocialLinks(ink: Color, enabled: Boolean) {
    Row {
        ProfileLink(Res.drawable.social_x, "X", "https://x.com/mejdi141", ink, enabled)
        ProfileLink(
            Res.drawable.social_linkedin,
            "LinkedIn",
            "https://www.linkedin.com/in/mejdi-hafiane-5b1646142/",
            ink,
            enabled,
        )
        ProfileLink(Res.drawable.social_github, "GitHub", "https://github.com/mejdi14", ink, enabled)
    }
}

@Composable
private fun ProfileLink(
    icon: DrawableResource,
    label: String,
    url: String,
    ink: Color,
    enabled: Boolean,
) {
    val uriHandler = LocalUriHandler.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, role = Role.Button) { uriHandler.openUri(url) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = ink,
            modifier = Modifier.size(18.dp),
        )
    }
}
