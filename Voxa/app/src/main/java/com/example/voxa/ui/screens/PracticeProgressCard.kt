package com.example.voxa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.voxa.R
import com.example.voxa.data.QuestPrefs
import com.example.voxa.data.license.*
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PracticeProgressCard(viewModel: IVoxaViewModel) {
    val license by viewModel.license.collectAsState()
    val profile by viewModel.activeProfile.collectAsState()
    if (profile == null || !license.canExportProgress) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportPdf by remember(profile?.id) { mutableStateOf<Boolean?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var viewing by remember(profile?.id) { mutableStateOf(false) }
    var days by remember { mutableIntStateOf(7) }
    var review by remember(profile?.id) { mutableStateOf<ProgressReview?>(null) }
    val colors = LocalVoxaColors.current
    Spacer(Modifier.height(16.dp))
    Card(colors = CardDefaults.cardColors(containerColor = colors.normalCard), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.license_progress_title), color = colors.primaryText, style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.report_intro), color = colors.secondaryText, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { error = null; viewing = true }) { Text(stringResource(R.string.report_review)) }
                OutlinedButton(onClick = { exportPdf = true; pin = ""; error = null }) { Text("PDF") }
                TextButton(onClick = { exportPdf = false; pin = ""; error = null }) { Text("JSON") }
            }
        }
    }
    if (viewing) {
        LaunchedEffect(profile?.id, days) {
            review = null; error = null
            try { review = ProgressReport.review(context, profile!!.id, days) }
            catch (e: Exception) { error = e.message }
        }
        Dialog(onDismissRequest = { viewing = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(Modifier.fillMaxSize(), color = colors.background) {
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(18.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.license_progress_title), style = MaterialTheme.typography.titleLarge)
                        TextButton(onClick = { viewing = false }) { Text(stringResource(android.R.string.cancel)) }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(days == 7, onClick = { days = 7 }, label = { Text(stringResource(R.string.report_week)) })
                        FilterChip(days == 30, onClick = { days = 30 }, label = { Text(stringResource(R.string.report_month)) })
                        TextButton(onClick = { exportPdf = true; pin = ""; error = null }) { Text("PDF") }
                    }
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                    val value = review
                    if (value == null) LinearProgressIndicator(Modifier.fillMaxWidth())
                    else Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { ProgressReviewContent(value) }
                }
            }
        }
    }
    if (exportPdf != null) AlertDialog(onDismissRequest = { if (!busy) exportPdf = null },
        title = { Text(stringResource(R.string.license_progress_title)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.license_parent_pin))
            OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                visualTransformation = PasswordVisualTransformation(), singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword))
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
        } },
        confirmButton = { TextButton(enabled = !busy && pin.length == 4, onClick = {
            if (!QuestPrefs.validatePin(context, pin)) error = context.getString(R.string.license_pin_error)
            else {
                val selectedProfile = profile!!.id; val pdf = exportPdf == true; busy = true
                scope.launch {
                    try {
                        val snapshot = review?.takeIf { it.period.days == days }
                            ?: ProgressReport.review(context, selectedProfile, days).also { review = it }
                        ProgressReport.share(context, org.json.JSONObject(ProgressReport.gson.toJson(snapshot)), pdf)
                        exportPdf = null
                    } catch (e: Exception) { error = e.message }
                    finally { busy = false }
                }
            }
        }) { Text(stringResource(R.string.license_progress_share)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { exportPdf = null }) { Text(stringResource(android.R.string.cancel)) } }
    )
}
