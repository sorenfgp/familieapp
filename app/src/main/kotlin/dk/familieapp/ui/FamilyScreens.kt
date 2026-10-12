package dk.familieapp.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import dk.familieapp.data.Profile
import dk.familieapp.data.ROLE_CHILD
import dk.familieapp.data.ROLE_PARENT
import kotlinx.coroutines.launch

private fun List<Profile>.sortedForDisplay(): List<Profile> =
    sortedWith(compareBy({ it.role != ROLE_PARENT }, { it.name.lowercase() }))

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
                keyboardOptions = SentenceCaps,
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

// ---------- Vælg profil på denne enhed ----------

@Composable
fun ProfileGate(vm: AppViewModel, profiles: List<Profile>) {
    if (profiles.isEmpty()) {
        FirstProfileScreen(vm)
        return
    }

    var unlocking by remember { mutableStateOf<Profile?>(null) }
    var askPinForNew by remember { mutableStateOf(false) }
    var addingNew by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Ground)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(24.dp))
        Text("Hvem bruger telefonen?", fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            "Vælg din profil. En forælderprofil kræver forælder-PIN.",
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = Muted,
        )
        profiles.sortedForDisplay().forEach { profile ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.White)
                    .clickable {
                        if (profile.role == ROLE_PARENT && vm.hasPin()) unlocking = profile
                        else vm.selectProfile(profile.id)
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Avatar(profile, 44.dp)
                Column(Modifier.weight(1f)) {
                    Text(profile.name, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(roleLabel(profile.role), fontSize = 14.sp, color = Muted)
                }
            }
        }
        OutlinedButton(
            onClick = { if (vm.hasPin()) askPinForNew = true else addingNew = true },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
        ) {
            Text("Ny profil", fontWeight = FontWeight.Bold)
        }
    }

    unlocking?.let { profile ->
        PinDialog(
            title = "Skift til ${profile.name}",
            check = vm::checkPin,
            onSuccess = {
                unlocking = null
                vm.selectProfile(profile.id)
            },
            onDismiss = { unlocking = null },
        )
    }
    if (askPinForNew) {
        PinDialog(
            title = "Ny profil",
            check = vm::checkPin,
            onSuccess = {
                askPinForNew = false
                addingNew = true
            },
            onDismiss = { askPinForNew = false },
        )
    }
    if (addingNew) {
        ProfileDialog(
            profile = Profile(color = profiles.size % ProfileColors.size),
            canDelete = false,
            onSave = {
                addingNew = false
                vm.saveProfile(it)
            },
            onDelete = {},
            onDismiss = { addingNew = false },
        )
    }
}

@Composable
private fun FirstProfileScreen(vm: AppViewModel) {
    var name by rememberSaveable { mutableStateOf("") }
    var pin by rememberSaveable { mutableStateOf("") }
    var repeat by rememberSaveable { mutableStateOf("") }
    val pinsMatch = pin.length >= 4 && pin == repeat

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
        Text("Opret din profil", fontSize = 30.sp, fontWeight = FontWeight.Bold)
        Text(
            "Du bliver den første forælder i familien. Forælder-PIN bruges, når nogen skifter til en forælderprofil, " +
                "så børnene ikke kan rette indkøbslisten eller bekræfte deres egne pligter.",
            fontSize = 16.sp,
            lineHeight = 22.sp,
            color = Muted,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Dit navn") },
            keyboardOptions = SentenceCaps,
            singleLine = true,
        )
        PinField(pin, { pin = it }, "Forælder-PIN (4–6 cifre)")
        PinField(repeat, { repeat = it }, "Gentag PIN")
        if (repeat.isNotEmpty() && pin != repeat) {
            Text("De to PIN-koder er ikke ens.", color = MaterialTheme.colorScheme.error)
        }
        YellowButton(
            text = "Opret profil",
            onClick = { vm.createFirstParent(name.trim(), pin) },
            enabled = name.isNotBlank() && pinsMatch,
        )
    }
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String, isError: Boolean = false) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onChange(text.filter { it.isDigit() }.take(6)) },
        modifier = Modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        isError = isError,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
    )
}

@Composable
fun PinDialog(title: String, check: (String) -> Boolean, onSuccess: () -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var wrong by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(pin, { pin = it; wrong = false }, "Forælder-PIN", isError = wrong)
                if (wrong) Text("Forkert PIN", color = MaterialTheme.colorScheme.error)
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (check(pin)) onSuccess() else wrong = true },
                enabled = pin.length >= 4,
            ) { Text("OK") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annullér") }
        },
    )
}

@Composable
private fun ProfileDialog(
    profile: Profile,
    canDelete: Boolean,
    onSave: (Profile) -> Unit,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(profile.name) }
    var role by remember { mutableStateOf(profile.role) }
    var color by remember { mutableIntStateOf(profile.color) }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (profile.id.isBlank()) "Ny profil" else "Ret profil") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Navn") },
                    keyboardOptions = SentenceCaps,
                    singleLine = true,
                )
                Segmented(
                    options = listOf("Forælder", "Barn"),
                    selected = if (role == ROLE_PARENT) 0 else 1,
                    onSelect = { role = if (it == 0) ROLE_PARENT else ROLE_CHILD },
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ProfileColors.forEachIndexed { index, swatch ->
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(swatch)
                                .then(
                                    if (index == color) Modifier.border(3.dp, Ink, CircleShape) else Modifier
                                )
                                .clickable { color = index },
                        )
                    }
                }
                if (canDelete) {
                    TextButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) { Text("Slet profil") }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(profile.copy(name = name.trim(), role = role, color = color)) },
                enabled = name.isNotBlank(),
            ) { Text("Gem") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annullér") }
        },
    )

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Slet ${profile.name}?") },
            text = { Text("Profilen forsvinder på alle telefoner. Pligter, der står på profilen, bliver liggende.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete()
                }) { Text("Slet") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Annullér") }
            },
        )
    }
}

