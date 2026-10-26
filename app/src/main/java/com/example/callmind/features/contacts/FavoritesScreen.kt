package com.example.callmind.features.contacts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.callmind.ui.components.EmptyState
import com.example.callmind.ui.theme.contactListBackground

/** Starred contacts, same list style as the main Contacts tab. */
@Composable
fun FavoritesScreen(
    onBack: (() -> Unit)?,
    onOpenContact: (Long) -> Unit,
    viewModel: ContactViewModel = hiltViewModel()
) {
    val contacts by viewModel.contacts.collectAsState()
    val favorites = remember(contacts) { contacts.filter { it.starred } }

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.contactListBackground).statusBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            } else {
                Spacer(modifier = Modifier.width(12.dp)) // opened as a tab: no back arrow
            }
            Text("Favorites", fontSize = 28.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onBackground)
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (favorites.isEmpty()) {
            EmptyState(
                title = "No favorites",
                message = "Open a contact and tap the star to add it here.",
                icon = {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.18f)
                    )
                }
            )
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(favorites, key = { it.id }) { contact ->
                    ContactRow(
                        contact = contact,
                        subtitle = null,
                        selecting = false,
                        selected = false,
                        onClick = { onOpenContact(contact.id) },
                        onLongClick = {}
                    )
                }
            }
        }
    }
}
