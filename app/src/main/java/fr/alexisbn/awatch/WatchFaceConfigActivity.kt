package fr.alexisbn.awatch

import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.RecyclerView
import androidx.wear.widget.WearableLinearLayoutManager
import androidx.wear.widget.WearableRecyclerView
import kotlinx.android.synthetic.main.activity_watch_face_config.*


class WatchFaceConfigActivity : AppCompatActivity() {
    private val TAG = "WatchFaceConfigActivity"

    private lateinit var recyclerView: WearableRecyclerView
    private lateinit var viewAdapter: SettingsListAdapter
    private lateinit var viewManager: WearableLinearLayoutManager
    private lateinit var viewAdapterListener: SettingsListAdapter.Callback

    var settingsItems = arrayListOf<Triple<String, Int, String>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_watch_face_config)

        settingsItems.add(Triple("Couleur", R.drawable.ic_baseline_color_lens_24, "ACCENT_SELECTOR"))
        settingsItems.add(Triple("Complication 1", R.drawable.ic_baseline_one_24, "FIRST_COMPLICATION_SELECTOR"))
        settingsItems.add(Triple("Complication 2", R.drawable.ic_baseline_two_24, "SECOND_COMPLICATION_SELECTOR"))
        settingsItems.add(Triple("Complication 3", R.drawable.ic_baseline_3_24, "THIRD_COMPLICATION_SELECTOR"))

        viewAdapterListener = object : SettingsListAdapter.Callback {
            override fun handleFeature(featureName: String) {
                when (featureName) {
                    "ACCENT_SELECTOR" -> {
                        Log.d(TAG, "couleur")
                    }
                    "FIRST_COMPLICATION_SELECTOR" -> {
                        Log.d(TAG, "C1")
                    }
                    "SECOND_COMPLICATION_SELECTOR" -> {
                        Log.d(TAG, "C2")
                    }
                    "THIRD_COMPLICATION_SELECTOR" -> {
                        Log.d(TAG, "C3")
                    }
                    else -> {
                        Log.d(TAG, "unknown")
                    }
                }
            }
        }

        viewManager = WearableLinearLayoutManager(this)
        viewAdapter = SettingsListAdapter(settingsItems, viewAdapterListener)

        settingsRecyclerView.apply {
            layoutManager = viewManager
            adapter = viewAdapter
            setHasFixedSize(true)
        }
    }

    class SettingsListAdapter(
        private var settingsItems: ArrayList<Triple<String, Int, String>>,
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

            holder.itemView.setOnClickListener {
                Log.d(TAG, "Element $pos clicked.")
                callback?.handleFeature(settingsItems[pos].third)
            }
        }

        // Return the size of the dataset (invoked by the layout manager)
        override fun getItemCount() = settingsItems.size

        companion object {
            private val TAG = "WFCA.SettingsListAdapter"
        }
    }
}