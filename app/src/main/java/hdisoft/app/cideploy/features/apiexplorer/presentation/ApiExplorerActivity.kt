package hdisoft.app.cideploy.features.apiexplorer.presentation

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import hdisoft.app.cideploy.R
import hdisoft.app.cideploy.features.apiexplorer.data.ApiCatalog
import hdisoft.app.cideploy.features.apiexplorer.data.ApiEndpoint
import hdisoft.app.cideploy.features.apiexplorer.data.ApiSessionPrefs
import hdisoft.app.appupdate.hostdiscovery.di.HostDiscoveryServiceLocator

class ApiExplorerActivity : AppCompatActivity() {

    private lateinit var rvApiEndpoints: RecyclerView
    private lateinit var tvConnectionSummary: TextView
    private lateinit var sessionPrefs: ApiSessionPrefs

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_api_explorer)

        sessionPrefs = ApiSessionPrefs(this)

        val toolbar: Toolbar = findViewById(R.id.toolbarApiExplorer)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setDisplayShowHomeEnabled(true)
        toolbar.setNavigationOnClickListener { finish() }

        tvConnectionSummary = findViewById(R.id.tvConnectionSummary)
        rvApiEndpoints = findViewById(R.id.rvApiEndpoints)
        rvApiEndpoints.layoutManager = LinearLayoutManager(this)
        rvApiEndpoints.adapter = ApiEndpointAdapter(ApiCatalog.all) { endpoint ->
            startActivity(
                Intent(this, ApiExplorerDetailActivity::class.java)
                    .putExtra(ApiExplorerDetailActivity.EXTRA_ENDPOINT_ID, endpoint.id)
            )
        }

        findViewById<View>(R.id.btnApiSettings).setOnClickListener { showConnectionSettingsDialog() }

        seedHostFromDiscoveryIfEmpty()
        updateConnectionSummary()
    }

    override fun onResume() {
        super.onResume()
        updateConnectionSummary()
    }

    private fun seedHostFromDiscoveryIfEmpty() {
        if (sessionPrefs.getHost().isNotEmpty()) return
        try {
            HostDiscoveryServiceLocator.init(applicationContext)
            val savedHost = HostDiscoveryServiceLocator.getSavedHostUseCase()
            if (!savedHost.isNullOrEmpty()) {
                sessionPrefs.save(savedHost, sessionPrefs.getPort(), sessionPrefs.getUsername(), sessionPrefs.getPassword())
            }
        } catch (e: Exception) {
            // Host discovery not initialized yet — user can still set the host manually.
        }
    }

    private fun updateConnectionSummary() {
        val host = sessionPrefs.getHost().ifEmpty { "—" }
        tvConnectionSummary.text = "http://$host:${sessionPrefs.getPort()}  (${sessionPrefs.getUsername()})"
    }

    private fun showConnectionSettingsDialog() {
        val dp = resources.displayMetrics.density
        val scrollContainer = android.widget.ScrollView(this)
        val container = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            val p = (20 * dp).toInt()
            setPadding(p, p, p, p)
        }

        val etHost = EditText(this).apply {
            setText(sessionPrefs.getHost())
            hint = "192.168.1.135"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val etPort = EditText(this).apply {
            setText(sessionPrefs.getPort().toString())
            hint = "8080"
            inputType = InputType.TYPE_CLASS_NUMBER
            setSingleLine(true)
        }
        val etUsername = EditText(this).apply {
            setText(sessionPrefs.getUsername())
            hint = "admin"
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }
        val etPassword = EditText(this).apply {
            setText(sessionPrefs.getPassword())
            hint = "admin"
            inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
        }

        container.addView(TextView(this).apply { text = "Host" })
        container.addView(etHost)
        container.addView(TextView(this).apply { text = "Port"; setPadding(0, (12 * dp).toInt(), 0, 0) })
        container.addView(etPort)
        container.addView(TextView(this).apply { text = "Username (Basic Auth)"; setPadding(0, (12 * dp).toInt(), 0, 0) })
        container.addView(etUsername)
        container.addView(TextView(this).apply { text = "Password"; setPadding(0, (12 * dp).toInt(), 0, 0) })
        container.addView(etPassword)

        scrollContainer.addView(container)

        AlertDialog.Builder(this)
            .setTitle("Connection settings")
            .setView(scrollContainer)
            .setPositiveButton("Save") { _, _ ->
                val port = etPort.text.toString().trim().toIntOrNull() ?: 8080
                sessionPrefs.save(
                    etHost.text.toString().trim(),
                    port,
                    etUsername.text.toString().trim(),
                    etPassword.text.toString()
                )
                updateConnectionSummary()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private class ApiEndpointAdapter(
        private val endpoints: List<ApiEndpoint>,
        private val onClick: (ApiEndpoint) -> Unit
    ) : RecyclerView.Adapter<ApiEndpointViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ApiEndpointViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_api_endpoint, parent, false)
            return ApiEndpointViewHolder(view)
        }

        override fun onBindViewHolder(holder: ApiEndpointViewHolder, position: Int) {
            val endpoint = endpoints[position]
            holder.bind(endpoint)
            holder.itemView.setOnClickListener { onClick(endpoint) }
        }

        override fun getItemCount(): Int = endpoints.size
    }

    private class ApiEndpointViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvMethod: TextView = view.findViewById(R.id.tvMethod)
        private val tvTitle: TextView = view.findViewById(R.id.tvTitle)
        private val tvPath: TextView = view.findViewById(R.id.tvPath)

        fun bind(endpoint: ApiEndpoint) {
            tvMethod.text = endpoint.method
            val (bgColor, textColor) = getMethodColors(endpoint.method)
            tvMethod.setTextColor(textColor)

            val context = itemView.context
            val drawable = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.bg_badge_method)?.mutate() as? android.graphics.drawable.GradientDrawable
            drawable?.let {
                it.setColor(bgColor)
                tvMethod.background = it
            }

            tvTitle.text = endpoint.title
            tvPath.text = endpoint.pathTemplate
        }

        private fun getMethodColors(method: String): Pair<Int, Int> = when (method) {
            "GET" -> Color.parseColor("#E3F2FD") to Color.parseColor("#0D47A1")
            "POST" -> Color.parseColor("#E8F5E9") to Color.parseColor("#1B5E20")
            "PUT", "PATCH" -> Color.parseColor("#FFF3E0") to Color.parseColor("#E65100")
            "DELETE" -> Color.parseColor("#FFEBEE") to Color.parseColor("#B71C1C")
            else -> Color.parseColor("#F5F5F5") to Color.parseColor("#424242")
        }
    }
}
