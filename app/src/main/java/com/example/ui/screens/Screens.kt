package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.data.local.CallEntity
import com.example.data.local.ChatEntity
import com.example.data.local.MessageEntity
import com.example.data.local.StatusEntity
import com.example.data.model.CallStatus
import com.example.data.model.MessageType
import com.example.security.SignalEncryption
import com.example.ui.theme.*
import com.example.ui.viewmodel.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

enum class ScreenDestination {
    LOGIN, OTP, PROFILE_SETUP, HOME, CHAT_DETAIL, CALLING, INCOMING_CALL, VERIFY_SECURITY, STATUS_VIEWER
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigator(
    authViewModel: AuthViewModel,
    chatViewModel: ChatViewModel,
    callViewModel: CallViewModel,
    statusViewModel: StatusViewModel,
    settingsViewModel: SettingsViewModel
) {
    val authState by authViewModel.authState.collectAsState()
    val activeCall by callViewModel.activeCall.collectAsState()
    val incomingCall by callViewModel.incomingCall.collectAsState()
    val isDarkMode by settingsViewModel.isDarkMode.collectAsState()

    var currentScreen by remember { mutableStateOf(ScreenDestination.HOME) }
    var securityTargetChatId by remember { mutableStateOf<String?>(null) }
    var statusViewerTargetIndex by remember { mutableStateOf(0) }

    LaunchedEffect(authState) {
        currentScreen = when (authState) {
            AuthState.UNAUTHENTICATED -> ScreenDestination.LOGIN
            AuthState.OTP_VERIFICATION -> ScreenDestination.OTP
            AuthState.PROFILE_SETUP -> ScreenDestination.PROFILE_SETUP
            AuthState.AUTHENTICATED -> ScreenDestination.HOME
        }
    }

    LaunchedEffect(activeCall, incomingCall) {
        if (incomingCall != null) {
            currentScreen = ScreenDestination.INCOMING_CALL
        } else if (activeCall != null) {
            currentScreen = ScreenDestination.CALLING
        }
    }

    MyApplicationTheme(darkTheme = isDarkMode) {
        Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when (currentScreen) {
                ScreenDestination.LOGIN -> LoginScreen(authViewModel)
                ScreenDestination.OTP -> OtpScreen(authViewModel)
                ScreenDestination.PROFILE_SETUP -> ProfileSetupScreen(authViewModel)
                ScreenDestination.HOME -> HomeScreen(
                    chatViewModel = chatViewModel,
                    callViewModel = callViewModel,
                    statusViewModel = statusViewModel,
                    settingsViewModel = settingsViewModel,
                    authViewModel = authViewModel,
                    onChatClick = { chatId ->
                        chatViewModel.setActiveChat(chatId)
                        currentScreen = ScreenDestination.CHAT_DETAIL
                    },
                    onStatusClick = { index ->
                        statusViewerTargetIndex = index
                        currentScreen = ScreenDestination.STATUS_VIEWER
                    },
                    onLogout = { authViewModel.logout() }
                )
                ScreenDestination.CHAT_DETAIL -> {
                    val activeId by chatViewModel.activeChatId.collectAsState()
                    if (activeId != null) {
                        ChatDetailScreen(
                            chatViewModel = chatViewModel,
                            callViewModel = callViewModel,
                            settingsViewModel = settingsViewModel,
                            authViewModel = authViewModel,
                            onBack = {
                                chatViewModel.setActiveChat(null)
                                currentScreen = ScreenDestination.HOME
                            },
                            onHeaderClick = {
                                securityTargetChatId = activeId
                                currentScreen = ScreenDestination.VERIFY_SECURITY
                            }
                        )
                    } else {
                        currentScreen = ScreenDestination.HOME
                    }
                }
                ScreenDestination.CALLING -> {
                    ActiveCallScreen(callViewModel, onHangUp = {
                        callViewModel.endActiveCall()
                        currentScreen = ScreenDestination.HOME
                    })
                }
                ScreenDestination.INCOMING_CALL -> {
                    IncomingCallScreen(
                        callViewModel = callViewModel,
                        onAccept = {
                            callViewModel.acceptIncomingCall()
                            currentScreen = ScreenDestination.CALLING
                        },
                        onReject = {
                            callViewModel.rejectIncomingCall()
                            currentScreen = ScreenDestination.HOME
                        }
                    )
                }
                ScreenDestination.VERIFY_SECURITY -> {
                    EncryptionVerifyScreen(
                        chatId = securityTargetChatId ?: "",
                        chatViewModel = chatViewModel,
                        onBack = { currentScreen = ScreenDestination.CHAT_DETAIL }
                    )
                }
                ScreenDestination.STATUS_VIEWER -> {
                    val statuses by statusViewModel.activeStatuses.collectAsState()
                    if (statuses.isNotEmpty() && statusViewerTargetIndex in statuses.indices) {
                        StatusViewerScreen(
                            statuses = statuses,
                            startIndex = statusViewerTargetIndex,
                            onDismiss = { currentScreen = ScreenDestination.HOME }
                        )
                    } else {
                        currentScreen = ScreenDestination.HOME
                    }
                }
            }

            val toastMsg by settingsViewModel.inAppNotification.collectAsState()
            AnimatedVisibility(
                visible = toastMsg != null,
                enter = slideInVertically(initialOffsetY = { -it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { -it }) + fadeOut(),
                modifier = Modifier.padding(16.dp).align(Alignment.TopCenter)
            ) {
                toastMsg?.let { text ->
                    Card(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        elevation = CardDefaults.cardElevation(8.dp),
                        modifier = Modifier.fillMaxWidth().testTag("in_app_banner")
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(16.dp)
                        ) {
                            AvatarComponent(name = "Notification", url = null, modifier = Modifier.size(40.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Emerald Secure Alert",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = text,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AvatarComponent(name: String, url: String?, modifier: Modifier = Modifier) {
    if (url != null && url.isNotEmpty()) {
        AsyncImage(
            model = url,
            contentDescription = "$name's avatar",
            contentScale = ContentScale.Crop,
            modifier = modifier.clip(CircleShape)
        )
    } else {
        Box(
            contentAlignment = Alignment.Center,
            modifier = modifier
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(EmeraldPrimaryLight, EmeraldSecondaryLight)))
        ) {
            val shortText = if (name.isNotEmpty()) name.take(1).uppercase() else "?"
            Text(
                text = shortText,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
fun VerifiedBadge(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(Color(0xFF1DA1F2)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Default.Check,
            contentDescription = "Verified Blue Badge",
            tint = Color.White,
            modifier = Modifier.size(10.dp)
        )
    }
}

@Composable
fun LoginScreen(viewModel: AuthViewModel) {
    var phoneInput by remember { mutableStateOf("") }
    var showDevDialog by remember { mutableStateOf(false) }
    var devPassword by remember { mutableStateOf("") }
    var devError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = "Lock Icon",
            tint = EmeraldPrimaryDark,
            modifier = Modifier.size(80.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Welcome to Emerald Chat",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Enter your phone number to receive a secure, simulated OTP verification key.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = phoneInput,
            onValueChange = { phoneInput = it },
            label = { Text("Phone Number") },
            placeholder = { Text("+1 555-0100 or 010612") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("login_phone_input")
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = { 
                if (phoneInput == "010612") {
                    viewModel.loginWithDeveloperPassword("010612")
                } else if (phoneInput.isNotEmpty()) {
                    viewModel.sendOtp(phoneInput) 
                }
            },
            enabled = phoneInput.isNotEmpty(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("submit_button")
        ) {
            Text("Next", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(48.dp))

        // Professional, polished developer entrance
        OutlinedButton(
            onClick = { showDevDialog = true },
            shape = RoundedCornerShape(20.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)),
            modifier = Modifier.testTag("developer_portal_entry_button")
        ) {
            Icon(
                imageVector = Icons.Default.Build,
                contentDescription = "Dev Portal",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("🛠️ Developer Sandbox Access", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
        }
    }

    if (showDevDialog) {
        Dialog(onDismissRequest = { showDevDialog = false }) {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .testTag("dev_login_dialog")
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Admin",
                        tint = EmeraldPrimaryDark,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "System Developer Key",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Enter the developer access key code to initiate authenticated state simulation with blue badge authorization.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = devPassword,
                        onValueChange = { 
                            devPassword = it
                            devError = false 
                        },
                        label = { Text("Developer Pin/Password") },
                        placeholder = { Text("010612") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        modifier = Modifier.fillMaxWidth().testTag("dev_password_input")
                    )

                    if (devError) {
                        Text(
                            text = "Invalid passcode. Please try again.",
                            color = Color.Red,
                            fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.Start).padding(top = 4.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { 
                            showDevDialog = false
                            devPassword = ""
                            devError = false
                        }) {
                            Text("Dismiss")
                        }

                        Button(
                            onClick = {
                                val success = viewModel.loginWithDeveloperPassword(devPassword)
                                if (success) {
                                    showDevDialog = false
                                } else {
                                    devError = true
                                }
                            },
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Inject Signature Key")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OtpScreen(viewModel: AuthViewModel) {
    var otpInput by remember { mutableStateOf("") }
    val isVerifying by viewModel.isOtpVerifying.collectAsState()
    val phone by viewModel.phoneNumber.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Verify Code",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "We sent a simulated 6-digit key code to $phone. Enter it below.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        OutlinedTextField(
            value = otpInput,
            onValueChange = { if (it.length <= 6) otpInput = it },
            label = { Text("OTP Verification Code") },
            placeholder = { Text("123456") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("otp_input")
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isVerifying) {
            CircularProgressIndicator(color = EmeraldPrimaryDark)
        } else {
            Button(
                onClick = { if (otpInput.length == 6) viewModel.verifyOtp(otpInput) },
                enabled = otpInput.length == 6,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("verify_button")
            ) {
                Text("Verify Code", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun ProfileSetupScreen(viewModel: AuthViewModel) {
    var name by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("Hey there! I am using Emerald Chat.") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Profile Info",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Please provide your name and an optional bio status representation.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(CircleShape)
                .background(EmeraldSecondaryLight),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Contact Avatar",
                tint = Color.White,
                modifier = Modifier.size(50.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Your Name") },
            placeholder = { Text("e.g. John Doe") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("profile_name_input")
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = bio,
            onValueChange = { bio = it },
            label = { Text("Status Bio") },
            placeholder = { Text("Available") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().testTag("profile_bio_input")
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            onClick = { if (name.isNotEmpty()) viewModel.saveProfile(name, bio, null) },
            enabled = name.isNotEmpty(),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().height(50.dp).testTag("profile_save_button")
        ) {
            Text("Go to Chats", fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    chatViewModel: ChatViewModel,
    callViewModel: CallViewModel,
    statusViewModel: StatusViewModel,
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    onChatClick: (String) -> Unit,
    onStatusClick: (Int) -> Unit,
    onLogout: () -> Unit
) {
    var selectedTab by remember { mutableStateOf(0) }
    var showNewChatSheet by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Emerald Chat",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 20.sp
                    )
                },
                actions = {
                    IconButton(onClick = {
                        val isDark = settingsViewModel.isDarkMode.value
                        settingsViewModel.toggleTheme()
                        settingsViewModel.triggerInAppToast(if (isDark) "Switched to Light Emerald!" else "Switched to Midnight AMOLED!")
                    }) {
                        val isDark by settingsViewModel.isDarkMode.collectAsState()
                        Icon(
                            imageVector = if (isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Toggle Theme"
                        )
                    }
                    IconButton(onClick = onLogout) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Log out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                windowInsets = WindowInsets.navigationBars,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(imageVector = Icons.Default.Chat, contentDescription = "Chats") },
                    label = { Text("Chats") },
                    modifier = Modifier.testTag("nav_chats")
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(imageVector = Icons.Default.HistoryToggleOff, contentDescription = "Status") },
                    label = { Text("Status") },
                    modifier = Modifier.testTag("nav_status")
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(imageVector = Icons.Default.Call, contentDescription = "Calls") },
                    label = { Text("Calls") },
                    modifier = Modifier.testTag("nav_calls")
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") },
                    modifier = Modifier.testTag("nav_settings")
                )
            }
        },
        floatingActionButton = {
            if (selectedTab <= 1) {
                FloatingActionButton(
                    onClick = { showNewChatSheet = true },
                    containerColor = EmeraldPrimaryLight,
                    contentColor = Color.White,
                    modifier = Modifier.testTag("fab_create_chat")
                ) {
                    Icon(
                        imageVector = if (selectedTab == 0) Icons.Default.Message else Icons.Default.CameraAlt,
                        contentDescription = "Action"
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            when (selectedTab) {
                0 -> ChatListScreen(chatViewModel, onChatClick)
                1 -> StatusTabScreen(statusViewModel, onStatusClick)
                2 -> CallHistoryScreen(callViewModel)
                3 -> SettingsScreen(settingsViewModel, callViewModel, authViewModel)
            }

            if (showNewChatSheet) {
                NewChatBottomSheet(
                    chatViewModel = chatViewModel,
                    settingsViewModel = settingsViewModel,
                    onDismiss = { showNewChatSheet = false }
                )
            }
        }
    }
}

@Composable
fun ChatListScreen(viewModel: ChatViewModel, onChatClick: (String) -> Unit) {
    val chats by viewModel.chats.collectAsState()

    if (chats.isEmpty()) {
        EmptyPlaceholder(Icons.Default.ChatBubbleOutline, "Create a chat using the action icon below securely!")
    } else {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(chats) { chat ->
                ChatItemRow(chat = chat, onClick = { onChatClick(chat.chatId) })
                HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            }
        }
    }
}

@Composable
fun ChatItemRow(chat: ChatEntity, onClick: () -> Unit) {
    val unread = chat.unreadCount
    val lastTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(chat.lastMessageTime))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AvatarComponent(name = chat.name, url = chat.avatarUrl, modifier = Modifier.size(52.dp))
        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = chat.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = lastTime,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (chat.isTyping) {
                    Text(
                        text = "typing...",
                        color = EmeraldPrimaryDark,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                } else {
                    Text(
                        text = chat.lastMessage ?: "",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }

                if (unread > 0) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(EmeraldPrimaryLight)
                    ) {
                        Text(
                            text = unread.toString(),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chatViewModel: ChatViewModel,
    callViewModel: CallViewModel,
    settingsViewModel: SettingsViewModel,
    authViewModel: AuthViewModel,
    onBack: () -> Unit,
    onHeaderClick: () -> Unit
) {
    val activeId by chatViewModel.activeChatId.collectAsState()
    val messages by chatViewModel.messagesForActiveChat.collectAsState()
    val replyTarget by chatViewModel.replyingMessage.collectAsState()
    val attachmentPreviewUrl by chatViewModel.attachmentPreview.collectAsState()
    val profile by authViewModel.userProfile.collectAsState()

    var textInput by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var editTargetMessageId by remember { mutableStateOf<String?>(null) }
    var selectedMessageForSheet by remember { mutableStateOf<MessageEntity?>(null) }
    var showForwardSheet by remember { mutableStateOf(false) }

    var chatItemState by remember { mutableStateOf<ChatEntity?>(null) }

    LaunchedEffect(activeId) {
        activeId?.let { id ->
            chatItemState = chatViewModel.repository.getChatById(id)
        }
    }

    val scrollState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scrollState.animateScrollToItem(messages.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column(modifier = Modifier.clickable(onClick = onHeaderClick)) {
                        Text(
                            text = chatItemState?.name ?: "Secure Thread",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
                        )
                        val sub = when {
                            chatItemState?.isOnline == true -> "online"
                            chatItemState?.isTyping == true -> "typing..."
                            else -> "E2EE secure key"
                        }
                        Text(
                            text = sub,
                            fontSize = 11.sp,
                            color = if (chatItemState?.isTyping == true) EmeraldPrimaryDark else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Return")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        val peer = chatItemState
                        if (peer != null) {
                            callViewModel.initiateCall(peer.chatId, peer.name, peer.avatarUrl, isVideo = false)
                        }
                    }) {
                        Icon(imageVector = Icons.Default.Call, contentDescription = "Voice Call")
                    }
                    IconButton(onClick = {
                        val peer = chatItemState
                        if (peer != null) {
                            callViewModel.initiateCall(peer.chatId, peer.name, peer.avatarUrl, isVideo = true)
                        }
                    }) {
                        Icon(imageVector = Icons.Default.VideoCall, contentDescription = "Video Call")
                    }
                }
            )
        }
    ) { innerPadding ->
        val chatBgModifier = if (profile.isPremium) {
            when (profile.chatBgIndex) {
                1 -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFFE8F5E9), Color(0xFFC8E6C9)))) // Emerald Grid Style Gradient
                2 -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFFFFEE58).copy(alpha = 0.35f), Color(0xFFEC407A).copy(alpha = 0.35f)))) // Sunset Ripple Glow
                3 -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF0F2027), Color(0xFF203A43), Color(0xFF2C5364)))) // Neon Cyber Midnight
                4 -> Modifier.background(Brush.verticalGradient(listOf(Color(0xFF1F1C2C), Color(0xFF928DAB)))) // Carbon Lavender Tech
                else -> Modifier.background(MaterialTheme.colorScheme.background)
            }
        } else {
            Modifier.background(MaterialTheme.colorScheme.background)
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .then(chatBgModifier)
        ) {
            if (profile.isPremium && profile.chatBgIndex == 5 && !profile.customChatBgUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = profile.customChatBgUrl,
                    contentDescription = "Custom Chat Backdrop",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                    alpha = 0.4f
                )
            }
            Column(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = scrollState,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp)
                ) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Lock",
                                    tint = EmeraldPrimaryDark,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Messages are end-to-end encrypted. Tap to verify safety fingerprint.",
                                    fontSize = 11.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }

                    items(messages) { message ->
                        val isMe = message.senderId == "me"
                        val decryptedContent = if (message.type == MessageType.TEXT.name && !message.isDeletedForEveryone) {
                            SignalEncryption.decrypt(message.content, chatItemState?.name ?: "secure")
                        } else {
                            message.content
                        }

                        MessageBubble(
                            message = message.copy(content = decryptedContent),
                            isMe = isMe,
                            isDevSender = if (isMe) profile.isDeveloper else false,
                            customTitle = if (isMe) profile.customTitle else null,
                            titleBgColorIdx = if (isMe) profile.titleBgColorIndex else 0,
                            onLongClick = {
                                selectedMessageForSheet = message
                            }
                        )
                    }
                }

                attachmentPreviewUrl?.let { url ->
                    Row(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(model = url, contentDescription = "Uploaded", modifier = Modifier.size(60.dp).clip(RoundedCornerShape(8.dp)))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Ready to transmit secure media...", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        IconButton(onClick = { chatViewModel.setAttachmentPreview(null) }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel attachment")
                        }
                    }
                }

                replyTarget?.let { reply ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.Reply, contentDescription = "Reply target")
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = reply.senderName, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = EmeraldPrimaryDark)
                            Text(text = reply.content, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        IconButton(onClick = { chatViewModel.setReplyingMessage(null) }) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Cancel reply")
                        }
                    }
                }

