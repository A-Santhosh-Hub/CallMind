package com.example.callmind.features.contacts

import android.Manifest
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.ContactsContract.CommonDataKinds
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callmind.core.permissions.rememberPermissionState
import com.example.callmind.data.system.NewContact
import com.example.callmind.ui.components.PageHorizontalPadding
import com.example.callmind.ui.theme.contactAccent
import com.example.callmind.ui.theme.contactAvatar
import com.example.callmind.ui.theme.tertiaryText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

private data class TypedValue(val type: Int, val value: String)

private val PhoneTypes = listOf(
    CommonDataKinds.Phone.TYPE_MOBILE to "Mobile",
    CommonDataKinds.Phone.TYPE_HOME to "Home",
    CommonDataKinds.Phone.TYPE_WORK to "Work",
    CommonDataKinds.Phone.TYPE_OTHER to "Other"
)
private val EmailTypes = listOf(
    CommonDataKinds.Email.TYPE_HOME to "Personal",
    CommonDataKinds.Email.TYPE_WORK to "Work",
    CommonDataKinds.Email.TYPE_OTHER to "Other"
)

/** New-contact form that saves to the phone's address book. */
@Composable
fun ContactEditorScreen(
    onClose: () -> Unit,
    prefillNumber: String? = null,
    viewModel: ContactViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val writePermission = rememberPermissionState(Manifest.permission.WRITE_CONTACTS)

    var fullName by remember { mutableStateOf("") }
    var givenName by remember { mutableStateOf("") }
    var familyName by remember { mutableStateOf("") }
    var splitName by remember { mutableStateOf(false) }
    var company by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var expanded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var photo by remember { mutableStateOf<Bitmap?>(null) }
    var photoBytes by remember { mutableStateOf<ByteArray?>(null) }
    val phones = remember {
        mutableStateListOf(TypedValue(CommonDataKinds.Phone.TYPE_MOBILE, prefillNumber.orEmpty())).apply {
            // A prefilled number gets a spare empty row after it, like typing one would.
            if (!prefillNumber.isNullOrBlank()) add(TypedValue(CommonDataKinds.Phone.TYPE_MOBILE, ""))
        }
    }
    val emails = remember { mutableStateListOf(TypedValue(CommonDataKinds.Email.TYPE_HOME, "")) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) {
            // Contacts photos are small: decode to at most ~720 px and store as JPEG.
            scope.launch {
                val bytes = withContext(Dispatchers.IO) { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }
                val decoded = decodeScaled(bytes)
                if (decoded != null) {
                    photo = decoded
                    photoBytes = ByteArrayOutputStream().also { decoded.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
                }
            }
        }
    }

    val hasName = if (splitName) givenName.isNotBlank() || familyName.isNotBlank() else fullName.isNotBlank()
    val hasPhone = phones.any { it.value.isNotBlank() }
    val canSave = (hasName || hasPhone) && !saving

    var pendingSave by remember { mutableStateOf(false) }
    fun doSave() {
        saving = true
        viewModel.save(
            NewContact(
                givenName = if (splitName) givenName else "",
                familyName = if (splitName) familyName else "",
                displayName = if (splitName) "" else fullName,
                company = company,
                title = title,
                phones = phones.map { it.type to it.value },
                emails = emails.map { it.type to it.value },
                note = note,
                photoJpeg = photoBytes
            )
        ) { saved ->
            saving = false
            Toast.makeText(context, if (saved) "Contact saved" else "Couldn't save the contact", Toast.LENGTH_SHORT).show()
            if (saved) onClose()
        }
    }
    LaunchedEffect(writePermission.isGranted) {
        if (writePermission.isGranted && pendingSave) {
            pendingSave = false
            doSave()
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().imePadding()) {
        // ✕   New contact   ✓
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Clear, contentDescription = "Close") }
            Text(
                "New contact",
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            IconButton(
                enabled = canSave,
                onClick = {
                    if (writePermission.isGranted) doSave() else {
                        pendingSave = true
                        writePermission.requestPermission()
                    }
                }
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = "Save",
                    tint = if (canSave) MaterialTheme.colorScheme.contactAccent else MaterialTheme.colorScheme.tertiaryText
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = PageHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Photo
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentAlignment = Alignment.Center) {
                Box(
                    modifier = Modifier
                        .size(84.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.contactAvatar)
                        .clickable {
                            photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val shown = photo
                    if (shown != null) {
                        Image(shown.asImageBitmap(), contentDescription = "Contact photo", contentScale = ContentScale.Crop, modifier = Modifier.size(84.dp))
                    } else {
                        Icon(Icons.Default.Add, contentDescription = "Add photo", tint = MaterialTheme.colorScheme.contactAccent, modifier = Modifier.size(40.dp))
                    }
                }
            }

            // Name
            FormCard {
                Row(verticalAlignment = Alignment.Top) {
                    FieldIcon(Icons.Default.Person)
                    Column(modifier = Modifier.weight(1f)) {
                        if (splitName) {
                            FlatField(givenName, { givenName = it }, "First name", capitalize = true)
                            FieldDivider()
                            FlatField(familyName, { familyName = it }, "Last name", capitalize = true)
                        } else {
                            FlatField(fullName, { fullName = it }, "Name", capitalize = true)
                        }
                    }
                    IconButton(onClick = {
                        // Keep what was typed when switching between one field and two.
                        if (!splitName) {
                            val parts = fullName.trim().split(" ", limit = 2)
                            givenName = parts.getOrElse(0) { "" }
                            familyName = parts.getOrElse(1) { "" }
                        } else {
                            fullName = listOf(givenName, familyName).filter { it.isNotBlank() }.joinToString(" ")
                        }
                        splitName = !splitName
                    }) {
                        Icon(
                            if (splitName) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (splitName) "Use one name field" else "Split into first and last name"
                        )
                    }
                }
            }

            // Company / title
            FormCard {
                Row(verticalAlignment = Alignment.Top) {
                    FieldIcon(Icons.Default.Edit)
                    Column(modifier = Modifier.weight(1f)) {
                        FlatField(company, { company = it }, "Company", capitalize = true)
                        FieldDivider()
                        FlatField(title, { title = it }, "Title", capitalize = true)
                    }
                }
            }

            // Phones
            FormCard {
                Row(verticalAlignment = Alignment.Top) {
                    FieldIcon(Icons.Default.Call)
                    Column(modifier = Modifier.weight(1f)) {
                        phones.forEachIndexed { index, row ->
                            if (index > 0) FieldDivider()
                            TypeSelector(PhoneTypes, row.type) { phones[index] = row.copy(type = it) }
                            FlatField(
                                row.value,
                                {
                                    phones[index] = row.copy(value = it)
                                    // Typing in the last row opens a new empty one (up to 5), like the reference.
                                    if (index == phones.lastIndex && it.isNotBlank() && phones.size < 5) {
                                        phones.add(TypedValue(CommonDataKinds.Phone.TYPE_MOBILE, ""))
                                    }
                                },
                                "Phone",
                                keyboardType = KeyboardType.Phone
                            )
                        }
                    }
                }
            }

            // Emails
            FormCard {
                Row(verticalAlignment = Alignment.Top) {
                    FieldIcon(Icons.Default.Email)
                    Column(modifier = Modifier.weight(1f)) {
                        emails.forEachIndexed { index, row ->
                            if (index > 0) FieldDivider()
                            TypeSelector(EmailTypes, row.type) { emails[index] = row.copy(type = it) }
                            FlatField(
                                row.value,
                                {
                                    emails[index] = row.copy(value = it)
                                    if (index == emails.lastIndex && it.isNotBlank() && emails.size < 5) {
                                        emails.add(TypedValue(CommonDataKinds.Email.TYPE_HOME, ""))
                                    }
                                },
                                "Email",
                                keyboardType = KeyboardType.Email
                            )
                        }
                    }
                }
            }

            // Where it is saved (the reference calls this "Save location")
            FormCard {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Save location", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    Text("Phone", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            if (expanded) {
                FormCard {
                    FlatField(note, { note = it }, "Notes", singleLine = false, capitalize = true, modifier = Modifier.padding(start = 16.dp))
                }
            } else {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TextButton(onClick = { expanded = true }) {
                        Text("Expand", color = MaterialTheme.colorScheme.contactAccent, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

private suspend fun decodeScaled(bytes: ByteArray?): Bitmap? = withContext(Dispatchers.Default) {
    if (bytes == null) return@withContext null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    var sample = 1
    while (bounds.outWidth / sample > 1440 || bounds.outHeight / sample > 1440) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return@withContext null
    val side = minOf(decoded.width, decoded.height)
    val square = Bitmap.createBitmap(decoded, (decoded.width - side) / 2, (decoded.height - side) / 2, side, side)
    if (side > 720) Bitmap.createScaledBitmap(square, 720, 720, true) else square
}

@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surface) {
        Column(content = content)
    }
}

@Composable
private fun FieldIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Icon(
        icon,
        contentDescription = null,
        modifier = Modifier.padding(start = 16.dp, end = 14.dp, top = 15.dp).size(24.dp),
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f)
    )
}

@Composable
private fun FieldDivider() {
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
}

/** Plain text field with no box or underline, as in the reference cards. */
@Composable
private fun FlatField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalize: Boolean = false,
    singleLine: Boolean = true
) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        placeholder = { Text(placeholder, fontSize = 17.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)) },
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            capitalization = if (capitalize) KeyboardCapitalization.Words else KeyboardCapitalization.None
        ),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            disabledContainerColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            cursorColor = MaterialTheme.colorScheme.contactAccent
        )
    )
}

/** "Mobile ⌄" label that opens a menu to choose the number/email type. */
@Composable
private fun TypeSelector(options: List<Pair<Int, String>>, selected: Int, onSelected: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.clickable { open = true }.padding(start = 16.dp, top = 14.dp, bottom = 0.dp, end = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                options.firstOrNull { it.first == selected }?.second ?: options.first().second,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Choose type", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { (type, label) ->
                DropdownMenuItem(text = { Text(label) }, onClick = { onSelected(type); open = false })
            }
        }
    }
}
