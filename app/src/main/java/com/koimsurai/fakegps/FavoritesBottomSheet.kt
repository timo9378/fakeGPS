package com.koimsurai.fakegps

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import org.osmdroid.util.GeoPoint

class FavoritesBottomSheet(
    private val favorites: MutableMap<String, GeoPoint>,
    private val onFavoriteSelected: (GeoPoint) -> Unit,
    private val onFavoriteDeleted: (String) -> Unit
) : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_favorites, container, false)
        val recyclerView = view.findViewById<RecyclerView>(R.id.favorites_recycler_view)
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = FavoritesAdapter(favorites, onFavoriteSelected, onFavoriteDeleted)
        return view
    }
}

class FavoritesAdapter(
    private val favorites: MutableMap<String, GeoPoint>,
    private val onFavoriteSelected: (GeoPoint) -> Unit,
    private val onFavoriteDeleted: (String) -> Unit
) : RecyclerView.Adapter<FavoritesAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val nameTextView: TextView = view.findViewById(R.id.favorite_name)
        val deleteButton: ImageButton = view.findViewById(R.id.delete_favorite_button)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_favorite, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val name = favorites.keys.elementAt(position)
        holder.nameTextView.text = name
        holder.itemView.setOnClickListener {
            favorites[name]?.let { geoPoint -> onFavoriteSelected(geoPoint) }
        }
        holder.deleteButton.setOnClickListener {
            onFavoriteDeleted(name)
        }
    }

    override fun getItemCount() = favorites.size
}