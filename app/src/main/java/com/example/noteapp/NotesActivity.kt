package com.example.noteapp

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.*

class NotesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val context = LocalContext.current
            val themeMode = remember { mutableStateOf(loadThemeMode(context)) }
            val darkTheme = when (themeMode.value) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val window = (context as? ComponentActivity)?.window

            DisposableEffect(darkTheme) {
                if (window != null) {
                    WindowCompat.setDecorFitsSystemWindows(window, false)
                    val insetsController = WindowCompat.getInsetsController(window, window.decorView)
                    insetsController.isAppearanceLightStatusBars = !darkTheme
                }
                onDispose { }
            }
            NoteAppTheme(darkTheme = darkTheme) {
                NotesScreen(
                    themeMode = themeMode.value,
                    onThemeModeChange = { mode ->
                        themeMode.value = mode
                        saveThemeMode(context, mode)
                    }
                )
            }
        }
    }
}

enum class ThemeMode { LIGHT, DARK, SYSTEM }

private const val PREFS_THEME = "theme_prefs"
private const val THEME_KEY = "theme_mode"

private fun loadThemeMode(context: Context): ThemeMode {
    val prefs = context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
    return ThemeMode.entries.getOrNull(prefs.getInt(THEME_KEY, 0)) ?: ThemeMode.SYSTEM
}

private fun saveThemeMode(context: Context, mode: ThemeMode) {
    context.getSharedPreferences(PREFS_THEME, Context.MODE_PRIVATE)
        .edit()
        .putInt(THEME_KEY, mode.ordinal)
        .apply()
}

@Composable
fun NoteAppTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit
) {
    val lightScheme = lightColorScheme(
        primary = Color(0xFF3F51B5),
        secondary = Color(0xFF4CAF50),
        background = Color(0xFFF5F5F5),
        surface = Color.White,
        onSurface = Color(0xFF1C1B1F),
        onSurfaceVariant = Color(0xFF49454F)
    )
    val darkScheme = darkColorScheme(
        primary = Color(0xFF8B9DC3),
        secondary = Color(0xFF81C784),
        background = Color(0xFF121212),
        surface = Color(0xFF1E1E1E),
        onSurface = Color(0xFFE6E1E5),
        onSurfaceVariant = Color(0xFFCAC4D0)
    )
    MaterialTheme(
        colorScheme = if (darkTheme) darkScheme else lightScheme,
        content = content
    )
}

