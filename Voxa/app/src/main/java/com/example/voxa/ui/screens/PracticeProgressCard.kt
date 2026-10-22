package com.example.voxa.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.example.voxa.R
import com.example.voxa.data.QuestPrefs
import com.example.voxa.data.license.ProgressReport
import com.example.voxa.ui.IVoxaViewModel
import com.example.voxa.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun PracticeProgressCard(viewModel: IVoxaViewModel) {
    val license by viewModel.license.collectAsState()
    val profile by viewModel.activeProfile.collectAsState()
    val stats by viewModel.practiceStats.collectAsState()
    if (profile == null || !license.canExportProgress) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportPdf by remember { mutableStateOf<Boolean?>(null) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    Spacer(Modifier.height(16.dp))
    Card(colors = CardDefaults.cardColors(containerColor = Slate800), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.license_progress_title), color = Sky400)
            Text(stringResource(R.string.license_progress_count, stats.size), color = Slate400)
            Text(stringResource(R.string.license_progress_note), color = Slate400, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { exportPdf = true; pin = ""; error = null }) { Text("PDF") }
                OutlinedButton(onClick = { exportPdf = false; pin = ""; error = null }) { Text("JSON") }
            }
        }
    }
    if (exportPdf != null) AlertDialog(onDismissRequest = { if (!busy) exportPdf = null },
        title = { Text(stringResource(R.string.license_progress_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.license_parent_pin))
                OutlinedTextField(value = pin, onValueChange = { pin = it.filter(Char::isDigit).take(4) },
                    visualTransformation = PasswordVisualTransformation(), singleLine = true)
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        },
        confirmButton = { TextButton(enabled = !busy && pin.length == 4, onClick = {
            if (!QuestPrefs.validatePin(context, pin)) error = context.getString(R.string.license_pin_error)
            else {
                val selectedProfile = profile!!.id; val pdf = exportPdf == true; busy = true
                scope.launch {
                    try { ProgressReport.share(context, ProgressReport.build(context, selectedProfile), pdf); exportPdf = null }
                    catch (e: Exception) { error = e.message }
                    finally { busy = false }
                }
            }
        }) { Text(stringResource(R.string.license_progress_share)) } },
        dismissButton = { TextButton(enabled = !busy, onClick = { exportPdf = null }) { Text(stringResource(android.R.string.cancel)) } }
    )
}
