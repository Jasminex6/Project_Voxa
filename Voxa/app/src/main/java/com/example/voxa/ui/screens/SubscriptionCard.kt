package com.example.voxa.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.voxa.BuildConfig
import com.example.voxa.R
import com.example.voxa.data.QuestPrefs
import com.example.voxa.data.license.*
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.theme.*
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SubscriptionCard(viewModel: IVoxaViewModel) {
    val license by viewModel.license.collectAsState()
    val context = LocalContext.current
    var purchaseCode by remember { mutableStateOf("") }
    Card(colors = CardDefaults.cardColors(containerColor = Slate800), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.license_title), color = Sky400, style = MaterialTheme.typography.titleMedium)
            Text(licensePlanName(license), color = androidx.compose.ui.graphics.Color.White,
                style = MaterialTheme.typography.titleLarge)
            Text(licenseStatusText(license), color = Sky400)
            if (license.expiresAt > 0) Text(
                stringResource(R.string.license_valid_until, DateFormat.getDateInstance().format(Date(license.expiresAt))), color = Slate400)
            Text(stringResource(R.string.license_purchase_hint), color = Slate400)
            OutlinedTextField(
                value = purchaseCode,
                onValueChange = { purchaseCode = it.take(8192) },
                label = { Text(stringResource(R.string.license_purchase_code)) },
                modifier = Modifier.fillMaxWidth(), maxLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = androidx.compose.ui.graphics.Color.White,
                    unfocusedTextColor = androidx.compose.ui.graphics.Color.White,
                    focusedLabelColor = Sky400, unfocusedLabelColor = Slate400
                )
            )
            Button(enabled = purchaseCode.isNotBlank(), onClick = { viewModel.requestActivation(purchaseCode.trim()) }) {
                Text(stringResource(R.string.license_activate))
            }
            OutlinedButton(onClick = {
                GmsBarcodeScanning.getClient(context).startScan()
                    .addOnSuccessListener { barcode -> barcode.rawValue?.let { viewModel.requestActivation(it) } }
                    .addOnFailureListener { android.widget.Toast.makeText(context, R.string.license_scan_error, android.widget.Toast.LENGTH_LONG).show() }
            }) { Text(stringResource(R.string.license_scan)) }
            Text(stringResource(R.string.license_scan_note), color = Slate400, style = MaterialTheme.typography.bodySmall)
            if (BuildConfig.WEB_UPGRADE_ENABLED) TextButton(onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("${BuildConfig.VOXA_WEB_URL}/en/pricing"))) }
                    .onFailure { android.widget.Toast.makeText(context, R.string.license_browser_error, android.widget.Toast.LENGTH_SHORT).show() }
            }) { Text(stringResource(R.string.license_upgrade)) }
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
