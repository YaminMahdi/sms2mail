package com.kit.sms2mail.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.kit.sms2mail.model.UserInfo
import com.kit.sms2mail.ui.components.AppBar
import com.kit.sms2mail.ui.components.EmptyListPlaceholder
import com.kit.sms2mail.ui.components.UserInfoCard
import com.kit.sms2mail.ui.theme.Sms2MailTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    userInfo: UserInfo,
    forwardFromList: List<String>,
    emailList: List<String>,
    serviceStatus: Boolean,
    onRemoveForward: (String) -> Unit,
    onRemoveEmail: (String) -> Unit,
    onAddFromSms: () -> Unit,
    onAddFromContact: () -> Unit,
    onAddEmail: () -> Unit,
    onServiceStatusChange: (Boolean) -> Unit,
    onLogoutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Top App Bar
        AppBar(
            title = "SMS2Mail",
            serviceStatus = serviceStatus,
            onServiceStatusChange = onServiceStatusChange,
            onLogoutClick = onLogoutClick
        )

        // Two sections split 50/50
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Forward from Contact List section
            UserInfoCard(userInfo = userInfo)
            ForwardListSection(
                modifier = Modifier.weight(1f),
                forwardFromList = forwardFromList,
                onRemove = onRemoveForward,
                onAddFromSms = onAddFromSms,
                onAddFromContact = onAddFromContact
            )

            // Send to Mail List section
            EmailListSection(
                modifier = Modifier.weight(1f),
                emailList = emailList,
                onRemove = onRemoveEmail,
                onAddEmail = onAddEmail
            )
        }
    }
}

@Composable
private fun ForwardListSection(
    modifier: Modifier = Modifier,
    forwardFromList: List<String>,
    onRemove: (String) -> Unit,
    onAddFromSms: () -> Unit,
    onAddFromContact: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Section Header
            Text(
                text = "Forward from Contact List",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Add Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Add from SMS button
                FilledTonalButton(
                    onClick = onAddFromSms,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Sms,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Add from SMS",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }

                // Add from Contact button
                FilledTonalButton(
                    onClick = onAddFromContact,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Contacts,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        "Add from Contact",
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1
                    )
                }
            }

            // Forward List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(forwardFromList, key = { it }) { item ->
                    ForwardListItem(
                        text = item,
                        onRemove = { onRemove(item) }
                    )
                }

                if (forwardFromList.isEmpty()) {
                    item {
                        EmptyListPlaceholder(
                            text = "No contacts added",
                            modifier = Modifier.fillParentMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ForwardListItem(
    text: String,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun EmailListSection(
    modifier: Modifier = Modifier,
    emailList: List<String>,
    onRemove: (String) -> Unit,
    onAddEmail: () -> Unit
) {
    Card(
        modifier = modifier.fillMaxHeight(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer
        ),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Section Header
            Text(
                text = "Send to Mail List",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Add Email Button
            FilledTonalButton(
                onClick = onAddEmail,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Email")
            }

            // Email List
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(emailList, key = { it }) { email ->
                    EmailListItem(
                        email = email,
                        onRemove = { onRemove(email) }
                    )
                }

                if (emailList.isEmpty()) {
                    item {
                        EmptyListPlaceholder(
                            text = "No emails added",
                            modifier = Modifier.fillParentMaxSize()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EmailListItem(
    email: String,
    onRemove: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Preview
@Composable
private fun HomeScreenPrev() {
    Sms2MailTheme {
        HomeScreen(
            userInfo = UserInfo(
                email = "william.paterson@domain.com",
                name = "John Doe",
                phone = "+1 234 567 8900",
            ),
            forwardFromList = listOf("0345345", "534654654"),
            emailList = listOf("afbh@gf.com", "yk@gf.com"),
            serviceStatus = true,
            onServiceStatusChange = {},
            onLogoutClick = {},
            onRemoveForward = {},
            onRemoveEmail = {},
            onAddFromSms = {},
            onAddFromContact = {},
            onAddEmail = {},
        )
    }

}