// ---------- Familie og indstillinger ----------

@Composable
fun FamilyScreen(vm: AppViewModel, nav: NavController) {
    val context = LocalContext.current
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val me by vm.me.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val isParent = me?.role == ROLE_PARENT
    val all = profiles.orEmpty().sortedForDisplay()
    val parentCount = all.count { it.role == ROLE_PARENT }
    val code = vm.session.householdId

    var editing by remember { mutableStateOf<Profile?>(null) }
    var changingPin by remember { mutableStateOf(false) }
    var confirmLeave by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        BackBar("Tilbage", onBack = { nav.popBackStack() })
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Familien", fontSize = 30.sp, fontWeight = FontWeight.Bold)

            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White) {
                Row(
                    Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Avatar(me, 48.dp)
                    Column(Modifier.weight(1f)) {
                        Text(me?.name.orEmpty(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("${roleLabel(me?.role.orEmpty())} · på denne telefon", fontSize = 14.sp, color = Muted)
                    }
                    TextButton(onClick = { vm.selectProfile(null) }) {
                        Text("Skift", fontWeight = FontWeight.Bold)
                    }
                }
            }

            SectionLabel("Profiler")
            WhiteCard {
                all.forEach { profile ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = isParent) { editing = profile }
                            .heightIn(min = 56.dp)
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Avatar(profile, 36.dp)
                        Text(profile.name, Modifier.weight(1f), fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(roleLabel(profile.role), fontSize = 14.sp, color = Muted)
                    }
                }
                if (isParent) {
                    TextButton(
                        onClick = { editing = Profile(color = all.size % ProfileColors.size) },
                        modifier = Modifier.padding(horizontal = 4.dp),
                    ) {
                        Text("+ Ny profil", fontWeight = FontWeight.Bold)
                    }
                }
            }
            if (!isParent) {
                Text("Kun forældre kan rette profilerne.", fontSize = 14.sp, color = Muted)
            }

            if (isParent) {
                SectionLabel("Forælder-PIN")
                OutlinedButton(onClick = { changingPin = true }) {
                    Text("Skift forælder-PIN")
                }
            }

            SectionLabel("Familiekalender")
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White) {
                Text(
                    settings?.calendarName?.takeIf { it.isNotBlank() }
                        ?: "Ikke valgt endnu. En forælder vælger den under fanen Kalender.",
                    Modifier.padding(16.dp),
                    fontSize = 15.sp,
                )
            }

            SectionLabel("Deling")
            Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), color = Color.White) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!vm.session.firebaseEnabled) {
                        Text("Kun på denne telefon", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "Appen er bygget uden Firebase, så intet deles med andre telefoner.",
                            fontSize = 14.sp,
                            color = Muted,
                        )
                    } else if (code != null) {
                        Text("Familiekode", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Muted)
                        Text(code, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                        Button(onClick = {
                            val intent = Intent(Intent.ACTION_SEND)
                                .setType("text/plain")
                                .putExtra(Intent.EXTRA_TEXT, "Vores familiekode til appen Familien: $code")
                            context.startActivity(Intent.createChooser(intent, "Del familiekode"))
                        }) {
                            Text("Del koden", fontWeight = FontWeight.Bold)
                        }
                        TextButton(onClick = { confirmLeave = true }) {
                            Text("Forlad familien på denne telefon")
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    editing?.let { profile ->
        val lastParent = profile.role == ROLE_PARENT && parentCount <= 1
        ProfileDialog(
            profile = profile,
            canDelete = profile.id.isNotBlank() && !lastParent,
            onSave = {
                // Den sidste forælder kan ikke gøres til barn.
                val safe = if (lastParent) it.copy(role = ROLE_PARENT) else it
                vm.saveProfile(safe)
                editing = null
            },
            onDelete = {
                vm.deleteProfile(profile.id)
                editing = null
            },
            onDismiss = { editing = null },
        )
    }

    if (changingPin) {
        ChangePinDialog(
            onSave = {
                vm.setPin(it)
                changingPin = false
            },
            onDismiss = { changingPin = false },
        )
    }

    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { confirmLeave = false },
            title = { Text("Forlad familien?") },
            text = { Text("Telefonen forlader familien. Data bliver liggende, og du kan komme ind igen med koden.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLeave = false
                    vm.leaveHousehold()
                }) { Text("Forlad") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLeave = false }) { Text("Annullér") }
            },
        )
    }
}

@Composable
private fun ChangePinDialog(onSave: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    var repeat by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ny forælder-PIN") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                PinField(pin, { pin = it }, "Ny PIN (4–6 cifre)")
                PinField(repeat, { repeat = it }, "Gentag PIN", isError = repeat.isNotEmpty() && repeat != pin)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(pin) }, enabled = pin.length >= 4 && pin == repeat) { Text("Gem") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Annullér") }
        },
    )
}
