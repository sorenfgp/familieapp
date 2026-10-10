package dk.familieapp.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

// ---------- Kalender (kommer i næste version) ----------

@Composable
fun CalendarScreen() {
    Column(Modifier.fillMaxSize()) {
        ScreenHeader("Kommer i næste version", "Kalender")
        Surface(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            shape = RoundedCornerShape(22.dp),
            color = Color.White,
        ) {
            Text(
                "Her kommer familiens aftaler fra Google Kalender, sammen med aftensmaden fra madplanen.",
                Modifier.padding(20.dp),
                fontSize = 15.sp,
                lineHeight = 21.sp,
                color = Muted,
            )
        }
    }
}

// ---------- Mere ----------

@Composable
fun MoreScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val code = vm.session.householdId
    var confirmLeave by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader("Familie og indstillinger", "Mere")
        Column(
            Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SectionLabel("Familien")
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!vm.session.firebaseEnabled) {
                        Text("Kun på denne telefon", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Appen er bygget uden Firebase, så madplan og lister deles ikke med andre telefoner endnu. " +
                                "README i projektet beskriver, hvordan deling slås til.",
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = Muted,
                        )
                    } else if (code != null) {
                        Text("Familiekode", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                        Text(code, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Resten af familien skriver koden i appen for at dele madplan og lister.",
                            fontSize = 15.sp,
                            lineHeight = 21.sp,
                            color = Muted,
                        )
                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "Vores familiekode til appen Familien: $code")
                            context.startActivity(Intent.createChooser(intent, "Del familiekode"))
                        }) {
                            Text("Del koden", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { confirmLeave = true }) {
                            Text("Skift familie")
                        }
                    }
                }
            }

            SectionLabel("På vej")
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Google Kalender", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Text("Næste version.", fontSize = 14.sp, color = Muted)
                }
            }
        }
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Skift familie?") },
            text = { Text("Telefonen forlader familien. Data bliver liggende, og du kan komme ind igen med koden.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.leaveHousehold()
                }) { Text("Skift") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Annullér") }
            },
        )
    }
}

// ---------- Første start: opret eller deltag i en familie ----------

@Composable
fun HouseholdSetupScreen(vm: AppViewModel) {
    var mode by rememberSaveable { mutableIntStateOf(0) }
    var name by rememberSaveable { mutableStateOf("") }
    var code by rememberSaveable { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun attempt(action: suspend () -> Unit) {
        busy = true
        error = null
        scope.launch {
            try {
                action()
            } catch (e: IllegalArgumentException) {
                error = e.message
            } catch (e: Exception) {
                error = if (mode == 0) {
                    "Familien kunne ikke oprettes. Tjek internetforbindelsen."
                } else {
                    "Koden blev ikke fundet. Tjek koden og internetforbindelsen."
                }
            } finally {
                busy = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ground)
            .systemBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Velkommen", fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "Opret jeres familie på den første telefon. De andre deltager med familiekoden.",
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = Muted,
        )
        Segmented(listOf("Opret familie", "Deltag med kode"), mode, { mode = it })

        if (mode == 0) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Familiens navn") },
                singleLine = true,
            )
            YellowButton(
                text = if (busy) "Opretter …" else "Opret familie",
                onClick = { attempt { vm.createHousehold(name.trim()) } },
                enabled = name.isNotBlank() && !busy,
            )
        } else {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.uppercase().take(8) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Familiekode (8 tegn)") },
                singleLine = true,
            )
            YellowButton(
                text = if (busy) "Finder familien …" else "Deltag",
                onClick = { attempt { vm.joinHousehold(code) } },
                enabled = code.length == 8 && !busy,
            )
        }

        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 15.sp) }
    }
}