                ChatInputBar(
                    text = textInput,
                    onTextChanged = { textInput = it },
                    editMode = editTargetMessageId != null,
                    onSend = {
                        val activePrompt = textInput
                        val editingId = editTargetMessageId

                        textInput = ""
                        editTargetMessageId = null

                        if (editingId != null) {
                            chatViewModel.editMessage(editingId, activePrompt)
                        } else {
                            attachmentPreviewUrl?.let { media ->
                                chatViewModel.sendMediaMessage(activePrompt, MessageType.IMAGE, media)
                            } ?: run {
                                chatViewModel.sendTextMessage(activePrompt)
                            }
                        }
                    },
                    onAttachClick = {
                        val randomImage = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?q=80&w=400"
                        chatViewModel.setAttachmentPreview(randomImage)
                    }
                )
            }
        }
    }

    selectedMessageForSheet?.let { resolvedMessage ->
        Dialog(onDismissRequest = { selectedMessageForSheet = null }) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("message_actions_dialog")
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Secure Controls", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))

                    val reactionsList = if (profile.isPremium) {
                        listOf("❤️", "👍", "😂", "😮", "🙏", "🔥", "😎", "👑", "🪐", "⚡", "💎", "🦄")
                    } else {
                        listOf("❤️", "👍", "😂", "😮", "🙏", "🔥")
                    }

                    reactionsList.chunked(6).forEach { chunk ->
                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            chunk.forEach { emoji ->
                                Text(
                                    text = emoji,
                                    fontSize = 28.sp,
                                    modifier = Modifier
                                        .clickable {
                                            chatViewModel.addReaction(resolvedMessage.messageId, emoji)
                                            selectedMessageForSheet = null
                                        }
                                        .padding(4.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    if (resolvedMessage.senderId == "me") {
                        DropdownMenuItem(
                            text = { Text("Edit Message") },
                            onClick = {
                                textInput = SignalEncryption.decrypt(resolvedMessage.content, chatItemState?.name ?: "secure")
                                editTargetMessageId = resolvedMessage.messageId
                                selectedMessageForSheet = null
                            },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = "Edit") }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete For Everyone", color = RedAccent) },
                            onClick = {
                                chatViewModel.deleteMessage(resolvedMessage.messageId)
                                selectedMessageForSheet = null
                            },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = "Delete", tint = RedAccent) }
                        )
                    }

                    DropdownMenuItem(
                        text = { Text("Forward Message") },
                        onClick = {
                            selectedMessageForSheet = null
                            showForwardSheet = true
                        },
                        leadingIcon = { Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = "Forward") }
                    )

                    DropdownMenuItem(
                        text = { Text("Reply") },
                        onClick = {
                            chatViewModel.setReplyingMessage(resolvedMessage)
                            selectedMessageForSheet = null
                        },
                        leadingIcon = { Icon(Icons.Default.Reply, contentDescription = "Reply") }
                    )
                }
            }
        }
    }

    if (showForwardSheet) {
        ForwardBottomSheet(
            onForward = { destChatId ->
                showForwardSheet = false
                val originalMsg = selectedMessageForSheet
                if (originalMsg != null) {
                    scope.launch {
                        chatViewModel.repository.sendMessage(
                            chatId = destChatId,
                            content = originalMsg.content,
                            type = MessageType.valueOf(originalMsg.type)
                        )
                        settingsViewModel.triggerInAppToast("Forwarded securely!")
                    }
                }
            },
            chatViewModel = chatViewModel,
            onDismiss = { showForwardSheet = false }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    message: MessageEntity,
    isMe: Boolean,
    isDevSender: Boolean = false,
    customTitle: String? = null,
    titleBgColorIdx: Int = 0,
    onLongClick: () -> Unit
) {
    val unreadColor = BlueAccent
    val readMarker = if (message.status == "READ") "✓✓" else "✓"
    val readColor = if (message.status == "READ") unreadColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Card(
            shape = RoundedCornerShape(
                topStart = 12.dp,
                topEnd = 12.dp,
                bottomStart = if (isMe) 12.dp else 0.dp,
                bottomEnd = if (isMe) 0.dp else 12.dp
            ),
            colors = CardDefaults.cardColors(
                containerColor = if (isMe) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier
                .widthIn(max = 290.dp)
                .combinedClickable(
                    onClick = {},
                    onLongClick = onLongClick
                )
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                if (isDevSender || !customTitle.isNullOrEmpty()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 6.dp)
                    ) {
                        if (isDevSender) {
                            VerifiedBadge(modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "DEV USER",
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFF1DA1F2)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        if (!customTitle.isNullOrEmpty()) {
                            val titleGradients = listOf(
                                Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF00B0FF))), // Emerald Wave
                                Brush.horizontalGradient(listOf(Color(0xFF8E24AA), Color(0xFFD81B60))), // Fuchsia Flame
                                Brush.horizontalGradient(listOf(Color(0xFFFF8F00), Color(0xFFFF3D00))), // Sunset Gold
                                Brush.horizontalGradient(listOf(Color(0xFF2979FF), Color(0xFF00E5FF))), // Blue Laser
                                Brush.horizontalGradient(listOf(Color(0xFF37474F), Color(0xFF78909C)))  // Slate Armor
                            )
                            val selectedBrush = titleGradients.getOrElse(titleBgColorIdx) { titleGradients[0] }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(selectedBrush)
                                    .padding(horizontal = 4.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = customTitle.uppercase(),
                                    fontSize = 7.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }

                if (message.replyToText != null) {
                    Row(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.08f), RoundedCornerShape(4.dp))
                            .padding(6.dp)
                    ) {
                        Box(modifier = Modifier.width(3.dp).fillMaxHeight().background(EmeraldPrimaryLight))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = message.replyToText, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }

                when (message.type) {
                    MessageType.IMAGE.name -> {
                        AsyncImage(
                            model = message.mediaUrl,
                            contentDescription = "Shared image",
                            modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        if (message.content.isNotEmpty()) {
                            Text(text = message.content, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                    MessageType.DOCUMENT.name -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.05f)).padding(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Description, contentDescription = "Doc", tint = EmeraldPrimaryLight)
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Secure_Key_Vault.pdf", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text("10.2 KB • Downloaded", fontSize = 11.sp)
                            }
                        }
                    }
                    MessageType.VOICE.name -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Play")
                            LinearProgressIndicator(progress = 0.45f, modifier = Modifier.weight(1f).height(4.dp).padding(horizontal = 8.dp))
                            Text("0:08", fontSize = 11.sp)
                        }
                    }
                    else -> {
                        Text(
                            text = message.content,
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.End).padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (message.isEdited) {
                        Text(
                            text = "edited • ",
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                        )
                    }

                    val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
                    Text(
                        text = time,
                        fontSize = 10.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )

                    if (isMe) {
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = readMarker,
                            fontSize = 12.sp,
                            color = readColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                if (message.reactions.isNotEmpty()) {
                    Row(
                        modifier = Modifier.padding(top = 4.dp).align(Alignment.Start)
                    ) {
                        message.reactions.split(",").forEach { react ->
                            Card(
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    text = react,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    text: String,
    onTextChanged: (String) -> Unit,
    editMode: Boolean,
    onSend: () -> Unit,
    onAttachClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onAttachClick) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = "Attachment",
                tint = MaterialTheme.colorScheme.primary
            )
        }

        OutlinedTextField(
            value = text,
            onValueChange = onTextChanged,
            placeholder = { Text(if (editMode) "Edit secure message..." else "Secure encrypted message...") },
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier.weight(1f).testTag("chat_input"),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent
            ),
            trailingIcon = {
                IconButton(onClick = onSend) {
                    Icon(
                        imageVector = if (editMode) Icons.Default.Check else Icons.AutoMirrored.Filled.Send,
                        contentDescription = "Send",
                        tint = EmeraldPrimaryDark
                    )
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallHistoryScreen(viewModel: CallViewModel) {
    val callHistory by viewModel.callLogs.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call Logs", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewModel.clearLog() }) {
                        Icon(imageVector = Icons.Default.DeleteOutline, contentDescription = "Clear Session History")
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            if (callHistory.isEmpty()) {
                EmptyPlaceholder(Icons.Default.Call, "No historical call logs found.")
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(callHistory) { record ->
                        val isIncoming = record.receiverId == "me"
                        val callIcon = if (record.isVideo) Icons.Default.Videocam else Icons.Default.Call
                        val tint = if (record.status == "MISSED") RedAccent else EmeraldPrimaryLight
                        val label = if (record.status == "MISSED") "Missed call" else "Completed (${record.duration}s)"

                        Row(
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarComponent(name = if (isIncoming) record.callerName else record.receiverName, url = if (isIncoming) record.callerAvatar else record.receiverAvatar, modifier = Modifier.size(48.dp))
                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (isIncoming) record.callerName else record.receiverName,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isIncoming) Icons.Default.CallReceived else Icons.Default.CallMade,
                                        contentDescription = "Direction",
                                        tint = tint,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(label, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }

                            IconButton(onClick = { viewModel.deleteCall(record.callId) }) {
                                Icon(imageVector = callIcon, contentDescription = "Call", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ActiveCallScreen(viewModel: CallViewModel, onHangUp: () -> Unit) {
    val activeCall by viewModel.activeCall.collectAsState()
    val duration by viewModel.callDurationSeconds.collectAsState()

    val formattedDuration = String.format("%02d:%02d", duration / 60, duration % 60)
    val callText = if (activeCall?.isVideo == true) "Secure Video Call" else "Secure Voice Call"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(EmeraldBackgroundDark)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 48.dp)
        ) {
            Text(
                activeCall?.receiverName ?: "Peer Connection",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(callText, fontSize = 14.sp, color = EmeraldPrimaryDark)
            Spacer(modifier = Modifier.height(6.dp))
            Text(formattedDuration, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(color = EmeraldPrimaryDark, radius = size.minDimension / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
            }
            AvatarComponent(name = activeCall?.receiverName ?: "?", url = activeCall?.receiverAvatar, modifier = Modifier.size(120.dp))
        }

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)
        ) {
            FloatingActionButton(
                onClick = onHangUp,
                containerColor = RedAccent,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp).testTag("hangup_button")
            ) {
                Icon(imageVector = Icons.Default.CallEnd, contentDescription = "Hang Up")
            }
        }
    }
}

@Composable
fun IncomingCallScreen(callViewModel: CallViewModel, onAccept: () -> Unit, onReject: () -> Unit) {
    val incomingCall by callViewModel.incomingCall.collectAsState()

    Column(
        modifier = Modifier.fillMaxSize().background(EmeraldBackgroundDark).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(top = 64.dp)) {
            Text("Incoming encrypted call...", color = EmeraldPrimaryLight, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Text(incomingCall?.callerName ?: "Unknown Contact", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = Color.White)
        }

        AvatarComponent(name = incomingCall?.callerName ?: "?", url = incomingCall?.callerAvatar, modifier = Modifier.size(130.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 48.dp),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            FloatingActionButton(
                onClick = onReject,
                containerColor = RedAccent,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp).testTag("reject_button")
            ) {
                Icon(imageVector = Icons.Default.CallEnd, contentDescription = "Reject")
            }

            FloatingActionButton(
                onClick = onAccept,
                containerColor = EmeraldPrimaryLight,
                contentColor = Color.White,
                shape = CircleShape,
                modifier = Modifier.size(56.dp).testTag("accept_button")
            ) {
                Icon(imageVector = Icons.Default.Call, contentDescription = "Accept")
            }
        }
    }
}

@Composable
fun StatusTabScreen(viewModel: StatusViewModel, onStatusClick: (Int) -> Unit) {
    val statuses by viewModel.activeStatuses.collectAsState()
    val isUploading by viewModel.isUploading.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            onClick = {
                val sampleStatusImages = listOf(
                    "https://images.unsplash.com/photo-1507525428034-b723cf961d3e?q=80&w=400",
                    "https://images.unsplash.com/photo-1470071459604-3b5ec3a7fe05?q=80&w=400"
                )
                viewModel.uploadStatus("Secure snapshot! 📱✨", sampleStatusImages.random())
            }
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(contentAlignment = Alignment.BottomEnd, modifier = Modifier.size(54.dp)) {
                    AvatarComponent(name = "You", url = null, modifier = Modifier.size(48.dp))
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(EmeraldPrimaryLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = Icons.Default.Add, contentDescription = "Add status", tint = Color.White, modifier = Modifier.size(14.dp))
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))

                Column {
                    Text("My Status Updates", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text(
                        if (isUploading) "Uploading status file..." else "Tap here to share a dynamic secure 1-day status story!",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))

        Text(
            text = "RECENT STATUS REVEALS",
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
        )

        if (statuses.isEmpty()) {
            EmptyPlaceholder(Icons.Default.HourglassEmpty, "No statuses recently shared around here (expires in 24h).")
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(statuses.size) { index ->
                    val status = statuses[index]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onStatusClick(index) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(54.dp)) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(color = GoldAccent, radius = size.minDimension / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(4f))
                            }
                            AvatarComponent(name = status.userName, url = status.userAvatar, modifier = Modifier.size(44.dp))
                        }
                        Spacer(modifier = Modifier.width(14.dp))

                        Column {
                            Text(status.userName, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            val relativeTime = SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(status.timestamp))
                            Text(relativeTime, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusViewerScreen(statuses: List<StatusEntity>, startIndex: Int, onDismiss: () -> Unit) {
    var currentIndex by remember { mutableStateOf(startIndex) }
    val active = statuses[currentIndex]

    BackHandler(onBack = onDismiss)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(onClick = {
                if (currentIndex + 1 < statuses.size) {
                    currentIndex += 1
                } else {
                    onDismiss()
                }
            })
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            statuses.indices.forEach { idx ->
                val progress = when {
                    idx < currentIndex -> 1f
                    idx == currentIndex -> 1f
                    else -> 0f
                }
                LinearProgressIndicator(
                    progress = progress,
                    color = EmeraldPrimaryLight,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier.weight(1f).height(3.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AsyncImage(
            model = active.mediaUrl,
            contentDescription = "Status",
            contentScale = ContentScale.Fit,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        Column(
            modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                active.caption,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(16.dp)
            )

            Text(
                "by ${active.userName} • Tap to proceed",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncryptionVerifyScreen(chatId: String, chatViewModel: ChatViewModel, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var contactName by remember { mutableStateOf("Secure Contact") }
    var phoneNumber by remember { mutableStateOf("+1 555-0101") }

    LaunchedEffect(chatId) {
        chatViewModel.repository.getChatById(chatId)?.let {
            contactName = it.name
        }
        val db = com.example.data.local.AppDatabase.getDatabase(context.applicationContext)
        db.contactDao().getContactById(chatId)?.let {
            phoneNumber = it.phoneNumber
        }
    }

    val fingerprint = SignalEncryption.generateSecurityNumber("me", chatId)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify Safety Keys", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Return")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "Verification code matches cryptographic fingerprint key signatures between you and $contactName.",
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
            )

            Box(
                modifier = Modifier
                    .size(180.dp)
                    .background(Color.White)
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    drawRect(color = Color.Black, size = androidx.compose.ui.geometry.Size(40f, 40f))
                    drawRect(color = Color.Black, topLeft = androidx.compose.ui.geometry.Offset(size.width - 40f, 0f), size = androidx.compose.ui.geometry.Size(40f, 40f))
                    drawRect(color = Color.Black, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - 40f), size = androidx.compose.ui.geometry.Size(40f, 40f))

                    for (i in 0..120 step 15) {
                        drawLine(color = Color.Black, start = androidx.compose.ui.geometry.Offset(60f + i, 60f), end = androidx.compose.ui.geometry.Offset(60f + i, size.height - 60f), strokeWidth = 5f)
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = fingerprint,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                color = EmeraldPrimaryLight,
                modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant).padding(16.dp)
            )

            Text(
                text = "To verify E2EE layers, scan the QR code above or compare these 60 numbers with recipients.",
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        }
    }
}

@Composable
fun SettingsScreen(viewModel: SettingsViewModel, callViewModel: CallViewModel, authViewModel: AuthViewModel) {
    val isDark by viewModel.isDarkMode.collectAsState()
    val receipts by viewModel.privacyReadReceipts.collectAsState()
    val notificationEnabled by viewModel.notificationEnabled.collectAsState()
    val blocked by viewModel.blockedContacts.collectAsState()
    val profile by authViewModel.userProfile.collectAsState()
    val selectedGradientIndex by authViewModel.profileGradientIndex.collectAsState()

    var showBlockSelector by remember { mutableStateOf(false) }
    var showProfileEditor by remember { mutableStateOf(false) }

    val profileGradients = remember {
        listOf(
            Brush.horizontalGradient(listOf(Color(0xFF00B0FF), Color(0xFF00E5FF))), // Cyber Wave
            Brush.horizontalGradient(listOf(Color(0xFF8E24AA), Color(0xFFD81B60))), // Neon Amethyst / Fuchsia
            Brush.horizontalGradient(listOf(Color(0xFF00695C), Color(0xFF00E676))), // Classic Emerald
            Brush.horizontalGradient(listOf(Color(0xFF1565C0), Color(0xFF00E5FF))), // Deep Sapphire
            Brush.horizontalGradient(listOf(Color(0xFFD84315), Color(0xFFFF8F00))), // Sunset Blaze
            Brush.horizontalGradient(listOf(Color(0xFF37474F), Color(0xFF90A4AE)))  // Carbon Shield
        )
    }
    val profileGradientNames = remember {
        listOf("Cyber Wave", "Electric Fuchsia", "Classic Emerald", "Deep Sapphire", "Sunset Blaze", "Carbon Shield")
    }
    val preSelectedAvatars = remember {
        listOf(
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?w=150", 
            "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?w=150", 
            "https://images.unsplash.com/photo-1494790108377-be9c29b29330?w=150", 
            "https://images.unsplash.com/photo-1500648767791-00dcc994a43e?w=150", 
            "https://images.unsplash.com/photo-1618005182384-a83a8bd57fbe?w=150", 
            "https://images.unsplash.com/photo-1579783902614-a3fb3927b6a5?w=150"  
        )
    }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        item {
            Text("Emerald Configuration", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = EmeraldPrimaryLight)
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Modern Visual Interactive Profile Card
        item {
            Card(
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp)
                    .clickable { showProfileEditor = true }
                    .testTag("profile_appearance_card"),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(profileGradients[selectedGradientIndex])
                        .padding(24.dp)
                ) {
                    Column {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(72.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                AvatarComponent(
                                    name = profile.name,
                                    url = profile.avatarUrl,
                                    modifier = Modifier.size(68.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = profile.name,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 20.sp,
                                        color = Color.White
                                    )
                                    if (profile.isDeveloper) {
                                        Spacer(modifier = Modifier.width(6.dp))
                                        VerifiedBadge(modifier = Modifier.size(18.dp))
                                    }
                                }
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = profile.phoneNumber,
                                    fontSize = 12.sp,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontFamily = FontFamily.Monospace
                                )
                                val customTitle = profile.customTitle
                                if (profile.isPremium && !customTitle.isNullOrEmpty()) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    val titleGradients = listOf(
                                        Brush.horizontalGradient(listOf(Color(0xFF00E676), Color(0xFF00B0FF))), // Emerald Wave
                                        Brush.horizontalGradient(listOf(Color(0xFF8E24AA), Color(0xFFD81B60))), // Fuchsia Flame
                                        Brush.horizontalGradient(listOf(Color(0xFFFF8F00), Color(0xFFFF3D00))), // Sunset Gold
                                        Brush.horizontalGradient(listOf(Color(0xFF2979FF), Color(0xFF00E5FF))), // Blue Laser
                                        Brush.horizontalGradient(listOf(Color(0xFF37474F), Color(0xFF78909C)))  // Slate Armor
                                    )
                                    val selectedBrush = titleGradients.getOrElse(profile.titleBgColorIndex) { titleGradients[0] }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(selectedBrush)
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = Icons.Default.Star,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(10.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                text = customTitle.uppercase(),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                color = Color.White,
                                                letterSpacing = 0.5.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Info,
                                        contentDescription = "Bio icon",
                                        tint = Color.White.copy(alpha = 0.8f),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "STATUS BIO",
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp,
                                        color = Color.White.copy(alpha = 0.7f)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = profile.bio,
                                    fontSize = 13.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = profileGradientNames[selectedGradientIndex].uppercase(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = "Edit looks",
                                    tint = Color.White,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Edit looks & profile",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- DEVELOPER TERM & PREMIUM CONTROLS ---
        if (profile.isDeveloper) {
            item {
                var newCodeInput by remember { mutableStateOf("") }
                var useLimitInput by remember { mutableStateOf("5") }
                val createdCodes by authViewModel.developerCodes.collectAsState()
                var createError by remember { mutableStateOf("") }

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Build, contentDescription = null, tint = EmeraldPrimaryDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("🛠️ Developer Sandbox Token Console", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.tertiary)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Forge Premium Activation Passcode:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = newCodeInput,
                                onValueChange = { 
                                    newCodeInput = it
                                    createError = ""
                                },
                                label = { Text("Code Text (CAPS)") },
                                placeholder = { Text("VIP99") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedTextField(
                                value = useLimitInput,
                                onValueChange = { useLimitInput = it },
                                label = { Text("Limit") },
                                placeholder = { Text("5") },
                                singleLine = true,
                                modifier = Modifier.width(70.dp)
                            )
                        }
                        
                        if (createError.isNotEmpty()) {
                            Text(createError, color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = {
                                val limit = useLimitInput.trim().toIntOrNull() ?: 5
                                if (newCodeInput.isBlank()) {
                                    createError = "Code cannot be empty"
                                } else {
                                    val success = authViewModel.createDeveloperCode(newCodeInput, limit)
                                    if (success) {
                                        viewModel.triggerInAppToast("Signature Code '${newCodeInput.uppercase()}' generated!")
                                        newCodeInput = ""
                                        useLimitInput = "5"
                                        createError = ""
                                    } else {
                                        createError = "Code signature already exists"
                                    }
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Forge Activation Signature")
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Live Signature Keys Directory:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        if (createdCodes.isEmpty()) {
                            Text("No signature codes registered.", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                        } else {
                            createdCodes.forEach { devCode ->
                                val isExhausted = devCode.currentUses >= devCode.limit
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .background(Color.Black.copy(alpha = 0.05f), RoundedCornerShape(6.dp))
                                        .padding(8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(devCode.code, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = if (isExhausted) Color.Gray else EmeraldPrimaryLight)
                                        Text("Limit: ${devCode.limit} activations total", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(if (isExhausted) Color.Red.copy(alpha = 0.15f) else Color.Green.copy(alpha = 0.15f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = "${devCode.currentUses} / ${devCode.limit} USED",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isExhausted) Color.Red else Color.Green
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (!profile.isPremium) {
            item {
                var unlockCodeInput by remember { mutableStateOf("") }
                var unlockError by remember { mutableStateOf(false) }
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, tint = EmeraldPrimaryDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Unlock Elite Premium Suite 👑", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Enter an activation signature key (created by developers inside the Sandbox, or default seed e.g. EMERALD777) to immediately unlock visual modifications, custom chat backdrops, and react emojis.",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = unlockCodeInput,
                                onValueChange = { 
                                    unlockCodeInput = it
                                    unlockError = false 
                                },
                                label = { Text("Activation Key Signature") },
                                placeholder = { Text("e.g. EMERALD777") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    val success = authViewModel.attemptUnlockPremium(unlockCodeInput)
                                    if (success) {
                                        viewModel.triggerInAppToast("Elite Premium unlocked! Customise looks below. 👑")
                                        unlockCodeInput = ""
                                    } else {
                                        unlockError = true
                                    }
                                },
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Decrypt")
                            }
                        }
                        if (unlockError) {
                            Text("Invalid or fully exhausted key signature. Forge a new one in Dev Sandbox.", color = Color.Red, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }

        if (profile.isPremium) {
            item {
                var customTitleInput by remember { mutableStateOf(profile.customTitle ?: "") }
                var customBgUrlInput by remember { mutableStateOf(profile.customChatBgUrl ?: "") }
                
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.WorkspacePremium, contentDescription = null, tint = EmeraldPrimaryDark)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("👑 Elite Identity Visual Studio", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        // 1. Title setup
                        Text("Custom Profile Title Aura Text", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        OutlinedTextField(
                            value = customTitleInput,
                            onValueChange = { 
                                customTitleInput = it
                                authViewModel.updatePremiumTitle(it, profile.titleBgColorIndex)
                            },
                            placeholder = { Text("e.g. handsome, elite, architect") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))

                        // Gradient selector
                        Text("Title Aura Gradient Shade", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        val availableGradients = listOf("Emerald Wave", "Fuchsia Flame", "Sunset Gold", "Blue Laser", "Slate Armor")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            availableGradients.forEachIndexed { idx, name ->
                                val isSelected = profile.titleBgColorIndex == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.05f))
                                        .clickable { authViewModel.updatePremiumTitle(customTitleInput, idx) }
                                        .padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = name,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                        Spacer(modifier = Modifier.height(16.dp))

                        // 2. Chat backdrop selection
                        Text("Premium Chat Backdrop Shader", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                        val backdrops = listOf("Classic", "Emerald", "Sunset", "Cyber", "Midnight", "URL Backdrop 🖼️")
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                        ) {
                            backdrops.forEachIndexed { idx, name ->
                                val isSelected = profile.chatBgIndex == idx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.05f))
                                        .clickable { authViewModel.updateChatBackground(idx, customBgUrlInput) }
                                        .padding(horizontal = 8.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = name,
                                        color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        if (profile.chatBgIndex == 5) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("Direct Link to Custom Background Landscape Image (https://...)", fontWeight = FontWeight.Medium, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = customBgUrlInput,
                                onValueChange = { 
                                    customBgUrlInput = it
                                    authViewModel.updateChatBackground(5, it)
                                },
                                placeholder = { Text("e.g. https://images.unsplash.com/photo-1507525428034-b723cf961d3e?w=800") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("✨ Modern Emojis Active! React options fully expanded: 😎 👑 🪐 ⚡ 💎 🦄", fontSize = 11.sp, color = EmeraldPrimaryDark, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(8.dp))
        }

        item {
            ListItem(
                headlineContent = { Text("Midnight Theme / Dark Mode") },
                supportingContent = { Text("Toggles secure AMOLED midnight style layouts") },
                trailingContent = {
                    Switch(checked = isDark, onCheckedChange = {
                        viewModel.toggleTheme()
                        viewModel.triggerInAppToast("Midnight Theme updated!")
                    })
                }
            )
            HorizontalDivider()
        }

        item {
            ListItem(
                headlineContent = { Text("E2EE Verification receipts") },
                supportingContent = { Text("Block or allow other peers to observe if you read messages") },
                trailingContent = {
                    Switch(checked = receipts, onCheckedChange = { viewModel.toggleReadReceipts() })
                }
            )
            HorizontalDivider()
        }

        item {
            ListItem(
                headlineContent = { Text("Simulated Push Notifications") },
                supportingContent = { Text("In-app active toast preview banners alert toggles") },
                trailingContent = {
                    Switch(checked = notificationEnabled, onCheckedChange = { viewModel.toggleNotifications() })
                }
            )
            HorizontalDivider()
        }

        item {
            ListItem(
                headlineContent = { Text("Blocked Contacts") },
                supportingContent = { Text("${blocked.size} currently restricted user profile directories") },
                trailingContent = {
                    IconButton(onClick = { showBlockSelector = true }) {
                        Icon(imageVector = Icons.Default.Block, contentDescription = "Open directory", tint = RedAccent)
                    }
                }
            )
            HorizontalDivider()
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text("SECURE CLOUD BACKEND SCHEMAS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldPrimaryDark)
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Firestore Structured database mappings details:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("• /users/{userId}: { name, phone, bio, avatar, e2ee_pub_key }", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("• /chats/{chatId}: { isGroup, admins:[], last_msg, last_time }", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    Text("• /chats/{chatId}/messages/{msgId}: { senderKey, aes_payload_blob, timestamp, status }", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
            Text("TEST BENCH SIMULATORS", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = EmeraldPrimaryDark)
            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = {
                    callViewModel.triggerSimulatedIncomingCall("alice", "Alice Vance", "https://images.unsplash.com/photo-1494790108377-be9c29b29330?q=80&w=150", isVideo = false)
                },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Simulate Incoming Call")
            }

            Button(
                onClick = {
                    viewModel.triggerInAppToast("Alice Vance: Let's catch up later today soon! 📬")
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Simulate Incoming Message Alert")
            }
        }
    }

    if (showBlockSelector) {
        BlockListSelectorDialog(
            viewModel = viewModel,
            onDismiss = { showBlockSelector = false }
        )
    }

    if (showProfileEditor) {
        var editName by remember { mutableStateOf(profile.name) }
        var editBio by remember { mutableStateOf(profile.bio) }
        var editAvatarUrl by remember { mutableStateOf(profile.avatarUrl ?: "") }
        var tempGradientIndex by remember { mutableStateOf(selectedGradientIndex) }

        Dialog(onDismissRequest = { showProfileEditor = false }) {
            Card(
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
                    .testTag("profile_editor_dialog")
            ) {
                LazyColumn(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    item {
                        Text(
                            text = "Aesthetic Identity Look",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Personalize your public profile visual elements",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                    }

                    item {
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 16.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(profileGradients[tempGradientIndex])
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    AvatarComponent(
                                        name = editName.ifEmpty { "Preview" },
                                        url = editAvatarUrl,
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = editName.ifEmpty { "Name Preview" },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            color = Color.White
                                        )
                                        Text(
                                            text = editBio.ifEmpty { "Status Bio preview..." },
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.8f),
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }

                    item {
                        OutlinedTextField(
                            value = editName,
                            onValueChange = { editName = it },
                            label = { Text("Display Name") },
                            placeholder = { Text("Enter your name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp).testTag("edit_profile_name")
                        )

                        OutlinedTextField(
                            value = editBio,
                            onValueChange = { editBio = it },
                            label = { Text("Status Quote Bio") },
                            placeholder = { Text("Hey there!") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("edit_profile_bio")
                        )
                    }

                    item {
                        Text(
                            text = "Select Avatar Portrait",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )

                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                        ) {
                            items(preSelectedAvatars.size) { index ->
                                val avatarUrl = preSelectedAvatars[index]
                                val isSelected = editAvatarUrl == avatarUrl
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                        .clickable { editAvatarUrl = avatarUrl }
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    AvatarComponent(
                                        name = "Avatar",
                                        url = avatarUrl,
                                        modifier = Modifier.size(48.dp)
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = editAvatarUrl,
                            onValueChange = { editAvatarUrl = it },
                            label = { Text("Or Custom Avatar Image URL") },
                            placeholder = { Text("https://...") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp).testTag("edit_profile_avatar_url")
                        )
                    }

                    item {
                        Text(
                            text = "Select Premium Backdrop Theme",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        )

                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 20.dp)
                        ) {
                            items(profileGradients.size) { idx ->
                                val isSelected = tempGradientIndex == idx
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(profileGradients[idx])
                                        .clickable { tempGradientIndex = idx }
                                        .padding(if (isSelected) 4.dp else 0.dp)
                                ) {
                                    if (isSelected) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(6.dp)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Check,
                                                contentDescription = "Selected",
                                                tint = Color.White,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            TextButton(onClick = { showProfileEditor = false }) {
                                Text("Cancel")
                            }

                            Button(
                                onClick = {
                                    if (editName.isNotEmpty()) {
                                        authViewModel.updateProfile(
                                            name = editName,
                                            bio = editBio,
                                            avatarUrl = if (editAvatarUrl.isNotEmpty()) editAvatarUrl else null,
                                            gradientIndex = tempGradientIndex
                                        )
                                        viewModel.triggerInAppToast("Signature Appearance updated!")
                                        showProfileEditor = false
                                    }
                                },
                                enabled = editName.isNotEmpty(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Apply Visuals")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BlockListSelectorDialog(viewModel: SettingsViewModel, onDismiss: () -> Unit) {
    val blockedByTheme by viewModel.blockedContacts.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Blocked Contacts Directories", fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(bottom = 12.dp))

                if (blockedByTheme.isEmpty()) {
                    Text("No blocked files directory matching standard limits found.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                        items(blockedByTheme) { blockedItem ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(blockedItem.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Button(
                                    onClick = { viewModel.toggleBlock(blockedItem.contactId) },
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldPrimaryLight),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Unblock", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Close")
                }
            }
        }
    }
}

@Composable
fun NewChatBottomSheet(
    chatViewModel: ChatViewModel,
    settingsViewModel: SettingsViewModel,
    onDismiss: () -> Unit
) {
    val contacts by chatViewModel.contacts.collectAsState()
    var isCreatingGroup by remember { mutableStateOf(false) }

    var groupName by remember { mutableStateOf("") }
    var groupDesc by remember { mutableStateOf("") }
    val selectedMembers = remember { mutableStateListOf<String>() }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("new_chat_sheet")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        if (isCreatingGroup) "Create secure Group" else "New Secure Conversation",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    TextButton(onClick = { isCreatingGroup = !isCreatingGroup }) {
                        Text(if (isCreatingGroup) "One-to-One" else "Create Group")
                    }
                }

                if (isCreatingGroup) {
                    OutlinedTextField(
                        value = groupName,
                        onValueChange = { groupName = it },
                        label = { Text("Group Title") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    )
                    OutlinedTextField(
                        value = groupDesc,
                        onValueChange = { groupDesc = it },
                        label = { Text("Group Bio Description") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                    )

                    Text("Select Group Members:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    LazyColumn(modifier = Modifier.height(140.dp)) {
                        items(contacts) { person ->
                            val isChecked = selectedMembers.contains(person.contactId)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) selectedMembers.remove(person.contactId)
                                        else selectedMembers.add(person.contactId)
                                    }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(checked = isChecked, onCheckedChange = {
                                    if (isChecked) selectedMembers.remove(person.contactId)
                                    else selectedMembers.add(person.contactId)
                                })
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(person.name)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = {
                            if (groupName.isNotEmpty() && selectedMembers.isNotEmpty()) {
                                chatViewModel.createGroup(groupName, groupDesc, selectedMembers.toList())
                                settingsViewModel.triggerInAppToast("E2EE Group Securely Formed!")
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = groupName.isNotEmpty() && selectedMembers.isNotEmpty()
                    ) {
                        Text("Establish Group")
                    }
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(contacts) { person ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        chatViewModel.setActiveChat(person.contactId)
                                        onDismiss()
                                    }
                                    .padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                AvatarComponent(name = person.name, url = person.avatarUrl, modifier = Modifier.size(40.dp))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(person.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(person.phoneNumber, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ForwardBottomSheet(
    chatViewModel: ChatViewModel,
    onForward: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val chats by chatViewModel.chats.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("forward_sheet")
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Forward Message to:", fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(bottom = 12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                    items(chats) { chat ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onForward(chat.chatId) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AvatarComponent(name = chat.name, url = chat.avatarUrl, modifier = Modifier.size(38.dp))
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(chat.name, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EmptyPlaceholder(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = "Empty",
            tint = EmeraldPrimaryLight.copy(alpha = 0.35f),
            modifier = Modifier.size(72.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = text,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
        )
    }
}
