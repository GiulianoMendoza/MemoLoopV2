package com.example.memoloop

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity // Ya no se usa directamente, ahora se hereda de BaseActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.text.SimpleDateFormat
import java.util.*

import com.bumptech.glide.Glide
import com.github.chrisbanes.photoview.PhotoView
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.maps.UiSettings

class ReminderDetailActivity : BaseActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    private lateinit var tvTitle: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvTime: TextView
    private lateinit var tvFrequency: TextView
    private lateinit var tvType: TextView
    private lateinit var cardView: CardView
    private lateinit var cardImgReminder: CardView
    private lateinit var cardMapReminder: CardView
    private lateinit var imgReminder: PhotoView
    private lateinit var btnDownloadImage: ImageButton

    private lateinit var mapView: MapView

    private lateinit var btnEdit: Button
    private lateinit var btnDelete: Button

    private var reminderId: String? = null
    private var reminderData: Reminder? = null

    override fun attachBaseContext(newBase: Context?) {
        super.attachBaseContext(LanguageManager.updateBaseContextLocale(newBase!!))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        MapLibre.getInstance(this)
        setContentView(R.layout.activity_reminder_detail)


        auth = FirebaseAuth.getInstance()
        firestore = FirebaseFirestore.getInstance()

        initViews()
        loadReminder()
        setupListeners()
    }

    private fun initViews() {
        tvTitle = findViewById(R.id.tv_detail_title)
        tvDate = findViewById(R.id.tv_detail_date)
        tvTime = findViewById(R.id.tv_detail_time)
        tvFrequency = findViewById(R.id.tv_detail_frequency)
        tvType = findViewById(R.id.tv_detail_type)
        btnEdit = findViewById(R.id.btn_edit)
        btnDelete = findViewById(R.id.btn_delete)
        cardView = findViewById(R.id.card_reminder_detail)
        imgReminder = findViewById(R.id.img_reminder)
        mapView = findViewById(R.id.mapView)
        cardImgReminder = findViewById(R.id.card_img_reminder)
        cardMapReminder = findViewById(R.id.card_map_reminder)
        btnDownloadImage = findViewById(R.id.btn_download_image)

        mapView.onCreate(null)
    }

    private fun getColorByType(context: Context, type: String): Int {
        val typeLower = type.lowercase()
        val colorRes = when (typeLower) {
            "salud" -> R.color.reminder_category_salud
            "deporte" -> R.color.reminder_category_deporte
            "ocio" -> R.color.reminder_category_ocio
            "estudio" -> R.color.reminder_category_estudio
            "general" -> R.color.reminder_category_general
            else -> R.color.reminder_category_general
        }
        return ContextCompat.getColor(context, colorRes)
    }

    private fun loadReminder() {
        reminderId = intent.getStringExtra("REMINDER_ID")
        val userId = auth.currentUser?.uid

        if (userId == null || reminderId == null) {
            Toast.makeText(this, getString(R.string.error_loading_reminder_detail), Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        firestore.collection("users")
            .document(userId)
            .collection("reminders")
            .document(reminderId!!)
            .get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    reminderData = doc.toObject(Reminder::class.java)
                    reminderData?.let { displayReminder(it) }
                } else {
                    Toast.makeText(this, getString(R.string.toast_reminder_not_found), Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.error_fetching_reminder_detail), Toast.LENGTH_SHORT).show()
                finish()
            }
    }

    private fun displayReminder(reminder: Reminder) {
        tvTitle.text = reminder.title

        val date = Date(reminder.timestamp)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        tvDate.text = getString(R.string.detail_date_placeholder).split(":")[0] + ": ${dateFormat.format(date)}"
        tvTime.text = getString(R.string.detail_time_placeholder).split(":")[0] + ": ${timeFormat.format(date)}"
        tvFrequency.text = reminder.type
        tvType.text = reminder.category

        val url = reminder.imageUrl?.trim()
        if (!url.isNullOrEmpty()) {
            cardImgReminder.visibility = View.VISIBLE
            Glide.with(this)
                .load(url)
                .into(imgReminder)
            btnDownloadImage.visibility = View.VISIBLE
            btnDownloadImage.setOnClickListener {
                downloadImage(this, url, reminder.title)
            }
        } else {
            cardImgReminder.visibility = View.GONE
            btnDownloadImage.visibility = View.GONE
        }

        if (reminder.latitude != null && reminder.longitude != null) {
            cardMapReminder.visibility = View.VISIBLE

            val styleUrl = "https://basemaps.cartocdn.com/gl/positron-gl-style/style.json"

            mapView.getMapAsync { mapLibreMap ->
                mapLibreMap.setStyle(styleUrl) {

                    val location = LatLng(reminder.latitude!!, reminder.longitude!!)

                    mapLibreMap.setCameraPosition(
                        CameraPosition.Builder()
                            .target(location)
                            .zoom(14.0)
                            .build()
                    )

                    mapLibreMap.uiSettings.apply {
                        isScrollGesturesEnabled = true
                        isZoomGesturesEnabled = true
                        isRotateGesturesEnabled = false
                        isTiltGesturesEnabled = false
                    }

                    mapLibreMap.addMarker(
                        MarkerOptions()
                            .position(location)
                            .title(getString(R.string.map_location_title))
                    )
                }
            }
        } else {
            cardMapReminder.visibility = View.GONE
        }

        cardView.setCardBackgroundColor(getColorByType(this, reminder.category))
    }

    private fun setupListeners() {
        btnEdit.setOnClickListener {
            reminderData?.let { reminder ->
                val intent = Intent(this, AddReminderActivity::class.java).apply {
                    putExtra("EDIT_MODE", true)
                    putExtra("REMINDER_ID", reminderId)
                    putExtra("TITLE", reminder.title)
                    putExtra("TIMESTAMP", reminder.timestamp)
                    putExtra("TYPE", reminder.type)
                    putExtra("CATEGORY", reminder.category)
                    putExtra("LATITUDE", reminder.latitude)
                    putExtra("LONGITUDE", reminder.longitude)
                    putExtra("IMAGE_URL", reminder.imageUrl)
                    putStringArrayListExtra("SHARED_WITH", ArrayList(reminder.sharedWith))
                }
                startActivity(intent)
            }
        }

        btnDelete.setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle(getString(R.string.delete_reminder_title_dialog))
                .setMessage(getString(R.string.delete_reminder_confirmation_detail))
                .setPositiveButton(getString(R.string.yes_delete_button_short)) { _, _ -> deleteReminder() } // Reutilizar 'yes_delete_button_short'
                .setNegativeButton(getString(R.string.no_cancel_button), null)
                .show()
        }
    }

    private fun deleteReminder() {
        val userId = auth.currentUser?.uid ?: return
        val id = reminderId ?: return

        firestore.collection("users")
            .document(userId)
            .collection("reminders")
            .document(id)
            .delete()
            .addOnSuccessListener {
                Toast.makeText(this, getString(R.string.toast_reminder_deleted), Toast.LENGTH_SHORT).show()
                finish()
            }
            .addOnFailureListener {
                Toast.makeText(this, getString(R.string.error_deleting_reminder_detail), Toast.LENGTH_SHORT).show()
            }
    }

    private fun downloadImage(context: Context, imageUrl: String, reminderTitle: String) {
        val fileName = "${reminderTitle.replace("[^a-zA-Z0-9.-]".toRegex(), "_")}.jpg"
        val request = DownloadManager.Request(Uri.parse(imageUrl))
            .setTitle(getString(R.string.download_image_title, fileName))
            .setDescription(getString(R.string.download_image_description))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            .setAllowedOverMetered(true)
            .setAllowedOverRoaming(true)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        downloadManager.enqueue(request)
        Toast.makeText(context, getString(R.string.toast_download_started), Toast.LENGTH_SHORT).show()
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }
    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }
    override fun onPause() {
        mapView.onPause()
        super.onPause()
    }
    override fun onStop() {
        mapView.onStop()
        super.onStop()
    }
    override fun onDestroy() {
        mapView.onDestroy()
        super.onDestroy()
    }
    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }
}
