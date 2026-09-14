package io.starfleet.lcars.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.starfleet.lcars.app.PantryViewModel
import io.starfleet.lcars.app.data.BAYS
import io.starfleet.lcars.app.data.PantryItem
import io.starfleet.lcars.app.data.bayLabel
import io.starfleet.lcars.app.data.catalogLabel
import io.starfleet.lcars.app.scan.CameraPane
import io.starfleet.lcars.app.speech.ScanVoice

private val Gold = Color(0xFFFF9900)
private val Ice = Color(0xFF99CCFF)
private val Amber = Color(0xFFFFCC00)
private val Lilac = Color(0xFFCC99CC)
private val Salmon = Color(0xFFFF6666)
private val Panel = Color(0xFF0A0A0A)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScannerScreen(vm: PantryViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val voice = remember { ScanVoice(context) }
    DisposableEffect(Unit) { onDispose { voice.shutdown() } }

    LaunchedEffect(state.lastItem?.id, state.lastItem?.qty) {
        val item = state.lastItem ?: return@LaunchedEffect
        voice.scanningComplete()
        vibrate(context)
    }

    val permission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> vm.setCameraOn(granted) }

    LaunchedEffect(Unit) {
        val ok = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (ok) vm.setCameraOn(true) else permission.launch(Manifest.permission.CAMERA)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "PROVISIONS",
                color = Amber,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Spacer(Modifier.weight(1f))
            Text(
                if (state.online) "BAY ONLINE" else "BAY OFFLINE",
                color = Ice,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Text(
            state.status,
            color = Ice,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 2,
        )

        Text("STAÐSETNING", color = Gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            BAYS.forEach { bay ->
                LcarsChip(
                    label = bay.label,
                    selected = state.location == bay.id,
                    color = Gold,
                    onClick = { vm.pickLocation(bay.id) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            LcarsChip("INTAKE", state.mode == "in", Amber) { vm.setMode("in") }
            LcarsChip("USE", state.mode == "use", Salmon) { vm.setMode("use") }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(Panel)
                .border(1.dp, if (state.holding) Amber else Gold.copy(alpha = 0.5f)),
        ) {
            if (state.cameraOn && state.location.isNotBlank()) {
                CameraPane(
                    enabled = true,
                    zoom = state.zoom,
                    onBarcode = vm::onBarcode,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    if (state.location.isBlank()) "VELDU STAÐ ÁÐUR EN ÞÚ SKANNAR" else "CAMERA OFF / PERMISSION",
                    color = Gold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.align(Alignment.Center).padding(12.dp),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.72f)
                    .height(88.dp)
                    .border(1.5.dp, Gold.copy(alpha = 0.85f), RoundedCornerShape(2.dp)),
            )
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf(1f, 2f, 3f).forEach { z ->
                    val selected = state.zoom == z
                    Text(
                        "${z.toInt()}×",
                        color = if (selected) Color.Black else Gold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .background(if (selected) Gold else Color.Black.copy(alpha = 0.55f), RoundedCornerShape(2.dp))
                            .clickable { vm.setZoom(z) }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (state.holding) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("HOLD ${state.holdLeftSec}s", color = Amber, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("MOVE THE PACK", color = Ice, fontSize = 14.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        }

        TextField(
            value = state.manualName,
            onValueChange = vm::setManualName,
            placeholder = { Text("No barcode — type name", color = Color.Gray) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { vm.commitManual() }),
            colors = TextFieldDefaults.colors(
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                focusedContainerColor = Panel,
                unfocusedContainerColor = Panel,
                cursorColor = Gold,
                focusedIndicatorColor = Lilac,
                unfocusedIndicatorColor = Color.DarkGray,
            ),
            modifier = Modifier.fillMaxWidth(),
        )

        state.lastItem?.let { item ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Amber)
                    .background(Amber.copy(alpha = 0.12f))
                    .padding(8.dp),
            ) {
                Text(
                    "JUST LOGGED · ${catalogLabel(item.source).ifBlank { "SCAN" }}",
                    color = Amber,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(item.name, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${item.qty} ${item.unit} · ${bayLabel(item.location)}" +
                        (item.barcode?.let { " · $it" } ?: " · NO CODE"),
                    color = Color.Gray,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }

        Text("INVENTORY", color = Ice, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(state.items, key = { it.id }) { item ->
                InventoryRow(item, highlighted = item.id == state.lastItem?.id, onBump = vm::bump)
            }
        }
    }
}

@Composable
private fun InventoryRow(
    item: PantryItem,
    highlighted: Boolean,
    onBump: (PantryItem, Double) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Panel)
            .border(1.dp, if (highlighted) Amber else Color(0xFF222222))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(item.name, color = Color.White, fontSize = 15.sp, maxLines = 1)
            Text(
                buildString {
                    append("${item.qty} ${item.unit}")
                    append(" · ${item.locationLabel ?: bayLabel(item.location)}")
                    item.barcode?.let { append(" · $it") }
                    catalogLabel(item.source).takeIf { it.isNotBlank() }?.let { append(" · $it") }
                },
                color = Color.Gray,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
            )
        }
        Text("−", color = Ice, fontSize = 22.sp, modifier = Modifier
            .size(32.dp)
            .clickable { onBump(item, -1.0) })
        Text("+", color = Amber, fontSize = 22.sp, modifier = Modifier
            .size(32.dp)
            .clickable { onBump(item, 1.0) })
    }
}

@Composable
private fun LcarsChip(label: String, selected: Boolean, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(if (selected) color else Color(0xFF111111), RoundedCornerShape(2.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) Color.Black else color,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

private fun vibrate(context: android.content.Context) {
    val vibrator = if (Build.VERSION.SDK_INT >= 31) {
        context.getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Vibrator::class.java)
    }
    vibrator.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE))
}
