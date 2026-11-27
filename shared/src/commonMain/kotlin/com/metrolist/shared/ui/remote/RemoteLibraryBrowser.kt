// Ported from Sora (github.com/shizenworks/sora)'s remote libraries. Licensed under GPL-3.0.

package com.metrolist.shared.ui.remote

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.metrolist.shared.generated.resources.Res
import com.metrolist.shared.generated.resources.cancel
import com.metrolist.shared.generated.resources.remote_library_add
import com.metrolist.shared.generated.resources.remote_library_address
import com.metrolist.shared.generated.resources.remote_library_address_invalid
import com.metrolist.shared.generated.resources.remote_library_empty_folder
import com.metrolist.shared.generated.resources.remote_library_failed
import com.metrolist.shared.generated.resources.remote_library_name
import com.metrolist.shared.generated.resources.remote_library_none
import com.metrolist.shared.generated.resources.remote_library_password
import com.metrolist.shared.generated.resources.remote_library_remove
import com.metrolist.shared.generated.resources.remote_library_save
import com.metrolist.shared.generated.resources.remote_library_title
import com.metrolist.shared.generated.resources.remote_library_username
import com.metrolist.shared.generated.resources.retry
import com.metrolist.shared.model.SongItem
import com.metrolist.shared.remote.RemoteEntry
import com.metrolist.shared.remote.RemoteLibraries
import com.metrolist.shared.remote.RemoteServer
import com.metrolist.shared.remote.listRemoteFolder
import com.metrolist.shared.ui.icons.MetrolistIcons
import com.metrolist.shared.ui.theme.mochyInterFontFamily
import com.metrolist.shared.utils.MetrolistClock
import org.jetbrains.compose.resources.stringResource

private val Row = Color(0xFF1C1C1E)
private val Muted = Color.White.copy(alpha = 0.6f)

// where the browser is: the server list, the add form, or a folder on a server
private sealed interface RemotePage {
    data object Servers : RemotePage

    data object AddServer : RemotePage

    data class Folder(
        val server: RemoteServer,
        val url: String,
        val title: String,
    ) : RemotePage
}

