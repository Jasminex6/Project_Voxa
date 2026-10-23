package com.example.voxa.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.voxa.BuildConfig
import com.example.voxa.R
import com.example.voxa.data.QuestPrefs
import com.example.voxa.data.license.*
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.theme.*
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SubscriptionCard(viewModel: IVoxaViewModel) {
    val license by viewModel.license.collectAsState()
    val context = LocalContext.current
    var purchaseCode by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    val paid = license.effectiveTier != LicenseTier.FREE
    val accent = if (paid) QuestTeal else Sky400
    val statusColor = when (license.status) {
        LicenseStatus.ACTIVE, LicenseStatus.FREE -> accent
        LicenseStatus.GRACE -> WarningAmber
        else -> ErrorRed
    }
    Card(
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.3f)),
        colors = CardDefaults.cardColors(containerColor = Slate800), modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            Modifier.background(Brush.linearGradient(listOf(accent.copy(alpha = 0.10f), Slate800)))
                .padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(shape = RoundedCornerShape(12.dp), color = accent.copy(alpha = 0.15f)) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = accent,
                        modifier = Modifier.padding(10.dp).size(22.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.license_title), color = Slate300, style = MaterialTheme.typography.labelMedium)
                    Text(if (paid) licensePlanName(license) else stringResource(R.string.license_free_name),
                        color = Color.White, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                Surface(shape = RoundedCornerShape(50), color = statusColor.copy(alpha = 0.13f)) {
                    Text(stringResource(when (license.status) {
                        LicenseStatus.ACTIVE, LicenseStatus.FREE -> R.string.license_active
                        LicenseStatus.GRACE -> R.string.license_grace_badge
                        LicenseStatus.EXPIRED -> R.string.license_expired_badge
                        LicenseStatus.CLOCK_INVALID -> R.string.license_clock_badge
                    }), color = statusColor, style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
            Text(
                if (license.expiresAt > 0) stringResource(R.string.license_valid_until,
                    DateFormat.getDateInstance().format(Date(license.expiresAt)))
                else stringResource(R.string.license_free_details),
                color = Slate300, style = MaterialTheme.typography.bodySmall
            )
            if (license.status !in listOf(LicenseStatus.ACTIVE, LicenseStatus.FREE)) {
                Text(licenseStatusText(license), color = statusColor, style = MaterialTheme.typography.bodySmall)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween) {
                FilledTonalButton(onClick = { expanded = !expanded },
                    colors = ButtonDefaults.filledTonalButtonColors(containerColor = accent.copy(alpha = 0.14f), contentColor = accent),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Text(stringResource(R.string.license_enter_code), style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(4.dp))
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(if (expanded) R.string.license_collapse else R.string.license_expand),
                        modifier = Modifier.size(18.dp))
                }
                if (BuildConfig.WEB_UPGRADE_ENABLED) TextButton(onClick = {
                    val lang = if (context.resources.configuration.locales[0].language == "ar") "ar" else "en"
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.VOXA_WEB_URL}/$lang/pricing"))) }
                        .onFailure { android.widget.Toast.makeText(context, R.string.license_browser_error, android.widget.Toast.LENGTH_SHORT).show() }
                }) { Text(stringResource(R.string.license_compare), color = Slate300, style = MaterialTheme.typography.labelMedium) }
            }
            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    HorizontalDivider(color = accent.copy(alpha = 0.15f))
                    Text(stringResource(R.string.license_purchase_hint), color = Slate400, style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(
                        value = purchaseCode,
                        onValueChange = { purchaseCode = it.take(8192) },
                        label = { Text(stringResource(R.string.license_purchase_code)) },
                        modifier = Modifier.fillMaxWidth(), maxLines = 3,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White, unfocusedTextColor = Color.White,
                            focusedLabelColor = Sky400, unfocusedLabelColor = Slate400
                        )
                    )
                    Button(modifier = Modifier.fillMaxWidth(), enabled = purchaseCode.isNotBlank(),
                        onClick = { viewModel.requestActivation(purchaseCode.trim()) }) {
                        Text(stringResource(R.string.license_activate))
                    }
                    OutlinedButton(modifier = Modifier.fillMaxWidth(), onClick = {
                        val options = GmsBarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).enableAutoZoom().build()
                        GmsBarcodeScanning.getClient(context, options).startScan()
                            .addOnSuccessListener { barcode ->
                                val value = barcode.rawValue
                                if (value.isNullOrBlank()) android.widget.Toast.makeText(context, R.string.license_scan_error, android.widget.Toast.LENGTH_LONG).show()
                                else runCatching { ActivationClient.parse(value) }
                                    .onSuccess { viewModel.requestActivation(value) }
                                    .onFailure { android.widget.Toast.makeText(context, R.string.license_scan_invalid, android.widget.Toast.LENGTH_LONG).show() }
                            }
                            .addOnFailureListener { android.widget.Toast.makeText(context, R.string.license_scan_error, android.widget.Toast.LENGTH_LONG).show() }
                    }) { Text(stringResource(R.string.license_scan)) }
                }
            }
        }
    }
}

