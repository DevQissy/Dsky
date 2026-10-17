package com.devqissy.dsky.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.devqissy.dsky.R
import com.devqissy.dsky.sys.IntraVpnService
import com.devqissy.dsky.sys.VpnState
import com.devqissy.dsky.ui.fragment.MoonPhaseView

private val BreeSerifFontFamily = FontFamily(
    Font(R.font.breeserif_regular)
)

data class VpnUiState(
    val vpnState: VpnState? = null,
    val serverName: String = "",
    val systemDnsServer: String = "",
    val isAnotherVpnActive: Boolean = false,
    val privateDnsMode: PrivateDnsMode = PrivateDnsMode.NONE,
    val showChangeServerButton: Boolean = false
)

enum class PrivateDnsMode { NONE, UPGRADED, STRICT }

@Composable
fun MainControlScreen(
    uiState: VpnUiState,
    onMoonClick: () -> Unit,
    onServerClick: () -> Unit,
    onChangeServerClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    val titleFontSize = (screenWidthDp / 6f).sp
    val subtitleFontSize = (screenWidthDp / 14f).sp
    val moonSize = (screenWidthDp / 1.8f).dp

    Column(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .navigationBarsPadding()
            .systemBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.wrapContentHeight()
        ) {
            StatusHeader(
                titleFontSize = titleFontSize,
                subtitleFontSize = subtitleFontSize
            )

            Spacer(modifier = Modifier.height(24.dp))

            MoonButtonSection(
                uiState = uiState,
                onMoonClick = onMoonClick,
                moonSize = moonSize
            )

            Spacer(modifier = Modifier.height(16.dp))

            ExplanationText(uiState)

            Spacer(modifier = Modifier.height(12.dp))

            ServerButton(
                serverName = uiState.serverName,
                onClick = onServerClick
            )

            if (uiState.showChangeServerButton) {
                Spacer(modifier = Modifier.height(8.dp))
                ChangeServerButton(onClick = onChangeServerClick)
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .wrapContentHeight()
                .padding(bottom = 24.dp)
        ) {
            val isOn = uiState.vpnState?.on == true
            if (isOn) {
                SystemDetailsCard(serverName = uiState.serverName)
            } else {
                InsecureSystemDetailsCard(systemDnsServer = uiState.systemDnsServer)
            }
        }
    }
}

@Composable
private fun StatusHeader(
    titleFontSize: androidx.compose.ui.unit.TextUnit,
    subtitleFontSize: androidx.compose.ui.unit.TextUnit
) {
    val indicatorColor = colorResource(R.color.indicator)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.padding(top = 16.dp)
    ) {
        Text(
            text = stringResource(R.string.app_name),
            fontSize = titleFontSize,
            fontWeight = FontWeight.Bold,
            color = indicatorColor,
            fontFamily = BreeSerifFontFamily
        )
        Text(
            text = stringResource(R.string.subtitle_fast_stable_seamless),
            fontSize = subtitleFontSize,
            fontWeight = FontWeight.Bold,
            color = indicatorColor,
            fontFamily = BreeSerifFontFamily
        )
    }
}

@Composable
private fun MoonButtonSection(
    uiState: VpnUiState,
    onMoonClick: () -> Unit,
    moonSize: Dp
) {
    val isOn = uiState.vpnState?.activationRequested == true
    val isFailing = uiState.vpnState?.connectionState == IntraVpnService.State.FAILING

    val targetPhase = when {
        isOn && isFailing -> 0.5f
        isOn              -> 1.0f
        else              -> 0.0f
    }

    val animatedPhase by animateFloatAsState(
        targetValue = targetPhase,
        animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing),
        label = "moonPhase"
    )

    val pulseScale by rememberInfiniteTransition(label = "pulse").animateFloat(
        initialValue = 1f,
        targetValue = if (isOn && !isFailing) 1.04f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val moonColor = when {
        isOn && isFailing -> colorResource(R.color.cardview_dark_background)
        else              -> colorResource(R.color.cardview_light_background)
    }

    MoonPhaseView(
        phase = animatedPhase,
        color = moonColor,
        modifier = Modifier
            .size(moonSize)
            .scale(pulseScale)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onMoonClick
            )
    )
}

@Composable
private fun ExplanationText(uiState: VpnUiState) {
    val (textRes, colorRes) = remember(uiState) {
        resolveExplanation(uiState)
    }
    Text(
        text = stringResource(textRes),
        fontSize = 18.sp,
        color = colorResource(colorRes),
        modifier = Modifier.padding(horizontal = 32.dp)
    )
}

private fun resolveExplanation(uiState: VpnUiState): Pair<Int, Int> {
    val state = uiState.vpnState
    return when {
        state?.activationRequested == true -> {
            val color = if (state.connectionState == IntraVpnService.State.FAILING)
                R.color.accent_bad else R.color.accent_good
            Pair(R.string.status_protected, color)
        }
        uiState.isAnotherVpnActive ->
            Pair(R.string.explanation_vpn, R.color.Gray)
        uiState.privateDnsMode == PrivateDnsMode.STRICT ->
            Pair(R.string.status_strict, R.color.indicator)
        uiState.privateDnsMode == PrivateDnsMode.UPGRADED ->
            Pair(R.string.status_upgraded, R.color.indicator)
        else ->
            Pair(R.string.status_exposed, R.color.Gray)
    }
}

@Composable
private fun ServerButton(serverName: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0x33FFFFFF)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .wrapContentWidth()
            .padding(horizontal = 32.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = serverName,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.width(8.dp))

            Icon(
                painter = painterResource(id = R.drawable.ic_down),
                contentDescription = stringResource(R.string.content_desc_change_server),
                tint = Color.White,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun ChangeServerButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0x33FFFFFF)
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp)
    ) {
        Text(
            text = stringResource(R.string.try_all_servers),
            color = Color.White,
            fontSize = 16.sp
        )
    }
}

@Composable
private fun SystemDetailsCard(serverName: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22FFFFFF))
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.server_label),
            fontSize = 14.sp,
            color = Color(0xFFAAAAAA)
        )
        Text(
            text = serverName,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

@Composable
private fun InsecureSystemDetailsCard(systemDnsServer: String) {
    val displayServer = systemDnsServer.ifEmpty {
        stringResource(R.string.unknown_server)
    }
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x22FFFFFF))
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.default_server_label),
            fontSize = 14.sp,
            color = Color(0xFFAAAAAA)
        )
        Text(
            text = displayServer,
            fontSize = 16.sp,
            color = Color.White
        )
    }
}