/** The listener's own servers, full screen: add one, open its folders, and play the audio files in a folder. */
@Composable
fun RemoteLibraryBrowser(
    onDismiss: () -> Unit,
    onPlaySongs: (List<SongItem>, Int) -> Unit,
) {
    val stack = remember { mutableStateListOf<RemotePage>(RemotePage.Servers) }
    var servers by remember { mutableStateOf(RemoteLibraries.servers()) }
    val page = stack.last()
    val back = { if (stack.size > 1) stack.removeAt(stack.lastIndex) else onDismiss() }

    Dialog(
        onDismissRequest = { back() },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)) {
                IconButton(onClick = { back() }) {
                    Icon(MetrolistIcons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.White)
                }
                Text(
                    text =
                        when (page) {
                            is RemotePage.Folder -> page.title
                            RemotePage.AddServer -> stringResource(Res.string.remote_library_add)
                            RemotePage.Servers -> stringResource(Res.string.remote_library_title)
                        },
                    color = Color.White,
                    fontFamily = mochyInterFontFamily(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            when (page) {
                RemotePage.Servers -> {
                    ServerList(
                        servers = servers,
                        onOpen = { stack += RemotePage.Folder(it, it.address, it.name) },
                        onRemove = {
                            RemoteLibraries.remove(it.id)
                            servers = RemoteLibraries.servers()
                        },
                        onAdd = { stack += RemotePage.AddServer },
                    )
                }

                RemotePage.AddServer -> {
                    AddServerForm(
                        onSave = {
                            RemoteLibraries.upsert(it)
                            servers = RemoteLibraries.servers()
                            back()
                        },
                        onCancel = { back() },
                    )
                }

                is RemotePage.Folder -> {
                    FolderPage(
                        page = page,
                        onOpenFolder = { entry -> stack += RemotePage.Folder(page.server, entry.url, entry.name) },
                        onPlay = { files, index ->
                            onPlaySongs(files.map { RemoteLibraries.song(page.server, it) }, index)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ServerList(
    servers: List<RemoteServer>,
    onOpen: (RemoteServer) -> Unit,
    onRemove: (RemoteServer) -> Unit,
    onAdd: () -> Unit,
) {
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (servers.isEmpty()) {
            item { Text(stringResource(Res.string.remote_library_none), color = Muted, modifier = Modifier.padding(vertical = 12.dp)) }
        }
        items(servers, key = { it.id }) { server ->
            BrowserRow(
                icon = MetrolistIcons.Outlined.Storage,
                title = server.name,
                subtitle = server.address,
                onClick = { onOpen(server) },
                trailing = {
                    IconButton(onClick = { onRemove(server) }) {
                        Icon(
                            MetrolistIcons.Outlined.Delete,
                            contentDescription = stringResource(Res.string.remote_library_remove),
                            tint = Muted,
                        )
                    }
                },
            )
        }
        item {
            BrowserRow(
                icon = MetrolistIcons.Filled.Add,
                title = stringResource(Res.string.remote_library_add),
                subtitle = null,
                onClick = onAdd,
            )
        }
    }
}

@Composable
private fun AddServerForm(
    onSave: (RemoteServer) -> Unit,
    onCancel: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("https://") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val valid = (address.startsWith("https://") || address.startsWith("http://")) && address.substringAfter("://").isNotBlank()
    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name, {
            name = it
        }, label = { Text(stringResource(Res.string.remote_library_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            address,
            { address = it.trim() },
            label = { Text(stringResource(Res.string.remote_library_address)) },
            singleLine = true,
            isError = !valid,
            supportingText = if (valid) null else ({ Text(stringResource(Res.string.remote_library_address_invalid)) }),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(username, {
            username = it
        }, label = { Text(stringResource(Res.string.remote_library_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(
            password,
            { password = it },
            label = { Text(stringResource(Res.string.remote_library_password)) },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.End, modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onCancel) { Text(stringResource(Res.string.cancel)) }
            Spacer(Modifier.width(8.dp))
            TextButton(
                enabled = valid,
                onClick = {
                    onSave(
                        RemoteServer(
                            id = MetrolistClock.nowMs().toString(36),
                            name = name.trim().ifBlank { address.substringAfter("://").substringBefore('/') },
                            address = address,
                            username = username.trim(),
                            password = password,
                        ),
                    )
                },
            ) { Text(stringResource(Res.string.remote_library_save)) }
        }
    }
}

@Composable
private fun FolderPage(
    page: RemotePage.Folder,
    onOpenFolder: (RemoteEntry) -> Unit,
    onPlay: (List<RemoteEntry>, Int) -> Unit,
) {
    var attempt by remember { mutableIntStateOf(0) }
    var result by remember(page) { mutableStateOf<Result<List<RemoteEntry>>?>(null) }
    LaunchedEffect(page, attempt) {
        result = null
        result = listRemoteFolder(page.server, page.url)
    }
    val current = result
    when {
        current == null -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color.White) }
        }

        current.isFailure -> {
            Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(Res.string.remote_library_failed, current.exceptionOrNull()?.message.orEmpty()),
                    color = Muted,
                )
                TextButton(onClick = { attempt++ }) { Text(stringResource(Res.string.retry)) }
            }
        }

        else -> {
            val entries = current.getOrThrow()
            val files = entries.filterNot { it.isFolder }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (entries.isEmpty()) {
                    item {
                        Text(
                            stringResource(Res.string.remote_library_empty_folder),
                            color = Muted,
                            modifier = Modifier.padding(vertical = 12.dp),
                        )
                    }
                }
                items(entries, key = { it.url }) { entry ->
                    BrowserRow(
                        icon = if (entry.isFolder) MetrolistIcons.AutoMirrored.Filled.QueueMusic else MetrolistIcons.Outlined.MusicNote,
                        title = if (entry.isFolder) entry.name else entry.name.substringBeforeLast('.'),
                        subtitle = null,
                        onClick = { if (entry.isFolder) onOpenFolder(entry) else onPlay(files, files.indexOf(entry)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserRow(
    icon: ImageVector,
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(Row)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = Color.White,
                fontFamily = mochyInterFontFamily(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = Muted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        trailing?.invoke()
    }
}