@Composable
fun licensePlanName(license: ActiveLicense): String = when (license.effectiveTier) {
    LicenseTier.FREE -> stringResource(R.string.license_free)
    LicenseTier.FAMILY_PRO -> "Family Pro"
    LicenseTier.CLINICAL_SLP -> "Clinical SLP"
}

@Composable
fun licenseStatusText(license: ActiveLicense): String = stringResource(when (license.status) {
    LicenseStatus.ACTIVE -> R.string.license_active
    LicenseStatus.GRACE -> R.string.license_grace
    LicenseStatus.EXPIRED -> R.string.license_expired
    LicenseStatus.CLOCK_INVALID -> R.string.license_clock
    LicenseStatus.FREE -> R.string.license_free_status
})

@Composable
fun ActivationPrompt(viewModel: IVoxaViewModel) {
    val pending by viewModel.pendingActivation.collectAsState()
    if (pending == null) return
    key(pending) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        var input by remember { mutableStateOf(pending.orEmpty()) }
        var pin by remember { mutableStateOf("") }
        var confirmPin by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        val hasPin = remember { QuestPrefs.isPinSetup(context) }
        val manager = remember { LicenseManager.get(context) }
        AlertDialog(
            onDismissRequest = { if (!busy) viewModel.requestActivation(null) },
            title = { Text(stringResource(R.string.license_activate)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.license_activation_note))
                    OutlinedTextField(value = input, onValueChange = { input = it.take(8192) },
                        label = { Text(stringResource(R.string.license_code)) }, maxLines = 3)
                    Text(stringResource(if (hasPin) R.string.license_parent_pin else R.string.license_setup_pin))
                    OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                        visualTransformation = PasswordVisualTransformation(), singleLine = true,
                        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword))
                    if (!hasPin) OutlinedTextField(value = confirmPin, onValueChange = { confirmPin = it.filter(Char::isDigit).take(4) },
                        label = { Text(stringResource(R.string.license_confirm_pin)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy && input.isNotBlank() && pin.length == 4, onClick = {
                    if ((hasPin && !QuestPrefs.validatePin(context, pin)) || (!hasPin && pin != confirmPin)) {
                        error = context.getString(R.string.license_pin_error)
                    } else {
                        busy = true; error = null
                        scope.launch {
                            try {
                                ActivationClient.redeem(input, manager)
                                if (!hasPin) QuestPrefs.setCaregiverPin(context, pin)
                                android.widget.Toast.makeText(context, R.string.license_success, android.widget.Toast.LENGTH_LONG).show()
                                viewModel.requestActivation(null)
                            } catch (e: Exception) { error = e.message ?: context.getString(R.string.license_activation_error) }
                            finally { busy = false }
                        }
                    }
                }) { Text(stringResource(R.string.license_confirm)) }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { viewModel.requestActivation(null) }) { Text(stringResource(android.R.string.cancel)) } }
        )
    }
}