data class Note(
    val id: Int,
    val text: String,
    val dateTime: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {}
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val coroutineScope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val scrollState = rememberLazyListState()

    var noteText by remember { mutableStateOf("") }
    val notes = remember { mutableStateListOf<Note>() }
    var noteIdCounter by remember { mutableIntStateOf(0) }
    var editingNote by remember { mutableStateOf<Note?>(null) }

    LaunchedEffect(Unit) {
        loadNotes(context, notes) { maxId ->
            noteIdCounter = maxId + 1
        }
    }

    fun startEdit(note: Note) {
        editingNote = note
        noteText = note.text
        keyboardController?.show()
    }

    fun cancelEdit() {
        editingNote = null
        noteText = ""
        keyboardController?.hide()
    }

    fun saveNote() {
        if (noteText.isNotBlank()) {
            val dateTime = DateFormat.getDateTimeInstance(
                DateFormat.MEDIUM,
                DateFormat.SHORT,
                Locale.getDefault()
            ).format(Date())
            if (editingNote != null) {
                val note = editingNote!!
                val index = notes.indexOfFirst { it.id == note.id }
                if (index >= 0) {
                    notes[index] = note.copy(text = noteText, dateTime = dateTime)
                    saveNotes(context, notes)
                }
                cancelEdit()
            } else {
                val newNote = Note(
                    id = noteIdCounter++,
                    text = noteText,
                    dateTime = dateTime
                )
                notes.add(newNote)
                saveNotes(context, notes)
                noteText = ""
                keyboardController?.hide()
                coroutineScope.launch {
                    scrollState.animateScrollToItem(0)
                }
            }
        }
    }

    val imePadding = WindowInsets.ime.asPaddingValues().calculateBottomPadding()

    var showThemeMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.app_title),
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                },
                actions = {
                    Box {
                        IconButton(onClick = { showThemeMenu = true }) {
                            Icon(
                                imageVector = when (themeMode) {
                                    ThemeMode.LIGHT -> Icons.Default.LightMode
                                    ThemeMode.DARK -> Icons.Default.DarkMode
                                    ThemeMode.SYSTEM -> Icons.Default.Settings
                                },
                                contentDescription = stringResource(R.string.theme),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        DropdownMenu(
                            expanded = showThemeMenu,
                            onDismissRequest = { showThemeMenu = false }
                        ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme_light)) },
                            onClick = {
                                onThemeModeChange(ThemeMode.LIGHT)
                                showThemeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme_dark)) },
                            onClick = {
                                onThemeModeChange(ThemeMode.DARK)
                                showThemeMenu = false
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme_system)) },
                            onClick = {
                                onThemeModeChange(ThemeMode.SYSTEM)
                                showThemeMenu = false
                            }
                        )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        content = { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize(),
                    state = scrollState,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 16.dp,
                        bottom = 200.dp
                    )
                ) {
                    if (notes.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.notes_empty),
                                    fontSize = 18.sp,
                                    color = Color.Gray,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    } else {
                        items(notes.reversed()) { note ->
                            NoteItem(
                                note = note,
                                onEdit = { startEdit(note) },
                                onDelete = {
                                    notes.remove(note)
                                    saveNotes(context, notes)
                                    if (editingNote?.id == note.id) cancelEdit()
                                }
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                ) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = imePadding)
                            .heightIn(min = 56.dp, max = 200.dp),
                        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            BasicTextField(
                                value = noteText,
                                onValueChange = { noteText = it },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 40.dp, max = 120.dp)
                                    .focusRequester(focusRequester)
                                    .padding(vertical = 8.dp),
                                textStyle = TextStyle(
                                    fontSize = 16.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                ),
                                maxLines = 4,
                                decorationBox = { innerTextField ->
                                    Box(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentAlignment = Alignment.TopStart
                                    ) {
                                        if (noteText.isEmpty()) {
                                            Text(
                                                text = if (editingNote != null) stringResource(R.string.hint_edit_note) else stringResource(R.string.hint_new_note),
                                                fontSize = 16.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            )

                            if (editingNote != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = { cancelEdit() },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Text(stringResource(R.string.cancel), fontSize = 16.sp)
                                    }
                                    Button(
                                        onClick = { saveNote() },
                                        modifier = Modifier.weight(1f).height(48.dp),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary
                                        )
                                    ) {
                                        Text(stringResource(R.string.save), fontSize = 16.sp, fontWeight = FontWeight.Medium)
                                    }
                                }
                            } else {
                                Button(
                                    onClick = { saveNote() },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.secondary
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(
                                        defaultElevation = 4.dp,
                                        pressedElevation = 8.dp
                                    )
                                ) {
                                    Text(
                                        text = stringResource(R.string.add_note),
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoteItem(
    note: Note,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteDialog by remember { mutableStateOf(false) }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.delete_note_title)) },
            text = { Text(stringResource(R.string.delete_note_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.delete), color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteDialog = false }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "🕐 ${note.dateTime}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onEdit,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = stringResource(R.string.edit),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { showDeleteDialog = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            tint = Color(0xFFF44336),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
                modifier = Modifier.padding(vertical = 4.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = note.text,
                fontSize = 16.sp,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 22.sp
            )
        }
    }
}

private const val PREFS_NAME = "notes_prefs"
private const val NOTES_KEY = "notes_list"

fun saveNotes(context: Context, notes: List<Note>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = Gson().toJson(notes)
    prefs.edit().putString(NOTES_KEY, json).apply()
}

fun loadNotes(context: Context, notesList: MutableList<Note>, onMaxIdLoaded: (Int) -> Unit) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val json = prefs.getString(NOTES_KEY, "[]")

    val type = object : TypeToken<List<Note>>() {}.type
    val notes = Gson().fromJson<List<Note>>(json, type) ?: emptyList()

    notesList.clear()
    notesList.addAll(notes)
    val maxId = notes.maxByOrNull { it.id }?.id ?: 0
    onMaxIdLoaded(maxId)
}