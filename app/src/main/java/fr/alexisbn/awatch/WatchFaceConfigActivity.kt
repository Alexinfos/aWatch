package fr.alexisbn.awatch

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Bundle
import android.support.wearable.complications.ComplicationData
import android.support.wearable.complications.ComplicationHelperActivity
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView
import fr.alexisbn.awatch.databinding.ActivityWatchFaceConfigBinding
import org.jraf.android.androidwearcolorpicker.ColorPickActivity


class WatchFaceConfigActivity : AppCompatActivity() {
    private val TAG = "WatchFaceConfigActivity"

    private lateinit var binding: ActivityWatchFaceConfigBinding

    private lateinit var recyclerView: WearableRecyclerView
    private lateinit var viewAdapter: SettingsListAdapter
    private lateinit var viewManager: WearableLinearLayoutManager
    private lateinit var viewAdapterListener: SettingsListAdapter.Callback

    var settingsItems = arrayListOf<Triple<String, Int, String>>()

    var selectedColor = Color.parseColor("#00DE7A")
    var complicationTopSlotId = 10
    var complicationLeftSlotId = 20
    var complicationBottomSlotId = 30
    var prideRing = false

    private lateinit var sharedPref: SharedPreferences

    private val supportedComplicationTypes = intArrayOf(
        ComplicationData.TYPE_RANGED_VALUE,
        ComplicationData.TYPE_ICON,
        ComplicationData.TYPE_SHORT_TEXT,
        ComplicationData.TYPE_SMALL_IMAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWatchFaceConfigBinding.inflate(layoutInflater)
        val view = binding.root
        setContentView(view)

        sharedPref = getSharedPreferences(getString(R.string.preference_file_key), Context.MODE_PRIVATE) ?: return
        prideRing = sharedPref.getBoolean("prideRing", false)

        settingsItems.add(
            Triple(
                "Couleur",
                R.drawable.ic_baseline_color_lens_24,
                "ACCENT_SELECTOR"
            )
        )
        settingsItems.add(
            Triple(
                "Complication 1",
                R.drawable.ic_baseline_one_24,
                "FIRST_COMPLICATION_SELECTOR"
            )
        )
        settingsItems.add(
            Triple(
                "Complication 2",
                R.drawable.ic_baseline_two_24,
                "SECOND_COMPLICATION_SELECTOR"
            )
        )
        settingsItems.add(
            Triple(
                "Complication 3",
                R.drawable.ic_baseline_3_24,
                "THIRD_COMPLICATION_SELECTOR"
            )
        )
        settingsItems.add(
            Triple(
                "Pride ring: " + if (prideRing) "on" else "off",
                R.drawable.ic_ring,
                "PRIDE_TOGGLE"
            )
        )

        viewAdapterListener = object : SettingsListAdapter.Callback {
            override fun handleFeature(featureName: String) {
                when (featureName) {
                    "ACCENT_SELECTOR" -> {
                        Log.d(TAG, "couleur")
                        val intent =
                            ColorPickActivity.IntentBuilder().oldColor(selectedColor).build(
                                view.context
                            )
                        startActivityForResult(intent, 4242)
                    }
                    "FIRST_COMPLICATION_SELECTOR" -> {
                        Log.d(TAG, "C1")
                        val intent = ComplicationHelperActivity.createProviderChooserHelperIntent(
                            this@WatchFaceConfigActivity, ComponentName(
                                view.context,
                                MyWatchFace::class.java
                            ), complicationTopSlotId, *supportedComplicationTypes
                        )
                        startActivityForResult(intent, 20561)
                    }
                    "SECOND_COMPLICATION_SELECTOR" -> {
                        Log.d(TAG, "C2")
                        val intent = ComplicationHelperActivity.createProviderChooserHelperIntent(
                            this@WatchFaceConfigActivity, ComponentName(
                                view.context,
                                MyWatchFace::class.java
                            ), complicationLeftSlotId, *supportedComplicationTypes
                        )
                        startActivityForResult(intent, 20562)
                    }
                    "THIRD_COMPLICATION_SELECTOR" -> {
                        Log.d(TAG, "C3")
                        val intent = ComplicationHelperActivity.createProviderChooserHelperIntent(
                            this@WatchFaceConfigActivity, ComponentName(
                                view.context,
                                MyWatchFace::class.java
                            ), complicationBottomSlotId, *supportedComplicationTypes
                        )
                        startActivityForResult(intent, 20563)
                    }
                    "PRIDE_TOGGLE" -> {
                        Log.d(TAG, "Pride")
                        prideRing = !prideRing
                        with(sharedPref.edit()) {
                            putBoolean("prideRing", prideRing)
                            apply()
                        }
                        viewAdapter.updateItemTitle(4, "Pride ring: " + if (prideRing) "on" else "off")
                    }
                    else -> {
                        Log.d(TAG, "unknown")
                    }
                }
            }
        }

        viewManager = WearableLinearLayoutManager(this)
        viewAdapter = SettingsListAdapter(settingsItems, selectedColor, viewAdapterListener)

        binding.settingsRecyclerView.apply {
            layoutManager = viewManager
            adapter = viewAdapter
            setHasFixedSize(true)

            isEdgeItemsCenteringEnabled = true
            isCircularScrollingGestureEnabled = true
            requestFocus()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            4242 -> {
                if (resultCode != RESULT_CANCELED) {
                    // TODO: update color.
                    selectedColor = ColorPickActivity.getPickedColor(data!!)
                    viewAdapter.updateSelectedAccentColor(selectedColor)
                }
            }
        }
    }

    class SettingsListAdapter(
        private var settingsItems: ArrayList<Triple<String, Int, String>>,
        private var selectedAccentColor: Int,
        private var callback: Callback?
    ) : RecyclerView.Adapter<SettingsListAdapter.ViewHolder>() {

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val iconImageView: ImageView
            val titleTextView: TextView

            init {
                iconImageView = v.findViewById(R.id.iconImageView)
                titleTextView = v.findViewById(R.id.titleTextView)
            }
        }

        interface Callback {
            fun handleFeature(featureName: String)
        }

        fun setCallback(callback: Callback) {
            this.callback = callback
        }

        // Create new views (invoked by the layout manager)
        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            // Create a new view.
            val v = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.item_watch_face_config_setting, viewGroup, false)
            return ViewHolder(v)
        }

        // Replace the contents of a view (invoked by the layout manager)
        override fun onBindViewHolder(holder: ViewHolder, pos: Int) {
            Log.d(TAG, "Element $pos set.")

            // Get element from the dataset at this position and replace the contents of the view
            // with that element
            holder.titleTextView.text = settingsItems[pos].first
            holder.iconImageView.setImageResource(settingsItems[pos].second)

            if (settingsItems[pos].third == "ACCENT_SELECTOR") {
                DrawableCompat.setTint(holder.iconImageView.drawable, selectedAccentColor)
            }

            holder.itemView.setOnClickListener {
                Log.d(TAG, "Element $pos clicked.")
                callback?.handleFeature(settingsItems[pos].third)
            }
        }

        fun updateSelectedAccentColor(newAccentColor: Int) {
            selectedAccentColor = newAccentColor
            notifyDataSetChanged()
        }

        fun updateItemTitle(itemPos: Int, newTitle: String) {
            settingsItems[itemPos] = Triple(
                newTitle,
                settingsItems[itemPos].second,
                settingsItems[itemPos].third
            )
            notifyDataSetChanged()
        }

        // Return the size of the dataset (invoked by the layout manager)
        override fun getItemCount() = settingsItems.size

        companion object {
            private val TAG = "WFCA.SettingsListAdapter"
        }
    }
}