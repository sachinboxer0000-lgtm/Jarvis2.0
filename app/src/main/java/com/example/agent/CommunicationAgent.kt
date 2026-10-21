package com.example.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ContactMatch(
    val name: String,
    val phoneNumber: String
)

class CommunicationAgent(private val context: Context) {

    fun hasContactPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun searchContacts(query: String): List<ContactMatch> = withContext(Dispatchers.IO) {
        if (!hasContactPermission()) return@withContext emptyList()

        val results = mutableListOf<ContactMatch>()
        val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )
        val selection = "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ?"
        val selectionArgs = arrayOf("%$query%")

        try {
            context.contentResolver.query(uri, projection, selection, selectionArgs, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (cursor.moveToNext() && results.size < 5) {
                    val name = cursor.getString(nameIndex) ?: ""
                    val number = cursor.getString(numberIndex) ?: ""
                    results.add(ContactMatch(name, number))
                }
            }
        } catch (_: Exception) {
            // Gracefully handled if permission denied or query fails
        }
        results
    }

    /**
     * Prepares and opens SMS app with recipient and message pre-populated,
     * or sends directly if SMS permission is granted and user explicitly confirmed.
     */
    fun openMessagingApp(recipient: String, message: String): Result<String> {
        return try {
            val cleanNumber = recipient.replace(Regex("[^0-9+]"), "")
            val uri = if (cleanNumber.isNotEmpty()) {
                Uri.parse("smsto:$cleanNumber")
            } else {
                Uri.parse("smsto:")
            }

            val intent = Intent(Intent.ACTION_SENDTO, uri).apply {
                putExtra("sms_body", message)
                putExtra(Intent.EXTRA_TEXT, message)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            context.startActivity(intent)
            Result.success("Opened SMS composer for '$recipient' with draft: \"$message\"")
        } catch (e: Exception) {
            // Fallback generic send
            try {
                val genericIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, message)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(Intent.createChooser(genericIntent, "Send message via").apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                })
                Result.success("Opened share sheet with prepared message")
            } catch (ex: Exception) {
                Result.failure(Exception("Could not open messaging client: ${ex.localizedMessage}"))
            }
        }
    }
}
