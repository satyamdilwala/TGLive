package app.fqrs.tglive.ui

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.fqrs.tglive.R
import app.fqrs.tglive.utils.Country
import app.fqrs.tglive.utils.CountryHelper

class CountryPickerDialog(
    context: Context,
    private val onCountrySelected: (Country) -> Unit
) : Dialog(context) {
    
    private lateinit var searchEditText: EditText
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: CountryAdapter
    
    private val countries = CountryHelper.getCountries()
    private var filteredCountries = countries.toMutableList()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.dialog_country_picker)
        
        // Make dialog wider - 90% of screen width
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.8).toInt()
        )
        
        setupViews()
        setupRecyclerView()
        setupSearch()
    }
    
    private fun setupViews() {
        searchEditText = findViewById(R.id.searchEditText)
        recyclerView = findViewById(R.id.countriesRecyclerView)
        
        findViewById<ImageView>(R.id.closeButton).setOnClickListener {
            dismiss()
        }
    }
    
    private fun setupRecyclerView() {
        adapter = CountryAdapter(filteredCountries) { country ->
            onCountrySelected(country)
            dismiss()
        }
        recyclerView.layoutManager = LinearLayoutManager(context)
        recyclerView.adapter = adapter
    }
    
    private fun setupSearch() {
        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                filterCountries(s.toString())
            }
        })
    }
    
    private fun filterCountries(query: String) {
        filteredCountries.clear()
        if (query.isEmpty()) {
            filteredCountries.addAll(countries)
        } else {
            filteredCountries.addAll(countries.filter { country ->
                country.name.contains(query, ignoreCase = true) ||
                country.dialCode.contains(query) ||
                country.code.contains(query, ignoreCase = true)
            })
        }
        adapter.notifyDataSetChanged()
    }
}

class CountryAdapter(
    private val countries: List<Country>,
    private val onCountryClick: (Country) -> Unit
) : RecyclerView.Adapter<CountryAdapter.CountryViewHolder>() {
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CountryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_country, parent, false)
        return CountryViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: CountryViewHolder, position: Int) {
        holder.bind(countries[position])
    }
    
    override fun getItemCount(): Int = countries.size
    
    inner class CountryViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val flagTextView: TextView = itemView.findViewById(R.id.flagTextView)
        private val nameTextView: TextView = itemView.findViewById(R.id.nameTextView)
        private val dialCodeTextView: TextView = itemView.findViewById(R.id.dialCodeTextView)
        
        fun bind(country: Country) {
            flagTextView.text = country.flag
            nameTextView.text = country.name
            dialCodeTextView.text = country.dialCode
            
            itemView.setOnClickListener {
                onCountryClick(country)
            }
        }
    }
}