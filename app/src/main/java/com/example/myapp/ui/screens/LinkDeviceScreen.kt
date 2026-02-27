package com.example.myapp.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.myapp.ui.theme.GreenPrimary
import com.example.myapp.ui.theme.GreenPrimaryDark
import com.example.myapp.ui.theme.GreenPrimaryLight
import com.example.myapp.ui.theme.GreenSurface
import com.example.myapp.ui.theme.IPETheme

@Composable
fun LinkDeviceScreen(
    navController: NavController,
    onLinkDevice: (String) -> Unit,
    onScanQR: () -> Unit,
    onBack: () -> Unit
) {
    val scrollState = rememberScrollState()
    val focusManager = LocalFocusManager.current
    var deviceCode by remember { mutableStateOf("") }
    
    // Get scanned QR code from navigation's SavedStateHandle
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    
    // Use DisposableEffect or LaunchedEffect to observe the savedStateHandle
    LaunchedEffect(navBackStackEntry) {
        val savedStateHandle = navBackStackEntry?.savedStateHandle
        val scannedCode = savedStateHandle?.get<String>("scannedCode")
        if (!scannedCode.isNullOrEmpty()) {
            deviceCode = scannedCode
            // Clear the code after consuming it
            savedStateHandle.remove<String>("scannedCode")
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(scrollState)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 16.dp, top = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = GreenPrimary
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "Connect to Parent",
                fontSize = 18.sp,
                fontWeight = FontWeight.SemiBold,
                color = GreenPrimaryDark
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Icon
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                GreenPrimaryLight.copy(alpha = 0.2f),
                                GreenPrimary.copy(alpha = 0.05f)
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(GreenPrimary, GreenPrimaryDark)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = "Link Device",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Title
            Text(
                text = "Connect to Parent",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = GreenPrimaryDark,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Subtitle
            Text(
                text = "Enter the Parent Code from the parent's device to link this device",
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Pairing Code Input with better styling
            OutlinedTextField(
                value = deviceCode,
                onValueChange = { 
                    if (it.length <= 6) deviceCode = it 
                },
                label = { Text("Pairing Code") },
                placeholder = { Text("Enter 6-digit code") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp),
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GreenPrimary,
                    unfocusedBorderColor = Color.LightGray,
                    focusedLabelColor = GreenPrimary,
                    cursorColor = GreenPrimary,
                    focusedContainerColor = GreenSurface.copy(alpha = 0.3f),
                    unfocusedContainerColor = Color.White
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        focusManager.clearFocus()
                        if (deviceCode.length == 6) onLinkDevice(deviceCode)
                    }
                ),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Link Button
            Button(
                onClick = { onLinkDevice(deviceCode) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = GreenPrimary,
                    disabledContainerColor = GreenPrimary.copy(alpha = 0.4f)
                ),
                enabled = deviceCode.length == 6,
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 3.dp,
                    pressedElevation = 6.dp
                )
            ) {
                Text(
                    text = "Link Device",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Divider with OR
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 1.dp,
                    color = Color.LightGray
                )
                Text(
                    text = "OR",
                    color = Color.Gray,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                HorizontalDivider(
                    modifier = Modifier.weight(1f),
                    thickness = 1.dp,
                    color = Color.LightGray
                )
            }

            Spacer(modifier = Modifier.height(28.dp))

            // Scan QR Button
            OutlinedButton(
                onClick = onScanQR,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(26.dp),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = GreenPrimary
                ),
                border = BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(GreenPrimary, GreenPrimaryLight)
                    )
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.QrCodeScanner,
                    contentDescription = "Scan QR",
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Scan QR Code",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            // Help Text
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = GreenSurface
                )
            ) {
                Text(
                    text = "💡 Ask your parent for the code shown on their app",
                    fontSize = 13.sp,
                    color = GreenPrimaryDark,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Preview(showBackground = true)
@Composable
fun LinkDeviceScreenPreview() {
    IPETheme {
        LinkDeviceScreen(
            navController = rememberNavController(),
            onLinkDevice = {},
            onScanQR = {},
            onBack = {}
        )
    }
}
